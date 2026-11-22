package de.gxp.text.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import de.gxp.text.control.TemplateControl.TemplateContext;

/**
 * Unit tests for {@link TemplateControl} (REQ-317/REQ-318). Formatting is
 * asserted against exact strings for the pinned JDK 21 CLDR data —
 * 2026-08-19T14:42Z is 16:42 in Europe/Berlin.
 */
class TemplateControlTest {

    private final TemplateControl templates = new TemplateControl();

    private static final Instant CHECK_OUT = Instant.parse("2026-08-19T14:42:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static TemplateContext context(String localeTag) {
        return new TemplateContext("Meine Unterkunft", "Lisa", CHECK_OUT, BERLIN,
                Locale.forLanguageTag(localeTag));
    }

    @Test
    void allFourPlaceholdersResolveWithGermanFormatting() {
        String resolved = templates.resolve(
                "Hallo {stay.displayName}! Check-out {stay.checkOut|date} um {stay.checkOut|time} — {property.name}",
                context("de-DE"));
        assertEquals("Hallo Lisa! Check-out 19.08.2026 um 16:42 — Meine Unterkunft", resolved);
    }

    @Test
    void timeAndDateFollowTheEnglishLocale() {
        String resolved = templates.resolve(
                "Check-out is at {stay.checkOut|time} on {stay.checkOut|date}",
                context("en-GB"));
        assertEquals("Check-out is at 16:42 on 19 Aug 2026", resolved);
    }

    @Test
    void formattingUsesThePropertyTimezone() {
        TemplateContext utcContext = new TemplateContext("P", "L", CHECK_OUT,
                ZoneId.of("UTC"), Locale.forLanguageTag("de-DE"));
        assertEquals("14:42", templates.resolve("{stay.checkOut|time}", utcContext));
    }

    @Test
    void validTemplatePassesValidation() {
        assertTrue(templates.findInvalidPlaceholders(
                "Willkommen, {stay.displayName}! {stay.checkOut|time} {stay.checkOut|date} {property.name}",
                TemplateControl.ALL_PLACEHOLDERS).isEmpty());
    }

    @Test
    void unknownPlaceholderIsNamedWithBraces() {
        List<String> invalid = templates.findInvalidPlaceholders(
                "Bye at {stay.checkout}", TemplateControl.ALL_PLACEHOLDERS);
        assertEquals(List.of("{stay.checkout}"), invalid);
    }

    @Test
    void standbyContextRejectsStayPlaceholders() {
        List<String> invalid = templates.findInvalidPlaceholders(
                "{property.name} — {stay.displayName}", TemplateControl.STANDBY_PLACEHOLDERS);
        assertEquals(List.of("{stay.displayName}"), invalid);
    }

    @Test
    void everyOffenderIsListedNotJustTheFirst() {
        List<String> invalid = templates.findInvalidPlaceholders(
                "{foo} and {stay.displayName} and {bar}", TemplateControl.STANDBY_PLACEHOLDERS);
        assertEquals(List.of("{foo}", "{stay.displayName}", "{bar}"), invalid);
    }

    @Test
    void unpairedBracesAreLiteralAndValid() {
        assertTrue(templates.findInvalidPlaceholders("100% { of the time",
                TemplateControl.ALL_PLACEHOLDERS).isEmpty());
        assertEquals("100% { of the time",
                templates.resolve("100% { of the time", context("de-DE")));
    }

    @Test
    void emptyBracePairIsFlaggedNotGuessed() {
        assertEquals(List.of("{}"),
                templates.findInvalidPlaceholders("hello {}", TemplateControl.ALL_PLACEHOLDERS));
    }

    @Test
    void placeholderWithoutAContextValueStaysLiteral() {
        TemplateContext vacant = new TemplateContext("Meine Unterkunft", null, null, BERLIN,
                Locale.forLanguageTag("de-DE"));
        assertEquals("Willkommen, {stay.displayName} in Meine Unterkunft",
                templates.resolve("Willkommen, {stay.displayName} in {property.name}", vacant));
    }
}
