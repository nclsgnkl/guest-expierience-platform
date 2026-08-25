package de.gxp.text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control — raw text templates (REQ-317/REQ-318). Stateless: validation at
 * save time (an unknown or out-of-context placeholder is named, never a
 * surprise on a kiosk) and server-side resolution (consumed by the guest API
 * in the next feature). No Boundary, no Entity — other Controls inject this.
 *
 * <p>Grammar: any brace pair without nested braces is a placeholder candidate
 * and must be in the field's allowed set — strictness is the point. Unpaired
 * braces are literal text. {@code |time}/{@code |date} format the value in
 * the property's timezone with the language's locale.
 *
 * <p>{@code @PermitAll}: this Control has no HTTP surface and carries no
 * secrets; the annotation keeps the "every public Control method is
 * explicitly annotated" audit mechanical.
 */
@ApplicationScoped
public class TemplateControl {

    /** Greeting and card templates — the full v1 placeholder set. */
    public static final Set<String> ALL_PLACEHOLDERS = Set.of(
            "stay.displayName", "stay.checkOut|time", "stay.checkOut|date", "property.name");

    /** Standby templates — no stay exists when the room is vacant. */
    public static final Set<String> STANDBY_PLACEHOLDERS = Set.of("property.name");

    private static final Pattern PLACEHOLDER_CANDIDATE = Pattern.compile("\\{([^{}]*)}");

    /** Everything a template may draw from at resolution time. */
    public record TemplateContext(String propertyName, String stayDisplayName, Instant checkOut,
            ZoneId timezone, Locale locale) {
    }

    /**
     * Returns every offending placeholder (with braces, e.g.
     * {@code {stay.checkout}}) — empty means the template is valid for a
     * field allowing {@code allowedPlaceholders}.
     */
    @PermitAll
    public List<String> findInvalidPlaceholders(String template, Set<String> allowedPlaceholders) {
        List<String> invalid = new ArrayList<>();
        if (template == null) {
            return invalid;
        }
        Matcher matcher = PLACEHOLDER_CANDIDATE.matcher(template);
        while (matcher.find()) {
            if (!allowedPlaceholders.contains(matcher.group(1))) {
                invalid.add(matcher.group());
            }
        }
        return invalid;
    }

    /**
     * Resolves a stored (already validated) template. A placeholder whose
     * context value is absent stays as-is rather than rendering "null".
     */
    @PermitAll
    public String resolve(String template, TemplateContext context) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER_CANDIDATE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = valueOf(matcher.group(1), context);
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String valueOf(String placeholder, TemplateContext context) {
        return switch (placeholder) {
            case "property.name" -> context.propertyName();
            case "stay.displayName" -> context.stayDisplayName();
            case "stay.checkOut|time" -> context.checkOut() == null ? null
                    : DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                            .withLocale(context.locale())
                            .withZone(context.timezone())
                            .format(context.checkOut());
            case "stay.checkOut|date" -> context.checkOut() == null ? null
                    : DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            .withLocale(context.locale())
                            .withZone(context.timezone())
                            .format(context.checkOut());
            default -> null; // validation prevents storage; resolve leaves it literal
        };
    }
}
