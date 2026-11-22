package de.gxp.infrastructure.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * REQ-603, default (LAN-only) profile: no trusted proxy is configured, so
 * every {@code Forwarded} / {@code X-Forwarded-*} /
 * {@code X-Forwarded-Trusted-Proxy} header is ignored — the socket peer stays
 * the attributed address and via-proxy is false. This is the spoof case; the
 * honoured case lives in {@code de.gxp.auth.TrustedProxyLoginTest} under the
 * trusted-proxy test profile.
 */
@QuarkusTest
class ClientAttributionTest {

    @Test
    void withoutATrustedProxyForwardedHeadersAreIgnored() {
        given().header("Forwarded", "for=203.0.113.9")
                .header("X-Forwarded-For", "203.0.113.9")
                .header("X-Forwarded-Trusted-Proxy", "true")
                .when().get("/test/client-attribution")
                .then().statusCode(200)
                .body("address", equalTo("127.0.0.1"))
                .body("viaTrustedProxy", equalTo(false));
    }

    @Test
    void thePlainPeerIsAttributedToItself() {
        given().when().get("/test/client-attribution")
                .then().statusCode(200)
                .body("address", equalTo("127.0.0.1"))
                .body("viaTrustedProxy", equalTo(false));
    }
}
