package de.gxp.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single shared admin credential row (REQ-330, constitution v1.5.x).
 * Package-private — never leaves {@code de.gxp.auth}.
 */
@Entity
@Table(name = "admin_credential")
class AdminCredential {

    @Id
    Short id;

    /** Self-describing PBKDF2 value — see {@link Pbkdf2PasswordHasher}. */
    @Column(name = "password_hash", nullable = false)
    String passwordHash;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
