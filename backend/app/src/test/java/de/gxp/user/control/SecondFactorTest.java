package de.gxp.user.control;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.LogSweep;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.user.entity.ManagerUser;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * REQ-615/616/618/619 over the real contract paths (auth-hardening T-09,
 * D10/D11/D24). Lives in {@code de.gxp.user.control} because the codes are
 * computed with the package-private {@link TotpVerifier} from the secret the
 * enroll response returned. A dedicated account is seeded per test and
 * removed afterwards (recovery codes cascade), so the seeded {@code admin}
 * never carries a factor — the other classes log in as admin. The login-side
 * enforcement (each recovery code once through the proxy) is T-10's
 * {@code TrustedProxyLoginTest}.
 *
 * <p>Log sweep (REQ-619): every secret, code and password this class ever
 * sees is collected and asserted absent from every captured record.
 */
@QuarkusTest
class SecondFactorTest {

    private static final String USER = "factor-user";
    private static final String PASSWORD = "factor-password-1";
    private static final Pattern FORBIDDEN_KEY = Pattern.compile("(?i)\"[^\"]*(hash|secret|password)[^\"]*\"\\s*:");
    private static final LogSweep SWEEP = new LogSweep();
    private static final List<String> SENSITIVE = new ArrayList<>(List.of(PASSWORD, ManagerFixtures.PASSWORD));

    @Inject
    ManagerUserRepository users;
    @Inject
    RecoveryCodeRepository recoveryCodes;
    @Inject
    Pbkdf2PasswordHasher hasher;
    @Inject
    TotpVerifier totp;

    @BeforeAll
    static void captureLogs() {
        SWEEP.start();
    }

    @AfterAll
    static void stopCapturing() {
        SWEEP.stop();
    }

    @BeforeEach
    void seedUser() {
        SWEEP.clear();
        QuarkusTransaction.requiringNew().run(() -> {
            ManagerUser user = new ManagerUser();
            user.username = USER;
            user.passwordHash = hasher.hash(PASSWORD);
            user.createdAt = Instant.now();
            user.updatedAt = user.createdAt;
            users.persist(user);
        });
    }

    @AfterEach
    void removeUserAndSweep() {
        QuarkusTransaction.requiringNew().run(() -> users.delete("username", USER));
        for (LogRecord record : SWEEP.records()) {
            String text = record.getMessage() + " " + java.util.Arrays.toString(record.getParameters());
            for (String value : SENSITIVE) {
                assertFalse(text.contains(value), "secret/code/password material reached a log (REQ-619):\n" + SWEEP.describe());
            }
        }
    }

    private static Response status(String token) {
        return given().header("Authorization", "Bearer " + token).when().get("/api/manager/users/me/second-factor");
    }

    private static Response enroll(String token) {
        return given().header("Authorization", "Bearer " + token).when().post("/api/manager/users/me/second-factor");
    }

    private static Response confirm(String token, String code) {
        return given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("code", code)).when().post("/api/manager/users/me/second-factor/confirm");
    }

    private static Response disable(String token, String password) {
        return given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("password", password)).when().delete("/api/manager/users/me/second-factor");
    }

    private static Response regenerate(String token, String password) {
        return given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("password", password)).when().post("/api/manager/users/me/recovery-codes");
    }

    /** Enrolls and returns the raw secret; the base32 and URI are remembered for the log sweep. */
    private byte[] enrollOk(String token) {
        Response response = enroll(token);
        response.then().statusCode(200)
                .body("secret", matchesPattern("^[A-Z2-7]{32}$"))
                .body("otpauthUri", matchesPattern("^otpauth://totp/.*secret=[A-Z2-7]+.*$"));
        String base32 = response.jsonPath().getString("secret");
        SENSITIVE.add(base32);
        return decodeBase32(base32);
    }

    private List<String> confirmOk(String token, byte[] secret) {
        Response response = confirm(token, totp.codeAt(secret, totp.currentStep()));
        response.then().statusCode(200).body("codes", hasSize(10));
        List<String> codes = response.jsonPath().getList("codes", String.class);
        codes.forEach(code -> assertTrue(code.matches("^[a-z0-9]{10}$"), code));
        SENSITIVE.addAll(codes);
        return codes;
    }

    private List<String> unusedHashesOf() {
        return QuarkusTransaction.requiringNew().call(() -> recoveryCodes.unusedOf(users.byUsername(USER))
                .stream().map(row -> row.codeHash).toList());
    }

    @Test
    void enrollIsPendingUntilOneValidCodeConfirmsItAndReturnsTenCodesOnce() {
        String token = ManagerFixtures.login(USER, PASSWORD);
        status(token).then().statusCode(200).body("state", equalTo("none")).body("recoveryCodesRemaining", equalTo(0));

        byte[] first = enrollOk(token);
        status(token).then().statusCode(200).body("state", equalTo("pending"));
        // re-enrolling while pending replaces the secret
        byte[] second = enrollOk(token);
        assertFalse(java.util.Arrays.equals(first, second));

        confirm(token, totp.codeAt(first, totp.currentStep())).then().statusCode(400).body("code", equalTo("invalid-code"));
        confirm(token, "12345").then().statusCode(400).body("code", equalTo("validation-failed"));
        status(token).then().statusCode(200).body("state", equalTo("pending"));

        List<String> codes = confirmOk(token, second);
        assertEquals(10, codes.stream().distinct().count());
        Response confirmed = status(token);
        confirmed.then().statusCode(200).body("state", equalTo("confirmed")).body("recoveryCodesRemaining", equalTo(10));
        assertFalse(FORBIDDEN_KEY.matcher(confirmed.asString()).find(), confirmed.asString());
        // stored hashed, never plain
        List<String> stored = unusedHashesOf();
        assertEquals(10, stored.size());
        for (String code : codes) {
            assertFalse(stored.contains(code));
            assertTrue(stored.contains(RecoveryCodes.hash(code)));
        }

        // on the LAN (default profile) a confirmed factor changes nothing: no code, or a wrong one, still logs in (D8)
        ManagerFixtures.login(USER, PASSWORD);
        given().contentType("application/json")
                .body(Map.of("username", USER, "password", PASSWORD, "totpCode", "000000"))
                .when().post("/api/manager/login").then().statusCode(200);

        enroll(token).then().statusCode(409).body("code", equalTo("second-factor-already-confirmed"));
        confirm(token, totp.codeAt(second, totp.currentStep())).then().statusCode(409).body("code", equalTo("no-pending-enrollment"));
        given().header("Authorization", "Bearer " + token).when().get("/api/manager/users")
                .then().statusCode(200).body("find { it.username == '" + USER + "' }.secondFactorState", equalTo("confirmed"));
    }

    @Test
    void confirmWithoutAPendingEnrollmentIs409() {
        String token = ManagerFixtures.login(USER, PASSWORD);
        confirm(token, "123456").then().statusCode(409).body("code", equalTo("no-pending-enrollment"));
    }

    @Test
    void disableAndRegenerateRequireTheCurrentPassword() {
        String token = ManagerFixtures.login(USER, PASSWORD);
        regenerate(token, PASSWORD).then().statusCode(409).body("code", equalTo("second-factor-not-confirmed"));
        List<String> codes = confirmOk(token, enrollOk(token));

        regenerate(token, "not-the-password").then().statusCode(400).body("code", equalTo("current-password-mismatch"));
        Response regenerated = regenerate(token, PASSWORD);
        regenerated.then().statusCode(200).body("codes", hasSize(10));
        List<String> fresh = regenerated.jsonPath().getList("codes", String.class);
        SENSITIVE.addAll(fresh);
        assertNotEquals(codes, fresh);
        List<String> stored = unusedHashesOf();
        assertEquals(10, stored.size());
        assertFalse(stored.contains(RecoveryCodes.hash(codes.get(0))), "the old batch is void");
        assertTrue(stored.contains(RecoveryCodes.hash(fresh.get(0))));

        disable(token, "not-the-password").then().statusCode(400).body("code", equalTo("current-password-mismatch"));
        status(token).then().statusCode(200).body("state", equalTo("confirmed"));
        disable(token, PASSWORD).then().statusCode(204);
        status(token).then().statusCode(200).body("state", equalTo("none")).body("recoveryCodesRemaining", equalTo(0));
        assertEquals(0, unusedHashesOf().size());
        // idempotent, and the session survives (a disable is not a credential change)
        disable(token, PASSWORD).then().statusCode(204);
        status(token).then().statusCode(200);
        // ... and the factor can be enrolled again
        enrollOk(token);
        status(token).then().statusCode(200).body("state", equalTo("pending"));
    }

    @Test
    void anotherAdminCanResetTheFactorButNobodyTheirOwn() {
        String token = ManagerFixtures.login(USER, PASSWORD);
        confirmOk(token, enrollOk(token));
        String admin = ManagerFixtures.login();
        long id = given().header("Authorization", "Bearer " + admin).when().get("/api/manager/users")
                .then().statusCode(200).extract().jsonPath().getLong("find { it.username == '" + USER + "' }.id");
        long adminId = given().header("Authorization", "Bearer " + admin).when().get("/api/manager/users")
                .then().statusCode(200).extract().jsonPath().getLong("find { it.username == 'admin' }.id");

        given().header("Authorization", "Bearer " + admin).when().delete("/api/manager/users/" + adminId + "/second-factor")
                .then().statusCode(409).body("code", equalTo("cannot-reset-self"));
        given().header("Authorization", "Bearer " + admin).when().delete("/api/manager/users/999999/second-factor")
                .then().statusCode(404).body("code", equalTo("user-not-found"));

        given().header("Authorization", "Bearer " + admin).when().delete("/api/manager/users/" + id + "/second-factor")
                .then().statusCode(204);
        // the target's session is dropped (a lost device may hold one); a fresh login shows no factor
        status(token).then().statusCode(401);
        String again = ManagerFixtures.login(USER, PASSWORD);
        status(again).then().statusCode(200).body("state", equalTo("none")).body("recoveryCodesRemaining", equalTo(0));
        assertEquals(0, unusedHashesOf().size());
        given().header("Authorization", "Bearer " + admin).when().get("/api/manager/users")
                .then().statusCode(200).body("find { it.username == '" + USER + "' }.secondFactorState", equalTo("none"));
    }

    @Test
    void theWholeSurfaceIsAdminOnly() {
        given().when().get("/api/manager/users/me/second-factor").then().statusCode(401);
        given().when().post("/api/manager/users/me/second-factor").then().statusCode(401);
        given().contentType("application/json").body("{}").when().post("/api/manager/users/me/second-factor/confirm").then().statusCode(401);
        given().contentType("application/json").body("{}").when().delete("/api/manager/users/me/second-factor").then().statusCode(401);
        given().contentType("application/json").body("{}").when().post("/api/manager/users/me/recovery-codes").then().statusCode(401);
        given().when().delete("/api/manager/users/1/second-factor").then().statusCode(401);
    }

    /** REQ-619: the property key {@code secret} exists exactly once in the contract, in the enrollment DTO. */
    @Test
    void theContractCarriesTheSecretOnlyInTheEnrollmentDto() throws Exception {
        String yaml = Files.readString(Path.of("../../api/openapi.yaml"));
        Matcher keys = Pattern.compile("(?m)^\\s+secret:\\s*$").matcher(yaml);
        int count = 0;
        int position = -1;
        while (keys.find()) {
            count++;
            position = keys.start();
        }
        assertEquals(1, count, "the property key 'secret' must appear exactly once");
        int enrollment = yaml.indexOf("    SecondFactorEnrollmentDto:");
        int nextSchema = yaml.indexOf("\n    SecondFactorCodeRequest:", enrollment);
        assertTrue(enrollment >= 0 && position > enrollment && position < nextSchema, "…inside SecondFactorEnrollmentDto");
    }

    /** RFC 4648 base32 decode — test-side only; production never decodes base32 (the secret is stored base64). */
    private static byte[] decodeBase32(String base32) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : base32.toCharArray()) {
            if (c == '=') {
                break;
            }
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xff);
            }
        }
        byte[] decoded = out.toByteArray();
        assertEquals(20, decoded.length);
        assertEquals(base32, Base32.encode(decoded));
        return decoded;
    }

    /** Sanity of the test-side decoder against the production codec. */
    @Test
    void theTestDecoderRoundTripsTheProductionEncoder() {
        byte[] secret = totp.generateSecret();
        assertTrue(java.util.Arrays.equals(secret, decodeBase32(Base32.encode(secret))));
    }
}
