package de.gxp.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The {@code de.gxp.*} classes this module actually SHIPS, shared by the two
 * bytecode-reading architecture guards {@link EntityExposureTest} and
 * {@link ControlPackageVisibilityTest}.
 *
 * <p>Found the same way {@code de.gxp.security.BoundaryAnnotationAuditTest}
 * finds them — from the code source of a class that is itself shipped, so the
 * directory is never hard-coded and never drifts from the build layout.
 *
 * <p><b>Recorded limit: {@code target/test-classes} is deliberately NOT
 * scanned.</b> Test classes are not shipped, so nothing they expose can leak
 * out of the running instance; scanning them would only add noise from
 * fixtures that exist to poke at package-private internals on purpose. This is
 * a decision, not an oversight — a violation introduced in test code is
 * invisible to these two guards and is caught instead by
 * {@link FeatureInternalsImportTest}, which DOES scan {@code src/test/java}.
 */
final class ShippedClasses {

    private ShippedClasses() {
    }

    static List<String> names() throws Exception {
        Path classesDir = Path.of(de.gxp.system.control.SystemControl.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        try (Stream<Path> paths = Files.walk(classesDir)) {
            List<String> names = paths
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .map(path -> classesDir.relativize(path).toString()
                            .replace(File.separatorChar, '.')
                            .replaceAll("\\.class$", ""))
                    .filter(name -> name.startsWith("de.gxp."))
                    .toList();
            assertFalse(names.isEmpty(), "no shipped classes found under " + classesDir);
            return names;
        }
    }

    static String packageOf(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? "" : className.substring(0, lastDot);
    }
}
