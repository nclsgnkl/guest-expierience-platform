package de.gxp.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Manager auth lifecycle over the real contract paths (REQ-326..REQ-328,
 * REQ-331, REQ-332, REQ-335). The credential comes from the test profile's
 * {@code %test.gxp.admin.password} through {@link AdminPasswordSeeder} — a
 * successful login here IS the end-to-end seeding proof (REQ-330/334).
 *
 * <p>State discipline (shared DB): the login path keeps NO failure state at
 * all — REQ-326 as amended 2026-08-24 has no lockout, no rate limit and no
 * concurrency gate, so nothing needs resetting after a failed attempt. The
 * password-change test still restores the original password before
 * finishing.
 */
@QuarkusTest
class AuthResourceTest {

    /** Must match %test.gxp.admin.password in application.properties. */
    static final String PASSWORD = "test-admin-password-1";

    static String login(String password) {
        return given()
                .contentType("application/json")
                .body(Map.of("password", password))
                .when().post("/api/manager/login")
                .then()
                .statusCode(200)
                .extract().path("token");
    }

    @Test
    void loginIssuesASlidingTokenWithExpiry() {
        given()
                .contentType("application/json")
                .body(Map.of("password", PASSWORD))
                .when().post("/api/manager/login")
                .then()
                .statusCode(200)
                .body("token", matchesPattern("[A-Za-z0-9_-]{43}")) // 256-bit base64url
                .body("idleTimeoutSeconds", equalTo(600))
                .body("expiresAt", notNullValue())
                .header("GXP-Token-Expires", notNullValue());
    }

    @Test
    void wrongPasswordIsAGeneric401() {
        given()
                .contentType("application/json")
                .body(Map.of("password", "definitely-wrong"))
                .when().post("/api/manager/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("invalid-credentials"))
                // generic — the message must not echo or hint at anything
                .body("message", not(equalTo("definitely-wrong")))
                .body("message", equalTo("Login failed"));
    }

    @Test
    void expiryHeaderSlidesForwardAcrossRequests() throws InterruptedException {
        String token = login(PASSWORD);
        String first = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(200)
                .extract().header("GXP-Token-Expires");
        Thread.sleep(1100);
        String second = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(200)
                .extract().header("GXP-Token-Expires");
        assertTrue(Instant.parse(second).isAfter(Instant.parse(first)),
                "the idle expiry must move forward on every authenticated request");
    }

    @Test
    void logoutKillsExactlyThePresentedToken() {
        String token = login(PASSWORD);
        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/logout")
                .then().statusCode(204);
        // the dropped token is dead everywhere from now on
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(401);
        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/logout")
                .then().statusCode(401);
    }

    @Test
    void missingAndGarbageTokensAreChallengedWith401() {
        given()
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
        given().header("Authorization", "Bearer never-issued-token")
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(401);
    }

    @Test
    void passwordChangeDropsEveryTokenAndTheOldPassword() {
        String newPassword = "changed-password-9";
        String sessionOne = login(PASSWORD);
        String sessionTwo = login(PASSWORD);

        // wrong current password: 400, nothing changes
        given().header("Authorization", "Bearer " + sessionOne)
                .contentType("application/json")
                .body(Map.of("currentPassword", "wrong-current", "newPassword", "long-enough-pw"))
                .when().put("/api/manager/password")
                .then().statusCode(400)
                .body("code", equalTo("current-password-mismatch"));

        // policy violation (< 8): 400 in the shared ApiError shape
        given().header("Authorization", "Bearer " + sessionOne)
                .contentType("application/json")
                .body(Map.of("currentPassword", PASSWORD, "newPassword", "short"))
                .when().put("/api/manager/password")
                .then().statusCode(400)
                .body("code", notNullValue())
                .body("message", notNullValue());

        // the real change
        given().header("Authorization", "Bearer " + sessionOne)
                .contentType("application/json")
                .body(Map.of("currentPassword", PASSWORD, "newPassword", newPassword))
                .when().put("/api/manager/password")
                .then().statusCode(204);

        // BOTH live sessions are dead — including the caller's (v1.5.1)
        given().header("Authorization", "Bearer " + sessionOne)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(401);
        given().header("Authorization", "Bearer " + sessionTwo)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(401);

        // the old password no longer logs in; the new one does
        given().contentType("application/json").body(Map.of("password", PASSWORD))
                .when().post("/api/manager/login")
                .then().statusCode(401);
        String restoreSession = login(newPassword);

        // restore the original password so the shared DB state stays clean
        given().header("Authorization", "Bearer " + restoreSession)
                .contentType("application/json")
                .body(Map.of("currentPassword", newPassword, "newPassword", PASSWORD))
                .when().put("/api/manager/password")
                .then().statusCode(204);
        login(PASSWORD); // proves restoration
    }

    @Test
    void unauthenticatedPasswordChangeIsDenied() {
        given().contentType("application/json")
                .body(Map.of("currentPassword", PASSWORD, "newPassword", "does-not-matter-1"))
                .when().put("/api/manager/password")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
    }

    @Test
    void sixWrongPasswordsInARowArmNothingAndTheCorrectOneStillWorks() {
        // The positive statement of REQ-326 as amended: login is deliberately
        // NOT throttled, so repeated failures accumulate no punitive state and
        // never produce a 429 or a Retry-After.
        for (int attempt = 0; attempt < 6; attempt++) {
            given().contentType("application/json").body(Map.of("password", "wrong-" + attempt))
                    .when().post("/api/manager/login")
                    .then().statusCode(401)
                    .header("Retry-After", org.hamcrest.Matchers.nullValue());
        }
        login(PASSWORD); // still the correct password, after six failures in a row
    }

    @Test
    void loginRejectsAContractInvalidBodyWithApiError() {
        given().contentType("application/json")
                .body("{}") // password is required by the contract
                .when().post("/api/manager/login")
                .then().statusCode(400)
                .body("code", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    void idleTimeoutIsAtLeastTheDocumentedTenMinutes() {
        given().contentType("application/json").body(Map.of("password", PASSWORD))
                .when().post("/api/manager/login")
                .then().statusCode(200)
                .body("idleTimeoutSeconds", greaterThanOrEqualTo(600));
    }
}
