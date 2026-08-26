package de.gxp.user.control;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.OptionalLong;
import java.util.regex.Pattern;

import de.gxp.api.model.PasswordConfirmationRequest;
import de.gxp.api.model.PasswordResetRequest;
import de.gxp.api.model.RecoveryCodesDto;
import de.gxp.api.model.SecondFactorCodeRequest;
import de.gxp.api.model.SecondFactorEnrollmentDto;
import de.gxp.api.model.SecondFactorState;
import de.gxp.api.model.SecondFactorStatusDto;
import de.gxp.api.model.UserCreateRequest;
import de.gxp.api.model.UserDto;
import de.gxp.audit.control.AuditControl;
import de.gxp.infrastructure.http.ApiErrors;
import de.gxp.user.entity.ManagerUser;
import de.gxp.user.entity.ManagerUserMapper;
import de.gxp.user.entity.RecoveryCode;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;

/**
 * Control — manager accounts (auth-hardening A2, REQ-607..REQ-613). This is
 * the feature's one public type; {@code AuthControl} (sessions) depends on
 * it, never the other way round: whenever a credential stops being valid —
 * own password change, admin reset, deactivation — this Control fires the
 * synchronous CDI event {@link CredentialsInvalidated} and the session store
 * drops that user's tokens (REQ-611, design D13/D14).
 *
 * <p><b>No username oracle</b> (REQ-609): {@link #verifyLogin} costs the same
 * PBKDF2 work whether the username is unknown, the account is deactivated or
 * the password is wrong — when no active account matches, the password is
 * verified against a fixed dummy hash derived with the configured iteration
 * count. The {@link Verdict} never carries a reason; the caller maps a miss
 * to the one generic 401.
 *
 * <p><b>Second factor</b> (A3, REQ-614..616, D9–D11): the TOTP secret and
 * the recovery codes are handled here only. The secret leaves this class
 * exactly once, in {@link #enrollSecondFactor}'s response; recovery codes
 * exactly once, in {@link #confirmSecondFactor} / {@link #regenerateRecoveryCodes}.
 * Disable and regenerate require the current password (D11).
 *
 * <p><b>Audited</b> (A4, REQ-621): every account and second-factor action
 * records one row through {@link AuditControl#record} in the same
 * transaction — action name, {@code user}, the affected username; never a
 * hash, secret or code (the table has no value column).
 *
 * <p>Logs nothing; no password material, secret or code in any message
 * (principle 3, REQ-619).
 */
@ApplicationScoped
public class UserControl {

    /** The contract's username rule — also enforced on the first-start seed. */
    static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");
    /** Same minimum as the first-start seed — one policy, every entry point. */
    static final int MINIMUM_PASSWORD_LENGTH = 8;

    /**
     * Fired (synchronously) whenever a user's credential stops being valid;
     * observed by the session store's Control, which drops that user's
     * tokens. Carries the username only.
     */
    public record CredentialsInvalidated(String username) {
    }

    /**
     * Outcome of a login verification — {@code ok} or not, never why.
     * {@code username} is set on success only. {@code matchedUsername} is the
     * canonical name of the account the submitted name resolved to (active or
     * not), null when nothing matched — it exists for the audit row of a
     * failure (REQ-620: never the submitted free text, which may be a
     * mistyped password) and is NEVER surfaced to the client (REQ-609).
     */
    public record Verdict(boolean ok, String username, String matchedUsername) {
        static Verdict fail(ManagerUser matched) {
            return new Verdict(false, null, matched == null ? null : matched.username);
        }

        static Verdict ok(String username) {
            return new Verdict(true, username, username);
        }
    }

    private final ManagerUserRepository users;
    private final RecoveryCodeRepository recoveryCodes;
    private final Pbkdf2PasswordHasher hasher;
    private final TotpVerifier totp;
    private final AuditControl auditControl;
    private final Event<CredentialsInvalidated> credentialsInvalidated;
    private final SecurityIdentity identity;
    /** Verified against when no active account matches — equal work, no oracle. */
    private final String dummyHash;

    UserControl(ManagerUserRepository users, RecoveryCodeRepository recoveryCodes, Pbkdf2PasswordHasher hasher,
            TotpVerifier totp, AuditControl auditControl, Event<CredentialsInvalidated> credentialsInvalidated,
            SecurityIdentity identity) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.hasher = hasher;
        this.totp = totp;
        this.auditControl = auditControl;
        this.credentialsInvalidated = credentialsInvalidated;
        this.identity = identity;
        this.dummyHash = hasher.hash("no-such-user-" + System.nanoTime());
    }

    // ------------------------------------------------------------ administration (REQ-610)

    /** Every account, active or not, by id. Never any hash (REQ-613). */
    public List<UserDto> list() {
        return users.list("order by id").stream().map(ManagerUserMapper::toDto).toList();
    }

    /** Creates an active account; {@code 409 username-taken} on a duplicate. */
    @Transactional
    public UserDto create(UserCreateRequest request) {
        String username = request.getUsername();
        if (users.byUsername(username) != null) {
            throw ApiErrors.conflict("username-taken", "That username is already taken");
        }
        Instant now = Instant.now();
        ManagerUser user = new ManagerUser();
        user.username = username;
        user.passwordHash = hasher.hash(request.getPassword());
        user.active = true;
        user.createdAt = now;
        user.updatedAt = now;
        users.persist(user);
        auditControl.record(AuditControl.ACTION_USER_CREATE, AuditControl.ENTITY_USER, username, null);
        return ManagerUserMapper.toDto(user);
    }

    /**
     * Deactivates another account and drops its tokens (REQ-611). The
     * caller's own account is refused with {@code 409 cannot-deactivate-self}
     * (D12) — the one guard against a box with zero active admins.
     */
    @Transactional
    public void deactivate(long userId) {
        ManagerUser user = existing(userId);
        if (user.username.equals(identity.getPrincipal().getName())) {
            throw ApiErrors.conflict("cannot-deactivate-self", "An account cannot deactivate itself");
        }
        user.active = false;
        user.updatedAt = Instant.now();
        auditControl.record(AuditControl.ACTION_USER_DEACTIVATE, AuditControl.ENTITY_USER, user.username, null);
        credentialsInvalidated.fire(new CredentialsInvalidated(user.username));
    }

    @Transactional
    public void reactivate(long userId) {
        ManagerUser user = existing(userId);
        user.active = true;
        user.updatedAt = Instant.now();
        auditControl.record(AuditControl.ACTION_USER_REACTIVATE, AuditControl.ENTITY_USER, user.username, null);
    }

    /**
     * Admin reset of ANOTHER account's password; that user's tokens are
     * dropped (REQ-611). The caller's own account is refused with
     * {@code 409 cannot-reset-self} (D12 amendment, lead decision
     * 2026-08-26): an own change goes through {@code PUT /api/manager/password}
     * and needs the current password — otherwise an idle-but-open session
     * could take the account over (the same reasoning as D11).
     */
    @Transactional
    public void resetPassword(long userId, PasswordResetRequest request) {
        ManagerUser user = existing(userId);
        if (user.username.equals(identity.getPrincipal().getName())) {
            throw ApiErrors.conflict("cannot-reset-self", "Use the own-password change for your own account");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < MINIMUM_PASSWORD_LENGTH) {
            throw ApiErrors.badRequest("password-policy", "The new password must have at least 8 characters");
        }
        user.passwordHash = hasher.hash(request.getNewPassword());
        user.updatedAt = Instant.now();
        auditControl.record(AuditControl.ACTION_PASSWORD_RESET, AuditControl.ENTITY_USER, user.username, null);
        credentialsInvalidated.fire(new CredentialsInvalidated(user.username));
    }

    /**
     * Admin reset of ANOTHER account's second factor (REQ-618, §12 item 4 —
     * the documented recovery route): clears the secret and every recovery
     * code; that user's tokens are dropped (a lost device may hold a
     * session). Own account → {@code 409 cannot-reset-self}; the own path is
     * {@link #disableSecondFactor} with the current password (D11).
     */
    @Transactional
    public void resetSecondFactor(long userId) {
        ManagerUser user = existing(userId);
        if (user.username.equals(identity.getPrincipal().getName())) {
            throw ApiErrors.conflict("cannot-reset-self", "Use the own second-factor removal for your own account");
        }
        clearSecondFactor(user);
        auditControl.record(AuditControl.ACTION_SECOND_FACTOR_RESET, AuditControl.ENTITY_USER, user.username, null);
        credentialsInvalidated.fire(new CredentialsInvalidated(user.username));
    }

    private ManagerUser existing(long userId) {
        ManagerUser user = users.findById(userId);
        if (user == null) {
            throw ApiErrors.notFound("user-not-found", "No such account");
        }
        return user;
    }

    // ------------------------------------------------------------ second factor, own (REQ-615/616)

    public SecondFactorStatusDto secondFactorStatus() {
        ManagerUser me = self();
        SecondFactorState state = ManagerUserMapper.secondFactorState(me);
        int remaining = state == SecondFactorState.CONFIRMED ? (int) recoveryCodes.countUnused(me) : 0;
        return new SecondFactorStatusDto().state(state).recoveryCodesRemaining(remaining);
    }

    /**
     * Issues a PENDING secret and returns it once (base32 + otpauth URI).
     * Re-enrolling replaces a pending secret; a confirmed factor is refused
     * with {@code 409 second-factor-already-confirmed} — disable it first.
     */
    @Transactional
    public SecondFactorEnrollmentDto enrollSecondFactor() {
        ManagerUser me = self();
        if (me.totpConfirmedAt != null) {
            throw ApiErrors.conflict("second-factor-already-confirmed", "A second factor is already confirmed");
        }
        byte[] secret = totp.generateSecret();
        me.totpSecret = Base64.getEncoder().encodeToString(secret);
        me.totpLastStep = null;
        me.updatedAt = Instant.now();
        auditControl.record(AuditControl.ACTION_SECOND_FACTOR_ENROLL, AuditControl.ENTITY_USER, me.username, null);
        return new SecondFactorEnrollmentDto()
                .secret(totp.base32(secret))
                .otpauthUri(totp.otpauthUri(me.username, secret));
    }

    /**
     * One valid code turns pending into confirmed and returns the first
     * batch of recovery codes. {@code 409 no-pending-enrollment} when nothing
     * is pending, {@code 400 invalid-code} on a miss (the caller is
     * authenticated and this is their own factor — no oracle concern).
     */
    @Transactional
    public RecoveryCodesDto confirmSecondFactor(SecondFactorCodeRequest request) {
        ManagerUser me = self();
        if (me.totpSecret == null || me.totpConfirmedAt != null) {
            throw ApiErrors.conflict("no-pending-enrollment", "Enroll first");
        }
        OptionalLong step = totp.verify(secretOf(me), request.getCode(), null);
        if (step.isEmpty()) {
            throw ApiErrors.badRequest("invalid-code", "The code did not verify");
        }
        Instant now = Instant.now();
        me.totpConfirmedAt = now;
        me.totpLastStep = step.getAsLong();
        me.updatedAt = now;
        auditControl.record(AuditControl.ACTION_SECOND_FACTOR_CONFIRM, AuditControl.ENTITY_USER, me.username, null);
        return new RecoveryCodesDto().codes(replaceRecoveryCodes(me));
    }

    /** Clears secret and codes after the current password verified (D11); idempotent. */
    @Transactional
    public void disableSecondFactor(PasswordConfirmationRequest request) {
        ManagerUser me = selfWithPassword(request.getPassword());
        clearSecondFactor(me);
        auditControl.record(AuditControl.ACTION_SECOND_FACTOR_DISABLE, AuditControl.ENTITY_USER, me.username, null);
    }

    /** New batch, old batch void — password required (D11), confirmed factor required. */
    @Transactional
    public RecoveryCodesDto regenerateRecoveryCodes(PasswordConfirmationRequest request) {
        ManagerUser me = selfWithPassword(request.getPassword());
        if (me.totpConfirmedAt == null) {
            throw ApiErrors.conflict("second-factor-not-confirmed", "No confirmed second factor");
        }
        auditControl.record(AuditControl.ACTION_RECOVERY_CODES_REGENERATE, AuditControl.ENTITY_USER, me.username, null);
        return new RecoveryCodesDto().codes(replaceRecoveryCodes(me));
    }

    private ManagerUser self() {
        ManagerUser me = users.byUsername(identity.getPrincipal().getName());
        if (me == null) {
            throw ApiErrors.notFound("user-not-found", "No such account");
        }
        return me;
    }

    private ManagerUser selfWithPassword(String password) {
        ManagerUser me = self();
        if (!hasher.verify(password, me.passwordHash)) {
            throw ApiErrors.badRequest("current-password-mismatch", "The current password is not correct");
        }
        return me;
    }

    private void clearSecondFactor(ManagerUser user) {
        user.totpSecret = null;
        user.totpConfirmedAt = null;
        user.totpLastStep = null;
        user.updatedAt = Instant.now();
        recoveryCodes.deleteAllOf(user);
    }

    /** Drops every code of the user and stores a fresh batch; returns the plain codes for the one-time response. */
    private List<String> replaceRecoveryCodes(ManagerUser user) {
        recoveryCodes.deleteAllOf(user);
        List<String> plain = RecoveryCodes.generate();
        for (String code : plain) {
            RecoveryCode row = new RecoveryCode();
            row.user = user;
            row.codeHash = RecoveryCodes.hash(code);
            recoveryCodes.persist(row);
        }
        return plain;
    }

    private static byte[] secretOf(ManagerUser user) {
        return Base64.getDecoder().decode(user.totpSecret);
    }

    // ------------------------------------------------------------ verification (REQ-609)

    /** Password-only verification (the LAN path, REQ-617/D8). */
    public Verdict verifyLogin(String username, String password) {
        return verifyLogin(username, password, null, null, false);
    }

    /**
     * Verifies username + password and — only when {@code requireSecondFactor}
     * — the second factor (REQ-617, D7–D9). Unknown username, deactivated
     * account and wrong password all return the same {@code fail} verdict
     * after the same PBKDF2 work. With the factor required: no confirmed
     * factor → {@code fail} (after the password work, so it is not a
     * password oracle); {@code totpCode} → RFC 6238 ±1 step, accepted step
     * stored as the replay guard; else {@code recoveryCode} → SHA-256 match
     * against an unspent row, which is marked used; else {@code fail}. Both
     * consumptions are conditional UPDATEs that require one affected row,
     * so a code presented concurrently twice is accepted once (REQ-614/616).
     * The consumption commits with this transaction. Without the requirement
     * both code fields are ignored entirely (D8).
     */
    @Transactional
    public Verdict verifyLogin(String username, String password, String totpCode, String recoveryCode,
            boolean requireSecondFactor) {
        ManagerUser user = username == null ? null : users.byUsername(username);
        if (user == null || !user.active) {
            hasher.verify(password, dummyHash);
            return Verdict.fail(user);
        }
        if (!hasher.verify(password, user.passwordHash)) {
            return Verdict.fail(user);
        }
        if (requireSecondFactor && !secondFactorAccepted(user, totpCode, recoveryCode)) {
            return Verdict.fail(user);
        }
        return Verdict.ok(user.username);
    }

    private boolean secondFactorAccepted(ManagerUser user, String totpCode, String recoveryCode) {
        if (user.totpSecret == null || user.totpConfirmedAt == null) {
            return false;
        }
        if (totpCode != null) {
            OptionalLong step = totp.verify(secretOf(user), totpCode, user.totpLastStep);
            if (step.isEmpty()) {
                return false;
            }
            // conditional UPDATE — the second of two concurrent uses of one code affects no row
            // the DB holds the step now; the managed entity is left untouched on purpose — dirtying it
            // would issue a second, full-row UPDATE from a stale snapshot (overwriting a concurrent reset)
            if (!users.advanceTotpStep(user, step.getAsLong())) {
                return false;
            }
            return true;
        }
        if (recoveryCode != null) {
            String hash = RecoveryCodes.hash(recoveryCode);
            for (RecoveryCode row : recoveryCodes.unusedOf(user)) {
                if (MessageDigest.isEqual(row.codeHash.getBytes(StandardCharsets.US_ASCII),
                        hash.getBytes(StandardCharsets.US_ASCII))) {
                    // conditional UPDATE — a row spent concurrently is not spent twice
                    return recoveryCodes.markUsed(row, Instant.now());
                }
            }
        }
        return false;
    }

    /**
     * Changes the named account's own password: verifies the current one
     * (returns {@code false} on a mismatch — the caller counts it against
     * the address and answers the 400), enforces the ≥ 8 policy (also
     * contract-level bean validation), stores the new hash and fires
     * {@link CredentialsInvalidated} so that user's tokens die with the old
     * password (REQ-611).
     */
    @Transactional
    public boolean changeOwnPassword(String username, String currentPassword, String newPassword) {
        ManagerUser user = users.byUsername(username);
        if (user == null || !user.active || !hasher.verify(currentPassword, user.passwordHash)) {
            return false;
        }
        if (newPassword == null || newPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw ApiErrors.badRequest("password-policy", "The new password must have at least 8 characters");
        }
        user.passwordHash = hasher.hash(newPassword);
        user.updatedAt = Instant.now();
        auditControl.record(AuditControl.ACTION_PASSWORD_CHANGE, AuditControl.ENTITY_USER, user.username, null);
        credentialsInvalidated.fire(new CredentialsInvalidated(user.username));
        return true;
    }

    /**
     * Test probe — how many PBKDF2 verifications the hasher has run. Public
     * only because the throttle tests live in {@code de.gxp.auth.control}
     * and must prove that a 429 is answered BEFORE any derivation (REQ-602);
     * the hasher itself stays package-private here.
     */
    public long passwordVerifications() {
        return hasher.verifyCount();
    }
}
