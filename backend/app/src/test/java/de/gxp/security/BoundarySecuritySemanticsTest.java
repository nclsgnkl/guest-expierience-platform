package de.gxp.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.ManagerFixtures;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Pins the three Quarkus security semantics the Boundary-annotation model of
 * constitution v1.6.0 rests on. They were verified against the Quarkus
 * 3.33.3.1 bytecode; this test proves them at runtime, and keeps proving them
 * across upgrades.
 *
 * <ol>
 * <li>{@code quarkus.security.jaxrs.deny-unannotated-endpoints=true} is
 * <b>global</b>: an unannotated endpoint method is denied even when the
 * Control behind it carries {@code @PermitAll} — so a Boundary nobody
 * annotated fails closed, which {@code deny-unannotated-members} (class-
 * scoped) cannot do on an annotation-free bean.</li>
 * <li>An annotation on the <b>implementing</b> Boundary method <b>wins over
 * one on the interface it implements</b>, and an annotation-free Control does
 * not block the internal call. Every real Boundary implements a generated
 * {@code de.gxp.api.*Api} interface, so the fixture does too — and annotates
 * the interface the opposite way round
 * ({@link BoundaryAnnotatedApi}), because with only one candidate the test
 * could not tell "the implementation wins" from "the implementation is all
 * there is". Both directions are asserted: interface {@code @RolesAllowed} vs
 * impl {@code @PermitAll} must let anonymous through, and interface
 * {@code @PermitAll} vs impl {@code @RolesAllowed} must keep it out.</li>
 * <li>{@code @RolesAllowed} on the Boundary still challenges anonymous with
 * 401 and admits the matching authenticated identity.</li>
 * </ol>
 *
 * <p>The flag is BUILD_TIME and now ships in {@code application.properties},
 * so this test deliberately carries <b>no</b> {@code @TestProfile} override:
 * claim 1 fails the moment the shipped configuration loses the flag, which a
 * profile-local override would have hidden. The admin token is still minted
 * in-process through {@link AuthControl} rather than over HTTP — the assertions
 * here are about annotation resolution, and going through the real login
 * endpoint would make them depend on a second Boundary's annotation.
 */
@QuarkusTest
class BoundarySecuritySemanticsTest {

    /**
     * Read, never hardcoded: the seeded credential is whatever this instance
     * booted with, and a preceding {@code @TestProfile} class that fails to
     * start can leave its config in place for the next one (observed on this
     * box when Dev Services could not launch Postgres).
     */
    @ConfigProperty(name = "gxp.admin.password")
    String seededAdminPassword;

    /**
     * Claim 1 — global deny: FailClosedControl.permitted() IS {@code @PermitAll}
     * and {@link FailClosedResource#unannotatedOverPermitAllControl()} in front
     * of it is annotated nowhere at all, so nothing but the global flag can
     * deny it. This claim is only provable while that one endpoint stays
     * deliberately unannotated — see the warning on {@link FailClosedResource}.
     */
    @Test
    void unannotatedEndpointOverAPermitAllControlIsDenied() {
        given()
                .when().get("/test/fail-closed/unannotated-endpoint")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
    }

    /**
     * Claim 2 — the implementing method's {@code @PermitAll} beats the
     * {@code @RolesAllowed("admin")} on the interface method it overrides, and
     * the annotation-free Control does not block the internal call. If the
     * interface won, this would be 401.
     */
    @Test
    void permitAllOnTheBoundaryMethodBeatsRolesAllowedOnTheInterface() {
        given()
                .when().get("/test/boundary-security/permitted")
                .then()
                .statusCode(200)
                .body(equalTo("control-reached"));
    }

    /**
     * Claim 3a — the security-critical direction of the same rule:
     * {@code @RolesAllowed("admin")} on the implementing method beats the
     * interface method's {@code @PermitAll}, so anonymous is still challenged.
     * If the interface won, this endpoint would be wide open.
     */
    @Test
    void rolesAllowedOnTheBoundaryMethodBeatsPermitAllOnTheInterfaceAndDeniesAnonymous() {
        given()
                .when().get("/test/boundary-security/admin-only")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer"));
    }

    /** Claim 3b — and admits a valid admin token. */
    @Test
    void rolesAllowedOnTheBoundaryMethodAdmitsAValidAdminToken() {
        String token = ManagerFixtures.login(ManagerFixtures.USERNAME, seededAdminPassword);
        given().header("Authorization", "Bearer " + token)
                .when().get("/test/boundary-security/admin-only")
                .then()
                .statusCode(200)
                .body(equalTo("control-reached"));
    }
}
