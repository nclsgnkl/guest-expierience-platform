package de.gxp.user;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.ManagerFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Manager account administration over the real contract paths
 * (auth-hardening REQ-610, REQ-611, REQ-613; design D12/D13/D14). Every
 * account this class creates is removed afterwards (shared DB); the seeded
 * {@code admin} is never touched. Deliberate login failures are followed by
 * a success so the address bucket stays clear for the other classes.
 */
@QuarkusTest
class UserResourceTest {

    private static final String PASSWORD = "initial-password-1";
    private static final Pattern FORBIDDEN_KEY = Pattern.compile("(?i)\"[^\"]*(hash|secret|code|password)[^\"]*\"\\s*:");

    @Inject
    EntityManager entityManager;

    @AfterEach
    void removeCreatedAccounts() {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("DELETE FROM manager_user WHERE username <> 'admin'").executeUpdate());
    }

    private static Response create(String token, String username, String password) {
        return given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("username", username, "password", password))
                .when().post("/api/manager/users");
    }

    private static long createOk(String token, String username) {
        Response response = create(token, username, PASSWORD);
        response.then().statusCode(201)
                .body("id", notNullValue())
                .body("username", equalTo(username))
                .body("active", equalTo(true))
                .body("secondFactorState", equalTo("none"))
                .body("createdAt", notNullValue());
        assertNoSecretKeys(response.asString());
        return response.jsonPath().getLong("id");
    }

    private static void assertNoSecretKeys(String json) {
        assertFalse(FORBIDDEN_KEY.matcher(json).find(),
                "a user response must never carry hash/secret/code/password (REQ-613): " + json);
    }

    private static Response authed(String token) {
        return given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/menu/catalogue");
    }

    private static long idOf(String token, String username) {
        return given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/users")
                .then().statusCode(200)
                .extract().jsonPath().getLong("find { it.username == '" + username + "' }.id");
    }

    @Test
    void createListsTheAccountAndItCanLogIn() {
        String admin = ManagerFixtures.login();
        createOk(admin, "colleague");
        Response list = given().header("Authorization", "Bearer " + admin)
                .when().get("/api/manager/users");
        list.then().statusCode(200)
                .body("username", hasItem("colleague"))
                .body("username", hasItem(ManagerFixtures.USERNAME));
        assertNoSecretKeys(list.asString());
        ManagerFixtures.attempt("colleague", PASSWORD).then().statusCode(200)
                .body("username", equalTo("colleague"));
    }

    @Test
    void aDuplicateUsernameIs409AndABadOneIs400() {
        String admin = ManagerFixtures.login();
        createOk(admin, "twice");
        create(admin, "twice", PASSWORD).then().statusCode(409).body("code", equalTo("username-taken"));
        create(admin, ManagerFixtures.USERNAME, PASSWORD).then().statusCode(409);
        create(admin, "Not Valid", PASSWORD).then().statusCode(400).body("code", equalTo("validation-failed"));
        create(admin, "short-pw", "short").then().statusCode(400).body("code", equalTo("validation-failed"));
    }

    @Test
    void deactivatingAnotherAccountKillsItsTokensOnTheNextRequestAndReactivationRestoresLogin() {
        String admin = ManagerFixtures.login();
        long id = createOk(admin, "leaver");
        String leaverSession = ManagerFixtures.login("leaver", PASSWORD);
        authed(leaverSession).then().statusCode(200);

        given().header("Authorization", "Bearer " + admin)
                .when().post("/api/manager/users/" + id + "/deactivate")
                .then().statusCode(204);

        authed(leaverSession).then().statusCode(401);
        authed(admin).then().statusCode(200);
        // the generic 401 — indistinguishable from a wrong password (REQ-609)
        ManagerFixtures.attempt("leaver", PASSWORD).then().statusCode(401)
                .body("code", equalTo("invalid-credentials")).body("message", equalTo("Login failed"));
        given().header("Authorization", "Bearer " + admin)
                .when().get("/api/manager/users")
                .then().statusCode(200)
                .body("find { it.username == 'leaver' }.active", equalTo(false));

        given().header("Authorization", "Bearer " + admin)
                .when().post("/api/manager/users/" + id + "/reactivate")
                .then().statusCode(204);
        ManagerFixtures.login("leaver", PASSWORD);
    }

    @Test
    void anAccountCannotDeactivateItself() {
        String admin = ManagerFixtures.login();
        long self = idOf(admin, ManagerFixtures.USERNAME);
        given().header("Authorization", "Bearer " + admin)
                .when().post("/api/manager/users/" + self + "/deactivate")
                .then().statusCode(409)
                .body("code", equalTo("cannot-deactivate-self"));
        authed(admin).then().statusCode(200);
    }

    @Test
    void anAdminResetReplacesThePasswordAndDropsTheTargetsTokens() {
        String admin = ManagerFixtures.login();
        long id = createOk(admin, "forgot");
        String forgotSession = ManagerFixtures.login("forgot", PASSWORD);

        given().header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .body(Map.of("newPassword", "short"))
                .when().put("/api/manager/users/" + id + "/password")
                .then().statusCode(400);
        given().header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .body(Map.of("newPassword", "reset-password-2"))
                .when().put("/api/manager/users/" + id + "/password")
                .then().statusCode(204);

        authed(forgotSession).then().statusCode(401);
        authed(admin).then().statusCode(200);
        ManagerFixtures.attempt("forgot", PASSWORD).then().statusCode(401);
        ManagerFixtures.login("forgot", "reset-password-2");
    }

    @Test
    void anAccountCannotResetItsOwnPasswordWithoutTheCurrentOne() {
        // D12 amendment (2026-08-26): the own change is PUT /api/manager/password
        String admin = ManagerFixtures.login();
        long self = idOf(admin, ManagerFixtures.USERNAME);
        given().header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .body(Map.of("newPassword", "reset-password-2"))
                .when().put("/api/manager/users/" + self + "/password")
                .then().statusCode(409)
                .body("code", equalTo("cannot-reset-self"));
        authed(admin).then().statusCode(200);
        ManagerFixtures.login();
    }

    @Test
    void unknownAccountsAre404() {
        String admin = ManagerFixtures.login();
        for (String action : List.of("deactivate", "reactivate")) {
            given().header("Authorization", "Bearer " + admin)
                    .when().post("/api/manager/users/999999/" + action)
                    .then().statusCode(404).body("code", equalTo("user-not-found"));
        }
        given().header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .body(Map.of("newPassword", "reset-password-2"))
                .when().put("/api/manager/users/999999/password")
                .then().statusCode(404).body("code", equalTo("user-not-found"));
    }

    @Test
    void theWholeSurfaceIsAdminOnly() {
        // (ContractSecuredOperationsDeniedTest derives the same for every
        // bearerAuth operation; this is the class-level @RolesAllowed proof)
        for (String path : List.of("/api/manager/users", "/api/manager/users/1/deactivate")) {
            given().contentType("application/json").body("{}").when().post(path).then().statusCode(401);
        }
        given().when().get("/api/manager/users").then().statusCode(401);
    }
}
