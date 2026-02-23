package com.fileexplorer.service.event.events;

import java.util.Objects;

/**
 * FileOpFailed.
 * <p>
 * Auto-generated API documentation for this type.
 */
public record FileOpFailed(long jobId, Throwable error) {
    public FileOpFailed {
        Objects.requireNonNull(error, "error");
    }
}
