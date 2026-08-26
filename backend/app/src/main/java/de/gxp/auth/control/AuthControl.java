package de.gxp.auth.control;

import java.time.ZoneOffset;

import de.gxp.infrastructure.http.ApiErrors;
import de.gxp.infrastructure.http.ClientAttribution;
import de.gxp.api.model.LoginRequest;
import de.gxp.api.model.LoginResponse;
import de.gxp.api.model.PasswordChangeRequest;
import de.gxp.audit.control.AuditControl;
import de.gxp.user.control.UserControl;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Control — manager session lifecycle (REQ-326..REQ-331; auth-hardening A2).
 * Sessions live here; accounts and password verification live in
 * {@code de.gxp.user} ({@link UserControl}), which this Control depends on
 * and which never depends back. The only code that ever sees a password is
 * {@link #login} / {@link #changePassword}, and both hand it straight to the
 * user Control; failures are deliberately generic (no wording or timing
 * oracle — REQ-609), no password or token material appears in any message or
 * log (principle 3).
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

    private final UserControl userControl;
    private final AuditControl auditControl;
    private final TokenStore tokenStore;
    private final LoginThrottle throttle;
    private final ClientAttribution clientAttribution;
    private final SecurityIdentity identity;

    AuthControl(UserControl userControl, AuditControl auditControl, TokenStore tokenStore,
            LoginThrottle throttle, ClientAttribution clientAttribution, SecurityIdentity identity) {
        this.userControl = userControl;
        this.auditControl = auditControl;
        this.tokenStore = tokenStore;
        this.throttle = throttle;
        this.clientAttribution = clientAttribution;
        this.identity = identity;
    }

    /**
     * Verifies username + password and issues a session token.
     *
     * <p><b>Throttled per attributed source address</b> (auth-hardening
     * REQ-601..REQ-605, reopening REQ-326): {@link LoginThrottle#check} runs
     * FIRST — before the credential row is read and before any PBKDF2
     * derivation — so an exhausted address costs this box nothing. Never
     * global, never an account lockout: the two global mechanisms tried on
     * 2026-08-24 answered the manager's CORRECT password with 429 and were
     * withdrawn for exactly that reason. A verification miss counts against
     * the address; a success clears it.
     *
     * <p><b>Second factor via the trusted proxy</b> (auth-hardening
     * REQ-617, D6–D8; the second of the two consumptions of the transport
     * fact that constitution v1.8.0 allows): when the request came through
     * the configured proxy and carries neither {@code totpCode} nor
     * {@code recoveryCode}, it is answered {@code 401 second-factor-required}
     * BEFORE the password is verified — nothing is learned about the
     * password, and nothing is counted (a UI round-trip must not eat the
     * budget). With a code, the user Control verifies password and factor
     * together and every miss is the one generic {@code invalid-credentials}.
     * On the LAN the flag is false and the code fields are ignored.
     *
     * <p>Residual, accepted and recorded: the first five attempts per address
     * still cost a PBKDF2 derivation each, and a distributed attacker has
     * many addresses — that is what the second factor exists for.
     *
     * <p><b>Audited</b> (auth-hardening REQ-621/624, D19/D20): every
     * VERIFIED outcome — success and failure — is recorded by
     * {@link AuditControl} in its OWN transaction, so the row survives the
     * {@code 401}. The code-less {@code second-factor-required} answer
     * writes nothing (review 2026-08-26, 🟡 #1): like the {@code 429} it is
     * unthrottled and unauthenticated, and an uncounted request must not
     * become an unbounded database insert once the relay exists. A failure
     * row names the account the submitted name MATCHED (canonical, active or
     * not) or nothing — never the submitted text, which is free text the
     * caller chose and may be a mistyped password; the code-less
     * {@code 401} looked nothing up and records address + outcome only. The
     * {@code 429} is never recorded: it must stay free of database work.
     * This method therefore carries no {@code @Transactional} of its own —
     * the token store is memory, and the one DB write of a login (the
     * TOTP step / recovery-code spend) commits inside
     * {@link UserControl#verifyLogin}'s transaction.
     */
    public LoginResponse login(LoginRequest request) {
        ClientAttribution.Attribution attribution = clientAttribution.attribution();
        String address = attribution.address();
        throttle.check(address);
        boolean viaProxy = attribution.viaTrustedProxy();
        if (viaProxy && request.getTotpCode() == null && request.getRecoveryCode() == null) {
            throw ApiErrors.unauthorized("second-factor-required", "A second factor is required");
        }
        UserControl.Verdict verdict = userControl.verifyLogin(request.getUsername(), request.getPassword(),
                request.getTotpCode(), request.getRecoveryCode(), viaProxy);
        if (!verdict.ok()) {
            throttle.recordFailure(address);
            auditControl.recordLoginFailure(verdict.matchedUsername(), attribution);
            throw ApiErrors.unauthorized("invalid-credentials", "Login failed");
        }
        throttle.recordSuccess(address);
        auditControl.recordLoginSuccess(verdict.username(), attribution);
        TokenStore.IssuedToken issued = tokenStore.issue(verdict.username());
        return new LoginResponse()
                .token(issued.rawToken())
                .username(verdict.username())
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
     * Own password change (constitution v1.5.1, scoped per user by
     * auth-hardening REQ-611/D13): the caller is the principal name (the
     * username, REQ-612); {@link UserControl#changeOwnPassword} verifies the
     * current password, enforces the policy, stores the new hash and fires
     * {@link UserControl.CredentialsInvalidated}, which
     * {@link #onCredentialsInvalidated} turns into dropping THAT user's
     * tokens — including the caller's — so the old credential never survives
     * into another idle window. Other users' sessions are untouched.
     *
     * <p>The current-password check is a password-bearing operation, so it
     * shares the caller address's login budget (REQ-604): checked first, and
     * a mismatch counts as a failure.
     */
    public void changePassword(PasswordChangeRequest request) {
        String address = clientAttribution.attribution().address();
        throttle.check(address);
        boolean changed = userControl.changeOwnPassword(identity.getPrincipal().getName(),
                request.getCurrentPassword(), request.getNewPassword());
        if (!changed) {
            throttle.recordFailure(address);
            throw ApiErrors.badRequest("current-password-mismatch", "The current password is not correct");
        }
    }

    /**
     * The one way account changes reach the session store (design D14):
     * {@code de.gxp.user} fires this synchronous CDI event on password
     * change, admin reset and deactivation; the user's tokens are dead on
     * the next request. Greppable, no circular injection — {@code UserControl}
     * never references this Control.
     */
    void onCredentialsInvalidated(@Observes UserControl.CredentialsInvalidated event) {
        tokenStore.removeAllOf(event.username());
    }
}
