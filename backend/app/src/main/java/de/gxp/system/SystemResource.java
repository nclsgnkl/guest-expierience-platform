package de.gxp.system;

import de.gxp.api.SystemApi;
import de.gxp.api.model.SystemHealth;
import de.gxp.api.model.SystemInfo;
import de.gxp.api.model.SystemReadiness;

/**
 * Boundary — JAX-RS adapter over the generated {@link SystemApi} interface.
 * Every method body is ONE delegating line (spec §4, binding): no branching,
 * no mapping, no transactions, no security annotations — all of that is the
 * Control's.
 */
public class SystemResource implements SystemApi {

    private final SystemControl systemControl;

    SystemResource(SystemControl systemControl) {
        this.systemControl = systemControl;
    }

    @Override
    public SystemHealth getSystemHealth() {
        return systemControl.health();
    }

    @Override
    public SystemInfo getSystemInfo() {
        return systemControl.info();
    }

    @Override
    public SystemReadiness getSystemReadiness() {
        return systemControl.ready();
    }
}
