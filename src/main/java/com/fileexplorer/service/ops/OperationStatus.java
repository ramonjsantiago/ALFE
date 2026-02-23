package com.fileexplorer.service.ops;

/**
 * High-level lifecycle/outcome status for a file operation.
 *
 * <p>Phase 3.9.3 adds {@link #SKIPPED} to represent per-item outcomes when a
 * multi-source request aborts early (e.g., first failure) or when a specific
 * item is intentionally not processed.</p>
 */
public enum OperationStatus {
    QUEUED,
    RUNNING,

    /** Operation finished successfully. */
    COMPLETED,

    /** Operation failed with an error. */
    FAILED,

    /** Operation was cancelled by the user or by shutdown. */
    CANCELLED,

    /**
     * Item was not processed (e.g., skipped due to a previous failure in a batch
     * or skipped by policy).
     */
    SKIPPED
}
