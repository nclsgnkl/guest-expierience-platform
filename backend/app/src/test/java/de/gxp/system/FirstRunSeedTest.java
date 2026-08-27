package de.gxp.system;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import de.gxp.infrastructure.testsupport.ManagerFixtures;

/**
 * T-24 (REQ-334): a fresh database + GXP_ADMIN_PASSWORD yields a USABLE
 * instance with zero manual database work — neutral property, offered
 * languages/appearances with defaults, the 13-row catalogue, one default
 * room, and a seeded first account (the login below IS the credential
 * proof). Everything is asserted through the real API, authenticated the
 * way a first manager session would be.
 */
@QuarkusTest
class FirstRunSeedTest {

    @Test
    void aFreshInstanceIsCompletelyUsable() {
        // 1. the seeded account logs in (the user feature's FirstUserSeeder ran on boot)
        String token = ManagerFixtures.login();

        // 2. neutral property with the pinned defaults (nothing property-
        //    specific — principle 1: placeholder copy the manager edits)
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/property")
                .then().statusCode(200)
                .body("mainLanguage", equalTo("de"))
                .body("timezone", equalTo("Europe/Berlin"))
                .body("defaultAppearance", equalTo("light"))
                .body("languages", hasSize(2))
                .body("languages[0].code", equalTo("de"))
                .body("languages[1].code", equalTo("en"))
                .body("appearances", hasSize(2))
                .body("appearances[0].mode", equalTo("light"))
                .body("appearances[1].mode", equalTo("dark"));

        // 3. the release catalogue: 13 rows since V10 — the five real kiosk
        //    sections first (they own default_sort_index 0..4), then the eight
        //    disposable placeholder fixtures. Deliberately NOT naming a
        //    fixture key: REQ-903 says removing them must be one migration
        //    plus a registered-icons.ts edit, so no test may depend on one.
        //    DEFINITIONS ONLY — never the menu_item_config rows: this class
        //    shares the suite's database and has no @AfterEach, so asserting
        //    enablement here would reintroduce the order-dependence
        //    MenuFixtures.restoreCatalogueOptOut() removes (02-design.md §3.3).
        //    D5's enablement state is asserted in CatalogueExpansionTest.
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/menu/catalogue")
                .then().statusCode(200)
                .body("$", hasSize(13))
                .body("key[0..4]",
                        equalTo(java.util.List.of("home", "info", "umgebung", "smart-home", "services")));

        // 4. one default room exists
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/rooms")
                .then().statusCode(200)
                .body("findAll { it.code == '1' }", hasSize(1));

        // 5. platform metadata (V1) is untouched by V2+ (REQ-302);
        //    authenticated since device-pairing-and-guest-api (REQ-432)
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/system/info")
                .then().statusCode(200)
                .body("instanceName", equalTo("Guest Experience Platform"));
    }
}
