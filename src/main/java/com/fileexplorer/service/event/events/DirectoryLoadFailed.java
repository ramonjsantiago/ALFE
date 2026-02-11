package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.Objects;

public record DirectoryLoadFailed(long requestId, Path directory, Throwable error) {
    public DirectoryLoadFailed {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(error, "error");
    }
}
