package de.gxp.auth;

import java.time.Instant;
import java.time.ZoneOffset;

import de.gxp.api.ApiErrors;
import de.gxp.api.model.LoginRequest;
import de.gxp.api.model.LoginResponse;
import de.gxp.api.model.PasswordChangeRequest;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
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
    private final LoginRateLimiter rateLimiter;
    private final SecurityIdentity identity;

    AuthControl(AdminCredentialRepository credentials, Pbkdf2PasswordHasher hasher, TokenStore tokenStore,
            LoginRateLimiter rateLimiter, SecurityIdentity identity) {
        this.credentials = credentials;
        this.hasher = hasher;
        this.tokenStore = tokenStore;
        this.rateLimiter = rateLimiter;
        this.identity = identity;
    }

    @PermitAll
    @Transactional
    public LoginResponse login(LoginRequest request) {
        rateLimiter.checkNotLocked();
        AdminCredential credential = credentials.theRow();
        if (credential == null || !hasher.verify(request.getPassword(), credential.passwordHash)) {
            rateLimiter.recordFailure();
            throw ApiErrors.unauthorized("invalid-credentials", "Login failed");
        }
        rateLimiter.recordSuccess();
        TokenStore.IssuedToken issued = tokenStore.issue();
        return new LoginResponse()
                .token(issued.rawToken())
                .idleTimeoutSeconds(tokenStore.idleTimeoutSeconds())
                .expiresAt(issued.expiresAt().atOffset(ZoneOffset.UTC));
    }

    @RolesAllowed("admin")
    public void logout() {
        tokenStore.remove(identity.getAttribute(TokenIdentityProvider.ATTRIBUTE_TOKEN_HASH));
    }

    /**
     * Constitution v1.5.1: verifies the current password, enforces the minimal
     * policy (≥ 8 — also contract-level bean validation), stores the new hash
     * and drops EVERY active token including the caller's — the old
     * credential must never survive into another idle window.
     */
    @RolesAllowed("admin")
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
