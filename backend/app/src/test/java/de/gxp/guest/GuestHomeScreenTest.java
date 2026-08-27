package de.gxp.guest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.infrastructure.testsupport.MenuFixtures;

/**
 * T-22 (REQ-419, REQ-420, REQ-421): vacant → 409 room-vacant; templates
 * resolve per offered language with {@code |time}/{@code |date} in the
 * property timezone and each language's locale; NO braces in any payload;
 * 0–4 cards; resolved-disabled home → 404; unknown screen key → 404.
 */
@QuarkusTest
class GuestHomeScreenTest {

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
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Home-Kiosk");
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from stay where room_id = :id")
                    .setParameter("id", roomId).executeUpdate();
            entityManager.createNativeQuery(
                    "delete from menu_item_config where menu_item_key = 'home'").executeUpdate();
        });
        // card sets are cleared through the API in the tests that create them
        // This class replaces the property-scope menu config (a whole-set
        // replace), which deletes V10's eight `enabled = false` opt-out rows.
        // Restore them, or the placeholder catalogue fixtures arrive ENABLED
        // at whatever class runs next in the shared database (02-design.md §3.3).
        MenuFixtures.restoreCatalogueOptOut(entityManager);
    }

    private static String adminToken() {
        return ManagerFixtures.login();
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
    void aVacantRoomAnswersTheNamed409() {
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/screens/home")
                .then().statusCode(409)
                .body("code", equalTo("room-vacant"));
    }

    @Test
    void templatesResolvePerLanguageInThePropertyTimezoneAndNoBracesLeaveTheServer() {
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
            String body = given().header("Authorization", "Bearer " + deviceToken)
                    .when().get("/api/guest/screens/home")
                    .then().statusCode(200)
                    .body("headline.de", equalTo("Willkommen, Anna Schmidt!")) // seed greeting resolved
                    .body("actionCards", hasSize(1))
                    .body("actionCards[0].title.de", equalTo("Bis " + expectedDe + " Uhr am Abreisetag"))
                    .body("actionCards[0].title.en", equalTo("Until " + expectedEn + " on departure day"))
                    .body("actionCards[0].description.de", equalTo("Für Anna Schmidt"))
                    .body("actionCards[0].targetKey", equalTo("info"))
                    .body("actionCards[0].targetRoute", equalTo("/info"))
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
    void zeroCardsIsAValidHomeScreen() {
        createActiveStay(adminToken());
        given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/screens/home")
                .then().statusCode(200)
                .body("actionCards", hasSize(0))
                .body("subtitle.de", equalTo("Herzlich willkommen"));
    }

    @Test
    void aResolvedDisabledHomeIs404() {
        createActiveStay(adminToken());
        // disable 'home' at the property scope through the real API
        given().header("Authorization", "Bearer " + adminToken())
                .contentType("application/json")
                .body(List.of(Map.of("menuItemKey", "home", "enabled", false)))
                .when().put("/api/manager/menu-config")
                .then().statusCode(200);
        try {
            given().header("Authorization", "Bearer " + deviceToken)
                    .when().get("/api/guest/screens/home")
                    .then().statusCode(404)
                    .body("code", equalTo("screen-not-found"));
        } finally {
            given().header("Authorization", "Bearer " + adminToken())
                    .contentType("application/json").body(List.of())
                    .when().put("/api/manager/menu-config")
                    .then().statusCode(200);
        }
    }

    @Test
    void anUnknownScreenKeyIs404() {
        // decision 7: only implemented screen keys exist as paths
        int status = given().header("Authorization", "Bearer " + deviceToken)
                .when().get("/api/guest/screens/wifi")
                .then().extract().statusCode();
        assertEquals(404, status);
    }
}
