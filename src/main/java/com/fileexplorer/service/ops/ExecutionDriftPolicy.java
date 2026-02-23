package com.fileexplorer.service.ops;

/**
 * Phase 4.4.1: Defines how snapshot execution should react when runtime filesystem state
 * differs from the {@code OperationPlanSnapshot}.
 */
public enum ExecutionDriftPolicy {

    /** Abort the entire operation on the first drift event. */
    FAIL_FAST,

    /** Skip only the affected items and continue. */
    SKIP_AFFECTED,

    /** For drift that can be resolved interactively, escalate to the conflict queue UI. */
    ESCALATE_TO_QUEUE,

    /** Abort and require a new preview before execution. */
    REPLAN_REQUIRED
}
