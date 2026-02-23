package com.fileexplorer.service.ops.command;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists/restores the CommandManager undo/redo stacks.
 *
 * <p>Phase 4.0.3 uses a small Java-serialized DTO so we don't introduce JSON dependencies.</p>
 */
final class CommandStackStore {

    private static final Logger LOG = Logger.getLogger(CommandStackStore.class.getName());

    private static final String ROOT_DIR_NAME = ".fileexplorer";
    private static final String COMMAND_DIR_NAME = "commands";
    private static final String STACK_FILE_NAME = "command-stacks.ser";

    private final Path dir;
    private final Path file;

/**
 * Constructs a CommandStackStore.
 *
 */
    CommandStackStore() {
        this.dir = resolveDir();
        this.file = dir.resolve(STACK_FILE_NAME);
    }

    Path dir() { return dir; }
    Path file() { return file; }

/**
 * LoadStatus.
 * <p>
 * Auto-generated API documentation for this type.
 */
    enum LoadStatus { OK, MISSING, CORRUPT_RESET }

    record LoadReport(LoadStatus status, PersistedState state, String message) {}


/**
 * resolveDir.
 *
 * @return TODO
 */
    private static Path resolveDir() {
        String override = System.getProperty("fileexplorer.command.dir");
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        Path home = Paths.get(System.getProperty("user.home"));
        return home.resolve(ROOT_DIR_NAME).resolve(COMMAND_DIR_NAME);
    }

/**
 * save.
 *
 * @param undo TODO
 * @param redo TODO
 */
    void save(Deque<CommandManager.ExecutedCommand> undo, Deque<CommandManager.ExecutedCommand> redo) {
        try {
            Files.createDirectories(dir);
            var state = new PersistedState(
                    toMementos(new ArrayList<>(undo)),
                    toMementos(new ArrayList<>(redo))
            );
            try (var out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)))) {
                out.writeObject(state);
            }
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Command stack persistence save failed (ignored)", ex);
        }
    }

/**
 * load.
 *
 * @return TODO
 */
    Optional<PersistedState> load() {
        LoadReport r = loadReport();
        return (r.status == LoadStatus.OK && r.state != null) ? Optional.of(r.state) : Optional.empty();
    }

/**
 * loadReport.
 *
 * @return TODO
 */
    LoadReport loadReport() {
        if (!Files.isRegularFile(file)) {
            return new LoadReport(LoadStatus.MISSING, null, "no stack file");
        }
        try (var in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            Object o = in.readObject();
            if (o instanceof PersistedState ps) {
                return new LoadReport(LoadStatus.OK, ps, "loaded");
            }
            // Unexpected content: treat as corrupt.
            backupBadFile("unexpected content");
            return new LoadReport(LoadStatus.CORRUPT_RESET, null, "unexpected content");
        } catch (Exception ex) {
            backupBadFile(ex.getClass().getSimpleName());
            LOG.log(Level.WARNING, "Failed to load command stacks; starting fresh", ex);
            return new LoadReport(LoadStatus.CORRUPT_RESET, null, ex.getClass().getSimpleName());
        }
    }

/**
 * backupBadFile.
 *
 * @param reason TODO
 */
    void backupBadFile(String reason) {
        try {
            Files.createDirectories(dir);
            if (!Files.exists(file)) return;
            String ts = Instant.now().toString().replace(':','-');
            Path bad = dir.resolve("command-stacks.bad." + ts + "." + reason + ".ser");
            Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Failed to backup bad command stack file (ignored)", ex);
        }
    }

/**
 * toMementos.
 *
 * @param executed TODO
 * @return TODO
 */
    private static List<CommandMemento> toMementos(List<CommandManager.ExecutedCommand> executed) {
        List<CommandMemento> out = new ArrayList<>();
        for (var ex : executed) {
            out.add(toMemento(ex));
        }
        return out;
    }

/**
 * toMemento.
 *
 * @param exec TODO
 * @return TODO
 */
    private static CommandMemento toMemento(CommandManager.ExecutedCommand exec) {
        Command cmd = exec.command();
        Instant at = exec.executedAt();

        // Batch
        if (cmd instanceof BatchCommand bc) {
            List<CommandMemento> kids = new ArrayList<>();
            for (Command child : bc.children()) {
                // For child executedAt, reuse parent at (good enough for stack restore)
                kids.add(toMemento(new CommandManager.ExecutedCommand(child.id(), child, at)));
            }
            return CommandMemento.forBatch(cmd.id(), cmd.label(), at, cmd.isUndoable(), kids);
        }

        // File op
        if (cmd instanceof FileOperationCommand foc) {
            var req = foc.request();
            var sources = req.sources().stream().map(p -> p.toString()).toList();
            String target = req.targetDirectory() == null ? null : req.targetDirectory().toString();
            CommandMemento.Kind kind = switch (req.type()) {
                case COPY -> CommandMemento.Kind.COPY;
                case MOVE -> CommandMemento.Kind.MOVE;
                case DELETE -> CommandMemento.Kind.DELETE;
                default -> CommandMemento.Kind.UNKNOWN;
            };
            return CommandMemento.forFileOp(
                    kind,
                    cmd.id(),
                    cmd.label(),
                    at,
                    cmd.isUndoable(),
                    req.type().name(),
                    sources,
                    target,
                    req.newName(),
                    req.overwrite(),
                    req.skipConflicts(),
                    req.sendToTrash()
            );
        }

        return CommandMemento.unknown(cmd.id(), cmd.label(), at, cmd.isUndoable());
    }

    /** Java-serialized persisted state. */
    static final class PersistedState implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        final List<CommandMemento> undo;
        final List<CommandMemento> redo;
        PersistedState(List<CommandMemento> undo, List<CommandMemento> redo) {
            this.undo = undo;
            this.redo = redo;
        }
    }
}
