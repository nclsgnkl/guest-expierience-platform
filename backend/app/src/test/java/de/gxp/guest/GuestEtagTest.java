package de.gxp.guest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.DeviceFixtures;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * The version stamp's three properties (REQ-946, REQ-947), on the ONE guest
 * content resource.
 *
 * <p><b>REQ-946 is asserted at the SEAM, not by timing</b>: the 304 must be
 * answerable without resolving the payload at all. Proven by making the
 * payload genuinely unresolvable while leaving the stamp untouched — a native
 * SQL write writes no audit row — and requiring the 304 anyway, with the
 * unconditional poll failing in the same state as the control.
 *
 * <p><b>REQ-947 is the sharp edge</b>: the stamp must move for everything
 * that changes what THIS kiosk sees, not only for content writes. Its named
 * failure modes get a case each — a check-in with no write anywhere, two
 * rooms that must never share a stamp, and the guest's own preference write,
 * which is deliberately un-audited and so rests on the stay fingerprint alone.
 */
@QuarkusTest
class GuestEtagTest {

    /**
     * Mirrors {@code GuestContentVersion.STAMP_REFRESH_SECONDS} (package-
     * private, one package down). The stamp deliberately changes at least
     * this often (02-design.md §2.3), so a poll PAIR that straddles a bucket
     * boundary answers 200 where REQ-946's criterion says 304 — roughly one
     * run in ten if ignored. {@link #startInsideAFreshBucket()} removes that
     * without weakening a single assertion, and without touching the term:
     * deleting the bucket would re-open an unbounded stale 304.
     */
    private static final long STAMP_REFRESH_SECONDS = 300;

    /** Comfortably longer than any case below takes, on this box. */
    private static final long HEADROOM_SECONDS = 30;

    @Inject
    EntityManager entityManager;

    private long roomId;
    private String deviceToken;
    private String adminToken;

    @BeforeEach
    void seed() {
        roomId = ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Etag-Kiosk");
        adminToken = ManagerFixtures.login();
        startInsideAFreshBucket();
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        renameSeedRoom("Zimmer 1");
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from stay where room_id = :id")
                    .setParameter("id", roomId).executeUpdate();
            entityManager.createNativeQuery("update property set timezone = 'Europe/Berlin' where id = 1")
                    .executeUpdate();
        });
    }

    /** Waits out the tail of the current refresh bucket when one is imminent. */
    private static void startInsideAFreshBucket() {
        long remaining = STAMP_REFRESH_SECONDS - (Instant.now().getEpochSecond() % STAMP_REFRESH_SECONDS);
        if (remaining >= HEADROOM_SECONDS) {
            return;
        }
        try {
            Thread.sleep((remaining + 1) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void renameSeedRoom(String displayName) {
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("code", "1", "displayName", displayName))
                .when().put("/api/manager/rooms/" + roomId)
                .then().statusCode(200);
    }

    private Response content(String ifNoneMatch) {
        return content(deviceToken, ifNoneMatch);
    }

    private static Response content(String token, String ifNoneMatch) {
        var request = given().header("Authorization", "Bearer " + token);
        if (ifNoneMatch != null) {
            request = request.header("If-None-Match", ifNoneMatch);
        }
        return request.when().get("/api/guest/content").then().extract().response();
    }

    @Test
    void etagRoundTripReplayAndInvalidationByManagerEdit() {
        Response first = content(null);
        assertEquals(200, first.statusCode());
        String etag = first.header("ETag");
        assertTrue(etag != null && etag.matches("^\"[0-9a-f]{64}\"$"), "strong sha-256 ETag: " + etag);
        assertEquals("no-cache", first.header("Cache-Control"));

        // steady-state poll: 304, EMPTY body
        Response replay = content(etag);
        assertEquals(304, replay.statusCode());
        assertEquals("", replay.asString());
        assertEquals(etag, replay.header("ETag"));

        // a manager edit changes the content — and with it the ETag
        renameSeedRoom("Zimmer Eins");
        Response afterEdit = content(etag);
        assertEquals(200, afterEdit.statusCode());
        assertNotEquals(etag, afterEdit.header("ETag"));
        assertEquals("Zimmer Eins", afterEdit.path("room.displayName"));
    }

    @Test
    void theNotModifiedAnswerResolvesNothing() {
        // REQ-946's seam. 1) a normal poll
        String etag = content(null).header("ETag");

        // 2) break the payload WITHOUT moving the stamp. Native SQL reaches
        // past the layering on purpose (a test's job) and, crucially, writes
        // no audit row — so the stamp is unchanged by construction. Every
        // resolve path calls ZoneId.of(property.timezone), standby texts
        // included, so nothing in this payload can be composed any more.
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("update property set timezone = 'Not/AZone' where id = 1")
                .executeUpdate());
        try {
            // 3) the conditional poll still answers 304 — it cannot have
            // resolved, let alone serialized, anything
            Response conditional = content(etag);
            assertEquals(304, conditional.statusCode());
            assertEquals("", conditional.asString());

            // 4) the control: the same request WITHOUT If-None-Match fails,
            // proving step 3's payload was genuinely unresolvable rather than
            // merely cheap
            assertTrue(content(null).statusCode() >= 500,
                    "the unconditional poll must fail while the payload is unresolvable");
        } finally {
            // 5) restore (the @AfterEach repeats this — a failure here must
            // not poison the shared database for the next class)
            QuarkusTransaction.requiringNew().run(() -> entityManager
                    .createNativeQuery("update property set timezone = 'Europe/Berlin' where id = 1")
                    .executeUpdate());
        }
    }

    @Test
    void aCheckInWithNoWriteAnywhereInvalidatesTheStamp() {
        // REQ-947, failure mode one. The room is vacant; the kiosk holds its
        // ETag.
        Response vacant = content(null);
        assertEquals(200, vacant.statusCode());
        assertEquals("VACANT", vacant.path("occupancy"));
        String vacantEtag = vacant.header("ETag");

        // A stay whose check-in has already elapsed appears with NO audit row,
        // no restart and no manager request — the clock alone moves the room
        // into OCCUPIED. A stamp that tracked only content writes would answer
        // 304 here and leave the guest looking at the standby screen.
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("insert into stay (room_id, display_name, check_in, check_out, status)"
                        + " values (:room, 'Späte Anreise', :in, :out, 'BOOKED')")
                .setParameter("room", roomId)
                .setParameter("in", OffsetDateTime.now().minusMinutes(5))
                .setParameter("out", OffsetDateTime.now().plusDays(2))
                .executeUpdate());

        Response afterCheckIn = content(vacantEtag);
        assertEquals(200, afterCheckIn.statusCode(), "the check-in must invalidate the held ETag");
        assertEquals("OCCUPIED", afterCheckIn.path("occupancy"));
        assertEquals("Späte Anreise", afterCheckIn.path("guestDisplayName"));
        assertNotEquals(vacantEtag, afterCheckIn.header("ETag"));
    }

    @Test
    void twoRoomsNeverShareAStamp() {
        // REQ-947, failure mode two. Both rooms are vacant and inherit the
        // same property-scoped content, so everything the two payloads carry
        // apart from the room's own identity is identical.
        int otherRoomId = given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("code", "ETAG-B", "displayName", "Zimmer B"))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
        String otherToken = DeviceFixtures.seedDevice(entityManager, otherRoomId, "Etag-Kiosk-B");
        try {
            Response a = content(null);
            Response b = content(otherToken, null);
            assertEquals(200, a.statusCode());
            assertEquals(200, b.statusCode());
            assertNotEquals(a.header("ETag"), b.header("ETag"), "the room must be part of the stamp");

            // and the behaviour that guards: room B must not be able to
            // satisfy its poll with room A's tag
            Response crossed = content(otherToken, a.header("ETag"));
            assertEquals(200, crossed.statusCode());
            assertEquals("ETAG-B", crossed.path("room.code"));
        } finally {
            DeviceFixtures.deleteDevice(entityManager, otherToken);
            given().header("Authorization", "Bearer " + adminToken)
                    .when().delete("/api/manager/rooms/" + otherRoomId)
                    .then().statusCode(204);
        }
    }

    @Test
    void theGuestsOwnPreferenceWriteInvalidatesTheStamp() {
        // REQ-947's third term, and the one no other case reaches. The write is
        // deliberately NOT a manager mutation (AuditCoverageTest's
        // NOT_A_MANAGER_MUTATION holds it), so max(audit_entry.id) does not move:
        // only the stay fingerprint's language/appearance fields do. Delete them
        // from GuestContentVersion.fingerprintOf and this is the case that fails.
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("insert into stay (room_id, display_name, check_in, check_out, status)"
                        + " values (:room, 'Anna', :in, :out, 'BOOKED')")
                .setParameter("room", roomId)
                .setParameter("in", OffsetDateTime.now().minusHours(1))
                .setParameter("out", OffsetDateTime.now().plusDays(1))
                .executeUpdate());
        String etag = content(null).header("ETag");

        given().header("Authorization", "Bearer " + deviceToken)
                .contentType("application/json")
                .body(Map.of("language", "en", "appearance", "dark"))
                .when().put("/api/guest/preferences")
                .then().statusCode(204);

        Response after = content(etag);
        assertEquals(200, after.statusCode(), "the guest's own write must invalidate its held ETag");
        assertEquals("en", after.path("preference.language"));
        assertNotEquals(etag, after.header("ETag"));
    }
}
