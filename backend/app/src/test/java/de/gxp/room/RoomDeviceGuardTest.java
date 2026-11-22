package de.gxp.room;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.DeviceFixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import de.gxp.infrastructure.testsupport.ManagerFixtures;

/**
 * T-21 (REQ-408): deleting a room with an ACTIVE device is a 409 NAMING it —
 * manager work is never silently destroyed; after revoking, the delete
 * succeeds and the revoked device rows are gone with the room.
 */
@QuarkusTest
class RoomDeviceGuardTest {

    @Inject
    EntityManager entityManager;

    private static String adminToken() {
        return ManagerFixtures.login();
    }

    @Test
    void activeDeviceBlocksTheDeleteRevokedGoesWithTheRoom() {
        String token = adminToken();
        int roomId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "GUARD-1", "displayName", "Guard-Zimmer"))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
        String deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Guard-Kiosk");
        long deviceId = DeviceFixtures.deviceIdOf(entityManager, deviceToken);

        // active device → 409 naming it
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/rooms/" + roomId)
                .then().statusCode(409)
                .body("code", equalTo("room-has-devices"))
                .body("details[0].message", containsString("Guard-Kiosk"))
                .body("details[0].reference", equalTo(String.valueOf(deviceId)));

        // revoke, then the delete succeeds and takes the revoked row with it
        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/devices/" + deviceId + "/revoke")
                .then().statusCode(204);
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/rooms/" + roomId)
                .then().statusCode(204);
        Number remaining = (Number) entityManager
                .createNativeQuery("select count(*) from device where id = :id")
                .setParameter("id", deviceId).getSingleResult();
        assertEquals(0L, remaining.longValue(), "revoked device rows are deleted with the room");
    }
}
