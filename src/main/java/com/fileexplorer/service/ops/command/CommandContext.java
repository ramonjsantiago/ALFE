package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.ops.OperationQueueService;
import com.fileexplorer.service.ops.history.OperationHistoryService;

import java.util.Objects;

/**
 * Phase 4.0.0: shared dependencies for command execution.
 */
public final class CommandContext {

    private final OperationQueueService operationQueueService;
    private final OperationHistoryService operationHistoryService;
    private final EventBus eventBus;

    public CommandContext(
            OperationQueueService operationQueueService,
            OperationHistoryService operationHistoryService,
            EventBus eventBus
    ) {
        this.operationQueueService = Objects.requireNonNull(operationQueueService, "operationQueueService");
        this.operationHistoryService = Objects.requireNonNull(operationHistoryService, "operationHistoryService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

/**
 * operationQueueService.
 *
 * @return TODO
 */
    public OperationQueueService operationQueueService() {
        return operationQueueService;
    }

/**
 * operationHistoryService.
 *
 * @return TODO
 */
    public OperationHistoryService operationHistoryService() {
        return operationHistoryService;
    }

/**
 * eventBus.
 *
 * @return TODO
 */
    public EventBus eventBus() {
        return eventBus;
    }
}
