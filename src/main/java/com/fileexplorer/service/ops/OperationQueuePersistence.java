package com.fileexplorer.service.ops;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple, dependency-free persistence for the operation queue.
 *
 * <p>Stores non-finished operations so the user can resume after restart.</p>
 *
 * <p>Phase 3.6.7.3:
 * - Persist whether an operation was RUNNING at shutdown, so recovery can optionally "resume queued only".</p>
 *
 * <p>Phase 6.0.0:
 * - Persist a stable operation id and last-known status for stronger crash/restart behavior and better diagnostics.</p>
 */
final class OperationQueuePersistence {

    static final class SavedOperation {
        private final String operationId;          // may be null/blank for legacy persisted lines
        private final OperationStatus status;      // may be null for legacy persisted lines
        private final FileOperationRequest request;
        private final boolean wasRunning;

        SavedOperation(String operationId, OperationStatus status, FileOperationRequest request, boolean wasRunning) {
            this.operationId = operationId;
            this.status = status;
            this.request = request;
            this.wasRunning = wasRunning;
        }

        String operationId() { return operationId; }
        OperationStatus status() { return status; }
        FileOperationRequest request() { return request; }
        boolean wasRunning() { return wasRunning; }
    }

    private static final String DIR_NAME = ".fileexplorer";
    private static final String FILE_NAME = "operation-queue.tsv";

    Path storePath() {
        Path dir = Paths.get(System.getProperty("user.home"), DIR_NAME);
        return dir.resolve(FILE_NAME);
    }

    void saveSaved(List<SavedOperation> ops) {
        Path file = storePath();
        try {
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>(ops.size());
            lines.add("# FileExplorer operation queue persistence (best-effort)");
            lines.add("# format=v4: opId\tstatus\ttype\tsourcesCsv\ttargetDir\tnewName\toverwrite\tskipConflicts\tsendToTrash\twasRunning");
            for (SavedOperation op : ops) {
                if (op == null || op.request == null) continue;
                lines.add(encode(op.operationId, op.status, op.request, op.wasRunning));
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Persistence is best-effort; never break the app.
        }
    }

    List<SavedOperation> loadSaved() {
        Path file = storePath();
        if (!Files.exists(file)) return List.of();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<SavedOperation> out = new ArrayList<>();
            for (String line : lines) {
                if (line == null) continue;
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                SavedOperation r = decodeSaved(t);
                if (r != null) out.add(r);
            }
            return out;
        } catch (IOException ignored) {
            return List.of();
        }
    }

    void clear() {
        Path file = storePath();
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    // v4 format:
    // opId \t status \t type \t sourcesCsv \t targetDir \t newName \t overwrite \t skipConflicts \t sendToTrash \t wasRunning
    //
    // Backward compatible:
    // v3 (8 columns): type \t sourcesCsv \t targetDir \t newName \t overwrite \t skipConflicts \t sendToTrash \t wasRunning
    // v2 (7 columns): type \t sourcesCsv \t targetDir \t newName \t overwrite \t sendToTrash \t wasRunning (skipConflicts defaults to 0)
    // v1 (6 columns): type \t sourcesCsv \t targetDir \t newName \t overwrite \t sendToTrash (wasRunning defaults to 0)

    private static String encode(String opId, OperationStatus status, FileOperationRequest r, boolean wasRunning) {
        String opIdEnc = esc(opId == null ? "" : opId);
        String stEnc = esc(status == null ? "" : status.name());

        String type = esc(r.type().name());
        String sources = esc(joinPaths(r.sources()));
        String target = esc(r.targetDirectory() == null ? "" : r.targetDirectory().toString());
        String newName = esc(r.newName() == null ? "" : r.newName());
        String overwrite = r.overwrite() ? "1" : "0";
        String skip = r.skipConflicts() ? "1" : "0";
        String trash = r.sendToTrash() ? "1" : "0";
        String running = wasRunning ? "1" : "0";

        return opIdEnc + "\t" + stEnc + "\t" + type + "\t" + sources + "\t" + target + "\t" + newName + "\t" +
                overwrite + "\t" + skip + "\t" + trash + "\t" + running;
    }

    private static SavedOperation decodeSaved(String line) {
        String[] parts = line.split("\t", -1);

        // v4
        if (parts.length >= 10) {
            String opId = unesc(parts[0]);
            if (opId.isEmpty()) opId = null;

            OperationStatus status = safeStatus(unesc(parts[1]));

            FileOperationType type;
            try {
                type = FileOperationType.valueOf(unesc(parts[2]));
            } catch (Exception e) {
                return null;
            }

            List<Path> sources = splitPaths(unesc(parts[3]));
            Path target = unesc(parts[4]).isEmpty() ? null : Paths.get(unesc(parts[4]));
            String newName = unesc(parts[5]);
            if (newName.isEmpty()) newName = null;

            boolean overwrite = "1".equals(parts[6]);
            boolean skipConflicts = "1".equals(parts[7]);
            boolean sendToTrash = "1".equals(parts[8]);
            boolean wasRunning = "1".equals(parts[9]);

            FileOperationRequest req = new FileOperationRequest(type, sources, target, newName, overwrite, skipConflicts, sendToTrash);
            return new SavedOperation(opId, status, req, wasRunning);
        }

        // legacy (v1-v3)
        if (parts.length < 6) return null;

        FileOperationType type;
        try {
            type = FileOperationType.valueOf(unesc(parts[0]));
        } catch (Exception e) {
            return null;
        }

        List<Path> sources = splitPaths(unesc(parts[1]));
        Path target = unesc(parts[2]).isEmpty() ? null : Paths.get(unesc(parts[2]));
        String newName = unesc(parts[3]);
        if (newName.isEmpty()) newName = null;
        boolean overwrite = "1".equals(parts[4]);
        boolean skipConflicts = false;
        boolean sendToTrash;
        boolean wasRunning;

        if (parts.length >= 8) {
            // v3
            skipConflicts = "1".equals(parts[5]);
            sendToTrash = "1".equals(parts[6]);
            wasRunning = "1".equals(parts[7]);
        } else {
            // v1/v2
            sendToTrash = "1".equals(parts[5]);
            wasRunning = parts.length >= 7 && "1".equals(parts[6]);
        }

        FileOperationRequest req = new FileOperationRequest(type, sources, target, newName, overwrite, skipConflicts, sendToTrash);

        // status/opId unknown in legacy format; wasRunning preserved
        return new SavedOperation(null, null, req, wasRunning);
    }

    private static OperationStatus safeStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OperationStatus.valueOf(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String joinPaths(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(paths.get(i).toString());
        }
        return sb.toString();
    }

    private static List<Path> splitPaths(String csv) {
        if (csv == null || csv.isEmpty()) return List.of();
        String[] p = csv.split(",", -1);
        List<Path> out = new ArrayList<>(p.length);
        for (String s : p) {
            if (s == null || s.isEmpty()) continue;
            out.add(Paths.get(s));
        }
        return out;
    }

    private static String esc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String unesc(String s) {
        return URLDecoder.decode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
