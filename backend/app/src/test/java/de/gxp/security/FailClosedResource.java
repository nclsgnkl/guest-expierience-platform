package de.gxp.security;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * TEST-ONLY Boundary for {@link FailClosedControl} (src/test, never shipped).
 * One delegating line per method, as everywhere; not part of the contract —
 * it exists solely so the security behavior is observable over HTTP.
 */
@Path("/test/fail-closed")
public class FailClosedResource {

    private final FailClosedControl failClosedControl;

    FailClosedResource(FailClosedControl failClosedControl) {
        this.failClosedControl = failClosedControl;
    }

    @GET
    @Path("/permitted")
    public String permitted() {
        return failClosedControl.permitted();
    }

    @GET
    @Path("/unannotated")
    public String unannotated() {
        return failClosedControl.unannotated();
    }

    @GET
    @Path("/admin-only")
    public String adminOnly() {
        return failClosedControl.adminOnly();
    }
}
