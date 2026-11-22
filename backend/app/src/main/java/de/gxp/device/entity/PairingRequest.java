package de.gxp.device.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A short-lived pairing request (REQ-403/404, spec §6 hardened). Two
 * credentials by design: the display {@code code} is the human channel (the
 * manager reads it off the kiosk), the poll secret — stored only as
 * {@code pollSecretHash} — is the machine channel the kiosk keeps and never
 * displays. {@code roomId}/{@code deviceName} are set at manager confirm;
 * {@code claimedAt} makes the token hand-out claim-once.
 *
 * <p>The TYPE is {@code public} only because Java has no sub-package
 * visibility and {@code de.gxp.device.control} names it (spec §4,
 * constitution v1.6.0); it still never leaves {@code de.gxp.device}, which
 * the architecture guards enforce instead of the compiler.
 *
 * <p>Every field is public: this row has no mapper at all — the whole
 * lifecycle (create, confirm, poll, claim) is written and read by
 * {@code DeviceControl} across the sub-package line, so there is nothing left
 * to keep package-private.
 */
@Entity
@Table(name = "pairing_request")
public class PairingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String code;

    @Column(name = "poll_secret_hash", nullable = false)
    public String pollSecretHash;

    @Column(name = "room_id")
    public Long roomId;

    @Column(name = "device_name")
    public String deviceName;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "confirmed_at")
    public Instant confirmedAt;

    @Column(name = "claimed_at")
    public Instant claimedAt;
}
