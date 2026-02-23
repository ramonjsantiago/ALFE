package com.fileexplorer.service.ops.preview;

/**
 * Phase 4.3.1: Planned action classification for preview UI.
 */
public enum OperationPlanAction {
    COPY,
    MOVE,
    DELETE,

    OVERWRITE,
    RENAME,
    SKIP,
    ESCALATE
}
