package de.gxp.stay;

import java.time.Instant;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Package-private data access for stays. */
@ApplicationScoped
class StayRepository implements PanacheRepository<Stay> {

    List<Stay> allOrdered() {
        return list("order by checkIn");
    }

    List<Stay> byRoom(long roomId) {
        return list("roomId = ?1 order by checkIn", roomId);
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
