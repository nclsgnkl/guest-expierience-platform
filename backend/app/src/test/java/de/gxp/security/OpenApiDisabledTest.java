package de.gxp.security;

import static io.restassured.RestAssured.given;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Proof for the "no dev-time surface in %prod" decision (2026-08-19,
 * resolves backend-foundation 06-review finding #1).
 *
 * <p>Tests run in {@code %test}, so the {@code %prod.} prefix wiring itself
 * cannot be asserted here — that stays a config-review item plus the compose
 * smoke ({@code /q/openapi} → 404 while {@code /api/system/ready} → 200).
 * What this test DOES prove, on the exact Quarkus version we ship: the
 * property name {@code quarkus.smallrye-openapi.enabled} is real and its
 * {@code false} value answers 404 on {@code /q/openapi} without harming the
 * application routes. A misspelled or renamed property would silently no-op —
 * this test is the tripwire for that on every future Quarkus bump.
 */
@QuarkusTest
@TestProfile(OpenApiDisabledTest.OpenApiDisabledProfile.class)
class OpenApiDisabledTest {

    public static class OpenApiDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Same key %prod sets in application.properties (runtime config).
            return Map.of("quarkus.smallrye-openapi.enabled", "false");
        }
    }

    @Test
    void openApiDocumentAnswers404WhenDisabled() {
        given()
                .when().get("/q/openapi")
                .then()
                .statusCode(404);
    }

    @Test
    void applicationRoutesAreUnaffected() {
        given()
                .when().get("/api/system/ready")
                .then()
                .statusCode(200);
    }
}
