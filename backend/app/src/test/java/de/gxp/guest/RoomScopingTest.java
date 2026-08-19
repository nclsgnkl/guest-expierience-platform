package de.gxp.guest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.testsupport.DeviceFixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * T-21 (REQ-411, REQ-425): the two-devices/two-rooms matrix. Each device
 * sees ONLY its own room's stay name, preference override and room-scoped
 * menu config — and the contract carries no parameter to tamper with: the
 * room comes exclusively from the token. A revoked device receives nothing.
 */
@QuarkusTest
class RoomScopingTest {

    @Inject
    EntityManager entityManager;

    private String adminToken;
    private int roomA;
    private int roomB;
    private String deviceA;
    private String deviceB;

    @BeforeEach
    void seedTwoRoomsWithStaysAndDevices() {
        adminToken = given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200).extract().path("token");
        roomA = createRoom("SCOPE-A", "Zimmer A");
        roomB = createRoom("SCOPE-B", "Zimmer B");
        createStay(roomA, "Gast Alpha", "de");
        createStay(roomB, "Gast Beta", "en");
        // room-scoped config: room B opts out of smart-home
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "smart-home", "enabled", false)))
                .when().put("/api/manager/rooms/" + roomB + "/menu-config")
                .then().statusCode(200);
        deviceA = DeviceFixtures.seedDevice(entityManager, roomA, "Kiosk A");
        deviceB = DeviceFixtures.seedDevice(entityManager, roomB, "Kiosk B");
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceA);
        DeviceFixtures.deleteDevice(entityManager, deviceB);
        for (int roomId : new int[] { roomA, roomB }) {
            List<Integer> stayIds = given().header("Authorization", "Bearer " + adminToken)
                    .when().get("/api/manager/stays?roomId=" + roomId)
                    .then().statusCode(200).extract().path("id");
            for (Integer stayId : stayIds) {
                given().header("Authorization", "Bearer " + adminToken)
                        .when().delete("/api/manager/stays/" + stayId)
                        .then().statusCode(204);
            }
            given().header("Authorization", "Bearer " + adminToken)
                    .when().delete("/api/manager/rooms/" + roomId)
                    .then().statusCode(204);
        }
    }

    private int createRoom(String code, String displayName) {
        return given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("code", code, "displayName", displayName))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
    }

    private void createStay(long roomId, String displayName, String language) {
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", displayName,
                        "checkIn", OffsetDateTime.now().minusHours(1).toString(),
                        "checkOut", OffsetDateTime.now().plusHours(3).toString(),
                        "language", language, "status", "BOOKED"))
                .when().post("/api/manager/stays")
                .then().statusCode(201);
    }

    @Test
    void eachDeviceSeesExactlyItsOwnRoom() {
        given().header("Authorization", "Bearer " + deviceA)
                .when().get("/api/guest/session")
                .then().statusCode(200)
                .body("room.code", equalTo("SCOPE-A"))
                .body("guestDisplayName", equalTo("Gast Alpha"))
                .body("preference.language", equalTo("de"))
                .body("menu.items.key", hasItem("smart-home")); // room A inherits — enabled
        given().header("Authorization", "Bearer " + deviceB)
                .when().get("/api/guest/session")
                .then().statusCode(200)
                .body("room.code", equalTo("SCOPE-B"))
                .body("guestDisplayName", equalTo("Gast Beta"))
                .body("preference.language", equalTo("en"))
                .body("menu.items.key", not(hasItem("smart-home"))); // room B's own opt-out
    }

    @Test
    void aRevokedDeviceReceivesNothingAnywhere() {
        long deviceId = DeviceFixtures.deviceIdOf(entityManager, deviceA);
        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/api/manager/devices/" + deviceId + "/revoke")
                .then().statusCode(204);
        for (String path : List.of("/api/guest/session", "/api/guest/screens/home", "/api/system/info")) {
            given().header("Authorization", "Bearer " + deviceA)
                    .when().get(path)
                    .then().statusCode(401);
        }
    }
}
