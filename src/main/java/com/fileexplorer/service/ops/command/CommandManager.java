package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.FileOperationRequest;
import com.fileexplorer.service.ops.FileOperationType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Phase 4.0.x: central executor for Commands.
 *
 * <p>Phase 4.0.3 adds persistence of the undo/redo stacks so Ctrl+Z / Ctrl+Y can survive restarts
 * for supported commands.</p>
 */
public final class CommandManager {

    private static final Logger LOG = Logger.getLogger(CommandManager.class.getName());

    private final CommandContext ctx;

    private final Deque<ExecutedCommand> undoStack = new ArrayDeque<>();
    private final Deque<ExecutedCommand> redoStack = new ArrayDeque<>();

    private final CommandStackStore store = new CommandStackStore();

    private volatile String lastLoadStatus = "MISSING";
    private volatile String lastLoadMessage = "";
    private volatile int lastLoadRestoredUndo = 0;
    private volatile int lastLoadRestoredRedo = 0;
    private volatile int lastLoadDropped = 0;

/**
 * CommandManager.
 *
 * @param ctx TODO
 * @return TODO
 */
    public CommandManager(CommandContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        restoreBestEffort();
    }

/**
 * context.
 *
 * @return TODO
 */
    public CommandContext context() {
        return ctx;
    }

    public int undoDepth() { return undoStack.size(); }
    public int redoDepth() { return redoStack.size(); }
    public Path storeFile() { return store.file(); }

    public String lastLoadStatus() { return lastLoadStatus; }
    public String lastLoadMessage() { return lastLoadMessage; }
    public int lastLoadRestoredUndo() { return lastLoadRestoredUndo; }
    public int lastLoadRestoredRedo() { return lastLoadRestoredRedo; }
    public int lastLoadDropped() { return lastLoadDropped; }

/**
 * repairStacks.
 *
 */
    public void repairStacks() {
        // Best-effort: backup current file and reset stacks to empty.
        store.backupBadFile("repair");
        undoStack.clear();
        redoStack.clear();
        store.save(undoStack, redoStack);
        lastLoadStatus = "RESET_BY_USER";
        lastLoadMessage = "Stacks reset by user";
        lastLoadRestoredUndo = 0;
        lastLoadRestoredRedo = 0;
        lastLoadDropped = 0;
    }

    
    public List<ExecutedCommand> undoStackSnapshot() { return new ArrayList<>(undoStack); }
    public List<ExecutedCommand> redoStackSnapshot() { return new ArrayList<>(redoStack); }


    /** Clears the redo stack and persists the updated stacks. */
    public synchronized void clearRedoStack() {
        redoStack.clear();
        persistBestEffort();
    }

    /** Clears both undo and redo stacks and persists the updated stacks. */
    public synchronized void clearAllStacks() {
        undoStack.clear();
        redoStack.clear();
        persistBestEffort();
    }



/**
 * execute.
 *
 * @param cmd TODO
 * @return TODO
 */
    public ExecutedCommand execute(Command cmd) {
        Objects.requireNonNull(cmd, "cmd");
        try {
            var handle = cmd.execute(ctx);
            var exec = new ExecutedCommand(UUID.randomUUID().toString(), cmd, Instant.now());

            if (cmd.isUndoable()) {
                undoStack.push(exec);
                redoStack.clear();
                persistBestEffort();
            } else {
                // Still persist stacks (no-op for non-undoable) so a user can see stability.
                persistBestEffort();
            }
            return exec;
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Command execution failed: " + safeLabel(cmd), ex);
            throw new CommandExecutionException("Command execution failed: " + safeLabel(cmd), ex);
        }
    }

/**
 * canUndo.
 *
 * @return TODO
 */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

/**
 * canRedo.
 *
 * @return TODO
 */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

/**
 * undo.
 *
 * @return TODO
 */
    public ExecutedCommand undo() {
        if (undoStack.isEmpty()) {
            throw new IllegalStateException("No commands to undo");
        }
        var exec = undoStack.pop();
        var cmd = exec.command();
        if (!cmd.isUndoable()) {
            throw new IllegalStateException("Command is not undoable: " + cmd.getClass().getSimpleName());
        }
        try {
            cmd.undo(ctx);
            redoStack.push(exec);
            persistBestEffort();
            return exec;
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Undo failed: " + safeLabel(cmd), ex);
            throw new CommandExecutionException("Undo failed: " + safeLabel(cmd), ex);
        }
    }

/**
 * redo.
 *
 * @return TODO
 */
    public ExecutedCommand redo() {
        if (redoStack.isEmpty()) {
            throw new IllegalStateException("No commands to redo");
        }
        var exec = redoStack.pop();
        var cmd = exec.command();
        try {
            cmd.redo(ctx);
            if (cmd.isUndoable()) {
                undoStack.push(exec);
            }
            persistBestEffort();
            return exec;
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Redo failed: " + safeLabel(cmd), ex);
            throw new CommandExecutionException("Redo failed: " + safeLabel(cmd), ex);
        }
    }

    /** Exposes current persisted storage directory for diagnostics UI. */
    public java.nio.file.Path persistenceDir() {
        return store.dir();
    }

/**
 * persistBestEffort.
 *
 */
    private void persistBestEffort() {
        store.save(undoStack, redoStack);
    }

/**
 * restoreBestEffort.
 *
 */
    private void restoreBestEffort() {
        var report = store.loadReport();
        if (report.status() != CommandStackStore.LoadStatus.OK || report.state() == null) {
            lastLoadStatus = report.status().name();
            lastLoadMessage = report.message();
            lastLoadRestoredUndo = 0;
            lastLoadRestoredRedo = 0;
            lastLoadDropped = 0;
            return;
        }
        var state = report.state();

        int restoredUndo = restoreStack(state.undo, undoStack);
        int restoredRedo = restoreStack(state.redo, redoStack);

        int expected = (state.undo == null ? 0 : state.undo.size()) + (state.redo == null ? 0 : state.redo.size());
        int restored = restoredUndo + restoredRedo;
        int dropped = Math.max(0, expected - restored);

        lastLoadRestoredUndo = restoredUndo;
        lastLoadRestoredRedo = restoredRedo;
        lastLoadDropped = dropped;

        if (dropped > 0) {
            lastLoadStatus = "RECOVERED_WITH_WARNINGS";
            lastLoadMessage = "Dropped " + dropped + " non-restorable command(s)";
            LOG.warning(() -> "Command stacks restored with warnings from " + store.file() + " (undo=" + restoredUndo + ", redo=" + restoredRedo + ", dropped=" + dropped + ")");
        } else {
            lastLoadStatus = "OK";
            lastLoadMessage = "Loaded";
            LOG.info(() -> "Command stacks restored from " + store.file() + " (undo=" + restoredUndo + ", redo=" + restoredRedo + ")");
        }
    }

/**
 * restoreStack.
 *
 * @param mementosTopFirst TODO
 * @param stack TODO
 * @return TODO
 */
    private int restoreStack(List<CommandMemento> mementosTopFirst, Deque<ExecutedCommand> stack) {
        if (mementosTopFirst == null || mementosTopFirst.isEmpty()) return 0;

        // To keep top-of-stack at deque head, push in reverse.
        int restored = 0;
        for (int i = mementosTopFirst.size() - 1; i >= 0; i--) {
            CommandMemento m = mementosTopFirst.get(i);
            try {
                Command cmd = fromMemento(m);
                if (cmd != null && cmd.isUndoable() == m.undoable()) {
                    stack.push(new ExecutedCommand(m.id(), cmd, m.executedAt()));
                    restored++;
                }
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Dropping non-restorable command memento: " + m.kind() + " " + m.label(), ex);
            }
        }
        return restored;
    }

/**
 * fromMemento.
 *
 * @param m TODO
 * @return TODO
 */
    private Command fromMemento(CommandMemento m) {
        return switch (m.kind()) {
            case COPY -> CopyCommand.fromMemento(m.id(), m.label(), toRequest(m));
            case MOVE -> MoveCommand.fromMemento(m.id(), m.label(), toRequest(m));
            case DELETE -> DeleteCommand.fromMemento(m.id(), m.label(), toRequest(m));
            case BATCH -> {
                List<Command> kids = new ArrayList<>();
                if (m.children() != null) {
                    for (CommandMemento kid : m.children()) {
                        Command child = fromMemento(kid);
                        if (child != null) {
                            kids.add(child);
                        }
                    }
                }
                yield BatchCommand.fromMemento(m.id(), m.label(), kids);
            }
            case UNKNOWN -> null;
        };
    }

/**
 * toRequest.
 *
 * @param m TODO
 * @return TODO
 */
    private static FileOperationRequest toRequest(CommandMemento m) {
        FileOperationType type = FileOperationType.valueOf(m.opType());
        List<Path> sources = m.sources() == null ? List.of() : m.sources().stream().map(Paths::get).toList();
        Path targetDir = (m.targetDirectory() == null || m.targetDirectory().isBlank()) ? null : Paths.get(m.targetDirectory());
        return new FileOperationRequest(type, sources, targetDir, m.newName(), m.overwrite(), m.skipConflicts(), m.sendToTrash());
    }

/**
 * safeLabel.
 *
 * @param cmd TODO
 * @return TODO
 */
    private static String safeLabel(Command cmd) {
        try {
            return cmd.label();
        } catch (Throwable t) {
            return cmd.getClass().getSimpleName();
        }
    }

    /**
     * Immutable record of an executed command.
     */
    public record ExecutedCommand(String executionId, Command command, Instant executedAt) {
    }

    
/** Lightweight lookup result for linking history rows to commands. */
public record CommandInfo(
        String commandId,
        String label,
        String commandType,
        Instant executedAt,
        boolean undoable,
        String stackLocation
) { }

/**
 * Best-effort lookup by commandId across the in-memory undo/redo stacks.
 * Returns null if not found (e.g., stacks trimmed or persistence dropped command).
 */
public synchronized CommandInfo lookupByCommandId(String commandId) {
    if (commandId == null || commandId.isBlank()) return null;
    int idx = 0;
    for (ExecutedCommand ec : undoStack) {
        if (ec.command().id().equals(commandId)) {
            return new CommandInfo(commandId, safeLabel(ec.command()), ec.command().getClass().getSimpleName(),
                    ec.executedAt(), ec.command().isUndoable(), "UNDO_STACK[" + idx + "]");
        }
        idx++;
    }
    idx = 0;
    for (ExecutedCommand ec : redoStack) {
        if (ec.command().id().equals(commandId)) {
            return new CommandInfo(commandId, safeLabel(ec.command()), ec.command().getClass().getSimpleName(),
                    ec.executedAt(), ec.command().isUndoable(), "REDO_STACK[" + idx + "]");
        }
        idx++;
    }
    return null;
}

/** Most recent command ids (from undo stack top downward). */
public synchronized List<String> recentCommandIds(int max) {
    int n = Math.max(0, max);
    List<String> out = new ArrayList<>();
    if (n == 0) return out;
    for (ExecutedCommand ec : undoStack) {
        out.add(ec.command().id());
        if (out.size() >= n) break;
    }
    return out;
}

public static final class CommandExecutionException extends RuntimeException {
/**
 * CommandExecutionException.
 *
 * @param message TODO
 * @param cause TODO
 * @return TODO
 */
        public CommandExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
