package de.gxp.update;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.ManagerFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * update-channel T-11 (REQ-710…714, REQ-726): the four manager operations
 * over HTTP, their audit rows (D20) and the exchange file they leave behind.
 * Arranges the row with native SQL (the store is a {@code .control} internal)
 * and serves the shared fixture from the suite's {@link ManifestServer}.
 */
@QuarkusTest
@QuarkusTestResource(ManifestServer.class)
class UpdateApiTest {

    private static final Path EXCHANGE = Path.of("target/update-exchange");

    @Inject
    EntityManager entityManager;

    private String token;
    private long auditMark;

    @BeforeEach
    void arrange() throws IOException {
        resetRowAndFiles();
        ManifestServer.serveNothing();
        token = ManagerFixtures.login();
        auditMark = maxAuditId();
    }

    @AfterEach
    void cleanUp() throws IOException {
        resetRowAndFiles();
        long mark = auditMark;
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from audit_entry where id > :mark").setParameter("mark", mark).executeUpdate());
    }

    private void resetRowAndFiles() throws IOException {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                "update update_channel set auto_update_enabled = true, apply_at = '04:00', last_check_at = null,"
                        + " last_check_outcome = 'never-checked', available_version = null, available_artifact_ref = null,"
                        + " available_sha256 = null, available_published_at = null, available_manifest = null,"
                        + " available_signature = null, manual_requested_version = null, manual_requested_at = null,"
                        + " updated_at = now()").executeUpdate());
        Files.createDirectories(EXCHANGE);
        Files.deleteIfExists(EXCHANGE.resolve("desired-update.json"));
        Files.deleteIfExists(EXCHANGE.resolve("state.json"));
    }

    @Test
    void anonymousIs401OnAllFour() {
        given().when().get("/api/manager/updates").then().statusCode(401);
        given().contentType("application/json").body(Map.of("autoUpdateEnabled", true, "applyAt", "04:00"))
                .when().put("/api/manager/updates/settings").then().statusCode(401);
        given().when().post("/api/manager/updates/apply").then().statusCode(401);
        given().when().post("/api/manager/updates/check").then().statusCode(401);
    }

    @Test
    void theStatusCarriesTheDefaultsTheConfigurationAndNoRawBytes() {
        String body = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/updates").then().statusCode(200)
                .body("currentVersion", equalTo("1.0.0-SNAPSHOT"))
                .body("channel", equalTo("stable"))
                .body("configured", equalTo(true))
                .body("autoUpdateEnabled", equalTo(true))
                .body("applyAt", equalTo("04:00"))
                .body("timezone", equalTo("Europe/Berlin"))
                .body("lastCheckOutcome", equalTo("never-checked"))
                .body("lastCheckAt", nullValue())
                .body("available", nullValue())
                .body("scheduledFor", nullValue())
                .body("updater", nullValue())
                .extract().asString();
        assertFalse(body.contains("manifest") || body.contains("signature"), "REQ-726: no raw manifest/signature field");
    }

    @Test
    void settingsAreValidatedByTheContractAndAuditedPerChangedField() {
        for (String bad : List.of("4:00", "24:00", "04:60", "")) {
            given().header("Authorization", "Bearer " + token).contentType("application/json")
                    .body(Map.of("autoUpdateEnabled", true, "applyAt", bad))
                    .when().put("/api/manager/updates/settings").then().statusCode(400);
        }
        assertEquals(0, auditShapes().size(), "a refused write leaves no row");

        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("autoUpdateEnabled", false, "applyAt", "23:30"))
                .when().put("/api/manager/updates/settings").then().statusCode(200)
                .body("autoUpdateEnabled", equalTo(false))
                .body("applyAt", equalTo("23:30"));
        assertEquals(List.of("update update-settings 1 applyAt", "update update-settings 1 autoUpdateEnabled"), auditShapes());

        // unchanged values → one field-less row (the AuditControl.record contract, same as PropertyControl#update)
        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("autoUpdateEnabled", false, "applyAt", "23:30"))
                .when().put("/api/manager/updates/settings").then().statusCode(200);
        assertEquals(List.of("update update-settings 1 applyAt", "update update-settings 1 autoUpdateEnabled",
                "update update-settings 1"), auditShapes());
    }

    @Test
    void applyWithNothingAvailableIsAConflictAndLeavesNoRow() {
        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/updates/apply").then().statusCode(409)
                .body("code", equalTo("no-update-available"));
        assertTrue(auditShapes().isEmpty());
    }

    @Test
    void checkThenApplyWritesTheManualFileAtOnceAndAuditsBoth() throws IOException {
        ManifestServer.serveFixture();
        Instant before = Instant.now().minusSeconds(1);
        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/updates/check").then().statusCode(200)
                .body("lastCheckOutcome", equalTo("update-available"))
                .body("available.version", equalTo("1.0.1"))
                .body("available.artifactRef", equalTo("ghcr.io/example/gxp-backend:1.0.1"))
                .body("available.publishedAt", equalTo("2026-09-01T10:00:00Z"));
        assertEquals(List.of("update-check update null"), auditShapes());
        String automatic = Files.readString(EXCHANGE.resolve("desired-update.json"));
        assertTrue(automatic.contains("\"requestedBy\": \"auto\""));

        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/updates/apply").then().statusCode(202)
                .body("available.version", equalTo("1.0.1"));
        assertEquals(List.of("update-check update null", "update-apply update 1.0.1"), auditShapes());
        String manual = Files.readString(EXCHANGE.resolve("desired-update.json"), StandardCharsets.UTF_8);
        assertTrue(manual.contains("\"requestedBy\": \"manual\""), manual);
        Instant applyNotBefore = Instant.parse(manual.replaceAll("(?s).*\"applyNotBefore\": \"([^\"]+)\".*", "$1"));
        assertTrue(applyNotBefore.isAfter(before) && applyNotBefore.isBefore(Instant.now().plus(Duration.ofSeconds(2))),
                "apply-not-before = now for a manual request: " + applyNotBefore);
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/updates").then().statusCode(200)
                .body("scheduledFor", equalTo(applyNotBefore.toString().replace(".000", "")));
    }

    @Test
    void disabledAutomaticUpdatesStillReportTheAvailableUpdateButScheduleNothing() throws IOException {
        ManifestServer.serveFixture();
        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body(Map.of("autoUpdateEnabled", false, "applyAt", "04:00"))
                .when().put("/api/manager/updates/settings").then().statusCode(200);
        given().header("Authorization", "Bearer " + token)
                .when().post("/api/manager/updates/check").then().statusCode(200)
                .body("autoUpdateEnabled", equalTo(false))
                .body("available.version", equalTo("1.0.1"))
                .body("scheduledFor", nullValue());
        assertFalse(Files.exists(EXCHANGE.resolve("desired-update.json")));
    }

    @Test
    void theUpdaterStateIsRenderedWhenPresent() throws IOException {
        Files.copy(ManifestServer.fixtures().resolve("state-rolled-back.json"), EXCHANGE.resolve("state.json"));
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/updates").then().statusCode(200)
                .body("updater.outcome", equalTo("rolled-back"))
                .body("updater.rolledBack", equalTo(true))
                .body("updater.message", equalTo("health-timeout"))
                .body("updater.attemptedVersion", equalTo("1.0.1"));
    }

    // ------------------------------------------------------------ helpers

    private long maxAuditId() {
        return ((Number) entityManager.createNativeQuery("select coalesce(max(id), 0) from audit_entry").getSingleResult()).longValue();
    }

    /** {@code "<action> <entity> <entityId>[ <field>]"} since the mark, oldest first; every row by the admin. */
    @SuppressWarnings("unchecked")
    private List<String> auditShapes() {
        List<Object[]> rows = entityManager.createNativeQuery("select actor, action, entity, entity_id, field"
                + " from audit_entry where id > :mark order by id").setParameter("mark", auditMark).getResultList();
        List<String> shapes = new ArrayList<>();
        for (Object[] row : rows) {
            assertEquals("admin", row[0]);
            shapes.add(row[1] + " " + row[2] + " " + row[3] + (row[4] == null ? "" : " " + row[4]));
        }
        return shapes;
    }
}
