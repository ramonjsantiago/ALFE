package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.OperationHandle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Synchronous Explorer-style rename command for a single filesystem path.
 *
 * <p>Undo renames the target back to the original source path. Redo reapplies the rename.
 * The implementation deliberately avoids the background queue so rename-driven shell-state restoration
 * can happen in a single UI transaction.</p>
 */
public final class RenamePathCommand implements Command {

    private final String id;
    private final String label;
    private final Path sourcePath;
    private final Path targetPath;

    /**
     * Creates a rename command for one source and one target path.
     *
     * @param label human-readable command label shown in undo UI
     * @param sourcePath original path before rename
     * @param targetPath resulting path after rename
     */
    public RenamePathCommand(String label, Path sourcePath, Path targetPath) {
        this(UUID.randomUUID().toString(), label, sourcePath, targetPath);
    }

    RenamePathCommand(String id, String label, Path sourcePath, Path targetPath) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.targetPath = Objects.requireNonNull(targetPath, "targetPath");
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
     * Returns the original path before the rename.
     *
     * @return original source path
     */
    public Path sourcePath() {
        return sourcePath;
    }

    /**
     * Returns the resulting path after the rename.
     *
     * @return destination path after rename
     */
    public Path targetPath() {
        return targetPath;
    }

    @Override
    public OperationHandle execute(CommandContext ctx) throws Exception {
        move(sourcePath, targetPath);
        return null;
    }

    @Override
    public boolean isUndoable() {
        return true;
    }

    @Override
    public OperationHandle undo(CommandContext ctx) throws Exception {
        move(targetPath, sourcePath);
        return null;
    }

    @Override
    public OperationHandle redo(CommandContext ctx) throws Exception {
        return execute(ctx);
    }

    private static void move(Path from, Path to) throws Exception {
        if (Files.notExists(from)) {
            return;
        }
        Path parent = to.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    }

    static RenamePathCommand fromMemento(String id, String label, Path sourcePath, Path targetPath) {
        return new RenamePathCommand(id, label, sourcePath, targetPath);
    }
}
