package com.fileexplorer.service.template;

import com.fileexplorer.service.ops.ExecutionDriftPolicy;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.rollback.RollbackMode;

import java.util.List;
import java.util.Objects;

/**
 * Phase 5.2.0: Persistable operation template.
 *
 * <p>Intentionally small and dependency-free.</p>
 */
public record OperationTemplate(
        String id,
        String name,
        FileOperationType type,
        List<String> sources,
        String target,
        String conflictProfileId,
        ExecutionDriftPolicy driftPolicy,
        RollbackMode rollbackMode,
        boolean batchTransaction
) {
    public OperationTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(target, "target");
        // conflictProfileId may be null
        if (driftPolicy == null) driftPolicy = ExecutionDriftPolicy.FAIL_FAST;
        if (rollbackMode == null) rollbackMode = RollbackMode.ASK;
    }
}
