package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.FileOperationRequest;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.OperationHandle;
import com.fileexplorer.service.ops.conflict.ConflictPolicyConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CopyCommand: delegates to OperationQueueService COPY request.
 *
 * <p>Undo semantics: best-effort delete of copied targets (sent to app recycle bin).</p>
 */
public final class CopyCommand extends FileOperationCommand {

/**
 * CopyCommand.
 *
 * @param label TODO
 * @param sources TODO
 * @param targetDirectory TODO
 * @param overwrite TODO
 * @return TODO
 */
    public CopyCommand(String label, List<Path> sources, Path targetDirectory, boolean overwrite) {
        this(label, sources, targetDirectory, overwrite, false);
    }

    public CopyCommand(String label, List<Path> sources, Path targetDirectory, boolean overwrite, boolean skipConflicts) {
        super(label,
                new FileOperationRequest(FileOperationType.COPY, sources, targetDirectory, null, overwrite, skipConflicts, false));
    }

/**
 * CopyCommand.
 *
 * @param label TODO
 * @param sources TODO
 * @param targetDirectory TODO
 * @param overwrite TODO
 * @param skipConflicts TODO
 * @param conflictPolicyOverride TODO
 * @return TODO
 */
    public CopyCommand(String label, List<Path> sources, Path targetDirectory, boolean overwrite, boolean skipConflicts, ConflictPolicyConfig conflictPolicyOverride) {
        super(label,
                new FileOperationRequest(FileOperationType.COPY, sources, targetDirectory, null, overwrite, skipConflicts, false),
                conflictPolicyOverride);
    }

    CopyCommand(String id, String label, FileOperationRequest request) {
        super(id, label, request);
    }

    @Override
/**
 * isUndoable.
 *
 * @return TODO
 */
    public boolean isUndoable() {
        return true;
    }

    @Override
/**
 * undo.
 *
 * @param ctx TODO
 * @return TODO
 */
    public OperationHandle undo(CommandContext ctx) {
        var req = request();
        var sources = req.sources();
        var targetDir = req.targetDirectory();
        if (targetDir == null) {
            throw new IllegalStateException("COPY undo requires targetDirectory");
        }
        List<Path> targets = sources.stream()
                .map(p -> targetDir.resolve(p.getFileName()))
                .collect(Collectors.toList());

        var delReq = new FileOperationRequest(FileOperationType.DELETE, targets, null, null, false, false, true);
        return ctx.operationQueueService().enqueue(delReq, "Undo: " + label(), id());
    }

    /** For persistence restore. */
    static CopyCommand fromMemento(String id, String label, FileOperationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.type() != FileOperationType.COPY) {
            throw new IllegalArgumentException("Not a COPY request");
        }
        return new CopyCommand(id, label, request);
    }
}
