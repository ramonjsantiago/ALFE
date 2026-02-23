package com.fileexplorer.service.ops;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Immutable request describing a file operation.
 */
public record FileOperationRequest(
        FileOperationType type,
        List<Path> sources,
        Path targetDirectory,
        String newName,
        boolean overwrite,
        boolean skipConflicts,
        boolean sendToTrash
) {
    public FileOperationRequest {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sources, "sources");
    }
}
