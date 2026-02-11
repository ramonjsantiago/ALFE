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

    public DirectoryListingService(Executor ioExecutor, FileMetadataService metadata) {
        this.ioExecutor = ioExecutor;
        this.metadata = metadata;
    }

    public CompletableFuture<List<FileItem>> listAsync(Path dir, ListingOptions opts, CancellationToken token) {
        return CompletableFuture.supplyAsync(() -> listSync(dir, opts, token), ioExecutor);
    }

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
                String type = metadata.detectFileType(p);
                String size = metadata.humanReadableSize(p);
                String mod = metadata.lastModifiedLocalString(p);

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

    private static boolean isHiddenSafe(Path p) {
        try {
            return Files.isHidden(p);
        } catch (IOException e) {
            return false;
        }
    }
}
