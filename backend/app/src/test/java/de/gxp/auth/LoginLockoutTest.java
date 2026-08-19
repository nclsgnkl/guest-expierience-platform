package de.gxp.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Login lockout window (REQ-326): 5 consecutive failures → 429 with
 * Retry-After, even for the correct password. Runs on its own test profile
 * with a 2 s lock so the suite never waits 30 s and — because a profile gets
 * its own application instance — the tripped lock cannot poison other tests.
 * The production default stays the pinned 30 s.
 */
@QuarkusTest
@TestProfile(LoginLockoutTest.ShortLockoutProfile.class)
class LoginLockoutTest {

    public static class ShortLockoutProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("gxp.auth.lockout-seconds", "2");
        }
    }

    @Test
    void fiveConsecutiveFailuresLockTheEndpoint() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            given().contentType("application/json")
                    .body(Map.of("password", "wrong-attempt-" + i))
                    .when().post("/api/manager/login")
                    .then().statusCode(401);
        }
        // locked now — even the CORRECT password is answered 429 + Retry-After
        given().contentType("application/json")
                .body(Map.of("password", AuthResourceTest.PASSWORD))
                .when().post("/api/manager/login")
                .then().statusCode(429)
                .header("Retry-After", notNullValue())
                .body("code", equalTo("login-locked"));

        Thread.sleep(2200); // the profile's 2 s lock window passes

        given().contentType("application/json")
                .body(Map.of("password", AuthResourceTest.PASSWORD))
                .when().post("/api/manager/login")
                .then().statusCode(200);
    }
}
