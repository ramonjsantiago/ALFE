package com.fileexplorer.service.ops.group;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Phase 5.1.0: Atomic operation group context.
 *
 * <p>This is a lightweight model used by the queue to tag multiple operations with a group id
 * and to journal group-level start/complete/rollback markers.</p>
 */
public final class OperationGroupContext {

    private final String groupId;
    private final Instant createdAt;
    private final List<String> operationIds = new ArrayList<>();

/**
 * OperationGroupContext.
 *
 * @param groupId TODO
 * @return TODO
 */
    public OperationGroupContext(String groupId) {
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.createdAt = Instant.now();
    }

/**
 * groupId.
 *
 * @return TODO
 */
    public String groupId() {
        return groupId;
    }

/**
 * createdAt.
 *
 * @return TODO
 */
    public Instant createdAt() {
        return createdAt;
    }

/**
 * addOperationId.
 *
 * @param operationId TODO
 */
    public void addOperationId(String operationId) {
        if (operationId != null && !operationId.isBlank()) {
            operationIds.add(operationId);
        }
    }

/**
 * operationIds.
 *
 * @return TODO
 */
    public List<String> operationIds() {
        return Collections.unmodifiableList(operationIds);
    }
}
