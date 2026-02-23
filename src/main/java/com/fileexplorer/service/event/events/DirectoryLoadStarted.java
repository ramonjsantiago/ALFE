package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.Objects;

/**
 * DirectoryLoadStarted.
 * <p>
 * Auto-generated API documentation for this type.
 */
public record DirectoryLoadStarted(long requestId, Path directory) {
    public DirectoryLoadStarted {
        Objects.requireNonNull(directory, "directory");
    }
}
