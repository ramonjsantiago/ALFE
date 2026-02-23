package com.fileexplorer.service.ops.rollback;

import com.fileexplorer.service.ops.OperationHandle;
import com.fileexplorer.service.ops.journal.OperationJournalRecordType;
import com.fileexplorer.service.ops.journal.OperationJournalService;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Phase 5.0.0: best-effort rollback engine.
 *
 * <p>We only attempt conservative actions:
 * <ul>
 *   <li>Delete created destinations (COPY)</li>
 *   <li>Move destination back to source (MOVE)</li>
 *   <li>Restore backed-up destination for overwrite (files only)</li>
 * </ul>
 *
 * <p>Rollback is applied in reverse order of successful item completion.</p>
 */
public final class OperationRollbackService {

    private final OperationJournalService journal;

/**
 * OperationRollbackService.
 *
 * @param journal TODO
 * @return TODO
 */
    public OperationRollbackService(OperationJournalService journal) {
        this.journal = (journal == null) ? new OperationJournalService() : journal;
    }

    /**
     * Apply rollback steps (reverse order). Returns true if rollback completed without internal exceptions.
     */
    public boolean rollback(OperationHandle handle, Deque<RollbackStep> steps) {
        if (handle == null || steps == null || steps.isEmpty()) return true;

        String opId = handle.id();
        try {
            journal.append(opId, OperationJournalRecordType.ROLLBACK_START, map("reason", "auto"));
        } catch (Throwable ignored) {}

        boolean ok = true;
        Deque<RollbackStep> stack = new ArrayDeque<>(steps);
        while (!stack.isEmpty()) {
            RollbackStep s = stack.removeLast();
            try {
                applyStep(s);
                try {
                    journal.append(opId, OperationJournalRecordType.ROLLBACK_ITEM_OK, stepFields(s));
                } catch (Throwable ignored) {}
            } catch (Throwable ex) {
                ok = false;
                try {
                    Map<String, String> f = stepFields(s);
                    f.put("error", ex.getClass().getSimpleName() + ":" + (ex.getMessage() == null ? "" : ex.getMessage()));
                    journal.append(opId, OperationJournalRecordType.ROLLBACK_ITEM_FAIL, f);
                } catch (Throwable ignored) {}
            }
        }

        try {
            journal.append(opId, OperationJournalRecordType.ROLLBACK_COMPLETE, map("ok", Boolean.toString(ok)));
        } catch (Throwable ignored) {}
        return ok;
    }

/**
 * applyStep.
 *
 * @param s TODO
 */
    private void applyStep(RollbackStep s) throws IOException {
        if (s == null || s.type() == null) return;

        switch (s.type()) {
            case DELETE_CREATED -> {
                Path created = s.primary();
                if (created == null) return;
                if (!Files.exists(created)) return;
                if (Files.isDirectory(created)) {
                    deleteDirectoryRecursive(created);
                } else {
                    Files.deleteIfExists(created);
                }
            }
            case MOVE_BACK -> {
                Path from = s.primary();
                Path to = s.secondary();
                if (from == null || to == null) return;
                if (!Files.exists(from)) return;
                Files.createDirectories(to.getParent());
                Files.move(from, to, REPLACE_EXISTING);
            }
            case RESTORE_BACKUP -> {
                Path backup = s.primary();
                Path dest = s.secondary();
                if (backup == null || dest == null) return;
                if (!Files.exists(backup)) return;
                Files.createDirectories(dest.getParent());
                // conservative: only restore as a move (best-effort)
                Files.move(backup, dest, REPLACE_EXISTING);
            }
        }
    }

/**
 * deleteDirectoryRecursive.
 *
 * @param dir TODO
 */
    private static void deleteDirectoryRecursive(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.deleteIfExists(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

/**
 * map.
 *
 * @param k TODO
 * @param v TODO
 * @return TODO
 */
    private static Map<String, String> map(String k, String v) {
        HashMap<String, String> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

/**
 * stepFields.
 *
 * @param s TODO
 * @return TODO
 */
    private static Map<String, String> stepFields(RollbackStep s) {
        HashMap<String, String> f = new HashMap<>();
        f.put("type", s.type().name());
        f.put("primary", s.primary() == null ? "" : s.primary().toString());
        f.put("secondary", s.secondary() == null ? "" : s.secondary().toString());
        if (s.note() != null && !s.note().isBlank()) f.put("note", s.note());
        return f;
    }
}
