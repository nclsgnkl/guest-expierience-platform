package de.gxp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * Architecture guard C of three (constitution v1.6.0, 2026-08-24) — the
 * VISIBILITY side of the feature boundary. Part of the definition of done: it
 * must stay green.
 *
 * <p>Guards A and B police who may NAME a feature's internals and what may
 * appear in a public signature. Neither of them stops a collaborator inside
 * {@code de.gxp.<feature>.control} from simply being declared {@code public}
 * in the first place. That matters because the whole justification for the
 * Option A layout — Repository, hasher, pairing-code helper, SPI adapters,
 * the guest ETag filter and friends all live in {@code .control} — is that
 * those types <b>stay package-private</b> there (spec §4). Widening one costs
 * nothing at compile time and is invisible in review once a package holds a
 * dozen files.
 *
 * <p>So: <b>in a {@code .control} package, only a {@code *Control} may be
 * public — and in a {@code .boundary} package, only a {@code *Resource}.</b>
 *
 * <p>The {@code .boundary} half was added 2026-08-25, after a review put a
 * public {@code StayView extends Stay} in {@code de.gxp.stay.boundary} and
 * read an entity field off it from another feature's Control with all three
 * guards green. {@code backend/CLAUDE.md} says the boundary package holds
 * "the {@code *Resource} JAX-RS adapters, <b>and nothing else</b>"; that
 * sentence now has an enforcer. A {@code *Resource} is public only because
 * the framework instantiates it — unlike the {@code *Control} it is NOT an
 * allowed cross-feature reference, and {@link FeatureInternalsImportTest}
 * scans {@code .boundary} with no exception at all.
 *
 * <p>The name test is the {@code *Control} SUFFIX rather than the exact
 * {@code <Feature>Control}, for the same reason
 * {@link FeatureInternalsImportTest} uses the suffix:
 * {@code de.gxp.text}'s Control is {@code TemplateControl}, and
 * {@code backend/CLAUDE.md} words the rule as "the public {@code *Control}".
 *
 * <p><b>Residual risk, stated rather than hidden:</b> a collaborator that is
 * both made public AND named {@code SomethingControl} still passes this guard
 * and guard A. This test shrinks the loophole from "any widened collaborator"
 * to "a collaborator deliberately misnamed", which is a lie a reviewer can
 * see, but it does not eliminate it. Closing it fully would need a rule like
 * "exactly one public type per {@code .control} package" — that is a design
 * constraint nobody has approved, so it is written down here instead of
 * invented.
 *
 * <p>Only TOP-LEVEL types are judged. A nested type's reachability is bounded
 * by its enclosing type, so a public record inside a package-private
 * collaborator is not reachable anyway, and a public record inside the
 * Control — {@code TemplateControl.TemplateContext},
 * {@code DeviceControl.DeviceIdentity} — is deliberately part of that
 * Control's cross-feature API.
 *
 * <p>Scanned: the shipped classes directory only — see {@link ShippedClasses}
 * for why {@code target/test-classes} is excluded.
 *
 * <p>Scope: the {@code .control} package of EVERY feature
 * {@link FeaturePackages} finds on disk, and the {@code .boundary} package of
 * every feature that has one ({@code de.gxp.text} serves no HTTP). The
 * migration ran on a hand-maintained list of already-migrated features; that
 * list is gone (constitution v1.6.0, completed 2026-08-25) so a feature added
 * later is covered from the day its directory exists.
 */
class ControlPackageVisibilityTest {

    @Test
    void onlyAFeatureControlIsPublicInsideAControlPackage() throws Exception {
        List<String> controlPackages = FeaturePackages.names().stream()
                .map(feature -> "de.gxp." + feature + ".control")
                .toList();
        assertTrue(controlPackages.size() >= 10,
                "only " + controlPackages.size() + " control packages were derived — the guard would then"
                        + " be judging almost nothing; on 2026-08-25 there were 10");

        assertOnlyPublic(controlPackages, "*Control", FeaturePackages::isFeatureControl,
                "a collaborator inside a .control package is public. Only the feature's *Control may be"
                        + " (spec §4) — everything else in .control (repository, hasher, SPI adapter, helper)"
                        + " stays package-private, which is the entire reason it lives there");
    }

    /**
     * The {@code .boundary} half. A public non-{@code *Resource} type here is
     * the exact shape the 2026-08-25 review used to launder an entity out of
     * its feature ({@code public class StayView extends Stay}), so the rule
     * {@code backend/CLAUDE.md} already stated in prose — "the {@code *Resource}
     * JAX-RS adapters, and nothing else" — is enforced.
     */
    @Test
    void onlyAResourceIsPublicInsideABoundaryPackage() throws Exception {
        List<String> withoutBoundary = FeaturePackages.featuresWithoutBoundaries();
        List<String> boundaryPackages = FeaturePackages.names().stream()
                .filter(feature -> !withoutBoundary.contains(feature))
                .map(feature -> "de.gxp." + feature + ".boundary")
                .toList();
        assertTrue(boundaryPackages.size() >= 9,
                "only " + boundaryPackages.size() + " boundary packages were derived — the guard would then"
                        + " be judging almost nothing; on 2026-08-25 there were 9");

        assertOnlyPublic(boundaryPackages, "*Resource", FeaturePackages::isBoundaryResource,
                "a type inside a .boundary package is public and is not a *Resource. The boundary package"
                        + " holds the JAX-RS adapters and nothing else (backend/CLAUDE.md): a public type"
                        + " here is namable from any other feature, which is how a review laundered an"
                        + " entity out of its feature on 2026-08-25");
    }

    /**
     * Shared sweep: over the shipped classes of {@code packages}, the only
     * public top-level type allowed is one whose simple name {@code allowed}
     * accepts, and every package must hold at least one — otherwise the guard
     * would be passing on an empty set.
     */
    private static void assertOnlyPublic(List<String> packages, String allowedDescription,
            Predicate<String> allowed, String failureMessage) throws Exception {
        List<String> shipped = ShippedClasses.names();
        assertTrue(shipped.size() >= 130,
                "the scan found only " + shipped.size() + " shipped classes — it is looking in the wrong"
                        + " place; on 2026-08-25 the module shipped 136");

        List<String> violations = new ArrayList<>();
        List<String> unloadable = new ArrayList<>();
        List<String> allowedSeen = new ArrayList<>();
        List<String> packagesSeen = new ArrayList<>();

        for (String className : shipped) {
            String owningPackage = ShippedClasses.packageOf(className);
            if (!packages.contains(owningPackage)) {
                continue;
            }
            packagesSeen.add(owningPackage);
            if (className.contains("$")) {
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName(className, false, ControlPackageVisibilityTest.class.getClassLoader());
            } catch (Throwable failure) {
                unloadable.add(className + " (" + failure + ")");
                continue;
            }
            if (!Modifier.isPublic(type.getModifiers())) {
                continue;
            }
            if (allowed.test(type.getSimpleName())) {
                allowedSeen.add(className);
            } else {
                violations.add(className + " is public");
            }
        }

        for (String guarded : packages) {
            assertTrue(packagesSeen.contains(guarded),
                    guarded + " holds no shipped class — either the feature is not migrated after all,"
                            + " or the package was renamed and this guard now scans for nothing");
            assertTrue(allowedSeen.stream()
                            .anyMatch(name -> ShippedClasses.packageOf(name).equals(guarded)),
                    guarded + " holds no public " + allowedDescription + " — so either it was moved out"
                            + " or this guard is passing vacuously");
        }

        assertTrue(unloadable.isEmpty(),
                "these shipped classes could not be inspected, so the guard did NOT cover them — fix the"
                        + " scan rather than accepting the gap:\n" + String.join("\n", unloadable));
        assertTrue(violations.isEmpty(), failureMessage + ":\n" + String.join("\n", violations));
    }
}
