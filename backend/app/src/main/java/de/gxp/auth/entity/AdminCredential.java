package de.gxp.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single shared admin credential row (REQ-330, constitution v1.5.x).
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and {@code de.gxp.auth.control} names it (spec §4, constitution
 * v1.6.0); it still never leaves {@code de.gxp.auth}, which the architecture
 * guards enforce instead of the compiler.
 *
 * <p>All three fields are public: this row has no mapper — it never becomes a
 * DTO, because the password hash must never be serialized anywhere. Its only
 * readers and writers are the repository's seed and
 * {@code AuthControl.login}/{@code changePassword}, all of which sit across
 * the sub-package line in {@code .control}.
 */
@Entity
@Table(name = "admin_credential")
public class AdminCredential {

    @Id
    public Short id;

    /** Self-describing PBKDF2 value — produced by the hasher in {@code de.gxp.auth.control}. */
    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
