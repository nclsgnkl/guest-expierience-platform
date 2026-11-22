package de.gxp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.gxp.api.model.PropertyDto;
import de.gxp.api.model.RoomDto;
import de.gxp.audit.control.AuditControl;

/**
 * {@link AuditControl#changedFields} (design D17/D18, principle 3): names
 * only, never values. Plain unit test — no Quarkus boot.
 */
class ChangedFieldsTest {

    private static final String OLD_NAME = "Villa Sonnenhof";
    private static final String NEW_NAME = "Villa Mondschein";
    private static final String OLD_ZONE = "Europe/Berlin";
    private static final String NEW_ZONE = "Europe/Vienna";

    @Test
    void returnsExactlyTheNamesOfTheDifferingPropertiesSorted() {
        PropertyDto before = new PropertyDto().name(OLD_NAME).timezone(OLD_ZONE).brandPrimary("#112233");
        PropertyDto after = new PropertyDto().name(NEW_NAME).timezone(NEW_ZONE).brandPrimary("#112233");

        List<String> changed = AuditControl.changedFields(before, after);

        assertEquals(List.of("name", "timezone"), changed);
        for (String value : List.of(OLD_NAME, NEW_NAME, OLD_ZONE, NEW_ZONE, "#112233")) {
            assertFalse(changed.contains(value), "a value must never be returned: " + value);
            assertTrue(changed.stream().noneMatch(name -> name.contains(value)));
        }
    }

    @Test
    void equalDtosYieldNoFieldAndNullValuedFieldsCompareByEquality() {
        RoomDto before = new RoomDto().code("R1").displayName("Eins");
        RoomDto after = new RoomDto().code("R1").displayName("Eins");
        assertEquals(List.of(), AuditControl.changedFields(before, after));
        after.artworkAssetId(4711L);
        assertEquals(List.of("artworkAssetId"), AuditControl.changedFields(before, after),
                "null → value is a change; the row names the field, never the value");
    }

    @Test
    void refusesDifferentClassesAndNull() {
        assertThrows(IllegalArgumentException.class,
                () -> AuditControl.changedFields(new RoomDto(), new PropertyDto()));
        assertThrows(IllegalArgumentException.class, () -> AuditControl.changedFields(null, new RoomDto()));
    }
}
