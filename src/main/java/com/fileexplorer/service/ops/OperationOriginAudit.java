package com.fileexplorer.service.ops;

/**
 * Origin metadata attached to an operation so it can be persisted into the operation history/audit trail.
 *
 * <p>Phase 5.5.1: This is primarily used by the Template Scheduler to stamp scheduled executions with
 * schedule/template metadata (trigger, recurrence, retry attempt).</p>
 */
public record OperationOriginAudit(
        String originType,
        String templateId,
        String scheduleId,
        String triggerType,
        long recurrenceMinutes,
        int retryAttempt
) {

    /**
     * Create a best-effort normalized instance. Empty strings are converted to "" (not null) for
     * stability in persistence.
     */
    public static OperationOriginAudit of(String originType,
                                         String templateId,
                                         String scheduleId,
                                         String triggerType,
                                         long recurrenceMinutes,
                                         int retryAttempt) {
        return new OperationOriginAudit(
                safe(originType),
                safe(templateId),
                safe(scheduleId),
                safe(triggerType),
                Math.max(0L, recurrenceMinutes),
                Math.max(0, retryAttempt)
        );
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
