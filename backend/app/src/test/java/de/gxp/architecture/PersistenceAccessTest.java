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
 * Architecture guard D of four (owner-approved 2026-08-25) — the PERSISTENCE
 * boundary. Part of the definition of done: it must stay green.
 *
 * <p>Guards A–C police the feature boundary: who may name a feature's
 * internals, what may appear in a public signature, and what may be public in
 * {@code .control}/{@code .boundary}. None of them says anything about
 * <b>how</b> a feature reaches its own table. Today every one does it through
 * a {@code *Repository}, and no Control or Boundary in {@code src/main/java}
 * touches JPA, Panache or JDBC at all — that was true before this guard
 * existed and is exactly why it can be added without moving a line of
 * production code. This test turns the habit into a rule.
 *
 * <p><b>Why a guard and not a convention.</b> Raw persistence in a Control is
 * the kind of violation a convention cannot catch, because it is invisible in
 * the diff that introduces it: {@code entityManager.createNativeQuery(…)}
 * inside a Control method looks like ordinary code, reads correctly, and
 * passes review on its own merits. What is wrong with it is not local — it is
 * that the feature now has two ways into its table, so the Repository's
 * invariants (the {@code last_seen_at} write throttle, the single-row
 * {@code theRow()} contract, the pessimistic lock in
 * {@code lockForStayWrite}) hold for one path and not the other. That is a
 * property of the SET of files, and the set is what a guard can see.
 *
 * <p><b>What may reach the persistence API</b> ({@link #PERSISTENCE_PREFIXES}):
 *
 * <ol>
 * <li>A class in {@code de.gxp.<feature>.entity} — {@code jakarta.persistence}
 * ONLY. The ORM mapping is what an entity IS; forbidding it there would be
 * absurd. Panache is deliberately NOT allowed to an entity: an entity that
 * extended {@code PanacheEntity} would carry query methods itself, which is
 * precisely the second way into the table this guard exists to prevent. No
 * entity does today — all 17 are plain {@code @Entity} classes and the nine
 * repositories hold the queries.</li>
 * <li>A {@code *Repository} in {@code de.gxp.<feature>.control} —
 * {@code jakarta.persistence} and Panache. Both parts are required: the NAME,
 * so that a Control cannot qualify by living in the right package, and the
 * PACKAGE, so that a {@code *Repository} parked in {@code .boundary} does not
 * qualify by being named well.</li>
 * <li>{@link #FILES_THAT_MAY_USE_PERSISTENCE_DIRECTLY} — one named file,
 * checked for staleness below.</li>
 * </ol>
 *
 * <p>Everything else in {@code src/main/java} — every Control, every
 * {@code *Resource}, {@code de.gxp.api}, {@code de.gxp.web} — may not
 * reference these packages at all.
 *
 * <p><b>Scope: {@code src/main/java} only</b>, unlike guard A. Sixteen tests
 * inject an {@code EntityManager} to arrange database state, and that is their
 * job: a test reaches past the production layering on purpose. Exempting them
 * one by one would be sixteen exemptions guarding nothing, and a blanket
 * "tests may" is more honest written down once, here, than spread across a
 * list. The cost is stated rather than hidden: a Control that laundered raw
 * SQL through a test-only helper would not be seen — but such a helper is not
 * on the main compile path, so it cannot be reached from a Control at all.
 *
 * <p>Raw file text is scanned, comments included, for the same reason guard A
 * does it: over-reporting is the safe direction, and a comment-stripper would
 * be a second thing that can fail silently. This also closes the blind spot of
 * a fully-qualified inline use ({@code new jakarta.persistence.…}) that no
 * import line would show. What it cannot see is a reference that carries no
 * name — reflection, or a string fed to a factory. That hole is left open and
 * named rather than papered over.
 */
class PersistenceAccessTest {

    /**
     * The package prefixes that mean "this file talks to the database".
     *
     * <p>{@code java.sql} and {@code javax.sql} and {@code io.agroal} are
     * included although NOTHING in the module uses them today: they are the
     * obvious way around the other three, and a prefix with zero current
     * matches costs nothing while it stays unused. Known friction, so nobody
     * has to rediscover it: {@code java.sql.Timestamp} and
     * {@code java.sql.Date} are ordinary value types and would trip this scan.
     * If one is ever legitimately wanted, narrow the prefix to
     * {@code java.sql.Connection}/{@code Statement}/{@code PreparedStatement}
     * — do NOT exempt the file, or the file stops being watched for real JDBC
     * too.
     */
    private static final List<String> PERSISTENCE_PREFIXES = List.of(
            "jakarta.persistence.",
            "io.quarkus.hibernate.orm.panache.",
            "org.hibernate.",
            "java.sql.",
            "javax.sql.",
            "io.agroal.");

    /** The subset an {@code .entity} class may use — the ORM mapping itself. */
    private static final List<String> ENTITY_PREFIXES = List.of("jakarta.persistence.");

    /** The subset a {@code *Repository} may use — the mapping plus Panache. */
    private static final List<String> REPOSITORY_PREFIXES = List.of(
            "jakarta.persistence.",
            "io.quarkus.hibernate.orm.panache.");

    /**
     * The named exemption, and the only one. {@code DevDataSeeder} writes the
     * developer fixture with native SQL against tables owned by six different
     * features, on {@code %dev} only. It has no Repository and should not get
     * one: a Repository is a feature's production access path, and this seeder
     * deliberately reaches across features in a way no production code may.
     * {@code backend/CLAUDE.md} already names {@code de.gxp.devseed} as a
     * non-feature package for the same reason.
     *
     * <p>Keyed on the PATH, not the class name, so a same-named class added
     * elsewhere is not covered — and checked below for staleness, because an
     * exemption whose file has moved is a hole nobody is watching.
     */
    private static final List<String> FILES_THAT_MAY_USE_PERSISTENCE_DIRECTLY =
            List.of("src/main/java/de/gxp/devseed/DevDataSeeder.java");

    /**
     * Non-vacuity floor: a scan pointed at the wrong directory finds no files
     * and passes silently. On 2026-08-25 {@code src/main/java} held 68 sources.
     */
    private static final int MAIN_SOURCE_FLOOR = 60;

    /**
     * Non-vacuity floors for the ALLOWED side. These matter more than the file
     * count: if a prefix in {@link #PERSISTENCE_PREFIXES} were misspelled, the
     * scan would match nothing anywhere, report no violations, and pass — while
     * guarding exactly nothing. Requiring that the legitimate users are still
     * FOUND is what makes a green run mean something. On 2026-08-25 there were
     * 17 mapped entities and 9 repositories.
     */
    private static final int MAPPED_ENTITY_FLOOR = 15;
    private static final int REPOSITORY_FLOOR = 8;

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");

    @Test
    void onlyEntitiesAndRepositoriesReachThePersistenceApi() throws IOException {
        List<String> features = FeaturePackages.names();
        List<String> violations = new ArrayList<>();
        List<String> exemptFilesSeen = new ArrayList<>();
        List<String> exemptFilesThatNeededIt = new ArrayList<>();
        int scanned = 0;
        int mappedEntities = 0;
        int repositories = 0;

        for (Path file : mainSources()) {
            scanned++;
            String path = file.toString().replace(java.io.File.separatorChar, '/');
            String filePackage = declaredPackage(file);
            String simpleName = file.getFileName().toString().replace(".java", "");

            String exemptedPath = FILES_THAT_MAY_USE_PERSISTENCE_DIRECTLY.stream()
                    .filter(suffix -> path.endsWith("/" + suffix))
                    .findFirst().orElse(null);

            List<String> allowed;
            String role;
            if (exemptedPath != null) {
                exemptFilesSeen.add(exemptedPath);
                allowed = PERSISTENCE_PREFIXES;
                role = "an exempted file";
            } else if (isInSubPackage(filePackage, features, "entity")) {
                allowed = ENTITY_PREFIXES;
                role = "an entity";
            } else if (isInSubPackage(filePackage, features, "control") && simpleName.endsWith("Repository")) {
                allowed = REPOSITORY_PREFIXES;
                role = "a repository";
            } else {
                allowed = List.of();
                role = "neither an entity nor a *Repository";
            }

            List<String> used = referencesIn(file, PERSISTENCE_PREFIXES);
            List<String> forbidden = referencesIn(file, notIn(allowed));
            if (!used.isEmpty()) {
                if (exemptedPath != null) {
                    exemptFilesThatNeededIt.add(exemptedPath);
                } else if (allowed == ENTITY_PREFIXES && forbidden.isEmpty()) {
                    mappedEntities++;
                } else if (allowed == REPOSITORY_PREFIXES && forbidden.isEmpty()) {
                    repositories++;
                }
            }
            for (String reference : forbidden) {
                violations.add(reference + "  (" + filePackage + "." + simpleName + " is " + role + ")");
            }
        }

        assertTrue(scanned >= MAIN_SOURCE_FLOOR,
                "the scan found only " + scanned + " java sources under src/main/java — it is looking in"
                        + " the wrong place; on 2026-08-25 the module held 68");
        assertTrue(mappedEntities >= MAPPED_ENTITY_FLOOR,
                "the scan recognised only " + mappedEntities + " mapped entities — on 2026-08-25 there"
                        + " were 17. Either the prefixes in PERSISTENCE_PREFIXES no longer match what the"
                        + " code imports (in which case this guard is matching nothing anywhere and is"
                        + " passing vacuously), or the entities moved out of de.gxp.<feature>.entity");
        assertTrue(repositories >= REPOSITORY_FLOOR,
                "the scan recognised only " + repositories + " repositories — on 2026-08-25 there were 9."
                        + " Same two causes as the entity floor above; check PERSISTENCE_PREFIXES first");
        for (String exempt : FILES_THAT_MAY_USE_PERSISTENCE_DIRECTLY) {
            assertTrue(exemptFilesSeen.contains(exempt),
                    exempt + " is exempted from this guard but was not found among the " + scanned
                            + " scanned sources — a stale exemption is a hole nobody is watching;"
                            + " delete it or fix the path");
            assertTrue(exemptFilesThatNeededIt.contains(exempt),
                    exempt + " is exempted from this guard but no longer references any persistence API"
                            + " — it does not need the exemption any more; delete it");
        }
        assertTrue(violations.isEmpty(),
                "the database is reached from outside a *Repository. A feature talks to its own table"
                        + " through its Repository and nowhere else, so that the Repository's invariants"
                        + " hold for every path into it (owner decision 2026-08-25) — move the query into"
                        + " the feature's *Repository and call it from the Control:\n"
                        + String.join("\n", violations));
    }

    /**
     * Non-vacuity of a different kind: the guard above is a NAME test, so it
     * would keep passing if a feature quietly stopped having a Repository and
     * moved the queries somewhere the scan does not flag. Every feature that
     * owns a table must own the class that reads it.
     */
    @Test
    void everyFeatureWithAnEntityPackageHasARepository() throws IOException {
        Path featureRoot = FeaturePackages.moduleRoot().resolve("src/main/java/de/gxp");
        List<String> withoutEntities = FeaturePackages.featuresWithoutEntities();
        List<String> violations = new ArrayList<>();

        for (String feature : FeaturePackages.names()) {
            if (withoutEntities.contains(feature)) {
                continue;
            }
            Path control = featureRoot.resolve(feature).resolve("control");
            boolean hasRepository;
            try (Stream<Path> entries = Files.list(control)) {
                hasRepository = entries.anyMatch(path -> path.getFileName().toString().endsWith("Repository.java"));
            }
            if (!hasRepository) {
                violations.add("de.gxp." + feature + " owns an .entity package but de.gxp." + feature
                        + ".control holds no *Repository");
            }
        }

        assertTrue(violations.isEmpty(),
                "a feature owns a table but no Repository — whatever reads that table is then either"
                        + " outside the feature or inside a Control, and PersistenceAccessTest's name"
                        + " test cannot see it:\n" + String.join("\n", violations));
    }

    /** True if {@code filePackage} is {@code de.gxp.<feature>.<subPackage>} for any known feature. */
    private static boolean isInSubPackage(String filePackage, List<String> features, String subPackage) {
        return features.stream().anyMatch(feature -> filePackage.equals("de.gxp." + feature + "." + subPackage));
    }

    /** The scanned prefixes minus the ones this file is allowed to use. */
    private static List<String> notIn(List<String> allowed) {
        return PERSISTENCE_PREFIXES.stream().filter(prefix -> !allowed.contains(prefix)).toList();
    }

    /** Every reference to any of {@code prefixes} in the file, as {@code path:line  reference}. */
    private static List<String> referencesIn(Path file, List<String> prefixes) throws IOException {
        List<String> found = new ArrayList<>();
        if (prefixes.isEmpty()) {
            return found;
        }
        List<String> lines = Files.readAllLines(file);
        for (String prefix : prefixes) {
            Pattern reference = Pattern.compile(Pattern.quote(prefix) + "(\\w+)");
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = reference.matcher(lines.get(i));
                while (matcher.find()) {
                    found.add(file + ":" + (i + 1) + "  references " + prefix + matcher.group(1));
                }
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

    /** Production sources only — see the class javadoc on why tests are out of scope. */
    private static List<Path> mainSources() throws IOException {
        Path sourceRoot = FeaturePackages.moduleRoot().resolve("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), sourceRoot + " is missing");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".java")).forEach(files::add);
        }
        assertFalse(files.isEmpty(), "no java sources found under " + sourceRoot);
        return files;
    }
}
