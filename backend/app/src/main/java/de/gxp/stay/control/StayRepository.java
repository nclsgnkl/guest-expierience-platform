package de.gxp.stay.control;

import java.time.Instant;
import java.util.List;

import de.gxp.stay.entity.Stay;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private data access for stays. It lives in
 * {@code .control} rather than beside the entity precisely so it can stay
 * package-private (spec §4).
 */
@ApplicationScoped
class StayRepository implements PanacheRepository<Stay> {

    List<Stay> allOrdered() {
        return list("order by checkIn");
    }

    List<Stay> byRoom(long roomId) {
        return list("roomId = ?1 order by checkIn", roomId);
    }

    /**
     * The ACTIVE stay of a room (REQ-419): BOOKED ∧ check-in ≤ now <
     * check-out — the same derivation as everywhere; invariant 5 guarantees
     * at most one row.
     */
    Stay activeOf(long roomId, Instant now) {
        return find("roomId = ?1 and status = 'BOOKED' and checkIn <= ?2 and checkOut > ?2", roomId, now)
                .firstResult();
    }

    /**
     * Invariant 5's range check (strict inequalities — adjacent stays pass):
     * non-cancelled stays of the room overlapping [in, out), excluding the
     * stay being updated. Race-free because the caller holds the room-row
     * lock for the whole transaction.
     */
    List<Stay> overlapping(long roomId, Instant checkIn, Instant checkOut, Long selfId) {
        return list("roomId = ?1 and status <> 'CANCELLED' and checkIn < ?2 and checkOut > ?3 and id <> ?4",
                roomId, checkOut, checkIn, selfId == null ? -1L : selfId);
    }
}
