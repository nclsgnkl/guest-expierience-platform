package de.gxp.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Architecture guard E of five (constitution v1.7.0, owner decision
 * 2026-08-25) — the SCHEDULED-WORK inventory. Part of the definition of done:
 * it must stay green.
 *
 * <p><b>What it is for.</b> {@code quarkus-scheduler} makes a timer a
 * one-line annotation anywhere on the compile path. That is exactly what
 * makes "what runs on this box on a timer, and how often" unanswerable
 * without reading every file — and an on-premise instance is a box nobody
 * can log into to find out. This test is the answer, pinned as a literal
 * list: adding, removing or re-timing a job fails the build until the list
 * says so, so the list cannot drift from the code.
 *
 * <p><b>What it asserts</b>, over the source TEXT of {@code src/main/java}:
 *
 * <ol>
 * <li>The {@code @Scheduled} methods found — as
 * {@code <package>.<Class>#<method> cron="…"} (or {@code every="…"}) —
 * EQUAL {@link #PINNED_INVENTORY}, exactly. Compared as sorted LISTS, not as
 * sets: {@code @Scheduled} is {@code @Repeatable}, so two identical
 * annotations on one method are legal Java that would schedule it twice, and
 * a set comparison would collapse them into a pass.</li>
 * <li>Every job sits in {@code de.gxp.infrastructure.scheduler} (owner
 * decision: scheduling is centrally managed, one class per domain), and no
 * {@code @Scheduled} method sits on a {@code *Control}, {@code *Resource} or
 * {@code *Repository}. The package prefix is asserted rather than only the
 * class NAME, because a job parked in some feature's own {@code .control}
 * package under a name like {@code PairingSweeper} breaks central management
 * while passing every suffix check. (The example is spelled out with no
 * package prefix on purpose: guard A scans TEXT, so a fully qualified
 * cross-feature name in this javadoc would itself be a violation.) The
 * {@code *Resource} half is a live runtime trap, not a style rule:
 * {@code quarkus.security.deny-unannotated-members} is CLASS-scoped, so a
 * scheduled method on an annotated Boundary is security-checked with no
 * identity present and denied at runtime — with every test still green,
 * because no test calls it.</li>
 * <li>Non-vacuity: a sensible number of sources was actually scanned, and the
 * inventory is not empty — a scan pointed at the wrong directory, or a
 * pattern that stopped matching, would otherwise pass while guarding
 * nothing.</li>
 * </ol>
 *
 * <p>Source TEXT rather than reflection, consistent with guards A and D: the
 * annotation is not runtime-retained in a form this suite can enumerate
 * without booting Quarkus, and a text scan also sees a job that was commented
 * out or is not yet wired. Its blind spot is stated rather than hidden: a job
 * registered PROGRAMMATICALLY through the {@code Scheduler} API carries no
 * annotation and is invisible here. Nothing does that today; if something
 * ever needs to, it belongs in the same package and in this list, and this
 * javadoc is where the next reader learns the list is then incomplete.
 */
class ScheduledJobInventoryTest {

    /**
     * Every scheduled job on this box. THE source of truth for "what runs on
     * a timer and how often" — keep it in sync deliberately, never by
     * pasting whatever the failure message printed without reading it.
     *
     * <p>Five jobs: three daily at 02:00 (the audit retention joined on
     * 2026-08-26, auth-hardening T-14 / REQ-623) and, since update-channel
     * T-12 (REQ-707/708), the hourly manifest check at minute 17 and the
     * 15-minute desired-file reconcile — all expressed as a QUARTZ-style cron
     * (seconds minutes hours day-of-month month day-of-week —
     * {@code quarkus.scheduler.cron-type} defaults to {@code quartz},
     * verified against Quarkus 3.33.3.1). Cron rather than
     * {@code every = "24h"} on purpose: a relative interval restarts with the
     * process, and a building's power cut restarts the process.
     */
    private static final List<String> PINNED_INVENTORY = List.of(
            "de.gxp.infrastructure.scheduler.AuditScheduler#pruneExpiredEntries cron=\"0 0 2 * * ?\"",
            "de.gxp.infrastructure.scheduler.AuthScheduler#purgeExpiredTokens cron=\"0 0 2 * * ?\"",
            "de.gxp.infrastructure.scheduler.DeviceScheduler#sweepExpiredPairingRequests cron=\"0 0 2 * * ?\"",
            "de.gxp.infrastructure.scheduler.UpdateScheduler#checkForUpdate cron=\"0 17 * * * ?\"",
            "de.gxp.infrastructure.scheduler.UpdateScheduler#reconcileDesiredUpdate cron=\"0 */15 * * * ?\"");

    /** A class holding a {@code @Scheduled} method may not be named like these. */
    private static final List<String> FORBIDDEN_SUFFIXES = List.of("Control", "Resource", "Repository");

    /** The one package a scheduled job may live in (owner decision 2026-08-25). */
    private static final String SCHEDULER_PACKAGE = "de.gxp.infrastructure.scheduler";

    /**
     * Non-vacuity floor: a scan pointed at the wrong directory finds no files
     * and passes silently. On 2026-08-25 {@code src/main/java} held 70 sources.
     */
    private static final int MAIN_SOURCE_FLOOR = 60;

    /** The two jobs of 2026-08-25 — the inventory may grow, never silently shrink to nothing. */
    private static final int INVENTORY_FLOOR = 2;

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final String ANNOTATION = "@Scheduled(";
    private static final Pattern CRON = Pattern.compile("cron\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern EVERY = Pattern.compile("every\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern METHOD_NAME = Pattern.compile("(\\w+)\\s*\\(");

    @Test
    void theScheduledJobsAreExactlyTheOnesPinnedHere() throws IOException {
        Scan scan = scan();

        assertTrue(scan.scanned >= MAIN_SOURCE_FLOOR,
                "the scan found only " + scan.scanned + " java sources under src/main/java — it is looking"
                        + " in the wrong place; on 2026-08-25 the module held 70");
        assertTrue(scan.inventory.size() >= INVENTORY_FLOOR,
                "the scan found only " + scan.inventory.size() + " @Scheduled methods — on 2026-08-25 there"
                        + " were 2. Either the jobs were removed (then update PINNED_INVENTORY deliberately)"
                        + " or this scan no longer recognises the annotation and is passing vacuously");
        assertEquals(sorted(PINNED_INVENTORY), sorted(scan.inventory),
                "the scheduled jobs on disk are not the ones pinned in PINNED_INVENTORY. This list is how"
                        + " an operator learns what runs unattended on an instance nobody can log into, so"
                        + " a job may not be added, removed or re-timed without saying so here. Found on"
                        + " disk, verbatim (a repeated line means one method carries @Scheduled twice —"
                        + " @Scheduled is @Repeatable, and that schedules the job twice):\n"
                        + asJavaList(scan.inventory));
    }

    @Test
    void everyScheduledJobSitsInTheSchedulerPackageAndNotOnAControlBoundaryOrRepository() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String entry : scan().inventory) {
            String type = entry.substring(0, entry.indexOf('#'));
            String simpleName = type.substring(type.lastIndexOf('.') + 1);
            if (!type.startsWith(SCHEDULER_PACKAGE + ".")) {
                violations.add(entry + "  (outside " + SCHEDULER_PACKAGE + ")");
            }
            for (String suffix : FORBIDDEN_SUFFIXES) {
                if (simpleName.endsWith(suffix)) {
                    violations.add(entry + "  (a *" + suffix + " may not hold a scheduled job)");
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "a @Scheduled method sits outside de.gxp.infrastructure.scheduler, or on a *Control,"
                        + " *Resource or *Repository. On a *Resource that is a RUNTIME trap:"
                        + " quarkus.security.deny-unannotated-members is class-scoped, so the job would be"
                        + " security-checked with no identity and denied — with every test green."
                        + " Scheduling is centrally managed in de.gxp.infrastructure.scheduler, one class"
                        + " per domain, delegating to the feature's Control:\n"
                        + String.join("\n", violations));
    }

    /** The scan result: the inventory entries and how many files produced them. */
    private record Scan(List<String> inventory, int scanned) {
    }

    private static Scan scan() throws IOException {
        List<String> inventory = new ArrayList<>();
        int scanned = 0;
        for (Path file : mainSources()) {
            scanned++;
            String type = declaredPackage(file) + "." + file.getFileName().toString().replace(".java", "");
            String text = Files.readString(file);
            for (int at = text.indexOf(ANNOTATION); at >= 0; at = text.indexOf(ANNOTATION, at + 1)) {
                int argumentsStart = at + ANNOTATION.length() - 1;
                int argumentsEnd = closingParenthesis(text, argumentsStart);
                String arguments = text.substring(argumentsStart, argumentsEnd + 1);
                inventory.add(type + "#" + methodNameAfter(text, argumentsEnd + 1) + " " + schedule(arguments));
            }
        }
        return new Scan(inventory, scanned);
    }

    /** The index of the {@code )} matching the {@code (} at {@code open}. */
    private static int closingParenthesis(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        throw new IllegalStateException("unbalanced @Scheduled(...) at offset " + open);
    }

    /**
     * The name of the method the annotation is attached to: the first
     * {@code name(} after it, ignoring any further annotation lines in
     * between (an annotation line would otherwise look like a method).
     */
    private static String methodNameAfter(String text, int from) {
        StringBuilder declaration = new StringBuilder();
        for (String line : text.substring(from).lines().toList()) {
            if (line.isBlank() || line.strip().startsWith("@")) {
                continue;
            }
            declaration.append(line).append('\n');
            Matcher matcher = METHOD_NAME.matcher(declaration);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IllegalStateException("no method declaration follows a @Scheduled annotation");
    }

    /** {@code cron="…"} or {@code every="…"} — a job must state one of them. */
    private static String schedule(String arguments) {
        Matcher cron = CRON.matcher(arguments);
        if (cron.find()) {
            return "cron=\"" + cron.group(1) + "\"";
        }
        Matcher every = EVERY.matcher(arguments);
        if (every.find()) {
            return "every=\"" + every.group(1) + "\"";
        }
        return "NO-SCHEDULE" + arguments.replaceAll("\\s+", " ");
    }

    /** Sorted so the comparison ignores scan ORDER but not multiplicity. */
    private static List<String> sorted(List<String> entries) {
        return entries.stream().sorted().toList();
    }

    /** The found inventory as copy-pasteable Java, so fixing the pin is mechanical. */
    private static String asJavaList(List<String> inventory) {
        return sorted(inventory).stream()
                .map(entry -> "            \"" + entry.replace("\"", "\\\"") + "\",")
                .reduce((a, b) -> a + "\n" + b).orElse("            (nothing found)");
    }

    private static String declaredPackage(Path file) throws IOException {
        for (String line : Files.readAllLines(file)) {
            Matcher matcher = PACKAGE_DECLARATION.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    /** Production sources only — a test may schedule nothing that ships. */
    private static List<Path> mainSources() throws IOException {
        Path sourceRoot = FeaturePackages.moduleRoot().resolve("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), sourceRoot + " is missing");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".java")).forEach(files::add);
        }
        assertFalse(files.isEmpty(), "no java sources found under " + sourceRoot);
        return files;
    }
}
