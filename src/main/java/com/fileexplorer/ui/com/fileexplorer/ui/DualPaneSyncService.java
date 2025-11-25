package com.fileexplorer.ui;

import java.nio.file.*;

/**
 * DualPaneSyncService
 *  - For dual-pane “Commander-style” Explorer views
 *  - Provides directory mirroring and quick sync helpers
 */
public class DualPaneSyncService {

    public enum SyncMode {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT
    }

    public void sync(Path src, Path dst, SyncMode mode) throws Exception {
        if (!Files.isDirectory(src) || !Files.isDirectory(dst))
            throw new Exception("Both panels must be folders.");

        try (var s = Files.walk(src)) {
            s.forEach(p -> {
                try {
                    Path relative = src.relativize(p);
                    Path target = dst.resolve(relative);

                    if (Files.isDirectory(p)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception ignored) {}
            });
        }
    }
}