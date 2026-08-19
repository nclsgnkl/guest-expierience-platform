package de.gxp.device;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * T-17 (REQ-429): {@code GET /api/pairing/ca} is public. The %test profile
 * has no TLS material (dev/test stay plain HTTP — decided 2026-08-19), so
 * this proves the clearly coded 404 branch; the 200/PEM branch is proven by
 * the compose smoke (`curl --cacert` handshake against the real material).
 */
@QuarkusTest
class PairingCaTest {

    @Test
    void withoutTlsMaterialTheCaEndpointAnswersAClearlyCoded404() {
        given()
                .when().get("/api/pairing/ca")
                .then().statusCode(404)
                .body("code", equalTo("ca-not-available"));
    }
}
