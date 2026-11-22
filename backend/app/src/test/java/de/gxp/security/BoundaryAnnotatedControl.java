package de.gxp.security;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * TEST-ONLY fixture (src/test, never shipped) for the Boundary-annotation
 * model the constitution v1.6.0 adopts: a Control carrying <b>no</b> security
 * annotation at all, so {@code quarkus.security.deny-unannotated-members}
 * (class-scoped — it only bites beans that annotate <i>some</i> member) leaves
 * it alone and the internal call is not a trust boundary.
 *
 * <p>Deliberately distinct from {@link FailClosedControl}, which annotates
 * some members and therefore proves the opposite half.
 */
@ApplicationScoped
public class BoundaryAnnotatedControl {

    public String value() {
        return "control-reached";
    }
}
