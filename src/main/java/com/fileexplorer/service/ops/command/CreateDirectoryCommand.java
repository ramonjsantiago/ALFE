package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.OperationHandle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Command that creates a single directory and can undo by deleting that directory when it still exists.
 *
 * <p>This command is intentionally synchronous so Explorer-style create/rename flows can update selection,
 * focus, and viewport state immediately without waiting on the background operation queue.</p>
 */
public final class CreateDirectoryCommand implements Command {

    private final String id;
    private final String label;
    private final Path directoryPath;

    /**
     * Creates a command for a single directory path.
     *
     * @param label human-readable command label shown in undo UI
     * @param directoryPath full path of the directory to create
     */
    public CreateDirectoryCommand(String label, Path directoryPath) {
        this(UUID.randomUUID().toString(), label, directoryPath);
    }

    CreateDirectoryCommand(String id, String label, Path directoryPath) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.directoryPath = Objects.requireNonNull(directoryPath, "directoryPath");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String label() {
        return label;
    }

    /**
     * Returns the created directory path.
     *
     * @return absolute or relative directory path captured by the command
     */
    public Path directoryPath() {
        return directoryPath;
    }

    @Override
    public OperationHandle execute(CommandContext ctx) throws Exception {
        Path parent = directoryPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createDirectory(directoryPath);
        return null;
    }

    @Override
    public boolean isUndoable() {
        return true;
    }

    @Override
    public OperationHandle undo(CommandContext ctx) throws Exception {
        if (Files.notExists(directoryPath)) {
            return null;
        }
        Files.deleteIfExists(directoryPath);
        return null;
    }

    @Override
    public OperationHandle redo(CommandContext ctx) throws Exception {
        return execute(ctx);
    }

    static CreateDirectoryCommand fromMemento(String id, String label, Path directoryPath) {
        return new CreateDirectoryCommand(id, label, directoryPath);
    }
}
