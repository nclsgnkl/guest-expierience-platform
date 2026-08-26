package de.gxp.auth.control;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.gxp.infrastructure.http.ApiErrors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Per-address login budget (auth-hardening REQ-601..REQ-605, design D2/D3/
 * D20/D21; reopens REQ-326). Keyed by the ATTRIBUTED client address, never
 * global and never per account, so a flood from one address slows that
 * address only and a correct password from anywhere else is never refused.
 *
 * <p>{@link #check} is the first thing {@code AuthControl.login} and
 * {@code changePassword} do: a blocked address is answered {@code 429} +
 * {@code Retry-After} before any PBKDF2 derivation and before any database
 * read — the derivation is the amplifier, so the rejection must be cheaper
 * than the attempt.
 *
 * <p>Numbers (pinned in code, one {@code %test} speed override for the base
 * block): 5 failures inside a 15-minute window; the 5th blocks for 60 s,
 * every further failure doubles it, capped at 900 s; a success drops the
 * bucket. The map self-bounds at {@value #MAX_TRACKED} addresses (stale
 * buckets older than 1 h are evicted first, then the oldest), so no scheduler
 * job is needed and an attacker with many addresses cannot grow it.
 *
 * <p>Bucket key (D2, amended 2026-08-26 after review finding #2): an IPv4
 * address is its own bucket; an IPv6 address is keyed by its <b>/64 prefix</b>
 * — every LAN host owns a /64 and privacy extensions mint fresh addresses in
 * it for free, so a per-address key would hand one machine an unlimited
 * budget. An IPv4-mapped IPv6 address ({@code ::ffff:a.b.c.d}) is treated as
 * the IPv4 address it carries.
 *
 * <p>Logs nothing — no address, no username, no count (principle 3). Never
 * consults the user table.
 */
@ApplicationScoped
class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration MAX_BLOCK = Duration.ofSeconds(900);
    static final Duration STALE_AFTER = Duration.ofHours(1);
    static final int MAX_TRACKED = 10_000;

    private static final class Bucket {
        int failures;
        Instant windowStart;
        Instant blockedUntil;
        Instant lastTouched;

        Bucket(Instant now) {
            this.windowStart = now;
            this.lastTouched = now;
        }
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration baseBlock;

    @Inject
    LoginThrottle(@ConfigProperty(name = "gxp.auth.throttle-block-seconds", defaultValue = "60") long baseBlockSeconds) {
        this(Clock.systemUTC(), baseBlockSeconds);
    }

    LoginThrottle(Clock clock, long baseBlockSeconds) {
        this.clock = clock;
        this.baseBlock = Duration.ofSeconds(baseBlockSeconds);
    }

    /**
     * The map key for an attributed address: IPv4 verbatim, IPv6 masked to
     * its /64 (rendered {@code <prefix>/64}), IPv4-mapped IPv6 as IPv4. The
     * parse never triggers a name lookup: only a literal IPv6 shape is handed
     * to {@link InetAddress}; anything else is used verbatim.
     */
    static String bucketKey(String address) {
        if (address.indexOf(':') < 0) {
            return address;
        }
        String literal = address;
        int scope = literal.indexOf('%');
        if (scope >= 0) {
            literal = literal.substring(0, scope);
        }
        if (!literal.matches("[0-9A-Fa-f:.]+")) {
            return address;
        }
        try {
            InetAddress parsed = InetAddress.getByName(literal);
            if (!(parsed instanceof Inet6Address)) {
                return parsed.getHostAddress(); // IPv4-mapped — the JDK already unwrapped it
            }
            byte[] bytes = parsed.getAddress();
            for (int i = 8; i < bytes.length; i++) {
                bytes[i] = 0;
            }
            return InetAddress.getByAddress(bytes).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            return address;
        }
    }

    /** Throws {@code 429 too-many-attempts} while the address is blocked; otherwise returns. */
    void check(String address) {
        Bucket bucket = buckets.get(bucketKey(address));
        if (bucket == null) {
            return;
        }
        Instant now = clock.instant();
        Instant until;
        synchronized (bucket) {
            until = bucket.blockedUntil;
        }
        if (until != null && now.isBefore(until)) {
            long retryAfter = Math.max(1, Duration.between(now, until).plusMillis(999).toSeconds());
            throw ApiErrors.tooManyRequests("too-many-attempts", "Too many failed attempts", retryAfter);
        }
    }

    void recordFailure(String address) {
        Instant now = clock.instant();
        String key = bucketKey(address);
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            bucket = insert(key, now);
        }
        synchronized (bucket) {
            if (Duration.between(bucket.windowStart, now).compareTo(WINDOW) > 0) {
                bucket.failures = 0;
                bucket.windowStart = now;
                bucket.blockedUntil = null;
            }
            bucket.failures++;
            bucket.lastTouched = now;
            if (bucket.failures >= MAX_FAILURES) {
                int exhaustions = Math.min(bucket.failures - MAX_FAILURES, 20);
                Duration block = baseBlock.multipliedBy(1L << exhaustions);
                bucket.blockedUntil = now.plus(block.compareTo(MAX_BLOCK) > 0 ? MAX_BLOCK : block);
            }
        }
    }

    void recordSuccess(String address) {
        buckets.remove(bucketKey(address));
    }

    private synchronized Bucket insert(String key, Instant now) {
        if (buckets.size() >= MAX_TRACKED) {
            Instant staleBefore = now.minus(STALE_AFTER);
            buckets.entrySet().removeIf(e -> e.getValue().lastTouched.isBefore(staleBefore));
            while (buckets.size() >= MAX_TRACKED) {
                buckets.entrySet().stream()
                        .min(Comparator.comparing((Map.Entry<String, Bucket> e) -> e.getValue().lastTouched))
                        .ifPresent(oldest -> buckets.remove(oldest.getKey()));
            }
        }
        return buckets.computeIfAbsent(key, k -> new Bucket(now));
    }

    // --- test probes (package-private) -------------------------------------

    void reset() {
        buckets.clear();
    }

    int failuresOf(String address) {
        Bucket bucket = buckets.get(bucketKey(address));
        if (bucket == null) {
            return 0;
        }
        synchronized (bucket) {
            return bucket.failures;
        }
    }

    int trackedAddresses() {
        return buckets.size();
    }
}
