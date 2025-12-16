package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

public class FileWatcherService {

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;

    public void startWatching(Path folder, Consumer<WatchEvent<?>> onEvent) throws IOException {
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Path must be a directory");
        }

        stopWatching(); // stop previous

        watchService = FileSystems.getDefault().newWatchService();
        folder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                                      StandardWatchEventKinds.ENTRY_DELETE,
                                      StandardWatchEventKinds.ENTRY_MODIFY);

        running = true;
        watchThread = new Thread(() -> {
            while (running) {
                try {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (onEvent != null) onEvent.accept(event);
                    }
                    key.reset();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public void stopWatching() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignored) {}
        }
    }
}