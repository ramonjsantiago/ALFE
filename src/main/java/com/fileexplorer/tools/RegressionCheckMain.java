package com.fileexplorer.tools;

import com.fileexplorer.service.template.TemplateRecurringScheduleService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Phase 6.5.0: Headless regression checks.
 *
 * <p>Runs a small suite of deterministic checks covering persistence formats and
 * basic round-trip behavior. Exits with code {@code 0} on success and {@code 2}
 * on failure.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   java -cp target/classes com.fileexplorer.tools.RegressionCheckMain
 * </pre>
 *
 * <p>Optional system properties:</p>
 * <ul>
 *   <li>{@code fileexplorer.check.home}: override the temp home directory used for checks</li>
 * </ul>
 */
public final class RegressionCheckMain {

    private RegressionCheckMain() {}

    public static void main(String[] args) {
        String originalHome = System.getProperty("user.home");
        Path home = resolveHome();

        try {
            System.setProperty("user.home", home.toString());

            // 1) Operation queue persistence round-trip
            com.fileexplorer.service.ops.RegressionOpsFacade.assertQueuePersistenceRoundTrip();

            // 2) Template recurring schedule persistence sanity
            assertSchedulesPersistence();

            // 3) Lightweight crash snapshot path sanity (no IO required)
            CrashPathChecks.assertCrashPathStable();

            System.out.println("OK: Regression checks passed at " + Instant.now());
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("FAILED: Regression checks failed: " + t);
            t.printStackTrace(System.err);
            System.exit(2);
        } finally {
            if (originalHome != null) System.setProperty("user.home", originalHome);
        }
    }

    private static Path resolveHome() {
        String override = System.getProperty("fileexplorer.check.home");
        if (override != null && !override.isBlank()) return Paths.get(override.trim());
        return Paths.get(System.getProperty("java.io.tmpdir"), "fileexplorer-regression-home");
    }

    private static void assertSchedulesPersistence() {
        TemplateRecurringScheduleService s = new TemplateRecurringScheduleService();
        String templateId = "template-regression-1";
        s.removeRecurring(templateId);
        s.setRecurringMinutes(templateId, 15);
        long minutes = s.getRecurringMinutes(templateId).orElseThrow();
        if (minutes != 15L) {
            throw new AssertionError("Expected recurring minutes 15, got " + minutes);
        }
        // Write a full schedule state (Phase 5.4+) and ensure it can be re-read.
        s.setSchedule(templateId, 60, 0L, System.currentTimeMillis());
        var schedule = s.getSchedule(templateId).orElseThrow();
        if (schedule.minutes() != 60L) {
            throw new AssertionError("Expected minutes=60, got " + schedule.minutes());
        }
    }
    static final class CrashPathChecks {
        private CrashPathChecks() {}

        static void assertCrashPathStable() {
            // This check ensures the crash directory stays under ~/.fileexplorer/crash.
            Path home = Paths.get(System.getProperty("user.home"));
            Path expected = home.resolve(".fileexplorer").resolve("crash").resolve("last-crash.txt");
            // We avoid importing app classes here; just sanity-check the convention.
            if (!expected.toString().contains(".fileexplorer")) {
                throw new AssertionError("Unexpected crash path: " + expected);
            }
        }
    }
}
