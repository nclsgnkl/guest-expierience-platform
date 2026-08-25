package de.gxp.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Architecture guard B of three (constitution v1.6.0, 2026-08-24) — the
 * SIGNATURE side of the feature boundary. Part of the definition of done: it
 * must stay green.
 *
 * <p>{@link FeatureInternalsImportTest} scans source text, so it can only see
 * a leak that is written down. This one closes its blind spot: a public
 * Control method declared to RETURN an entity lets any caller hold that entity
 * with no import to find —
 *
 * <pre>var row = systemControl.something();   // no name, no import, no scan hit</pre>
 *
 * <p>So the rule is enforced where it cannot be hidden, in the compiled
 * signature: <b>no public field, method or constructor of any class outside a
 * {@code de.gxp.<feature>.entity} package may mention an entity type</b> —
 * not as a return type, a parameter, a generic type argument or an array
 * component. This is what actually upholds spec §4's "entities never leave
 * their feature"; the import scan is the earlier, more readable signal.
 *
 * <p>Inheritance counts too, for public types: a public class that
 * {@code extends} an entity, or implements an interface parameterised with one
 * ({@code implements PanacheRepository<Room>} hands out {@code findById} and
 * {@code listAll} without declaring a single member), exposes that entity just
 * as surely as a declared return type. Added 2026-08-25 — a review subclassed
 * an entity in another package to launder its now-public fields, and a scan of
 * DECLARED members alone saw nothing, because a subclass declares none.
 *
 * <p>Classes INSIDE {@code .entity} are exempt by construction — the Mapper's
 * whole job is to take an entity and hand back a generated API DTO, and it
 * lives beside the entity for exactly that reason. Non-public members are out
 * of scope: a package-private repository method returning its own entity is
 * the intended design — every feature's repository does exactly that.
 *
 * <p>Scanned: the shipped classes directory only —
 * {@code target/test-classes} is deliberately excluded, a recorded decision
 * rather than an oversight; see {@link ShippedClasses}.
 *
 * <p>Scope: EVERY feature package {@link FeaturePackages} finds on disk, less
 * the two that own no table. The migration ran on a hand-maintained list of
 * already-migrated features; that list is gone (constitution v1.6.0,
 * completed 2026-08-25) so a feature added later is covered from the day its
 * directory exists.
 */
class EntityExposureTest {

    @Test
    void noPublicSignatureOutsideAnEntityPackageMentionsAnEntity() throws Exception {
        List<String> withoutEntities = FeaturePackages.featuresWithoutEntities();
        List<String> entityPackages = FeaturePackages.names().stream()
                .filter(feature -> !withoutEntities.contains(feature))
                .map(feature -> "de.gxp." + feature + ".entity")
                .toList();

        List<String> shipped = ShippedClasses.names();
        assertTrue(shipped.size() >= 130,
                "the scan found only " + shipped.size() + " shipped classes — it is looking in the wrong"
                        + " place; on 2026-08-25 the module shipped 136");
        assertTrue(entityPackages.size() >= 8,
                "only " + entityPackages.size() + " entity packages were derived — the guard would then be"
                        + " looking for almost no entity types; on 2026-08-25 there were 8");
        for (String entityPackage : entityPackages) {
            assertTrue(shipped.stream().anyMatch(name -> ShippedClasses.packageOf(name).equals(entityPackage)),
                    entityPackage + " holds no shipped class — either the feature is not migrated after all,"
                            + " or the package was renamed and this guard now scans for nothing");
        }

        List<String> violations = new ArrayList<>();
        List<String> unloadable = new ArrayList<>();
        for (String className : shipped) {
            if (entityPackages.contains(ShippedClasses.packageOf(className))) {
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName(className, false, EntityExposureTest.class.getClassLoader());
            } catch (Throwable failure) {
                unloadable.add(className + " (" + failure + ")");
                continue;
            }
            try {
                if (isPublic(type.getModifiers())) {
                    List<Type> supertypes = new ArrayList<>();
                    if (type.getGenericSuperclass() != null) {
                        supertypes.add(type.getGenericSuperclass());
                    }
                    supertypes.addAll(List.of(type.getGenericInterfaces()));
                    violations.addAll(exposures(entityPackages, className + " (supertypes)", supertypes));
                }
                for (Field field : type.getDeclaredFields()) {
                    if (isPublic(field.getModifiers()) && !field.isSynthetic()) {
                        violations.addAll(exposures(entityPackages, className + "#" + field.getName(),
                                List.of(field.getGenericType())));
                    }
                }
                for (Method method : type.getDeclaredMethods()) {
                    if (isPublic(method.getModifiers()) && !method.isSynthetic() && !method.isBridge()) {
                        violations.addAll(exposures(entityPackages, className + "#" + method.getName() + "(..)",
                                signatureOf(method, method.getGenericReturnType())));
                    }
                }
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    if (isPublic(constructor.getModifiers()) && !constructor.isSynthetic()) {
                        violations.addAll(exposures(entityPackages, className + "#<init>(..)",
                                signatureOf(constructor, null)));
                    }
                }
            } catch (Throwable failure) {
                unloadable.add(className + " (members unreadable: " + failure + ")");
            }
        }

        assertTrue(unloadable.isEmpty(),
                "these shipped classes could not be inspected, so the guard did NOT cover them — fix the"
                        + " scan rather than accepting the gap:\n" + String.join("\n", unloadable));
        assertTrue(violations.isEmpty(),
                "an entity escaped its feature through a public signature. Entities never leave their"
                        + " feature (spec §4): return the generated API DTO and map inside the feature:\n"
                        + String.join("\n", violations));
    }

    private static boolean isPublic(int modifiers) {
        return Modifier.isPublic(modifiers);
    }

    private static List<Type> signatureOf(Executable executable, Type returnType) {
        List<Type> types = new ArrayList<>();
        if (returnType != null) {
            types.add(returnType);
        }
        types.addAll(List.of(executable.getGenericParameterTypes()));
        return types;
    }

    /** One line per entity type reachable from the given signature types. */
    private static List<String> exposures(List<String> entityPackages, String member, List<Type> types) {
        Set<String> referenced = new HashSet<>();
        for (Type type : types) {
            collect(type, referenced, new HashSet<>());
        }
        List<String> found = new ArrayList<>();
        for (String candidate : referenced) {
            if (entityPackages.contains(ShippedClasses.packageOf(candidate))) {
                found.add("public " + member + " exposes the entity " + candidate);
            }
        }
        return found;
    }

    /** Every class name reachable from a generic type: raw types, arguments, bounds, array components. */
    private static void collect(Type type, Set<String> into, Set<Type> seen) {
        if (type == null || !seen.add(type)) {
            return;
        }
        if (type instanceof Class<?> raw) {
            Class<?> component = raw;
            while (component.isArray()) {
                component = component.getComponentType();
            }
            if (!component.isPrimitive()) {
                into.add(component.getName());
            }
        } else if (type instanceof ParameterizedType parameterized) {
            collect(parameterized.getRawType(), into, seen);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collect(argument, into, seen);
            }
        } else if (type instanceof GenericArrayType array) {
            collect(array.getGenericComponentType(), into, seen);
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                collect(bound, into, seen);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                collect(bound, into, seen);
            }
        } else if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                collect(bound, into, seen);
            }
        }
    }

}
