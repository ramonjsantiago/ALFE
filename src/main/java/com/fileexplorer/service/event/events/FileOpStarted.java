package com.fileexplorer.service.event.events;

import com.fileexplorer.service.ops.FileOperationType;

import java.util.Objects;

/**
 * FileOpStarted.
 * <p>
 * Auto-generated API documentation for this type.
 */
public record FileOpStarted(long jobId, FileOperationType type, int totalItems) {
    public FileOpStarted {
        Objects.requireNonNull(type, "type");
    }
}
