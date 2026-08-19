package de.gxp.security;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * TEST-ONLY fixture (src/test, never shipped) proving the fail-closed posture
 * (REQ-213): this Control carries security annotations on some members, so
 * {@code quarkus.security.deny-unannotated-members=true} must auto-deny the
 * unannotated one.
 */
@ApplicationScoped
public class FailClosedControl {

    @PermitAll
    public String permitted() {
        return "permitted";
    }

    /** Deliberately unannotated — deny-unannotated-members must deny it. */
    public String unannotated() {
        return "must never be reachable";
    }

    @RolesAllowed("admin")
    public String adminOnly() {
        return "admin only";
    }
}
