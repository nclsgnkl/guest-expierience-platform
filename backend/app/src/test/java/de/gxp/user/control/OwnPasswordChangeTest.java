package de.gxp.user.control;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.user.entity.ManagerUser;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * REQ-611 / REQ-612 over HTTP with TWO accounts (auth-hardening T-06, D13):
 * an own password change kills the caller's sessions and leaves the other
 * user's alone, and the caller is resolved from the bearer identity's
 * principal name — i.e. the principal IS the username, not the role. The
 * second account is seeded through the repository because no create
 * endpoint exists before T-07; it is removed again afterwards.
 */
@QuarkusTest
class OwnPasswordChangeTest {

    private static final String SECOND = "second-user";
    private static final String SECOND_PASSWORD = "second-password-1";

    @Inject
    ManagerUserRepository users;
    @Inject
    Pbkdf2PasswordHasher hasher;

    @BeforeEach
    void seedSecondUser() {
        QuarkusTransaction.requiringNew().run(() -> {
            ManagerUser user = new ManagerUser();
            user.username = SECOND;
            user.passwordHash = hasher.hash(SECOND_PASSWORD);
            user.createdAt = Instant.now();
            user.updatedAt = user.createdAt;
            users.persist(user);
        });
    }

    @AfterEach
    void removeSecondUser() {
        QuarkusTransaction.requiringNew().run(() -> users.delete("username", SECOND));
    }

    private static void changePassword(String token, String current, String next, int expectedStatus) {
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("currentPassword", current, "newPassword", next))
                .when().put("/api/manager/password")
                .then().statusCode(expectedStatus);
    }

    @Test
    void changingOnePasswordDropsOnlyThatUsersSessions() {
        String secondSessionA = ManagerFixtures.login(SECOND, SECOND_PASSWORD);
        String secondSessionB = ManagerFixtures.login(SECOND, SECOND_PASSWORD);
        String adminSession = ManagerFixtures.login();

        // the principal is the username: the change applies to SECOND's
        // credential (admin's current password would be a mismatch here)
        changePassword(secondSessionA, ManagerFixtures.PASSWORD, "changed-second-2", 400);
        changePassword(secondSessionA, SECOND_PASSWORD, "changed-second-2", 204);

        given().header("Authorization", "Bearer " + secondSessionA)
                .when().get("/api/manager/menu/catalogue").then().statusCode(401);
        given().header("Authorization", "Bearer " + secondSessionB)
                .when().get("/api/manager/menu/catalogue").then().statusCode(401);
        given().header("Authorization", "Bearer " + adminSession)
                .when().get("/api/manager/menu/catalogue").then().statusCode(200);

        // the old password is gone, the new one works, admin's is untouched
        ManagerFixtures.attempt(SECOND, SECOND_PASSWORD).then().statusCode(401);
        ManagerFixtures.attempt(SECOND, "changed-second-2").then().statusCode(200)
                .body("username", equalTo(SECOND));
        ManagerFixtures.login();
    }
}
