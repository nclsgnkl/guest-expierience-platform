package de.gxp.user.control;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.gxp.user.entity.ManagerUser;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/**
 * First-start account seeding (auth-hardening REQ-608, design D15; replaces
 * {@code AdminPasswordSeeder} of REQ-330): when NO manager account exists,
 * the first one is created from {@code GXP_ADMIN_USERNAME} (MicroProfile
 * Config {@code gxp.admin.username}, optional, default {@code admin}) and
 * {@code GXP_ADMIN_PASSWORD} ({@code gxp.admin.password}, mandatory, ≥ 8).
 * Once any account exists both variables are ignored forever — never
 * re-read, never logged.
 *
 * <p>Unset/blank/too short password or an invalid username with no account
 * is a refused start: every message names the VARIABLE and the rule only,
 * never a value (principle 3). Startup observers run before the HTTP server
 * accepts requests.
 */
@ApplicationScoped
class FirstUserSeeder {

    /** Same minimum as the own-password change — one policy, every entry point. */
    static final int MINIMUM_PASSWORD_LENGTH = 8;
    static final String DEFAULT_USERNAME = "admin";

    private final ManagerUserRepository users;
    private final Pbkdf2PasswordHasher hasher;
    private final Optional<String> configuredUsername;
    private final Optional<String> configuredPassword;

    FirstUserSeeder(ManagerUserRepository users, Pbkdf2PasswordHasher hasher,
            @ConfigProperty(name = "gxp.admin.username") Optional<String> configuredUsername,
            @ConfigProperty(name = "gxp.admin.password") Optional<String> configuredPassword) {
        this.users = users;
        this.hasher = hasher;
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        seedIfMissing();
    }

    void seedIfMissing() {
        if (users.count() > 0) {
            return; // seeded once — the environment variables are dead from here on
        }
        String username = configuredUsername.map(String::trim).filter(v -> !v.isEmpty()).orElse(DEFAULT_USERNAME);
        if (!UserControl.USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalStateException(
                    "No manager account exists yet and GXP_ADMIN_USERNAME is not a valid username"
                            + " (lower-case letters, digits, '.', '_' and '-', 1-64 characters, starting with a"
                            + " letter or digit). Fix it or unset it to use the default for the first start.");
        }
        String password = configuredPassword.orElse("");
        if (password.isBlank()) {
            throw new IllegalStateException(
                    "No manager account exists yet and GXP_ADMIN_PASSWORD is unset or blank. "
                            + "Set GXP_ADMIN_PASSWORD for the first start; it is stored hashed and ignored afterwards.");
        }
        if (password.length() < MINIMUM_PASSWORD_LENGTH) {
            // names the variable and the rule, never the value (principle 3)
            throw new IllegalStateException(
                    "GXP_ADMIN_PASSWORD is shorter than the required minimum of " + MINIMUM_PASSWORD_LENGTH
                            + " characters. Set a longer value for the first start.");
        }
        Instant now = Instant.now();
        ManagerUser user = new ManagerUser();
        user.username = username;
        user.passwordHash = hasher.hash(password);
        user.active = true;
        user.createdAt = now;
        user.updatedAt = now;
        users.persist(user);
    }
}
