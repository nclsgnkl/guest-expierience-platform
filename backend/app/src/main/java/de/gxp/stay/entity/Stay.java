package de.gxp.stay.entity;

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
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and {@code de.gxp.stay.control} names it (spec §4, constitution
 * v1.6.0); it still never leaves {@code de.gxp.stay}, which the architecture
 * guards enforce instead of the compiler.
 *
 * <p>Fields are widened per field: {@code StayControl} writes the preference
 * overrides and reads the overlap-conflict evidence, so those six are public.
 * {@code roomId} and {@code status} stay package-private — only
 * {@link StayMapper} and the repository's JPQL touch them.
 */
@Entity
@Table(name = "stay")
public class Stay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "room_id", nullable = false)
    Long roomId;

    @Column(name = "display_name", nullable = false)
    public String displayName;

    @Column(name = "check_in", nullable = false)
    public Instant checkIn;

    @Column(name = "check_out", nullable = false)
    public Instant checkOut;

    public String language;

    public String appearance;

    @Column(nullable = false)
    String status;
}
