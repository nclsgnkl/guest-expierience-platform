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
     * Opportunistic cleanup (REQ-409, no scheduler on the allow-list): every
     * create deletes expired rows first — a pairing table with a handful of
     * live rows needs nothing more.
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
