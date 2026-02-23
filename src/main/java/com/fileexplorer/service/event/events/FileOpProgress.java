package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.Objects;

/**
 * FileOpProgress.
 * <p>
 * Auto-generated API documentation for this type.
 */
public record FileOpProgress(long jobId, int processedItems, int totalItems, Path currentPath) {
    public FileOpProgress {
        Objects.requireNonNull(currentPath, "currentPath");
    }

/**
 * percent.
 *
 * @return TODO
 */
    public int percent() {
        if (totalItems <= 0) return 0;
        double p = (processedItems * 100.0) / totalItems;
        return (int) Math.max(0, Math.min(100, Math.round(p)));
    }
}
