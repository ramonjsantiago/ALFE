package com.fileexplorer.service.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;

/**
 * Phase 5.2.1+: Persistence for recurring template schedules.
 *
 * <p><strong>Backward compatible format</strong></p>
 * <ul>
 *   <li>Legacy value: {@code templateId = minutes}</li>
 *   <li>Phase 5.4.0 value: {@code templateId = minutes|lastRunEpochMillis|nextDueEpochMillis}</li>
 *   <li>Phase 5.4.2 value: {@code templateId = minutes|lastRunEpochMillis|nextDueEpochMillis|retryCount|backoffUntilEpochMillis|lastFailureCategory}</li>
 * </ul>
 *
 * <p>Times are stored as epoch-millis (UTC) so scheduler calculations are deterministic and DST-safe.</p>
 */
public final class TemplateRecurringScheduleService {

    private static final String DIR_NAME = ".fileexplorer";
    private static final String TEMPLATES_DIR = "templates";
    private static final String FILE_NAME = "recurring-schedules.properties";

    private final Path file;

    /**
     * Create a schedule persistence service using the user's home directory.
     */
    public TemplateRecurringScheduleService() {
        this.file = Paths.get(System.getProperty("user.home"), DIR_NAME, TEMPLATES_DIR, FILE_NAME);
    }

    /**
     * The backing schedules file.
     */
    public Path schedulesFile() {
        return file;
    }

    /**
     * Get only the recurring minutes for a template.
     *
     * <p>Kept for UI/backwards-compat callers.</p>
     */
    public OptionalLong getRecurringMinutes(String templateId) {
        Objects.requireNonNull(templateId, "templateId");
        Optional<RecurringSchedule> s = getSchedule(templateId);
        return s.map(RecurringSchedule::minutes).filter(m -> m > 0).map(OptionalLong::of).orElse(OptionalLong.empty());
    }

    /**
     * List recurring minutes across all templates (minutes only).
     *
     * <p>Kept for backwards compatibility.</p>
     */
    public Map<String, Long> listRecurringMinutes() {
        Map<String, RecurringSchedule> m = listSchedules();
        if (m.isEmpty()) return Collections.emptyMap();
        Map<String, Long> out = new HashMap<>();
        for (var e : m.entrySet()) {
            long minutes = e.getValue().minutes();
            if (minutes > 0) out.put(e.getKey(), minutes);
        }
        return out;
    }

    /**
     * Set recurring minutes for a template, preserving (best-effort) lastRun/nextDue if present.
     *
     * <p>For Phase 5.4.0 correctness, prefer {@link #setSchedule(String, long, long, long)}.</p>
     */
    public void setRecurringMinutes(String templateId, long minutes) {
        Objects.requireNonNull(templateId, "templateId");
        long n = Math.max(1, minutes);

        Properties p = load();
        RecurringSchedule cur = parse(templateId, p.getProperty(templateId)).orElse(null);
        long lastRun = cur == null ? 0L : cur.lastRunEpochMillis();
        long nextDue = cur == null ? 0L : cur.nextDueEpochMillis();

        p.setProperty(templateId, encode(new RecurringSchedule(n, lastRun, nextDue, 0, 0L, null)));
        store(p);
    }

    /**
     * Remove a recurring schedule for a template.
     */
    public void removeRecurring(String templateId) {
        Objects.requireNonNull(templateId, "templateId");
        Properties p = load();
        p.remove(templateId);
        store(p);
    }

    /**
     * Phase 5.4.0: Read a full schedule state for a template.
     */
    public Optional<RecurringSchedule> getSchedule(String templateId) {
        Objects.requireNonNull(templateId, "templateId");
        Properties p = load();
        return parse(templateId, p.getProperty(templateId));
    }

    /**
     * Phase 5.4.0: List full schedule states for all templates.
     */
    public Map<String, RecurringSchedule> listSchedules() {
        Properties p = load();
        if (p.isEmpty()) return Collections.emptyMap();
        Map<String, RecurringSchedule> out = new HashMap<>();
        for (String k : p.stringPropertyNames()) {
            parse(k, p.getProperty(k)).ifPresent(v -> {
                if (v.minutes() > 0) out.put(k, v);
            });
        }
        return out;
    }

    /**
     * Phase 5.4.0: Persist full schedule state for a template.
     *
     * @param templateId template identifier
     * @param minutes recurring period in minutes (must be &gt; 0)
     * @param lastRunEpochMillis last run timestamp (epoch millis) or 0 if unknown
     * @param nextDueEpochMillis next due timestamp (epoch millis) or 0 to recompute
     */
    public void setSchedule(String templateId, long minutes, long lastRunEpochMillis, long nextDueEpochMillis) {
        // Back-compat convenience overload (Phase 5.4.0 callers)
        setSchedule(templateId, minutes, lastRunEpochMillis, nextDueEpochMillis, 0, 0L, null);
    }

    /**
     * Phase 5.4.2: Persist full schedule state including failure backoff state.
     *
     * @param templateId template identifier
     * @param minutes recurring period in minutes (must be > 0)
     * @param lastRunEpochMillis last run timestamp (epoch millis) or 0 if unknown
     * @param nextDueEpochMillis next due timestamp (epoch millis) or 0 to recompute
     * @param retryCount consecutive retry attempts since last success (non-negative)
     * @param backoffUntilEpochMillis backoff-imposed next due time (epoch millis) or 0
     * @param lastFailureCategory optional last failure category string
     */
    public void setSchedule(
            String templateId,
            long minutes,
            long lastRunEpochMillis,
            long nextDueEpochMillis,
            int retryCount,
            long backoffUntilEpochMillis,
            String lastFailureCategory
    ) {
        Objects.requireNonNull(templateId, "templateId");
        long n = Math.max(1, minutes);
        long lastRun = Math.max(0L, lastRunEpochMillis);
        long nextDue = Math.max(0L, nextDueEpochMillis);
        int retries = Math.max(0, retryCount);
        long backoffUntil = Math.max(0L, backoffUntilEpochMillis);

        Properties p = load();
        p.setProperty(templateId, encode(new RecurringSchedule(n, lastRun, nextDue, retries, backoffUntil, lastFailureCategory)));
        store(p);
    }

    /**
     * Parse a schedule value (legacy or Phase 5.4.0).
     */
    private static Optional<RecurringSchedule> parse(String templateId, String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            String v = raw.trim();
            if (!v.contains("|")) {
                long minutes = Long.parseLong(v);
                if (minutes <= 0) return Optional.empty();
                return Optional.of(new RecurringSchedule(minutes, 0L, 0L, 0, 0L, null));
            }
            String[] parts = v.split("\\|", -1);
            long minutes = parts.length > 0 ? parseLong(parts[0], 0L) : 0L;
            long lastRun = parts.length > 1 ? parseLong(parts[1], 0L) : 0L;
            long nextDue = parts.length > 2 ? parseLong(parts[2], 0L) : 0L;
            int retryCount = parts.length > 3 ? (int) Math.max(0L, parseLong(parts[3], 0L)) : 0;
            long backoffUntil = parts.length > 4 ? parseLong(parts[4], 0L) : 0L;
            String failureCat = parts.length > 5 ? safeString(parts[5]) : null;
            if (minutes <= 0) return Optional.empty();
            return Optional.of(new RecurringSchedule(
                    minutes,
                    Math.max(0L, lastRun),
                    Math.max(0L, nextDue),
                    retryCount,
                    Math.max(0L, backoffUntil),
                    failureCat
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static long parseLong(String s, long def) {
        if (s == null) return def;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    private static String safeString(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String encode(RecurringSchedule s) {
        // Phase 5.4.2+ format (backward compatible with earlier readers)
        String cat = s.lastFailureCategory() == null ? "" : s.lastFailureCategory();
        return s.minutes()
                + "|" + s.lastRunEpochMillis()
                + "|" + s.nextDueEpochMillis()
                + "|" + s.retryCount()
                + "|" + s.backoffUntilEpochMillis()
                + "|" + cat;
    }

    /**

    /**
     * Phase 5.6.1: Validation and repair report for recurring schedule persistence.
     *
     * @param total total entries encountered in the properties file
     * @param removed entries removed due to invalid values
     * @param repaired entries rewritten due to normalization or missing fields
     * @param errors entries that could not be parsed (best-effort)
     */
    public record ValidationReport(int total, int removed, int repaired, int errors) {}

    /**
     * Phase 5.6.1: Validate and repair the recurring schedule store (best-effort).
     *
     * <p>Actions performed:</p>
     * <ul>
     *   <li>Remove entries with minutes <= 0 or non-numeric minutes.</li>
     *   <li>Normalize negative timestamps/retry counts to 0.</li>
     *   <li>Recompute nextDue when missing/zero using lastRun (or now) + period.</li>
     *   <li>Clamp backoffUntil to >= 0, and clear expired backoff.</li>
     * </ul>
     *
     * @return report describing repairs
     */
    public ValidationReport validateAndRepair() {
        Properties p = load();
        if (p.isEmpty()) return new ValidationReport(0, 0, 0, 0);

        int total = 0;
        int removed = 0;
        int repaired = 0;
        int errors = 0;
        boolean changed = false;
        long now = System.currentTimeMillis();

        for (String k : p.stringPropertyNames()) {
            total++;
            String raw = p.getProperty(k);
            try {
                Optional<RecurringSchedule> parsed = parse(k, raw);
                if (parsed.isEmpty()) {
                    p.remove(k);
                    removed++;
                    changed = true;
                    continue;
                }

                RecurringSchedule s = parsed.get();
                long minutes = s.minutes();
                if (minutes <= 0) {
                    p.remove(k);
                    removed++;
                    changed = true;
                    continue;
                }

                long periodMillis = minutes * 60_000L;
                long lastRun = Math.max(0L, s.lastRunEpochMillis());
                long nextDue = Math.max(0L, s.nextDueEpochMillis());
                int retryCount = Math.max(0, s.retryCount());
                long backoffUntil = Math.max(0L, s.backoffUntilEpochMillis());
                String cat = safeString(s.lastFailureCategory());

                if (nextDue <= 0L) {
                    long base = lastRun > 0L ? lastRun : now;
                    nextDue = base + periodMillis;
                }

                if (backoffUntil > 0L && backoffUntil < now) {
                    backoffUntil = 0L;
                }

                String encoded = encode(new RecurringSchedule(minutes, lastRun, nextDue, retryCount, backoffUntil, cat));
                String normalizedRaw = raw == null ? "" : raw.trim();
                if (!Objects.equals(normalizedRaw, encoded)) {
                    p.setProperty(k, encoded);
                    repaired++;
                    changed = true;
                }
            } catch (Exception ex) {
                errors++;
            }
        }

        if (changed) store(p);
        return new ValidationReport(total, removed, repaired, errors);
    }
    /**

     * Load the schedule properties file.
     */
    private Properties load() {
        Properties p = new Properties();
        if (!Files.exists(file)) return p;
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            p.load(in);
        } catch (IOException ignored) {
        }
        return p;
    }

    /**
     * Store schedule properties file (best-effort).
     */
    private void store(Properties p) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException ignored) {
        }
        try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            p.store(out, "FileExplorer recurring template schedules (Phase 5.2.1+, " + Instant.now() + ")");
        } catch (IOException ignored) {
        }
    }

    /**
     * Full recurring schedule state (Phase 5.4.2).
     *
     * @param minutes recurring period in minutes
     * @param lastRunEpochMillis last time a run started (epoch millis) or 0
     * @param nextDueEpochMillis next due timestamp (epoch millis) or 0
     * @param retryCount consecutive retry attempts since the last successful run (non-negative)
     * @param backoffUntilEpochMillis if > 0, the next due time imposed by the failure backoff policy
     * @param lastFailureCategory optional category string describing the last failure
     */
    public record RecurringSchedule(
            long minutes,
            long lastRunEpochMillis,
            long nextDueEpochMillis,
            int retryCount,
            long backoffUntilEpochMillis,
            String lastFailureCategory
    ) { }
}
