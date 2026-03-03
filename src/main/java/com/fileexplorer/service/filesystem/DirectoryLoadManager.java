package com.fileexplorer.service.filesystem;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.model.FileItem;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Coordinates directory listing work and provides best-effort cancellation.
 *
 * <p>Phase 2.x introduced cancellation semantics in {@link DirectoryListingService} via a
 * {@link DirectoryListingService.CancellationSource}. This manager cancels the previous in-flight
 * request when a new directory is requested.</p>
 */
public final class DirectoryLoadManager {

    private final ExplorerContext context;
    private final DirectoryListingService listingService;
    private final Executor ioExecutor;

    private volatile DirectoryListingService.CancellationSource activeCancel;
    private volatile Path activeDir;

    public DirectoryLoadManager(
            ExplorerContext context,
            DirectoryListingService listingService,
            Executor ioExecutor
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.listingService = Objects.requireNonNull(listingService, "listingService");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
    }

    /** Cancel any in-flight listing (best-effort). */
    public void cancelActive() {
        DirectoryListingService.CancellationSource c = activeCancel;
        if (c != null) {
            c.cancel();
        }
    }

    /**
     * Preferred Phase 3.x API: matches {@link com.fileexplorer.service.coordinator.DirectoryCoordinator}.
     */
    public void load(
            Path dir,
            boolean includeHidden,
            Consumer<List<FileItem>> onSuccess,
            Consumer<Throwable> onFailure
    ) {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");

        // cancel previous request
        cancelActive();

        activeDir = dir;
        DirectoryListingService.CancellationSource cancel = new DirectoryListingService.CancellationSource();
        activeCancel = cancel;

        DirectoryListingService.ListingOptions opts = buildListingOptions(includeHidden);

        CompletableFuture<List<FileItem>> fut = listingService.listAsync(dir, opts, cancel.token());

        fut.whenCompleteAsync((items, err) -> {
            // ignore stale completions
            if (!Objects.equals(activeDir, dir) || activeCancel != cancel) {
                return;
            }

            if (err != null) {
                onFailure.accept(err);
            } else {
                onSuccess.accept(items);
            }
        }, ioExecutor);
    }

    /**
     * Backward-compatible helper: retains the earlier BiConsumer signature.
     */
    public void requestDirectory(
            Path dir,
            boolean includeHidden,
            BiConsumer<Path, List<FileItem>> onSuccess,
            BiConsumer<Path, Throwable> onFailure
    ) {
        load(
                dir,
                includeHidden,
                items -> onSuccess.accept(dir, items),
                err -> onFailure.accept(dir, err)
        );
    }

/**
 * buildListingOptions.
 *
 * @param includeHidden TODO
 * @return TODO
 */
    private DirectoryListingService.ListingOptions buildListingOptions(boolean includeHidden) {
        // Keep this simple and deterministic.
        // Use system properties (the app already prints them on boot).
        int maxEntries = intProp("fileexplorer.maxDirEntries", 5000);
        int hardMaxEntries = intProp("fileexplorer.safeMode.maxDirEntries.hardMax", 2000);

        boolean safeMode = boolProp("fileexplorer.safeMode", false);

        int effectiveMax = safeMode ? intProp("fileexplorer.safeMode.maxDirEntries", 500) : maxEntries;
        // If safeMode is enabled, allow the hard cap to constrain. Otherwise keep it at least as large as max.
        int effectiveHardMax = safeMode ? hardMaxEntries : Math.max(hardMaxEntries, maxEntries);
        if (effectiveMax > effectiveHardMax) {
            effectiveMax = effectiveHardMax;
        }

        // DirectoryListingService.ListingOptions is: (maxEntries, includeHidden, foldersFirst)
        boolean foldersFirst = true;
        return new DirectoryListingService.ListingOptions(effectiveMax, includeHidden, foldersFirst);
    }

/**
 * intProp.
 *
 * @param key TODO
 * @param def TODO
 * @return TODO
 */
    private static int intProp(String key, int def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

/**
 * boolProp.
 *
 * @param key TODO
 * @param def TODO
 * @return TODO
 */
    private static boolean boolProp(String key, boolean def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        return Boolean.parseBoolean(v.trim());
    }


/**
 * Phase 4A: Progressive load for fast first render.
 * Emits batches on the IO executor thread (caller should marshal to FX thread).
 */
public void loadProgressive(
        Path dir,
        boolean includeHidden,
        int batchSize,
        java.util.function.Consumer<java.util.List<FileItem>> onBatch,
        Runnable onDone,
        java.util.function.Consumer<Throwable> onFailure
) {
if (dir == null) {
    // Defensive: some selection paths can briefly pass null during TreeView initialization.
    // Treat as a no-op and complete immediately to avoid surfacing an exception dialog.
    try {
        onDone.run();
    } catch (Exception ignored) {
    }
    return;
}
    Objects.requireNonNull(onBatch, "onBatch");
    Objects.requireNonNull(onDone, "onDone");
    Objects.requireNonNull(onFailure, "onFailure");

    cancelActive();
    activeDir = dir;
    DirectoryListingService.CancellationSource cancel = new DirectoryListingService.CancellationSource();
    activeCancel = cancel;

    DirectoryListingService.ListingOptions opts = buildListingOptions(includeHidden);

    ioExecutor.execute(() -> {
        try {
            // ignore stale start
            if (!Objects.equals(activeDir, dir) || activeCancel != cancel) return;

            listingService.listProgressiveSync(dir, opts, cancel.token(), batchSize, batch -> {
                // stop if cancelled/stale
                if (cancel.token().isCancelled()) return;
                if (!Objects.equals(activeDir, dir) || activeCancel != cancel) return;
                onBatch.accept(batch);
            });

            if (!Objects.equals(activeDir, dir) || activeCancel != cancel) return;
            onDone.run();
        } catch (Throwable t) {
            if (!Objects.equals(activeDir, dir) || activeCancel != cancel) return;
            onFailure.accept(t);
        }
    });
}

}
