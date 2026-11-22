package de.gxp.user.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.user.entity.ManagerUser;
import de.gxp.user.entity.RecoveryCode;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The CONCURRENT edge of the two single-use guarantees (review of T-08…T-10,
 * 2026-08-26, finding #1; REQ-614 "refused", REQ-616 "exactly once"): two
 * transactions presenting the same TOTP code or the same recovery code at
 * the same moment must yield exactly one session. Sequential replay is
 * proved over HTTP by {@code SecondFactorLoginTest}; this class drives the
 * Control directly on two threads so both verifications run in separate,
 * simultaneously open transactions. Whatever the interleaving, the
 * conditional UPDATE in the repository lets only one of them affect a row —
 * so the assertion is deterministic, not a lucky race.
 */
@QuarkusTest
class SecondFactorSingleUseTest {

    private static final String USER = "single-use-user";
    private static final String PASSWORD = "single-use-password-1";
    private static final String RECOVERY = "kkkkk11111";

    @Inject
    ManagerUserRepository users;
    @Inject
    RecoveryCodeRepository recoveryCodes;
    @Inject
    Pbkdf2PasswordHasher hasher;
    @Inject
    TotpVerifier totp;
    @Inject
    UserControl userControl;

    private byte[] secret;

    @BeforeEach
    void seedConfirmedUser() {
        secret = totp.generateSecret();
        QuarkusTransaction.requiringNew().run(() -> {
            ManagerUser user = new ManagerUser();
            user.username = USER;
            user.passwordHash = hasher.hash(PASSWORD);
            user.totpSecret = Base64.getEncoder().encodeToString(secret);
            user.totpConfirmedAt = Instant.now();
            user.createdAt = Instant.now();
            user.updatedAt = user.createdAt;
            users.persist(user);
            RecoveryCode row = new RecoveryCode();
            row.user = user;
            row.codeHash = RecoveryCodes.hash(RECOVERY);
            recoveryCodes.persist(row);
        });
    }

    @AfterEach
    void removeUser() {
        QuarkusTransaction.requiringNew().run(() -> users.delete("username", USER));
    }

    @Test
    void theSameTotpCodeIsAcceptedByExactlyOneOfTwoConcurrentLogins() throws Exception {
        long step = totp.currentStep();
        String code = totp.codeAt(secret, step);
        assertEquals(1, accepted(code, null), "one code, two simultaneous logins, one session (REQ-614)");
        Long stored = QuarkusTransaction.requiringNew().call(() -> users.byUsername(USER).totpLastStep);
        assertEquals(step, stored, "the accepted step is the replay guard, written once by the conditional UPDATE");
    }

    @Test
    void theSameRecoveryCodeIsAcceptedByExactlyOneOfTwoConcurrentLogins() throws Exception {
        assertEquals(1, accepted(null, RECOVERY), "one recovery code, two simultaneous logins, one session (REQ-616)");
        long unused = QuarkusTransaction.requiringNew().call(() -> recoveryCodes.countUnused(users.byUsername(USER)));
        assertEquals(0, unused, "the code is spent exactly once");
    }

    /** Runs two verifications released together by a barrier; returns how many succeeded. */
    private long accepted(String totpCode, String recoveryCode) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Boolean> attempt = () -> {
            Arc.container().requestContext().activate();
            try {
                start.await();
                return userControl.verifyLogin(USER, PASSWORD, totpCode, recoveryCode, true).ok();
            } finally {
                Arc.container().requestContext().terminate();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> outcomes = pool.invokeAll(List.of(attempt, attempt));
            long ok = 0;
            for (Future<Boolean> outcome : outcomes) {
                if (outcome.get()) {
                    ok++;
                }
            }
            return ok;
        } finally {
            pool.shutdownNow();
        }
    }
}
