package de.gxp.audit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.ManagerFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * REQ-620 / REQ-622 (auth-hardening T-11): the read surface and the schema
 * guarantee. Rows are seeded with native SQL — the repository is a
 * {@code de.gxp.audit.control} internal — with a marker action so the
 * class cleans up only what it wrote; every other class's login rows stay.
 */
@QuarkusTest
class AuditResourceTest {

    private static final String MARKER = "t11-paging";

    @Inject
    EntityManager entityManager;

    @AfterEach
    void removeSeededRows() {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from audit_entry where action = :action")
                .setParameter("action", MARKER).executeUpdate());
    }

    /** REQ-620: exactly the nine designed columns — and therefore NO value column, by schema. */
    @Test
    void theTableHasExactlyTheNineDesignedColumnsAndNoValueColumn() {
        @SuppressWarnings("unchecked")
        List<String> columns = entityManager
                .createNativeQuery("select column_name from information_schema.columns"
                        + " where table_name = 'audit_entry' order by column_name")
                .getResultList();
        assertEquals(List.of("action", "actor", "address", "entity", "entity_id", "field", "id",
                "occurred_at", "via_proxy"), columns,
                "audit_entry must carry exactly the nine designed columns — a value column can never exist");
    }

    @Test
    void pagesAreNewestFirstWithTheTotalCount() {
        // 60 rows dated in the future, so they are the newest whatever else the suite wrote
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < 60; i++) {
                entityManager.createNativeQuery("insert into audit_entry"
                        + " (occurred_at, actor, address, via_proxy, action, entity, entity_id, field)"
                        + " values (now() + make_interval(mins => :offset), 'admin', '127.0.0.1', false,"
                        + " :action, 'room', :entityId, null)")
                        .setParameter("offset", 100 + i)
                        .setParameter("action", MARKER)
                        .setParameter("entityId", String.valueOf(i))
                        .executeUpdate();
            }
        });
        String token = ManagerFixtures.login();
        // counted AFTER the login: its own row is older than the seeded ones and part of the total
        long expectedTotal = ((Number) entityManager
                .createNativeQuery("select count(*) from audit_entry").getSingleResult()).longValue();

        JsonPath first = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/audit?page=0&size=50")
                .then().statusCode(200)
                .body("page", equalTo(0)).body("size", equalTo(50))
                .body("entries", hasSize(50))
                .extract().jsonPath();
        assertEquals(expectedTotal, first.getLong("total"));
        List<String> actions = first.getList("entries.action");
        assertTrue(actions.stream().allMatch(MARKER::equals), "the 50 newest rows are the seeded ones");
        assertEquals("59", first.getString("entries[0].entityId"), "newest first");
        List<String> occurred = first.getList("entries.occurredAt");
        for (int i = 1; i < occurred.size(); i++) {
            assertTrue(OffsetDateTime.parse(occurred.get(i - 1)).compareTo(OffsetDateTime.parse(occurred.get(i))) >= 0,
                    "descending occurredAt at index " + i);
        }
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/audit?page=1&size=50")
                .then().statusCode(200)
                .body("entries[0].entityId", equalTo("9"))
                .body("entries[0].actor", equalTo("admin"))
                .body("entries[0].address", equalTo("127.0.0.1"))
                .body("entries[0].viaProxy", equalTo(false));
    }

    @Test
    void defaultsArePageZeroSizeFiftyAndTheBoundsAreEnforced() {
        String token = ManagerFixtures.login();
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/audit")
                .then().statusCode(200).body("page", equalTo(0)).body("size", equalTo(50));
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/audit?size=201")
                .then().statusCode(400).body("code", equalTo("validation-failed"));
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/audit?size=0")
                .then().statusCode(400).body("code", equalTo("validation-failed"));
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/audit?size=200")
                .then().statusCode(200).body("size", equalTo(200));
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/audit?page=-1")
                .then().statusCode(400).body("code", equalTo("validation-failed"));
    }

    /** REQ-620: no update or delete operation exists on the audit surface. */
    @Test
    void theSurfaceIsReadOnlyAndAdminOnly() {
        given().when().get("/api/manager/audit").then().statusCode(401);
        String token = ManagerFixtures.login();
        given().header("Authorization", "Bearer " + token).when().delete("/api/manager/audit").then().statusCode(405);
        given().header("Authorization", "Bearer " + token).when().delete("/api/manager/audit/1").then().statusCode(404);
        given().header("Authorization", "Bearer " + token).contentType("application/json").body("{}")
                .when().put("/api/manager/audit/1").then().statusCode(404);
    }
}
