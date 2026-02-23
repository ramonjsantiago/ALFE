package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.FileOperationRequest;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.OperationHandle;
import com.fileexplorer.service.ops.ExecutionDriftPolicy;
import com.fileexplorer.service.ops.conflict.ConflictPolicyConfig;
import com.fileexplorer.service.ops.preview.OperationPlanSnapshot;

import java.util.Objects;
import java.util.UUID;

/**
 * Base Command that delegates execution to the existing OperationQueueService.
 */
public abstract class FileOperationCommand implements Command, PreviewableCommand {

    private final String id;
    private final String label;
    private final FileOperationRequest request;

    // Phase 4.2.1: optional per-operation conflict policy override (not persisted)
    private final ConflictPolicyConfig conflictPolicyOverride;

    // Phase 4.4.1: optional per-operation execution drift policy override (not persisted)
    private final ExecutionDriftPolicy driftPolicyOverride;

    // Phase 4.4.0: last preview snapshot (best-effort). When present, execution can be deterministic.
    private volatile OperationPlanSnapshot lastPreviewSnapshot;

/**
 * FileOperationCommand.
 *
 * @param label TODO
 * @param request TODO
 * @return TODO
 */
    protected FileOperationCommand(String label, FileOperationRequest request) {
        this(UUID.randomUUID().toString(), label, request, null);
    }

    protected FileOperationCommand(String label, FileOperationRequest request, ConflictPolicyConfig conflictPolicyOverride) {
        this(UUID.randomUUID().toString(), label, request, conflictPolicyOverride);
    }

/**
 * FileOperationCommand.
 *
 * @param id TODO
 * @param label TODO
 * @param request TODO
 * @return TODO
 */
    protected FileOperationCommand(String id, String label, FileOperationRequest request) {
        this(id, label, request, null);
    }

    protected FileOperationCommand(String id, String label, FileOperationRequest request, ConflictPolicyConfig conflictPolicyOverride) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.request = Objects.requireNonNull(request, "request");
        this.conflictPolicyOverride = conflictPolicyOverride;
        this.driftPolicyOverride = null;
    }

    @Override
/**
 * id.
 *
 * @return TODO
 */
    public String id() {
        return id;
    }

    @Override
/**
 * label.
 *
 * @return TODO
 */
    public String label() {
        return label;
    }

/**
 * request.
 *
 * @return TODO
 */
    public FileOperationRequest request() {
        return request;
    }

/**
 * conflictPolicyOverride.
 *
 * @return TODO
 */
    public ConflictPolicyConfig conflictPolicyOverride() {
        return conflictPolicyOverride;
    }

    /**
     * Phase 4.4.1: optional per-operation drift policy override (may be null).
     */
    public ExecutionDriftPolicy driftPolicyOverride() {
        return driftPolicyOverride;
    }

    /**
     * Phase 4.4.0: last preview snapshot (may be null).
     */
    public OperationPlanSnapshot lastPreviewSnapshot() {
        return lastPreviewSnapshot;
    }

    @Override
/**
 * preview.
 *
 * @return TODO
 */
    public CommandPreview preview() {
        var req = request();
        var type = req.type();

        // Phase 4.3.1: use dry-run preview engine and include structured plan snapshot.
        var svc = new com.fileexplorer.service.ops.preview.OperationPreviewService();
        var report = svc.preview(req, conflictPolicyOverride);

        // Phase 4.4.0: capture the snapshot so execute() can run deterministically from it.
        this.lastPreviewSnapshot = report.snapshot();

        String title;
        if (type == FileOperationType.COPY) title = "Preview Copy";
        else if (type == FileOperationType.MOVE) title = "Preview Move";
        else if (type == FileOperationType.DELETE) title = "Preview Delete";
        else if (type == FileOperationType.RENAME) title = "Preview Rename";
        else title = "Preview";

        String summary;
        if (type == FileOperationType.COPY || type == FileOperationType.MOVE) {
            summary = (type == FileOperationType.COPY ? "Copy " : "Move ") + report.counts().sources() + " item(s)"
                    + (report.targetDir() == null ? "" : (" -> " + report.targetDir()));
        } else if (type == FileOperationType.DELETE) {
            summary = "Delete " + report.counts().sources() + " item(s)" + (req.sendToTrash() ? " (Recycle Bin)" : " (Permanent)");
        } else {
            summary = type + " " + report.counts().sources() + " item(s)";
        }

        return new CommandPreview(
                title,
                summary,
                report.itemsSample(),
                report.conflicts(),
                report.warnings(),
                report.snapshot()
        );
    }

    @Override
/**
 * execute.
 *
 * @param ctx TODO
 * @return TODO
 */
    public OperationHandle execute(CommandContext ctx) {
        // Use label as history label override so history stays meaningful.
        // Phase 4.4.0: pass lastPreviewSnapshot when available.
        return ctx.operationQueueService().enqueue(request, label, id, conflictPolicyOverride, lastPreviewSnapshot);
    }
}
