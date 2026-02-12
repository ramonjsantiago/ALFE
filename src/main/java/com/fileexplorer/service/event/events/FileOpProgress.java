package com.fileexplorer.service.event.events;

import java.nio.file.Path;

/**
 * Best-effort progress events for long-running operations.
 */
public record FileOpProgress(
        long requestId,
        String op,
        Path current,
        int completed,
        int total
) {}
