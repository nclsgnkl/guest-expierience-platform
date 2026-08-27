package de.gxp.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;

/**
 * REQ-332 (amended 2026-08-24): the CONTRACT is the source of truth for what is
 * authenticated. Every operation in {@code api/openapi.yaml} that declares
 * {@code security: [bearerAuth]} must answer <b>401 + WWW-Authenticate: Bearer</b>
 * to an anonymous caller — one case per operation, derived, never hand-listed.
 *
 * <p>{@code ManagerSurfaceDeniedTest} asserts the same thing per manager
 * Resource by hand; this test is what makes a NEW secured operation impossible
 * to forget, because adding it to the contract adds a case here automatically.
 * Together with {@code quarkus.security.jaxrs.deny-unannotated-endpoints=true}
 * the two ends meet: an operation the contract secures but the Boundary forgot
 * to annotate fails closed at runtime AND is named here.
 *
 * <p>The YAML is parsed with Jackson's YAML dataformat, already on the
 * classpath through {@code quarkus-smallrye-openapi} — no dependency was added
 * for this (the allow-list in {@code docs/constitution.md} is closed).
 */
@QuarkusTest
class ContractSecuredOperationsDeniedTest {

    /** One anonymous request derived from one secured contract operation. */
    record SecuredOperation(String operationId, String httpMethod, String path, String requestContentType) {
        @Override
        public String toString() {
            return operationId + " (" + httpMethod.toUpperCase() + " " + path + ")";
        }
    }

    static Stream<SecuredOperation> securedOperations() throws Exception {
        return allOperations().stream().filter(ContractOperation::secured).map(ContractOperation::operation);
    }

    /** One contract operation and whether it declares {@code bearerAuth}. */
    private record ContractOperation(SecuredOperation operation, boolean secured) {
    }

    /** EVERY operation in the contract, secured or not — the derivation both tests share. */
    private static List<ContractOperation> allOperations() throws Exception {
        JsonNode contract = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(contractPath()));
        List<ContractOperation> operations = new ArrayList<>();
        JsonNode paths = contract.get("paths");
        for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> pathEntry = it.next();
            for (Iterator<Map.Entry<String, JsonNode>> ops = pathEntry.getValue().fields(); ops.hasNext();) {
                Map.Entry<String, JsonNode> operation = ops.next();
                if (!HTTP_METHODS.contains(operation.getKey())) {
                    continue;
                }
                operations.add(new ContractOperation(new SecuredOperation(
                        operation.getValue().path("operationId").asText(),
                        operation.getKey(),
                        // a value for every template variable: the security check
                        // answers long before anything looks the id up
                        pathEntry.getKey().replaceAll("\\{[^}]+}", "1"),
                        requestContentType(operation.getValue())),
                        requiresBearerAuth(operation.getValue())));
            }
        }
        return operations;
    }

    private static final List<String> HTTP_METHODS = List.of("get", "put", "post", "delete", "patch");

    private static boolean requiresBearerAuth(JsonNode operation) {
        for (JsonNode requirement : operation.path("security")) {
            if (requirement.has("bearerAuth")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first declared request media type, or {@code null}. RESTEasy selects
     * the resource method by {@code @Consumes} BEFORE the security handler
     * runs, so a wrong content type would answer 415 and prove nothing.
     */
    private static String requestContentType(JsonNode operation) {
        JsonNode content = operation.path("requestBody").path("content");
        Iterator<String> mediaTypes = content.fieldNames();
        return mediaTypes.hasNext() ? mediaTypes.next() : null;
    }

    /** The contract lives outside the Maven module (same file the generator reads). */
    private static Path contractPath() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve("api").resolve("openapi.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("api/openapi.yaml not found above " + Path.of("").toAbsolutePath());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("securedOperations")
    void everySecuredContractOperationChallengesAnonymous(SecuredOperation operation) {
        RequestSpecification request = given();
        if (operation.requestContentType() != null) {
            // a minimal payload of the declared type: never parsed, the eager
            // security check rejects the call before deserialization. The two
            // body(...) calls are deliberately separate — one ternary would pick
            // RestAssured's Object overload, which tries to SERIALIZE the bytes
            // and fails on image/png before any request is sent.
            request = request.contentType(operation.requestContentType());
            request = operation.requestContentType().startsWith("application/json")
                    ? request.body("{}")
                    : request.body(new byte[] { 0 });
        }
        request.when().request(operation.httpMethod().toUpperCase(), operation.path())
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
    }

    /**
     * A parse regression that quietly matched FEWER operations would leave the
     * parameterized test green, so the derivation itself is pinned: the count
     * cannot shrink below what the contract carries today, and the three
     * surfaces that matter most must be in it by operation id.
     *
     * <p>The TOTAL is floored as well as the secured count, because the two
     * fail differently. A secured operation that loses its {@code bearerAuth}
     * drops the secured count only; a whole path deleted from the contract —
     * or a parse regression that stops seeing one — drops both, and would
     * otherwise be invisible here AND in the parameterized test, which only
     * ever asserts about operations it managed to derive. On 2026-08-24 the
     * contract carried 42 operations: 36 secured, 6 anonymous (pairing ×3,
     * manager login, health, ready). Auth-hardening T-07 (2026-08-26) raised
     * both floors by the five {@code manager-users} operations: 47 / 41;
     * T-09 (2026-08-26) by the six second-factor operations: 53 / 47;
     * T-11 (2026-08-26) by the audit read: 54 / 48; update-channel T-03
     * (2026-08-26) by the four {@code manager-updates} operations: 58 / 52.
     * guest-payload-consolidation T-01 (2026-08-27) LOWERED both by one —
     * the first deliberate drop: {@code getGuestSession} and
     * {@code getGuestHomeScreen} became the single {@code getGuestContent}
     * (REQ-941), so 57 / 51. A floor is lowered only with the removal that
     * caused it named here.
     */
    @Test
    void theDerivationCoversTheWholeSecuredSurface() throws Exception {
        int totalOperations = allOperations().size();
        assertTrue(totalOperations >= 57,
                "the contract declares fewer operations than it did on 2026-08-27 (" + totalOperations
                        + " of 57) — a path or method disappeared, or the parse regressed. If the removal "
                        + "is intentional, lower this floor deliberately.");
        List<String> operationIds = securedOperations().map(SecuredOperation::operationId).toList();
        assertTrue(operationIds.size() >= 51,
                "the contract secures fewer operations than it did on 2026-08-27 (" + operationIds.size()
                        + ") — if that is intentional, lower this floor deliberately: " + operationIds);
        for (String pinned : List.of("getGuestContent", "putGuestPreferences", "listDevices", "revokeDevice",
                "changeManagerPassword", "getSystemInfo", "createUser", "deactivateUser", "resetUserPassword",
                "resetUserSecondFactor", "enrollOwnSecondFactor", "confirmOwnSecondFactor",
                "disableOwnSecondFactor", "regenerateOwnRecoveryCodes", "listAuditEntries",
                "getUpdateStatus", "updateUpdateSettings", "applyUpdateNow", "checkForUpdateNow")) {
            assertTrue(operationIds.contains(pinned), pinned + " is no longer derived as a secured operation");
        }
    }
}
