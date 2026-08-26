package de.gxp.update.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import de.gxp.update.ManifestServer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

/** Shared arrangement for the update-channel {@code @QuarkusTest}s: seeded row, empty exchange dir, default seams. */
final class UpdateTestSupport {

    static final Path EXCHANGE = Path.of("target/update-exchange");

    private UpdateTestSupport() {
    }

    /** Back to the V8/V9 seed (tests share one database) and an empty exchange directory. */
    static void reset(EntityManager entityManager, UpdateControl control) throws IOException {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                "update update_channel set auto_update_enabled = true, apply_at = '04:00', last_check_at = null,"
                        + " last_check_outcome = 'never-checked', available_version = null, available_artifact_ref = null,"
                        + " available_sha256 = null, available_published_at = null, available_manifest = null,"
                        + " available_signature = null, manual_requested_version = null, manual_requested_at = null,"
                        + " trusted_keys = null, trusted_keys_version = null,"
                        + " updated_at = now()").executeUpdate());
        Files.createDirectories(EXCHANGE);
        Files.deleteIfExists(EXCHANGE.resolve(ExchangeFiles.DESIRED_FILE));
        Files.deleteIfExists(EXCHANGE.resolve(ExchangeFiles.STATE_FILE));
        control.useClock(Clock.systemUTC());
        control.useBaseUrl(ManifestServer.url(), true);
        ManifestServer.serveNothing();
    }

    static void plantState(String fixtureName) throws IOException {
        Files.copy(ManifestServer.fixtures().resolve(fixtureName), EXCHANGE.resolve(ExchangeFiles.STATE_FILE),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
