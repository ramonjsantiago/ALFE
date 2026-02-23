package com.fileexplorer.service.ops.journal;

import com.fileexplorer.service.ops.ExecutionDriftPolicy;
import com.fileexplorer.service.ops.preview.OperationPlanAction;
import com.fileexplorer.service.ops.preview.OperationPlanItem;
import com.fileexplorer.service.ops.preview.OperationPlanSnapshot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 4.5.0: Append-only transaction journal.
 *
 * <p>Design goals:</p>
 * <ul>
 *   <li>Dependency-free format</li>
 *   <li>Append-only (write-ahead)</li>
 *   <li>Recoverable into a deterministic {@link OperationPlanSnapshot}</li>
 * </ul>
 */
public final class OperationJournalService {

    private static final String DIR_NAME = ".fileexplorer";
    private static final String JOURNAL_DIR = "journal";

    /**
     * Newline format:
     * <pre>
     * epochMillis\toperationId\ttype\tkey=value;key=value
     * </pre>
     */
    public void append(String operationId, OperationJournalRecordType type, Map<String, String> fields) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");

        StringBuilder sb = new StringBuilder(256);
        sb.append(Instant.now().toEpochMilli()).append('\t')
                .append(operationId).append('\t')
                .append(type.name()).append('\t');

        if (fields != null && !fields.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (!first) sb.append(';');
                first = false;
                sb.append(escape(e.getKey())).append('=')
                        .append(escape(e.getValue()));
            }
        }

        Path file = journalPath(operationId);
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                w.write(sb.toString());
                w.newLine();
            }
        } catch (IOException ignored) {
            // best effort
        }
    }

/**
 * journalDir.
 *
 * @return TODO
 */
    public Path journalDir() {
        Path base = Paths.get(System.getProperty("user.home"), DIR_NAME);
        return base.resolve(JOURNAL_DIR);
    }

/**
 * journalPath.
 *
 * @param operationId TODO
 * @return TODO
 */
    public Path journalPath(String operationId) {
        return journalDir().resolve(operationId + ".journal");
    }

    public List<String> listJournalOperationIds() {
        Path dir = journalDir();
        if (!Files.isDirectory(dir)) return List.of();
        try {
            List<String> ids = new ArrayList<>();
            try (var s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".journal"))
                        .forEach(p -> {
                            String fn = p.getFileName().toString();
                            ids.add(fn.substring(0, fn.length() - ".journal".length()));
                        });
            }
            Collections.sort(ids);
            return ids;
        } catch (IOException e) {
            return List.of();
        }
    }

/**
 * readAll.
 *
 * @param operationId TODO
 * @return TODO
 */
    public List<OperationJournalEntry> readAll(String operationId) {
        Path file = journalPath(operationId);
        if (!Files.exists(file)) return List.of();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<OperationJournalEntry> out = new ArrayList<>();
            for (String line : lines) {
                OperationJournalEntry e = parseLine(line);
                if (e != null) out.add(e);
            }
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

/**
 * isComplete.
 *
 * @param operationId TODO
 * @return TODO
 */
    public boolean isComplete(String operationId) {
        List<OperationJournalEntry> entries = readAll(operationId);
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).type() == OperationJournalRecordType.OPERATION_COMPLETE) return true;
        }
        return false;
    }

    /** Delete a journal file (best-effort). */
    public boolean deleteJournal(String operationId) {
        if (operationId == null || operationId.isBlank()) return false;
        try {
            return java.nio.file.Files.deleteIfExists(journalPath(operationId));
        } catch (Exception e) {
            return false;
        }
    }

/**
 * buildRecoveryCandidate.
 *
 * @param operationId TODO
 * @return TODO
 */
    public RecoveryCandidate buildRecoveryCandidate(String operationId) {
        List<OperationJournalEntry> entries = readAll(operationId);
        if (entries.isEmpty()) return null;
        boolean complete = false;
        String previewHash = "";
        String opType = "";
        String targetDirectory = "";
        ExecutionDriftPolicy driftPolicy = null;
        int planItems = 0;
        int completedItems = 0;

        for (OperationJournalEntry e : entries) {
            if (e.type() == OperationJournalRecordType.OPERATION_START) {
                previewHash = e.fields().getOrDefault("previewHash", "");
                opType = e.fields().getOrDefault("type", "");
                targetDirectory = e.fields().getOrDefault("targetDirectory", "");
                String dp = e.fields().getOrDefault("driftPolicy", "");
                if (!dp.isBlank()) {
                    try { driftPolicy = ExecutionDriftPolicy.valueOf(dp); } catch (Exception ignored) {}
                }
            } else if (e.type() == OperationJournalRecordType.PLAN_ITEM) {
                planItems++;
            } else if (e.type() == OperationJournalRecordType.ITEM_SUCCESS || e.type() == OperationJournalRecordType.ITEM_FAIL) {
                completedItems++;
            } else if (e.type() == OperationJournalRecordType.OPERATION_COMPLETE) {
                complete = true;
            }
        }

        if (complete) return null;
        return new RecoveryCandidate(operationId, previewHash, opType, targetDirectory, driftPolicy, planItems, completedItems);
    }

/**
 * findIncomplete.
 *
 * @return TODO
 */
    public List<RecoveryCandidate> findIncomplete() {
        List<String> ids = listJournalOperationIds();
        if (ids.isEmpty()) return List.of();
        List<RecoveryCandidate> out = new ArrayList<>();
        for (String id : ids) {
            RecoveryCandidate c = buildRecoveryCandidate(id);
            if (c != null) out.add(c);
        }
        return out;
    }

/**
 * reconstructSnapshot.
 *
 * @param operationId TODO
 * @return TODO
 */
    public OperationPlanSnapshot reconstructSnapshot(String operationId) {
        List<OperationJournalEntry> entries = readAll(operationId);
        if (entries.isEmpty()) return null;

        String type = "";
        String targetDirectory = "";
        String previewHash = "";
        List<OperationPlanItem> items = new ArrayList<>();

        for (OperationJournalEntry e : entries) {
            if (e.type() == OperationJournalRecordType.OPERATION_START) {
                type = e.fields().getOrDefault("type", "");
                targetDirectory = e.fields().getOrDefault("targetDirectory", "");
                previewHash = e.fields().getOrDefault("previewHash", "");
            } else if (e.type() == OperationJournalRecordType.PLAN_ITEM) {
                int idx = parseInt(e.fields().get("idx"), -1);
                String src = e.fields().getOrDefault("src", "");
                String dst = e.fields().getOrDefault("dst", "");
                String act = e.fields().getOrDefault("action", "COPY");
                String reason = e.fields().getOrDefault("reason", "");

                OperationPlanAction a;
                try { a = OperationPlanAction.valueOf(act); } catch (Exception ex) { a = OperationPlanAction.COPY; }

                // Keep list position stable
                while (items.size() <= idx) items.add(null);
                items.set(idx, new OperationPlanItem(
                        src.isBlank() ? null : Path.of(src),
                        dst.isBlank() ? null : Path.of(dst),
                        a,
                        reason
                ));
            }
        }

        // compact nulls if any
        List<OperationPlanItem> compact = new ArrayList<>();
        for (OperationPlanItem it : items) {
            if (it != null) compact.add(it);
        }

        com.fileexplorer.service.ops.FileOperationType t;
        try { t = com.fileexplorer.service.ops.FileOperationType.valueOf(type); }
        catch (Exception ex) { return null; }

        Path td = targetDirectory.isBlank() ? null : Path.of(targetDirectory);

        // counts are not critical for recovery; keep safe defaults
        com.fileexplorer.service.ops.preview.PreviewCounts counts = new com.fileexplorer.service.ops.preview.PreviewCounts(
                compact.size(), 0, 0, 0, 0, 0, 0, false, false, false
        );

        return new OperationPlanSnapshot(
                t,
                td,
                null,
                counts,
                compact,
                List.of(),
                List.of("Recovered plan from journal", "PreviewHash=" + previewHash),
                previewHash.isBlank() ? null : previewHash
        );
    }

/**
 * writeOperationStart.
 *
 * @param operationId TODO
 * @param snap TODO
 * @param driftPolicy TODO
 */
    public void writeOperationStart(String operationId, OperationPlanSnapshot snap, ExecutionDriftPolicy driftPolicy) {
        Map<String, String> f = new HashMap<>();
        f.put("type", snap == null ? "" : String.valueOf(snap.type()));
        f.put("targetDirectory", snap == null || snap.targetDirectory() == null ? "" : snap.targetDirectory().toString());
        f.put("previewHash", snap == null ? "" : String.valueOf(snap.previewHash()));
        f.put("driftPolicy", driftPolicy == null ? "" : driftPolicy.name());
        append(operationId, OperationJournalRecordType.OPERATION_START, f);

        if (snap != null && snap.actions() != null) {
            int idx = 0;
            for (OperationPlanItem it : snap.actions()) {
                Map<String, String> pi = new HashMap<>();
                pi.put("idx", String.valueOf(idx));
                pi.put("src", it.source() == null ? "" : it.source().toString());
                pi.put("dst", it.destination() == null ? "" : it.destination().toString());
                pi.put("action", it.action() == null ? "" : it.action().name());
                pi.put("reason", it.reason() == null ? "" : it.reason());
                append(operationId, OperationJournalRecordType.PLAN_ITEM, pi);
                idx++;
            }
        }
    }

/**
 * writeItemStart.
 *
 * @param operationId TODO
 * @param idx TODO
 * @param it TODO
 */
    public void writeItemStart(String operationId, int idx, OperationPlanItem it) {
        Map<String, String> f = new HashMap<>();
        f.put("idx", String.valueOf(idx));
        if (it != null) {
            f.put("src", it.source() == null ? "" : it.source().toString());
            f.put("dst", it.destination() == null ? "" : it.destination().toString());
            f.put("action", it.action() == null ? "" : it.action().name());
        }
        append(operationId, OperationJournalRecordType.ITEM_START, f);
    }

/**
 * writeItemSuccess.
 *
 * @param operationId TODO
 * @param idx TODO
 * @param message TODO
 */
    public void writeItemSuccess(String operationId, int idx, String message) {
        Map<String, String> f = new HashMap<>();
        f.put("idx", String.valueOf(idx));
        f.put("msg", message == null ? "" : message);
        append(operationId, OperationJournalRecordType.ITEM_SUCCESS, f);
    }

/**
 * writeItemFail.
 *
 * @param operationId TODO
 * @param idx TODO
 * @param message TODO
 */
    public void writeItemFail(String operationId, int idx, String message) {
        Map<String, String> f = new HashMap<>();
        f.put("idx", String.valueOf(idx));
        f.put("msg", message == null ? "" : message);
        append(operationId, OperationJournalRecordType.ITEM_FAIL, f);
    }

/**
 * writeDrift.
 *
 * @param operationId TODO
 * @param message TODO
 */
    public void writeDrift(String operationId, String message) {
        Map<String, String> f = new HashMap<>();
        f.put("msg", message == null ? "" : message);
        append(operationId, OperationJournalRecordType.DRIFT_EVENT, f);
    }

/**
 * writeComplete.
 *
 * @param operationId TODO
 * @param status TODO
 * @param replayIntegrity TODO
 */
    public void writeComplete(String operationId, String status, String replayIntegrity) {
        Map<String, String> f = new HashMap<>();
        f.put("status", status == null ? "" : status);
        f.put("replayIntegrity", replayIntegrity == null ? "" : replayIntegrity);
        append(operationId, OperationJournalRecordType.OPERATION_COMPLETE, f);
        // Phase 4.5.1: compact completed journals to keep disk usage low
        compactCompleteJournal(operationId);
    }



    /**
     * Phase 4.5.1: Compact a completed journal into a minimal summary.
     * Keeps OPERATION_START and OPERATION_COMPLETE records only.
     */
    public void compactCompleteJournal(String operationId) {
        if (operationId == null || operationId.isBlank()) return;
        if (!isComplete(operationId)) return;
        Path file = journalPath(operationId);
        if (!Files.exists(file)) return;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return;
            String start = null;
            String complete = null;
            for (String line : lines) {
                OperationJournalEntry e = parseLine(line);
                if (e == null) continue;
                if (e.type() == OperationJournalRecordType.OPERATION_START && start == null) start = line;
                if (e.type() == OperationJournalRecordType.OPERATION_COMPLETE) complete = line;
            }
            if (start == null || complete == null) return;
            List<String> out = new ArrayList<>();
            out.add(start);
            out.add(complete);
            Files.write(file, out, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception ignored) {
        }
    }

/**
 * parseLine.
 *
 * @param line TODO
 * @return TODO
 */
    private static OperationJournalEntry parseLine(String line) {
        if (line == null) return null;
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#")) return null;
        String[] parts = t.split("\t", 4);
        if (parts.length < 3) return null;

        long ts;
        try { ts = Long.parseLong(parts[0]); } catch (Exception e) { ts = 0; }
        String opId = parts[1];
        OperationJournalRecordType type;
        try { type = OperationJournalRecordType.valueOf(parts[2]); }
        catch (Exception e) { return null; }

        Map<String, String> fields = new HashMap<>();
        if (parts.length == 4 && !parts[3].isBlank()) {
            String[] kvs = parts[3].split(";");
            for (String kv : kvs) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String k = unescape(kv.substring(0, eq));
                String v = unescape(kv.substring(eq + 1));
                fields.put(k, v);
            }
        }

        return new OperationJournalEntry(ts, opId, type, fields);
    }

/**
 * parseInt.
 *
 * @param s TODO
 * @param def TODO
 * @return TODO
 */
    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace(";", "\\;")
                .replace("=", "\\=");
    }

/**
 * unescape.
 *
 * @param s TODO
 * @return TODO
 */
    private static String unescape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!esc) {
                if (c == '\\') { esc = true; }
                else out.append(c);
            } else {
                esc = false;
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    default -> out.append(c);
                }
            }
        }
        if (esc) out.append('\\');
        return out.toString();
    }

    /**
     * Phase 4.5.0: Summary of an incomplete journal.
     */
    public record RecoveryCandidate(
            String operationId,
            String previewHash,
            String type,
            String targetDirectory,
            ExecutionDriftPolicy driftPolicy,
            int planItems,
            int completedItems
    ) {}
}
