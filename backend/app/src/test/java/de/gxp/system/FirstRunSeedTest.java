package de.gxp.system;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * T-24 (REQ-334): a fresh database + GXP_ADMIN_PASSWORD yields a USABLE
 * instance with zero manual database work — neutral property, offered
 * languages/appearances with defaults, the 5-item catalogue, one default
 * room, and a seeded admin credential (the login below IS the credential
 * proof). Everything is asserted through the real API, authenticated the
 * way a first manager session would be.
 */
@QuarkusTest
class FirstRunSeedTest {

    @Test
    void aFreshInstanceIsCompletelyUsable() {
        // 1. the seeded credential logs in (AdminPasswordSeeder ran on boot)
        String token = given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200)
                .body("token", notNullValue())
                .extract().path("token");

        // 2. neutral property with the pinned defaults (nothing property-
        //    specific — principle 1: placeholder copy the manager edits)
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/property")
                .then().statusCode(200)
                .body("mainLanguage", equalTo("de"))
                .body("timezone", equalTo("Europe/Berlin"))
                .body("defaultAppearance", equalTo("light"))
                .body("languages", hasSize(2))
                .body("languages[0].code", equalTo("de"))
                .body("languages[1].code", equalTo("en"))
                .body("appearances", hasSize(2))
                .body("appearances[0].mode", equalTo("light"))
                .body("appearances[1].mode", equalTo("dark"));

        // 3. the release catalogue covers the five real kiosk sections
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(200)
                .body("$", hasSize(5))
                .body("key", equalTo(java.util.List.of("home", "info", "umgebung", "smart-home", "services")));

        // 4. one default room exists
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/rooms")
                .then().statusCode(200)
                .body("findAll { it.code == '1' }", hasSize(1));

        // 5. platform metadata (V1) is untouched by V2+ (REQ-302)
        given()
                .when().get("/api/system/info")
                .then().statusCode(200)
                .body("instanceName", equalTo("Guest Experience Platform"));
    }
}
