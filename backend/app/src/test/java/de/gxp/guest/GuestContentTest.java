package de.gxp.guest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.DeviceFixtures;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.infrastructure.testsupport.MenuFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * The ONE guest content read (REQ-941, REQ-944, REQ-945, REQ-948; formerly
 * two classes, {@code GuestSessionTest} and {@code GuestHomeScreenTest}):
 * the payload equals the seeded frontend shape — 5 catalogue items, settings
 * chrome OUTSIDE the items, localized maps over exactly the offered
 * languages; ceiling parity (a property disable beats an explicit room enable
 * row); occupancy flip.
 *
 * <p>The two halves of the merge, asserted together because they are now one
 * answer: <b>occupied</b> carries {@code home} with its templates resolved
 * per offered language, and <b>vacant</b> is 200 with the property half
 * intact and no {@code home}, no {@code guestDisplayName}, no
 * {@code preference} — the retired {@code 409 room-vacant} expressed as
 * absence (REQ-944/945).
 */
@QuarkusTest
class GuestContentTest {

    private static final ZoneId PROPERTY_TZ = ZoneId.of("Europe/Berlin");

    @Inject
    EntityManager entityManager;

    private long roomId;
    private String deviceToken;

    @BeforeEach
    void seed() {
        roomId = ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Content-Kiosk");
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from stay where room_id = :id")
                    .setParameter("id", roomId).executeUpdate();
            entityManager.createNativeQuery(
                    "delete from menu_item_config where menu_item_key in ('smart-home', 'home')").executeUpdate();
        });
        // card sets are cleared through the API in the tests that create them.
        // Carried over from the deleted GuestHomeScreenTest, deliberately
        // (guest-payload-consolidation 02-design.md §0): a property-scope menu
        // config replace deletes V10's eight `enabled = false` opt-out rows,
        // and absence of a row resolves to ENABLED — so without this the
        // placeholder catalogue fixtures arrive on for whatever class runs
        // next in the shared database, green until the order changes.
        MenuFixtures.restoreCatalogueOptOut(entityManager);
    }

    private static String adminToken() {
        return ManagerFixtures.login();
    }

    private io.restassured.response.ValidatableResponse content() {
        return given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/content")
                .then().statusCode(200);
    }

    private OffsetDateTime createActiveStay(String token) {
        OffsetDateTime checkOut = OffsetDateTime.now().plusHours(3).withNano(0);
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", "Anna Schmidt",
                        "checkIn", OffsetDateTime.now().minusHours(1).toString(),
                        "checkOut", checkOut.toString(), "status", "BOOKED"))
                .when().post("/api/manager/stays")
                .then().statusCode(201);
        return checkOut;
    }

    @Test
    void theContentMatchesTheSeededFrontendShape() {
        content()
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
        // vacant: no stay-derived value anywhere — including no `home` at all,
        // which is the retired 409 (REQ-944). The property half is intact.
        content()
                .body("occupancy", equalTo("VACANT"))
                .body("guestDisplayName", nullValue())
                .body("preference", nullValue())
                .body("home", nullValue())
                .body("property.name", equalTo("Meine Unterkunft"))
                .body("menu.items", hasSize(5))
                .body("standby.message.de", equalTo("Herzlich willkommen"));

        // an active stay flips occupancy, serves the display name AND `home`
        String token = adminToken();
        int stayId = given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("roomId", roomId, "displayName", "Anna Schmidt",
                        "checkIn", OffsetDateTime.now().minusHours(1).toString(),
                        "checkOut", OffsetDateTime.now().plusHours(3).toString(),
                        "language", "en", "status", "BOOKED"))
                .when().post("/api/manager/stays")
                .then().statusCode(201).extract().path("id");
        content()
                .body("occupancy", equalTo("OCCUPIED"))
                .body("guestDisplayName", equalTo("Anna Schmidt"))
                // the RAW override — only what the stay carries (language here)
                .body("preference.language", equalTo("en"))
                .body("preference.appearance", nullValue())
                .body("home", notNullValue())
                .body("home.headline.de", equalTo("Willkommen, Anna Schmidt!"));

        // deleting the stay flips back to vacant, and `home` leaves with it
        given().header("Authorization", "Bearer " + token)
                .when().delete("/api/manager/stays/" + stayId)
                .then().statusCode(204);
        content()
                .body("occupancy", equalTo("VACANT"))
                .body("guestDisplayName", nullValue())
                .body("home", nullValue())
                // REQ-945: the property-scoped half survives the vacancy episode
                .body("property.name", equalTo("Meine Unterkunft"))
                .body("menu.items", hasSize(5));
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
        content()
                .body("menu.items", hasSize(4))
                .body("menu.items.key", not(org.hamcrest.Matchers.hasItem("smart-home")));
    }

    @Test
    void homeTemplatesResolvePerLanguageInThePropertyTimezoneAndNoBracesLeaveTheServer() {
        String token = adminToken();
        OffsetDateTime checkOut = createActiveStay(token);
        // the PRD's acceptance card — checkout |time per language locale
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(List.of(Map.of("sortIndex", 0, "icon", "phosphorKey", "targetMenuItemKey", "info",
                        "title", Map.of("de", "Bis {stay.checkOut|time} Uhr am Abreisetag",
                                "en", "Until {stay.checkOut|time} on departure day"),
                        "description", Map.of("de", "Für {stay.displayName}", "en", "For {stay.displayName}"))))
                .when().put("/api/manager/action-cards")
                .then().statusCode(200);
        try {
            String expectedDe = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                    .withLocale(Locale.forLanguageTag("de-DE")).withZone(PROPERTY_TZ)
                    .format(checkOut.toInstant());
            String expectedEn = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                    .withLocale(Locale.forLanguageTag("en-GB")).withZone(PROPERTY_TZ)
                    .format(checkOut.toInstant());
            String body = content()
                    .body("home.headline.de", equalTo("Willkommen, Anna Schmidt!")) // seed greeting resolved
                    .body("home.actionCards", hasSize(1))
                    .body("home.actionCards[0].title.de", equalTo("Bis " + expectedDe + " Uhr am Abreisetag"))
                    .body("home.actionCards[0].title.en", equalTo("Until " + expectedEn + " on departure day"))
                    .body("home.actionCards[0].description.de", equalTo("Für Anna Schmidt"))
                    .body("home.actionCards[0].targetKey", equalTo("info"))
                    .body("home.actionCards[0].targetRoute", equalTo("/info"))
                    .extract().asString();
            // "no braces leave the server": no placeholder syntax survives in
            // any VALUE (the JSON structure itself of course uses braces)
            assertFalse(body.contains("{stay.") || body.contains("{property."),
                    "a raw template placeholder leaked into the payload: " + body);
        } finally {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(List.of())
                    .when().put("/api/manager/action-cards")
                    .then().statusCode(200);
        }
    }

    @Test
    void zeroCardsIsValidHomeContent() {
        createActiveStay(adminToken());
        content()
                .body("home.actionCards", hasSize(0))
                .body("home.subtitle.de", equalTo("Herzlich willkommen"));
    }
}
