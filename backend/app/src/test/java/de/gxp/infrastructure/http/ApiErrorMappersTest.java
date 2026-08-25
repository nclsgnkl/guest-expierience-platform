package de.gxp.infrastructure.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * REQ-325: contract violations and malformed JSON answer with the shared
 * ApiError shape, not a framework-default body. Exercised against the login
 * operation (@PermitAll, so the mapping is observable unauthenticated).
 */
@QuarkusTest
class ApiErrorMappersTest {

    @Test
    void beanValidationFailureReturnsTheApiErrorShape() {
        given().contentType("application/json")
                .body("{}") // password is required by the contract
                .when().post("/api/manager/login")
                .then().statusCode(400)
                .body("code", equalTo("validation-failed"))
                .body("message", notNullValue())
                .body("details", notNullValue())
                .body("details[0].message", notNullValue());
    }

    @Test
    void malformedJsonReturnsTheApiErrorShapeWithoutEchoingTheBody() {
        given().contentType("application/json")
                .body("{this is not json")
                .when().post("/api/manager/login")
                .then().statusCode(400)
                .body("code", equalTo("malformed-request-body"))
                // the parser message quotes raw input — it must not be forwarded
                .body("message", not(org.hamcrest.Matchers.containsString("this is not json")));
    }
}
