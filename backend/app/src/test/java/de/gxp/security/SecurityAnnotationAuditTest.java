package de.gxp.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

/**
 * T-23 (REQ-332): makes the {@code backend/CLAUDE.md} convention mechanical —
 * EVERY public method of every {@code *Control} class in {@code de.gxp.*}
 * carries exactly ONE of {@code @PermitAll} / {@code @RolesAllowed}. A new
 * Control method without an explicit annotation fails this audit, not just
 * the runtime deny.
 */
class SecurityAnnotationAuditTest {

    @Test
    void everyPublicControlMethodCarriesExactlyOneSecurityAnnotation() throws Exception {
        Path classesDir = Path.of(de.gxp.system.SystemControl.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> controlClassNames;
        try (Stream<Path> paths = Files.walk(classesDir)) {
            controlClassNames = paths
                    .filter(path -> path.getFileName().toString().endsWith("Control.class"))
                    .map(path -> classesDir.relativize(path).toString()
                            .replace(java.io.File.separatorChar, '.')
                            .replaceAll("\\.class$", ""))
                    .filter(name -> name.startsWith("de.gxp."))
                    .toList();
        }
        assertFalse(controlClassNames.isEmpty(), "the deployment archive must contain Controls");

        List<String> violations = new ArrayList<>();
        for (String className : controlClassNames) {
            Class<?> controlClass = Class.forName(className);
            for (Method method : controlClass.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                    continue;
                }
                int annotations = (method.isAnnotationPresent(PermitAll.class) ? 1 : 0)
                        + (method.isAnnotationPresent(RolesAllowed.class) ? 1 : 0);
                if (annotations != 1) {
                    violations.add(className + "#" + method.getName() + " carries " + annotations
                            + " security annotations (exactly 1 required)");
                }
            }
        }
        assertTrue(violations.isEmpty(), "unaudited Control methods:\n" + String.join("\n", violations));
    }
}
