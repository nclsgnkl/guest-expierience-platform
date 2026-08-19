package de.gxp.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.test.junit.QuarkusTest;

/**
 * T-23 (REQ-332): anyone on the guest WiFi gets a 401 challenge — one
 * endpoint per manager Resource, anonymous. (Wrong/expired-token variants
 * are covered in {@code AuthResourceTest}.)
 */
@QuarkusTest
class ManagerSurfaceDeniedTest {

    static Stream<String> managerEndpoints() {
        return Stream.of(
                "/api/manager/property",        // PropertyResource
                "/api/manager/rooms",           // RoomResource
                "/api/manager/menu/catalogue",  // MenuResource
                "/api/manager/action-cards",    // MenuResource (card side)
                "/api/manager/stays",           // StayResource
                "/api/manager/assets");         // AssetResource
    }

    @ParameterizedTest
    @MethodSource("managerEndpoints")
    void anonymousGetIsChallengedWith401(String path) {
        given()
                .when().get(path)
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
    }

    @org.junit.jupiter.api.Test
    void anonymousLogoutIsChallengedToo() {
        given()
                .when().post("/api/manager/logout")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
    }
}
