package com.fileexplorer.service.template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Phase 5.2.1: Append-only history log for template scheduler executions.
 *
 * <p>Format is a single TSV line per entry:</p>
 * <pre>
 *   epochMillis \t templateId \t templateName \t status \t detail \t operationId
 * </pre>
 */
public final class TemplateRunHistoryService {

    private static final String DIR_NAME = ".fileexplorer";
    private static final String TEMPLATES_DIR = "templates";
    private static final String FILE_NAME = "scheduler-history.log";

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Path file;

    private final SchedulerSettingsService settingsService = new SchedulerSettingsService();

    // Best-effort retention trimming.
    private volatile int maxEntries = SchedulerSettings.DEFAULT_HISTORY_RETENTION_ENTRIES;
    private int writesSinceTrim = 0;


/**
 * TemplateRunHistoryService.
 *
 * @return TODO
 */
    public TemplateRunHistoryService() {
        this.file = Paths.get(System.getProperty("user.home"), DIR_NAME, TEMPLATES_DIR, FILE_NAME);
        // Load persisted settings best-effort.
        try {
            this.maxEntries = settingsService.load().historyRetentionEntries();
        } catch (Exception ignored) {
        }
    }

/**
 * historyFile.
 *
 * @return TODO
 */
    public Path historyFile() {
        return file;
    }

    /**
     * Configure the maximum number of history entries to retain (best-effort).
     */
    public void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(100, maxEntries);
        // Trigger an async trim on next write.
        this.writesSinceTrim = 25;
    }

/**
 * log.
 *
 * @param e TODO
 */
    public void log(TemplateRunHistoryEntry e) {
        Objects.requireNonNull(e, "e");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException ignored) {
        }

        String line = e.timestampMillis()
                + "\t" + esc(e.templateId())
                + "\t" + esc(nz(e.templateName()))
                + "\t" + esc(nz(e.status()))
                + "\t" + esc(nz(e.detail()))
                + "\t" + esc(nz(e.operationId()))
                + "\n";

        try {
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }

        // Best-effort retention: trim occasionally to keep the log bounded.
        if (++writesSinceTrim >= 25) {
            writesSinceTrim = 0;
            trimToLastN(maxEntries);
        }
    }

    /**
     * Read recent history entries for a template (best effort).
     */
    public List<TemplateRunHistoryEntry> listRecent(String templateId, int limit) {
        Objects.requireNonNull(templateId, "templateId");
        int max = Math.max(1, Math.min(500, limit));
        if (!Files.exists(file)) return List.of();

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return List.of();

            List<TemplateRunHistoryEntry> out = new ArrayList<>();
            for (int i = lines.size() - 1; i >= 0 && out.size() < max; i--) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) continue;
                TemplateRunHistoryEntry e = parse(line);
                if (e == null) continue;
                if (templateId.equals(e.templateId())) out.add(e);
            }
            Collections.reverse(out);
            return out;
        } catch (IOException ignored) {
            return List.of();
        }
    }


    /**
     * Phase 5.3.0: Read recent history entries across all templates (best effort).
     */
    public List<TemplateRunHistoryEntry> listRecentAll(int limit) {
        int max = Math.max(1, Math.min(1000, limit));
        if (!Files.exists(file)) return List.of();

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return List.of();

            List<TemplateRunHistoryEntry> out = new ArrayList<>();
            for (int i = lines.size() - 1; i >= 0 && out.size() < max; i--) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) continue;
                TemplateRunHistoryEntry e = parse(line);
                if (e != null) out.add(e);
            }
            Collections.reverse(out);
            return out;
        } catch (IOException ignored) {
            return List.of();
        }
    }


    /**
     * Phase 5.5.0: Read (best-effort) all history entries across all templates, capped to a limit.
     *
     * <p>This is intended for reporting/export. It reads the whole file and parses sequentially.</p>
     */
    public List<TemplateRunHistoryEntry> listAll(int limit) {
        int max = Math.max(1, Math.min(20000, limit));
        if (!Files.exists(file)) return List.of();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return List.of();
            List<TemplateRunHistoryEntry> out = new ArrayList<>(Math.min(lines.size(), max));
            for (int i = 0; i < lines.size() && out.size() < max; i++) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) continue;
                TemplateRunHistoryEntry e = parse(line);
                if (e != null) out.add(e);
            }
            return out;
        } catch (IOException ignored) {
            return List.of();
        }
    }

    /**
     * Phase 5.5.0: Read (best-effort) all history entries for a single template, capped to a limit.
     */
    public List<TemplateRunHistoryEntry> listAll(String templateId, int limit) {
        Objects.requireNonNull(templateId, "templateId");
        int max = Math.max(1, Math.min(20000, limit));
        if (!Files.exists(file)) return List.of();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return List.of();
            List<TemplateRunHistoryEntry> out = new ArrayList<>();
            for (int i = 0; i < lines.size() && out.size() < max; i++) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) continue;
                TemplateRunHistoryEntry e = parse(line);
                if (e != null && templateId.equals(e.templateId())) out.add(e);
            }
            return out;
        } catch (IOException ignored) {
            return List.of();
        }
    }

    /**
     * Best-effort retention trimming: keep only the last {@code max} entries in the history file.
     *
     * <p>This rewrites the file and is therefore intentionally called sparingly.</p>
     */
    private void trimToLastN(int max) {
    int n = Math.max(1, max);
    if (!Files.exists(file)) return;
    try {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() <= n) return;
        List<String> tail = lines.subList(Math.max(0, lines.size() - n), lines.size());
        Files.write(file, tail, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException ignored) {
    }
    }

/**
 * Phase 5.6.1: Force a best-effort trim now using the currently configured retention.
 *
 * <p>This rewrites the history file and may be slow for very large logs, so it should be used
 * sparingly (e.g., from a maintenance action).</p>
 *
 * @return true if a trim was attempted, false if history file does not exist
 */
public boolean trimNow() {
    if (!Files.exists(file)) return false;
    trimToLastN(maxEntries);
    return true;
}


    /**
     * Render a user-friendly line for UI lists.
     */
    public static String formatForUi(TemplateRunHistoryEntry e) {
        if (e == null) return "";
        String ts = TS_FMT.format(Instant.ofEpochMilli(e.timestampMillis()));
        String op = (e.operationId() == null || e.operationId().isBlank()) ? "" : ("  op=" + e.operationId());
        String name = (e.templateName() == null || e.templateName().isBlank()) ? e.templateId() : e.templateName();
        String detail = (e.detail() == null || e.detail().isBlank()) ? "" : (" — " + e.detail());
        return ts + "  " + name + "  [" + nz(e.status()) + "]" + op + detail;
    }

/**
 * parse.
 *
 * @param line TODO
 * @return TODO
 */
    private static TemplateRunHistoryEntry parse(String line) {
        try {
            String[] parts = line.split("\\t", -1);
            if (parts.length < 2) return null;
            long ts = Long.parseLong(parts[0].trim());
            String templateId = unesc(parts[1]);
            String templateName = parts.length > 2 ? unesc(parts[2]) : "";
            String status = parts.length > 3 ? unesc(parts[3]) : "";
            String detail = parts.length > 4 ? unesc(parts[4]) : "";
            String opId = parts.length > 5 ? unesc(parts[5]) : "";
            if (opId != null && opId.isBlank()) opId = null;
            if (templateName != null && templateName.isBlank()) templateName = null;
            return new TemplateRunHistoryEntry(ts, templateId, templateName, status, detail, opId);
        } catch (Exception ignored) {
            return null;
        }
    }

/**
 * nz.
 *
 * @param s TODO
 * @return TODO
 */
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

/**
 * unesc.
 *
 * @param s TODO
 * @return TODO
 */
    private static String unesc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                if (c == 't') out.append('\t');
                else if (c == 'n') out.append('\n');
                else out.append(c);
                esc = false;
            } else {
                if (c == '\\') esc = true;
                else out.append(c);
            }
        }
        if (esc) out.append('\\');
        return out.toString();
    }
}