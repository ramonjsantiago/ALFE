package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.List;

/**
 * Published when a file operation fails.
 */
public record FileOpFailed(
        long requestId,
        String op,
        List<Path> sources,
        Path targetDirectory,
        String message,
        Throwable error
) {}
