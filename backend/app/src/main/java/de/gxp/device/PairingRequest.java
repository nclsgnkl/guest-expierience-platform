package de.gxp.device;

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
 * {@code claimedAt} makes the token hand-out claim-once. Package-private.
 */
@Entity
@Table(name = "pairing_request")
class PairingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String code;

    @Column(name = "poll_secret_hash", nullable = false)
    String pollSecretHash;

    @Column(name = "room_id")
    Long roomId;

    @Column(name = "device_name")
    String deviceName;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "confirmed_at")
    Instant confirmedAt;

    @Column(name = "claimed_at")
    Instant claimedAt;
}
