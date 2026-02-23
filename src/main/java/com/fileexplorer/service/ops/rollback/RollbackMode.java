package com.fileexplorer.service.ops.rollback;

/**
 * Phase 5.0.1: Controls rollback behavior when an operation fails mid-run.
 */
public enum RollbackMode {
    /** Always rollback automatically on failure (best-effort). */
    ALWAYS,
    /** Ask the user whether to rollback on failure. */
    ASK,
    /** Never rollback automatically. */
    NEVER
}
