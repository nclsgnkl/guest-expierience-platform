package de.gxp.system;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * System endpoints over HTTP against the Dev Services database (REQ-211,
 * REQ-212, REQ-215, REQ-204). Paths are written out as the contract declares
 * them, so contract drift fails this suite, not just compilation.
 */
@QuarkusTest
class SystemResourceTest {

    @Test
    void healthAnswersUp() {
        given()
                .when().get("/api/system/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void readinessAnswersReadyWithDatabaseUp() {
        given()
                .when().get("/api/system/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("READY"));
    }

    @Test
    void infoReturnsSeededNameAndVersion() {
        given()
                .when().get("/api/system/info")
                .then()
                .statusCode(200)
                // value seeded by V1__foundation_baseline.sql — asserted against
                // the response; the application code never duplicates it
                .body("instanceName", equalTo("Guest Experience Platform"))
                .body("applicationVersion", not(emptyOrNullString()));
    }
}
