package de.gxp.device.control;

import java.time.Instant;

import de.gxp.device.entity.PairingRequest;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private data access for pairing requests.
 * It lives in {@code .control} rather than beside the entity precisely so it
 * can stay package-private (spec §4).
 */
@ApplicationScoped
class PairingRequestRepository implements PanacheRepository<PairingRequest> {

    PairingRequest byCode(String code) {
        return find("code", code).firstResult();
    }

    /**
     * Cleanup of expired requests (REQ-409). Called opportunistically on every
     * create, which is what this was built around while the allow-list had no
     * scheduler at all. {@code quarkus-scheduler} was approved on 2026-08-25
     * (constitution v1.7.0), so a time-driven sweep is now permitted — the
     * gap this closes is an instance nobody touches, where no create ever
     * arrives and expired rows are never removed. Nothing here is unsafe
     * meanwhile: an expired row is rejected by the poll and confirm checks
     * regardless of whether it has been deleted yet.
     */
    long deleteExpired(Instant now) {
        return delete("expiresAt <= ?1", now);
    }

    /**
     * The claim-once guard (REQ-404): atomically marks the request claimed.
     * Two racing polls yield exactly one row-count of 1 — the WHERE clause
     * decides, not the reader.
     */
    boolean claimOnce(long requestId, Instant now) {
        return getEntityManager()
                .createQuery("update PairingRequest p set p.claimedAt = :now"
                        + " where p.id = :id and p.claimedAt is null")
                .setParameter("now", now)
                .setParameter("id", requestId)
                .executeUpdate() == 1;
    }
}
