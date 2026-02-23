package com.fileexplorer.service.ops.rollback;

/**
 * Phase 5.0.0: minimal rollback action types.
 *
 * <p>This is intentionally conservative: we only attempt rollbacks that are
 * straightforward and do not require deep filesystem diffing.</p>
 */
public enum RollbackActionType {
    /** Delete a path created by the operation (typical for COPY). */
    DELETE_CREATED,

    /** Move a path back to its original location (typical for MOVE implemented as copy+delete). */
    MOVE_BACK,

    /** Restore a backed-up destination that was replaced during OVERWRITE. */
    RESTORE_BACKUP
}
