package de.gxp.system.control;

import de.gxp.system.entity.InstanceInfo;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private, reachable only through
 * {@link SystemControl}. Also hosts the readiness probe query (spec §9).
 *
 * <p>It lives in {@code .control} rather than beside the entity precisely so
 * it can stay package-private (spec §4): only the {@code *Control} of a
 * feature is public.
 */
@ApplicationScoped
class InstanceInfoRepository implements PanacheRepository<InstanceInfo> {

    /** Single-row table: the V1 baseline seeds exactly one row with id = 1. */
    InstanceInfo theRow() {
        return findAll().singleResult();
    }

    /** Readiness probe — fails by exception when the database is unreachable. */
    void probe() {
        getEntityManager().createNativeQuery("SELECT 1").getSingleResult();
    }
}
