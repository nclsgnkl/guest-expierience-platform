package de.gxp.update.control;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import de.gxp.api.model.UpdateCheckOutcome;
import de.gxp.update.entity.UpdateChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * The feature's TRANSACTIONAL collaborator (update-channel D8/D9): every
 * database touch of the single {@code update_channel} row happens here, in
 * a short transaction of its own, so {@code UpdateControl} never holds a
 * transaction while it talks HTTP or writes a file. Package-private;
 * {@code AuditCoverageTest} scans {@code *Control.java} only, and this class
 * audits nothing — the Control does, on the request path.
 *
 * <p>Every instant is stored at SECOND precision (review 2026-08-26 R2-1):
 * the desired file renders {@code requestedAt} without a fraction, the
 * updater copies that into {@code state.lastRequestedAt}, and the reconcile
 * rule compares the two — a sub-second {@code manualRequestedAt} would stay
 * "newer" than its own handled copy forever.
 */
@ApplicationScoped
class UpdateStateStore {

    private final UpdateChannelRepository repository;

    UpdateStateStore(UpdateChannelRepository repository) {
        this.repository = repository;
    }

    /** A detached copy of the row — read outside the transaction as plain data. */
    @Transactional
    UpdateChannel load() {
        return repository.theRow();
    }

    /**
     * Records a check outcome at {@code now}. {@code UPDATE_AVAILABLE} stores
     * the parsed fields AND the verified raw bytes (written together — the
     * V8 CHECK ties them); every other outcome clears the stored manifest,
     * so an update that stops being offered stops being "available".
     * {@code rotateTo} (nullable) is the new trust set a verified manifest
     * introduced (review A-1): stored with the manifest's version in the
     * same statement as everything else.
     */
    @Transactional
    void recordCheck(UpdateCheckOutcome outcome, Manifest manifest, byte[] manifestBytes, byte[] signature, Instant at,
            String rotateTo) {
        Instant now = seconds(at);
        UpdateChannel row = repository.theRow();
        row.lastCheckAt = now;
        row.lastCheckOutcome = outcome.toString();
        if (outcome == UpdateCheckOutcome.UPDATE_AVAILABLE) {
            row.availableVersion = manifest.version();
            row.availableArtifactRef = manifest.artifactRef();
            row.availableSha256 = manifest.sha256();
            row.availablePublishedAt = manifest.publishedAt();
            row.availableManifest = manifestBytes;
            row.availableSignature = signature;
            if (rotateTo != null) {
                row.trustedKeys = rotateTo;
                row.trustedKeysVersion = manifest.version();
            }
        } else {
            row.availableVersion = null;
            row.availableArtifactRef = null;
            row.availableSha256 = null;
            row.availablePublishedAt = null;
            row.availableManifest = null;
            row.availableSignature = null;
        }
        row.updatedAt = now;
    }

    @Transactional
    void saveSettings(boolean autoUpdateEnabled, LocalTime applyAt, Instant at) {
        Instant now = seconds(at);
        UpdateChannel row = repository.theRow();
        row.autoUpdateEnabled = autoUpdateEnabled;
        row.applyAt = applyAt;
        row.updatedAt = now;
    }

    @Transactional
    void recordManualRequest(String version, Instant at) {
        Instant now = seconds(at);
        UpdateChannel row = repository.theRow();
        row.manualRequestedVersion = version;
        row.manualRequestedAt = now;
        row.updatedAt = now;
    }

    /** The exchange-file precision (Design §2 instants: {@code ss'Z'}, no fraction). */
    static Instant seconds(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS);
    }
}
