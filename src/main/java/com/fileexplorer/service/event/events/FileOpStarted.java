package com.fileexplorer.service.event.events;

import java.nio.file.Path;
import java.util.List;

/**
 * Published when a file operation starts.
 */
public record FileOpStarted(
        long requestId,
        String op,
        List<Path> sources,
        Path targetDirectory
) {}
