package de.gxp.infrastructure.devseed;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

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
                .when().get("/api/guest/session")
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
                .when().get("/api/guest/screens/home")
                .then().statusCode(200)
                .body("actionCards", hasSize(4))
                .body("headline.de", equalTo("Willkommen im Airbnb von Anna & Tom!"))
                .body("headline.en", equalTo("Welcome to Anna & Tom's Airbnb!"))
                .body("subtitle.de", equalTo("Schön, dass ihr da seid"))
                .body("actionCards[0].icon", equalTo("phosphorKey"))
                .body("actionCards[0].title.de", equalTo("Check-Out"));
    }

    @Test
    void reseedingIsAFullNoOp() {
        long devices = scalar("select count(*) from device");
        long stays = scalar("select count(*) from stay");
        long cards = scalar("select count(*) from action_card_config");
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
