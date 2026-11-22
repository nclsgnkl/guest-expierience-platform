package de.gxp.update.entity;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity — the single {@code update_channel} row (update-channel REQ-709,
 * D8), state only. Schema and seed come from Flyway {@code V8__update_channel.sql}
 * (+ {@code V9} for the rotated trust set).
 *
 * <p>Public type, and public fields where {@code de.gxp.update.control}
 * (the state store and the Control) reads or writes them — Java has no
 * sub-package visibility. Referencing it from outside {@code de.gxp.update}
 * is a violation the architecture guards catch. {@code available_*} is
 * written only from bytes that passed signature verification; the two
 * byte columns are the verified originals, never re-serialized.
 */
@Entity
@Table(name = "update_channel")
public class UpdateChannel {

    @Id
    Short id;

    @Column(name = "auto_update_enabled", nullable = false)
    public boolean autoUpdateEnabled;

    @Column(name = "apply_at", nullable = false)
    public LocalTime applyAt;

    @Column(name = "last_check_at")
    public Instant lastCheckAt;

    @Column(name = "last_check_outcome", nullable = false)
    public String lastCheckOutcome;

    @Column(name = "available_version")
    public String availableVersion;

    @Column(name = "available_artifact_ref")
    public String availableArtifactRef;

    @Column(name = "available_sha256")
    public String availableSha256;

    @Column(name = "available_published_at")
    public Instant availablePublishedAt;

    @Column(name = "available_manifest")
    public byte[] availableManifest;

    @Column(name = "available_signature")
    public byte[] availableSignature;

    @Column(name = "manual_requested_version")
    public String manualRequestedVersion;

    @Column(name = "manual_requested_at")
    public Instant manualRequestedAt;

    /** The rotated trust set (review A-1, V9): comma-separated key tokens, or null = the image's set. */
    @Column(name = "trusted_keys")
    public String trustedKeys;

    /** The manifest version that introduced {@link #trustedKeys}; decides against the running version. */
    @Column(name = "trusted_keys_version")
    public String trustedKeysVersion;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
