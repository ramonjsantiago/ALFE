package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.Objects;

/**
 * DirectoryLoadFailed.
 * <p>
 * Auto-generated API documentation for this type.
 */
public record DirectoryLoadFailed(long requestId, Path directory, Throwable error) {
    public DirectoryLoadFailed {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(error, "error");
    }
}
