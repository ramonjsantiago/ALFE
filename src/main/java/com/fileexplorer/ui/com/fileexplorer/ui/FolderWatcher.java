package com.fileexplorer.ui;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FolderWatcher {

    private final ExecutorService executor;
    private WatchService watchService;
    private final File folder;
    private final Runnable onChangeCallback;

    public FolderWatcher(File folder, Runnable onChangeCallback) throws Exception {
        this.folder = folder;
        this.onChangeCallback = onChangeCallback;
        executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        initWatcher();
    }

    private void initWatcher() throws Exception {
        watchService = FileSystems.getDefault().newWatchService();
        Paths.get(folder.getAbsolutePath()).register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );
        startWatcherLoop();
    }

    private void startWatcherLoop() {
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try { key = watchService.take(); }
                catch (InterruptedException e) { break; }
                for (WatchEvent<?> event : key.pollEvents()) {
                    onChangeCallback.run();
                }
                key.reset();
            }
        });
    }

    public void stop() throws Exception {
        executor.shutdownNow();
        watchService.close();
    }
}
