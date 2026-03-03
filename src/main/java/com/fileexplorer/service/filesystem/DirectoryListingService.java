package com.fileexplorer.service.filesystem;

import com.fileexplorer.model.FileItem;
import com.fileexplorer.model.FileStatus;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * DirectoryListingService.
 * <p>
 * Auto-generated API documentation for this type.
 */
public final class DirectoryListingService {

    public record ListingOptions(int maxEntries, boolean includeHidden, boolean foldersFirst) { }

    public interface CancellationToken {
        boolean isCancelled();
    }

    public static final class CancellationSource {
        private volatile boolean cancelled = false;
        public void cancel() { cancelled = true; }
        public CancellationToken token() { return () -> cancelled; }
    }

    private final Executor ioExecutor;
    private final FileMetadataService metadata;

/**
 * DirectoryListingService.
 *
 * @param ioExecutor TODO
 * @param metadata TODO
 * @return TODO
 */
    public DirectoryListingService(Executor ioExecutor, FileMetadataService metadata) {
        this.ioExecutor = ioExecutor;
        this.metadata = metadata;
    }

    public CompletableFuture<List<FileItem>> listAsync(Path dir, ListingOptions opts, CancellationToken token) {
        return CompletableFuture.supplyAsync(() -> listSync(dir, opts, token), ioExecutor);
    }

/**
 * listSync.
 *
 * @param dir TODO
 * @param opts TODO
 * @param token TODO
 * @return TODO
 */
    public List<FileItem> listSync(Path dir, ListingOptions opts, CancellationToken token) {
        if (token != null && token.isCancelled()) return List.of();
        if (dir == null || !Files.isDirectory(dir)) return List.of();

        int limit = Math.max(1, opts.maxEntries());
        boolean includeHidden = opts.includeHidden();
        boolean foldersFirst = opts.foldersFirst();

        List<FileItem> out = new ArrayList<>(Math.min(limit, 2048));

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (token != null && token.isCancelled()) return List.of();

                if (!includeHidden && isHiddenSafe(p)) {
                    continue;
                }

                String name = (p.getFileName() != null) ? p.getFileName().toString() : p.toString();
                boolean lazy = lazyMetadata();

                String type = metadata.detectFileType(p);
                String size = lazy ? "" : metadata.humanReadableSize(p);
                String mod  = lazy ? "" : metadata.lastModifiedLocalString(p);

                // Phase 1: placeholder status (A)
                out.add(new FileItem(p, name, type, size, mod, FileStatus.NONE));

                if (out.size() >= limit) break;
            }
        } catch (IOException ignore) {
        }

        if (foldersFirst) {
            out.sort(Comparator
                    .comparing((FileItem fi) -> !Files.isDirectory(fi.path()))
                    .thenComparing(FileItem::name, String.CASE_INSENSITIVE_ORDER));
        } else {
            out.sort(Comparator.comparing(FileItem::name, String.CASE_INSENSITIVE_ORDER));
        }
        return out;
    }

/**
 * isHiddenSafe.
 *
 * @param p TODO
 * @return TODO
 */
    private static boolean isHiddenSafe(Path p) {
        try {
            return Files.isHidden(p);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean lazyMetadata() {
        String v = System.getProperty("fileexplorer.metadata.lazy");
        if (v == null || v.isBlank()) return true;
        return Boolean.parseBoolean(v.trim());
    }


/**
 * Progressive directory listing: emits batches as entries are discovered.
 * Runs on the caller's thread; intended to be invoked from an IO executor.
 */
public void listProgressiveSync(
        Path dir,
        ListingOptions opts,
        CancellationToken token,
        int batchSize,
        java.util.function.Consumer<java.util.List<FileItem>> onBatch
) {
    if (onBatch == null) return;
    if (token != null && token.isCancelled()) return;
    if (dir == null || !Files.isDirectory(dir)) return;

    int limit = Math.max(1, opts.maxEntries());
    boolean includeHidden = opts.includeHidden();
    boolean foldersFirst = opts.foldersFirst();

    int effectiveBatch = Math.max(25, batchSize);

    java.util.List<FileItem> batch = new java.util.ArrayList<>(effectiveBatch);
    int emitted = 0;

    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
        for (Path p : ds) {
            if (token != null && token.isCancelled()) return;
            if (emitted >= limit) break;

            if (!includeHidden && isHiddenSafe(p)) {
                continue;
            }

            String name = (p.getFileName() != null) ? p.getFileName().toString() : p.toString();
            boolean lazy = lazyMetadata();

            String type = metadata.detectFileType(p);
            String size = lazy ? "" : metadata.humanReadableSize(p);
            String mod  = lazy ? "" : metadata.lastModifiedLocalString(p);

            batch.add(new FileItem(p, name, type, size, mod, FileStatus.NONE));
            emitted++;

            if (batch.size() >= effectiveBatch) {
                onBatch.accept(java.util.List.copyOf(batch));
                batch.clear();
            }
        }
    } catch (Exception ex) {
        // Bubble as unchecked; caller decides how to report.
        throw new RuntimeException(ex);
    }

    if (!batch.isEmpty()) {
        onBatch.accept(java.util.List.copyOf(batch));
    }
}

}