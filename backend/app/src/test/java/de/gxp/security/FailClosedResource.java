package de.gxp.security;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * TEST-ONLY Boundary for {@link FailClosedControl} (src/test, never shipped).
 * One delegating line per method, as everywhere; not part of the contract —
 * it exists solely so the security behavior is observable over HTTP.
 *
 * <p>The four methods are deliberately four different combinations, because
 * with {@code quarkus.security.jaxrs.deny-unannotated-endpoints=true} switched
 * on globally every unannotated endpoint answers 401 and a fixture that does
 * not vary the Boundary annotation can no longer tell WHICH mechanism denied
 * it:
 *
 * <ul>
 * <li>{@link #permitted()} — {@code @PermitAll} both sides: reachable.</li>
 * <li>{@link #unannotated()} — {@code @PermitAll} here, <b>unannotated on the
 * Control</b>: the endpoint check passes, so the only thing that can deny it is
 * the class-scoped {@code deny-unannotated-members} on a Control that annotates
 * its other members ({@link FailClosedTest}).</li>
 * <li>{@link #adminOnly()} — {@code @RolesAllowed("admin")} here: the 401 is
 * the role check, not the global default.</li>
 * <li>{@link #unannotatedOverPermitAllControl()} — <b>annotated nowhere</b>,
 * over a {@code @PermitAll} Control method. <b>It must stay that way.</b> It is
 * the only evidence that the global endpoint deny bites at all
 * ({@link BoundarySecuritySemanticsTest} claim 1) — the hole the class-scoped
 * {@code deny-unannotated-members} cannot close. The Boundary-annotation sweep
 * ({@code BoundaryAnnotationAuditTest}) covers {@code src/main} classes only,
 * so this fixture needs no exception there; do not "fix" it by annotating it.
 * </li>
 * </ul>
 */
@Path("/test/fail-closed")
public class FailClosedResource {

    private final FailClosedControl failClosedControl;

    FailClosedResource(FailClosedControl failClosedControl) {
        this.failClosedControl = failClosedControl;
    }

    @GET
    @Path("/permitted")
    @PermitAll
    public String permitted() {
        return failClosedControl.permitted();
    }

    @GET
    @Path("/unannotated")
    @PermitAll
    public String unannotated() {
        return failClosedControl.unannotated();
    }

    @GET
    @Path("/admin-only")
    @RolesAllowed("admin")
    public String adminOnly() {
        return failClosedControl.adminOnly();
    }

    /** Deliberately annotated NOWHERE — see the class javadoc. */
    @GET
    @Path("/unannotated-endpoint")
    public String unannotatedOverPermitAllControl() {
        return failClosedControl.permitted();
    }
}
