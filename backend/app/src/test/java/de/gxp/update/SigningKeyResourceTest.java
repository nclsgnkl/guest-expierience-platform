package de.gxp.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The build fails closed without the production TRUST SET (update-channel
 * REQ-704, D17; review A-1): the resource must exist on disk, hold at least
 * TWO distinct Ed25519 public keys (key 1 active, key 2 dormant — a box that
 * trusts one key can never learn a second one without a visit), and none of
 * them may be a test key. And no PRIVATE key may exist anywhere under the
 * shipped resources, the updater or the release tools (REQ-725). Plain
 * JUnit — reads the repository, not the classpath, so a test-scope shadow
 * could not satisfy it.
 */
class SigningKeyResourceTest {

    static final String PRODUCTION_RESOURCE = "src/main/resources/update/signing-public-keys.pem";
    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN PUBLIC KEY-----([A-Za-z0-9+/=\\s]*?)-----END PUBLIC KEY-----");

    @Test
    void theProductionTrustSetHoldsAtLeastTwoDistinctKeysAndNoTestKey() throws IOException, GeneralSecurityException {
        Path resource = moduleRoot().resolve(PRODUCTION_RESOURCE);
        assertTrue(Files.isRegularFile(resource), "missing: " + PRODUCTION_RESOURCE
                + " — generate the pairs with tools/update/gen-signing-key.sh and concatenate ONLY the public keys");
        List<PublicKey> production = parse(Files.readAllBytes(resource));
        assertTrue(production.size() >= 2, "the shipped trust set must carry key 1 (active) and key 2 (dormant), found " + production.size());
        assertTrue(production.size() <= 4, "at most four keys");
        for (int i = 0; i < production.size(); i++) {
            assertEquals("EdDSA", production.get(i).getAlgorithm());
            for (int j = i + 1; j < production.size(); j++) {
                assertFalse(sameKey(production.get(i), production.get(j)), "keys " + i + " and " + j + " are the same key");
            }
            assertFalse(sameKey(production.get(i), TestSigningKeys.publicKey()) || sameKey(production.get(i), TestSigningKeys.publicKey2()),
                    "key " + i + " is a TEST key — a release would be verifiable by anyone holding the repo");
        }
    }

    @Test
    void noPrivateKeyExistsUnderShippedResourcesTheUpdaterOrTheTools() throws IOException {
        Path module = moduleRoot();
        List<Path> roots = List.of(module.resolve("src/main/resources"), module.resolve("../updater").normalize(),
                module.resolve("../../tools/update").normalize());
        List<String> offenders = new ArrayList<>();
        for (Path root : roots) {
            assertTrue(Files.isDirectory(root), "expected directory: " + root);
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    if (new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains("PRIVATE KEY")) {
                        offenders.add(module.relativize(file).toString());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "a private key block is present in: " + offenders);
    }

    private static boolean sameKey(PublicKey a, PublicKey b) {
        return Arrays.equals(a.getEncoded(), b.getEncoded());
    }

    private static List<PublicKey> parse(byte[] pem) throws GeneralSecurityException {
        String text = new String(pem, StandardCharsets.US_ASCII);
        assertTrue(text.startsWith("-----BEGIN PUBLIC KEY-----"), "not a PEM public key");
        List<PublicKey> keys = new ArrayList<>();
        Matcher blocks = PEM_BLOCK.matcher(text);
        while (blocks.find()) {
            byte[] der = Base64.getDecoder().decode(blocks.group(1).replaceAll("\\s", ""));
            keys.add(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der)));
        }
        return keys;
    }

    static Path moduleRoot() {
        return ManifestServer.moduleRoot();
    }
}
