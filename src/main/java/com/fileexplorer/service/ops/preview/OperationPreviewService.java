package com.fileexplorer.service.ops.preview;

import com.fileexplorer.service.ops.FileOperationRequest;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.conflict.ConflictPolicyAction;
import com.fileexplorer.service.ops.conflict.ConflictPolicyConfig;
import com.fileexplorer.service.ops.conflict.ConflictPolicyProfile;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Phase 4.3.1: Dry-run preview engine with deterministic plan snapshot.
 *
 * <p>Builds an in-memory plan snapshot for a {@link FileOperationRequest} without mutating the filesystem.</p>
 */
public final class OperationPreviewService {

/**
 * PlannedConflictOutcome.
 * <p>
 * Auto-generated API documentation for this type.
 */
    public enum PlannedConflictOutcome {
        NONE,
        OVERWRITE,
        RENAME,
        SKIP,
        ESCALATE
    }

    /**
     * Backwards-compatible preview report used by existing UI code.
     */
    public record PreviewReport(
            FileOperationType type,
            Path targetDir,
            PreviewCounts counts,
            List<String> itemsSample,
            List<String> conflicts,
            List<String> warnings,
            OperationPlanSnapshot snapshot
    ) {}

    private static final int MAX_ITEMS_SAMPLE = 250;
    private static final int MAX_CONFLICTS = 500;
    private static final int MAX_ACTIONS = 2000;

    /**
     * Phase 4.3.1: Primary API. Produces a deterministic plan snapshot for rendering and (later) execution.
     */
    public OperationPlanSnapshot previewPlan(FileOperationRequest req, ConflictPolicyConfig policyOverride) {
        Objects.requireNonNull(req, "req");

        FileOperationType type = req.type();
        List<Path> sources = (req.sources() == null) ? List.of() : req.sources();
        Path targetDir = req.targetDirectory();

        ArrayList<OperationPlanItem> actions = new ArrayList<>();
        ArrayList<String> conflicts = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();

        int missing = 0;
        int conflictCount = 0;
        int ow = 0;
        int rn = 0;
        int sk = 0;
        int esc = 0;
        boolean crossVol = false;
        boolean deepMerge = false;
        boolean targetNotWritable = false;

        if (targetDir != null) {
            try {
                if (Files.exists(targetDir) && !Files.isWritable(targetDir)) {
                    targetNotWritable = true;
                    warnings.add("Target directory is not writable (operation may fail).");
                }
            } catch (Exception ignored) {
            }
        }

        if (type == FileOperationType.COPY || type == FileOperationType.MOVE) {
            FileStore targetStore = null;
            if (targetDir != null) {
                try {
                    if (Files.exists(targetDir)) targetStore = Files.getFileStore(targetDir);
                } catch (IOException ignored) {}
            }

            for (Path src : sources) {
                if (src == null) continue;

                boolean srcExists = Files.exists(src);
                if (!srcExists) {
                    missing++;
                    if (actions.size() < MAX_ACTIONS) {
                        actions.add(new OperationPlanItem(src, null, OperationPlanAction.SKIP, "missing source"));
                    }
                    continue;
                }

                Path dst = (targetDir == null) ? null : targetDir.resolve(src.getFileName());
                boolean dstExists = (dst != null && Files.exists(dst));

                Path resolvedDst = dst;
                OperationPlanAction action = (type == FileOperationType.COPY) ? OperationPlanAction.COPY : OperationPlanAction.MOVE;
                String reason = "";

                if (dstExists) {
                    conflictCount++;
                    if (conflicts.size() < MAX_CONFLICTS) conflicts.add(dst.toString());

                    PlannedConflictOutcome outcome = decideOutcome(req, policyOverride);
                    switch (outcome) {
                        case OVERWRITE -> {
                            ow++;
                            action = OperationPlanAction.OVERWRITE;
                            reason = "target exists; overwrite";
                        }
                        case RENAME -> {
                            rn++;
                            action = OperationPlanAction.RENAME;
                            resolvedDst = computeNonColliding(targetDir, src.getFileName().toString());
                            reason = "target exists; keep both";
                        }
                        case SKIP -> {
                            sk++;
                            action = OperationPlanAction.SKIP;
                            reason = "target exists; skip";
                        }
                        case ESCALATE -> {
                            esc++;
                            action = OperationPlanAction.ESCALATE;
                            reason = "target exists; needs decision";
                        }
                        default -> {}
                    }

                    try {
                        if (!deepMerge && Files.isDirectory(src) && Files.isDirectory(dst)) {
                            deepMerge = true;
                        }
                    } catch (Exception ignored) {}
                }

                if (type == FileOperationType.MOVE && targetStore != null) {
                    try {
                        FileStore srcStore = Files.getFileStore(src);
                        if (srcStore != null && !srcStore.equals(targetStore)) {
                            crossVol = true;
                        }
                    } catch (IOException ignored) {}
                }

                if (actions.size() < MAX_ACTIONS) {
                    actions.add(new OperationPlanItem(src, resolvedDst, action, reason));
                }
            }

            if (req.overwrite()) {
                warnings.add("Overwrite is enabled.");
            } else if (!conflicts.isEmpty()) {
                warnings.add("Some targets already exist (overwrite is disabled). Policy may auto-resolve or escalate.");
            }
            if (req.skipConflicts()) {
                warnings.add("Skip-conflicts is enabled: conflicting items will be skipped.");
            }
            if (missing > 0) {
                warnings.add("Missing sources: " + missing + " (will be skipped).");
            }
            if (crossVol) {
                warnings.add("Move appears to be cross-volume (may degrade to copy+delete).");
            }
            if (deepMerge) {
                warnings.add("Directory merge detected: copying/moving a directory onto an existing directory may be destructive depending on conflicts.");
            }

            warnings.add(0, formatSummary(type, sources.size(), conflictCount, ow, rn, sk, esc, crossVol));

            PreviewCounts counts = new PreviewCounts(
                    sources.size(),
                    missing,
                    conflictCount,
                    ow,
                    rn,
                    sk,
                    esc,
                    crossVol,
                    deepMerge,
                    targetNotWritable
            );

            OperationPlanSnapshot snap = new OperationPlanSnapshot(
                    type,
                    targetDir,
                    policyOverride,
                    counts,
                    actions,
                    conflicts,
                    warnings,
                    null
            );

            ArrayList<String> w2 = new ArrayList<>(snap.warnings());
            w2.add("PreviewHash=" + snap.previewHash());

            return new OperationPlanSnapshot(
                    snap.type(), snap.targetDirectory(), snap.policy(), snap.counts(),
                    snap.actions(), snap.conflicts(), w2, snap.previewHash()
            );
        }

        if (type == FileOperationType.DELETE) {
            for (Path src : sources) {
                if (src == null) continue;
                boolean exists = Files.exists(src);
                if (!exists) missing++;
                if (actions.size() < MAX_ACTIONS) {
                    actions.add(new OperationPlanItem(src, null,
                            exists ? OperationPlanAction.DELETE : OperationPlanAction.SKIP,
                            exists ? "delete" : "missing source"));
                }
            }
            if (req.sendToTrash()) {
                warnings.add("Items will be moved to the Recycle Bin (managed).");
            } else {
                warnings.add("Permanent delete: items will be removed immediately.");
            }
            if (missing > 0) warnings.add("Missing sources: " + missing + " (no-op).");
            warnings.add(0, "Plan summary: DELETE sources=" + sources.size() + " missing=" + missing);

            PreviewCounts counts = new PreviewCounts(
                    sources.size(), missing, 0, 0, 0, missing, 0, false, false, false
            );

            OperationPlanSnapshot snap = new OperationPlanSnapshot(
                    type, null, policyOverride, counts, actions, List.of(), warnings, null
            );

            ArrayList<String> w2 = new ArrayList<>(snap.warnings());
            w2.add("PreviewHash=" + snap.previewHash());

            return new OperationPlanSnapshot(
                    snap.type(), snap.targetDirectory(), snap.policy(), snap.counts(),
                    snap.actions(), snap.conflicts(), w2, snap.previewHash()
            );
        }

        warnings.add("Preview engine not implemented for operation: " + type);
        PreviewCounts counts = new PreviewCounts(sources.size(), 0, 0, 0, 0, 0, 0, false, false, false);
        return new OperationPlanSnapshot(type, targetDir, policyOverride, counts, actions, conflicts, warnings, null);
    }

    /**
     * Backwards-compatible report for existing UI renderers.
     */
    public PreviewReport preview(FileOperationRequest req, ConflictPolicyConfig policyOverride) {
        OperationPlanSnapshot snap = previewPlan(req, policyOverride);

        ArrayList<String> items = new ArrayList<>();
        int max = Math.min(MAX_ITEMS_SAMPLE, snap.actions().size());
        for (int i = 0; i < max; i++) {
            OperationPlanItem it = snap.actions().get(i);
            String line = it.sourceText() + " -> " + (it.destination() == null ? "" : it.destinationText());
            if (it.action() == OperationPlanAction.OVERWRITE
                    || it.action() == OperationPlanAction.RENAME
                    || it.action() == OperationPlanAction.SKIP
                    || it.action() == OperationPlanAction.ESCALATE) {
                line = line + "  [" + it.action()
                        + (it.reason() == null || it.reason().isBlank() ? "" : (": " + it.reason()))
                        + "]";
            }
            items.add(line);
        }

        return new PreviewReport(
                snap.type(),
                snap.targetDirectory(),
                snap.counts(),
                List.copyOf(items),
                snap.conflicts(),
                snap.warnings(),
                snap
        );
    }

/**
 * decideOutcome.
 *
 * @param req TODO
 * @param override TODO
 * @return TODO
 */
    private static PlannedConflictOutcome decideOutcome(FileOperationRequest req, ConflictPolicyConfig override) {
        if (req.overwrite()) return PlannedConflictOutcome.OVERWRITE;
        if (req.skipConflicts()) return PlannedConflictOutcome.SKIP;

        ConflictPolicyConfig cfg = (override != null)
                ? override
                : new ConflictPolicyConfig(ConflictPolicyProfile.DEFAULT, ConflictPolicyAction.PROMPT);

        ConflictPolicyProfile profile = (cfg.profile() == null) ? ConflictPolicyProfile.DEFAULT : cfg.profile();
        ConflictPolicyAction action = cfg.customDefaultAction();
        if (action == null) action = ConflictPolicyAction.PROMPT;

/**
 * switch.
 *
 * @param profile TODO
 * @return TODO
 */
        return switch (profile) {
            case CONSERVATIVE -> PlannedConflictOutcome.ESCALATE;
            case AGGRESSIVE -> PlannedConflictOutcome.OVERWRITE;
            case MIRROR -> PlannedConflictOutcome.OVERWRITE;
            case CUSTOM -> mapAction(action);
            case DEFAULT -> PlannedConflictOutcome.ESCALATE;
        };
    }

/**
 * mapAction.
 *
 * @param a TODO
 * @return TODO
 */
    private static PlannedConflictOutcome mapAction(ConflictPolicyAction a) {
        if (a == null) return PlannedConflictOutcome.ESCALATE;
        return switch (a) {
            case OVERWRITE -> PlannedConflictOutcome.OVERWRITE;
            case RENAME -> PlannedConflictOutcome.RENAME;
            case SKIP -> PlannedConflictOutcome.SKIP;
            case PROMPT -> PlannedConflictOutcome.ESCALATE;
        };
    }

    private static String formatSummary(FileOperationType type, int sources, int conflicts,
                                        int ow, int rn, int sk, int esc, boolean crossVol) {
        return "Plan summary: " + type + " sources=" + sources
                + " conflicts=" + conflicts
                + " overwrite=" + ow
                + " rename=" + rn
                + " skip=" + sk
                + " escalations=" + esc
                + (crossVol ? " crossVolume=true" : "");
    }

/**
 * computeNonColliding.
 *
 * @param parentDir TODO
 * @param name TODO
 * @return TODO
 */
    private static Path computeNonColliding(Path parentDir, String name) {
        if (parentDir == null) return null;
        Path dst = parentDir.resolve(name);
        if (!Files.exists(dst)) return dst;

        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }

        int i = 2;
        while (true) {
            Path candidate = parentDir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) return candidate;
            i++;
            if (i > 10_000) {
                return parentDir.resolve(base + " (" + Instant.now().toEpochMilli() + ")" + ext);
            }
        }
    }
}
