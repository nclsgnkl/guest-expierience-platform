package de.gxp.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One single-use recovery code of a manager account (auth-hardening REQ-616,
 * design D10): SHA-256-hex of the code at rest, never the code; {@code usedAt}
 * set = spent. Rows die with the user ({@code ON DELETE CASCADE}, V6).
 * Public only because {@code de.gxp.user.control} names it; a
 * {@code de.gxp.user} internal.
 */
@Entity
@Table(name = "manager_recovery_code")
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public ManagerUser user;

    @Column(name = "code_hash", nullable = false)
    public String codeHash;

    @Column(name = "used_at")
    public Instant usedAt;
}
