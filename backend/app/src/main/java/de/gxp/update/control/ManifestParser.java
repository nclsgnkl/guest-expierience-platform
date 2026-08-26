package de.gxp.update.control;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;

import de.gxp.api.model.UpdateCheckOutcome;

/**
 * Strict parsing of a VERIFIED manifest body and the REQ-705 acceptance
 * rules (update-channel, D6). Only ever called after {@link ManifestVerifier}
 * accepted the bytes — parsing is not a security boundary here, but the
 * values still travel into the exchange file a root process reads, so every
 * string is regex-validated (the same table the updater applies) and an
 * unknown field is a rejection, not a warning.
 */
final class ManifestParser {

    static final int MAX_BODY_BYTES = 64 * 1024;
    static final int SCHEMA_VERSION = 1;

    static final Pattern ARTIFACT_REF = Pattern.compile(
            "^[a-z0-9][a-z0-9._-]{0,127}(:[0-9]{1,5})?(/[a-z0-9][a-z0-9._-]{0,127}){0,7}(:[A-Za-z0-9_][A-Za-z0-9._-]{0,127})?(@sha256:[0-9a-f]{64})?$");
    static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    static final Pattern INSTANT = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$");
    static final Pattern CHANNEL = Pattern.compile("^[a-z0-9-]{1,32}$");

    /** The wire shape — every field a String or Integer so no type coercion decides anything. */
    private record Raw(Integer schemaVersion, String channel, String version, String artifactRef, String sha256,
            String publishedAt, String minimumFromVersion, String trustedKeys) {
    }

    private final ObjectMapper mapper = strictMapper();

    /** Unknown or duplicate field → error; a JSON string is never coerced into a number (or vice versa). */
    private static ObjectMapper strictMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, false)
                .configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
        mapper.coercionConfigDefaults()
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }

    /**
     * @throws IllegalArgumentException for anything that is not a well-formed
     *         manifest v1 — size, JSON, a missing or unknown field, a value
     *         outside its regex, an impossible instant, an overflowing
     *         version. This is the ONLY exception the method throws (REQ-705
     *         "no exception propagates" — the Control maps it to
     *         {@code MALFORMED}), and every version {@link #accept} later
     *         parses is proven parseable here. The message never carries
     *         body content.
     */
    Manifest parse(byte[] body) {
        if (body == null || body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("manifest body missing or above 64 KiB");
        }
        Raw raw;
        try {
            raw = mapper.readValue(body, Raw.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("manifest is not well-formed JSON of the expected shape");
        }
        if (raw == null || raw.schemaVersion == null) {
            throw new IllegalArgumentException("manifest lacks schemaVersion");
        }
        String channel = require("channel", raw.channel, CHANNEL);
        String version = requireVersion("version", raw.version);
        String artifactRef = require("artifactRef", raw.artifactRef, ARTIFACT_REF);
        String sha256 = require("sha256", raw.sha256, SHA256);
        Instant publishedAt = requireInstant("publishedAt", raw.publishedAt);
        String minimum = raw.minimumFromVersion == null ? null : requireVersion("minimumFromVersion", raw.minimumFromVersion);
        String trustedKeys = raw.trustedKeys == null ? null : requireKeySet("trustedKeys", raw.trustedKeys);
        return new Manifest(raw.schemaVersion, channel, version, artifactRef, sha256, publishedAt, minimum, trustedKeys);
    }

    /**
     * The REQ-705 rules on a parsed manifest, in order; {@code null} means
     * accepted (the caller records {@code UPDATE_AVAILABLE}).
     */
    UpdateCheckOutcome accept(Manifest manifest, String configuredChannel, SemanticVersion running) {
        if (manifest.schemaVersion() != SCHEMA_VERSION) {
            return UpdateCheckOutcome.WRONG_SCHEMA_VERSION;
        }
        if (!manifest.channel().equals(configuredChannel)) {
            return UpdateCheckOutcome.WRONG_CHANNEL;
        }
        if (SemanticVersion.parse(manifest.version()).compareTo(running) <= 0) {
            return UpdateCheckOutcome.NO_UPDATE;
        }
        if (manifest.minimumFromVersion() != null
                && SemanticVersion.parse(manifest.minimumFromVersion()).compareTo(running) > 0) {
            return UpdateCheckOutcome.MINIMUM_FROM_VERSION_NOT_MET;
        }
        return null;
    }

    private static String require(String field, String value, Pattern pattern) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw invalid(field);
        }
        return value;
    }

    /** Regex AND a full {@link SemanticVersion#parse} (review #4: {@code 99999999999.0.0} matches the regex but overflows). */
    private static String requireVersion(String field, String value) {
        require(field, value, SemanticVersion.PATTERN);
        try {
            SemanticVersion.parse(value);
        } catch (IllegalArgumentException e) {
            throw invalid(field);
        }
        return value;
    }

    /** Regex AND a real calendar instant (review #3: {@code 2026-02-30T00:00:00Z} matches the regex). */
    private static Instant requireInstant(String field, String value) {
        try {
            return Instant.parse(require(field, value, INSTANT));
        } catch (DateTimeException e) {
            throw invalid(field);
        }
    }

    /** Regex AND every token parses as an Ed25519 public key, none duplicated (review A-1). */
    private static String requireKeySet(String field, String value) {
        require(field, value, SigningKeyLoader.TOKEN_SET);
        try {
            SigningKeyLoader.parseTokens(value);
        } catch (IllegalArgumentException e) {
            throw invalid(field);
        }
        return value;
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("manifest field missing or invalid: " + field);
    }
}
