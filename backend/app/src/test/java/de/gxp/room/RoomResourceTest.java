package de.gxp.room;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Rooms CRUD (REQ-305, REQ-320, REQ-334): seed room, roundtrip, duplicate
 * code 409, override validation, delete guard naming stays, and the cascade
 * proof (the deleted room's own config rows are gone). Created data is
 * removed again — tests share one database.
 */
@QuarkusTest
class RoomResourceTest {

    @Inject
    EntityManager entityManager;

    private static String token() {
        return given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200).extract().path("token");
    }

    private static int createRoom(String token, String code, String displayName) {
        return given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", code, "displayName", displayName))
                .when().post("/api/manager/rooms")
                .then().statusCode(201)
                .body("id", notNullValue())
                .extract().path("id");
    }

    private static void deleteRoom(String token, int roomId) {
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/rooms/" + roomId)
                .then().statusCode(204);
    }

    @Test
    void seedRoomIsListed() {
        String token = token();
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/rooms")
                .then().statusCode(200)
                .body("find { it.code == '1' }.displayName", equalTo("Zimmer 1"));
    }

    @Test
    void crudRoundtrip() {
        String token = token();
        int id = createRoom(token, "T18-rt", "Testzimmer");
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/rooms/" + id)
                .then().statusCode(200)
                .body("code", equalTo("T18-rt"))
                .body("displayName", equalTo("Testzimmer"));
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T18-rt", "displayName", "Umbenannt", "defaultLanguage", "en",
                        "defaultAppearance", "dark"))
                .when().put("/api/manager/rooms/" + id)
                .then().statusCode(200)
                .body("displayName", equalTo("Umbenannt"))
                .body("defaultLanguage", equalTo("en"))
                .body("defaultAppearance", equalTo("dark"));
        deleteRoom(token, id);
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/rooms/" + id)
                .then().statusCode(404)
                .body("code", equalTo("unknown-room"));
    }

    @Test
    void duplicateCodeIs409OnCreateAndUpdate() {
        String token = token();
        int first = createRoom(token, "T18-dup", "Erstes");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T18-dup", "displayName", "Zweites"))
                .when().post("/api/manager/rooms")
                .then().statusCode(409)
                .body("code", equalTo("room-code-exists"));
        int second = createRoom(token, "T18-dup2", "Zweites");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T18-dup", "displayName", "Zweites"))
                .when().put("/api/manager/rooms/" + second)
                .then().statusCode(409);
        deleteRoom(token, second);
        deleteRoom(token, first);
    }

    @Test
    void emptyCodeIs400() {
        String token = token();
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "", "displayName", "Namenlos"))
                .when().post("/api/manager/rooms")
                .then().statusCode(400)
                .body("code", equalTo("validation-failed"));
    }

    @Test
    void appearanceOverrideOutsideTheOfferedSetIs400() {
        String token = token();
        // 'system' is a valid contract value but the seeded property offers
        // only light+dark — the semantic check must reject it
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T18-sys", "displayName", "Systemzimmer", "defaultAppearance", "system"))
                .when().post("/api/manager/rooms")
                .then().statusCode(400)
                .body("code", equalTo("appearance-not-offered"));
    }

    @Test
    void artworkOverrideMustReferenceAnArtworkKindAsset() throws Exception {
        String token = token();
        // upload a LOGO-kind asset and try to use it as room artwork
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(8, 8,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        int assetId = given().header("Authorization", "Bearer " + token)
                .contentType("image/png").body(out.toByteArray())
                .when().post("/api/manager/assets?kind=LOGO")
                .then().statusCode(201).extract().path("id");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("code", "T18-art", "displayName", "Kunstzimmer", "artworkAssetId", assetId))
                .when().post("/api/manager/rooms")
                .then().statusCode(400)
                .body("code", equalTo("asset-kind-mismatch"));
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/assets/" + assetId)
                .then().statusCode(204);
    }

    @Test
    void deletingARoomWithStaysIs409NamingThem() {
        String token = token();
        int roomId = createRoom(token, "T18-stay", "Belegt");
        int stayId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", "Lisa",
                        "checkIn", OffsetDateTime.parse("2027-01-01T15:00:00Z").toString(),
                        "checkOut", OffsetDateTime.parse("2027-01-05T11:00:00Z").toString(),
                        "status", "BOOKED"))
                .when().post("/api/manager/stays")
                .then().statusCode(201).extract().path("id");

        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/rooms/" + roomId)
                .then().statusCode(409)
                .body("code", equalTo("room-has-stays"))
                .body("details[0].reference", equalTo(String.valueOf(stayId)));

        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/stays/" + stayId)
                .then().statusCode(204);
        deleteRoom(token, roomId);
    }

    @Test
    void deletingARoomCascadesItsOwnConfigRows() {
        String token = token();
        int roomId = createRoom(token, "T18-casc", "Kaskade");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "info", "enabled", false)))
                .when().put("/api/manager/rooms/" + roomId + "/menu-config")
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(List.of(Map.of("sortIndex", 0, "icon", "phosphorKey", "targetMenuItemKey", "home",
                        "title", Map.of("de", "Titel"), "description", Map.of("de", "Text"))))
                .when().put("/api/manager/rooms/" + roomId + "/action-cards")
                .then().statusCode(200);

        deleteRoom(token, roomId);

        Long orphans = QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery(
                "select (select count(*) from menu_item_config where room_id = :id) + "
                        + "(select count(*) from action_card_config where room_id = :id)")
                .setParameter("id", (long) roomId).getSingleResult()).longValue());
        assertEquals(0L, orphans, "the deleted room's own override rows must cascade away");
    }
}
