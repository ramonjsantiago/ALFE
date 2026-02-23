package com.fileexplorer.service.ops.conflict;

/**
 * Phase 4.2.0: The policy engine's decision for a single conflict.
 */
public enum ConflictPolicyAction {
    /** Escalate to Conflict Queue UI. */
    PROMPT,

    /** Skip the source item. */
    SKIP,

    /** Overwrite destination with source. */
    OVERWRITE,

    /** Keep both by renaming the incoming item. */
    RENAME
}
