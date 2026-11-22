package de.gxp.update.control;

import java.time.Instant;

/**
 * A manifest v1 (update-channel REQ-702) AFTER signature verification and
 * parsing: every field already validated against the regex table of Design
 * §2, so each value is safe to store and to copy into the exchange file.
 * {@code minimumFromVersion} and {@code trustedKeys} are optional
 * ({@code null} when absent); {@code trustedKeys} is the comma-separated
 * token form of the trust set this release introduces (review A-1), every
 * token already proven to be an Ed25519 public key.
 */
record Manifest(int schemaVersion, String channel, String version, String artifactRef, String sha256,
        Instant publishedAt, String minimumFromVersion, String trustedKeys) {
}
