package de.gxp.guest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.DeviceFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * T-23 (REQ-424): the whole-record preference write — 204 lands on the
 * active stay and shows in the next session read; nulls clear the override;
 * a value outside the offered set is the named 400; a vacant room the named
 * 409 (the kiosk's fire-and-forget write treats both as normal).
 */
@QuarkusTest
class GuestPreferenceTest {

    @Inject
    EntityManager entityManager;

    private long roomId;
    private String deviceToken;
    private String adminToken;

    @BeforeEach
    void seed() {
        adminToken = given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200).extract().path("token");
        roomId = ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Pref-Kiosk");
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from stay where room_id = :id")
                .setParameter("id", roomId).executeUpdate());
    }

    private void createActiveStay() {
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", "Gast",
                        "checkIn", OffsetDateTime.now().minusHours(1).toString(),
                        "checkOut", OffsetDateTime.now().plusHours(3).toString(),
                        "status", "BOOKED"))
                .when().post("/api/manager/stays")
                .then().statusCode(201);
    }

    private io.restassured.response.Response putPreferences(Map<String, Object> body) {
        return given().header("Authorization", "Bearer " + deviceToken)
                .contentType("application/json").body(body)
                .when().put("/api/guest/preferences")
                .then().extract().response();
    }

    @Test
    void theWriteLandsOnTheActiveStayAndShowsInTheNextSession() {
        createActiveStay();
        var write = putPreferences(Map.of("language", "en", "appearance", "dark"));
        org.junit.jupiter.api.Assertions.assertEquals(204, write.statusCode());
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/session")
                .then().statusCode(200)
                .body("preference.language", equalTo("en"))
                .body("preference.appearance", equalTo("dark"));

        // whole-record semantics: nulls clear the override (follow the defaults)
        Map<String, Object> clear = new HashMap<>();
        clear.put("language", null);
        clear.put("appearance", null);
        org.junit.jupiter.api.Assertions.assertEquals(204, putPreferences(clear).statusCode());
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/session")
                .then().statusCode(200)
                .body("preference.language", nullValue())
                .body("preference.appearance", nullValue());
    }

    @Test
    void aValueOutsideTheOfferedSetIsTheNamed400() {
        createActiveStay();
        var response = putPreferences(Map.of("language", "fr"));
        org.junit.jupiter.api.Assertions.assertEquals(400, response.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals("value-not-offered", response.path("code"));
        var appearance = putPreferences(Map.of("appearance", "sepia"));
        org.junit.jupiter.api.Assertions.assertEquals(400, appearance.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals("value-not-offered", appearance.path("code"));
    }

    @Test
    void aVacantRoomIsTheNamed409() {
        var response = putPreferences(Map.of("language", "en"));
        org.junit.jupiter.api.Assertions.assertEquals(409, response.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals("room-vacant", response.path("code"));
    }
}
