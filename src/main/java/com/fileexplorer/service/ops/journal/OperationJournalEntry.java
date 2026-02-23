package com.fileexplorer.service.ops.journal;

import java.util.Map;

/**
 * Phase 4.5.0: Parsed journal entry.
 */
public record OperationJournalEntry(
        long epochMillis,
        String operationId,
        OperationJournalRecordType type,
        Map<String, String> fields
) {
}
