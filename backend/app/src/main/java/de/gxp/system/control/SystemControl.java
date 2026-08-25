package de.gxp.system.control;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.gxp.api.ApiErrors;
import de.gxp.api.model.SystemHealth;
import de.gxp.api.model.SystemInfo;
import de.gxp.api.model.SystemReadiness;
import de.gxp.system.entity.InstanceInfoMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Control — the feature's entry point and the ONLY type another feature may
 * reference (spec §4). All behavior lives here: transactions, mapping calls,
 * non-200 outcomes.
 *
 * <p>{@code de.gxp.system} is the BCE reference package and the first one
 * migrated to the {@code boundary}/{@code control}/{@code entity} sub-package
 * layout (constitution v1.6.0, 2026-08-24): its HTTP adapter is
 * {@code de.gxp.system.boundary.SystemResource}, its collaborator
 * {@link InstanceInfoRepository} stays package-private here, and its JPA types
 * live in {@code de.gxp.system.entity}.
 *
 * <p>Security convention (constitution v1.6.0): authorization sits on the
 * Boundary, so this Control carries no security annotation. The surface is
 * {@code /health} and {@code /ready} as the unauthenticated box-diagnosis
 * endpoints, and {@code /info} as {@code admin} or {@code device} (REQ-432).
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
    public SystemHealth health() {
        return new SystemHealth().status(SystemHealth.StatusEnum.UP);
    }

    /**
     * Readiness — probe query against the database. On failure the Control
     * throws the 503 via {@link ApiErrors}: non-200 outcomes are thrown from
     * the Control, never branched on in the Boundary — the pinned pattern for
     * later features.
     *
     * <p>Built through {@code ApiErrors} rather than a bare
     * {@code ServiceUnavailableException} (fixed 2026-08-25). That exception's
     * {@code String} constructor sets the JAVA message, not the response
     * body, so the 503 went out with no entity at all while the contract
     * declares an {@code ApiError} for it — the response then fell through
     * {@code WrappedJacksonExceptionMapper}'s pass-through untouched. Nothing
     * caught it because the path only runs with the database down.
     *
     * <p>The probe failure itself is deliberately NOT propagated into the
     * message: a JDBC exception can quote connection details (principle 3).
     */
    @Transactional
    public SystemReadiness ready() {
        try {
            instanceInfoRepository.probe();
        } catch (RuntimeException e) {
            throw ApiErrors.serviceUnavailable("database-unreachable", "The database is unreachable");
        }
        return new SystemReadiness().status(SystemReadiness.StatusEnum.READY);
    }

    /**
     * Instance metadata — the Flyway-seeded row through the full BCE stack
     * (REQ-211). Restricted to authenticated callers, admin OR device
     * (REQ-432, decided 2026-08-19): version fingerprinting on an
     * unpatchable box is not offered anonymously; health/ready above stay
     * public for box diagnosis.
     */
    @Transactional
    public SystemInfo info() {
        return InstanceInfoMapper.toSystemInfo(instanceInfoRepository.theRow(), applicationVersion);
    }
}
