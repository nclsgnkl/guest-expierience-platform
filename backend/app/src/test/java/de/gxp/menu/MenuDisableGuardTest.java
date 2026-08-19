package de.gxp.menu;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * T-21 — invariant 3's affected-scope matrix (REQ-314) under the CEILING
 * resolution (design amendment 2026-08-19): a menu-config change that would
 * strand any resolved scope's cards is rejected 409 listing EVERY offender
 * (code menu-item-targeted-by-cards), a property disable over explicit room
 * enables is rejected 409 listing the rooms (menu-item-enabled-by-rooms),
 * and nothing is changed. Every test restores the empty seed state.
 */
@QuarkusTest
class MenuDisableGuardTest {

    private static String token() {
        return given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200).extract().path("token");
    }

    private static Map<String, Object> card(int sortIndex, String target, String titleDe) {
        return Map.of("sortIndex", sortIndex, "icon", "phosphorKey", "targetMenuItemKey", target,
                "title", Map.of("de", titleDe), "description", Map.of("de", "Text"));
    }

    private static void putPropertyCards(String token, List<?> cards) {
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(cards)
                .when().put("/api/manager/action-cards")
                .then().statusCode(200);
    }

    private static void putPropertyConfig(String token, List<?> entries, int expectedStatus) {
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(entries)
                .when().put("/api/manager/menu-config")
                .then().statusCode(expectedStatus);
    }

    private static int createRoom(String token, String code) {
        return given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", code, "displayName", code))
                .when().post("/api/manager/rooms")
                .then().statusCode(201).extract().path("id");
    }

    private static void deleteRoom(String token, int roomId) {
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/rooms/" + roomId)
                .then().statusCode(204);
    }

    @Test
    void propertyDisableAgainstPropertyCardsIs409ListingTheCards() {
        String token = token();
        try {
            putPropertyCards(token, List.of(card(0, "info", "Check-Out")));
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "info", "enabled", false)))
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(409)
                    .body("code", equalTo("menu-item-targeted-by-cards"))
                    .body("details", hasSize(1))
                    .body("details[0].scope", equalTo("property"))
                    .body("details[0].message", org.hamcrest.Matchers.containsString("Check-Out"));
            // rejected atomically — the scope still has no config rows
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/menu-config")
                    .then().statusCode(200)
                    .body("$", hasSize(0));
        } finally {
            putPropertyCards(token, List.of());
        }
    }

    @Test
    void propertyDisableBeatsARoomEnableAndStrandsTheRoomsOwnCards() {
        String token = token();
        int roomId = createRoom(token, "T21-ovr");
        try {
            // the room explicitly re-enables info and brings its OWN cards
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "info", "enabled", true)))
                    .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                    .then().statusCode(200);
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(card(0, "info", "Zimmerinfo")))
                    .when().put("/api/manager/rooms/" + roomId + "/action-cards")
                    .then().statusCode(200);

            // ceiling amendment (2026-08-19): the property is the global
            // ceiling — its disable wins over the room's explicit enable, so
            // the room's own card would be stranded → 409, nothing changed
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "info", "enabled", false)))
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(409)
                    .body("code", equalTo("menu-item-targeted-by-cards"))
                    .body("details", hasSize(1))
                    .body("details[0].scope", equalTo("room:" + roomId));
        } finally {
            deleteRoom(token, roomId); // cascades the room's config + cards
        }
    }

    @Test
    void propertyDisableOfARoomEnabledItemIs409ListingTheRooms() {
        String token = token();
        int roomId = createRoom(token, "T21-dep");
        try {
            // explicit room-level enable (redundant today), no cards anywhere
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "services", "enabled", true)))
                    .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                    .then().statusCode(200);
            // ceiling amendment rule 3: the property disable is blocked while
            // the explicit room enable exists — listing the dependent room
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "services", "enabled", false)))
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(409)
                    .body("code", equalTo("menu-item-enabled-by-rooms"))
                    .body("details", hasSize(1))
                    .body("details[0].scope", equalTo("room:" + roomId))
                    .body("details[0].reference", equalTo("services"));
            // rejected atomically — the property scope still has no rows
            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/menu-config")
                    .then().statusCode(200)
                    .body("$", hasSize(0));
        } finally {
            deleteRoom(token, roomId); // cascades the room's config
        }
    }

    @Test
    void roomDisableAgainstInheritedPropertyCardsIs409NamingTheRoomScope() {
        String token = token();
        int roomId = createRoom(token, "T21-inh");
        try {
            putPropertyCards(token, List.of(card(0, "info", "Geerbt")));
            // the room has NO own cards — it resolves to the property set,
            // so disabling info in the room strands the inherited card
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "info", "enabled", false)))
                    .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                    .then().statusCode(409)
                    .body("code", equalTo("menu-item-targeted-by-cards"))
                    .body("details[0].scope", equalTo("room:" + roomId));
        } finally {
            putPropertyCards(token, List.of());
            deleteRoom(token, roomId);
        }
    }

    @Test
    void everyOffendingCardIsListedNotJustTheFirst() {
        String token = token();
        try {
            putPropertyCards(token, List.of(
                    card(0, "info", "Check-Out"),
                    card(1, "info", "Wichtige Infos"),
                    card(2, "umgebung", "Umgebung")));
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "info", "enabled", false)))
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(409)
                    .body("code", equalTo("menu-item-targeted-by-cards"))
                    .body("details", hasSize(2)); // both info cards, not the umgebung one
        } finally {
            putPropertyCards(token, List.of());
        }
    }

    @Test
    void aPropertyDisableIsCheckedAgainstEveryRoomScope() {
        String token = token();
        int roomId = createRoom(token, "T21-all");
        try {
            // room-own cards target umgebung; the room does NOT override its enabled state
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(card(0, "umgebung", "Zimmerumgebung")))
                    .when().put("/api/manager/rooms/" + roomId + "/action-cards")
                    .then().statusCode(200);
            // property-level disable of umgebung must be caught in the ROOM scope
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(List.of(Map.of("menuItemKey", "umgebung", "enabled", false)))
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(409)
                    .body("code", equalTo("menu-item-targeted-by-cards"))
                    .body("details[0].scope", equalTo("room:" + roomId));
        } finally {
            deleteRoom(token, roomId);
        }
    }
}
