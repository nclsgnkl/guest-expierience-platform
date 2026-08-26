package de.gxp.update.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.gxp.api.model.UpdaterStateDto;

/**
 * update-channel T-09 (REQ-715, REQ-716, REQ-717, D3): the app's half of the
 * file contract against the SHARED fixture under
 * {@code backend/updater/test/fixtures/} — the updater's {@code parse.sh}
 * reads the same files. Plain JUnit.
 */
class ExchangeFilesTest {

    static final Path FIXTURES = de.gxp.update.ManifestServer.fixtures();

    @TempDir
    Path directory;

    private static byte[] fixture(String name) throws IOException {
        return Files.readAllBytes(FIXTURES.resolve(name));
    }

    /** The fixture's inputs: the signed manifest pair + the desired file's schedule. */
    private static ExchangeFiles.DesiredUpdate fixtureDesired() throws IOException {
        byte[] manifest = fixture("manifest.json");
        byte[] signature = Base64.getDecoder().decode(new String(fixture("manifest.json.sig"), StandardCharsets.US_ASCII).strip());
        return new ExchangeFiles.DesiredUpdate("1.0.1", "ghcr.io/example/gxp-backend:1.0.1", "0123456789abcdef".repeat(4),
                Instant.parse("2026-09-01T10:00:00Z"), "auto", Instant.parse("2026-09-02T02:00:00Z"), manifest, signature);
    }

    @Test
    void writeDesiredIsByteIdenticalToTheSharedFixture() throws IOException {
        ExchangeFiles files = new ExchangeFiles(directory);
        files.writeDesired(fixtureDesired());
        assertArrayEquals(fixture("desired-update.json"), Files.readAllBytes(directory.resolve("desired-update.json")),
                "the updater's sed readers depend on this exact layout (D3)");
        assertFalse(Files.exists(directory.resolve(".desired-update.json.tmp")), "no temp file left behind");
    }

    @Test
    void theFinalNameIsOnlyEverCreatedByARenameNeverWrittenInPlace() throws Exception {
        ExchangeFiles files = new ExchangeFiles(directory);
        try (WatchService watch = FileSystems.getDefault().newWatchService()) {
            directory.register(watch, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            files.writeDesired(fixtureDesired()); // fresh
            files.writeDesired(fixtureDesired()); // overwrite — still a rename
            List<String> events = drain(watch);
            assertTrue(events.contains("ENTRY_CREATE desired-update.json"), events.toString());
            assertFalse(events.contains("ENTRY_MODIFY desired-update.json"),
                    "the final name was written in place — a reader could see a partial file: " + events);
            assertTrue(events.stream().anyMatch(e -> e.endsWith(".desired-update.json.tmp")), "temp file used: " + events);
        }
    }

    private static List<String> drain(WatchService watch) throws InterruptedException {
        List<String> events = new ArrayList<>();
        WatchKey key;
        while ((key = watch.poll(2, TimeUnit.SECONDS)) != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                events.add(event.kind().name() + " " + event.context());
            }
            key.reset();
        }
        return events;
    }

    @Test
    void readDesiredRoundTripsAndDeleteRemovesIt() throws IOException {
        ExchangeFiles files = new ExchangeFiles(directory);
        assertTrue(files.readDesired().isEmpty());
        files.writeDesired(fixtureDesired());
        ExchangeFiles.DesiredUpdate read = files.readDesired().orElseThrow();
        assertEquals("1.0.1", read.version());
        assertEquals("auto", read.requestedBy());
        assertEquals(Instant.parse("2026-09-02T02:00:00Z"), read.applyNotBefore());
        assertArrayEquals(fixture("manifest.json"), read.manifest());
        assertEquals(64, read.signature().length);
        files.deleteDesired();
        assertTrue(files.readDesired().isEmpty());
        files.deleteDesired(); // idempotent
    }

    @Test
    void everyStateFixtureRoundTripsIntoTheDto() throws IOException {
        List<Path> fixtures;
        try (Stream<Path> paths = Files.list(FIXTURES)) {
            fixtures = paths.filter(p -> p.getFileName().toString().startsWith("state-")).sorted().toList();
        }
        assertEquals(14, fixtures.size(), "every outcome of Design §2, in-progress, config-failed, and the future schema");
        for (Path fixture : fixtures) {
            Files.copy(fixture, directory.resolve("state.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String name = fixture.getFileName().toString().replace("state-", "").replace(".json", "");
            Optional<ExchangeFiles.UpdaterState> state = new ExchangeFiles(directory).readState();
            assertTrue(state.isPresent(), name);
            assertEquals(name.equals("future-schema") ? "applied" : name, state.get().dto().getOutcome(), name);
        }
        Files.copy(FIXTURES.resolve("state-applied.json"), directory.resolve("state.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ExchangeFiles.UpdaterState appliedState = new ExchangeFiles(directory).readState().orElseThrow();
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), appliedState.lastRequestedAt());
        UpdaterStateDto applied = appliedState.dto();
        assertEquals(OffsetDateTime.of(2026, 9, 2, 2, 3, 11, 0, ZoneOffset.UTC), applied.getUpdatedAt());
        assertEquals(OffsetDateTime.of(2026, 9, 2, 2, 0, 4, 0, ZoneOffset.UTC), applied.getLastAttemptAt());
        assertEquals("1.0.1", applied.getAttemptedVersion());
        assertEquals(false, applied.getRolledBack());
        assertEquals("ghcr.io/example/gxp-backend:1.0.1", applied.getCurrentImage());
        assertEquals("1.0.1", applied.getCurrentVersion());
        assertEquals("gxp-backend:1.0.0-SNAPSHOT", applied.getPreviousImage());
        assertEquals("pre-update-1.0.1-20260902T020004Z.dump", applied.getBackupFile());
        assertNull(applied.getMessage());

        Files.copy(FIXTURES.resolve("state-rolled-back.json"), directory.resolve("state.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        UpdaterStateDto rolledBack = new ExchangeFiles(directory).readState().orElseThrow().dto();
        assertEquals(true, rolledBack.getRolledBack());
        assertEquals("health-timeout", rolledBack.getMessage());

        Files.copy(FIXTURES.resolve("state-none.json"), directory.resolve("state.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ExchangeFiles.UpdaterState noneState = new ExchangeFiles(directory).readState().orElseThrow();
        assertNull(noneState.lastRequestedAt());
        UpdaterStateDto none = noneState.dto();
        assertNull(none.getLastAttemptAt());
        assertNull(none.getAttemptedVersion());
        assertNull(none.getBackupFile());
    }

    @Test
    void aMissingStateIsEmptyAndAnUnknownButWellFormedOutcomeIsTolerated() throws IOException {
        ExchangeFiles files = new ExchangeFiles(directory);
        assertTrue(files.readState().isEmpty());
        String newer = new String(fixture("state-applied.json"), StandardCharsets.UTF_8).replace("\"applied\"", "\"paused-by-operator\"");
        Files.writeString(directory.resolve("state.json"), newer);
        assertEquals("paused-by-operator", files.readState().orElseThrow().dto().getOutcome(), "REQ-716: a newer updater's code still renders");
    }

    @Test
    void stateIsUntrustedInputGarbageAndAnyValueOutsideItsRegexIsEmpty() throws IOException {
        ExchangeFiles files = new ExchangeFiles(directory);
        String good = new String(fixture("state-applied.json"), StandardCharsets.UTF_8);
        for (String broken : new String[] {
                "", "not json", "[]", "null", "{}",
                good.replace("ghcr.io/example/gxp-backend:1.0.1", "ghcr.io/x; rm -rf /"),   // shell text in an image ref
                good.replace("\"applied\"", "\"Applied\""),                                  // outcome is a lower-case code
                good.replace("\"applied\"", "\"applied but see <script>\""),
                good.replace("\"rolledBack\": false", "\"rolledBack\": \"false\""),          // wrong JSON type
                good.replace("\"rolledBack\": false,\n", ""),                                // required field missing
                good.replace("2026-09-02T02:03:11Z", "2026-09-02T02:03:11+02:00"),
                good.replace("pre-update-1.0.1-20260902T020004Z.dump", "../../etc/passwd"),
                good.replace("\"message\": null", "\"message\": \"" + "x".repeat(33) + "\"") }) {
            Files.writeString(directory.resolve("state.json"), broken);
            assertTrue(files.readState().isEmpty(), broken);
        }
        Files.write(directory.resolve("state.json"), new byte[64 * 1024 + 1]);
        assertTrue(files.readState().isEmpty(), "above the size cap");
    }
}
