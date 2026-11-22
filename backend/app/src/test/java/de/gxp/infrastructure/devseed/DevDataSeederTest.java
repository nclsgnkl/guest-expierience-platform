package de.gxp.infrastructure.devseed;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * REQ-503 — the {@code %dev} seed, exercised under a dev-config-profile
 * augmentation (the only build in which the {@code @IfBuildProfile("dev")}
 * bean exists at all; the plain {@code %test} suite proves the inverse —
 * {@code MigrationTest} keeps asserting zero devices there, REQ-504).
 *
 * <p>The profile points Dev Services at its own database name so this
 * instance's seed can never leak into the suite's shared {@code %test}
 * database.
 */
@QuarkusTest
@TestProfile(DevDataSeederTest.DevSeedProfile.class)
class DevDataSeederTest {

    public static class DevSeedProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "dev";
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> overrides = new java.util.HashMap<>();
            // Fresh dev DB → the FirstUserSeeder needs a first-start password.
            overrides.put("gxp.admin.password", "dev-seed-test-password-1");
            String mainUrl = System.getProperty("quarkus.datasource.jdbc.url");
            if (mainUrl == null) {
                // Dev Services box: own database name keeps the %test DB seedless.
                overrides.put("quarkus.datasource.devservices.db-name", "gxp_devseed");
            } else {
                // Explicit throwaway-Postgres run (this repo's documented CI
                // workaround for a sudo-only Docker socket): same server,
                // dedicated database — create `gxp_devseed` alongside the main
                // test database before running the suite.
                overrides.put("quarkus.datasource.jdbc.url",
                        mainUrl.substring(0, mainUrl.lastIndexOf('/') + 1) + "gxp_devseed");
            }
            return overrides;
        }
    }

    @Inject
    DevDataSeeder seeder;

    @Inject
    EntityManager entityManager;

    private static final String PROPERTY_SCOPE_ENABLED =
            "select count(*) from menu_item_config where room_id is null and enabled = true";

    @Test
    void seedsExactlyOneAuthenticatingDeviceAndTheExampleContent() {
        assertEquals(1L, scalar("select count(*) from device"),
                "the dev seed must create exactly one device");

        given().header("Authorization", "Bearer " + DevDataSeeder.DEV_DEVICE_TOKEN)
                .when().get("/api/guest/content")
                .then().statusCode(200)
                .body("occupancy", equalTo("OCCUPIED"))
                .body("guestDisplayName", equalTo("Anna & Tom"))
                .body("property.brand.primary", equalTo("#f45aa8"))
                .body("property.brand.primaryDeep", equalTo("#cc49af"))
                .body("property.brand.secondary", equalTo("#8a5cf6"))
                // REQ-906: the whole catalogue, V10's placeholder fixtures
                // included — %dev flips their property-scope opt-out rows on,
                // so overflow and the popover are the default dev experience.
                .body("menu.items", hasSize(13))
                .body("standby.headline.de", equalTo("Meine Unterkunft"));

        given().header("Authorization", "Bearer " + DevDataSeeder.DEV_DEVICE_TOKEN)
                .when().get("/api/guest/content")
                .then().statusCode(200)
                .body("home.actionCards", hasSize(4))
                .body("home.headline.de", equalTo("Willkommen im Airbnb von Anna & Tom!"))
                .body("home.headline.en", equalTo("Welcome to Anna & Tom's Airbnb!"))
                .body("home.subtitle.de", equalTo("Schön, dass ihr da seid"))
                .body("home.actionCards[0].icon", equalTo("phosphorKey"))
                .body("home.actionCards[0].title.de", equalTo("Check-Out"));
    }

    /** GPath to the `info` catalogue item — by key, never by position. */
    private static final String INFO_MODULE = "menu.items.find { it.key == 'info' }.module";

    /**
     * REQ-976 and REQ-967 together, because they are the same fact seen twice.
     * The {@code %dev} seed authors info.md's two-row copy table against the
     * {@code info} key, and row 1's value is RESOLVED server-side per offered
     * language (REQ-964) — so the check-out time it renders must be the very
     * time Home's first action card renders, both read from the one stay.
     */
    @Test
    void seedsTheInfoModuleAndRendersOneStoredCheckOutTimeTwice() {
        var body = given().header("Authorization", "Bearer " + DevDataSeeder.DEV_DEVICE_TOKEN)
                .when().get("/api/guest/content")
                .then().statusCode(200)
                .extract().body().jsonPath();

        assertEquals("info", body.getString(INFO_MODULE + ".type"),
                "the wire discriminant is the mapping key, never the schema name");
        assertEquals(2, body.getList(INFO_MODULE + ".rows").size(),
                "the seed authors exactly info.md's two rows");
        assertEquals("Check-out", body.getString(INFO_MODULE + ".rows[0].label.de"));
        assertEquals("WLAN-Passwort", body.getString(INFO_MODULE + ".rows[1].label.de"));
        assertEquals("Wi-Fi password", body.getString(INFO_MODULE + ".rows[1].label.en"));
        assertEquals("Meeresrauschen-77", body.getString(INFO_MODULE + ".rows[1].value.de"));
        assertEquals("Meeresrauschen-77", body.getString(INFO_MODULE + ".rows[1].value.en"));

        String checkOutDe = body.getString(INFO_MODULE + ".rows[0].value.de");
        String checkOutEn = body.getString(INFO_MODULE + ".rows[0].value.en");
        assertFalse(checkOutDe.contains("{"), "an occupied room's module carries no raw template");
        assertFalse(checkOutEn.contains("{"), "an occupied room's module carries no raw template");
        assertFalse(checkOutEn.contains("Uhr"), "the English column carries no \"Uhr\" — info.md's copy table");

        Matcher time = Pattern.compile("\\d{1,2}:\\d{2}").matcher(checkOutDe);
        assertTrue(time.find(), "row 1 must render a resolved check-out time, not a placeholder");
        assertEquals("Bis " + time.group() + " Uhr am Abreisetag",
                body.getString("home.actionCards[0].description.de"),
                "REQ-967: the check-out time is stored ONCE, in the stay, and rendered twice");
    }

    @Test
    void reseedingIsAFullNoOp() {
        long devices = scalar("select count(*) from device");
        long stays = scalar("select count(*) from stay");
        long cards = scalar("select count(*) from action_card_config");
        long infoRows = scalar("select count(*) from info_module_row");
        long enabledConfigs = scalar(PROPERTY_SCOPE_ENABLED);

        // Arrange a developer turning one section off after the first seed.
        // Without it this case could not fail: enableCatalogueFixtures() is an
        // idempotent UPDATE … SET enabled = true, so the count below is
        // identical whether or not it ran. The row is chosen key-free via
        // min(id) — REQ-903 forbids a test depending on a fixture key.
        long turnedOff = scalar("select min(id) from menu_item_config"
                + " where room_id is null and enabled = true");
        assertEquals(1, setEnabled(turnedOff, false),
                "the arrangement must turn exactly one property-scope row off");
        assertEquals(enabledConfigs - 1, scalar(PROPERTY_SCOPE_ENABLED),
                "the arrangement must be visible before re-seeding");
        try {
            QuarkusTransaction.requiringNew().run(seeder::seedIfMissing);

            assertEquals(devices, scalar("select count(*) from device"), "device seed must be idempotent");
            assertEquals(stays, scalar("select count(*) from stay"), "stay seed must be idempotent");
            assertEquals(cards, scalar("select count(*) from action_card_config"), "card seed must be idempotent");
            assertEquals(infoRows, scalar("select count(*) from info_module_row"),
                    "info module seed must be idempotent");
            assertEquals(enabledConfigs - 1, scalar(PROPERTY_SCOPE_ENABLED),
                    "re-seeding must not re-enable what a developer turned off — the"
                            + " catalogue-fixture enablement sits inside the devDeviceExists()"
                            + " early return");
        } finally {
            // Restore the exact row, so the other case in this class still sees
            // the full %dev catalogue whichever order the two run in.
            setEnabled(turnedOff, true);
        }
    }

    /** Single numeric result of a native query. */
    private long scalar(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private int setEnabled(long configId, boolean enabled) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager
                .createNativeQuery("update menu_item_config set enabled = :enabled where id = :id")
                .setParameter("enabled", enabled)
                .setParameter("id", configId)
                .executeUpdate());
    }
}
