package com.fileexplorer.service.ops.command;

import com.fileexplorer.service.ops.preview.OperationPlanSnapshot;

import java.util.List;

/**
 * Phase 4.3.1: Preview model rendered in UI prior to executing a command.
 *
 * @param title     dialog title
 * @param summary   short summary line
 * @param items     sample items (e.g., source -> target)
 * @param conflicts detected conflicts (e.g., targets that already exist)
 * @param warnings  warnings / notes
 * @param plan      structured plan snapshot (may be null)
 */
public record CommandPreview(
        String title,
        String summary,
        List<String> items,
        List<String> conflicts,
        List<String> warnings,
        OperationPlanSnapshot plan
) {
}
