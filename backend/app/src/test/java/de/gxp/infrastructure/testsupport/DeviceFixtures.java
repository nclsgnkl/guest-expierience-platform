package de.gxp.infrastructure.testsupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

/**
 * Test-only device seeding. Tests OUTSIDE {@code de.gxp.device} seed device
 * rows directly (hash-only, like the pairing claim would) instead of running
 * the HTTP pairing flow: the pairing lifecycle is proven once in
 * {@code PairingFlowTest}, and going through HTTP here would spend three round
 * trips and a pairing row per fixture for behaviour already proven there.
 */
public final class DeviceFixtures {

    private DeviceFixtures() {
    }

    /** Inserts an active device for the room and returns the RAW bearer token. */
    public static String seedDevice(EntityManager entityManager, long roomId, String name) {
        String rawToken = "test-device-token-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("insert into device (room_id, name, token_hash, created_at)"
                        + " values (:roomId, :name, :hash, now())")
                .setParameter("roomId", roomId)
                .setParameter("name", name)
                .setParameter("hash", sha256Hex(rawToken))
                .executeUpdate());
        return rawToken;
    }

    public static long deviceIdOf(EntityManager entityManager, String rawToken) {
        return ((Number) entityManager
                .createNativeQuery("select id from device where token_hash = :hash")
                .setParameter("hash", sha256Hex(rawToken))
                .getSingleResult()).longValue();
    }

    public static void deleteDevice(EntityManager entityManager, String rawToken) {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from device where token_hash = :hash")
                .setParameter("hash", sha256Hex(rawToken))
                .executeUpdate());
    }

    public static String sha256Hex(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
