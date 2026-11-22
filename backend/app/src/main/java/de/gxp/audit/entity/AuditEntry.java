package de.gxp.audit.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One audit row (auth-hardening A4, REQ-620): who, when, from which
 * attributed address, which action on which entity, which field. There is
 * NO value column — by schema (V7), not by convention — so no guest-sensitive
 * value can ever be recorded here (constitution principle 3). Public only
 * because {@code de.gxp.audit.control} names it; a {@code de.gxp.audit}
 * internal.
 */
@Entity
@Table(name = "audit_entry")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    /** Principal name; {@code null} when nobody was authenticated (a failed login). */
    @Column(name = "actor")
    public String actor;

    @Column(name = "address", nullable = false)
    public String address;

    @Column(name = "via_proxy", nullable = false)
    public boolean viaProxy;

    @Column(name = "action", nullable = false)
    public String action;

    @Column(name = "entity", nullable = false)
    public String entity;

    @Column(name = "entity_id")
    public String entityId;

    /** The NAME of the changed field on an update — never its value. */
    @Column(name = "field")
    public String field;
}
