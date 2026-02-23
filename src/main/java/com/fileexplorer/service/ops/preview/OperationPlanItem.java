package com.fileexplorer.service.ops.preview;

import java.nio.file.Path;

/**
 * Phase 4.3.1: One planned item in an operation preview.
 *
 * @param source      source path (may be null for some operations)
 * @param destination destination path (may be null when missing/no target)
 * @param action      planned action
 * @param reason      short reason string (conflict, missing source, policy, etc.)
 */
public record OperationPlanItem(
        Path source,
        Path destination,
        OperationPlanAction action,
        String reason
) {
/**
 * sourceText.
 *
 * @return TODO
 */
    public String sourceText() {
        return source == null ? "" : source.toString();
    }

/**
 * destinationText.
 *
 * @return TODO
 */
    public String destinationText() {
        return destination == null ? "" : destination.toString();
    }
}
