package de.gxp.menu;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import de.gxp.infrastructure.testsupport.ManagerFixtures;

/**
 * T-20 (REQ-308, REQ-312, REQ-313, REQ-317, REQ-322): whole-set replace,
 * invariant 1 (0–4, unique sort), invariant 2 (unknown target 400 /
 * disabled-in-scope 409), template validation, and two cards sharing one
 * target (the shipped frontend data's shape). Card sets are cleared again.
 */
@QuarkusTest
class ActionCardTest {

    private static String token() {
        return ManagerFixtures.login();
    }

    private static Map<String, Object> card(int sortIndex, String icon, String target, String titleDe) {
        return Map.of("sortIndex", sortIndex, "icon", icon, "targetMenuItemKey", target,
                "title", Map.of("de", titleDe),
                "description", Map.of("de", "Beschreibung"));
    }

    private static void clearPropertyCards(String token) {
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(List.of())
                .when().put("/api/manager/action-cards")
                .then().statusCode(200);
    }

    @Test
    void theShippedFrontendCardSetSavesIncludingTwoCardsForOneTarget() {
        String token = token();
        try {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(
                            card(0, "phosphorKey", "info", "Check-Out"),
                            card(1, "phosphorLightbulbFilament", "smart-home", "Smart Home"),
                            card(2, "phosphorMapPin", "umgebung", "Umgebung"),
                            card(3, "phosphorFileText", "info", "Wichtige Infos")))
                    .when().put("/api/manager/action-cards")
                    .then().statusCode(200)
                    .body("$", hasSize(4))
                    // both 'info' cards survive — the target is deliberately not unique
                    .body("findAll { it.targetMenuItemKey == 'info' }", hasSize(2));
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/action-cards")
                    .then().statusCode(200)
                    .body("$", hasSize(4))
                    .body("[0].title.de", equalTo("Check-Out"))
                    .body("[3].title.de", equalTo("Wichtige Infos"));
        } finally {
            clearPropertyCards(token);
        }
    }

    @Test
    void aFifthCardIs400() {
        // five entries cannot satisfy sortIndex 0..3 + uniqueness — rejected
        given().header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .body(List.of(
                        card(0, "i", "home", "a"), card(1, "i", "info", "b"),
                        card(2, "i", "umgebung", "c"), card(3, "i", "services", "d"),
                        card(0, "i", "smart-home", "e")))
                .when().put("/api/manager/action-cards")
                .then().statusCode(400);
    }

    @Test
    void duplicateSortIndexIs400() {
        given().header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .body(List.of(card(2, "i", "home", "a"), card(2, "i", "info", "b")))
                .when().put("/api/manager/action-cards")
                .then().statusCode(400)
                .body("code", equalTo("duplicate-sort-index"));
    }

    @Test
    void unknownTargetIs400NamingTheKey() {
        given().header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .body(List.of(card(0, "i", "wellness", "Spa")))
                .when().put("/api/manager/action-cards")
                .then().statusCode(400)
                .body("code", equalTo("unknown-menu-item"));
    }

    @Test
    void invalidTemplateInACardIs400NamingThePlaceholder() {
        given().header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .body(List.of(Map.of("sortIndex", 0, "icon", "phosphorKey", "targetMenuItemKey", "info",
                        "title", Map.of("de", "Bis {stay.checkout}"),
                        "description", Map.of("de", "ok"))))
                .when().put("/api/manager/action-cards")
                .then().statusCode(400)
                .body("code", equalTo("unknown-placeholder"))
                .body("details[0].reference", equalTo("{stay.checkout}"));
    }

    @Test
    void cardTargetingAnItemDisabledInItsScopeIs409() {
        String token = token();
        try {
            // disable smart-home at property scope (no cards target it yet)
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "smart-home", "enabled", false)))
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(200);
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(card(0, "phosphorLightbulbFilament", "smart-home", "Smart Home")))
                    .when().put("/api/manager/action-cards")
                    .then().statusCode(409)
                    .body("code", equalTo("target-disabled"))
                    .body("details[0].scope", equalTo("property"))
                    .body("details[0].reference", equalTo("smart-home"));
        } finally {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(List.of())
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(200);
        }
    }

    @Test
    void aPropertyCardStrandedInAnInheritingRoomIs409ListingCardAndRoom() {
        String token = token();
        int roomId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T20-inh2", "displayName", "Erbt Karten"))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
        try {
            // the room opts out of umgebung and has NO own cards — it inherits
            // the property set
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "umgebung", "enabled", false)))
                    .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                    .then().statusCode(200);
            // ceiling amendment rule 4 (invariant 2 extended): the new
            // property card would arrive stranded in the inheriting room
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(card(0, "phosphorMapPin", "umgebung", "Umgebung")))
                    .when().put("/api/manager/action-cards")
                    .then().statusCode(409)
                    .body("code", equalTo("target-disabled"))
                    .body("details", hasSize(1))
                    .body("details[0].scope", equalTo("room:" + roomId))
                    .body("details[0].message", org.hamcrest.Matchers.containsString("Umgebung"));
            // rejected atomically — the property scope still has no cards
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/action-cards")
                    .then().statusCode(200)
                    .body("$", hasSize(0));
        } finally {
            given().header("Authorization", "Bearer " + token)
                    .when().delete("/api/manager/rooms/" + roomId)
                    .then().statusCode(204); // cascades the room's config
        }
    }

    @Test
    void aRoomWithOwnCardsIsNotCheckedOnAPropertyCardPut() {
        String token = token();
        int roomId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T20-own2", "displayName", "Eigenes Set"))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
        try {
            // the room opts out of umgebung but brings its OWN card set —
            // the property cards never reach it (wholesale override, REQ-308)
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "umgebung", "enabled", false)))
                    .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                    .then().statusCode(200);
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(card(0, "phosphorKey", "info", "Zimmerkarte")))
                    .when().put("/api/manager/rooms/" + roomId + "/action-cards")
                    .then().statusCode(200);
            // rule 4 checks INHERITING rooms only — this PUT passes
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(card(0, "phosphorMapPin", "umgebung", "Umgebung")))
                    .when().put("/api/manager/action-cards")
                    .then().statusCode(200)
                    .body("$", hasSize(1));
        } finally {
            clearPropertyCards(token);
            given().header("Authorization", "Bearer " + token)
                    .when().delete("/api/manager/rooms/" + roomId)
                    .then().statusCode(204); // cascades the room's config + cards
        }
    }

    @Test
    void aRoomsOwnSetReplacesWholesaleAndCanBeCleared() {
        String token = token();
        int roomId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T20-own", "displayName", "Eigene Karten"))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
        try {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(card(0, "phosphorKey", "info", "Zimmerkarte")))
                    .when().put("/api/manager/rooms/" + roomId + "/action-cards")
                    .then().statusCode(200)
                    .body("$", hasSize(1));
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/rooms/" + roomId + "/action-cards")
                    .then().statusCode(200)
                    .body("$", hasSize(1))
                    .body("[0].title.de", equalTo("Zimmerkarte"));
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(List.of())
                    .when().put("/api/manager/rooms/" + roomId + "/action-cards")
                    .then().statusCode(200)
                    .body("$", hasSize(0));
        } finally {
            given().header("Authorization", "Bearer " + token)
                    .when().delete("/api/manager/rooms/" + roomId)
                    .then().statusCode(204);
        }
    }
}
