package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.OperationHandle;

/**
 * Phase 4.0.0: Command framework.
 *
 * <p>A Command represents a user-intentful operation that may be executed and (optionally)
 * undone/redone. Commands are executed via {@link CommandManager}.</p>
 */
public interface Command {

    /** Stable identifier for this command instance. */
    String id();

    /** Human friendly label for UI/history. */
    String label();

    /** Execute this command. */
    OperationHandle execute(CommandContext ctx) throws Exception;

    /** Whether undo is supported for this command. */
    default boolean isUndoable() {
        return false;
    }

    /** Undo this command (if supported). */
    default OperationHandle undo(CommandContext ctx) throws Exception {
        throw new UnsupportedOperationException("Undo not supported for command: " + getClass().getSimpleName());
    }

    /** Redo this command (if supported). */
    default OperationHandle redo(CommandContext ctx) throws Exception {
        // By default, redo is equivalent to execute.
        return execute(ctx);
    }
}
