package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.FileOperationRequest;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.OperationHandle;
import com.fileexplorer.service.ops.conflict.ConflictPolicyConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MoveCommand: delegates to OperationQueueService MOVE request.
 *
 * <p>Undo semantics: best-effort move back to original parent directories.</p>
 */
public final class MoveCommand extends FileOperationCommand {

/**
 * MoveCommand.
 *
 * @param label TODO
 * @param sources TODO
 * @param targetDirectory TODO
 * @param overwrite TODO
 * @return TODO
 */
    public MoveCommand(String label, List<Path> sources, Path targetDirectory, boolean overwrite) {
        this(label, sources, targetDirectory, overwrite, false);
    }

    public MoveCommand(String label, List<Path> sources, Path targetDirectory, boolean overwrite, boolean skipConflicts) {
        super(label,
                new FileOperationRequest(FileOperationType.MOVE, sources, targetDirectory, null, overwrite, skipConflicts, false));
    }

/**
 * MoveCommand.
 *
 * @param label TODO
 * @param sources TODO
 * @param targetDirectory TODO
 * @param overwrite TODO
 * @param skipConflicts TODO
 * @param conflictPolicyOverride TODO
 * @return TODO
 */
    public MoveCommand(String label, List<Path> sources, Path targetDirectory, boolean overwrite, boolean skipConflicts, ConflictPolicyConfig conflictPolicyOverride) {
        super(label,
                new FileOperationRequest(FileOperationType.MOVE, sources, targetDirectory, null, overwrite, skipConflicts, false),
                conflictPolicyOverride);
    }

    MoveCommand(String id, String label, FileOperationRequest request) {
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
    public OperationHandle undo(CommandContext ctx) throws Exception {
        var req = request();
        var targetDir = req.targetDirectory();
        if (targetDir == null) {
            throw new IllegalStateException("MOVE undo requires targetDirectory");
        }
        List<Command> children = new ArrayList<>();
        for (Path original : req.sources()) {
            Path originalParent = original.getParent();
            if (originalParent == null) {
                continue;
            }
            Path movedPath = targetDir.resolve(original.getFileName());
            // Move the movedPath back to originalParent.            children.add(new MoveCommand("Undo item: " + original.getFileName(), List.of(movedPath), originalParent, req.overwrite()));
            // Note: above constructor recreates request; keep consistent overwrite.
        }
        if (children.isEmpty()) {
            throw new IllegalStateException("Nothing to undo for MOVE command");
        }
        return new BatchCommand("Undo: " + label(), children).execute(ctx);
    }

/**
 * fromMemento.
 *
 * @param id TODO
 * @param label TODO
 * @param request TODO
 * @return TODO
 */
    static MoveCommand fromMemento(String id, String label, FileOperationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.type() != FileOperationType.MOVE) {
            throw new IllegalArgumentException("Not a MOVE request");
        }
        return new MoveCommand(id, label, request);
    }
}
