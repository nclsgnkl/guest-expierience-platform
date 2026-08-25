package de.gxp.guest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.DeviceFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * T-23 (REQ-423): every guest JSON response carries a strong ETag; a
 * matching If-None-Match yields an EMPTY 304 (steady-state polling is
 * 304s); a manager edit changes the ETag — the tag is content-derived.
 */
@QuarkusTest
class GuestEtagTest {

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
        adminToken = given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200).extract().path("token");
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        renameSeedRoom("Zimmer 1");
    }

    private void renameSeedRoom(String displayName) {
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("code", "1", "displayName", displayName))
                .when().put("/api/manager/rooms/" + roomId)
                .then().statusCode(200);
    }

    private Response session(String ifNoneMatch) {
        var request = given().header("Authorization", "Bearer " + deviceToken);
        if (ifNoneMatch != null) {
            request = request.header("If-None-Match", ifNoneMatch);
        }
        return request.when().get("/api/guest/session").then().extract().response();
    }

    @Test
    void etagRoundTripReplayAndInvalidationByManagerEdit() {
        Response first = session(null);
        assertEquals(200, first.statusCode());
        String etag = first.header("ETag");
        assertTrue(etag != null && etag.matches("^\"[0-9a-f]{64}\"$"), "strong sha-256 ETag: " + etag);
        assertEquals("no-cache", first.header("Cache-Control"));

        // steady-state poll: 304, EMPTY body
        Response replay = session(etag);
        assertEquals(304, replay.statusCode());
        assertEquals("", replay.asString());
        assertEquals(etag, replay.header("ETag"));

        // a manager edit changes the content — and with it the ETag
        renameSeedRoom("Zimmer Eins");
        Response afterEdit = session(etag);
        assertEquals(200, afterEdit.statusCode());
        assertNotEquals(etag, afterEdit.header("ETag"));
        assertEquals("Zimmer Eins", afterEdit.path("room.displayName"));
    }
}
