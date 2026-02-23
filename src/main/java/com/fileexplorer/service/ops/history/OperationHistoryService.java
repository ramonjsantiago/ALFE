package com.fileexplorer.service.ops.history;

import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.OperationStatus;
import com.fileexplorer.service.ops.history.persistence.HistoryStorageConfig;
import com.fileexplorer.service.ops.history.persistence.OperationHistoryStore;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Append-only operation history for audit and diagnostics.
 *
 * <p>
 * Phase 3.8.0 upgrades persistence to a snapshot + WAL engine:
 * <ul>
 *   <li>Snapshot: operation-history.snapshot.jsonl</li>
 *   <li>WAL: operation-history.wal.jsonl</li>
 * </ul>
 * Storage defaults to ${user.home}/.fileexplorer/history and may be overridden with
 * -Dfileexplorer.history.dir=/path.
 * </p>
 */
public final class OperationHistoryService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(OperationHistoryService.class.getName());

    private static final String PREF_NODE = "com.fileexplorer.history";
    private static final String PREF_MAX_ENTRIES = "history.maxEntries";
    private static final String PREF_MAX_DAYS = "history.maxDays";

    // Phase 3.8.2: persistence tuning
    private static final String PREF_WAL_CHECKPOINT_BYTES = "history.walCheckpointBytes";
    private static final String PREF_WAL_ARCHIVE_KEEP = "history.walArchiveKeep";
    private static final String PREF_CHECKPOINT_ON_STARTUP = "history.checkpointOnStartup";

    private static final int DEFAULT_MAX_ENTRIES = 5000;
    private static final int DEFAULT_MAX_DAYS = 30;

    /** Phase 3.8.2: checkpoint when WAL reaches this size (bytes). */
    private static final long DEFAULT_WAL_CHECKPOINT_BYTES = 2_000_000L; // ~2MB

    /** Phase 3.8.2: keep this many WAL archives when rotating. */
    private static final int DEFAULT_WAL_ARCHIVE_KEEP = 5;

    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    private final ObservableList<OperationHistoryEntry> entries =
            FXCollections.observableArrayList();

    private final OperationHistoryStore store;

    // ---- Phase 3.8.4: health/diagnostics ----
    private volatile Instant lastLoadAt = null;
    private volatile int lastLoadTotalLines = 0;
    private volatile int lastLoadBadLines = 0;
    private volatile boolean lastLoadCheckpointed = false;
    private volatile Instant lastCheckpointAt = null;
    private volatile String lastCheckpointReason = "";
    private volatile String lastLoadMessage = "Loading...";


    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fileexplorer-op-history-io");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean pruneScheduled = new AtomicBoolean(false);

/**
 * OperationHistoryService.
 *
 * @return TODO
 */
    public OperationHistoryService() {
        this.store = new OperationHistoryStore(HistoryStorageConfig.resolveBaseDir());
        // best-effort migration from Phase 3.7.x legacy single-file history
        this.store.migrateLegacyIfPresent(HistoryStorageConfig.legacyHistoryJsonl());
        loadExistingBestEffort();
    }

/**
 * entries.
 *
 * @return TODO
 */
    public ObservableList<OperationHistoryEntry> entries() {
        return FXCollections.unmodifiableObservableList(entries);
    }

    /**
     * Returns the primary on-disk snapshot file path (useful for "Reveal").
     */
    public Path historyFile() {
        return store.snapshotFile();
    }

    /**
     * Returns the directory that stores history persistence files.
     */
    public Path historyDirectory() {
        return store.baseDir();
    }


    // ---- Phase 3.8.4: Health & diagnostics ----

    /**
     * Short, UI-friendly health summary (single line).
     */
    public String healthSummary() {
        String base = (lastLoadMessage == null || lastLoadMessage.isBlank()) ? "OK" : lastLoadMessage;
        if (lastLoadBadLines > 0) {
            return "History: " + base;
        }
        if (lastCheckpointAt != null && lastCheckpointReason != null && !lastCheckpointReason.isBlank()) {
            // keep this terse; details are in diagnosticsText()
            return "History: OK (last checkpoint: " + lastCheckpointReason + ")";
        }
        return "History: OK";
    }

    /**
     * Multi-line diagnostic block intended for copy/paste in bug reports.
     */
    public String diagnosticsText() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("FileExplorer Operation History Diagnostics").append('\n');
        sb.append("Timestamp: ").append(Instant.now()).append('\n');
        sb.append('\n');

        sb.append("Storage").append('\n');
        sb.append("  Base dir: ").append(historyDirectory()).append('\n');
        sb.append("  Snapshot: ").append(store.snapshotFile()).append(" (").append(sizeBestEffort(store.snapshotFile())).append(" bytes)").append('\n');
        sb.append("  WAL:      ").append(store.walFile()).append(" (").append(store.walSizeBytesBestEffort()).append(" bytes)").append('\n');
        sb.append("  WAL archives kept: ").append(walArchiveKeep()).append('\n');
        sb.append('\n');

        sb.append("Load/Recovery").append('\n');
        sb.append("  Last load at: ").append(lastLoadAt).append('\n');
        sb.append("  Lines read: ").append(lastLoadTotalLines).append('\n');
        sb.append("  Corrupted lines skipped: ").append(lastLoadBadLines).append('\n');
        sb.append("  Startup checkpointed: ").append(lastLoadCheckpointed).append('\n');
        sb.append('\n');

        sb.append("Checkpointing").append('\n');
        sb.append("  Checkpoint-on-startup: ").append(checkpointOnStartup()).append(" (sysprop=").append(System.getProperty(HistoryStorageConfig.PROP_CHECKPOINT_ON_STARTUP)).append(")").append('\n');
        sb.append("  WAL checkpoint bytes: ").append(walCheckpointBytes()).append(" (sysprop=").append(System.getProperty(HistoryStorageConfig.PROP_WAL_CHECKPOINT_BYTES)).append(")").append('\n');
        sb.append("  Last checkpoint at: ").append(lastCheckpointAt).append('\n');
        sb.append("  Last checkpoint reason: ").append(lastCheckpointReason).append('\n');
        sb.append('\n');

        sb.append("Retention").append('\n');
        sb.append("  Max entries: ").append(maxEntries()).append('\n');
        sb.append("  Max days: ").append(maxDays()).append('\n');
        sb.append("  In-memory entries: ").append(entries.size()).append('\n');

        return sb.toString();
    }

    /**
     * Forces a best-effort checkpoint (snapshot rewrite + WAL rotation/truncation) using current in-memory entries.
     */
    public void forceCheckpointBestEffort(String reason) {
        String r = (reason == null || reason.isBlank()) ? "manual" : reason;
        // Snapshot the observable list on the FX thread, then compact on IO thread.
        java.util.concurrent.CompletableFuture<List<OperationHistoryEntry>> fut = new java.util.concurrent.CompletableFuture<>();
        fx(() -> {
            try {
                fut.complete(new ArrayList<>(entries));
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });

        io.submit(() -> {
            try {
                List<OperationHistoryEntry> snap = fut.get(500, TimeUnit.MILLISECONDS);
                // entries are newest-first already
                store.compactToSnapshot(snap, OperationHistoryService::toJson, true, walArchiveKeep());
                lastCheckpointAt = Instant.now();
                lastCheckpointReason = r;
            } catch (Throwable ignored) {
                // best-effort
            }
        });
    }

/**
 * sizeBestEffort.
 *
 * @param p TODO
 * @return TODO
 */
    private static long sizeBestEffort(Path p) {
        try {
            if (p == null || !Files.exists(p)) return 0L;
            return Files.size(p);
        } catch (IOException ignored) {
            return 0L;
        }
    }


/**
 * maxEntries.
 *
 * @return TODO
 */
    public int maxEntries() {
        return Math.max(100, prefs.getInt(PREF_MAX_ENTRIES, DEFAULT_MAX_ENTRIES));
    }

/**
 * maxDays.
 *
 * @return TODO
 */
    public int maxDays() {
        return Math.max(1, prefs.getInt(PREF_MAX_DAYS, DEFAULT_MAX_DAYS));
    }

/**
 * setMaxEntries.
 *
 * @param n TODO
 */
    public void setMaxEntries(int n) {
        prefs.putInt(PREF_MAX_ENTRIES, Math.max(100, n));
        schedulePrune();
    }

/**
 * setMaxDays.
 *
 * @param days TODO
 */
    public void setMaxDays(int days) {
        prefs.putInt(PREF_MAX_DAYS, Math.max(1, days));
        schedulePrune();
    }


    // ---- Phase 3.8.2: Persistence tuning (system properties override Preferences) ----

/**
 * walCheckpointBytes.
 *
 * @return TODO
 */
    public long walCheckpointBytes() {
        // 0 disables size-based checkpointing
        String prop = System.getProperty(HistoryStorageConfig.PROP_WAL_CHECKPOINT_BYTES);
        if (prop != null && !prop.isBlank()) {
            try {
                long v = Long.parseLong(prop.trim());
                return Math.max(0L, Math.min(v, 1_000_000_000L)); // clamp to 1GB
            } catch (NumberFormatException ignored) {
                // fall through to prefs
            }
        }
        long v = prefs.getLong(PREF_WAL_CHECKPOINT_BYTES, DEFAULT_WAL_CHECKPOINT_BYTES);
        return Math.max(0L, Math.min(v, 1_000_000_000L));
    }

/**
 * walArchiveKeep.
 *
 * @return TODO
 */
    public int walArchiveKeep() {
        String prop = System.getProperty(HistoryStorageConfig.PROP_WAL_ARCHIVE_KEEP);
        if (prop != null && !prop.isBlank()) {
            try {
                int v = Integer.parseInt(prop.trim());
                return Math.max(0, Math.min(v, 50));
            } catch (NumberFormatException ignored) {
                // fall through to prefs
            }
        }
        int v = prefs.getInt(PREF_WAL_ARCHIVE_KEEP, DEFAULT_WAL_ARCHIVE_KEEP);
        return Math.max(0, Math.min(v, 50));
    }

/**
 * checkpointOnStartup.
 *
 * @return TODO
 */
    public boolean checkpointOnStartup() {
        String prop = System.getProperty(HistoryStorageConfig.PROP_CHECKPOINT_ON_STARTUP);
        if (prop != null && !prop.isBlank()) {
            return Boolean.parseBoolean(prop.trim());
        }
        return prefs.getBoolean(PREF_CHECKPOINT_ON_STARTUP, true);
    }

/**
 * setWalCheckpointBytes.
 *
 * @param bytes TODO
 */
    public void setWalCheckpointBytes(long bytes) {
        prefs.putLong(PREF_WAL_CHECKPOINT_BYTES, Math.max(0L, bytes));
    }

/**
 * setWalArchiveKeep.
 *
 * @param keep TODO
 */
    public void setWalArchiveKeep(int keep) {
        prefs.putInt(PREF_WAL_ARCHIVE_KEEP, Math.max(0, keep));
    }

/**
 * setCheckpointOnStartup.
 *
 * @param enabled TODO
 */
    public void setCheckpointOnStartup(boolean enabled) {
        prefs.putBoolean(PREF_CHECKPOINT_ON_STARTUP, enabled);
    }

    
/**
 * add.
 *
 * @param entry TODO
 */
public void add(OperationHistoryEntry entry) {
    Objects.requireNonNull(entry, "entry");

    // Phase 3.9.1: ensure entry carries transactional metadata for UI grouping.
    String txId = UUID.randomUUID().toString();
    OperationHistoryEntry e2 = withBatch(entry, txId,
            entry.type() == null ? "" : entry.type().name(),
            0,
            1);

    // update UI model on FX thread (newest-first)
    fx(() -> entries.add(0, e2));

    // Phase 3.9.0: append as a single-entry WAL transaction for crash-safe recovery.
    io.submit(() -> {
        List<String> lines = new ArrayList<>(3);
        lines.add(toTxnBeginJson(txId, e2.batchLabel(), 1));
        lines.add(toJson(e2));
        lines.add(toTxnEndJson(txId));
        store.appendWalTransaction(lines);
    });

    // retention + compaction
    schedulePrune();
}


/**
 * Phase 3.9.0: Append multiple completed operations as a single durable WAL transaction.
 *
 * <p>Callers should provide entries in oldest-first order. The UI list is newest-first.</p>
 */
public void addBatch(List<OperationHistoryEntry> oldestFirst, String label) {
    if (oldestFirst == null || oldestFirst.isEmpty()) return;

    String txId = UUID.randomUUID().toString();
    String lbl = label == null ? "" : label;

    List<OperationHistoryEntry> enriched = new ArrayList<>(oldestFirst.size());
    for (int i = 0; i < oldestFirst.size(); i++) {
        OperationHistoryEntry e = oldestFirst.get(i);
        if (e == null) continue;
        enriched.add(withBatch(e, txId, lbl, i, oldestFirst.size()));
    }

    fx(() -> {
        for (int i = enriched.size() - 1; i >= 0; i--) {
            OperationHistoryEntry e = enriched.get(i);
            if (e != null) entries.add(0, e);
        }
    });

    io.submit(() -> {
        List<String> lines = new ArrayList<>(enriched.size() + 2);
        lines.add(toTxnBeginJson(txId, lbl, enriched.size()));
        for (OperationHistoryEntry e : enriched) {
            if (e == null) continue;
            lines.add(toJson(e));
        }
        lines.add(toTxnEndJson(txId));
        store.appendWalTransaction(lines);
    });

    schedulePrune();
}

/**
 * clear.
 *
 */
    public void clear() {
        fx(entries::clear);
        io.submit(store::clearAll);
    }

/**
 * exportJsonl.
 *
 * @param out TODO
 */
    public void exportJsonl(Path out) throws IOException {
        Objects.requireNonNull(out, "out");
        Files.createDirectories(out.getParent());
        // memory is source-of-truth; export from snapshot to include snapshot+wal+retention
        List<OperationHistoryEntry> snapshot = new ArrayList<>(entries);
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            // oldest-first on disk
            for (int i = snapshot.size() - 1; i >= 0; i--) {
                w.write(toJson(snapshot.get(i)));
                w.newLine();
            }
        }
    }

/**
 * exportJsonl.
 *
 * @param out TODO
 * @param subset TODO
 */
    public void exportJsonl(Path out, Collection<OperationHistoryEntry> subset) throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(subset, "subset");
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            for (var e : subset) {
                w.write(toJson(e));
                w.newLine();
            }
        }
    }

/**
 * exportJsonPretty.
 *
 * @param out TODO
 * @param subset TODO
 */
    public void exportJsonPretty(Path out, List<OperationHistoryEntry> subset) throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(subset, "subset");
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            w.write("[\n");
            for (int i = 0; i < subset.size(); i++) {
                w.write("  " + toJson(subset.get(i)));
                if (i < subset.size() - 1) w.write(",");
                w.write("\n");
            }
            w.write("]\n");
        }
    }

/**
 * exportCsv.
 *
 * @param out TODO
 * @param subset TODO
 */
    public void exportCsv(Path out, List<OperationHistoryEntry> subset) throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(subset, "subset");
        Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            w.write("operationId,type,status,startedAt,endedAt,durationMillis,processedBytes,totalBytes,targetSummary,message,verifyMode,verifyOk\n");
            for (var e : subset) {
                w.write(csv(e.operationId())); w.write(",");
                w.write(csv(e.type() == null ? "" : e.type().name())); w.write(",");
                w.write(csv(e.status() == null ? "" : e.status().name())); w.write(",");
                w.write(csv(e.startedAt() == null ? "" : e.startedAt().toString())); w.write(",");
                w.write(csv(e.endedAt() == null ? "" : e.endedAt().toString())); w.write(",");
                w.write(Long.toString(e.durationMillis())); w.write(",");
                w.write(Long.toString(e.processedBytes())); w.write(",");
                w.write(Long.toString(e.totalBytes())); w.write(",");
                w.write(csv(e.targetSummary())); w.write(",");
                w.write(csv(e.message())); w.write(",");
                w.write(csv(e.verifyMode())); w.write(",");
                w.write(Boolean.toString(e.verifyOk()));
                w.write("\n");
            }
        }
    }

/**
 * exportJsonPretty.
 *
 * @param out TODO
 */
    public void exportJsonPretty(Path out) throws IOException {
        Objects.requireNonNull(out, "out");
        Files.createDirectories(out.getParent());
        List<OperationHistoryEntry> snapshot = new ArrayList<>(entries);
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            w.write("[\n");
            for (int i = 0; i < snapshot.size(); i++) {
                w.write("  " + toJson(snapshot.get(i)));
                if (i < snapshot.size() - 1) w.write(",");
                w.write("\n");
            }
            w.write("]\n");
        }
    }

/**
 * exportCsv.
 *
 * @param out TODO
 */
    public void exportCsv(Path out) throws IOException {
        Objects.requireNonNull(out, "out");
        Files.createDirectories(out.getParent());
        List<OperationHistoryEntry> snapshot = new ArrayList<>(entries);
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            w.write("operationId,type,status,startedAt,endedAt,durationMillis,processedBytes,totalBytes,targetSummary,message,verifyMode,verifyOk\n");
            for (var e : snapshot) {
                w.write(csv(e.operationId())); w.write(",");
                w.write(csv(e.type() == null ? "" : e.type().name())); w.write(",");
                w.write(csv(e.status() == null ? "" : e.status().name())); w.write(",");
                w.write(csv(e.startedAt() == null ? "" : e.startedAt().toString())); w.write(",");
                w.write(csv(e.endedAt() == null ? "" : e.endedAt().toString())); w.write(",");
                w.write(Long.toString(e.durationMillis())); w.write(",");
                w.write(Long.toString(e.processedBytes())); w.write(",");
                w.write(Long.toString(e.totalBytes())); w.write(",");
                w.write(csv(e.targetSummary())); w.write(",");
                w.write(csv(e.message())); w.write(",");
                w.write(csv(e.verifyMode())); w.write(",");
                w.write(Boolean.toString(e.verifyOk()));
                w.write("\n");
            }
        }
    }

/**
 * csv.
 *
 * @param s TODO
 * @return TODO
 */
    private static String csv(String s) {
        if (s == null) return "";
        String t = s.replace("\r", " ").replace("\n", " ");
        if (t.contains(",") || t.contains("\"") ) {
            return "\"" + t.replace("\"", "\"\"") + "\"";
        }
        return t;
    }

/**
 * loadExistingBestEffort.
 *
 */
    private void loadExistingBestEffort() {
        io.submit(() -> {
            try {
                List<String> lines = store.loadAllJsonLinesBestEffort();
                lastLoadAt = Instant.now();

                lastLoadTotalLines = lines == null ? 0 : lines.size();
                
List<OperationHistoryEntry> parsed = new ArrayList<>();
int badLines = 0;

// Phase 3.9.0/3.9.1: Transaction-aware parse. Apply only fully closed BEGIN/END blocks.
String currentTxId = null;
String currentTxLabel = "";
int currentTxCount = 0;
int currentTxIndex = 0;
List<OperationHistoryEntry> txnBuffer = null;

for (String line : lines) {
    if (line == null) continue;
    String t = line.trim();
    if (t.isEmpty()) continue;

    String rt = get(t, "recordType");
    if (rt != null && !rt.isBlank()) {
        if ("BEGIN_TXN".equals(rt)) {
            // If a previous txn never ended, drop it (best-effort) and count it as bad.
            if (currentTxId != null && txnBuffer != null && !txnBuffer.isEmpty()) {
                badLines += Math.max(1, txnBuffer.size());
            }
            currentTxId = get(t, "txId");
                currentTxLabel = get(t, "label");
                currentTxCount = (int) getLong(t, "count");
                currentTxIndex = 0;
            txnBuffer = new ArrayList<>();
            continue;
        }
        if ("END_TXN".equals(rt)) {
            String txId = get(t, "txId");
            if (currentTxId != null && txId != null && txId.equals(currentTxId)) {
                parsed.addAll(txnBuffer); // oldest-first
            } else {
                badLines++;
            }
            currentTxId = null;
            currentTxLabel = "";
            currentTxCount = 0;
            currentTxIndex = 0;
            txnBuffer = null;
            continue;
        }

        // Unknown marker type
        badLines++;
        continue;
    }

    OperationHistoryEntry e = fromJsonBestEffort(t);
    if (e != null) {
        if (currentTxId != null && txnBuffer != null) {
            // If older entries don't carry batch metadata, fill it from txn markers.
            OperationHistoryEntry e2 = (e.batchId() != null && !e.batchId().isBlank())
                    ? e
                    : withBatch(e, currentTxId, currentTxLabel, currentTxIndex, currentTxCount);
            txnBuffer.add(e2);
            currentTxIndex++;
        } else {
            // Legacy single-entry line
            parsed.add(e);
        }
    } else {
        badLines++;
    }
}

// If we ended mid-transaction, drop buffered entries (crash mid-append).
if (currentTxId != null && txnBuffer != null && !txnBuffer.isEmpty()) {
    badLines += Math.max(1, txnBuffer.size());
}

lastLoadBadLines = badLines;
                if (badLines > 0) {
                    lastLoadMessage = "Recovered with warnings (skipped " + badLines + " corrupted line(s))";
                    LOG.log(Level.WARNING, "Operation history: skipped {0} corrupted/unreadable JSONL lines during load.", badLines);
                } else {
                    lastLoadMessage = "OK";
                }
                // parsed is oldest-first; load newest-first into memory
                for (int i = parsed.size() - 1; i >= 0; i--) {
                    OperationHistoryEntry e = parsed.get(i);
                    fx(() -> entries.add(e));
                }

                // Phase 3.8.2: checkpoint if WAL is large, or if we observed corruption.
                boolean checkpoint = (badLines > 0) || (checkpointOnStartup() && store.shouldCheckpointByWalBytes(walCheckpointBytes()));
                lastLoadCheckpointed = checkpoint;
                if (checkpoint) {
                    lastCheckpointAt = Instant.now();
                    lastCheckpointReason = (badLines > 0) ? "startup-recovery" : "startup-wal-large";

                    // Use parsed data as source-of-truth to avoid cross-thread list access.
                    List<OperationHistoryEntry> newestFirst = new ArrayList<>(parsed.size());
                    for (int i = parsed.size() - 1; i >= 0; i--) newestFirst.add(parsed.get(i));
                    store.compactToSnapshot(newestFirst, OperationHistoryService::toJson, true, walArchiveKeep());
                }

                schedulePrune();
            } catch (Throwable ignored) {
                // best-effort
            }
        });
    }

/**
 * schedulePrune.
 *
 */
    private void schedulePrune() {
        if (!pruneScheduled.compareAndSet(false, true)) return;
        io.submit(() -> {
            try {
                pruneInternal();
            } finally {
                pruneScheduled.set(false);
            }
        });
    }

/**
 * pruneInternal.
 *
 */
    private void pruneInternal() {
        try {
            int maxEntries = maxEntries();
            int maxDays = maxDays();
            Instant cutoff = Instant.now().minus(maxDays, ChronoUnit.DAYS);

            // snapshot newest-first (observable list is newest-first)
            List<OperationHistoryEntry> snap = new ArrayList<>(entries);
            List<OperationHistoryEntry> kept = new ArrayList<>();
            for (var e : snap) {
                if (e == null) continue;
                Instant ts = e.startedAt() == null ? e.endedAt() : e.startedAt();
                if (ts != null && ts.isBefore(cutoff)) continue;
                kept.add(e);
                if (kept.size() >= maxEntries) break;
            }

            // update in-memory list (FX)
            fx(() -> entries.setAll(kept));

            // Phase 3.8.2: only rewrite snapshot when we actually pruned, or WAL is large.
            boolean pruned = kept.size() != snap.size();
            boolean walLarge = store.shouldCheckpointByWalBytes(walCheckpointBytes());
            if (walLarge || pruned) {
                lastCheckpointAt = Instant.now();
                lastCheckpointReason = walLarge ? (pruned ? "prune+wal-large" : "wal-large") : "prune";
            }
            if (pruned || walLarge) {
                store.compactToSnapshot(kept, OperationHistoryService::toJson, walLarge, walArchiveKeep());
            }
        } catch (Throwable ignored) {
            // best-effort
        }
    }

/**
 * fx.
 *
 * @param r TODO
 */
    private static void fx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    // Minimal JSON (no external deps). Strings are escaped for backslash/quote/newline.
/**
 * esc.
 *
 * @param s TODO
 * @return TODO
 */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // requestSources are encoded as a single string separated by '\n' (escaped).
/**
 * joinLines.
 *
 * @param lines TODO
 * @return TODO
 */
    private static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        return String.join("\n", lines);
    }
    private static List<String> splitLines(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.asList(s.split("\\n"));
    }

/**
 * withBatch.
 *
 * @param e TODO
 * @param batchId TODO
 * @param batchLabel TODO
 * @param batchIndex TODO
 * @param batchSize TODO
 * @return TODO
 */
    private static OperationHistoryEntry withBatch(OperationHistoryEntry e, String batchId, String batchLabel, int batchIndex, int batchSize) {
        if (e == null) return null;
        return new OperationHistoryEntry(
                e.operationId(),
                e.type(),
                e.status(),
                e.startedAt(),
                e.endedAt(),
                e.durationMillis(),
                e.processedBytes(),
                e.totalBytes(),
                e.sourcesSummary(),
                e.targetSummary(),
                e.verifyMode(),
                e.verifyOk(),
                e.message(),
                e.requestSources(),
                e.requestTargetDirectory(),
                e.requestNewName(),
                e.requestOverwrite(),
                e.requestSendToTrash(),
                e.originType(),
                e.originTemplateId(),
                e.originScheduleId(),
                e.originTriggerType(),
                e.originRecurrenceMinutes(),
                e.originRetryAttempt(),
                batchId == null ? "" : batchId,
                batchLabel == null ? "" : batchLabel,
                Math.max(0, batchIndex),
                Math.max(0, batchSize),
                e.commandId()
        );
    }

/**
 * toJson.
 *
 * @param e TODO
 * @return TODO
 */
    private static String toJson(OperationHistoryEntry e) {
        return "{" 
                + "\"operationId\":\"" + esc(e.operationId()) + "\"," 
                + "\"type\":\"" + esc(e.type() == null ? "" : e.type().name()) + "\"," 
                + "\"status\":\"" + esc(e.status() == null ? "" : e.status().name()) + "\"," 
                + "\"startedAt\":\"" + esc(e.startedAt() == null ? "" : e.startedAt().toString()) + "\"," 
                + "\"endedAt\":\"" + esc(e.endedAt() == null ? "" : e.endedAt().toString()) + "\"," 
                + "\"durationMillis\":" + e.durationMillis() + "," 
                + "\"processedBytes\":" + e.processedBytes() + "," 
                + "\"totalBytes\":" + e.totalBytes() + "," 
                + "\"sourcesSummary\":\"" + esc(e.sourcesSummary()) + "\"," 
                + "\"targetSummary\":\"" + esc(e.targetSummary()) + "\"," 
                + "\"verifyMode\":\"" + esc(e.verifyMode()) + "\"," 
                + "\"verifyOk\":" + e.verifyOk() + "," 
                + "\"message\":\"" + esc(e.message()) + "\"," 
                + "\"requestSources\":\"" + esc(joinLines(e.requestSources())) + "\"," 
                + "\"requestTargetDirectory\":\"" + esc(e.requestTargetDirectory()) + "\"," 
                + "\"requestNewName\":\"" + esc(e.requestNewName()) + "\"," 
                + "\"requestOverwrite\":" + e.requestOverwrite() + "," 
                + "\"requestSendToTrash\":" + e.requestSendToTrash() + ","
                + "\"originType\":\"" + esc(e.originType()) + "\"," 
                + "\"originTemplateId\":\"" + esc(e.originTemplateId()) + "\"," 
                + "\"originScheduleId\":\"" + esc(e.originScheduleId()) + "\"," 
                + "\"originTriggerType\":\"" + esc(e.originTriggerType()) + "\"," 
                + "\"originRecurrenceMinutes\":" + e.originRecurrenceMinutes() + "," 
                + "\"originRetryAttempt\":" + e.originRetryAttempt() + "," 
                + "\"batchId\":\"" + esc(e.batchId()) + "\"," 
                + "\"batchLabel\":\"" + esc(e.batchLabel()) + "\"," 
                + "\"batchIndex\":" + e.batchIndex() + "," 
                + "\"batchSize\":" + e.batchSize() + ","
                + "\"commandId\":\"" + esc(e.commandId()) + "\""
                + "}";
    }


// ---- Phase 3.9.0: Transactional batching in WAL ----
// WAL records now support BEGIN/END transaction markers. Recovery only applies fully closed transactions.
/**
 * toTxnBeginJson.
 *
 * @param txId TODO
 * @param label TODO
 * @param count TODO
 * @return TODO
 */
private static String toTxnBeginJson(String txId, String label, int count) {
    return "{"
            + "\"recordType\":\"BEGIN_TXN\","
            + "\"txId\":\"" + esc(txId) + "\","
            + "\"label\":\"" + esc(label == null ? "" : label) + "\","
            + "\"count\":" + count
            + "}";
}

/**
 * toTxnEndJson.
 *
 * @param txId TODO
 * @return TODO
 */
private static String toTxnEndJson(String txId) {
    return "{"
            + "\"recordType\":\"END_TXN\","
            + "\"txId\":\"" + esc(txId) + "\""
            + "}";
}



/**
 * fromJsonBestEffort.
 *
 * @param json TODO
 * @return TODO
 */
    private static OperationHistoryEntry fromJsonBestEffort(String json) {
        try {
            String opId = get(json, "operationId");
            String type = get(json, "type");
            String status = get(json, "status");
            String startedAt = get(json, "startedAt");
            String endedAt = get(json, "endedAt");
            long duration = getLong(json, "durationMillis");
            long processed = getLong(json, "processedBytes");
            long total = getLong(json, "totalBytes");
            String src = get(json, "sourcesSummary");
            String tgt = get(json, "targetSummary");
            String verifyMode = get(json, "verifyMode");
            boolean verifyOk = getBool(json, "verifyOk");
            String msg = get(json, "message");

            // 3.7.1 fields (may be absent)
            String reqSources = get(json, "requestSources");
            String reqTargetDir = get(json, "requestTargetDirectory");
            String reqNewName = get(json, "requestNewName");
            boolean reqOverwrite = getBool(json, "requestOverwrite");
            boolean reqTrash = getBool(json, "requestSendToTrash");

            // 5.5.1 origin/audit fields (may be absent)
            String originType = get(json, "originType");
            String originTemplateId = get(json, "originTemplateId");
            String originScheduleId = get(json, "originScheduleId");
            String originTriggerType = get(json, "originTriggerType");
            long originRecurrenceMinutes = getLong(json, "originRecurrenceMinutes");
            int originRetryAttempt = (int) getLong(json, "originRetryAttempt");

            // 3.9.1 transactional metadata (may be absent)
            String batchId = get(json, "batchId");
            String batchLabel = get(json, "batchLabel");
            int batchIndex = (int) getLong(json, "batchIndex");
            int batchSize = (int) getLong(json, "batchSize");

            // 4.0.5 command link (may be absent)
            String commandId = get(json, "commandId");

            FileOperationType t =
                    (type == null || type.isBlank()) ? null : FileOperationType.valueOf(type);
            OperationStatus st =
                    (status == null || status.isBlank()) ? null : OperationStatus.valueOf(status);

            Instant sAt = (startedAt == null || startedAt.isBlank()) ? null : Instant.parse(startedAt);
            Instant eAt = (endedAt == null || endedAt.isBlank()) ? null : Instant.parse(endedAt);

            return new OperationHistoryEntry(opId, t, st, sAt, eAt, duration, processed, total, src, tgt, verifyMode, verifyOk, msg,
                    splitLines(reqSources), reqTargetDir, reqNewName, reqOverwrite, reqTrash,
                    originType, originTemplateId, originScheduleId, originTriggerType, Math.max(0L, originRecurrenceMinutes), Math.max(0, originRetryAttempt),
                    batchId, batchLabel, Math.max(0, batchIndex), Math.max(0, batchSize), commandId);
        } catch (Throwable ignored) {
            return null;
        }
    }

/**
 * get.
 *
 * @param json TODO
 * @param key TODO
 * @return TODO
 */
    private static String get(String json, String key) {
        String pat = "\"" + key + "\":\"";
        int i = json.indexOf(pat);
        if (i < 0) return "";
        int start = i + pat.length();
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int p = start; p < json.length(); p++) {
            char c = json.charAt(p);
            if (esc) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    default -> sb.append(c);
                }
                esc = false;
                continue;
            }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }

/**
 * getLong.
 *
 * @param json TODO
 * @param key TODO
 * @return TODO
 */
    private static long getLong(String json, String key) {
        String pat = "\"" + key + "\":";
        int i = json.indexOf(pat);
        if (i < 0) return 0L;
        int start = i + pat.length();
        int end = start;
        while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) end++;
        try { return Long.parseLong(json.substring(start, end)); } catch (Exception e) { return 0L; }
    }

/**
 * getBool.
 *
 * @param json TODO
 * @param key TODO
 * @return TODO
 */
    private static boolean getBool(String json, String key) {
        String pat = "\"" + key + "\":";
        int i = json.indexOf(pat);
        if (i < 0) return false;
        int start = i + pat.length();
        String tail = json.substring(start).trim().toLowerCase(Locale.ROOT);
        return tail.startsWith("true");
    }

    @Override
/**
 * close.
 *
 */
    public void close() {
        // best-effort final compaction on shutdown
        try {
            io.submit(this::pruneInternal).get(350, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
            // ignore
        }

        io.shutdown();
        try { io.awaitTermination(250, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { }
    }
}
