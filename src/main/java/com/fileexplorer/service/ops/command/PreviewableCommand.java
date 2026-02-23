package com.fileexplorer.service.ops.command;

/**
 * Phase 4.1.0: A command that can produce a non-mutating preview prior to execution.
 */
public interface PreviewableCommand {

    /**
     * Produces a best-effort preview for the command. Implementations must not mutate the filesystem.
     */
    CommandPreview preview();
}
