package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.List;

/**
 * Published when a file operation completes successfully.
 */
public record FileOpSucceeded(
        long requestId,
        String op,
        List<Path> sources,
        Path targetDirectory
) {}
