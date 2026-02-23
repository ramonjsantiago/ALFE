package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.FileOperationRequest;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.OperationHandle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DeleteCommand: delegates to OperationQueueService DELETE request.
 *
 * <p>Undo semantics supported only when sendToTrash=true (app-managed recycle bin).</p>
 */
public final class DeleteCommand extends FileOperationCommand {

/**
 * DeleteCommand.
 *
 * @param label TODO
 * @param sources TODO
 * @param sendToTrash TODO
 * @return TODO
 */
    public DeleteCommand(String label, List<Path> sources, boolean sendToTrash) {
        super(label,
                new FileOperationRequest(FileOperationType.DELETE, sources, null, null, false, false, sendToTrash));
    }

    DeleteCommand(String id, String label, FileOperationRequest request) {
        super(id, label, request);
    }

    @Override
/**
 * isUndoable.
 *
 * @return TODO
 */
    public boolean isUndoable() {
        return request().sendToTrash();
    }

    @Override
/**
 * undo.
 *
 * @param ctx TODO
 * @return TODO
 */
    public OperationHandle undo(CommandContext ctx) throws Exception {
        if (!request().sendToTrash()) {
            throw new UnsupportedOperationException("Undo not supported for permanent delete");
        }
                List<Command> restores = new ArrayList<>();
        for (Path original : request().sources()) {
            var recycledOpt = ctx.operationQueueService().resolveLatestRecycled(original);
            if (recycledOpt.isEmpty()) {
                continue;
            }
            Path recycled = recycledOpt.get();
            Path parent = original.getParent();
            if (parent == null) {
                continue;
            }
            restores.add(new MoveCommand("Restore item: " + original.getFileName(), List.of(recycled), parent, false));
        }
        if (restores.isEmpty()) {
            throw new IllegalStateException("Nothing to restore from recycle bin");
        }
        return new BatchCommand("Undo: " + label(), restores).execute(ctx);
    }

/**
 * fromMemento.
 *
 * @param id TODO
 * @param label TODO
 * @param request TODO
 * @return TODO
 */
    static DeleteCommand fromMemento(String id, String label, FileOperationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.type() != FileOperationType.DELETE) {
            throw new IllegalArgumentException("Not a DELETE request");
        }
        return new DeleteCommand(id, label, request);
    }
}
