package de.gxp.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.ManagerFixtures;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Manager auth lifecycle over the real contract paths (REQ-326..REQ-328,
 * REQ-331, REQ-332, REQ-335; auth-hardening REQ-609). The account comes from
 * the test profile's {@code %test.gxp.admin.password} through the user
 * feature's first-start seeder — a successful login here IS the end-to-end
 * seeding proof (REQ-330/334, REQ-608).
 *
 * <p>State discipline (shared DB): the per-address throttle (auth-hardening
 * REQ-601..605) counts the few deliberate failures here against 127.0.0.1,
 * far below its budget of five, and every successful login clears the bucket
 * again — so nothing needs resetting. The throttle itself is proven in
 * {@code de.gxp.auth.control.LoginThrottleTest} (unit) and
 * {@code LoginThrottleResourceTest} (HTTP), which reset it around every test.
 * The password-change test still restores the original password before
 * finishing.
 */
@QuarkusTest
class AuthResourceTest {

    /** Must match %test.gxp.admin.password in application.properties. */
    static final String PASSWORD = "test-admin-password-1";

    static String login(String password) {
        return ManagerFixtures.login(ManagerFixtures.USERNAME, password);
    }

    @Test
    void loginIssuesASlidingTokenWithExpiry() {
        given()
                .contentType("application/json")
                .body(Map.of("username", ManagerFixtures.USERNAME, "password", PASSWORD))
                .when().post("/api/manager/login")
                .then()
                .statusCode(200)
                .body("token", matchesPattern("[A-Za-z0-9_-]{43}")) // 256-bit base64url
                .body("username", equalTo(ManagerFixtures.USERNAME))
                .body("idleTimeoutSeconds", equalTo(600))
                .body("expiresAt", notNullValue())
                .header("GXP-Token-Expires", notNullValue());
    }

    /** REQ-617 / D8: on the LAN the factor is never asked and any supplied code is ignored. */
    @Test
    void onTheLanTheSecondFactorFieldsAreIgnored() {
        given().contentType("application/json")
                .body(Map.of("username", ManagerFixtures.USERNAME, "password", PASSWORD,
                        "totpCode", "000000", "recoveryCode", "0000000000"))
                .when().post("/api/manager/login")
                .then().statusCode(200).body("username", equalTo(ManagerFixtures.USERNAME));
        // contract validation still applies to the shape
        given().contentType("application/json")
                .body(Map.of("username", ManagerFixtures.USERNAME, "password", PASSWORD, "totpCode", "12"))
                .when().post("/api/manager/login")
                .then().statusCode(400).body("code", equalTo("validation-failed"));
    }

    @Test
    void wrongPasswordIsAGeneric401() {
        ManagerFixtures.attempt(ManagerFixtures.USERNAME, "definitely-wrong")
                .then()
                .statusCode(401)
                .body("code", equalTo("invalid-credentials"))
                // generic — the message must not echo or hint at anything
                .body("message", not(equalTo("definitely-wrong")))
                .body("message", equalTo("Login failed"));
        ManagerFixtures.login(); // clears the address bucket
    }

    @Test
    void unknownUserAndWrongPasswordAnswerByteIdenticalBodies() {
        // REQ-609: no username oracle by wording. (Same-work proof is in
        // the user feature's LoginOracleTest, which can read the hasher.)
        String unknownUser = ManagerFixtures.attempt("nobody-here", "definitely-wrong")
                .then().statusCode(401).extract().asString();
        String wrongPassword = ManagerFixtures.attempt(ManagerFixtures.USERNAME, "definitely-wrong")
                .then().statusCode(401).extract().asString();
        assertEquals(unknownUser, wrongPassword);
        ManagerFixtures.login(); // clears the address bucket
    }

    @Test
    void aContractInvalidUsernameIsA400NotA401() {
        ManagerFixtures.attempt("Not A Valid Name!", PASSWORD)
                .then().statusCode(400)
                .body("code", equalTo("validation-failed"));
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

    /**
     * Rewritten for auth-hardening T-06 (REQ-611, D13): the change drops
     * every session of the CALLER — both of the two here — and the old
     * password. That a second user's session survives needs a second
     * account, which only the user feature can seed before T-07; that half
     * lives in its {@code OwnPasswordChangeTest}.
     */
    @Test
    void passwordChangeDropsTheCallersSessionsAndTheOldPassword() {
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

        // BOTH live sessions of this user are dead — including the caller's (v1.5.1, D13)
        given().header("Authorization", "Bearer " + sessionOne)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(401);
        given().header("Authorization", "Bearer " + sessionTwo)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(401);

        // the old password no longer logs in; the new one does
        ManagerFixtures.attempt(ManagerFixtures.USERNAME, PASSWORD).then().statusCode(401);
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
    void loginRejectsAContractInvalidBodyWithApiError() {
        given().contentType("application/json")
                .body("{}") // username and password are required by the contract
                .when().post("/api/manager/login")
                .then().statusCode(400)
                .body("code", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    void idleTimeoutIsAtLeastTheDocumentedTenMinutes() {
        ManagerFixtures.attempt(ManagerFixtures.USERNAME, PASSWORD)
                .then().statusCode(200)
                .body("idleTimeoutSeconds", greaterThanOrEqualTo(600));
    }
}
