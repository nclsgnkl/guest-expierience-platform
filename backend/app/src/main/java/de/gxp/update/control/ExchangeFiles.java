package de.gxp.update.control;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.gxp.api.model.UpdaterStateDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The ONLY code touching {@code gxp.update.exchange-dir} — the volume shared
 * with the host-side updater (update-channel REQ-715/716/717, D3). Two
 * files, no secrets:
 *
 * <ul>
 * <li>{@code desired-update.json} — written here in the FIXED line layout of
 * Design §2 (hand-built, never {@code ObjectMapper} serialization: the
 * updater reads it with {@code sed}), via {@code .desired-update.json.tmp}
 * + {@code ATOMIC_MOVE}, so the updater never sees a partial file. Deleted
 * here only.</li>
 * <li>{@code state.json} — written by the updater (root). Read here as
 * UNTRUSTED input even though the writer is more privileged: size cap,
 * tolerant JSON (unknown keys and a newer {@code schemaVersion} are fine —
 * REQ-716), and every value regex-checked against the shared table before
 * it reaches a DTO. Anything else is "no state" plus a log line carrying
 * the exception's class name, never file content.</li>
 * </ul>
 *
 * The canonical expected output lives in {@code backend/updater/test/fixtures/}
 * and is asserted byte-identical by {@code ExchangeFilesTest}; the updater's
 * {@code parse.sh} reads the same files.
 */
@ApplicationScoped
class ExchangeFiles {

    static final String DESIRED_FILE = "desired-update.json";
    static final String DESIRED_TEMP = ".desired-update.json.tmp";
    static final String STATE_FILE = "state.json";
    static final int MAX_STATE_BYTES = 64 * 1024;
    static final int APPLY_WINDOW_SECONDS = 7200;
    static final String REQUESTED_BY_AUTO = "auto";
    static final String REQUESTED_BY_MANUAL = "manual";

    static final Pattern BACKUP_FILE = Pattern.compile("^pre-update-[0-9A-Za-z.-]+-[0-9]{8}T[0-9]{6}Z\\.dump$");
    static final Pattern BASE64 = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");
    /** {@code outcome}/{@code message}/{@code requestedBy}: a short lower-case code, never text. */
    static final Pattern CODE = Pattern.compile("^[a-z0-9-]{1,32}$");

    private static final Logger LOG = Logger.getLogger(ExchangeFiles.class);
    private static final DateTimeFormatter INSTANT_Z = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    /** What the app asks the updater to do — every string already validated (the manifest bytes are the verified originals). */
    record DesiredUpdate(String version, String artifactRef, String sha256, Instant requestedAt, String requestedBy,
            Instant applyNotBefore, byte[] manifest, byte[] signature) {
    }

    /** The DTO plus the one field the reconcile rule needs that the contract does not expose. */
    record UpdaterState(UpdaterStateDto dto, Instant lastRequestedAt) {
    }

    private final Path directory;
    private final ObjectMapper lenient = new ObjectMapper(); // readTree only — nothing is bound

    @Inject
    ExchangeFiles(@ConfigProperty(name = "gxp.update.exchange-dir") String directory) {
        this(Path.of(directory));
    }

    ExchangeFiles(Path directory) {
        this.directory = directory;
    }

    // ------------------------------------------------------------ desired-update.json (written by the app)

    /** Temp file in the same directory, then an atomic rename over any previous file. */
    void writeDesired(DesiredUpdate desired) {
        byte[] content = render(desired).getBytes(StandardCharsets.UTF_8);
        Path temp = directory.resolve(DESIRED_TEMP);
        try {
            Files.createDirectories(directory);
            Files.write(temp, content);
            Files.move(temp, directory.resolve(DESIRED_FILE), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // the failure below is the one worth reporting
            }
            throw new IllegalStateException("desired-update file could not be written: " + e.getClass().getName());
        }
    }

    void deleteDesired() {
        try {
            Files.deleteIfExists(directory.resolve(DESIRED_FILE));
        } catch (IOException e) {
            throw new IllegalStateException("desired-update file could not be removed: " + e.getClass().getName());
        }
    }

    /** The app's own file read back (after a restart); anything not exactly our layout is "none". */
    Optional<DesiredUpdate> readDesired() {
        Path file = directory.resolve(DESIRED_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode node = lenient.readTree(readCapped(file));
            return Optional.of(new DesiredUpdate(
                    text(node, "version", SemanticVersion.PATTERN, true),
                    text(node, "artifactRef", ManifestParser.ARTIFACT_REF, true),
                    text(node, "sha256", ManifestParser.SHA256, true),
                    instant(node, "requestedAt", true),
                    text(node, "requestedBy", CODE, true),
                    instant(node, "applyNotBefore", true),
                    Base64.getDecoder().decode(text(node, "manifest", BASE64, true)),
                    Base64.getDecoder().decode(text(node, "signature", BASE64, true))));
        } catch (IOException | RuntimeException e) {
            LOG.warnf("Desired-update file unreadable: %s", e.getClass().getName());
            return Optional.empty();
        }
    }

    private static String render(DesiredUpdate d) {
        require(d.version(), SemanticVersion.PATTERN, "version");
        require(d.artifactRef(), ManifestParser.ARTIFACT_REF, "artifactRef");
        require(d.sha256(), ManifestParser.SHA256, "sha256");
        require(d.requestedBy(), CODE, "requestedBy");
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"version\": \"" + d.version() + "\",\n"
                + "  \"artifactRef\": \"" + d.artifactRef() + "\",\n"
                + "  \"sha256\": \"" + d.sha256() + "\",\n"
                + "  \"requestedAt\": \"" + INSTANT_Z.format(d.requestedAt()) + "\",\n"
                + "  \"requestedBy\": \"" + d.requestedBy() + "\",\n"
                + "  \"applyNotBefore\": \"" + INSTANT_Z.format(d.applyNotBefore()) + "\",\n"
                + "  \"applyWindowSeconds\": " + APPLY_WINDOW_SECONDS + ",\n"
                + "  \"manifest\": \"" + Base64.getEncoder().encodeToString(d.manifest()) + "\",\n"
                + "  \"signature\": \"" + Base64.getEncoder().encodeToString(d.signature()) + "\"\n"
                + "}\n";
    }

    // ------------------------------------------------------------ state.json (written by the updater)

    /** Missing → empty; unreadable or any value outside its regex → empty + one class-name log line. */
    Optional<UpdaterState> readState() {
        Path file = directory.resolve(STATE_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode node = lenient.readTree(readCapped(file));
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("state is not a JSON object");
            }
            Instant lastRequestedAt = instant(node, "lastRequestedAt", false);
            return Optional.of(new UpdaterState(new UpdaterStateDto()
                    .updatedAt(instant(node, "updatedAt", true).atOffset(ZoneOffset.UTC))
                    .lastAttemptAt(offset(instant(node, "lastAttemptAt", false)))
                    .attemptedVersion(text(node, "attemptedVersion", SemanticVersion.PATTERN, false))
                    .outcome(text(node, "outcome", CODE, true))
                    .rolledBack(bool(node, "rolledBack"))
                    .currentImage(text(node, "currentImage", ManifestParser.ARTIFACT_REF, true))
                    .currentVersion(text(node, "currentVersion", SemanticVersion.PATTERN, false))
                    .previousImage(text(node, "previousImage", ManifestParser.ARTIFACT_REF, false))
                    .backupFile(text(node, "backupFile", BACKUP_FILE, false))
                    .message(text(node, "message", CODE, false)), lastRequestedAt));
        } catch (IOException | RuntimeException e) {
            LOG.warnf("Updater state file unreadable: %s", e.getClass().getName());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------ helpers

    /** Reads at most the cap + 1 in one go (no size-then-read window, review R2-7); one byte over the cap is "too big". */
    private static byte[] readCapped(Path file) throws IOException {
        try (java.io.InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(MAX_STATE_BYTES + 1);
            if (bytes.length > MAX_STATE_BYTES) {
                throw new IllegalArgumentException("exchange file above 64 KiB");
            }
            return bytes;
        }
    }

    private static String text(JsonNode node, String field, Pattern pattern, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw new IllegalArgumentException("state field missing: " + field);
            }
            return null;
        }
        if (!value.isTextual() || !pattern.matcher(value.asText()).matches()) {
            throw new IllegalArgumentException("state field invalid: " + field);
        }
        return value.asText();
    }

    private static Instant instant(JsonNode node, String field, boolean required) {
        String text = text(node, field, ManifestParser.INSTANT, required);
        try {
            return text == null ? null : Instant.parse(text);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("state field invalid: " + field);
        }
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("state field missing or invalid: " + field);
        }
        return value.asBoolean();
    }

    private static java.time.OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static void require(String value, Pattern pattern, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("desired-update field invalid: " + field);
        }
    }
}
