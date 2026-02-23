package com.fileexplorer.service.ops.rollback;

import java.nio.file.Path;

/**
 * Phase 5.0.0: rollback step captured during execution and applied in reverse order.
 */
public record RollbackStep(
        RollbackActionType type,
        Path primary,
        Path secondary,
        String note
) {
/**
 * deleteCreated.
 *
 * @param created TODO
 * @param note TODO
 * @return TODO
 */
    public static RollbackStep deleteCreated(Path created, String note) {
        return new RollbackStep(RollbackActionType.DELETE_CREATED, created, null, note);
    }

    public static RollbackStep moveBack(Path from, Path to, String note) {
        return new RollbackStep(RollbackActionType.MOVE_BACK, from, to, note);
    }

/**
 * restoreBackup.
 *
 * @param backup TODO
 * @param dest TODO
 * @param note TODO
 * @return TODO
 */
    public static RollbackStep restoreBackup(Path backup, Path dest, String note) {
        return new RollbackStep(RollbackActionType.RESTORE_BACKUP, backup, dest, note);
    }
}
