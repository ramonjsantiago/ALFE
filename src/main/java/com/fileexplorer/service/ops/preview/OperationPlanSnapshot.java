package com.fileexplorer.service.ops.preview;

import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.conflict.ConflictPolicyConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Phase 4.3.1: Deterministic, read-only plan snapshot produced by the preview engine.
 *
 * <p>This is designed to be stable so later phases can execute from the same snapshot
 * (Phase 4.4.x deterministic execution).</p>
 */
public record OperationPlanSnapshot(
        FileOperationType type,
        Path targetDirectory,
        ConflictPolicyConfig policy,
        PreviewCounts counts,
        List<OperationPlanItem> actions,
        List<String> conflicts,
        List<String> warnings,
        String previewHash
) {

    public OperationPlanSnapshot {
        Objects.requireNonNull(type, "type");
        actions = (actions == null) ? List.of() : List.copyOf(actions);
        conflicts = (conflicts == null) ? List.of() : List.copyOf(conflicts);
        warnings = (warnings == null) ? List.of() : List.copyOf(warnings);
        if (counts == null) {
            counts = new PreviewCounts(0, 0, 0, 0, 0, 0, 0, false, false, false);
        }
        if (previewHash == null || previewHash.isBlank()) {
            previewHash = computeHash(type, targetDirectory, policy, counts, actions);
        }
    }

    private static String computeHash(FileOperationType type,
                                      Path targetDirectory,
                                      ConflictPolicyConfig policy,
                                      PreviewCounts counts,
                                      List<OperationPlanItem> actions) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(String.valueOf(type).getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(String.valueOf(targetDirectory).getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(String.valueOf(policy).getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(String.valueOf(counts).getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            if (actions != null) {
                for (OperationPlanItem it : actions) {
                    md.update(String.valueOf(it.source()).getBytes(StandardCharsets.UTF_8));
                    md.update((byte) '\t');
                    md.update(String.valueOf(it.destination()).getBytes(StandardCharsets.UTF_8));
                    md.update((byte) '\t');
                    md.update(String.valueOf(it.action()).getBytes(StandardCharsets.UTF_8));
                    md.update((byte) '\t');
                    md.update(String.valueOf(it.reason()).getBytes(StandardCharsets.UTF_8));
                    md.update((byte) '\n');
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(Objects.hash(type, targetDirectory, policy, counts, actions));
        }
    }
}
