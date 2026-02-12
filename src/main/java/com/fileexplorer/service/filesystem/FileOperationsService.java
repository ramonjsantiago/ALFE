package com.fileexplorer.service.filesystem;

import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.event.events.FileOpFailed;
import com.fileexplorer.service.event.events.FileOpProgress;
import com.fileexplorer.service.event.events.FileOpStarted;
import com.fileexplorer.service.event.events.FileOpSucceeded;

import java.awt.Desktop;
import java.awt.HeadlessException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background file operations with best-effort progress via EventBus.
 *
 * <p>Scope (Phase 3.6.0): Copy / Cut(Move) / Paste, Delete, Rename.
 * This intentionally keeps the implementation conservative and synchronous per-request
 * (one op at a time per invocation), while running off the FX thread.</p>
 */
public final class FileOperationsService {

    public enum ConflictPolicy { RENAME, REPLACE, SKIP }


    public static final String OP_DELETE = "DELETE";
    public static final String OP_COPY = "COPY";
    public static final String OP_MOVE = "MOVE";
    public static final String OP_RENAME = "RENAME";
    public static final String OP_TRASH = "TRASH";

    private final EventBus eventBus;
    private final ExecutorService ioExecutor;
    private final AtomicLong seq = new AtomicLong(1);

    public FileOperationsService(EventBus eventBus, ExecutorService ioExecutor) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
    }

    public long delete(List<Path> sources, boolean recursive) {
        final long id = seq.getAndIncrement();
        final List<Path> src = safeList(sources);
        ioExecutor.execute(() -> {
            eventBus.publish(new FileOpStarted(id, OP_DELETE, src, null));
            try {
                int total = src.size();
                int done = 0;
                for (Path p : src) {
                    if (p == null) continue;
                    if (Files.isDirectory(p) && recursive) {
                        deleteDirectoryRecursive(p);
                    } else {
                        Files.deleteIfExists(p);
                    }
                    done++;
                    eventBus.publish(new FileOpProgress(id, OP_DELETE, p, done, total));
                }
                eventBus.publish(new FileOpSucceeded(id, OP_DELETE, src, null));
            } catch (Throwable t) {
                eventBus.publish(new FileOpFailed(id, OP_DELETE, src, null, messageOf(t), t));
            }
        });
        return id;
    }


/**
 * Move the given sources to the platform trash / recycle bin (best-effort).
 * On Windows, this uses {@link Desktop#moveToTrash(java.io.File)}.
 *
 * <p>If the platform does not support trash, callers should fall back to permanent delete.</p>
 */
public long trash(List<Path> sources) {
    final long id = seq.getAndIncrement();
    final List<Path> src = safeList(sources);
    ioExecutor.execute(() -> {
        eventBus.publish(new FileOpStarted(id, OP_TRASH, src, null));
        try {
            if (src.isEmpty()) {
                eventBus.publish(new FileOpSucceeded(id, OP_TRASH, src, null));
                return;
            }

            Desktop desktop;
            try {
                if (!Desktop.isDesktopSupported()) {
                    throw new UnsupportedOperationException("Trash not supported on this platform.");
                }
                desktop = Desktop.getDesktop();
                if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                    throw new UnsupportedOperationException("Trash not supported on this platform.");
                }
            } catch (HeadlessException ex) {
                throw new UnsupportedOperationException("Trash not supported (headless).");
            }

            int total = src.size();
            int done = 0;
            int ok = 0;
            List<Path> failed = new ArrayList<>();

            for (Path p : src) {
                if (p == null) continue;
                boolean moved = false;
                try {
                    moved = desktop.moveToTrash(p.toFile());
                } catch (Throwable t) {
                    moved = false;
                }
                if (moved) {
                    ok++;
                } else {
                    failed.add(p);
                }
                done++;
                eventBus.publish(new FileOpProgress(id, OP_TRASH, p, done, total));
            }

            if (!failed.isEmpty()) {
                throw new IOException("Could not move " + failed.size() + " item(s) to trash.");
            }
            if (ok == 0) {
                throw new IOException("Could not move selection to trash.");
            }

            eventBus.publish(new FileOpSucceeded(id, OP_TRASH, src, null));
        } catch (Throwable t) {
            eventBus.publish(new FileOpFailed(id, OP_TRASH, src, null, messageOf(t), t));
        }
    });
    return id;
}

    public long rename(Path source, String newName) {
        final long id = seq.getAndIncrement();
        final Path src = source;
        final String nn = (newName == null) ? "" : newName.trim();
        ioExecutor.execute(() -> {
            eventBus.publish(new FileOpStarted(id, OP_RENAME, (src == null) ? List.of() : List.of(src), null));
            try {
                if (src == null) {
                    throw new IllegalArgumentException("No source selected");
                }
                if (nn.isBlank()) {
                    throw new IllegalArgumentException("Name cannot be blank");
                }
                Path target = src.resolveSibling(nn);
                Files.move(src, target, StandardCopyOption.ATOMIC_MOVE);
                eventBus.publish(new FileOpSucceeded(id, OP_RENAME, List.of(src), null));
            } catch (Throwable t) {
                eventBus.publish(new FileOpFailed(id, OP_RENAME, (src == null) ? List.of() : List.of(src), null, messageOf(t), t));
            }
        });
        return id;
    }

    public long copy(List<Path> sources, Path targetDirectory) {
        return copy(sources, targetDirectory, ConflictPolicy.RENAME);
    }

    public long copy(List<Path> sources, Path targetDirectory, ConflictPolicy conflictPolicy) {
        final long id = seq.getAndIncrement();
        final List<Path> src = safeList(sources);
        final Path targetDir = targetDirectory;
        ioExecutor.execute(() -> {
            eventBus.publish(new FileOpStarted(id, OP_COPY, src, targetDir));
            try {
                if (targetDir == null) {
                    throw new IllegalArgumentException("No target directory");
                }
                int total = src.size();
                int done = 0;
                for (Path p : src) {
                    if (p == null) continue;
                    String name = p.getFileName() != null ? p.getFileName().toString() : "item";
                    Path dest = targetDir.resolve(name);
                    if (Files.exists(dest)) {
                        if (conflictPolicy == ConflictPolicy.SKIP) {
                            done++;
                            eventBus.publish(new FileOpProgress(id, OP_COPY, p, done, total));
                            continue;
                        } else if (conflictPolicy == ConflictPolicy.RENAME) {
                            dest = uniqueTarget(targetDir, name);
                        } else {
                            // REPLACE
                            if (Files.isDirectory(dest)) {
                                deleteDirectoryRecursive(dest);
                            } else {
                                Files.deleteIfExists(dest);
                            }
                        }
                    }
                    if (Files.isDirectory(p)) {
                        copyDirectoryRecursive(p, dest);
                    } else {
                        if (conflictPolicy == ConflictPolicy.REPLACE) {
                            Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                    }
                    done++;
                    eventBus.publish(new FileOpProgress(id, OP_COPY, p, done, total));
                }
                eventBus.publish(new FileOpSucceeded(id, OP_COPY, src, targetDir));
            } catch (Throwable t) {
                eventBus.publish(new FileOpFailed(id, OP_COPY, src, targetDir, messageOf(t), t));
            }
        });
        return id;
    }

    public long move(List<Path> sources, Path targetDirectory) {
        return move(sources, targetDirectory, ConflictPolicy.RENAME);
    }

    public long move(List<Path> sources, Path targetDirectory, ConflictPolicy conflictPolicy) {
        final long id = seq.getAndIncrement();
        final List<Path> src = safeList(sources);
        final Path targetDir = targetDirectory;
        ioExecutor.execute(() -> {
            eventBus.publish(new FileOpStarted(id, OP_MOVE, src, targetDir));
            try {
                if (targetDir == null) {
                    throw new IllegalArgumentException("No target directory");
                }
                int total = src.size();
                int done = 0;
                for (Path p : src) {
                    if (p == null) continue;
                    String name = p.getFileName() != null ? p.getFileName().toString() : "item";
                    Path dest = targetDir.resolve(name);
                    if (Files.exists(dest)) {
                        if (conflictPolicy == ConflictPolicy.SKIP) {
                            done++;
                            eventBus.publish(new FileOpProgress(id, OP_MOVE, p, done, total));
                            continue;
                        } else if (conflictPolicy == ConflictPolicy.RENAME) {
                            dest = uniqueTarget(targetDir, name);
                        } else {
                            // REPLACE
                            if (Files.isDirectory(dest)) {
                                deleteDirectoryRecursive(dest);
                            } else {
                                Files.deleteIfExists(dest);
                            }
                        }
                    }
                    try {
                        if (conflictPolicy == ConflictPolicy.REPLACE) {
                            Files.move(p, dest, StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            Files.move(p, dest, StandardCopyOption.ATOMIC_MOVE);
                        }
                    } catch (IOException ex) {
                        // Fall back across devices.
                        if (Files.isDirectory(p)) {
                            copyDirectoryRecursive(p, dest);
                            deleteDirectoryRecursive(p);
                        } else {
                            Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                            Files.deleteIfExists(p);
                        }
                    }
                    done++;
                    eventBus.publish(new FileOpProgress(id, OP_MOVE, p, done, total));
                }
                eventBus.publish(new FileOpSucceeded(id, OP_MOVE, src, targetDir));
            } catch (Throwable t) {
                eventBus.publish(new FileOpFailed(id, OP_MOVE, src, targetDir, messageOf(t), t));
            }
        });
        return id;
    }

    private static List<Path> safeList(List<Path> sources) {
        if (sources == null || sources.isEmpty()) return Collections.emptyList();
        return List.copyOf(sources);
    }

    private static String messageOf(Throwable t) {
        if (t == null) return "";
        String m = t.getMessage();
        if (m == null || m.isBlank()) return t.getClass().getSimpleName();
        return m;
    }

    /**
     * Returns a non-existing child path in targetDir by applying an Explorer-like " - Copy" suffix.
     */
    private static Path uniqueTarget(Path targetDir, String name) throws IOException {
        Path candidate = targetDir.resolve(name);
        if (!Files.exists(candidate)) return candidate;

        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }

        // Try " - Copy" then " - Copy (n)"
        String copy1 = base + " - Copy" + ext;
        candidate = targetDir.resolve(copy1);
        if (!Files.exists(candidate)) return candidate;

        for (int i = 2; i < 1000; i++) {
            String nn = base + " - Copy (" + i + ")" + ext;
            candidate = targetDir.resolve(nn);
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IOException("Could not find unique target name for: " + name);
    }

    /**
     * Computes a unique target path in the given directory for the desired name.
     * Public so UI can implement "Rename" conflict policy without duplicating logic.
     */
    public static Path computeUniqueTarget(Path targetDir, String desiredName) throws IOException {
        return uniqueTarget(targetDir, desiredName);
    }


    private static void deleteDirectoryRecursive(Path dir) throws IOException {
        if (dir == null) return;
        if (!Files.exists(dir)) return;

        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.deleteIfExists(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyDirectoryRecursive(Path sourceDir, Path targetDir) throws IOException {
        if (sourceDir == null) return;
        Files.createDirectories(targetDir);

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = sourceDir.relativize(dir);
                Path dest = targetDir.resolve(rel);
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = sourceDir.relativize(file);
                Path dest = targetDir.resolve(rel);
                Files.copy(file, dest, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
