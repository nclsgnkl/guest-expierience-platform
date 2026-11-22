package de.gxp.update.control;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Detached Ed25519 verification over the EXACT manifest bytes against a
 * TRUST SET (update-channel REQ-702/703, D4, D7; review A-1): the signature
 * is accepted when ANY key of the set verifies it, and that key is returned
 * so the caller can tie a key rotation to its signer. No canonicalisation,
 * no parsing before this: the bytes fetched are the bytes verified. Returns
 * {@code null} for every failure and throws nothing outward.
 */
@ApplicationScoped
class ManifestVerifier {

    /** A raw Ed25519 signature is exactly this long; anything else is rejected before the JCA call. */
    static final int SIGNATURE_LENGTH = 64;

    /** @return the key of {@code trusted} that verified the signature, or {@code null}. */
    PublicKey verify(byte[] manifest, byte[] signature, List<PublicKey> trusted) {
        if (manifest == null || signature == null || signature.length != SIGNATURE_LENGTH || trusted == null) {
            return null;
        }
        for (PublicKey key : trusted) {
            try {
                Signature verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(key);
                verifier.update(manifest);
                if (verifier.verify(signature)) {
                    return key;
                }
            } catch (GeneralSecurityException | IllegalStateException e) {
                // this key does not verify — try the next one; the outcome is "no key verified"
            }
        }
        return null;
    }
}
