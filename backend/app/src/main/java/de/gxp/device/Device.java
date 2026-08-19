package de.gxp.device;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A paired kiosk device (REQ-406/407). {@code tokenHash} holds the SHA-256
 * hex of the device token, NEVER the raw value — the raw token exists exactly
 * once, in the claiming poll response. Revocation is soft ({@code revokedAt})
 * and effective on the very next request: token verification reads the row.
 * Package-private, never leaves {@code de.gxp.device}.
 */
@Entity
@Table(name = "device")
class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "room_id", nullable = false)
    Long roomId;

    @Column(nullable = false)
    String name;

    @Column(name = "token_hash", nullable = false, unique = true)
    String tokenHash;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "last_seen_at")
    Instant lastSeenAt;

    @Column(name = "revoked_at")
    Instant revokedAt;
}
