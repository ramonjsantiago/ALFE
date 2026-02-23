package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.OperationHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A composite command that executes multiple commands.
 *
 * <p>Phase 4.0.x executes children sequentially (best-effort). This command is undoable if all
 * children are undoable. Undo executes children in reverse order.</p>
 */
public final class BatchCommand implements Command {

    private final String id;
    private final String label;
    private final List<Command> children;

/**
 * BatchCommand.
 *
 * @param label TODO
 * @param children TODO
 * @return TODO
 */
    public BatchCommand(String label, List<? extends Command> children) {
        this(UUID.randomUUID().toString(), label, children);
    }

    BatchCommand(String id, String label, List<? extends Command> children) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.children = List.copyOf(children);
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
 * children.
 *
 * @return TODO
 */
    public List<Command> children() {
        return children;
    }

    @Override
/**
 * isUndoable.
 *
 * @return TODO
 */
    public boolean isUndoable() {
        return children.stream().allMatch(Command::isUndoable);
    }

    @Override
/**
 * execute.
 *
 * @param ctx TODO
 * @return TODO
 */
    public OperationHandle execute(CommandContext ctx) throws Exception {
        // Execute sequentially; return last handle (useful for UI anchoring).
        OperationHandle last = null;
        List<Exception> failures = new ArrayList<>();
        for (Command c : children) {
            try {
                last = c.execute(ctx);
            } catch (Exception ex) {
                failures.add(ex);
            }
        }
        if (!failures.isEmpty()) {
            Exception head = failures.getFirst();
            for (int i = 1; i < failures.size(); i++) {
                head.addSuppressed(failures.get(i));
            }
            throw head;
        }
        return last;
    }

    @Override
/**
 * undo.
 *
 * @param ctx TODO
 * @return TODO
 */
    public OperationHandle undo(CommandContext ctx) throws Exception {
        if (!isUndoable()) {
            throw new UnsupportedOperationException("Undo not supported for BatchCommand (contains non-undoable child)");
        }
        OperationHandle last = null;
        List<Exception> failures = new ArrayList<>();
        for (int i = children.size() - 1; i >= 0; i--) {
            Command c = children.get(i);
            try {
                last = c.undo(ctx);
            } catch (Exception ex) {
                failures.add(ex);
            }
        }
        if (!failures.isEmpty()) {
            Exception head = failures.getFirst();
            for (int i = 1; i < failures.size(); i++) {
                head.addSuppressed(failures.get(i));
            }
            throw head;
        }
        return last;
    }

    @Override
/**
 * redo.
 *
 * @param ctx TODO
 * @return TODO
 */
    public OperationHandle redo(CommandContext ctx) throws Exception {
        // redo in original order
        return execute(ctx);
    }

/**
 * fromMemento.
 *
 * @param id TODO
 * @param label TODO
 * @param children TODO
 * @return TODO
 */
    static BatchCommand fromMemento(String id, String label, List<Command> children) {
        return new BatchCommand(id, label, children);
    }
}
