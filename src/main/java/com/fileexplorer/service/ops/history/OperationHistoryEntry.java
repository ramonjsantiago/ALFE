package com.fileexplorer.service.ops.history;

import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.OperationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Immutable audit record for a completed or terminated operation.
 *
 * <p>Note: request_* fields are best-effort to enable "Retry" from history.</p>
 */
public record OperationHistoryEntry(
        String operationId,
        FileOperationType type,
        OperationStatus status,
        Instant startedAt,
        Instant endedAt,
        long durationMillis,
        long processedBytes,
        long totalBytes,
        String sourcesSummary,
        String targetSummary,
        String verifyMode,
        boolean verifyOk,
        String message,

        // --- 3.7.1: best-effort request reconstruction for "Retry" ---
        List<String> requestSources,
        String requestTargetDirectory,
        String requestNewName,
        boolean requestOverwrite,
        boolean requestSendToTrash,

        // --- 5.5.1: origin/audit metadata for scheduled runs (optional) ---
        String originType,
        String originTemplateId,
        String originScheduleId,
        String originTriggerType,
        long originRecurrenceMinutes,
        int originRetryAttempt,

        // --- 3.9.1: transactional batching metadata (optional) ---
        String batchId,
        String batchLabel,
        int batchIndex,
        int batchSize
,

        // --- 4.0.5: command link (optional) ---
        String commandId
) { }
