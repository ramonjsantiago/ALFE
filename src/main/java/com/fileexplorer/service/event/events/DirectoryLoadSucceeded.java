package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.fileexplorer.model.FileItem;

/**
 * DirectoryLoadSucceeded.
 * <p>
 * Auto-generated API documentation for this type.
 */
public record DirectoryLoadSucceeded(long requestId, Path directory, List<FileItem> children, long durationMs) {
    public DirectoryLoadSucceeded {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(children, "children");
    }
}
