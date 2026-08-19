package de.gxp.stay;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A stay (REQ-309). Only {@code BOOKED}/{@code CANCELLED} are stored —
 * "active" is derived from the clock (BOOKED ∧ check-in ≤ now < check-out);
 * with invariant 5 that makes "at most one active per room" hold by
 * construction. The display name is guest data: it must never reach a log
 * (principle 3).
 */
@Entity
@Table(name = "stay")
class Stay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "room_id", nullable = false)
    Long roomId;

    @Column(name = "display_name", nullable = false)
    String displayName;

    @Column(name = "check_in", nullable = false)
    Instant checkIn;

    @Column(name = "check_out", nullable = false)
    Instant checkOut;

    String language;

    String appearance;

    @Column(nullable = false)
    String status;
}
