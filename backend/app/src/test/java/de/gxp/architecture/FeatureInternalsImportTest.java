package de.gxp.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Architecture guard A of three (constitution v1.6.0, 2026-08-24) — the
 * SOURCE side of the feature boundary. Part of the definition of done: it
 * must stay green.
 *
 * <p>Since the {@code boundary}/{@code control}/{@code entity} split landed,
 * package-private visibility no longer enforces "a feature's internals stay
 * inside the feature": Java has no sub-package visibility, so the Entity, the
 * Mapper and everything the Control needs across the split had to be widened.
 * These tests are what the owner accepted INSTEAD of the compiler, and
 * ArchUnit was rejected for them (a new dependency on an unpatchable box —
 * principle 4).
 *
 * <p>What this test asserts, over every {@code .java} file in
 * {@code src/main/java} and {@code src/test/java}, for EVERY feature package
 * {@link FeaturePackages} finds on disk:
 *
 * <ol>
 * <li>No file outside {@code de.gxp.<feature>} references
 * {@code de.gxp.<feature>.entity.*}, {@code de.gxp.<feature>.control.*} or
 * {@code de.gxp.<feature>.boundary.*} — by {@code import} or by
 * fully-qualified inline use. The ONE exception is the feature's own
 * {@code *Control}, which spec §4 makes the feature's public cross-feature
 * entry point. {@code .boundary} has NO exception: a {@code *Resource} is
 * public because the framework instantiates it, not because anyone may name
 * it. ({@code .boundary} was added to this scan 2026-08-25 — a review walked
 * a public non-{@code *Resource} type in another feature's {@code .boundary}
 * straight through all three guards and read an entity field off it.)</li>
 * <li>There are NO wildcard imports anywhere. Without this the scan above is
 * trivially defeated — {@code import de.gxp.x.entity.*;} names no type. The
 * codebase had zero on 2026-08-24 and this keeps it that way.</li>
 * <li>The layout is actually THERE: every feature's main sources live in
 * {@code boundary}/{@code control}/{@code entity} and nowhere else, and every
 * feature has an {@code .entity} package except the two that own no table and
 * a {@code .boundary} package except the one that serves no HTTP.
 * Without this a feature could be "covered" while holding no sub-packages at
 * all, and the scan above would pass by finding nothing.</li>
 * </ol>
 *
 * <p>Raw file text is scanned, comments included. That over-reports rather
 * than under-reports: naming another feature's internal type is worth fixing
 * even in prose, and a comment-stripper would be a second thing that can be
 * wrong in the direction of silence. Three javadoc references were rewritten
 * during the migration for exactly this reason rather than weakening the scan.
 *
 * <p>This guard is blind to a leak that carries no name — {@code var info =
 * systemControl.something()} imports nothing. That hole is closed by
 * {@link EntityExposureTest}, which reads signatures out of the bytecode.
 * It is also blind to a collaborator inside {@code .control} simply being
 * declared {@code public}, since the {@code *Control} exception below is a
 * NAME test; {@link ControlPackageVisibilityTest} is the guard for that.
 */
class FeatureInternalsImportTest {

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");

    /**
     * Shared non-vacuity floor for every scan in this class: a scan that walks
     * the wrong directory finds zero files and passes silently. On 2026-08-25
     * the module held 116 java sources.
     */
    private static final int SOURCE_FILE_FLOOR = 110;

    /**
     * The named exemption from the {@code .boundary} scan, and the only one.
     * {@code BoundaryAnnotationAuditTest} pins all ten Boundaries as literal
     * fully-qualified strings so that a rename or a package move fails loudly
     * instead of silently shrinking what it audits — and this scan reads raw
     * file text, so those pins look exactly like ten cross-feature references.
     *
     * <p>Exempting the file by name is deliberate, over the alternative of
     * assembling the pins from parts ({@code "de.gxp.asset" + BOUNDARY + …}).
     * String-splitting would hide the reference from a text scan while keeping
     * it in the code — i.e. it would demonstrate, in the test suite itself, the
     * one-line trick that defeats this guard. An exemption is instead visible,
     * reviewable, and checked below for staleness.
     *
     * <p>The exemption is narrow twice over: it applies to {@code .boundary}
     * only — the listed file is still scanned for {@code .entity} and
     * {@code .control} references like every other file — and it is keyed on
     * the PATH, so a same-named file added in another package is not covered
     * by it.
     */
    private static final List<String> FILES_THAT_MAY_NAME_BOUNDARIES =
            List.of("src/test/java/de/gxp/security/BoundaryAnnotationAuditTest.java");

    private static final Pattern WILDCARD_IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?[\\w.$]+\\.\\*\\s*;");

    @Test
    void noFileOutsideAFeatureReferencesThatFeaturesInternals() throws IOException {
        List<String> features = FeaturePackages.names();
        List<String> violations = new ArrayList<>();
        List<String> exemptFilesSeen = new ArrayList<>();
        int scanned = 0;

        for (Path file : javaSources()) {
            scanned++;
            String path = file.toString().replace(java.io.File.separatorChar, '/');
            String exemptedPath = FILES_THAT_MAY_NAME_BOUNDARIES.stream()
                    .filter(suffix -> path.endsWith("/" + suffix))
                    .findFirst().orElse(null);
            boolean mayNameBoundaries = exemptedPath != null;
            if (mayNameBoundaries) {
                exemptFilesSeen.add(exemptedPath);
            }
            String filePackage = declaredPackage(file);
            for (String feature : features) {
                String featureRoot = "de.gxp." + feature;
                if (filePackage.equals(featureRoot) || filePackage.startsWith(featureRoot + ".")) {
                    continue;
                }
                violations.addAll(referencesTo(file, featureRoot + ".entity.", false));
                violations.addAll(referencesTo(file, featureRoot + ".control.", true));
                if (!mayNameBoundaries) {
                    violations.addAll(referencesTo(file, featureRoot + ".boundary.", false));
                }
            }
        }

        assertTrue(scanned >= SOURCE_FILE_FLOOR,
                "the scan found only " + scanned + " java sources — it is looking in the wrong place;"
                        + " on 2026-08-25 the module held 116");
        for (String exempt : FILES_THAT_MAY_NAME_BOUNDARIES) {
            assertTrue(exemptFilesSeen.contains(exempt),
                    exempt + " is exempted from the .boundary scan but was not found among the "
                            + scanned + " scanned sources — a stale exemption is a hole nobody is"
                            + " watching; delete it or fix the path");
        }
        assertTrue(violations.isEmpty(),
                "a feature's internals were referenced from outside the feature. A feature's own *Control"
                        + " is the ONLY type another feature may name (spec §4); nothing in .entity and"
                        + " nothing in .boundary ever may — inject that Control instead:\n"
                        + String.join("\n", violations));
    }

    /**
     * A single wildcard import would let anything above import a whole
     * internal package without naming one type, so the scan's blind spot is
     * closed by forbidding the construct outright.
     */
    @Test
    void noSourceFileUsesAWildcardImport() throws IOException {
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        for (Path file : javaSources()) {
            scanned++;
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (WILDCARD_IMPORT.matcher(lines.get(i)).find()) {
                    violations.add(file + ":" + (i + 1) + "  " + lines.get(i).strip());
                }
            }
        }
        assertTrue(scanned >= SOURCE_FILE_FLOOR,
                "the scan found only " + scanned + " java sources — it is looking in the wrong place;"
                        + " on 2026-08-25 the module held 116");
        assertTrue(violations.isEmpty(),
                "wildcard imports are forbidden — they defeat the feature-internals scan by importing a"
                        + " package without naming a type:\n" + String.join("\n", violations));
    }

    /**
     * Non-vacuity: the guards above only mean something if the layout they
     * assume is really on disk. A feature whose classes still sat flat in
     * {@code de.gxp.<feature>} would sail through every scan in this class by
     * matching no {@code .entity}/{@code .control} prefix at all.
     */
    @Test
    void everyFeatureActuallyCarriesTheSubPackageLayout() throws IOException {
        Path featureRoot = FeaturePackages.moduleRoot().resolve("src/main/java/de/gxp");
        List<String> featuresWithoutEntities = FeaturePackages.featuresWithoutEntities();
        List<String> featuresWithoutBoundaries = FeaturePackages.featuresWithoutBoundaries();
        List<String> violations = new ArrayList<>();

        for (String feature : FeaturePackages.names()) {
            Path directory = featureRoot.resolve(feature);
            try (Stream<Path> entries = Files.list(directory)) {
                for (Path entry : entries.toList()) {
                    String name = entry.getFileName().toString();
                    if (Files.isDirectory(entry)) {
                        if (!FeaturePackages.LAYOUT.contains(name)) {
                            violations.add("de.gxp." + feature + "." + name
                                    + " is not one of boundary/control/entity");
                        }
                    } else if (name.endsWith(".java")) {
                        violations.add("de.gxp." + feature + "." + name.replace(".java", "")
                                + " sits at the feature root instead of in boundary/control/entity");
                    }
                }
            }
            if (!Files.isDirectory(directory.resolve("control"))) {
                violations.add("de.gxp." + feature + " has no .control package — every feature has a Control");
            }
            if (!featuresWithoutEntities.contains(feature)
                    && !Files.isDirectory(directory.resolve("entity"))) {
                violations.add("de.gxp." + feature + " has no .entity package, and it is not one of the"
                        + " table-less features named in FeaturePackages");
            }
            if (!featuresWithoutBoundaries.contains(feature)
                    && !Files.isDirectory(directory.resolve("boundary"))) {
                violations.add("de.gxp." + feature + " has no .boundary package, and it is not one of the"
                        + " HTTP-less features named in FeaturePackages");
            }
        }

        assertTrue(violations.isEmpty(),
                "the boundary/control/entity layout is not what is on disk, so the other guards in this"
                        + " class may be scanning for nothing (spec §4, constitution v1.6.0):\n"
                        + String.join("\n", violations));
    }

    /**
     * Every fully-qualified reference to {@code packagePrefix} in the file,
     * minus the feature's own Control where that is the allowed crossing
     * ({@link FeaturePackages#isFeatureControl}). Nothing in {@code .entity}
     * may cross, ever.
     */
    private static List<String> referencesTo(Path file, String packagePrefix, boolean allowFeatureControl)
            throws IOException {
        List<String> found = new ArrayList<>();
        Pattern reference = Pattern.compile(Pattern.quote(packagePrefix) + "(\\w+)");
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = reference.matcher(lines.get(i));
            while (matcher.find()) {
                if (allowFeatureControl && FeaturePackages.isFeatureControl(matcher.group(1))) {
                    continue;
                }
                found.add(file + ":" + (i + 1) + "  references " + packagePrefix + matcher.group(1));
            }
        }
        return found;
    }

    private static String declaredPackage(Path file) throws IOException {
        for (String line : Files.readAllLines(file)) {
            Matcher matcher = PACKAGE_DECLARATION.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    /** Both source roots of this module; generated sources live under target/ and are not scanned. */
    private static List<Path> javaSources() throws IOException {
        Path module = FeaturePackages.moduleRoot();
        List<Path> files = new ArrayList<>();
        for (String root : List.of("src/main/java", "src/test/java")) {
            Path sourceRoot = module.resolve(root);
            assertTrue(Files.isDirectory(sourceRoot), sourceRoot + " is missing");
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".java")).forEach(files::add);
            }
        }
        assertFalse(files.isEmpty(), "no java sources found under " + module);
        return files;
    }
}
