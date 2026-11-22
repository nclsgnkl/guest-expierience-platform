package de.gxp.guest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.DeviceFixtures;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Module content on the wire (module-content REQ-964, REQ-962, REQ-963):
 * per offered language, per occupancy state, in the authored order, and only
 * for a section the guest is actually offered.
 *
 * <p>Rows are seeded with native SQL because there IS no write path — D16
 * makes manager-authored module content the successor feature's, so this Part
 * ships a read and a seeder and nothing else. That is the same reason the
 * clean-up below is SQL too.
 *
 * <p>The vacant case is the one worth reading twice: a vacant room still
 * carries its modules (they are property-scoped, A2), so the raw
 * {@code {stay.checkOut|…}} template IS present in that payload. That is the
 * documented state (02-design.md §2.5), not a defect — nobody sees it, a
 * vacant kiosk is on {@code /standby} — and it is exactly why
 * {@code GuestControl}'s "no braces ever leave the server" sentence is scoped
 * to {@code home} and must not be generalised.
 */
@QuarkusTest
class GuestModuleContentTest {

    private static final ZoneId PROPERTY_TZ = ZoneId.of("Europe/Berlin");

    /** Deliberately unique: case (c) searches the WHOLE payload text for it. */
    private static final String WIFI_VALUE = "Meeresrauschen-Fixture-77";

    private static final String CHECK_OUT_DE = "{stay.checkOut|date}, {stay.checkOut|time} Uhr";
    private static final String CHECK_OUT_EN = "{stay.checkOut|date}, {stay.checkOut|time}";

    @Inject
    EntityManager entityManager;

    private long roomId;
    private String deviceToken;

    @BeforeEach
    void seed() {
        roomId = ((Number) entityManager
                .createNativeQuery("select id from room where code = '1'")
                .getSingleResult()).longValue();
        deviceToken = DeviceFixtures.seedDevice(entityManager, roomId, "Module-Kiosk");
        QuarkusTransaction.requiringNew().run(() -> {
            // seeded OUT of sort order on purpose: case (d) proves the
            // delivered order comes from sort_index, not from insertion.
            seedRow(1, "WLAN-Passwort", WIFI_VALUE, "Wi-Fi password", WIFI_VALUE);
            seedRow(0, "Check-out", CHECK_OUT_DE, "Check-out", CHECK_OUT_EN);
        });
    }

    @AfterEach
    void cleanUp() {
        DeviceFixtures.deleteDevice(entityManager, deviceToken);
        QuarkusTransaction.requiringNew().run(() -> {
            // the DB cascades info_module_row_translation
            entityManager.createNativeQuery("delete from info_module_row where menu_item_key = 'info'")
                    .executeUpdate();
            entityManager.createNativeQuery("delete from stay where room_id = :id")
                    .setParameter("id", roomId).executeUpdate();
            entityManager.createNativeQuery("delete from menu_item_config where menu_item_key = 'info'")
                    .executeUpdate();
        });
    }

    private void seedRow(int sortIndex, String labelDe, String valueDe, String labelEn, String valueEn) {
        Number rowId = (Number) entityManager.createNativeQuery("insert into info_module_row"
                + " (menu_item_key, sort_index) values ('info', :sortIndex) returning id")
                .setParameter("sortIndex", sortIndex)
                .getSingleResult();
        seedTranslation(rowId.longValue(), "de", labelDe, valueDe);
        seedTranslation(rowId.longValue(), "en", labelEn, valueEn);
    }

    private void seedTranslation(long rowId, String language, String label, String value) {
        entityManager.createNativeQuery("insert into info_module_row_translation"
                + " (row_id, language, label, value) values (:rowId, :language, :label, :value)")
                .setParameter("rowId", rowId)
                .setParameter("language", language)
                .setParameter("label", label)
                .setParameter("value", value)
                .executeUpdate();
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

    private static String localized(OffsetDateTime checkOut, String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale).withZone(PROPERTY_TZ).format(checkOut.toInstant());
        String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(locale).withZone(PROPERTY_TZ).format(checkOut.toInstant());
        return date + ", " + time;
    }

    /** (a) occupied, both languages offered — resolved, never raw. */
    @Test
    void anOccupiedRoomGetsEveryRowResolvedPerOfferedLanguage() {
        OffsetDateTime checkOut = createActiveStay(ManagerFixtures.login());

        String body = content()
                .body("menu.items.find { it.key == 'info' }.module.type", equalTo("info"))
                .body("menu.items.find { it.key == 'info' }.module.rows", hasSize(2))
                .body("menu.items.find { it.key == 'info' }.module.rows[0].label.de", equalTo("Check-out"))
                .body("menu.items.find { it.key == 'info' }.module.rows[0].label.en", equalTo("Check-out"))
                .body("menu.items.find { it.key == 'info' }.module.rows[0].value.de",
                        equalTo(localized(checkOut, "de-DE") + " Uhr"))
                .body("menu.items.find { it.key == 'info' }.module.rows[0].value.en",
                        equalTo(localized(checkOut, "en-GB")))
                // row 2 carries no placeholder, so it is identical in both
                .body("menu.items.find { it.key == 'info' }.module.rows[1].value.de", equalTo(WIFI_VALUE))
                .body("menu.items.find { it.key == 'info' }.module.rows[1].value.en", equalTo(WIFI_VALUE))
                // every other item is untouched by module attachment
                .body("menu.items.find { it.key == 'umgebung' }.module", org.hamcrest.Matchers.nullValue())
                .extract().asString();

        assertFalse(body.contains("{stay.") || body.contains("{property."),
                "an occupied payload must carry no raw template: " + body);
    }

    /** (b) vacant — the module is still delivered, with the template RAW. */
    @Test
    void aVacantRoomStillCarriesItsModulesAndTheStayTemplateStaysRaw() {
        String body = content()
                .body("occupancy", equalTo("VACANT"))
                .body("home", org.hamcrest.Matchers.nullValue())
                .body("menu.items.find { it.key == 'info' }.module", notNullValue())
                .body("menu.items.find { it.key == 'info' }.module.rows", hasSize(2))
                // property-scoped content resolves the same vacant or not
                .body("menu.items.find { it.key == 'info' }.module.rows[1].value.de", equalTo(WIFI_VALUE))
                // the documented state: no stay, so the placeholder resolves to itself
                .body("menu.items.find { it.key == 'info' }.module.rows[0].value.de", equalTo(CHECK_OUT_DE))
                .body("menu.items.find { it.key == 'info' }.module.rows[0].value.en", equalTo(CHECK_OUT_EN))
                .extract().asString();

        assertTrue(body.contains("{stay.checkOut|time}"),
                "02-design.md §2.5: this is the documented vacant outcome, not a defect");
    }

    /** (c) REQ-962 — a section disabled at property scope takes its content with it. */
    @Test
    void aDisabledSectionCarriesNeitherAnItemNorItsModuleContent() {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("insert into menu_item_config (menu_item_key, room_id, enabled)"
                        + " values ('info', null, false)")
                .executeUpdate());

        String body = content()
                .body("menu.items.key", not(org.hamcrest.Matchers.hasItem("info")))
                .extract().asString();

        assertFalse(body.contains(WIFI_VALUE),
                "module content of a section the guest is not offered must not appear ANYWHERE"
                        + " in the payload: " + body);
        assertFalse(body.contains("Check-out"),
                "module content of a section the guest is not offered must not appear ANYWHERE"
                        + " in the payload: " + body);
    }

    /** (d) REQ-962 one level down — the delivered order is the seeded sort_index order. */
    @Test
    void rowsArriveInTheSeededSortIndexOrderNotTheInsertionOrder() {
        content()
                .body("menu.items.find { it.key == 'info' }.module.rows.label.de",
                        equalTo(java.util.List.of("Check-out", "WLAN-Passwort")));
    }
}
