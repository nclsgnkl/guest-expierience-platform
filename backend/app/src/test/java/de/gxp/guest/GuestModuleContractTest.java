package de.gxp.guest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import de.gxp.api.model.GuestInfoModule;
import de.gxp.api.model.GuestModule;

/**
 * The discriminant literals of the {@code GuestModule} union, PINNED BY HAND
 * on purpose — the {@code ScheduledJobInventoryTest} precedent
 * (module-content REQ-961, REQ-971; 02-design.md §8).
 *
 * <p><b>Why this exists.</b> Under D9's Variant B the literals live ONLY in
 * {@code api/openapi.yaml}'s {@code discriminator.mapping}. Nothing in the
 * generated TypeScript carries them — {@code type} is a plain {@code string}
 * there — so a contract rename would leave the kiosk's module registry
 * silently stale while every build stayed green. This test turns that rename
 * into a red build with the old and the new literal both on screen.
 *
 * <p><b>This is the ONLY guard on that axis.</b> The fabricated-type test in
 * {@code module-registry.spec.ts} cannot cover it and says so in its own
 * header: the fabricated type's discriminant is defined by the test file, so
 * test and registry agree by construction and there is no contract for them to
 * disagree with. The {@code %dev} exercise cannot cover it either — one seeded
 * type means a rename shows up only as "{@code /info} renders the
 * placeholder", which is quiet unless somebody looks.
 *
 * <p><b>The residual, precisely.</b> After a rename this build goes red and the
 * developer must edit {@link #EXPECTED_MAPPING} — at which point the literal is
 * in front of them and the kiosk's registry is one grep away. It converts a
 * silent drift into a loud one with a pointer; it does not PROVE the kiosk
 * agrees. Proving that needs the literal in one machine-readable place both
 * sides read, which Variant B forecloses.
 *
 * <p>It is deliberately NOT in {@code de.gxp.architecture}: the constitution
 * counts FIVE architecture guards by name, a sixth needs an amendment, and
 * Part B refused one for the same reason. This is an ordinary feature test.
 *
 * <p>Plain JUnit, no {@code @QuarkusTest}: it reads annotations off the
 * generated classes and needs neither a container nor a database.
 */
class GuestModuleContractTest {

    /**
     * Every member of the union, keyed by its WIRE discriminant — the value a
     * kiosk {@code switch} matches, never the schema name. Update this map
     * only together with {@code discriminator.mapping} in
     * {@code api/openapi.yaml} and the kiosk's {@code MODULE_REGISTRY}.
     */
    private static final Map<String, Class<?>> EXPECTED_MAPPING = Map.of("info", GuestInfoModule.class);

    @Test
    void theUnionsSubTypesAreExactlyThePinnedMapping() {
        JsonSubTypes subTypes = GuestModule.class.getAnnotation(JsonSubTypes.class);
        assertNotNull(subTypes, "GuestModule must be an interface carrying @JsonSubTypes —"
                + " a flattened concrete class means useOneOfInterfaces or the SIMPLIFY_ONEOF_ANYOF"
                + " normalizer regressed in backend/app/pom.xml (d9-spike.md)");

        Map<String, Class<?>> actual = new LinkedHashMap<>();
        for (JsonSubTypes.Type type : subTypes.value()) {
            actual.put(type.name(), type.value());
        }
        assertEquals(new TreeMap<>(EXPECTED_MAPPING), new TreeMap<>(actual),
                "the union's wire discriminants drifted from api/openapi.yaml's discriminator.mapping —"
                        + " the kiosk's MODULE_REGISTRY keys must be updated with them");
    }

    @Test
    void theDiscriminantPropertyIsTypeAndItIsAPlainString() {
        JsonTypeInfo typeInfo = GuestModule.class.getAnnotation(JsonTypeInfo.class);
        assertNotNull(typeInfo, "GuestModule must carry @JsonTypeInfo");
        assertEquals("type", typeInfo.property(), "the discriminator property is `type`");
        assertEquals(JsonTypeInfo.Id.NAME, typeInfo.use(), "the discriminant is the mapping NAME, not a class id");

        for (Class<?> member : EXPECTED_MAPPING.values()) {
            Class<?> returned = returnTypeOfGetType(member);
            assertEquals(String.class, returned, member.getSimpleName()
                    + ".getType() must return String — a single-value enum is the spelling D9 proved breaks");
        }
    }

    @Test
    void everyMemberCarriesItsMappingKeyAsJsonTypeName() {
        for (Map.Entry<String, Class<?>> entry : EXPECTED_MAPPING.entrySet()) {
            JsonTypeName typeName = entry.getValue().getAnnotation(JsonTypeName.class);
            assertNotNull(typeName, entry.getValue().getSimpleName() + " must carry @JsonTypeName");
            assertEquals(entry.getKey(), typeName.value(),
                    "the serialized discriminant must be the mapping key, never the schema name —"
                            + " a green build has shipped {\"type\":\"GuestModule\"} before (d9-spike.md)");
            assertTrue(GuestModule.class.isAssignableFrom(entry.getValue()),
                    entry.getValue().getSimpleName() + " must implement GuestModule");
        }
    }

    private static Class<?> returnTypeOfGetType(Class<?> member) {
        try {
            return member.getMethod("getType").getReturnType();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(member.getSimpleName() + " has no getType()", e);
        }
    }
}
