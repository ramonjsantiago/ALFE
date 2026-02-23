package com.fileexplorer.service.ops.preview;

/**
 * Phase 4.3.1: Structured preview counts used by the preview UI and snapshot hashing.
 */
public record PreviewCounts(
        int sources,
        int missingSources,
        int conflicts,
        int overwritePlanned,
        int renamePlanned,
        int skipPlanned,
        int escalations,
        boolean crossVolumeMove,
        boolean deepDirectoryMerge,
        boolean targetNotWritable
) {
}
