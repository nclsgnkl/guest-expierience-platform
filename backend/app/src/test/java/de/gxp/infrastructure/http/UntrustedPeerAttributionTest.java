package de.gxp.infrastructure.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.UntrustedPeerProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * REQ-603 with the switch ON and the caller NOT the trusted proxy
 * ({@link UntrustedPeerProfile}): the headers are skipped by Quarkus before
 * any of them is parsed, and the via-proxy stamp is set to {@code false}
 * regardless of what the client sent. This is the case the default-profile
 * test cannot prove (there the handler is not even installed) and the
 * trusted-profile test cannot reach (there the peer IS the proxy).
 */
@QuarkusTest
@TestProfile(UntrustedPeerProfile.class)
class UntrustedPeerAttributionTest {

    @Test
    void aForwardedHeaderFromAnUntrustedPeerIsIgnored() {
        given().header("Forwarded", "for=203.0.113.9")
                .header("X-Forwarded-For", "203.0.113.9")
                .when().get("/test/client-attribution")
                .then().statusCode(200)
                .body("address", equalTo("127.0.0.1"))
                .body("viaTrustedProxy", equalTo(false));
    }

    @Test
    void aClientSentTrustedProxyHeaderFromAnUntrustedPeerIsOverwrittenToFalse() {
        given().header("Forwarded", "for=203.0.113.9")
                .header("X-Forwarded-Trusted-Proxy", "true")
                .when().get("/test/client-attribution")
                .then().statusCode(200)
                .body("address", equalTo("127.0.0.1"))
                .body("viaTrustedProxy", equalTo(false));
    }
}
