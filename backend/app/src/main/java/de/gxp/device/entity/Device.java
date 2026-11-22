package de.gxp.device.entity;

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
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and {@code de.gxp.device.control} names it (spec §4,
 * constitution v1.6.0); it still never leaves {@code de.gxp.device}, which
 * the architecture guards enforce instead of the compiler.
 *
 * <p>Every field is public here, unlike most entities: {@code DeviceControl}
 * builds the row at claim time and then writes {@code name},
 * {@code lastSeenAt} and {@code revokedAt} on the managed entity, and reads
 * {@code id}/{@code roomId}/{@code tokenHash}/{@code createdAt} back — there
 * is no field left that only {@link DeviceMapper} touches.
 */
@Entity
@Table(name = "device")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "room_id", nullable = false)
    public Long roomId;

    @Column(nullable = false)
    public String name;

    @Column(name = "token_hash", nullable = false, unique = true)
    public String tokenHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_seen_at")
    public Instant lastSeenAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;
}
