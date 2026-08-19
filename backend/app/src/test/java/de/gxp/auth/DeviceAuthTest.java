package de.gxp.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.testsupport.DeviceFixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * T-21 (REQ-410, REQ-412, REQ-414, REQ-432): one bearer path, two roles.
 * Valid device token → guest 200; revoked → 401 + challenge on the very
 * next request; unknown/malformed → 401; role separation both ways (admin
 * token has no room, device token grants no manager op); NO
 * {@code GXP-Token-Expires} on device responses (device tokens do not
 * slide); device token opens {@code /api/system/info}.
 */
@QuarkusTest
class DeviceAuthTest {

    @Inject
    EntityManager entityManager;

    private String deviceToken;

    @BeforeEach
    void seedDevice() {
        long roomId = ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Auth-Kiosk");
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
    }

    private static String adminToken() {
        return given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200).extract().path("token");
    }

    @Test
    void aValidDeviceTokenOpensTheGuestSessionWithoutExpiryHeader() {
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/session")
                .then().statusCode(200)
                .body("room.code", equalTo("1"))
                // device tokens do not slide — no expiry header (REQ-412)
                .header("GXP-Token-Expires", nullValue());
    }

    @Test
    void aRevokedTokenIs401WithChallengeOnTheVeryNextRequest() {
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/session")
                .then().statusCode(200);
        long deviceId = DeviceFixtures.deviceIdOf(entityManager, deviceToken);
        given().header("Authorization", "Bearer " + adminToken())
                .when().post("/api/manager/devices/" + deviceId + "/revoke")
                .then().statusCode(204);
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/session")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
    }

    @Test
    void unknownAndMalformedTokensAre401() {
        given().header("Authorization", "Bearer never-issued-token")
                .when().get("/api/guest/session")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
        given().header("Authorization", "NotBearer x")
                .when().get("/api/guest/session")
                .then().statusCode(401);
    }

    @Test
    void anAdminTokenGrantsNoGuestEndpoint() {
        // an admin has no room — the guest surface is device-only (REQ-414)
        given().header("Authorization", "Bearer " + adminToken())
                .when().get("/api/guest/session")
                .then().statusCode(403);
    }

    @Test
    void aDeviceTokenGrantsNoManagerEndpoint() {
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/manager/rooms")
                .then().statusCode(403);
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/manager/devices")
                .then().statusCode(403);
    }

    @Test
    void aDeviceTokenOpensSystemInfo() {
        // REQ-432: admin OR device — the kiosk may fingerprint its own box
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/system/info")
                .then().statusCode(200)
                .body("instanceName", equalTo("Guest Experience Platform"));
    }
}
