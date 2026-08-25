package de.gxp.device;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.gxp.api.ApiErrors;
import de.gxp.api.model.ConfirmPairingRequest;
import de.gxp.api.model.DeviceDto;
import de.gxp.api.model.DeviceUpdateRequest;
import de.gxp.api.model.PairingCompleted;
import de.gxp.api.model.PairingRequestCreated;
import de.gxp.room.RoomControl;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Control — the package's ONE public type: pairing lifecycle, device
 * CRUD/revoke and token verification for the auth package (REQ-403..407,
 * REQ-409, REQ-413). One package on purpose: the claim is a cross-entity
 * transaction (read {@link PairingRequest}, write {@link Device}, mark
 * claimed) — two packages would force a Control cycle. Two Boundaries sit on
 * top ({@code pairing} kiosk-facing, {@code manager-devices} admin-facing) —
 * the approved two-auth-surface exception (resolved decision #5, 2026-08-19).
 *
 * <p>Security-relevant throughout (constitution principle 4): every pairing
 * miss — unknown, expired, claimed, wrong secret — answers ONE identical
 * 404 (no oracle); tokens and poll secrets are stored as SHA-256 hex only
 * and never logged; device DTOs carry no token material.
 */
@ApplicationScoped
public class DeviceControl {

    /** What auth needs to build a device identity — never token material. */
    public record DeviceIdentity(long deviceId, long roomId) {
    }

    static final Duration PAIRING_TTL = Duration.ofMinutes(10); // pinned (spec §6 "short-lived")
    static final Duration LAST_SEEN_INTERVAL = Duration.ofSeconds(60); // pinned (REQ-413)

    private final DeviceRepository deviceRepository;
    private final PairingRequestRepository pairingRequestRepository;
    private final PairingRateLimiter rateLimiter;
    private final RoomControl roomControl;
    private final String caPemPath;
    private final Clock clock;

    /** Throttles the per-device last-seen write (REQ-413) — in-memory, like the token store. */
    private final ConcurrentHashMap<Long, Instant> lastSeenWriteByDeviceId = new ConcurrentHashMap<>();

    @Inject
    DeviceControl(DeviceRepository deviceRepository, PairingRequestRepository pairingRequestRepository,
            PairingRateLimiter rateLimiter, RoomControl roomControl,
            @ConfigProperty(name = "gxp.tls.ca-pem-path", defaultValue = "/tls/ca.pem") String caPemPath) {
        this(deviceRepository, pairingRequestRepository, rateLimiter, roomControl, caPemPath, Clock.systemUTC());
    }

    DeviceControl(DeviceRepository deviceRepository, PairingRequestRepository pairingRequestRepository,
            PairingRateLimiter rateLimiter, RoomControl roomControl, String caPemPath, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.pairingRequestRepository = pairingRequestRepository;
        this.rateLimiter = rateLimiter;
        this.roomControl = roomControl;
        this.caPemPath = caPemPath;
        this.clock = clock;
    }

    // ------------------------------------------------------------ pairing (kiosk)

    /**
     * Creates a pairing request (REQ-403): rate-limited, deletes expired rows
     * first (opportunistic cleanup), returns display code + RAW poll secret +
     * expiry — the secret is hashed at rest, this response is the only place
     * the raw value ever appears.
     */
    @PermitAll
    @Transactional
    public PairingRequestCreated createPairingRequest() {
        rateLimiter.acquire();
        Instant now = clock.instant();
        pairingRequestRepository.deleteExpired(now);
        String code = PairingCodes.newDisplayCode();
        while (pairingRequestRepository.byCode(code) != null) { // unique among live requests
            code = PairingCodes.newDisplayCode();
        }
        String pollSecret = PairingCodes.newSecret();
        PairingRequest request = new PairingRequest();
        request.code = code;
        request.pollSecretHash = PairingCodes.sha256Hex(pollSecret);
        request.createdAt = now;
        request.expiresAt = now.plus(PAIRING_TTL);
        pairingRequestRepository.persist(request);
        return new PairingRequestCreated()
                .code(code)
                .pollSecret(pollSecret)
                .expiresAt(request.expiresAt.atOffset(java.time.ZoneOffset.UTC));
    }

    /**
     * The kiosk's poll (REQ-404/406): pending → thrown 202; confirmed → mint
     * the 256-bit token, insert the {@link Device} row (hash only), claim the
     * request ATOMICALLY (two racing polls yield exactly one 200) and return
     * the raw token — exactly once. Every miss (unknown, expired, claimed,
     * wrong secret) is the one identical 404.
     */
    @PermitAll
    @Transactional
    public PairingCompleted poll(String code, String pollSecret) {
        Instant now = clock.instant();
        PairingRequest request = pairingRequestRepository.byCode(code);
        if (request == null || !request.expiresAt.isAfter(now) || request.claimedAt != null
                || !hashMatches(pollSecret, request.pollSecretHash)) {
            throw pairingNotFound();
        }
        if (request.confirmedAt == null) {
            throw new WebApplicationException(Response.status(202).build()); // pending — empty body
        }
        if (!pairingRequestRepository.claimOnce(request.id, now)) {
            throw pairingNotFound(); // the other racing poll won
        }
        String deviceToken = PairingCodes.newSecret();
        Device device = new Device();
        device.roomId = request.roomId;
        device.name = request.deviceName;
        device.tokenHash = PairingCodes.sha256Hex(deviceToken);
        device.createdAt = now;
        deviceRepository.persist(device);
        return new PairingCompleted()
                .deviceToken(deviceToken)
                .deviceName(device.name)
                .roomCode(deviceRepository.roomCodeOf(device.roomId));
    }

    /**
     * The instance CA certificate, PEM (REQ-429 — public material for kiosk
     * provisioning). Without TLS material ({@code %dev}/{@code %test}) → a
     * clearly coded 404.
     */
    @PermitAll
    public String caPem() {
        Path path = Path.of(caPemPath);
        if (!Files.isReadable(path)) {
            throw ApiErrors.notFound("ca-not-available", "This instance has no TLS material");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiErrors.notFound("ca-not-available", "This instance has no TLS material");
        }
    }

    // ------------------------------------------------------------ pairing (manager)

    /**
     * Manager confirm (REQ-405): assigns the pending request to an existing
     * room. Room existence answers 400 via {@link RoomControl#lockForStayWrite}
     * — which also holds the room row until commit, so a concurrent room
     * delete cannot slip between the check and the confirm. Default device
     * name: {@code Kiosk <room code>}.
     */
    @RolesAllowed("admin")
    @Transactional
    public void confirmPairing(String code, ConfirmPairingRequest request) {
        Instant now = clock.instant();
        PairingRequest pairing = pairingRequestRepository.byCode(code);
        if (pairing == null || !pairing.expiresAt.isAfter(now)) {
            throw ApiErrors.notFound("pairing-not-found", "No such pairing request");
        }
        if (pairing.confirmedAt != null) {
            throw ApiErrors.conflict("pairing-already-confirmed", "The pairing request is already confirmed");
        }
        roomControl.lockForStayWrite(request.getRoomId()); // 400 unknown-room + row held until commit
        pairing.roomId = request.getRoomId();
        pairing.deviceName = request.getDeviceName() == null || request.getDeviceName().isBlank()
                ? "Kiosk " + deviceRepository.roomCodeOf(request.getRoomId())
                : request.getDeviceName();
        pairing.confirmedAt = now;
    }

    // ------------------------------------------------------------ devices (manager)

    @RolesAllowed("admin")
    @Transactional
    public List<DeviceDto> listDevices() {
        return deviceRepository.allOrdered().stream()
                .map(device -> DeviceMapper.toDto(device, deviceRepository.roomCodeOf(device.roomId)))
                .toList();
    }

    @RolesAllowed("admin")
    @Transactional
    public DeviceDto renameDevice(Long deviceId, DeviceUpdateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw ApiErrors.badRequest("invalid-name", "The device name must not be blank");
        }
        Device device = require(deviceId);
        device.name = request.getName();
        return DeviceMapper.toDto(device, deviceRepository.roomCodeOf(device.roomId));
    }

    /** Soft revoke (REQ-407/412): idempotent; the token is dead on the very next request. */
    @RolesAllowed("admin")
    @Transactional
    public void revokeDevice(Long deviceId) {
        Device device = require(deviceId);
        if (device.revokedAt == null) {
            device.revokedAt = clock.instant();
        }
    }

    // ------------------------------------------------------------ auth-internal

    /**
     * Token verification for {@code de.gxp.auth} (REQ-410/412/413) — invoked
     * DURING authentication, pre-identity, hence {@code @PermitAll}. Reads
     * the row on every request (instant revocation beats caching a
     * credential check); the {@code last_seen_at} WRITE is throttled to one
     * per device per {@link #LAST_SEEN_INTERVAL}. Returns {@code null} for
     * unknown or revoked — the caller answers the generic 401 + challenge.
     */
    @PermitAll
    @Transactional
    public DeviceIdentity verifyToken(String tokenSha256Hex) {
        Device device = deviceRepository.byTokenHash(tokenSha256Hex);
        if (device == null || device.revokedAt != null) {
            return null;
        }
        Instant now = clock.instant();
        lastSeenWriteByDeviceId.compute(device.id, (id, lastWrite) -> {
            if (lastWrite == null || !lastWrite.plus(LAST_SEEN_INTERVAL).isAfter(now)) {
                device.lastSeenAt = now; // managed entity — flushed with the transaction
                return now;
            }
            return lastWrite;
        });
        return new DeviceIdentity(device.id, device.roomId);
    }

    // ------------------------------------------------------------ internals

    /** Test hook (same package): clears the in-memory last-seen throttle. */
    void resetLastSeenThrottle() {
        lastSeenWriteByDeviceId.clear();
    }

    private Device require(Long deviceId) {
        Device device = deviceRepository.findById(deviceId);
        if (device == null) {
            throw ApiErrors.notFound("unknown-device", "No device " + deviceId);
        }
        return device;
    }

    /** Constant-time comparison of the presented secret against the stored hash. */
    private static boolean hashMatches(String presentedSecret, String storedSha256Hex) {
        byte[] presented = PairingCodes.sha256Hex(presentedSecret).getBytes(StandardCharsets.UTF_8);
        byte[] stored = storedSha256Hex.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, stored);
    }

    /** ONE indistinguishable miss (REQ-404): unknown = expired = claimed = wrong secret. */
    private static WebApplicationException pairingNotFound() {
        return ApiErrors.notFound("pairing-not-found", "No such pairing request");
    }
}
