package de.gxp.property;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Property endpoint (REQ-303, REQ-304, REQ-311, REQ-317..319, REQ-334):
 * seeded neutral row, raw templates on read, full replace roundtrip, every
 * 400 path, and the DB-level single-row invariant. The roundtrip test
 * restores the seed state (tests share one database).
 */
@QuarkusTest
class PropertyResourceTest {

    @Inject
    EntityManager entityManager;

    private static String token() {
        return given().contentType("application/json")
                .body(Map.of("password", "test-admin-password-1"))
                .when().post("/api/manager/login")
                .then().statusCode(200).extract().path("token");
    }

    private static Map<String, Object> currentProperty(String token) {
        return given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/property")
                .then().statusCode(200)
                .extract().jsonPath().getMap("$");
    }

    @Test
    void seededNeutralPropertyIsReturnedWithRawTemplates() {
        String token = token();
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/property")
                .then().statusCode(200)
                .body("name", equalTo("Meine Unterkunft"))
                .body("mainLanguage", equalTo("de"))
                .body("timezone", equalTo("Europe/Berlin"))
                .body("brandPrimary", equalTo("#5b6472"))
                .body("defaultAppearance", equalTo("light"))
                .body("languages[0].code", equalTo("de"))
                .body("languages[0].shortLabel", equalTo("DE"))
                .body("languages[0].label", equalTo("Deutsch"))
                .body("languages[0].locale", equalTo("de-DE"))
                .body("languages[1].code", equalTo("en"))
                .body("languages[1].locale", equalTo("en-GB"))
                .body("appearances[0].mode", equalTo("light"))
                .body("appearances[0].icon", equalTo("phosphorSun"))
                .body("appearances[1].mode", equalTo("dark"))
                // templates come back RAW — the manager edits templates
                .body("headline.de", containsString("{stay.displayName}"))
                .body("standbyHeadline.de", equalTo("{property.name}"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullReplaceRoundtrip() {
        String token = token();
        String original = given().header("Authorization", "Bearer " + token)
                .when().get("/api/manager/property")
                .then().statusCode(200).extract().asString();
        try {
            Map<String, Object> updated = currentProperty(token);
            updated.put("name", "Roundtrip-Haus");
            updated.put("timezone", "Europe/Vienna");
            updated.put("brandPrimary", "#f45aa8");
            ((Map<String, Object>) updated.get("headline")).put("en", "Welcome, {stay.displayName}!");
            ((Map<String, Object>) updated.get("standbyMessage")).put("en", "Welcome to {property.name}");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(updated)
                    .when().put("/api/manager/property")
                    .then().statusCode(200)
                    .body("name", equalTo("Roundtrip-Haus"))
                    .body("timezone", equalTo("Europe/Vienna"));

            given().header("Authorization", "Bearer " + token)
                    .when().get("/api/manager/property")
                    .then().statusCode(200)
                    .body("name", equalTo("Roundtrip-Haus"))
                    .body("brandPrimary", equalTo("#f45aa8"))
                    .body("headline.en", equalTo("Welcome, {stay.displayName}!"))
                    .body("standbyMessage.en", equalTo("Welcome to {property.name}"));
        } finally {
            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(original)
                    .when().put("/api/manager/property")
                    .then().statusCode(200);
        }
    }

    @Test
    void invalidTimezoneIs400() {
        String token = token();
        Map<String, Object> body = currentProperty(token);
        body.put("timezone", "Mars/OlympusMons");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(body)
                .when().put("/api/manager/property")
                .then().statusCode(400)
                .body("code", equalTo("invalid-timezone"));
    }

    @Test
    void defaultLanguageOutsideTheOfferedSetIs400() {
        String token = token();
        Map<String, Object> body = currentProperty(token);
        body.put("mainLanguage", "en");
        body.put("languages", List.of(
                Map.of("code", "de", "shortLabel", "DE", "label", "Deutsch", "locale", "de-DE")));
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(body)
                .when().put("/api/manager/property")
                .then().statusCode(400)
                .body("code", equalTo("default-not-offered"));
    }

    @Test
    void unsupportedOfferedLanguageIs400() {
        String token = token();
        Map<String, Object> body = currentProperty(token);
        body.put("languages", List.of(
                Map.of("code", "fr", "shortLabel", "FR", "label", "Français", "locale", "fr-FR")));
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(body)
                .when().put("/api/manager/property")
                .then().statusCode(400); // contract enum: fr is not a LanguageCode
    }

    @Test
    void malformedHexColorIs400() {
        String token = token();
        Map<String, Object> body = currentProperty(token);
        body.put("brandPrimary", "eggplant");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(body)
                .when().put("/api/manager/property")
                .then().statusCode(400)
                .body("code", equalTo("validation-failed")); // contract pattern via bean validation
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownPlaceholderInAGreetingTemplateIs400NamingIt() {
        String token = token();
        Map<String, Object> body = currentProperty(token);
        ((Map<String, Object>) body.get("headline")).put("de", "Hi {stay.checkout}!");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(body)
                .when().put("/api/manager/property")
                .then().statusCode(400)
                .body("code", equalTo("unknown-placeholder"))
                .body("details[0].reference", equalTo("{stay.checkout}"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stayPlaceholderInAStandbyTemplateIs400() {
        String token = token();
        Map<String, Object> body = currentProperty(token);
        ((Map<String, Object>) body.get("standbyHeadline")).put("de", "Bye {stay.displayName}");
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(body)
                .when().put("/api/manager/property")
                .then().statusCode(400)
                .body("code", equalTo("unknown-placeholder"))
                .body("details[0].reference", equalTo("{stay.displayName}"));
    }

    @Test
    void unknownLogoAssetIs400() {
        String token = token();
        Map<String, Object> body = currentProperty(token);
        body.put("logoAssetId", 987654);
        given().header("Authorization", "Bearer " + token)
                .contentType("application/json").body(body)
                .when().put("/api/manager/property")
                .then().statusCode(400)
                .body("code", equalTo("unknown-asset"));
    }

    @Test
    void secondPropertyRowIsRejectedByTheDatabase() {
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("insert into property (id, name, main_language, timezone, brand_primary, "
                        + "brand_primary_deep, brand_secondary, default_appearance) values "
                        + "(2, 'x', 'de', 'Europe/Berlin', '#000000', '#000000', '#000000', 'light')")
                .executeUpdate()));
    }
}
