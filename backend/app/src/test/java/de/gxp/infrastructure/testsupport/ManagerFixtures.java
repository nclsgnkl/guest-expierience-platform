package de.gxp.infrastructure.testsupport;

import static io.restassured.RestAssured.given;

import java.util.Map;

/**
 * Test-only manager login (auth-hardening T-01, REQ-626/609). Every test that
 * needs an admin bearer token obtains it here, so the login contract can
 * change in ONE place (the username becomes mandatory in T-05) instead of in
 * ~21 private helpers.
 *
 * <p>The credentials are the seeded test ones ({@code %test.gxp.admin.password}
 * in {@code application.properties}; the username is the unset
 * {@code gxp.admin.username}'s default); a successful {@link #login()} is
 * therefore also the end-to-end seeding proof.
 *
 * <p>Every default-profile test shares the throttle bucket of
 * {@code 127.0.0.1} (five failures per 15 minutes; a success clears it). A
 * test that fails a login on purpose should follow it with a success, or
 * reset the throttle the way the auth feature's {@code LoginThrottleResourceTest}
 * does — otherwise an unrelated class can see a 429 with no obvious cause.
 */
public final class ManagerFixtures {

    /** The seeded first user (default of {@code gxp.admin.username}, T-05). */
    public static final String USERNAME = "admin";
    /** Must match {@code %test.gxp.admin.password} in application.properties. */
    public static final String PASSWORD = "test-admin-password-1";

    private ManagerFixtures() {
    }

    /** Logs in with the seeded test credentials and returns the bearer token. */
    public static String login() {
        return login(USERNAME, PASSWORD);
    }

    /** Logs in with explicit credentials and returns the bearer token; asserts a 200. */
    public static String login(String username, String password) {
        return attempt(username, password)
                .then().statusCode(200).extract().path("token");
    }

    /** Posts a login without asserting the outcome (for the failure paths). */
    public static io.restassured.response.Response attempt(String username, String password) {
        return given().contentType("application/json")
                .body(Map.of("username", username, "password", password))
                .when().post("/api/manager/login");
    }
}
