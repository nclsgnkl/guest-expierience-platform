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
            // Fresh dev DB → the AdminPasswordSeeder needs a first-start password.
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

    @Test
    void seedsExactlyOneAuthenticatingDeviceAndTheExampleContent() {
        assertEquals(1L, count("select count(*) from device"),
                "the dev seed must create exactly one device");

        given().header("Authorization", "Bearer " + DevDataSeeder.DEV_DEVICE_TOKEN)
                .when().get("/api/guest/session")
                .then().statusCode(200)
                .body("occupancy", equalTo("OCCUPIED"))
                .body("guestDisplayName", equalTo("Anna & Tom"))
                .body("property.brand.primary", equalTo("#f45aa8"))
                .body("property.brand.primaryDeep", equalTo("#cc49af"))
                .body("property.brand.secondary", equalTo("#8a5cf6"))
                .body("menu.items", hasSize(5))
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
        long devices = count("select count(*) from device");
        long stays = count("select count(*) from stay");
        long cards = count("select count(*) from action_card_config");

        QuarkusTransaction.requiringNew().run(seeder::seedIfMissing);

        assertEquals(devices, count("select count(*) from device"), "device seed must be idempotent");
        assertEquals(stays, count("select count(*) from stay"), "stay seed must be idempotent");
        assertEquals(cards, count("select count(*) from action_card_config"), "card seed must be idempotent");
    }

    private long count(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
