package com.fileexplorer.service.ops.history.persistence;

import com.fileexplorer.service.ops.history.OperationHistoryEntry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Phase 3.8.0: History persistence engine (snapshot + WAL).
 *
 * <p>
 * Model:
 * <ul>
 *   <li>Snapshot: operation-history.snapshot.jsonl (retained, rewritten on prune)</li>
 *   <li>WAL: operation-history.wal.jsonl (append-only between snapshots)</li>
 * </ul>
 *
 * On startup, snapshot is loaded first, then WAL is replayed.
 * Snapshot compaction truncates the WAL after a successful rewrite.
 */
public final class OperationHistoryStore {

    private final Path baseDir;
    private final Path snapshotFile;
    private final Path walFile;

    private static final String SNAPSHOT_NAME = "operation-history.snapshot.jsonl";
    private static final String WAL_NAME = "operation-history.wal.jsonl";
    private static final String WAL_ARCHIVE_PREFIX = "operation-history.wal.archive.";
    private static final String WAL_ARCHIVE_SUFFIX = ".jsonl";

/**
 * OperationHistoryStore.
 *
 * @param baseDir TODO
 * @return TODO
 */
    public OperationHistoryStore(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir");
        this.snapshotFile = baseDir.resolve(SNAPSHOT_NAME);
        this.walFile = baseDir.resolve(WAL_NAME);
    }

    public Path baseDir() { return baseDir; }
    public Path snapshotFile() { return snapshotFile; }
    public Path walFile() { return walFile; }

/**
 * ensureDirectories.
 *
 */
    public void ensureDirectories() throws IOException {
        Files.createDirectories(baseDir);
    }

/**
 * walSizeBytesBestEffort.
 *
 * @return TODO
 */
    public long walSizeBytesBestEffort() {
        try {
            if (!Files.exists(walFile)) return 0L;
            return Files.size(walFile);
        } catch (IOException ignored) {
            return 0L;
        }
    }

/**
 * shouldCheckpointByWalBytes.
 *
 * @param maxWalBytes TODO
 * @return TODO
 */
    public boolean shouldCheckpointByWalBytes(long maxWalBytes) {
        if (maxWalBytes <= 0) return false;
        return walSizeBytesBestEffort() >= maxWalBytes;
    }

    /**
     * Best-effort migration from 3.7.x legacy file into snapshot if no snapshot exists yet.
     */
    public void migrateLegacyIfPresent(Path legacyJsonl) {
        try {
            if (Files.exists(snapshotFile)) return;
            if (legacyJsonl == null || !Files.exists(legacyJsonl)) return;
            ensureDirectories();
            Files.copy(legacyJsonl, snapshotFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    /** Append a single already-serialized JSON object line. */
    public void appendWalLine(String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank()) return;
        try {
            ensureDirectories();
            try (BufferedWriter w = Files.newBufferedWriter(walFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                w.write(jsonLine);
                w.newLine();
            }
        } catch (IOException ignored) {
            // best-effort; never fail the app for audit persistence
        }
    }

/**
 * Append a transaction to the WAL: BEGIN marker, N entry lines, END marker.
 *
 * <p>Recovery only applies entries from fully closed transactions. This prevents partial writes
 * (crash mid-append) from polluting the in-memory history.</p>
 */
public void appendWalTransaction(List<String> jsonLines) {
    if (jsonLines == null || jsonLines.isEmpty()) return;
    try {
        ensureDirectories();
        try (BufferedWriter w = Files.newBufferedWriter(walFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            for (String line : jsonLines) {
                if (line == null || line.isBlank()) continue;
                w.write(line);
                w.newLine();
            }
        }
    } catch (IOException ignored) {
        // best-effort
    }
}


    /**
     * Load snapshot + WAL lines (oldest-first in files). Returns a newest-first list.
     */
    public List<String> loadAllJsonLinesBestEffort() {
        List<String> all = new ArrayList<>();
        try {
            if (Files.exists(snapshotFile)) {
                all.addAll(Files.readAllLines(snapshotFile, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) { }

        try {
            if (Files.exists(walFile)) {
                all.addAll(Files.readAllLines(walFile, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) { }

        // files are oldest-first; keep that ordering for parsing, caller can reverse
        return all;
    }

    /**
     * Rewrite snapshot from the provided newest-first list (writes oldest-first), then truncate WAL.
     */
    public void compactToSnapshot(List<OperationHistoryEntry> newestFirst,
                                 java.util.function.Function<OperationHistoryEntry, String> serializer,
                                 boolean rotateWal,
                                 int walArchiveKeep) {
        Objects.requireNonNull(newestFirst, "newestFirst");
        Objects.requireNonNull(serializer, "serializer");

        try {
            ensureDirectories();
            // write snapshot oldest-first for readability and append semantics
            try (BufferedWriter w = Files.newBufferedWriter(snapshotFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                for (int i = newestFirst.size() - 1; i >= 0; i--) {
                    String line = serializer.apply(newestFirst.get(i));
                    if (line == null || line.isBlank()) continue;
                    w.write(line);
                    w.newLine();
                }
            }

            // truncate WAL after successful snapshot write
            truncateWalBestEffort(rotateWal, walArchiveKeep);
        } catch (IOException ignored) {
            // best-effort
        }
    }

/**
 * compactToSnapshot.
 *
 * @param newestFirst TODO
 * @param serializer TODO
 */
    public void compactToSnapshot(List<OperationHistoryEntry> newestFirst, java.util.function.Function<OperationHistoryEntry, String> serializer) {
        compactToSnapshot(newestFirst, serializer, false, 0);
    }

    public void truncateWalBestEffort(boolean rotateWal, int walArchiveKeep) {
        try {
            if (!Files.exists(walFile)) return;
            if (rotateWal) {
                ensureDirectories();
                String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
                // file-system friendly
                ts = ts.replace(':', '-');
                Path archived = baseDir.resolve(WAL_ARCHIVE_PREFIX + ts + WAL_ARCHIVE_SUFFIX);
                try {
                    Files.move(walFile, archived, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveFail) {
                    // If move fails, fall back to delete.
                    try { Files.deleteIfExists(walFile); } catch (IOException ignored) { }
                }
                pruneWalArchivesBestEffort(walArchiveKeep);
                return;
            }

            try { Files.deleteIfExists(walFile); } catch (IOException ignored) { }
        } catch (IOException ignored) {
            // best-effort
        }
    }

/**
 * pruneWalArchivesBestEffort.
 *
 * @param keep TODO
 */
    private void pruneWalArchivesBestEffort(int keep) {
        if (keep <= 0) return;
        try {
            if (!Files.isDirectory(baseDir)) return;
            List<Path> archives;
            try (var stream = Files.list(baseDir)) {
                archives = stream
                        .filter(p -> {
                            String n = p.getFileName().toString();
                            return n.startsWith(WAL_ARCHIVE_PREFIX) && n.endsWith(WAL_ARCHIVE_SUFFIX);
                        })
                        .sorted(Comparator.comparing(Path::toString).reversed())
                        .collect(Collectors.toList());
            }
            for (int i = keep; i < archives.size(); i++) {
                try { Files.deleteIfExists(archives.get(i)); } catch (IOException ignored) { }
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

/**
 * clearAll.
 *
 */
    public void clearAll() {
        try { Files.deleteIfExists(snapshotFile); } catch (IOException ignored) { }
        try { Files.deleteIfExists(walFile); } catch (IOException ignored) { }
    }
}
