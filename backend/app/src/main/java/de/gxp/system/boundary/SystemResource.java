package de.gxp.system.boundary;

import de.gxp.api.SystemApi;
import de.gxp.api.model.SystemHealth;
import de.gxp.api.model.SystemInfo;
import de.gxp.api.model.SystemReadiness;
import de.gxp.system.control.SystemControl;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

/**
 * Boundary — JAX-RS adapter over the generated {@link SystemApi} interface.
 * Every method body is ONE delegating line (spec §4, binding): no branching,
 * no mapping, no transactions — all of that is the Control's. The security
 * annotation is the Boundary's (constitution v1.6.0): mixed surface here, so
 * it stays method-level — {@code /health} and {@code /ready} are the box-
 * diagnosis endpoints, {@code /info} is authenticated (REQ-432).
 */
public class SystemResource implements SystemApi {

    private final SystemControl systemControl;

    SystemResource(SystemControl systemControl) {
        this.systemControl = systemControl;
    }

    @Override
    @PermitAll
    public SystemHealth getSystemHealth() {
        return systemControl.health();
    }

    @Override
    @RolesAllowed({ "admin", "device" })
    public SystemInfo getSystemInfo() {
        return systemControl.info();
    }

    @Override
    @PermitAll
    public SystemReadiness getSystemReadiness() {
        return systemControl.ready();
    }
}
