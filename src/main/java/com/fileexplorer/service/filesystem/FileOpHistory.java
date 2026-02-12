package com.fileexplorer.service.filesystem;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory history of reversible file operations.
 *
 * <p>Phase 3.6.2 initial scope: Undo for RENAME and MOVE (cut/paste).</p>
 */
public final class FileOpHistory {

    public interface UndoEntry {
        String label();
        void undo(FileOperationsService ops);
    }

    private final Deque<UndoEntry> stack = new ArrayDeque<>();

    public boolean canUndo() {
        return !stack.isEmpty();
    }

    public UndoEntry popUndo() {
        return stack.pollFirst();
    }

    public void recordRename(Path from, Path to) {
        if (from == null || to == null) return;
        stack.addFirst(new RenameEntry(from, to));
    }

    public void recordMove(List<Path> from, List<Path> to) {
        if (from == null || to == null) return;
        if (from.isEmpty() || to.isEmpty()) return;

        int n = Math.min(from.size(), to.size());
        List<Path> f = new ArrayList<>(n);
        List<Path> t = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Path a = from.get(i);
            Path b = to.get(i);
            if (a == null || b == null) continue;
            f.add(a);
            t.add(b);
        }
        if (f.isEmpty() || t.isEmpty()) return;

        stack.addFirst(new MoveEntry(f, t));
    }

    private static final class RenameEntry implements UndoEntry {
        private final Path from;
        private final Path to;

        private RenameEntry(Path from, Path to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String label() {
            return "Rename";
        }

        @Override
        public void undo(FileOperationsService ops) {
            Objects.requireNonNull(ops, "ops");
            String originalName = (from.getFileName() != null) ? from.getFileName().toString() : null;
            if (originalName == null || originalName.isBlank()) return;
            ops.rename(to, originalName);
        }
    }

    private static final class MoveEntry implements UndoEntry {
        private final List<Path> from;
        private final List<Path> to;

        private MoveEntry(List<Path> from, List<Path> to) {
            this.from = List.copyOf(from);
            this.to = List.copyOf(to);
        }

        @Override
        public String label() {
            return "Move";
        }

        @Override
        public void undo(FileOperationsService ops) {
            Objects.requireNonNull(ops, "ops");

            Map<Path, List<Path>> byTargetDir = new HashMap<>();
            for (int i = 0; i < Math.min(from.size(), to.size()); i++) {
                Path original = from.get(i);
                Path current = to.get(i);
                if (original == null || current == null) continue;
                Path targetDir = original.getParent();
                if (targetDir == null) continue;
                byTargetDir.computeIfAbsent(targetDir, k -> new ArrayList<>()).add(current);
            }

            for (Map.Entry<Path, List<Path>> e : byTargetDir.entrySet()) {
                Path targetDir = e.getKey();
                List<Path> sources = e.getValue();
                if (sources == null || sources.isEmpty()) continue;
                ops.move(sources, targetDir, FileOperationsService.ConflictPolicy.RENAME);
            }
        }
    }
}
