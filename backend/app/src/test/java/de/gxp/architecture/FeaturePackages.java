package de.gxp.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The feature packages of this module, DERIVED FROM DISK and shared by the
 * three architecture guards {@link FeatureInternalsImportTest},
 * {@link EntityExposureTest} and {@link ControlPackageVisibilityTest}.
 *
 * <p>This replaces the {@code MigratedFeatures} scaffolding the migration ran
 * on (constitution v1.6.0, completed 2026-08-25). While the migration landed
 * one feature at a time, the guards needed a hand-maintained list of which
 * packages already carried the layout. They no longer do, and a hand-
 * maintained list is exactly the thing that would silently stop covering a
 * feature added later — so the list is gone and the set is read off
 * {@code src/main/java/de/gxp} instead: <b>every</b> immediate sub-directory
 * except the four non-feature ones named in {@code backend/CLAUDE.md}.
 *
 * <p>A new feature package is therefore guarded from the day its directory
 * exists, with nothing to remember to update.
 */
final class FeaturePackages {

    /**
     * Not features, by name and not by accident ({@code backend/CLAUDE.md}):
     * {@code api} is generated + shared HTTP plumbing, {@code web} is the SPA
     * route handler, {@code devseed} is a dev-profile-only seeder, and
     * {@code testsupport} exists only under {@code src/test/java}.
     */
    static final List<String> NON_FEATURE_PACKAGES = List.of("api", "web", "devseed", "testsupport");

    /**
     * Feature packages that legitimately have NO {@code .entity} package.
     * Read through {@link #featuresWithoutEntities()}, never directly — the
     * accessor is what checks the claim against disk.
     *
     * <p>{@code de.gxp.text} is stateless by design — template validation and
     * resolution, no Boundary and no JPA type; other Controls inject it.
     * {@code de.gxp.guest} owns no table either: it is the read model that
     * composes six other features' Controls, and spec §4 makes that its whole
     * job.
     *
     * <p>Named here rather than inferred, deliberately: if the check simply
     * skipped any feature without an {@code .entity} directory, a feature
     * that had not been split yet would pass silently, which is the one thing
     * these guards exist to prevent.
     */
    private static final List<String> FEATURES_WITHOUT_ENTITIES = List.of("text", "guest");

    /**
     * Feature packages that legitimately have NO {@code .boundary} package.
     * Read through {@link #featuresWithoutBoundaries()}. {@code de.gxp.text}
     * is the only one: it exposes no HTTP surface at all, so it has no
     * {@code *Resource} to hold. Every other feature answers the contract.
     */
    private static final List<String> FEATURES_WITHOUT_BOUNDARIES = List.of("text");

    /** The three sub-packages a feature may hold — nothing else (spec §4). */
    static final List<String> LAYOUT = List.of("boundary", "control", "entity");

    private FeaturePackages() {
    }

    /** Every feature package name, read off {@code src/main/java/de/gxp}. */
    static List<String> names() throws IOException {
        Path featureRoot = moduleRoot().resolve("src/main/java/de/gxp");
        List<String> features = new ArrayList<>();
        try (Stream<Path> entries = Files.list(featureRoot)) {
            entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !NON_FEATURE_PACKAGES.contains(name))
                    .sorted()
                    .forEach(features::add);
        }
        assertTrue(features.size() >= 10,
                "only " + features.size() + " feature packages were found under " + featureRoot
                        + " — the scan is looking in the wrong place; on 2026-08-25 there were 10"
                        + " (asset, auth, device, guest, menu, property, room, stay, system, text)");
        return features;
    }

    /**
     * {@link #FEATURES_WITHOUT_ENTITIES}, re-checked against disk on every
     * call. The list is not a skip of ONE check: while a feature is on it,
     * guard B never treats {@code de.gxp.<feature>.entity} as an entity
     * package, so an entity appearing there later would stop being recognised
     * as an entity — a public Control method could return it and nothing would
     * fail. Reported 2026-08-25 (review of Block C, 🟡 3). So the exemption
     * has to prove itself every run instead of being trusted.
     */
    static List<String> featuresWithoutEntities() throws IOException {
        return checkedAgainstDisk(FEATURES_WITHOUT_ENTITIES, "entity");
    }

    /** {@link #FEATURES_WITHOUT_BOUNDARIES}, re-checked against disk the same way. */
    static List<String> featuresWithoutBoundaries() throws IOException {
        return checkedAgainstDisk(FEATURES_WITHOUT_BOUNDARIES, "boundary");
    }

    /** Fails if an exempted feature vanished, or grew the sub-package it is exempted from. */
    private static List<String> checkedAgainstDisk(List<String> exempted, String subPackage)
            throws IOException {
        Path featureRoot = moduleRoot().resolve("src/main/java/de/gxp");
        List<String> features = names();
        for (String feature : exempted) {
            assertTrue(features.contains(feature),
                    "FeaturePackages names " + feature + " as a feature without a ." + subPackage
                            + " package, but there is no feature package de.gxp." + feature + " any more"
                            + " — the exemption is stale and exempts nothing; delete it");
            assertFalse(Files.isDirectory(featureRoot.resolve(feature).resolve(subPackage)),
                    "de.gxp." + feature + "." + subPackage + " now EXISTS, but " + feature + " is still"
                            + " listed in FeaturePackages as a feature without a ." + subPackage
                            + " package. While it is listed, the guards do not treat that package as a "
                            + subPackage + " package at all — its types would be silently unguarded."
                            + " Remove " + feature + " from the list");
        }
        return exempted;
    }

    /**
     * A type in {@code de.gxp.<feature>.control} may be referenced from
     * another feature only if it is the feature's Control. The name test is
     * the {@code *Control} SUFFIX rather than the exact
     * {@code <Feature>Control}, because that form is not universal:
     * {@code de.gxp.text}'s Control is {@code TemplateControl}, and
     * {@code backend/CLAUDE.md} words the rule as "the public {@code *Control}"
     * for that reason.
     *
     * <p>Residual risk, stated rather than hidden: a collaborator named
     * {@code SomethingControl} and made public would be admitted by this
     * exception. {@link ControlPackageVisibilityTest} shrinks that to the
     * misnamed case by failing on ANY other public type in {@code .control};
     * a collaborator that is both widened and named {@code *Control} remains
     * a review catch.
     */
    static boolean isFeatureControl(String simpleName) {
        return simpleName.endsWith("Control");
    }

    /**
     * A type in {@code de.gxp.<feature>.boundary} may be public only if it is
     * a JAX-RS {@code *Resource}: {@code backend/CLAUDE.md} says the boundary
     * package holds "the {@code *Resource} JAX-RS adapters, and nothing else".
     *
     * <p>Unlike {@link #isFeatureControl}, this is NOT an exception to the
     * cross-feature import rule — a {@code *Resource} is public because the
     * framework instantiates it, not because anyone may name it. No other
     * feature may reference one, and {@link FeatureInternalsImportTest} scans
     * {@code .boundary} with no exception at all.
     */
    static boolean isBoundaryResource(String simpleName) {
        return simpleName.endsWith("Resource");
    }

    /** The Maven module directory, found from the working directory upwards. */
    static Path moduleRoot() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            if (Files.isDirectory(directory.resolve("src/main/java/de/gxp"))) {
                return directory;
            }
        }
        throw new IllegalStateException("src/main/java/de/gxp not found above " + Path.of("").toAbsolutePath());
    }
}
