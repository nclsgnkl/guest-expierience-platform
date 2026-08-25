package de.gxp.device.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * T-21, throttle component (REQ-413): consecutive verifications cost at most
 * ONE {@code last_seen_at} write — ETag polling must not write per request.
 * Lives in this package deliberately: the throttle reset hook is
 * package-private. Also proves the revoked/unknown verification misses.
 */
@QuarkusTest
class DeviceLastSeenTest {

    @Inject
    DeviceControl deviceControl;

    @Inject
    EntityManager entityManager;

    private Long deviceId;

    @AfterEach
    void cleanUp() {
        deviceControl.resetLastSeenThrottle();
        if (deviceId != null) {
            QuarkusTransaction.requiringNew().run(() -> entityManager
                    .createNativeQuery("delete from device where id = :id")
                    .setParameter("id", deviceId).executeUpdate());
            deviceId = null;
        }
    }

    private String seedDevice() {
        String rawToken = "test-token-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("insert into device (room_id, name, token_hash, created_at)"
                        + " select id, 'Throttle-Kiosk', :hash, now() from room where code = '1'")
                .setParameter("hash", PairingCodes.sha256Hex(rawToken)).executeUpdate());
        deviceId = ((Number) entityManager
                .createNativeQuery("select max(id) from device").getSingleResult()).longValue();
        return rawToken;
    }

    private Object lastSeenAt() {
        return entityManager
                .createNativeQuery("select last_seen_at from device where id = :id")
                .setParameter("id", deviceId).getSingleResult();
    }

    @Test
    void consecutiveVerificationsWriteLastSeenAtMostOnce() {
        String rawToken = seedDevice();
        String hash = PairingCodes.sha256Hex(rawToken);

        DeviceControl.DeviceIdentity first = deviceControl.verifyToken(hash);
        assertNotNull(first);
        Object afterFirst = lastSeenAt();
        assertNotNull(afterFirst, "the first verification writes last_seen_at");

        DeviceControl.DeviceIdentity second = deviceControl.verifyToken(hash);
        assertNotNull(second);
        assertEquals(first.deviceId(), second.deviceId());
        assertEquals(afterFirst, lastSeenAt(), "an immediate second verification must NOT write again");
    }

    @Test
    void unknownAndRevokedTokensVerifyToNull() {
        assertNull(deviceControl.verifyToken(PairingCodes.sha256Hex("never-issued")));
        String rawToken = seedDevice();
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("update device set revoked_at = now() where id = :id")
                .setParameter("id", deviceId).executeUpdate());
        assertNull(deviceControl.verifyToken(PairingCodes.sha256Hex(rawToken)),
                "a revoked device verifies to null on the very next check");
    }
}
