package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.Objects;

public record DirectoryLoadRequested(Path directory) {
    public DirectoryLoadRequested {
        Objects.requireNonNull(directory, "directory");
    }
}
