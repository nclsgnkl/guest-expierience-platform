package de.gxp.user.control;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.ManagerFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * REQ-621 (auth-hardening T-12): one audit row per user-management and
 * second-factor action, with the right actor and the affected username as
 * entity id — and never a hash, secret or code (the table has no value
 * column; the assertion here is on the row shape). Lives in
 * {@code de.gxp.user.control} because confirming a factor needs
 * {@link TotpVerifier} to compute a code (precedent: {@code SecondFactorTest}).
 */
@QuarkusTest
class AccountAuditTest {

    private static final String USER = "audit-actions-user";
    private static final String PASSWORD = "audit-actions-pw-1";
    private static final String NEW_PASSWORD = "audit-actions-pw-2";

    @Inject
    ManagerUserRepository users;
    @Inject
    TotpVerifier totp;
    @Inject
    EntityManager entityManager;

    @AfterEach
    void removeUserAndRows() {
        QuarkusTransaction.requiringNew().run(() -> {
            users.delete("username", USER);
            entityManager.createNativeQuery("delete from audit_entry where entity_id = :name")
                    .setParameter("name", USER).executeUpdate();
        });
    }

    @Test
    void everyAccountAndSecondFactorActionLeavesExactlyOneRow() {
        String admin = ManagerFixtures.login();
        long userId = given().header("Authorization", "Bearer " + admin).contentType("application/json")
                .body(Map.of("username", USER, "password", PASSWORD))
                .when().post("/api/manager/users").then().statusCode(201).extract().jsonPath().getLong("id");

        String user = ManagerFixtures.login(USER, PASSWORD);
        given().header("Authorization", "Bearer " + user)
                .when().post("/api/manager/users/me/second-factor").then().statusCode(200);
        byte[] secret = QuarkusTransaction.requiringNew()
                .call(() -> Base64.getDecoder().decode(users.byUsername(USER).totpSecret));
        given().header("Authorization", "Bearer " + user).contentType("application/json")
                .body(Map.of("code", totp.codeAt(secret, totp.currentStep())))
                .when().post("/api/manager/users/me/second-factor/confirm").then().statusCode(200);
        given().header("Authorization", "Bearer " + user).contentType("application/json")
                .body(Map.of("password", PASSWORD))
                .when().post("/api/manager/users/me/recovery-codes").then().statusCode(200);
        // a wrong password on a /me operation is not an action and leaves no row
        given().header("Authorization", "Bearer " + user).contentType("application/json")
                .body(Map.of("password", "wrong-password-1"))
                .when().delete("/api/manager/users/me/second-factor").then().statusCode(400);
        given().header("Authorization", "Bearer " + user).contentType("application/json")
                .body(Map.of("password", PASSWORD))
                .when().delete("/api/manager/users/me/second-factor").then().statusCode(204);
        given().header("Authorization", "Bearer " + user).contentType("application/json")
                .body(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .when().put("/api/manager/password").then().statusCode(204);

        given().header("Authorization", "Bearer " + admin).contentType("application/json")
                .body(Map.of("newPassword", "audit-actions-pw-3"))
                .when().put("/api/manager/users/" + userId + "/password").then().statusCode(204);
        given().header("Authorization", "Bearer " + admin)
                .when().delete("/api/manager/users/" + userId + "/second-factor").then().statusCode(204);
        given().header("Authorization", "Bearer " + admin)
                .when().post("/api/manager/users/" + userId + "/deactivate").then().statusCode(204);
        given().header("Authorization", "Bearer " + admin)
                .when().post("/api/manager/users/" + userId + "/reactivate").then().statusCode(204);

        assertEquals(List.of(
                "admin user-create",
                USER + " login-success",
                USER + " second-factor-enroll",
                USER + " second-factor-confirm",
                USER + " recovery-codes-regenerate",
                USER + " second-factor-disable",
                USER + " password-change",
                "admin password-reset",
                "admin second-factor-reset",
                "admin user-deactivate",
                "admin user-reactivate"), actorAndActionRows());
    }

    /** {@code "<actor> <action>"} of every row whose entity id is the test user, oldest first; all entity {@code user}, no field. */
    @SuppressWarnings("unchecked")
    private List<String> actorAndActionRows() {
        List<Object[]> rows = entityManager.createNativeQuery("select actor, action, entity, field, address, via_proxy"
                + " from audit_entry where entity_id = :name order by id")
                .setParameter("name", USER).getResultList();
        List<String> shape = new ArrayList<>();
        for (Object[] row : rows) {
            assertEquals("user", row[2]);
            assertEquals(null, row[3], "account actions carry no field");
            assertEquals("127.0.0.1", row[4]);
            assertEquals(Boolean.FALSE, row[5]);
            shape.add(row[0] + " " + row[1]);
        }
        return shape;
    }
}
