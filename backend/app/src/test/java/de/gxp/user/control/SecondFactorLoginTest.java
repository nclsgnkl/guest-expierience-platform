package de.gxp.user.control;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.LogSweep;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.infrastructure.testsupport.TrustedProxyProfile;
import de.gxp.user.entity.ManagerUser;
import de.gxp.user.entity.RecoveryCode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * REQ-617 / REQ-604 / REQ-616 login matrix under {@link TrustedProxyProfile}
 * (auth-hardening T-10, D6–D9): every request here is via-proxy, so the
 * factor is required. A user with a CONFIRMED factor is seeded straight
 * through the repositories (secret, confirmation, ten hashed recovery codes)
 * — the enrollment flow itself is {@code SecondFactorTest} (default
 * profile). The seeded {@code admin} has no factor and is the "no confirmed
 * factor" case. Every test forwards its own address, so no bucket is shared.
 *
 * <p>Log sweep (REQ-605/619): nothing at all may be logged, and no address,
 * secret, code or password may appear in any record.
 */
@QuarkusTest
@TestProfile(TrustedProxyProfile.class)
class SecondFactorLoginTest {

    private static final String USER = "proxy-user";
    private static final String PASSWORD = "proxy-password-1";
    private static final List<String> RECOVERY = List.of("aaaaa11111", "bbbbb22222", "ccccc33333", "ddddd44444",
            "eeeee55555", "fffff66666", "ggggg77777", "hhhhh88888", "iiiii99999", "jjjjj00000");
    private static final LogSweep SWEEP = new LogSweep();

    @Inject
    ManagerUserRepository users;
    @Inject
    RecoveryCodeRepository recoveryCodes;
    @Inject
    Pbkdf2PasswordHasher hasher;
    @Inject
    TotpVerifier totp;
    @Inject
    UserControl userControl;
    @Inject
    EntityManager entityManager;

    private byte[] secret;

    @BeforeAll
    static void captureLogs() {
        SWEEP.start();
    }

    @AfterAll
    static void stopCapturing() {
        SWEEP.stop();
    }

    @BeforeEach
    void seedConfirmedUser() {
        SWEEP.clear();
        secret = totp.generateSecret();
        QuarkusTransaction.requiringNew().run(() -> {
            ManagerUser user = new ManagerUser();
            user.username = USER;
            user.passwordHash = hasher.hash(PASSWORD);
            user.totpSecret = Base64.getEncoder().encodeToString(secret);
            user.totpConfirmedAt = Instant.now();
            user.createdAt = Instant.now();
            user.updatedAt = user.createdAt;
            users.persist(user);
            for (String code : RECOVERY) {
                RecoveryCode row = new RecoveryCode();
                row.user = user;
                row.codeHash = RecoveryCodes.hash(code);
                recoveryCodes.persist(row);
            }
        });
    }

    @AfterEach
    void removeUserAndSweep() {
        QuarkusTransaction.requiringNew().run(() -> {
            users.delete("username", USER);
            // login rows of this class: the user's by name, the code-less 401s by the forwarded address
            entityManager.createNativeQuery("delete from audit_entry where entity_id = :name"
                    + " or (action = 'second-factor-required' and address = '203.0.113.101')")
                    .setParameter("name", USER).executeUpdate();
        });
        List<LogRecord> records = SWEEP.records();
        assertEquals(0, records.size(), "nothing may be logged on the login path (REQ-605):\n" + SWEEP.describe());
    }

    private static Response attempt(String address, String username, String password, Map<String, String> factor) {
        Map<String, String> body = new HashMap<>(Map.of("username", username, "password", password));
        body.putAll(factor);
        return given().header("Forwarded", "for=" + address)
                .contentType("application/json").body(body)
                .when().post("/api/manager/login");
    }

    private String currentCode() {
        return totp.codeAt(secret, totp.currentStep());
    }

    private long unusedRecoveryCodes() {
        return QuarkusTransaction.requiringNew().call(() -> recoveryCodes.countUnused(users.byUsername(USER)));
    }

    @Test
    void withoutACodeTheAnswerIsSecondFactorRequiredBeforeAnyPasswordWorkAndNothingIsCounted() {
        long before = userControl.passwordVerifications();
        // six code-less attempts, even with a WRONG password: never 429, never a derivation
        for (int i = 0; i < 6; i++) {
            attempt("203.0.113.101", USER, i % 2 == 0 ? PASSWORD : "wrong-" + i, Map.of())
                    .then().statusCode(401).body("code", equalTo("second-factor-required"));
        }
        assertEquals(before, userControl.passwordVerifications(), "the password must not be verified (D6)");
        // the budget is untouched: a real attempt is still admitted
        attempt("203.0.113.101", USER, PASSWORD, Map.of("totpCode", currentCode()))
                .then().statusCode(200).body("username", equalTo(USER));
        // NOT audited (06_review 🟡 #1, 2026-08-26): the uncounted, unverified 401 must not write —
        // an unthrottled, unauthenticated path would otherwise be an unbounded insert through the relay
        assertEquals(0L, auditRows("second-factor-required", null));
        assertEquals(1L, auditRows("login-success", USER));
    }

    /** Rows with that action and entity id (null = none), forwarded address 203.0.113.101, via_proxy = true. */
    private long auditRows(String action, String entityId) {
        return ((Number) entityManager.createNativeQuery("select count(*) from audit_entry"
                + " where action = :action and address = '203.0.113.101' and via_proxy = true"
                + (entityId == null ? " and entity_id is null" : " and entity_id = '" + entityId + "'"))
                .setParameter("action", action).getSingleResult()).longValue();
    }

    @Test
    void aWrongCodeIsTheGenericFailureAndCountsAgainstTheAddress() {
        String wrongPassword = attempt("203.0.113.102", USER, "wrong", Map.of("totpCode", currentCode())).asString();
        for (int i = 0; i < 4; i++) {
            attempt("203.0.113.102", USER, PASSWORD, Map.of("totpCode", "000000"))
                    .then().statusCode(401).body("code", equalTo("invalid-credentials"));
        }
        assertEquals(wrongPassword, attempt("203.0.113.103", USER, PASSWORD, Map.of("totpCode", "000000")).asString(),
                "wrong password and wrong code answer byte-identical bodies");
        // five failures (1 password + 4 code) → the sixth is throttled (REQ-604)
        attempt("203.0.113.102", USER, PASSWORD, Map.of("totpCode", currentCode()))
                .then().statusCode(429).body("code", equalTo("too-many-attempts"));
    }

    @Test
    void anAccountWithoutAConfirmedFactorCannotLogInThroughTheProxyAndLearnsNothing() {
        long before = userControl.passwordVerifications();
        String noFactor = attempt("203.0.113.104", ManagerFixtures.USERNAME, ManagerFixtures.PASSWORD,
                Map.of("totpCode", "123456")).asString();
        assertEquals(before + 1, userControl.passwordVerifications(), "the password work still happens (D7)");
        String wrongPassword = attempt("203.0.113.104", ManagerFixtures.USERNAME, "wrong", Map.of("totpCode", "123456")).asString();
        assertEquals(wrongPassword, noFactor, "no 'not enrolled' oracle");
        attempt("203.0.113.104", ManagerFixtures.USERNAME, ManagerFixtures.PASSWORD, Map.of("recoveryCode", "aaaaa11111"))
                .then().statusCode(401).body("code", equalTo("invalid-credentials"));
    }

    @Test
    void aValidCodeLogsInOnceAndIsRefusedOnReplay() {
        String code = currentCode();
        attempt("203.0.113.105", USER, PASSWORD, Map.of("totpCode", code))
                .then().statusCode(200).body("token", notNullValue()).body("username", equalTo(USER));
        attempt("203.0.113.105", USER, PASSWORD, Map.of("totpCode", code))
                .then().statusCode(401).body("code", equalTo("invalid-credentials"));
        // the next step is fresh (±1 drift, D9)
        attempt("203.0.113.105", USER, PASSWORD, Map.of("totpCode", totp.codeAt(secret, totp.currentStep() + 1)))
                .then().statusCode(200);
        // two steps away is never accepted
        attempt("203.0.113.105", USER, PASSWORD, Map.of("totpCode", totp.codeAt(secret, totp.currentStep() + 3)))
                .then().statusCode(401).body("code", equalTo("invalid-credentials"));
    }

    @Test
    void eachRecoveryCodeWorksExactlyOnce() {
        assertEquals(10, unusedRecoveryCodes());
        attempt("203.0.113.106", USER, PASSWORD, Map.of("recoveryCode", "zzzzz00000"))
                .then().statusCode(401).body("code", equalTo("invalid-credentials"));
        attempt("203.0.113.106", USER, PASSWORD, Map.of("recoveryCode", RECOVERY.get(0)))
                .then().statusCode(200).body("username", equalTo(USER));
        assertEquals(9, unusedRecoveryCodes());
        attempt("203.0.113.106", USER, PASSWORD, Map.of("recoveryCode", RECOVERY.get(0)))
                .then().statusCode(401).body("code", equalTo("invalid-credentials"));
        attempt("203.0.113.106", USER, PASSWORD, Map.of("recoveryCode", RECOVERY.get(1)))
                .then().statusCode(200);
        assertEquals(8, unusedRecoveryCodes());
        // a wrong password with a valid recovery code does not spend it
        attempt("203.0.113.106", USER, "wrong", Map.of("recoveryCode", RECOVERY.get(2)))
                .then().statusCode(401);
        assertEquals(8, unusedRecoveryCodes());
        // a TOTP code takes precedence when both are sent; the recovery code is not spent
        attempt("203.0.113.106", USER, PASSWORD, Map.of("totpCode", currentCode(), "recoveryCode", RECOVERY.get(2)))
                .then().statusCode(200);
        assertEquals(8, unusedRecoveryCodes());
    }

    @Test
    void noSecretOrCodeReachesAnyResponseButTheToken() {
        Response ok = attempt("203.0.113.107", USER, PASSWORD, Map.of("totpCode", currentCode()));
        ok.then().statusCode(200);
        String body = ok.asString();
        assertFalse(body.contains(Base32.encode(secret)));
        assertFalse(body.contains(PASSWORD));
        RECOVERY.forEach(code -> assertFalse(body.contains(code)));
    }
}
