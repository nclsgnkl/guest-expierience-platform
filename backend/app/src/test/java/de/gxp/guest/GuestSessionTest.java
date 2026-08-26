package de.gxp.guest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.DeviceFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import de.gxp.infrastructure.testsupport.ManagerFixtures;

/**
 * T-22 (REQ-416..419, REQ-426): the session equals the seeded frontend
 * shape — 5 catalogue items, settings chrome OUTSIDE the items, localized
 * maps over exactly the offered languages; ceiling parity (a property
 * disable beats an explicit room enable row); occupancy flip; vacancy
 * data-sparing (no name, no override, standby texts present).
 */
@QuarkusTest
class GuestSessionTest {

    @Inject
    EntityManager entityManager;

    private long roomId;
    private String deviceToken;

    @BeforeEach
    void seed() {
        roomId = ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Session-Kiosk");
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from stay where room_id = :id")
                    .setParameter("id", roomId).executeUpdate();
            entityManager.createNativeQuery(
                    "delete from menu_item_config where menu_item_key = 'smart-home'").executeUpdate();
        });
    }

    private static String adminToken() {
        return ManagerFixtures.login();
    }

    private io.restassured.response.ValidatableResponse session() {
        return given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/session")
                .then().statusCode(200);
    }

    @Test
    void theSessionMatchesTheSeededFrontendShape() {
        session()
                .body("property.name", equalTo("Meine Unterkunft"))
                .body("property.brand.primary", equalTo("#5b6472"))
                .body("room.code", equalTo("1"))
                .body("room.displayName", equalTo("Zimmer 1"))
                // 5 catalogue items, catalogue order, none disabled in the seed
                .body("menu.items", hasSize(5))
                .body("menu.items.key",
                        equalTo(java.util.List.of("home", "info", "umgebung", "smart-home", "services")))
                .body("menu.items[1].label.de", equalTo("Info"))
                .body("menu.items[2].label.en", equalTo("Nearby"))
                .body("menu.items[3].icon", equalTo("phosphorLightbulbFilament"))
                .body("menu.items[3].route", equalTo("/smart-home"))
                // the settings chrome sits OUTSIDE the items (REQ-418)
                .body("menu.settingsEntry.id", equalTo("einstellungen"))
                .body("menu.settingsEntry.icon", equalTo("phosphorGearSix"))
                .body("menu.settingsEntry.route", equalTo("/einstellungen"))
                .body("menu.settingsEntry.label.de", equalTo("Einstellungen"))
                .body("menu.settingsEntry.label.en", equalTo("Settings"))
                // settings config with resolved defaults
                .body("settings.languages", hasSize(2))
                .body("settings.languages[0].code", equalTo("de"))
                .body("settings.languages[0].locale", equalTo("de-DE"))
                .body("settings.appearances[1].icon", equalTo("phosphorMoon"))
                .body("settings.defaultLanguage", equalTo("de"))
                .body("settings.defaultAppearance", equalTo("light"))
                // localized maps carry EXACTLY the offered languages (REQ-426):
                // the seed has only a de translation row — en falls back, never a hole
                .body("standby.headline.keySet()", equalTo(java.util.Set.of("de", "en")))
                .body("standby.headline.de", equalTo("Meine Unterkunft")) // {property.name} resolved
                .body("menu.items[0].label.keySet()", equalTo(java.util.Set.of("de", "en")));
    }

    @Test
    void vacancyIsDataSparingAndOccupancyFlips() {
        // vacant: no stay-derived value anywhere, standby texts still present
        session()
                .body("occupancy", equalTo("VACANT"))
                .body("guestDisplayName", nullValue())
                .body("preference", nullValue())
                .body("standby.message.de", equalTo("Herzlich willkommen"));

        // an active stay flips occupancy and serves the display name
        String token = adminToken();
        int stayId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", "Anna Schmidt",
                        "checkIn", OffsetDateTime.now().minusHours(1).toString(),
                        "checkOut", OffsetDateTime.now().plusHours(3).toString(),
                        "language", "en", "status", "BOOKED"))
                .when().post("/api/manager/stays")
                .then().statusCode(201).extract().path("id");
        session()
                .body("occupancy", equalTo("OCCUPIED"))
                .body("guestDisplayName", equalTo("Anna Schmidt"))
                // the RAW override — only what the stay carries (language here)
                .body("preference.language", equalTo("en"))
                .body("preference.appearance", nullValue());

        // deleting the stay flips back to vacant
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/stays/" + stayId)
                .then().statusCode(204);
        session()
                .body("occupancy", equalTo("VACANT"))
                .body("guestDisplayName", nullValue());
    }

    @Test
    void aPropertyDisableCeilingsAnExplicitRoomEnable() {
        // The API itself refuses to create this state (the ceiling guards),
        // so seed it directly: an explicit room-level enable row AND a
        // property-level disable — resolution must still ceiling the item out.
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("insert into menu_item_config (menu_item_key, room_id, enabled)"
                    + " values ('smart-home', :roomId, true)")
                    .setParameter("roomId", roomId).executeUpdate();
            entityManager.createNativeQuery("insert into menu_item_config (menu_item_key, room_id, enabled)"
                    + " values ('smart-home', null, false)").executeUpdate();
        });
        session()
                .body("menu.items", hasSize(4))
                .body("menu.items.key", not(org.hamcrest.Matchers.hasItem("smart-home")));
    }
}
