package de.gxp.security;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

/**
 * TEST-ONLY Boundary (src/test, never shipped) over
 * {@link BoundaryAnnotatedControl}, shaped exactly like a real one: it
 * implements the {@link BoundaryAnnotatedApi} interface, takes its JAX-RS
 * annotations from there, and carries <b>only</b> the security annotation
 * itself — the model constitution v1.6.0 adopts.
 *
 * <p>The interface annotates the same two methods the other way round, so the
 * annotations here are not the only candidates: they have to <i>win</i>. Not
 * part of the contract — it exists solely so the resolution order is observable
 * over HTTP ({@link BoundarySecuritySemanticsTest}).
 */
public class BoundaryAnnotatedResource implements BoundaryAnnotatedApi {

    private final BoundaryAnnotatedControl boundaryAnnotatedControl;

    BoundaryAnnotatedResource(BoundaryAnnotatedControl boundaryAnnotatedControl) {
        this.boundaryAnnotatedControl = boundaryAnnotatedControl;
    }

    @Override
    @PermitAll
    public String permitted() {
        return boundaryAnnotatedControl.value();
    }

    @Override
    @RolesAllowed("admin")
    public String adminOnly() {
        return boundaryAnnotatedControl.value();
    }
}
