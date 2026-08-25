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
     * Cleanup of expired requests (REQ-409), with TWO callers since
     * 2026-08-25. {@link DeviceControl#createPairingRequest()} calls it
     * opportunistically on every create, which is what bounds the table during
     * a flood of anonymous creates; {@link
     * DeviceControl#sweepExpiredPairingRequests()} calls it once a day from
     * {@code de.gxp.infrastructure.scheduler.DeviceScheduler} (constitution
     * v1.7.0), which covers the opposite case — an instance nobody touches,
     * where no create ever arrives and expired rows would otherwise stay
     * forever. Neither is a security control: an expired row is rejected by
     * the poll and confirm checks whether or not it has been deleted yet.
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
