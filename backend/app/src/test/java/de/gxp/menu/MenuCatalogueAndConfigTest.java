package de.gxp.menu;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * T-19 (REQ-306, REQ-307, REQ-311, REQ-321): the seeded catalogue equals the
 * five frontend tuples verbatim, config roundtrips per scope with per-field
 * inheritance under the CEILING rule (design amendment 2026-08-19: a room can
 * only offer what the property offers), and the NULL-scope uniqueness holds
 * in the DB.
 */
@QuarkusTest
class MenuCatalogueAndConfigTest {

    @Inject
    EntityManager entityManager;

    private static String token() {
        return given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200).extract().path("token");
    }

    @Test
    void catalogueEqualsTheFiveFrontendTuples() {
        given().header("Authorization", "Bearer " + token())
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(200)
                .body("$", hasSize(5))
                .body("[0].key", equalTo("home"))
                .body("[0].route", equalTo(""))
                .body("[0].defaultIcon", equalTo("phosphorHouse"))
                .body("[0].defaultSortIndex", equalTo(0))
                .body("[0].label.de", equalTo("Home"))
                .body("[0].label.en", equalTo("Home"))
                .body("[1].key", equalTo("info"))
                .body("[1].route", equalTo("/info"))
                .body("[1].defaultIcon", equalTo("phosphorInfo"))
                .body("[1].label.de", equalTo("Info"))
                .body("[2].key", equalTo("umgebung"))
                .body("[2].route", equalTo("/umgebung"))
                .body("[2].defaultIcon", equalTo("phosphorMapPin"))
                .body("[2].label.de", equalTo("Umgebung"))
                .body("[2].label.en", equalTo("Nearby"))
                .body("[3].key", equalTo("smart-home"))
                .body("[3].route", equalTo("/smart-home"))
                .body("[3].defaultIcon", equalTo("phosphorLightbulbFilament"))
                .body("[3].label.en", equalTo("Smart Home"))
                .body("[4].key", equalTo("services"))
                .body("[4].route", equalTo("/services"))
                .body("[4].defaultIcon", equalTo("phosphorShoppingBag"))
                .body("[4].label.de", equalTo("Services"))
                // the structurally fixed settings entry is app chrome, not catalogue
                .body("findAll { it.key == 'einstellungen' }", hasSize(0));
    }

    @Test
    void propertyScopeOverrideRoundtrip() {
        String token = token();
        try {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "umgebung", "enabled", true, "sortIndex", 1,
                            "iconOverride", "phosphorTree", "label", Map.of("de", "Draußen", "en", "Outside"))))
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(200)
                    .body("[0].menuItemKey", equalTo("umgebung"));
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/menu-config")
                    .then().statusCode(200)
                    .body("$", hasSize(1))
                    .body("[0].sortIndex", equalTo(1))
                    .body("[0].iconOverride", equalTo("phosphorTree"))
                    .body("[0].label.de", equalTo("Draußen"))
                    .body("[0].label.en", equalTo("Outside"));
        } finally {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(List.of())
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(200);
        }
    }

    @Test
    void labelOnlyRoomRowLeavesEnabledAndSortInherited() {
        String token = token();
        int roomId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T19-inh", "displayName", "Vererbung"))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
        try {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "info", "label", Map.of("de", "Hausinfo"))))
                    .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                    .then().statusCode(200);
            // per-field semantics: only the label is overridden, the rest stays
            // null in the row and resolves property → catalogue default
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/rooms/" + roomId + "/menu-config")
                    .then().statusCode(200)
                    .body("$", hasSize(1))
                    .body("[0].menuItemKey", equalTo("info"))
                    .body("[0].label.de", equalTo("Hausinfo"))
                    .body("[0].enabled", nullValue())
                    .body("[0].sortIndex", nullValue())
                    .body("[0].iconOverride", nullValue());
        } finally {
            given().header("Authorization", "Bearer " + token)
                    .when().delete("/api/manager/rooms/" + roomId)
                    .then().statusCode(204);
        }
    }

    @Test
    void unknownCatalogueKeyIs400() {
        given().header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "spa", "enabled", true)))
                .when().put("/api/manager/menu-config")
                .then().statusCode(400)
                .body("code", equalTo("unknown-menu-item"));
    }

    @Test
    void duplicateEntryForOneKeyIs400() {
        given().header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "info", "enabled", true),
                        Map.of("menuItemKey", "info", "enabled", false)))
                .when().put("/api/manager/menu-config")
                .then().statusCode(400)
                .body("code", equalTo("duplicate-menu-item"));
    }

    @Test
    void roomEnableOfAPropertyDisabledItemIs409NamingTheItem() {
        String token = token();
        int roomId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T19-ceil", "displayName", "Ceiling"))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
        try {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "smart-home", "enabled", false)))
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(200);
            // ceiling amendment rule 2: the hotel doesn't offer it, so the
            // room can't enable it
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "smart-home", "enabled", true)))
                    .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                    .then().statusCode(409)
                    .body("code", equalTo("item-disabled-at-property"))
                    .body("details", hasSize(1))
                    .body("details[0].reference", equalTo("smart-home"));
            // rejected atomically — the room scope still has no rows
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/rooms/" + roomId + "/menu-config")
                    .then().statusCode(200)
                    .body("$", hasSize(0));
        } finally {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(List.of())
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(200);
            given().header("Authorization", "Bearer " + token)
                    .when().delete("/api/manager/rooms/" + roomId)
                    .then().statusCode(204);
        }
    }

    @Test
    void aRoomEnableRowIsInertUnderTheCeiling() {
        String token = token();
        int roomId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T19-ceil2", "displayName", "Decke"))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
        try {
            // forge the pre-amendment state the API no longer allows (rules
            // 2/3 reject it): property disables services while a room row
            // explicitly enables it
            QuarkusTransaction.requiringNew().run(() -> {
                entityManager.createNativeQuery("insert into menu_item_config (menu_item_key, room_id, enabled)"
                        + " values ('services', null, false)").executeUpdate();
                entityManager.createNativeQuery("insert into menu_item_config (menu_item_key, room_id, enabled)"
                        + " values ('services', " + roomId + ", true)").executeUpdate();
            });
            // ceiling resolution: the room-enable row cannot resurrect the
            // item — a room card targeting it is rejected as disabled
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("sortIndex", 0, "icon", "phosphorShoppingBag",
                            "targetMenuItemKey", "services",
                            "title", Map.of("de", "Services"), "description", Map.of("de", "Text"))))
                    .when().put("/api/manager/rooms/" + roomId + "/action-cards")
                    .then().statusCode(409)
                    .body("code", equalTo("target-disabled"))
                    .body("details[0].scope", equalTo("room:" + roomId))
                    .body("details[0].reference", equalTo("services"));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> entityManager
                    .createNativeQuery("delete from menu_item_config where menu_item_key = 'services'")
                    .executeUpdate());
            given().header("Authorization", "Bearer " + token)
                    .when().delete("/api/manager/rooms/" + roomId)
                    .then().statusCode(204);
        }
    }

    @Test
    void duplicateNullScopeRowIsRejectedByTheDatabase() {
        // UNIQUE NULLS NOT DISTINCT (menu_item_key, room_id): two property-
        // scope rows for the same key must violate the constraint
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery(
                    "insert into menu_item_config (menu_item_key, room_id, enabled) values ('services', null, false)")
                    .executeUpdate();
            entityManager.createNativeQuery(
                    "insert into menu_item_config (menu_item_key, room_id, enabled) values ('services', null, true)")
                    .executeUpdate();
        }));
    }
}
