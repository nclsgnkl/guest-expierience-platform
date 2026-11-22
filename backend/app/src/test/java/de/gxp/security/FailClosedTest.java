package de.gxp.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Proof of the fail-closed security posture (REQ-213, REQ-215; revisited per
 * REQ-332), against the test-only {@link FailClosedControl}/
 * {@link FailClosedResource} pair.
 *
 * <p>Revisit (backend-domain-config): the foundation asserted 403 because no
 * authentication mechanism existed that could issue a challenge. The first
 * real mechanism ({@code BearerAuthMechanism}) landed earlier than the
 * foundation's guess — with it installed, an anonymous caller against
 * {@code @RolesAllowed} is now a 401 WITH the {@code WWW-Authenticate:
 * Bearer} challenge. The fail-closed contract is unchanged: anonymous is
 * denied everywhere but {@code @PermitAll}.
 */
@QuarkusTest
class FailClosedTest {

    @Test
    void permitAllMethodIsReachable() {
        given()
                .when().get("/test/fail-closed/permitted")
                .then()
                .statusCode(200)
                .body(equalTo("permitted"));
    }

    /**
     * The Boundary method is {@code @PermitAll}, so the endpoint check passes
     * and the denial can only come from {@code deny-unannotated-members} on the
     * Control — the point of this fixture. Leaving the Boundary unannotated
     * would make the global endpoint deny an equally good explanation.
     */
    @Test
    void unannotatedMethodInAnnotatedControlIsDenied() {
        given()
                .when().get("/test/fail-closed/unannotated")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
    }

    @Test
    void rolesAllowedMethodRejectsAnonymousCallerWithChallenge() {
        given()
                .when().get("/test/fail-closed/admin-only")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
    }
}
