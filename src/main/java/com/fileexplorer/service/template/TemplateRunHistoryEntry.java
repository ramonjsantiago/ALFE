package com.fileexplorer.service.template;

import java.time.Instant;
import java.util.Objects;

/**
 * Phase 5.2.1: Persistent run history entry for scheduled templates.
 */
public record TemplateRunHistoryEntry(
        long timestampMillis,
        String templateId,
        String templateName,
        String status,
        String detail,
        String operationId
) {
    public TemplateRunHistoryEntry {
        Objects.requireNonNull(templateId, "templateId");
        if (status == null || status.isBlank()) status = "INFO";
        if (detail == null) detail = "";
    }

/**
 * now.
 *
 * @param templateId TODO
 * @param templateName TODO
 * @param status TODO
 * @param detail TODO
 * @param operationId TODO
 * @return TODO
 */
    public static TemplateRunHistoryEntry now(String templateId, String templateName, String status, String detail, String operationId) {
        return new TemplateRunHistoryEntry(Instant.now().toEpochMilli(), templateId, templateName, status, detail, operationId);
    }
}
