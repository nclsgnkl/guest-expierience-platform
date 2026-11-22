package de.gxp.security;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * TEST-ONLY stand-in for a generated {@code de.gxp.api.*Api} interface
 * (src/test, never shipped). It mirrors the generated shape exactly — class
 * level {@code @Path("")}, full paths on the methods, the implementation
 * carrying no JAX-RS annotation of its own — because that is the only
 * configuration in which the question this fixture exists to answer is even
 * meaningful: <b>when the same endpoint method is annotated in two places, does
 * the implementation win?</b>
 *
 * <p>Hence the deliberately <b>conflicting</b> security annotations here: this
 * interface says {@code @RolesAllowed("admin")} on the method that
 * {@link BoundaryAnnotatedResource} marks {@code @PermitAll}, and
 * {@code @PermitAll} on the one the Boundary marks
 * {@code @RolesAllowed("admin")}. Anonymous access must therefore be admitted
 * to the first and refused on the second; either outcome flipping would mean
 * the interface, not the implementation, is being honoured.
 *
 * <p>The generated interfaces carry no security annotation today
 * ({@code jaxrs-spec}, {@code useSwaggerAnnotations=false}) — the risk this
 * pins is a generator upgrade that starts emitting them from the contract's
 * {@code bearerAuth}. That would be invisible in a fixture with only one place
 * to look, and it is not hypothetical for annotations in general:
 * {@code de.gxp.infrastructure.http.ResponseStatusFilter} exists precisely because
 * {@code @ResponseStatus} on an overriding method IS invisible to the
 * build-time scanner. The security path resolves the other way round, and that
 * difference is what {@link BoundarySecuritySemanticsTest} claim 2 asserts.
 */
@Path("")
public interface BoundaryAnnotatedApi {

    @GET
    @Path("/test/boundary-security/permitted")
    @RolesAllowed("admin") // conflicts on purpose — the impl says @PermitAll
    String permitted();

    @GET
    @Path("/test/boundary-security/admin-only")
    @PermitAll // conflicts on purpose — the impl says @RolesAllowed("admin")
    String adminOnly();
}
