package com.fileexplorer.thumb;

import javafx.application.Platform;

import java.io.*;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * FileOperationManager - executes file operations on virtual threads and reports progress.
 * Uses Java 25 virtual threads via Executors.newThreadPerTaskExecutor.
 */
public class FileOperationManager {

    public interface ProgressListener {
        /**
         * Called periodically with bytesTransferred and totalBytes (totalBytes may be -1 when unknown).
         */
        void onProgress(long bytesTransferred, long totalBytes);

        /**
         * Called when operation completes successfully.
         */
        void onComplete();

        /**
         * Called on error or cancellation.
         */
        void onError(Throwable t);

        /**
         * Called when operation is cancelled.
         */
        void onCancelled();
    }

    public static class CancellableOperation {
        private final Future<?> future;

        CancellableOperation(Future<?> future) {
            this.future = future;
        }

        public boolean cancel() {
            return future.cancel(true);
        }

        public boolean isDone() {
            return future.isDone();
        }
    }

    private final ExecutorService ioExecutor;

    public FileOperationManager() {
        // Use a per-task virtual thread executor when available. Otherwise fallback to cached thread pool.
        ExecutorService exec;
        try {
            exec = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        } catch (Throwable t) {
            exec = Executors.newCachedThreadPool();
        }
        this.ioExecutor = exec;
    }

    public CancellableOperation copy(Path source, Path target, ProgressListener listener) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        Future<?> f = ioExecutor.submit(() -> {
            try {
                long total = Files.isRegularFile(source) ? Files.size(source) : -1L;
                if (Files.isDirectory(source)) {
                    // simple directory copy: recursively copy files, report per-file progress
                    Files.walk(source).forEach(p -> {
                        try {
                            Path rel = source.relativize(p);
                            Path dest = target.resolve(rel);
                            if (Files.isDirectory(p)) {
                                Files.createDirectories(dest);
                            } else {
                                copyFileWithProgress(p, dest, listener, total);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    Platform.runLater(listener::onComplete);
                } else {
                    Files.createDirectories(target.getParent() != null ? target.getParent() : target);
                    copyFileWithProgress(source, target, listener, total);
                    Platform.runLater(listener::onComplete);
                }
            } catch (CancellationException ce) {
                Platform.runLater(listener::onCancelled);
            } catch (Throwable t) {
                Platform.runLater(() -> listener.onError(t));
            }
        });
        return new CancellableOperation(f);
    }

    private void copyFileWithProgress(Path src, Path dst, ProgressListener listener, long total) throws IOException {
        try (InputStream in = Files.newInputStream(src);
             OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[8192];
            long transferred = 0;
            int read;
            while ((read = in.read(buf)) != -1) {
                // check for interruption to support cancellation
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Copy cancelled");
                }
                out.write(buf, 0, read);
                transferred += read;
                final long tTransferred = transferred;
                final long tTotal = total;
                // Report progress on FX thread
                Platform.runLater(() -> listener.onProgress(tTransferred, tTotal));
            }
            out.flush();
        }
    }

    public CancellableOperation move(Path source, Path target, ProgressListener listener) {
        // Move implemented as rename where possible; fallback to copy+delete with progress
        Future<?> f = ioExecutor.submit(() -> {
            try {
                try {
                    Files.createDirectories(target.getParent() != null ? target.getParent() : target);
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    Platform.runLater(listener::onComplete);
                } catch (IOException moveEx) {
                    // fallback: copy then delete
                    CancellableOperation copyOp = copy(source, target, listener);
                    try {
                        copyOp.future.get(); // wait for copy to finish
                        Files.delete(source);
                        Platform.runLater(listener::onComplete);
                    } catch (CancellationException ce) {
                        Platform.runLater(listener::onCancelled);
                    }
                }
            } catch (Throwable t) {
                Platform.runLater(() -> listener.onError(t));
            }
        });
        return new CancellableOperation(f);
    }

    public CancellableOperation delete(Path path, ProgressListener listener) {
        Future<?> f = ioExecutor.submit(() -> {
            try {
                // for directories, delete recursively and report per-file progress
                if (Files.isDirectory(path)) {
                    // count files for progress estimate
                    long totalFiles = Files.walk(path).filter(Files::isRegularFile).count();
                    final long[] processed = {0};
                    Files.walk(path)
                            .sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    if (Thread.currentThread().isInterrupted()) throw new CancellationException("Delete cancelled");
                                    Files.deleteIfExists(p);
                                    processed[0]++;
                                    final long pt = processed[0];
                                    Platform.runLater(() -> listener.onProgress(pt, totalFiles));
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                    Platform.runLater(listener::onComplete);
                } else {
                    Files.deleteIfExists(path);
                    Platform.runLater(listener::onComplete);
                }
            } catch (CancellationException ce) {
                Platform.runLater(listener::onCancelled);
            } catch (Throwable t) {
                Platform.runLater(() -> listener.onError(t));
            }
        });
        return new CancellableOperation(f);
    }

    public void shutdown() {
        ioExecutor.shutdownNow();
    }
}
