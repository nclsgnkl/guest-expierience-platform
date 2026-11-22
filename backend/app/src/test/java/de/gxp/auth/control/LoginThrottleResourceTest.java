package de.gxp.auth.control;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.LogSweep;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.user.control.UserControl;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * The per-address throttle over the real contract paths (REQ-601, REQ-602,
 * REQ-604, REQ-605, REQ-606's replacement criteria). Lives in
 * {@code de.gxp.auth.control} — not in {@code AuthResourceTest} as the TODO
 * first suggested — because the probes it needs ({@link LoginThrottle#reset},
 * {@link LoginThrottle#failuresOf}) are package-private on purpose
 * (precedent: the device feature's pairing flow test, which also sits in its
 * {@code .control} test package). The hasher's verification count is read
 * through {@link UserControl#passwordVerifications()} since the hasher moved
 * to {@code de.gxp.user} (T-05).
 *
 * <p>rest-assured always arrives from {@code 127.0.0.1}, so every test here
 * shares one bucket; it is reset before AND after each test so that the
 * suite's other deliberate failures (three in {@code AuthResourceTest}) and
 * this class never see each other's state. {@code %test.gxp.auth.throttle-block-seconds=1}
 * keeps the block short.
 *
 * <p>Log sweep (REQ-605): {@link LogSweep} captures everything under
 * {@code de.gxp} and {@code io.quarkus.vertx.http} during each test —
 * expected: nothing.
 */
@QuarkusTest
class LoginThrottleResourceTest {

    private static final String LOCALHOST = "127.0.0.1";
    private static final LogSweep SWEEP = new LogSweep();

    @Inject
    LoginThrottle throttle;
    @Inject
    UserControl userControl;
    @Inject
    EntityManager entityManager;

    @BeforeAll
    static void captureLogs() {
        SWEEP.start();
    }

    @AfterAll
    static void stopCapturing() {
        SWEEP.stop();
    }

    @BeforeEach
    void freshBucket() {
        throttle.reset();
        SWEEP.clear();
    }

    @AfterEach
    void leaveNoState() {
        throttle.reset();
        assertTrue(SWEEP.records().isEmpty(),
                "the throttle, the auth feature and the HTTP layer must log nothing (REQ-605):\n" + SWEEP.describe());
    }

    /** Audit rows of failed logins for the seeded username (auth-hardening T-12). */
    private long loginFailureRows() {
        return ((Number) entityManager.createNativeQuery("select count(*) from audit_entry"
                + " where action = 'login-failure' and entity_id = :name")
                .setParameter("name", ManagerFixtures.USERNAME).getSingleResult()).longValue();
    }

    private static io.restassured.response.Response attempt(String password) {
        return ManagerFixtures.attempt(ManagerFixtures.USERNAME, password);
    }

    @Test
    void fiveFailuresThenTheSixthIs429BeforeAnyHashing() throws InterruptedException {
        long failureRowsBefore = loginFailureRows();
        for (int i = 0; i < 5; i++) {
            attempt("wrong-" + i).then().statusCode(401).body("code", equalTo("invalid-credentials"));
        }
        assertEquals(5, throttle.failuresOf(LOCALHOST));
        assertEquals(failureRowsBefore + 5, loginFailureRows(), "each verified miss is audited (REQ-621)");

        long verificationsBefore = userControl.passwordVerifications();
        attempt(ManagerFixtures.PASSWORD).then()
                .statusCode(429)
                .header("Retry-After", equalTo("1"))
                .body("code", equalTo("too-many-attempts"))
                .body("message", notNullValue());
        assertEquals(verificationsBefore, userControl.passwordVerifications(),
                "a blocked address must be rejected BEFORE PBKDF2 runs (REQ-602)");
        assertEquals(5, throttle.failuresOf(LOCALHOST), "a 429 is not itself a failure");
        assertEquals(failureRowsBefore + 5, loginFailureRows(), "a 429 leaves NO audit row — it stays DB-free (D20)");

        // after Retry-After elapses the address is admitted again and a
        // success clears the bucket
        Thread.sleep(1100);
        attempt(ManagerFixtures.PASSWORD).then().statusCode(200);
        assertEquals(0, throttle.failuresOf(LOCALHOST));
    }

    @Test
    void aWrongCurrentPasswordOnPasswordChangeCountsAgainstTheAddress() {
        String token = ManagerFixtures.login();
        assertEquals(0, throttle.failuresOf(LOCALHOST));
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("currentPassword", "wrong-current", "newPassword", "long-enough-pw"))
                .when().put("/api/manager/password")
                .then().statusCode(400)
                .body("code", equalTo("current-password-mismatch"));
        assertEquals(1, throttle.failuresOf(LOCALHOST), "REQ-604: the current-password check shares the budget");
    }

    @Test
    void aBlockedAddressCannotChangeThePasswordEither() {
        String token = ManagerFixtures.login();
        for (int i = 0; i < 5; i++) {
            attempt("wrong-" + i).then().statusCode(401);
        }
        long verificationsBefore = userControl.passwordVerifications();
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("currentPassword", ManagerFixtures.PASSWORD, "newPassword", "long-enough-pw"))
                .when().put("/api/manager/password")
                .then().statusCode(429)
                .header("Retry-After", notNullValue())
                .body("code", equalTo("too-many-attempts"));
        assertEquals(verificationsBefore, userControl.passwordVerifications());
    }

    @Test
    void aSuccessfulLoginClearsPreviousFailures() {
        attempt("wrong-1").then().statusCode(401);
        attempt("wrong-2").then().statusCode(401);
        assertEquals(2, throttle.failuresOf(LOCALHOST));
        ManagerFixtures.login();
        assertEquals(0, throttle.failuresOf(LOCALHOST));
    }
}
