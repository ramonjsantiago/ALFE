package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.Objects;

public record DirectoryLoadStarted(long requestId, Path directory) {
    public DirectoryLoadStarted {
        Objects.requireNonNull(directory, "directory");
    }
}
