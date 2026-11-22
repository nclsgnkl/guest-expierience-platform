package de.gxp.update.control;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code MAJOR.MINOR.PATCH[-QUALIFIER]} (update-channel REQ-705/727, D6).
 * Numeric parts compare numerically; a qualifier sorts BELOW the same
 * release ({@code 1.0.0-SNAPSHOT < 1.0.0}); two qualifiers compare
 * lexically. Same regex as the exchange-file table (Design §2).
 *
 * <p><b>The updater must apply the same rule, and it is NOT what
 * {@code sort -V} does</b> (review 2026-08-26 #2: GNU {@code sort -V} orders
 * {@code 1.0.0 < 1.0.0-SNAPSHOT}, so a box on a qualified version would see
 * the release as "available" here and refuse it as a downgrade there — a
 * permanently stuck desired file). The bash side compares the numeric
 * triple and, on equality, treats the side WITH a qualifier as the lower one;
 * two qualifiers compare byte-wise. D6 was corrected accordingly.
 */
record SemanticVersion(int major, int minor, int patch, String qualifier) implements Comparable<SemanticVersion> {

    static final Pattern PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-([A-Za-z0-9.]{1,32}))?$");

    /** @throws IllegalArgumentException for anything the pattern does not admit (never echoes the input) */
    static SemanticVersion parse(String text) {
        Matcher matcher = text == null ? null : PATTERN.matcher(text);
        if (matcher == null || !matcher.matches() || text.length() > 64) {
            throw new IllegalArgumentException("not a semantic version");
        }
        try {
            return new SemanticVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)), matcher.group(4));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a semantic version");
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int byNumber = major != other.major ? Integer.compare(major, other.major)
                : minor != other.minor ? Integer.compare(minor, other.minor)
                : Integer.compare(patch, other.patch);
        if (byNumber != 0) {
            return byNumber;
        }
        if (qualifier == null) {
            return other.qualifier == null ? 0 : 1;
        }
        return other.qualifier == null ? -1 : qualifier.compareTo(other.qualifier);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (qualifier == null ? "" : "-" + qualifier);
    }
}
