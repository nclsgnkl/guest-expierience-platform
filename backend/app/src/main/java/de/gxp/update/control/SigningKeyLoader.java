package de.gxp.update.control;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Loads the release-signing TRUST SET — the Ed25519 PUBLIC keys shipped
 * inside the image (update-channel REQ-703/704, D17; review A-1: key 1
 * active, key 2 dormant) — from the classpath resource named by
 * {@code gxp.update.public-key-resource}: one PEM file holding one or more
 * {@code PUBLIC KEY} blocks ({@code update/signing-public-keys.pem} in
 * production, the two test keys in {@code %test}).
 *
 * <p><b>Fails closed:</b> an absent resource, or one without a single
 * parseable key, throws from the {@code StartupEvent} observer, so Quarkus
 * refuses to start (the {@code GXP_ADMIN_PASSWORD} posture). The message
 * names only the resource path — never key bytes.
 *
 * <p>The set can be ROTATED through the channel: a verified manifest may
 * carry {@code trustedKeys}, and the persisted set ({@code update_channel.trusted_keys})
 * replaces the shipped one — see {@link UpdateControl#trustedKeys}. Keys travel
 * as TOKENS: the standard base64 of the 44-byte X.509 SubjectPublicKeyInfo DER
 * (60 characters, one {@code =}), comma-separated — a single fixed-layout line
 * the updater's readers accept.
 */
@ApplicationScoped
class SigningKeyLoader {

    /** One token: base64 of the Ed25519 SPKI DER (exactly 44 bytes → 60 characters). */
    static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9+/]{59}=$");
    /** A trust set: 1..4 tokens, comma-separated. */
    static final Pattern TOKEN_SET = Pattern.compile("^[A-Za-z0-9+/]{59}=(,[A-Za-z0-9+/]{59}=){0,3}$");
    static final int MAX_KEYS = 4;

    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN PUBLIC KEY-----([A-Za-z0-9+/=\\s]*?)-----END PUBLIC KEY-----");

    private final String resource;
    private volatile List<PublicKey> keys;

    SigningKeyLoader(@ConfigProperty(name = "gxp.update.public-key-resource") String resource) {
        this.resource = resource;
    }

    void onStart(@Observes StartupEvent event) {
        keys = load();
    }

    /** The shipped set (≥ 1 key); loads lazily if the observer has not run yet (unit tests). */
    List<PublicKey> keys() {
        List<PublicKey> loaded = keys;
        if (loaded == null) {
            loaded = load();
            keys = loaded;
        }
        return loaded;
    }

    private List<PublicKey> load() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw missingOrInvalid(null);
            }
            List<PublicKey> parsed = parsePem(in.readAllBytes());
            if (parsed.isEmpty() || parsed.size() > MAX_KEYS) {
                throw missingOrInvalid(null);
            }
            return parsed;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw missingOrInvalid(e);
        }
    }

    /** PEM ("PUBLIC KEY" armour, one block per key) → DER (X.509 SubjectPublicKeyInfo) → Ed25519 public keys. */
    static List<PublicKey> parsePem(byte[] pem) throws GeneralSecurityException {
        String text = new String(pem, StandardCharsets.US_ASCII);
        if (!text.contains("-----BEGIN PUBLIC KEY-----")) {
            throw new IllegalArgumentException("not a PEM public key");
        }
        List<PublicKey> keys = new ArrayList<>();
        Matcher blocks = PEM_BLOCK.matcher(text);
        while (blocks.find()) {
            keys.add(fromDer(Base64.getDecoder().decode(blocks.group(1).replaceAll("\\s", ""))));
        }
        return List.copyOf(keys);
    }

    /** {@code "tok,tok"} (as stored in the row or carried by a manifest) → keys; rejects an ill-formed or duplicated token. */
    static List<PublicKey> parseTokens(String tokens) {
        if (tokens == null || !TOKEN_SET.matcher(tokens).matches()) {
            throw new IllegalArgumentException("not a trusted key set");
        }
        List<PublicKey> keys = new ArrayList<>();
        for (String token : tokens.split(",")) {
            try {
                PublicKey key = fromDer(Base64.getDecoder().decode(token));
                if (keys.stream().anyMatch(k -> same(k, key))) {
                    throw new IllegalArgumentException("duplicated key in the set");
                }
                keys.add(key);
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("not an Ed25519 public key");
            }
        }
        return List.copyOf(keys);
    }

    /** The row/manifest form of a set — canonical, because DER is. */
    static String tokens(List<PublicKey> keys) {
        return String.join(",", keys.stream().map(k -> Base64.getEncoder().encodeToString(k.getEncoded())).toList());
    }

    static boolean same(PublicKey a, PublicKey b) {
        return java.util.Arrays.equals(a.getEncoded(), b.getEncoded());
    }

    static boolean contains(List<PublicKey> set, PublicKey key) {
        return set.stream().anyMatch(k -> same(k, key));
    }

    private static PublicKey fromDer(byte[] der) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
    }

    private IllegalStateException missingOrInvalid(Exception cause) {
        return new IllegalStateException("update signing public key missing or invalid: " + resource, cause);
    }
}
