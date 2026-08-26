package de.gxp.update.control;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.api.model.UpdateCheckOutcome;
import de.gxp.infrastructure.testsupport.DeviceFixtures;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.update.ManifestServer;
import de.gxp.update.TestSigningKeys;
import de.gxp.update.entity.UpdateChannel;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * update-channel T-10 (REQ-701…706, REQ-727): the check sequence end to end
 * against the suite's {@link ManifestServer}, with the row and the exchange
 * file inspected directly.
 */
@QuarkusTest
@QuarkusTestResource(ManifestServer.class)
class UpdateCheckTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);

    @Inject
    UpdateControl control;
    @Inject
    UpdateStateStore store;
    @Inject
    EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void reset() throws IOException {
        UpdateTestSupport.reset(entityManager, control);
    }

    @Test
    void aSignedNewerManifestIsAvailableStoredRawAndScheduledForTheNextApplyTime() throws IOException {
        ManifestServer.serveFixture();
        control.useClock(FIXED);
        assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, control.runCheck());

        UpdateChannel row = store.load();
        assertEquals("update-available", row.lastCheckOutcome);
        assertEquals(FIXED.instant(), row.lastCheckAt);
        assertEquals("1.0.1", row.availableVersion);
        assertEquals("ghcr.io/example/gxp-backend:1.0.1", row.availableArtifactRef);
        assertArrayEquals(ManifestServer.fixture("manifest.json"), row.availableManifest, "the verified bytes, never re-serialized");
        assertEquals(64, row.availableSignature.length);

        // 04:00 Europe/Berlin (the seeded property) on 2026-09-02 = 02:00Z; requestedAt = the fixed clock → the fixture, byte for byte
        assertArrayEquals(ManifestServer.fixture("desired-update.json"),
                Files.readAllBytes(UpdateTestSupport.EXCHANGE.resolve("desired-update.json")));
    }

    @Test
    void aManifestSignedWithAnotherKeyIsInvalidSignatureAndChangesNothing() throws Exception {
        byte[] manifest = ManifestServer.fixture("manifest.json");
        java.security.KeyPair other = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ManifestServer.serve(manifest, Base64.getEncoder().encode(TestSigningKeys.sign(manifest, other.getPrivate())));
        assertEquals(UpdateCheckOutcome.INVALID_SIGNATURE, control.runCheck());
        UpdateChannel row = store.load();
        assertEquals("invalid-signature", row.lastCheckOutcome);
        assertNull(row.availableVersion);
        assertNull(row.availableManifest);
        assertFalse(Files.exists(UpdateTestSupport.EXCHANGE.resolve("desired-update.json")));
    }

    @Test
    void verificationPrecedesParsingSoGarbageWithABadSignatureIsInvalidSignatureNotMalformed() {
        ManifestServer.serve("{not json".getBytes(StandardCharsets.UTF_8), "AAAA".getBytes(StandardCharsets.US_ASCII));
        assertEquals(UpdateCheckOutcome.INVALID_SIGNATURE, control.runCheck());
        // and a VALIDLY signed garbage body is what MALFORMED means
        byte[] garbage = "{not json".getBytes(StandardCharsets.UTF_8);
        ManifestServer.serve(garbage, Base64.getEncoder().encode(TestSigningKeys.sign(garbage)));
        assertEquals(UpdateCheckOutcome.MALFORMED, control.runCheck());
    }

    @Test
    void aSignedManifestAtOrBelowTheRunningVersionIsNoUpdateAndClearsAnEarlierAvailable() throws IOException {
        ManifestServer.serveFixture();
        assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, control.runCheck());
        assertTrue(Files.exists(UpdateTestSupport.EXCHANGE.resolve("desired-update.json")));
        byte[] same = new String(ManifestServer.fixture("manifest.json"), StandardCharsets.UTF_8)
                .replace("\"1.0.1\"", "\"1.0.0-SNAPSHOT\"").replace(":1.0.1\"", ":1.0.0-SNAPSHOT\"").getBytes(StandardCharsets.UTF_8);
        ManifestServer.serve(same, Base64.getEncoder().encode(TestSigningKeys.sign(same)));
        assertEquals(UpdateCheckOutcome.NO_UPDATE, control.runCheck(), "REQ-727: equal version, validly signed");
        assertNull(store.load().availableVersion, "an update that stops being offered stops being available");
        assertFalse(Files.exists(UpdateTestSupport.EXCHANGE.resolve("desired-update.json")), "and its desired file is gone");
    }

    @Test
    void anUnsetBaseUrlIsNotConfiguredWithoutARequest() {
        int before = ManifestServer.hits();
        control.useBaseUrl(null, true);
        assertEquals(UpdateCheckOutcome.NOT_CONFIGURED, control.runCheck());
        assertEquals("not-configured", store.load().lastCheckOutcome);
        assertEquals(before, ManifestServer.hits());
    }

    @Test
    void plainHttpOutsideATestProfileIsInsecureBaseUrlWithoutARequest() {
        int before = ManifestServer.hits();
        control.useBaseUrl(ManifestServer.url(), false);
        assertEquals(UpdateCheckOutcome.INSECURE_BASE_URL, control.runCheck());
        assertEquals(before, ManifestServer.hits());
    }

    @Test
    void aMissingManifestIsHttpError() {
        ManifestServer.serveNothing();
        assertEquals(UpdateCheckOutcome.HTTP_ERROR, control.runCheck());
    }

    @Test
    void aClosedPortIsUnreachableFast() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        control.useBaseUrl("http://127.0.0.1:" + closedPort, true);
        Instant start = Instant.now();
        assertEquals(UpdateCheckOutcome.UNREACHABLE, control.runCheck());
        assertTrue(Duration.between(start, Instant.now()).compareTo(Duration.ofSeconds(5)) < 0);
        assertEquals("unreachable", store.load().lastCheckOutcome);
    }

    /**
     * The constitution's survival invariant (review R2-4): a check that is
     * genuinely IN FLIGHT — the listener accepts and never answers, so the
     * client sits on its 10 s request timeout — leaves the guest and manager
     * surfaces answering, and the check ends within the tick budget.
     */
    @Test
    void anInFlightCheckAgainstAListenerThatNeverAnswersLeavesGuestAndManagerRequestsUntouched() throws Exception {
        ServerSocket listener = new ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress());
        java.util.List<java.net.Socket> held = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        Thread acceptor = new Thread(() -> {
            try {
                while (true) {
                    held.add(listener.accept()); // accepted, then never read, never answered
                }
            } catch (IOException closed) {
                // the listener was closed at the end of the test
            }
        }, "never-answers");
        acceptor.setDaemon(true);
        acceptor.start();
        control.useBaseUrl("http://127.0.0.1:" + listener.getLocalPort(), true);
        String adminToken = ManagerFixtures.login();
        int roomId = given().header("Authorization", "Bearer " + adminToken).contentType("application/json")
                .body(Map.of("code", "T10-upd", "displayName", "Update"))
                .when().post("/api/manager/rooms").then().statusCode(201).extract().path("id");
        String deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Update-Kiosk");
        try {
            Instant start = Instant.now();
            CompletableFuture<UpdateCheckOutcome> check = CompletableFuture.supplyAsync(control::runCheck);
            given().header("Authorization", "Bearer " + deviceToken).when().get("/api/guest/session").then().statusCode(200);
            given().header("Authorization", "Bearer " + adminToken).when().get("/api/manager/property").then().statusCode(200);
            assertFalse(check.isDone(), "the check must still be in flight while the kiosk and the manager were served");
            assertEquals(UpdateCheckOutcome.UNREACHABLE, check.get());
            Duration took = Duration.between(start, Instant.now());
            assertTrue(took.compareTo(Duration.ofSeconds(15)) < 0, "took " + took);
            assertTrue(took.compareTo(Duration.ofSeconds(9)) > 0, "the request timeout, not a refused connection, ended it: " + took);
            assertEquals("unreachable", store.load().lastCheckOutcome);
        } finally {
            listener.close();
            for (java.net.Socket socket : held) {
                socket.close();
            }
            DeviceFixtures.deleteDevice(entityManager, deviceToken);
            given().header("Authorization", "Bearer " + adminToken).when().delete("/api/manager/rooms/" + roomId).then().statusCode(204);
        }
    }

    /** REQ-706 "≤ 15 s per tick" (review R2-2): the manifest and the .sig fetch share ONE budget — 8 s + 30 s of dripping ends at 15 s, not 23. */
    @Test
    void aServerThatDripsBothFilesCostsOneBudgetNotTwo() {
        ManifestServer.serveDripping(8, 30);
        Instant start = Instant.now();
        assertEquals(UpdateCheckOutcome.UNREACHABLE, control.runCheck());
        Duration took = Duration.between(start, Instant.now());
        assertTrue(took.compareTo(Duration.ofSeconds(14)) > 0, "took " + took);
        assertTrue(took.compareTo(Duration.ofSeconds(20)) < 0, "one budget across both fetches: " + took);
        assertEquals("unreachable", store.load().lastCheckOutcome);
    }

    // ------------------------------------------------------------ the two-key trust set and its rotation (review A-1, T-20a)

    /** The fixture manifest with {@code version} and, optionally, a {@code trustedKeys} line. */
    private static byte[] manifest(String version, String trustedKeys) throws IOException {
        String text = new String(ManifestServer.fixture("manifest.json"), StandardCharsets.UTF_8).replace("\"1.0.1\"", "\"" + version + "\"");
        if (trustedKeys != null) {
            text = text.replace("  \"publishedAt\": \"2026-09-01T10:00:00Z\"\n",
                    "  \"publishedAt\": \"2026-09-01T10:00:00Z\",\n  \"trustedKeys\": \"" + trustedKeys + "\"\n");
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void serveSignedBy(byte[] manifest, java.security.PrivateKey key) {
        ManifestServer.serve(manifest, Base64.getEncoder().encode(TestSigningKeys.sign(manifest, key)));
    }

    private static final String KEY1 = TestSigningKeys.token(TestSigningKeys.publicKey());
    private static final String KEY2 = TestSigningKeys.token(TestSigningKeys.publicKey2());

    @Test
    void aManifestSignedByTheDormantSecondKeyVerifies() throws IOException {
        serveSignedBy(manifest("1.0.1", null), TestSigningKeys.privateKey2());
        assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, control.runCheck());
        UpdateChannel row = store.load();
        assertEquals("1.0.1", row.availableVersion);
        assertNull(row.trustedKeys, "no trustedKeys line = no rotation");
    }

    @Test
    void aRotationSignedByKeyTwoRetiresKeyOneAndAReplayedOlderSetDoesNotRestoreIt() throws IOException {
        // 1.0.1 signed by key 2 introduces the set {key 2}: key 1 is retired
        serveSignedBy(manifest("1.0.1", KEY2), TestSigningKeys.privateKey2());
        assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, control.runCheck());
        UpdateChannel row = store.load();
        assertEquals(KEY2, row.trustedKeys);
        assertEquals("1.0.1", row.trustedKeysVersion);

        // the fixture (signed by key 1) is now refused, although the image still ships key 1
        ManifestServer.serveFixture();
        assertEquals(UpdateCheckOutcome.INVALID_SIGNATURE, control.runCheck());
        assertNull(store.load().availableVersion, "a refused manifest clears the offer");
        assertEquals(KEY2, store.load().trustedKeys, "a refusal never touches the set");

        // key 2 signs 1.0.2 with {key 2, key 1}: newer than the set's version → applied (a rotation is any signed newer set)
        serveSignedBy(manifest("1.0.2", KEY2 + "," + KEY1), TestSigningKeys.privateKey2());
        assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, control.runCheck());
        assertEquals(KEY2 + "," + KEY1, store.load().trustedKeys);
        assertEquals("1.0.2", store.load().trustedKeysVersion);

        // replaying the 1.0.1 {key 2} manifest is still a valid, available update — but its set is OLDER and stays ignored
        serveSignedBy(manifest("1.0.1", KEY2), TestSigningKeys.privateKey2());
        assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, control.runCheck());
        assertEquals(KEY2 + "," + KEY1, store.load().trustedKeys, "a set cannot be rolled back by replay");
        assertEquals("1.0.2", store.load().trustedKeysVersion);
    }

    @Test
    void aRotationMustBeSignedByAKeyOfTheCurrentSetAndMayNotRetireItsOwnSigner() throws Exception {
        java.security.KeyPair thief = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String thiefKey = TestSigningKeys.token(thief.getPublic());
        serveSignedBy(manifest("1.0.1", thiefKey), thief.getPrivate());
        assertEquals(UpdateCheckOutcome.INVALID_SIGNATURE, control.runCheck(), "a set introduced by an untrusted key is never read");
        assertNull(store.load().trustedKeys);

        // key 1 signs a set without key 1: refused as malformed, nothing rotated, nothing offered
        serveSignedBy(manifest("1.0.1", KEY2), TestSigningKeys.privateKey());
        assertEquals(UpdateCheckOutcome.MALFORMED, control.runCheck());
        assertNull(store.load().trustedKeys);
        assertNull(store.load().availableVersion);

        // a rotation below the running version is NO_UPDATE and rotates nothing (downgrade guard first)
        serveSignedBy(manifest("0.9.0", KEY2), TestSigningKeys.privateKey2());
        assertEquals(UpdateCheckOutcome.NO_UPDATE, control.runCheck());
        assertNull(store.load().trustedKeys);
    }

    @Test
    void aRolledBackImageWithTheOldResourceDoesNotReTrustARetiredKey() throws IOException {
        // the persisted set (written by 1.0.1, newer than the running 1.0.0-SNAPSHOT) wins over the image's {key 1, key 2}
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                "update update_channel set trusted_keys = :k, trusted_keys_version = '1.0.1'").setParameter("k", KEY2).executeUpdate());
        ManifestServer.serveFixture();
        assertEquals(UpdateCheckOutcome.INVALID_SIGNATURE, control.runCheck(), "key 1 stays retired on the rolled-back image");

        // …but a persisted set OLDER than the running image yields to the image's own set (a hand-installed newer release)
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                "update update_channel set trusted_keys = :k, trusted_keys_version = '0.0.1'").setParameter("k", KEY2).executeUpdate());
        ManifestServer.serveFixture();
        assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, control.runCheck(), "the image's set (key 1 included) applies");
    }
}
