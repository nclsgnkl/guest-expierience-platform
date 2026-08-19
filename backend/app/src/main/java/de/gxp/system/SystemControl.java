package de.gxp.system;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.gxp.api.model.SystemHealth;
import de.gxp.api.model.SystemInfo;
import de.gxp.api.model.SystemReadiness;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ServiceUnavailableException;

/**
 * Control — the package's public entry point and the only public type besides
 * its HTTP adapter {@link SystemResource} (spec §4). All behavior lives here:
 * transactions, security annotations, mapping calls, non-200 outcomes.
 *
 * Security convention (design §Security posture): every public Control method
 * carries exactly one of {@code @PermitAll} / {@code @RolesAllowed(...)}.
 * These three are the box-diagnosis endpoints — deliberately {@code @PermitAll}.
 */
@ApplicationScoped
public class SystemControl {

    private final InstanceInfoRepository instanceInfoRepository;
    private final String applicationVersion;

    SystemControl(InstanceInfoRepository instanceInfoRepository,
            @ConfigProperty(name = "quarkus.application.version") String applicationVersion) {
        this.instanceInfoRepository = instanceInfoRepository;
        this.applicationVersion = applicationVersion;
    }

    /** Liveness — static, no database touch: answers whenever the process serves HTTP. */
    @PermitAll
    public SystemHealth health() {
        return new SystemHealth().status(SystemHealth.StatusEnum.UP);
    }

    /**
     * Readiness — probe query against the database. On failure the Control
     * throws the 503 ({@link ServiceUnavailableException}): non-200 outcomes
     * are thrown from the Control, never branched on in the Boundary — the
     * pinned pattern for later features.
     */
    @PermitAll
    @Transactional
    public SystemReadiness ready() {
        try {
            instanceInfoRepository.probe();
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException("database unreachable");
        }
        return new SystemReadiness().status(SystemReadiness.StatusEnum.READY);
    }

    /** Instance metadata — the Flyway-seeded row through the full BCE stack (REQ-211). */
    @PermitAll
    @Transactional
    public SystemInfo info() {
        return InstanceInfoMapper.toSystemInfo(instanceInfoRepository.theRow(), applicationVersion);
    }
}
