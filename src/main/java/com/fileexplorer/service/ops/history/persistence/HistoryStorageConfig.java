package com.fileexplorer.service.ops.history.persistence;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Phase 3.8.0: History persistence configuration.
 *
 * <p>Storage directory resolution is intentionally simple and dependency-free.</p>
 *
 * <ul>
 *   <li>Primary: -Dfileexplorer.history.dir=/path/to/dir</li>
 *   <li>Default: ${user.home}/.fileexplorer/history</li>
 * </ul>
 */
public final class HistoryStorageConfig {

    /** System property override for the history storage directory. */
    public static final String PROP_HISTORY_DIR = "fileexplorer.history.dir";
    /** System property override for WAL checkpoint threshold in bytes (0 disables size-based checkpointing). */
    public static final String PROP_WAL_CHECKPOINT_BYTES = "fileexplorer.history.walCheckpointBytes";

    /** System property override for how many WAL archives to keep after rotation (minimum 0). */
    public static final String PROP_WAL_ARCHIVE_KEEP = "fileexplorer.history.walArchiveKeep";

    /** System property override for whether to checkpoint on startup when WAL is large (default true). */
    public static final String PROP_CHECKPOINT_ON_STARTUP = "fileexplorer.history.checkpointOnStartup";

    private HistoryStorageConfig() { }

/**
 * resolveBaseDir.
 *
 * @return TODO
 */
    public static Path resolveBaseDir() {
        String override = System.getProperty(PROP_HISTORY_DIR);
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim()).toAbsolutePath().normalize();
        }
        String home = System.getProperty("user.home");
        return Paths.get(home, ".fileexplorer", "history");
    }

    /** Legacy Phase 3.7.x single-file location. */
    public static Path legacyHistoryJsonl() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".fileexplorer", "operation-history.jsonl");
    }
}
