package de.gxp.auth.control;

import java.time.Instant;
import java.time.ZoneOffset;

import de.gxp.infrastructure.http.ApiErrors;
import de.gxp.api.model.LoginRequest;
import de.gxp.api.model.LoginResponse;
import de.gxp.api.model.PasswordChangeRequest;
import de.gxp.auth.entity.AdminCredential;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Control — manager session lifecycle (REQ-326..REQ-331). The only code that
 * ever sees the shared admin password is {@link #login}; failures are
 * deliberately generic (no timing/wording oracle beyond the unavoidable), no
 * password or token material appears in any message or log (principle 3).
 *
 * <p>Auth is isolated in this package so the later per-user swap (spec
 * decision 4) touches no other Boundary or Control.
 *
 * <p>Sub-package layout (constitution v1.6.0): this is the feature's one
 * public type. Everything else in {@code .control} stays package-private —
 * including the three framework SPI adapters ({@code BearerAuthMechanism},
 * {@code TokenIdentityProvider}, {@code TokenExpiryResponseFilter}), which
 * Quarkus discovers as CDI beans by their implemented interface and
 * {@code @Provider} annotation, never by name and never by package.
 */
@ApplicationScoped
public class AuthControl {

    /** The device role (REQ-410) — same bearer path, second token kind. */
    public static final String ROLE_DEVICE = "device";
    /** Device identity attributes — set by the auth flow, read by the guest read model (REQ-411). */
    public static final String DEVICE_ID_ATTRIBUTE = "gxp.device.id";
    public static final String DEVICE_ROOM_ATTRIBUTE = "gxp.device.room-id";

    private final AdminCredentialRepository credentials;
    private final Pbkdf2PasswordHasher hasher;
    private final TokenStore tokenStore;
    private final SecurityIdentity identity;

    AuthControl(AdminCredentialRepository credentials, Pbkdf2PasswordHasher hasher, TokenStore tokenStore,
            SecurityIdentity identity) {
        this.credentials = credentials;
        this.hasher = hasher;
        this.tokenStore = tokenStore;
        this.identity = identity;
    }

    /**
     * Verifies the shared admin password and issues a session token.
     *
     * <p><b>Deliberately not throttled</b> — no lockout, no rate limit, no
     * concurrency gate (owner decision 2026-08-24, REQ-326). Two earlier
     * mechanisms were tried and both were themselves denial-of-service
     * vectors: a global failed-attempt lockout answered the manager's CORRECT
     * password with 429, and the admission gate that replaced it still shed
     * arriving callers for the duration of a flood. The question is deferred
     * until the production hardware is known, because a throttle sized for the
     * wrong machine is worse than none.
     *
     * <p>What carries brute-force resistance instead is the password policy
     * (≥ 8 characters, enforced here and on the first-start seed by
     * {@link AdminPasswordSeeder}). <b>Accepted and recorded:</b> an
     * unauthenticated caller on the LAN can spend this box's CPU at will
     * through PBKDF2, so sustained abuse degrades the whole instance, kiosks
     * included.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        AdminCredential credential = credentials.theRow();
        if (credential == null || !hasher.verify(request.getPassword(), credential.passwordHash)) {
            throw ApiErrors.unauthorized("invalid-credentials", "Login failed");
        }
        TokenStore.IssuedToken issued = tokenStore.issue();
        return new LoginResponse()
                .token(issued.rawToken())
                .idleTimeoutSeconds(tokenStore.idleTimeoutSeconds())
                .expiresAt(issued.expiresAt().atOffset(ZoneOffset.UTC));
    }

    public void logout() {
        tokenStore.remove(identity.getAttribute(TokenIdentityProvider.ATTRIBUTE_TOKEN_HASH));
    }

    /**
     * Drops the idled-out entries from the in-memory token store
     * (constitution v1.7.0, owner decision 2026-08-25) — called by
     * {@code de.gxp.infrastructure.scheduler.AuthScheduler}. It exists as a
     * hop rather than the scheduler calling the store directly because
     * {@link TokenStore} is neither public nor a {@code *Control}, and a
     * feature's {@code *Control} is the only type another package may name
     * (spec §4, enforced by {@code FeatureInternalsImportTest}).
     *
     * <p>No {@code @Transactional}: the store is in memory, no database is
     * touched. Memory hygiene only — an expired entry is refused by the
     * store's own expiry check whether or not it has been swept yet.
     */
    public void purgeExpiredTokens() {
        tokenStore.purgeExpired();
    }

    /**
     * Constitution v1.5.1: verifies the current password, enforces the minimal
     * policy (≥ 8 — also contract-level bean validation), stores the new hash
     * and drops EVERY active token including the caller's — the old
     * credential must never survive into another idle window.
     *
     * <p>Like {@link #login}, this is not throttled (REQ-326, owner decision
     * 2026-08-24) — it is admin-only anyway, so it is not an unauthenticated
     * surface.
     */
    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        AdminCredential credential = credentials.theRow();
        if (credential == null || !hasher.verify(request.getCurrentPassword(), credential.passwordHash)) {
            throw ApiErrors.badRequest("current-password-mismatch", "The current password is not correct");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            throw ApiErrors.badRequest("password-policy", "The new password must have at least 8 characters");
        }
        credential.passwordHash = hasher.hash(request.getNewPassword());
        credential.updatedAt = Instant.now();
        tokenStore.clear();
    }
}
