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

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

/**
 * REQ-332, as amended 2026-08-24 (constitution v1.6.0 — authorization moved
 * from the Control to the Boundary). Makes the {@code backend/CLAUDE.md}
 * convention mechanical: EVERY public method of every {@code *Resource} class
 * in {@code de.gxp.*} resolves to exactly ONE of {@code @PermitAll} /
 * {@code @RolesAllowed(...)} — carried by the method itself, or inherited from
 * a single class-level annotation.
 *
 * <p>This replaces the {@code *Control} sweep that guarded the previous model.
 * That sweep could not be kept: {@code quarkus.security.deny-unannotated-members}
 * is class-scoped, so an annotation-free Control is simply outside its reach —
 * which is exactly why the move landed together with
 * {@code quarkus.security.jaxrs.deny-unannotated-endpoints=true}. The runtime
 * now fails closed on an unannotated endpoint by itself
 * ({@link BoundarySecuritySemanticsTest} claim 1); this audit is the earlier,
 * louder signal that names the offending method instead of answering 401 in
 * production.
 *
 * <p>Scope note: the sweep walks the SHIPPED classes directory, so the
 * test-only Boundaries in this package ({@link FailClosedResource},
 * {@link BoundaryAnnotatedResource}) are outside it by construction — the
 * deliberately unannotated fixture needs no exception here.
 *
 * <p>Known limits of the sweep, stated so nobody reads it as total: it matches
 * classes by the FILE NAME {@code *Resource.class} and inspects
 * {@code getDeclaredMethods()}, so an endpoint class named otherwise, or one
 * inheriting public endpoints from a superclass, is outside it. Neither exists
 * today (all ten Boundaries are pinned by name below); both remain covered by
 * {@code deny-unannotated-endpoints} at runtime and by
 * {@link ContractSecuredOperationsDeniedTest} for anything in the contract.
 */
class BoundaryAnnotationAuditTest {

    /**
     * Every real Boundary, pinned by name. The sweep is name-based, so a
     * rename or a package move must fail loudly rather than silently reduce
     * what is being audited (the trick the {@code *Control} sweep used for
     * DeviceControl/GuestControl, aimed at the security-critical Boundaries).
     */
    private static final List<String> REQUIRED_BOUNDARIES = List.of(
            "de.gxp.asset.boundary.AssetResource",
            "de.gxp.auth.boundary.AuthResource",
            "de.gxp.device.boundary.DeviceAdminResource",
            "de.gxp.device.boundary.PairingResource",
            "de.gxp.guest.boundary.GuestResource",
            "de.gxp.menu.boundary.MenuResource",
            "de.gxp.property.boundary.PropertyResource",
            "de.gxp.room.boundary.RoomResource",
            "de.gxp.stay.boundary.StayResource",
            "de.gxp.system.boundary.SystemResource");

    @Test
    void everyPublicBoundaryMethodResolvesToExactlyOneSecurityAnnotation() throws Exception {
        List<String> boundaryClassNames = shippedClassNames("Resource.class");
        assertFalse(boundaryClassNames.isEmpty(), "the deployment archive must contain Boundaries");
        for (String required : REQUIRED_BOUNDARIES) {
            assertTrue(boundaryClassNames.contains(required), required + " missing from the audit sweep");
        }

        List<String> violations = new ArrayList<>();
        for (String className : boundaryClassNames) {
            Class<?> boundaryClass = Class.forName(className);
            int onClass = securityAnnotationCount(boundaryClass::isAnnotationPresent);
            for (Method method : boundaryClass.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                int onMethod = securityAnnotationCount(method::isAnnotationPresent);
                int effective = onMethod + onClass;
                if (effective != 1) {
                    violations.add(className + "#" + method.getName() + " resolves to " + effective
                            + " security annotations (" + onMethod + " on the method, " + onClass
                            + " on the class; exactly 1 required)");
                }
            }
        }
        assertTrue(violations.isEmpty(), "unaudited Boundary methods:\n" + String.join("\n", violations));
    }

    /**
     * No shipped Boundary may carry a CLASS-LEVEL {@code @PermitAll}. This is
     * not style — it is the hole a review mutation walked through on
     * 2026-08-24: an unannotated {@code @GET} added to {@code PairingResource}
     * answered {@code 200} anonymously, and all three guards stayed green. The
     * class-level annotation was INHERITED by the new method, so this audit
     * resolved it to exactly 1; the contract test could not derive it (it was
     * not in {@code api/openapi.yaml}); and
     * {@code deny-unannotated-endpoints} saw an annotated endpoint. A
     * class-level {@code @PermitAll} silently anonymizes every method anyone
     * adds later, on the surface an attacker on the LAN reaches first.
     *
     * <p>Class-level {@code @RolesAllowed} is deliberately still allowed: a
     * method that accidentally inherits it fails CLOSED — the wrong role is a
     * 403 someone reports, not a silent anonymous opening — so the
     * readability win is kept everywhere it is not dangerous. Do not
     * "simplify" {@code PairingResource} back to a class-level
     * {@code @PermitAll}; put the annotation on each method instead.
     */
    @Test
    void noBoundaryCarriesAClassLevelPermitAll() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String className : shippedClassNames("Resource.class")) {
            if (Class.forName(className).isAnnotationPresent(PermitAll.class)) {
                violations.add(className);
            }
        }
        assertTrue(violations.isEmpty(),
                "class-level @PermitAll is forbidden on a Boundary — any method added later inherits it and "
                        + "answers anonymously while every guard stays green (the 2026-08-24 mutation). Move it "
                        + "onto each method. Class-level @RolesAllowed stays allowed; it fails closed:\n"
                        + String.join("\n", violations));
    }

    /**
     * The generated {@code de.gxp.api.*Api} interfaces must carry no security
     * annotation. Quarkus resolves the implementing method first and the
     * interface only as a FALLBACK ({@link BoundarySecuritySemanticsTest}
     * claim 2), so an interface annotation would take effect precisely where a
     * Boundary forgot one — turning the fail-closed default into a silent
     * fallback. The generator emits none today ({@code jaxrs-spec},
     * {@code useSwaggerAnnotations=false}); this is the tripwire for a
     * generator upgrade that starts translating the contract's
     * {@code bearerAuth} into Java.
     */
    @Test
    void noGeneratedApiInterfaceCarriesASecurityAnnotation() throws Exception {
        List<String> apiInterfaces = shippedClassNames("Api.class").stream()
                .filter(name -> name.startsWith("de.gxp.api."))
                .toList();
        assertFalse(apiInterfaces.isEmpty(), "the generated contract interfaces must be on the classpath");
        assertTrue(apiInterfaces.contains("de.gxp.api.GuestApi"), "GuestApi missing from the guard");

        List<String> violations = new ArrayList<>();
        for (String className : apiInterfaces) {
            Class<?> apiInterface = Class.forName(className);
            if (securityAnnotationCount(apiInterface::isAnnotationPresent) > 0) {
                violations.add(className + " (class level)");
            }
            for (Method method : apiInterface.getDeclaredMethods()) {
                if (securityAnnotationCount(method::isAnnotationPresent) > 0) {
                    violations.add(className + "#" + method.getName());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "generated interfaces must carry no security annotation — the Boundary is the only place:\n"
                        + String.join("\n", violations));
    }

    private interface AnnotationLookup {
        boolean has(Class<? extends java.lang.annotation.Annotation> annotation);
    }

    private static int securityAnnotationCount(AnnotationLookup element) {
        return (element.has(PermitAll.class) ? 1 : 0)
                + (element.has(RolesAllowed.class) ? 1 : 0)
                + (element.has(DenyAll.class) ? 1 : 0);
    }

    /** Class names under the shipped classes directory (never src/test). */
    private static List<String> shippedClassNames(String fileSuffix) throws Exception {
        Path classesDir = Path.of(de.gxp.system.control.SystemControl.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        try (Stream<Path> paths = Files.walk(classesDir)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(fileSuffix))
                    .map(path -> classesDir.relativize(path).toString()
                            .replace(java.io.File.separatorChar, '.')
                            .replaceAll("\\.class$", ""))
                    .filter(name -> name.startsWith("de.gxp."))
                    .toList();
        }
    }
}
