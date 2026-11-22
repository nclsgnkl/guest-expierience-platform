package de.gxp.device.control;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import de.gxp.infrastructure.testsupport.ManagerFixtures;

/**
 * T-20 (REQ-403..407, REQ-409): the full pairing lifecycle over the real
 * contract paths — create (201, code shape, TTL), pending 202, confirm
 * (204/404/409/400), claim EXACTLY once (incl. two racing polls), the
 * indistinguishable-404 matrix (status AND body equality), opportunistic
 * cleanup, hash-only storage, token-free DTOs. Created devices are removed
 * again — tests share one database. Creation carries no throttle of any kind
 * since REQ-409 was amended 2026-08-24, so there is no shared budget to reset
 * between tests — that absence is itself part of the fix.
 */
@QuarkusTest
class PairingFlowTest {

    @Inject
    EntityManager entityManager;

    private static String adminToken() {
        return ManagerFixtures.login();
    }

    private long seededRoomId() {
        return ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
    }

    private static Response createPairing() {
        return given().when().post("/api/pairing/requests")
                .then().statusCode(201)
                .body("code", matchesPattern("^[2-9A-HJ-NP-Z]{8}$")) // no 0/O/1/I
                .body("pollSecret", notNullValue())
                .body("expiresAt", notNullValue())
                .extract().response();
    }

    private static Response poll(String code, String secret) {
        return given().header("GXP-Pairing-Secret", secret)
                .when().get("/api/pairing/requests/" + code)
                .then().extract().response();
    }

    private static void confirm(String token, String code, long roomId) {
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("roomId", roomId))
                .when().post("/api/manager/pairings/" + code + "/confirm")
                .then().statusCode(204);
    }

    private void forceExpire(String code) {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("update pairing_request set expires_at = now() - interval '1 minute'"
                        + " where code = :code")
                .setParameter("code", code).executeUpdate());
    }

    private void deleteDevice(long deviceId) {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from device where id = :id")
                .setParameter("id", deviceId).executeUpdate());
    }

    private long lastDeviceId() {
        return ((Number) entityManager
                .createNativeQuery("select max(id) from device")
                .getSingleResult()).longValue();
    }

    @Test
    void pollBeforeConfirmIsPendingWith202() {
        Response created = createPairing();
        Response pending = poll(created.path("code"), created.path("pollSecret"));
        assertEquals(202, pending.statusCode());
        assertTrue(pending.asString().isEmpty(), "202 must carry no body");
    }

    @Test
    void confirmThenClaimYieldsTheTokenExactlyOnce() {
        Response created = createPairing();
        String code = created.path("code");
        String secret = created.path("pollSecret");
        confirm(adminToken(), code, seededRoomId());

        Response claimed = poll(code, secret);
        assertEquals(200, claimed.statusCode());
        String deviceToken = claimed.path("deviceToken");
        assertEquals("1", claimed.path("roomCode"));
        assertEquals("Kiosk 1", claimed.path("deviceName")); // default name (REQ-405)
        assertTrue(deviceToken != null && deviceToken.length() >= 43, "256-bit base64url token");

        // the SECOND poll — correct secret and all — is the indistinguishable 404
        Response second = poll(code, secret);
        assertEquals(404, second.statusCode());
        assertEquals("pairing-not-found", second.path("code"));

        // hash-only storage (REQ-406): 64-hex, equals sha256(raw), never the raw token
        String storedHash = (String) entityManager
                .createNativeQuery("select token_hash from device where id = :id")
                .setParameter("id", lastDeviceId()).getSingleResult();
        assertTrue(storedHash.matches("^[0-9a-f]{64}$"), "stored as sha-256 hex");
        assertEquals(PairingCodes.sha256Hex(deviceToken), storedHash);
        assertNotEquals(deviceToken, storedHash);

        // DTOs never carry token material (REQ-407)
        String listBody = given().header("Authorization", "Bearer " + adminToken())
                .when().get("/api/manager/devices")
                .then().statusCode(200).extract().asString();
        assertFalse(listBody.contains(deviceToken), "raw token leaked into a DTO");
        assertFalse(listBody.contains(storedHash), "token hash leaked into a DTO");

        deleteDevice(lastDeviceId());
    }

    @Test
    void theFourMissesAreOneIndistinguishable404() {
        // wrong secret on a live request
        Response live = createPairing();
        Response wrongSecret = poll(live.path("code"), "definitely-not-the-secret");
        // unknown code (valid shape, never issued)
        Response unknown = poll("ZZZZZZZZ", "whatever");
        // expired request, correct secret
        Response expiring = createPairing();
        forceExpire(expiring.path("code"));
        Response expired = poll(expiring.path("code"), expiring.path("pollSecret"));
        // claimed request, correct secret
        Response toClaim = createPairing();
        confirm(adminToken(), toClaim.path("code"), seededRoomId());
        assertEquals(200, poll(toClaim.path("code"), toClaim.path("pollSecret")).statusCode());
        Response claimed = poll(toClaim.path("code"), toClaim.path("pollSecret"));

        for (Response miss : List.of(wrongSecret, unknown, expired, claimed)) {
            assertEquals(404, miss.statusCode());
        }
        // body EQUALITY, not just the code — no oracle distinguishing the four
        assertEquals(wrongSecret.asString(), unknown.asString());
        assertEquals(wrongSecret.asString(), expired.asString());
        assertEquals(wrongSecret.asString(), claimed.asString());

        deleteDevice(lastDeviceId());
    }

    @Test
    void twoRacingPollsYieldExactlyOne200() throws Exception {
        Response created = createPairing();
        String code = created.path("code");
        String secret = created.path("pollSecret");
        confirm(adminToken(), code, seededRoomId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> claim = () -> poll(code, secret).statusCode();
            List<Future<Integer>> results = executor.invokeAll(List.of(claim, claim));
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertTrue(statuses.contains(200), "one racing poll must win: " + statuses);
            assertTrue(statuses.contains(404), "the other racing poll must lose: " + statuses);
        } finally {
            executor.shutdownNow();
        }
        deleteDevice(lastDeviceId());
    }

    @Test
    void confirmErrorsAreNamed() {
        String token = adminToken();
        // unknown code → 404
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(Map.of("roomId", seededRoomId()))
                .when().post("/api/manager/pairings/ZZZZZZZZ/confirm")
                .then().statusCode(404).body("code", equalTo("pairing-not-found"));
        // unknown room → 400
        Response created = createPairing();
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(Map.of("roomId", 999999))
                .when().post("/api/manager/pairings/" + created.path("code") + "/confirm")
                .then().statusCode(400).body("code", equalTo("unknown-room"));
        // double confirm → 409
        confirm(token, created.path("code"), seededRoomId());
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(Map.of("roomId", seededRoomId()))
                .when().post("/api/manager/pairings/" + created.path("code") + "/confirm")
                .then().statusCode(409).body("code", equalTo("pairing-already-confirmed"));
    }

    /**
     * REQ-409 as amended 2026-08-24 (owner decision): pairing-request creation
     * is <b>deliberately not throttled</b> — no rate limit, no admission gate —
     * so this is the positive statement of that, the counterpart of
     * {@code AuthResourceTest}'s six-wrong-passwords test.
     *
     * <p>A concurrent flood must leave nothing behind: every request is
     * answered 201, none carries a {@code Retry-After}, and the kiosk arriving
     * afterwards is served. The removed global 5-per-60 s limiter failed all
     * three — it let anyone on the LAN, or a single clock-skewed kiosk retrying
     * by itself ({@code guest-frontend-integration} 06-review 🟡 #3), keep a
     * real kiosk out for the rest of the window.
     *
     * <p>What bounds live rows now is age, not count: the 10-minute TTL and the
     * opportunistic {@code deleteExpired} on each create (asserted by
     * {@link #expiredRequestsAreCleanedUpOnTheNextCreate}). Guessing stays
     * infeasible on entropy and TTL alone — 32⁸ ≈ 1.1 × 10¹² codes, 10 minutes.
     */
    @Test
    void aConcurrentFloodIsFullyServedAndArmsNothing() throws Exception {
        int floodSize = 12;
        List<Response> responses = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(floodSize);
        try {
            List<Callable<Response>> flood = new ArrayList<>();
            for (int i = 0; i < floodSize; i++) {
                flood.add(() -> given().when().post("/api/pairing/requests").then().extract().response());
            }
            for (Future<Response> result : executor.invokeAll(flood)) {
                responses.add(result.get());
            }
        } finally {
            executor.shutdownNow();
        }

        for (Response response : responses) {
            assertEquals(201, response.statusCode(), "creation is not throttled — every flood request is served");
            assertNull(response.header("Retry-After"), "nothing throttles, so nothing hints at a retry delay");
        }
        // and no window to sit out: the kiosk behind the flood is served too
        createPairing();
    }

    @Test
    void expiredRequestsAreCleanedUpOnTheNextCreate() {
        Response stale = createPairing();
        String staleCode = stale.path("code");
        forceExpire(staleCode);
        createPairing(); // opportunistic delete runs first (REQ-409)
        Number remaining = (Number) entityManager
                .createNativeQuery("select count(*) from pairing_request where code = :code")
                .setParameter("code", staleCode).getSingleResult();
        assertEquals(0L, remaining.longValue(), "expired row must be gone after the next create");
    }

    @Test
    void deviceAdminRoundTripRenameAndIdempotentRevoke() {
        Response created = createPairing();
        String token = adminToken();
        confirm(token, created.path("code"), seededRoomId());
        assertEquals(200, poll(created.path("code"), created.path("pollSecret")).statusCode());
        long deviceId = lastDeviceId();

        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(Map.of("name", "Flur-Tablet"))
                .when().put("/api/manager/devices/" + deviceId)
                .then().statusCode(200)
                .body("name", equalTo("Flur-Tablet"))
                .body("roomCode", equalTo("1"));
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(Map.of("name", " "))
                .when().put("/api/manager/devices/" + deviceId)
                .then().statusCode(400).body("code", equalTo("invalid-name"));

        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/devices/" + deviceId + "/revoke")
                .then().statusCode(204);
        // idempotent (REQ-407)
        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/devices/" + deviceId + "/revoke")
                .then().statusCode(204);
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/devices")
                .then().statusCode(200)
                .body("find { it.id == " + deviceId + " }.revokedAt", notNullValue());

        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/devices/999999/revoke")
                .then().statusCode(404).body("code", equalTo("unknown-device"));

        deleteDevice(deviceId);
    }
}
