package com.fileexplorer.service.ops;

import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.event.events.FileOpCancelled;
import com.fileexplorer.service.event.events.FileOpCompleted;
import com.fileexplorer.service.event.events.FileOpFailed;
import com.fileexplorer.service.event.events.FileOpProgress;
import com.fileexplorer.service.event.events.FileOpStarted;

import java.io.IOException;
import java.awt.Desktop;
import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background file operations runner with best-effort cancellation and progress events.
 *
 * <p>Progress is item-based (not byte-accurate) to keep I/O overhead low.</p>
 */
public final class FileOperationService {

    private final EventBus eventBus;
    private final ExecutorService ioExecutor;

    private final AtomicLong jobSeq = new AtomicLong(1);

    private volatile long activeJobId = -1L;
    private volatile Future<?> activeFuture;
    private volatile AtomicBoolean activeCancel;

/**
 * FileOperationService.
 *
 * @param eventBus TODO
 * @param ioExecutor TODO
 * @return TODO
 */
    public FileOperationService(EventBus eventBus, ExecutorService ioExecutor) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
    }

    public long submit(FileOperationRequest req) {
        Objects.requireNonNull(req, "req");
        long jobId = jobSeq.getAndIncrement();

        // cancel any prior operation (best effort)
        cancelActive();

        AtomicBoolean cancel = new AtomicBoolean(false);
        activeCancel = cancel;
        activeJobId = jobId;

        eventBus.publish(new FileOpStarted(jobId, req.type(), req.sources().size()));

        Future<?> f = ioExecutor.submit(() -> {
            try {
                run(jobId, req, cancel);
                if (cancel.get()) {
                    eventBus.publish(new FileOpCancelled(jobId));
                } else {
                    eventBus.publish(new FileOpCompleted(jobId));
                }
            } catch (Throwable t) {
                eventBus.publish(new FileOpFailed(jobId, t));
            } finally {
                // clear active only if still current
                if (activeJobId == jobId) {
                    activeJobId = -1L;
                    activeFuture = null;
                    activeCancel = null;
                }
            }
        });
        activeFuture = f;
        return jobId;
    }

/**
 * cancelActive.
 *
 */
    public void cancelActive() {
        Future<?> f = activeFuture;
        AtomicBoolean c = activeCancel;
        if (c != null) c.set(true);
        if (f != null) f.cancel(true);
    }

/**
 * hasActiveJob.
 *
 * @return TODO
 */
    public boolean hasActiveJob() {
        return activeJobId > 0;
    }

/**
 * run.
 *
 * @param jobId TODO
 * @param req TODO
 * @param cancel TODO
 */
    private void run(long jobId, FileOperationRequest req, AtomicBoolean cancel) throws IOException {
        List<Path> sources = req.sources();
        int total = sources.size();
        int processed = 0;

        switch (req.type()) {
            case COPY -> {
                Path targetDir = Objects.requireNonNull(req.targetDirectory(), "targetDirectory");
                for (Path src : sources) {
                    if (cancel.get()) return;
                    copyOne(src, targetDir, req.overwrite(), cancel);
                    processed++;
                    eventBus.publish(new FileOpProgress(jobId, processed, total, src));
                }
            }
            case MOVE -> {
                Path targetDir = Objects.requireNonNull(req.targetDirectory(), "targetDirectory");
                for (Path src : sources) {
                    if (cancel.get()) return;
                    moveOne(src, targetDir, req.overwrite(), cancel);
                    processed++;
                    eventBus.publish(new FileOpProgress(jobId, processed, total, src));
                }
            }
            case DELETE -> {
                for (Path src : sources) {
                    if (cancel.get()) return;
                    deleteOne(src, req.sendToTrash(), cancel);
                    processed++;
                    eventBus.publish(new FileOpProgress(jobId, processed, total, src));
                }
            }
            case RENAME -> {
                if (sources.size() != 1) {
                    throw new IOException("Rename expects exactly one source.");
                }
                Path src = sources.get(0);
                String newName = Objects.requireNonNull(req.newName(), "newName");
                renameOne(src, newName, req.overwrite(), cancel);
                processed = 1;
                eventBus.publish(new FileOpProgress(jobId, processed, total, src));
            }
        }
    }

/**
 * copyOne.
 *
 * @param src TODO
 * @param targetDir TODO
 * @param overwrite TODO
 * @param cancel TODO
 */
    private static void copyOne(Path src, Path targetDir, boolean overwrite, AtomicBoolean cancel) throws IOException {
        Path dst = resolveNonColliding(targetDir, src.getFileName() != null ? src.getFileName().toString() : "item", overwrite);
        if (Files.isDirectory(src)) {
            copyDirectoryRecursive(src, dst, overwrite, cancel);
        } else {
            copyFile(src, dst, overwrite);
        }
    }

/**
 * moveOne.
 *
 * @param src TODO
 * @param targetDir TODO
 * @param overwrite TODO
 * @param cancel TODO
 */
    private static void moveOne(Path src, Path targetDir, boolean overwrite, AtomicBoolean cancel) throws IOException {
        Path dst = resolveNonColliding(targetDir, src.getFileName() != null ? src.getFileName().toString() : "item", overwrite);
        try {
            CopyOption[] opts = overwrite ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new CopyOption[0];
            Files.move(src, dst, opts);
        } catch (FileSystemException ex) {
            // Cross-volume or other move limitations -> copy + delete.
            if (Files.isDirectory(src)) {
                copyDirectoryRecursive(src, dst, overwrite, cancel);
                deleteDirectoryRecursive(src, cancel);
            } else {
                copyFile(src, dst, overwrite);
                Files.deleteIfExists(src);
            }
        }
    }

/**
 * deleteOne.
 *
 * @param src TODO
 * @param sendToTrash TODO
 * @param cancel TODO
 */
    private static void deleteOne(Path src, boolean sendToTrash, AtomicBoolean cancel) throws IOException {
    if (cancel.get()) return;

    // Prefer OS trash / recycle bin when requested.
    if (sendToTrash) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop d = Desktop.getDesktop();
                boolean moved = d.moveToTrash(src.toFile());
                if (moved) return;
            }
        } catch (Throwable ignored) {
            // fall through to permanent delete
        }
    }

    if (Files.isDirectory(src)) {
        deleteDirectoryRecursive(src, cancel);
    } else {
        Files.deleteIfExists(src);
    }
}


/**
 * renameOne.
 *
 * @param src TODO
 * @param newName TODO
 * @param overwrite TODO
 * @param cancel TODO
 */
    private static void renameOne(Path src, String newName, boolean overwrite, AtomicBoolean cancel) throws IOException {
        if (cancel.get()) return;
        Path parent = src.getParent();
        if (parent == null) throw new IOException("Cannot rename root.");
        Path dst = parent.resolve(newName);
        if (!overwrite && Files.exists(dst)) {
            dst = resolveNonColliding(parent, newName, false);
        }
            CopyOption[] opts = overwrite ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new CopyOption[0];
            Files.move(src, dst, opts);
    }

/**
 * copyFile.
 *
 * @param src TODO
 * @param dst TODO
 * @param overwrite TODO
 */
    private static void copyFile(Path src, Path dst, boolean overwrite) throws IOException {
        if (overwrite) {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } else {
            Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

/**
 * copyDirectoryRecursive.
 *
 * @param srcDir TODO
 * @param dstDir TODO
 * @param overwrite TODO
 * @param cancel TODO
 */
    private static void copyDirectoryRecursive(Path srcDir, Path dstDir, boolean overwrite, AtomicBoolean cancel) throws IOException {
        Files.createDirectories(dstDir);
        Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (cancel.get()) return FileVisitResult.TERMINATE;
                Path rel = srcDir.relativize(dir);
                Path target = dstDir.resolve(rel);
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
/**
 * visitFile.
 *
 * @param file TODO
 * @param attrs TODO
 * @return TODO
 */
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (cancel.get()) return FileVisitResult.TERMINATE;
                Path rel = srcDir.relativize(file);
                Path target = dstDir.resolve(rel);
                if (overwrite) {
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

/**
 * deleteDirectoryRecursive.
 *
 * @param dir TODO
 * @param cancel TODO
 */
    private static void deleteDirectoryRecursive(Path dir, AtomicBoolean cancel) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (cancel.get()) return FileVisitResult.TERMINATE;
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
/**
 * postVisitDirectory.
 *
 * @param d TODO
 * @param exc TODO
 * @return TODO
 */
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (cancel.get()) return FileVisitResult.TERMINATE;
                Files.deleteIfExists(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

/**
 * resolveNonColliding.
 *
 * @param targetDir TODO
 * @param name TODO
 * @param overwrite TODO
 * @return TODO
 */
    private static Path resolveNonColliding(Path targetDir, String name, boolean overwrite) throws IOException {
        Path dst = targetDir.resolve(name);
        if (overwrite) return dst;
        if (!Files.exists(dst)) return dst;

        // explorer-ish: "name - Copy", "name - Copy (2)" ...
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }

        Path candidate;
        int i = 1;
        do {
            String suffix = (i == 1) ? " - Copy" : " - Copy (" + i + ")";
            candidate = targetDir.resolve(base + suffix + ext);
            i++;
        } while (Files.exists(candidate));

        return candidate;
    }
}
