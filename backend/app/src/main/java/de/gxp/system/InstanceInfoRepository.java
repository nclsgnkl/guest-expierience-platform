package de.gxp.system;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private, reachable only through
 * {@link SystemControl}. Also hosts the readiness probe query (spec §9).
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
