package com.fileexplorer.services;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class FolderWatcher {

    private final ExecutorService executor;
    private final Map<Path, ScheduledFuture<?>> pendingUpdates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Consumer<List<File>> onChange;
    private final long coalesceDelayMs = 100;

    public FolderWatcher(Consumer<List<File>> onChange) {
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        this.onChange = onChange;
    }

    public void watchFolder(Path folder) throws Exception {
        WatchService watchService = FileSystems.getDefault().newWatchService();
        folder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                List<File> batch = new ArrayList<>();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = folder.resolve((Path) event.context());
                    batch.add(changed.toFile());
                }
                // Batch coalescing
                ScheduledFuture<?> previous = pendingUpdates.put(folder, scheduler.schedule(() -> {
                    onChange.accept(batch);
                    pendingUpdates.remove(folder);
                }, coalesceDelayMs, TimeUnit.MILLISECONDS));
                if (previous != null) previous.cancel(false);

                key.reset();
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
        scheduler.shutdownNow();
    }
}
