package de.gxp.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A named manager account (auth-hardening REQ-607/REQ-613, design A2) with
 * its optional second factor (V6, REQ-614..616): {@code totpSecret} set and
 * {@code totpConfirmedAt} null = enrollment pending; both set = confirmed;
 * {@code totpLastStep} is the per-user replay guard.
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and {@code de.gxp.user.control} names it (spec §4); it never
 * leaves {@code de.gxp.user} — the architecture guards enforce that instead of
 * the compiler. {@code passwordHash} and {@code totpSecret} are never mapped
 * to any DTO.
 */
@Entity
@Table(name = "manager_user")
public class ManagerUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String username;

    /** Self-describing PBKDF2 value — produced by the hasher in {@code de.gxp.user.control}. */
    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(nullable = false)
    public boolean active = true;

    /** The TOTP shared secret, base64 (JDK codec) — shown base32 exactly once at enrollment. */
    @Column(name = "totp_secret")
    public String totpSecret;

    @Column(name = "totp_confirmed_at")
    public Instant totpConfirmedAt;

    /** Last accepted 30-second step; a code for a step {@code <=} this is a replay. */
    @Column(name = "totp_last_step")
    public Long totpLastStep;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
