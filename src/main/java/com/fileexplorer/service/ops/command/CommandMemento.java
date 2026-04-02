package com.fileexplorer.service.ops.command;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Serializable memento for persisting/restoring command stacks.
 *
 * <p>We avoid external JSON deps by using Java serialization of this DTO.</p>
 */
public final class CommandMemento implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

/**
 * Kind.
 * <p>
 * Auto-generated API documentation for this type.
 */
    public enum Kind { COPY, MOVE, DELETE, CREATE_DIRECTORY, RENAME_PATH, BATCH, UNKNOWN }

    private final Kind kind;
    private final String id;
    private final String label;
    private final long executedAtEpochMilli;
    private final boolean undoable;

    // File op payload (for COPY/MOVE/DELETE)
    private final String opType; // FileOperationType name
    private final List<String> sources;
    private final String targetDirectory;
    private final String newName;
    private final boolean overwrite;
    private final boolean skipConflicts;
    private final boolean sendToTrash;

    // Batch payload
    private final List<CommandMemento> children;

    private CommandMemento(
            Kind kind,
            String id,
            String label,
            long executedAtEpochMilli,
            boolean undoable,
            String opType,
            List<String> sources,
            String targetDirectory,
            String newName,
            boolean overwrite,
            boolean skipConflicts,
            boolean sendToTrash,
            List<CommandMemento> children
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.executedAtEpochMilli = executedAtEpochMilli;
        this.undoable = undoable;
        this.opType = opType;
        this.sources = sources;
        this.targetDirectory = targetDirectory;
        this.newName = newName;
        this.overwrite = overwrite;
        this.skipConflicts = skipConflicts;
        this.sendToTrash = sendToTrash;
        this.children = children;
    }

    public static CommandMemento forFileOp(
            Kind kind,
            String id,
            String label,
            Instant executedAt,
            boolean undoable,
            String opType,
            List<String> sources,
            String targetDirectory,
            String newName,
            boolean overwrite,
            boolean skipConflicts,
            boolean sendToTrash
    ) {
        return new CommandMemento(kind, id, label, executedAt.toEpochMilli(), undoable,
                opType, List.copyOf(sources), targetDirectory, newName, overwrite, skipConflicts, sendToTrash, null);
    }

    public static CommandMemento forBatch(
            String id,
            String label,
            Instant executedAt,
            boolean undoable,
            List<CommandMemento> children
    ) {
        return new CommandMemento(Kind.BATCH, id, label, executedAt.toEpochMilli(), undoable,
                null, null, null, null, false, false, false, List.copyOf(children));
    }

/**
 * unknown.
 *
 * @param id TODO
 * @param label TODO
 * @param executedAt TODO
 * @param undoable TODO
 * @return TODO
 */
    public static CommandMemento unknown(String id, String label, Instant executedAt, boolean undoable) {
        return new CommandMemento(Kind.UNKNOWN, id, label, executedAt.toEpochMilli(), undoable,
                null, null, null, null, false, false, false, null);
    }

    public Kind kind() { return kind; }
    public String id() { return id; }
    public String label() { return label; }
    public Instant executedAt() { return Instant.ofEpochMilli(executedAtEpochMilli); }
    public boolean undoable() { return undoable; }

    public String opType() { return opType; }
    public List<String> sources() { return sources; }
    public String targetDirectory() { return targetDirectory; }
    public String newName() { return newName; }
    public boolean overwrite() { return overwrite; }
    public boolean skipConflicts() { return skipConflicts; }
    public boolean sendToTrash() { return sendToTrash; }

    public List<CommandMemento> children() { return children; }
}
