package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Published when a directory load is requested. Carries the requestId so UI can ignore stale results.
 */
public record DirectoryLoadRequested(long requestId, Path directory, boolean showHidden) {
    public DirectoryLoadRequested {
        Objects.requireNonNull(directory, "directory");
    }
}
