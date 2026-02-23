package com.fileexplorer.service.ops.journal;

/**
 * Phase 4.5.x: Append-only journal record types.
 *
 * <p>Phase 5.0.0 extends the journal with rollback markers so a partially failed
 * operation can be safely unwound (best-effort) and the outcome audited.</p>
 *
 * <p>Phase 5.1.0 extends the journal with group markers so multiple operations can be treated
 * as an atomic batch (transaction group).</p>
 */
public enum OperationJournalRecordType {
    OPERATION_START,
    PLAN_ITEM,
    ITEM_START,
    ITEM_SUCCESS,
    ITEM_FAIL,
    DRIFT_EVENT,

    // Phase 5.1.0: group transaction markers (written to a group journal id)
    GROUP_START,
    GROUP_OPERATION_START,
    GROUP_OPERATION_COMPLETE,
    GROUP_COMPLETE,
    GROUP_ROLLBACK_START,
    GROUP_ROLLBACK_ITEM_OK,
    GROUP_ROLLBACK_ITEM_FAIL,
    GROUP_ROLLBACK_COMPLETE,

    // Phase 5.0.0: rollback journal markers (per operation)
    ROLLBACK_START,
    ROLLBACK_ITEM_OK,
    ROLLBACK_ITEM_FAIL,
    ROLLBACK_COMPLETE,

    OPERATION_COMPLETE
}
