package de.gxp.update.control;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import de.gxp.api.model.UpdateCheckOutcome;
import de.gxp.api.model.UpdateSettingsRequest;
import de.gxp.api.model.UpdateStatusDto;
import de.gxp.audit.control.AuditControl;
import de.gxp.infrastructure.http.ApiErrors;
import de.gxp.property.control.PropertyControl;
import de.gxp.update.entity.UpdateChannel;
import de.gxp.update.entity.UpdateChannelMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/**
 * Control — the update channel's single public entry point (update-channel
 * Design §1, REQ-701…716): the hourly check ({@link #checkForUpdate}), the
 * desired-file reconciliation ({@link #reconcileDesiredUpdate}) and the
 * four manager operations (REQ-710…714, audited per D20). Orchestration only: the row lives behind
 * {@link UpdateStateStore} (short transactions of its own — HTTP and file
 * I/O never run inside one), the files behind {@link ExchangeFiles}.
 *
 * <p><b>Logging (principle 3):</b> an outcome code and, for a transport
 * failure, an exception class name. Never a URL, never a body byte.
 */
@ApplicationScoped
public class UpdateControl {

    private static final Logger LOG = Logger.getLogger(UpdateControl.class);
    static final Duration APPLY_WINDOW = Duration.ofSeconds(ExchangeFiles.APPLY_WINDOW_SECONDS);

    private final UpdateStateStore store;
    private final ExchangeFiles files;
    private final ManifestClient client;
    private final ManifestVerifier verifier;
    private final SigningKeyLoader keys;
    private final ManifestParser parser = new ManifestParser();
    private final PropertyControl propertyControl;
    private final AuditControl auditControl;
    private final SemanticVersion running;
    private final String channel;
    private final long jitterSeconds;
    private final ReentrantLock checkLock = new ReentrantLock();

    /** Read per check; a package-private override exists for the test cases that need another value. */
    private volatile String baseUrl;
    private volatile boolean allowPlainHttp;
    private volatile Clock clock = Clock.systemUTC();

    UpdateControl(UpdateStateStore store, ExchangeFiles files, ManifestClient client, ManifestVerifier verifier,
            SigningKeyLoader keys, PropertyControl propertyControl, AuditControl auditControl,
            @ConfigProperty(name = "quarkus.application.version") String runningVersion,
            @ConfigProperty(name = "gxp.update.base-url") Optional<String> baseUrl,
            @ConfigProperty(name = "gxp.update.channel") String channel,
            @ConfigProperty(name = "gxp.update.check-jitter-seconds") long jitterSeconds,
            @ConfigProperty(name = "gxp.update.allow-plain-http") boolean allowPlainHttp) {
        this.store = store;
        this.files = files;
        this.client = client;
        this.verifier = verifier;
        this.keys = keys;
        this.propertyControl = propertyControl;
        this.auditControl = auditControl;
        try {
            this.running = SemanticVersion.parse(runningVersion);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("quarkus.application.version is not MAJOR.MINOR.PATCH[-QUALIFIER]");
        }
        this.baseUrl = baseUrl.map(String::strip).filter(s -> !s.isEmpty()).orElse(null);
        this.channel = channel;
        this.jitterSeconds = jitterSeconds;
        this.allowPlainHttp = allowPlainHttp;
    }

    /** Design §1: the channel name is validated at startup, so a bad value refuses to boot rather than to 404 hourly. */
    void onStart(@Observes StartupEvent event) {
        if (!ManifestParser.CHANNEL.matcher(channel).matches()) {
            throw new IllegalStateException("gxp.update.channel does not match ^[a-z0-9-]{1,32}$");
        }
    }

    // ------------------------------------------------------------ scheduler entry points (REQ-707, REQ-708)

    /** Hourly: sleeps 0..jitter seconds (a fleet must not hit the server in one second, D10), then checks. */
    public void checkForUpdate() {
        if (jitterSeconds > 0) {
            try {
                Thread.sleep(Duration.ofSeconds(ThreadLocalRandom.current().nextLong(jitterSeconds + 1)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        runCheck();
    }

    /**
     * Every 15 minutes and after every request-path mutation (REQ-708/714/715,
     * D11–D13): makes the desired file agree with the row, the settings, the
     * updater's state and the clock. Idempotent per apply window.
     */
    public void reconcileDesiredUpdate() {
        UpdateChannel row = store.load();
        if (row.availableVersion == null) {
            files.deleteDesired();
            return;
        }
        Optional<ExchangeFiles.UpdaterState> state = files.readState();
        Instant lastRequestedAt = state.map(ExchangeFiles.UpdaterState::lastRequestedAt).orElse(null);
        boolean manualPending = row.manualRequestedAt != null
                && row.availableVersion.equals(row.manualRequestedVersion)
                && (lastRequestedAt == null || row.manualRequestedAt.isAfter(lastRequestedAt));
        if (manualPending) {
            files.writeDesired(desired(row, row.manualRequestedAt, ExchangeFiles.REQUESTED_BY_MANUAL, row.manualRequestedAt));
            return;
        }
        if (!row.autoUpdateEnabled) {
            files.deleteDesired();
            return;
        }
        String attempted = state.map(s -> s.dto().getAttemptedVersion()).orElse(null);
        if (row.availableVersion.equals(attempted)) {
            files.deleteDesired(); // hold rule (D13): one automatic attempt per version, a retry is manual
            return;
        }
        Instant now = clock.instant();
        Instant next = nextOccurrence(row, now);
        Optional<ExchangeFiles.DesiredUpdate> existing = files.readDesired();
        if (existing.isPresent() && existing.get().version().equals(row.availableVersion)
                && ExchangeFiles.REQUESTED_BY_AUTO.equals(existing.get().requestedBy())
                && existing.get().applyNotBefore().plus(APPLY_WINDOW).isAfter(now)
                && (!existing.get().applyNotBefore().isAfter(now) || existing.get().applyNotBefore().equals(next))) {
            return; // same window, still open (or currently open) — requestedAt stays stable
        }
        files.writeDesired(desired(row, now, ExchangeFiles.REQUESTED_BY_AUTO, next));
    }

    // ------------------------------------------------------------ the manager API (REQ-710…714, REQ-726)

    /** The row, the configuration, the running version, the desired file's schedule and the updater's state — no raw bytes (REQ-726). */
    public UpdateStatusDto status() {
        UpdateStatusDto status = UpdateChannelMapper.toStatus(store.load())
                .currentVersion(running.toString())
                .channel(channel)
                .configured(baseUrl != null)
                .timezone(propertyControl.get().getTimezone());
        files.readDesired().ifPresent(desired -> status.scheduledFor(desired.applyNotBefore().atOffset(ZoneOffset.UTC)));
        files.readState().ifPresent(state -> status.updater(state.dto()));
        return status;
    }

    /** Audited one row per changed field (D18/D20); the desired file is reconciled at once (REQ-714). */
    @Transactional
    public UpdateStatusDto updateSettings(UpdateSettingsRequest request) {
        UpdateChannel row = store.load();
        UpdateSettingsRequest before = new UpdateSettingsRequest().autoUpdateEnabled(row.autoUpdateEnabled)
                .applyAt(row.applyAt.toString());
        LocalTime applyAt;
        try {
            applyAt = LocalTime.parse(request.getApplyAt());
        } catch (DateTimeException e) {
            throw ApiErrors.badRequest("validation-failed", "applyAt must be HH:MM");
        }
        store.saveSettings(request.getAutoUpdateEnabled(), applyAt, clock.instant());
        auditControl.record(AuditControl.ACTION_UPDATE, AuditControl.ENTITY_UPDATE_SETTINGS, "1",
                AuditControl.changedFields(before, request));
        reconcileDesiredUpdate();
        return status();
    }

    /** {@code 409 no-update-available} without a verified manifest; otherwise a manual request with apply-not-before = now (REQ-712). */
    @Transactional
    public UpdateStatusDto applyNow() {
        UpdateChannel row = store.load();
        if (row.availableVersion == null) {
            throw ApiErrors.conflict("no-update-available", "No verified update is available to apply");
        }
        store.recordManualRequest(row.availableVersion, clock.instant());
        auditControl.record(AuditControl.ACTION_UPDATE_APPLY, AuditControl.ENTITY_UPDATE, row.availableVersion, null);
        reconcileDesiredUpdate();
        return status();
    }

    /**
     * NOT transactional (D9): the check talks HTTP for up to ~15 s; the audit
     * row opens its own transaction afterwards. The row is written even when
     * a concurrent check held the lock and this call only returned its
     * outcome (review R2-6): what is audited is that the manager ASKED, and
     * the returned status carries the outcome of the check that ran.
     */
    public UpdateStatusDto checkNow() {
        runCheck();
        auditControl.record(AuditControl.ACTION_UPDATE_CHECK, AuditControl.ENTITY_UPDATE, null, null);
        return status();
    }

    // ------------------------------------------------------------ the check (REQ-701…706, REQ-727)

    /** The sequence of Design §1; returns the recorded outcome. Serialized by a lock — a concurrent call returns the current one. */
    UpdateCheckOutcome runCheck() {
        if (!checkLock.tryLock()) {
            return UpdateCheckOutcome.fromValue(store.load().lastCheckOutcome);
        }
        try {
            UpdateCheckOutcome outcome = fetchAndDecide();
            reconcileDesiredUpdate();
            return outcome;
        } finally {
            checkLock.unlock();
        }
    }

    private UpdateCheckOutcome fetchAndDecide() {
        String base = baseUrl;
        if (base == null) {
            return record(UpdateCheckOutcome.NOT_CONFIGURED, null);
        }
        if (!base.startsWith("https://") && !allowPlainHttp) {
            return record(UpdateCheckOutcome.INSECURE_BASE_URL, null);
        }
        String prefix = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/manifest/" + channel + ".json";
        // ONE deadline for the whole check (REQ-706 "≤ 15 s per tick", review R2-2): the .sig fetch gets what the manifest left
        long startedAt = System.nanoTime();
        ManifestClient.Fetched manifest = client.get(prefix, ManifestClient.TOTAL_DEADLINE);
        if (!manifest.ok()) {
            return record(failureOf(manifest), manifest.exceptionClassName());
        }
        Duration remaining = ManifestClient.TOTAL_DEADLINE.minus(Duration.ofNanos(System.nanoTime() - startedAt));
        ManifestClient.Fetched signatureText = client.get(prefix + ".sig", remaining);
        if (!signatureText.ok()) {
            return record(failureOf(signatureText), signatureText.exceptionClassName());
        }
        byte[] signature = decodeSignature(signatureText.body());
        UpdateChannel row = store.load();
        PublicKey signer = signature == null ? null : verifier.verify(manifest.body(), signature, trustedKeys(row));
        if (signer == null) {
            return record(UpdateCheckOutcome.INVALID_SIGNATURE, null); // before any parse
        }
        Manifest parsed;
        try {
            parsed = parser.parse(manifest.body());
        } catch (IllegalArgumentException e) {
            return record(UpdateCheckOutcome.MALFORMED, null);
        }
        UpdateCheckOutcome rejected = parser.accept(parsed, channel, running);
        if (rejected != null) {
            return record(rejected, null);
        }
        String rotateTo = null;
        if (parsed.trustedKeys() != null) {
            List<PublicKey> next = SigningKeyLoader.parseTokens(parsed.trustedKeys());
            if (!SigningKeyLoader.contains(next, signer)) {
                return record(UpdateCheckOutcome.MALFORMED, null); // a set that retires its own signer is a mistake, never applied
            }
            if (row.trustedKeysVersion == null
                    || SemanticVersion.parse(parsed.version()).compareTo(SemanticVersion.parse(row.trustedKeysVersion)) > 0) {
                rotateTo = SigningKeyLoader.tokens(next);
            }
        }
        store.recordCheck(UpdateCheckOutcome.UPDATE_AVAILABLE, parsed, manifest.body(), signature, clock.instant(), rotateTo);
        return UpdateCheckOutcome.UPDATE_AVAILABLE;
    }

    /**
     * The effective trust set (review A-1): the persisted, rotated set when
     * the manifest that introduced it is NEWER than the running image
     * (a rolled-back older image must not re-trust a retired key); the
     * image's own set otherwise (a hand-installed newer image carries its
     * own). An unreadable persisted set falls back to the image's — it was
     * written from a verified manifest, so this is defensive only.
     */
    List<PublicKey> trustedKeys(UpdateChannel row) {
        if (row.trustedKeys != null && row.trustedKeysVersion != null) {
            try {
                if (SemanticVersion.parse(row.trustedKeysVersion).compareTo(running) > 0) {
                    return SigningKeyLoader.parseTokens(row.trustedKeys);
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("Persisted trusted key set unreadable, using the image's set");
            }
        }
        return keys.keys();
    }

    private UpdateCheckOutcome record(UpdateCheckOutcome outcome, String exceptionClassName) {
        store.recordCheck(outcome, null, null, null, clock.instant(), null);
        if (outcome != UpdateCheckOutcome.NO_UPDATE && outcome != UpdateCheckOutcome.NOT_CONFIGURED) {
            if (exceptionClassName == null) {
                LOG.warnf("Update check failed: %s", outcome.toString());
            } else {
                LOG.warnf("Update check failed: %s (%s)", outcome.toString(), exceptionClassName);
            }
        }
        return outcome;
    }

    private static UpdateCheckOutcome failureOf(ManifestClient.Fetched fetched) {
        return switch (fetched.kind()) {
            case HTTP_ERROR -> UpdateCheckOutcome.HTTP_ERROR;
            case TOO_LARGE -> UpdateCheckOutcome.TOO_LARGE;
            case UNREACHABLE -> UpdateCheckOutcome.UNREACHABLE;
        };
    }

    /** The .sig body is base64 of exactly 64 raw bytes (D4); anything else is "not a signature". */
    private static byte[] decodeSignature(byte[] body) {
        try {
            String text = new String(body, java.nio.charset.StandardCharsets.US_ASCII).strip();
            if (!ExchangeFiles.BASE64.matcher(text).matches()) {
                return null;
            }
            byte[] raw = Base64.getDecoder().decode(text);
            return raw.length == ManifestVerifier.SIGNATURE_LENGTH ? raw : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ helpers

    private static ExchangeFiles.DesiredUpdate desired(UpdateChannel row, Instant requestedAt, String requestedBy,
            Instant applyNotBefore) {
        return new ExchangeFiles.DesiredUpdate(row.availableVersion, row.availableArtifactRef, row.availableSha256,
                requestedAt, requestedBy, applyNotBefore, row.availableManifest, row.availableSignature);
    }

    /** The next {@code applyAt} in the property's zone strictly after {@code now}. */
    private Instant nextOccurrence(UpdateChannel row, Instant now) {
        ZoneId zone = propertyZone();
        ZonedDateTime today = ZonedDateTime.ofInstant(now, zone);
        ZonedDateTime candidate = today.with(row.applyAt);
        if (!candidate.toInstant().isAfter(now)) {
            candidate = LocalDate.from(today).plusDays(1).atTime(row.applyAt).atZone(zone);
        }
        return candidate.toInstant();
    }

    /** Validated on write by PropertyControl; the UTC fallback is defensive only. */
    private ZoneId propertyZone() {
        try {
            return ZoneId.of(propertyControl.get().getTimezone());
        } catch (DateTimeException e) {
            LOG.warnf("Property timezone unusable, using UTC: %s", e.getClass().getName());
            return ZoneOffset.UTC;
        }
    }

    // ------------------------------------------------------------ test seams (package-private, %test only)

    void useClock(Clock clock) {
        this.clock = clock;
    }

    void useBaseUrl(String baseUrl, boolean allowPlainHttp) {
        this.baseUrl = baseUrl;
        this.allowPlainHttp = allowPlainHttp;
    }
}
