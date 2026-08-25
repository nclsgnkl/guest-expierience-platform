package de.gxp.auth.control;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/**
 * First-start credential seeding (REQ-330, constitution v1.5.x): when no
 * credential row exists, the hash is seeded from {@code GXP_ADMIN_PASSWORD}
 * (MicroProfile Config maps it from {@code gxp.admin.password}); once a row
 * exists the variable is ignored forever — never re-read, never logged.
 *
 * <p>Unset or blank with no row is a refused start: the failure message names
 * the VARIABLE only, never any value (principle 3). Startup observers run
 * before the HTTP server accepts requests.
 *
 * <p>Since login throttling was removed entirely (REQ-326, owner decision
 * 2026-08-24 — no lockout, no rate limit, no concurrency gate), brute-force
 * resistance rests on the password itself. So the {@code ≥ 8} minimum
 * {@code AuthControl.changePassword} enforces applies to the first-start seed
 * too, using the same refuse-to-start posture as the unset variable rather
 * than a new concept.
 */
@ApplicationScoped
class AdminPasswordSeeder {

    /** Same minimum as {@code AuthControl.changePassword} — one policy, two entry points. */
    static final int MINIMUM_PASSWORD_LENGTH = 8;

    private final AdminCredentialRepository credentials;
    private final Pbkdf2PasswordHasher hasher;
    private final Optional<String> configuredPassword;

    AdminPasswordSeeder(AdminCredentialRepository credentials, Pbkdf2PasswordHasher hasher,
            @ConfigProperty(name = "gxp.admin.password") Optional<String> configuredPassword) {
        this.credentials = credentials;
        this.hasher = hasher;
        this.configuredPassword = configuredPassword;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        seedIfMissing();
    }

    void seedIfMissing() {
        if (credentials.theRow() != null) {
            return; // seeded once — the environment variable is dead from here on
        }
        String password = configuredPassword.orElse("");
        if (password.isBlank()) {
            throw new IllegalStateException(
                    "No admin credential exists yet and GXP_ADMIN_PASSWORD is unset or blank. "
                            + "Set GXP_ADMIN_PASSWORD for the first start; it is stored hashed and ignored afterwards.");
        }
        if (password.length() < MINIMUM_PASSWORD_LENGTH) {
            // names the variable and the rule, never the value (principle 3)
            throw new IllegalStateException(
                    "GXP_ADMIN_PASSWORD is shorter than the required minimum of " + MINIMUM_PASSWORD_LENGTH
                            + " characters. Set a longer value for the first start; there is no login "
                            + "throttling, so the password is the only thing resisting guesses.");
        }
        credentials.seed(hasher.hash(password), Instant.now());
    }
}
