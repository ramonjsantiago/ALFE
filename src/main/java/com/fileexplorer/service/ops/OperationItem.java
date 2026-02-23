package com.fileexplorer.service.ops;

import com.fileexplorer.service.ops.preview.OperationPlanSnapshot;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable queue item wrapper around {@link FileOperationRequest}.
 *
 * <p>Phase 4.4.0: optionally carries an {@link OperationPlanSnapshot} produced by the preview engine.
 * When present, the executor can run deterministically from the snapshot.</p>
 *
 * <p>Phase 5.1.0: optionally carries an operationGroupId so multiple operations can be treated as a
 * single atomic batch (group rollback on failure).</p>
 */
public final class OperationItem {

    private final String id;
    private final FileOperationRequest request;
    private final Instant createdAt;

    // Phase 4.4.0: deterministic execution snapshot (may be null)
    private final OperationPlanSnapshot planSnapshot;

    // Phase 4.4.1: per-operation drift policy override (may be null; falls back to global)
    private final ExecutionDriftPolicy driftPolicyOverride;

    // Phase 5.1.0: optional atomic group id (may be null)
    private final String operationGroupId;

    // Phase 5.5.1: optional origin/audit metadata (may be null)
    private final OperationOriginAudit originAudit;

/**
 * OperationItem.
 *
 * @param id TODO
 * @param request TODO
 * @return TODO
 */
    public OperationItem(String id, FileOperationRequest request) {
        this(id, request, null, null, null, null);
    }

    public OperationItem(String id, FileOperationRequest request, OperationPlanSnapshot planSnapshot) {
        this(id, request, planSnapshot, null, null, null);
    }

/**
 * OperationItem.
 *
 * @param id TODO
 * @param request TODO
 * @param planSnapshot TODO
 * @param driftPolicyOverride TODO
 * @return TODO
 */
    public OperationItem(String id, FileOperationRequest request, OperationPlanSnapshot planSnapshot, ExecutionDriftPolicy driftPolicyOverride) {
        this(id, request, planSnapshot, driftPolicyOverride, null, null);
    }

    public OperationItem(String id, FileOperationRequest request, OperationPlanSnapshot planSnapshot,
                         ExecutionDriftPolicy driftPolicyOverride, String operationGroupId) {
        this(id, request, planSnapshot, driftPolicyOverride, operationGroupId, null);
    }

    /**
     * Phase 5.5.1: Construct an operation item with optional origin/audit metadata.
     */
    public OperationItem(String id, FileOperationRequest request, OperationPlanSnapshot planSnapshot,
                         ExecutionDriftPolicy driftPolicyOverride, String operationGroupId,
                         OperationOriginAudit originAudit) {
        this.id = Objects.requireNonNull(id, "id");
        this.request = Objects.requireNonNull(request, "request");
        this.createdAt = Instant.now();
        this.planSnapshot = planSnapshot;
        this.driftPolicyOverride = driftPolicyOverride;
        this.operationGroupId = operationGroupId;
        this.originAudit = originAudit;
    }

    public String id() { return id; }
    public FileOperationRequest request() { return request; }
    public Instant createdAt() { return createdAt; }

    /**
     * Phase 4.4.0: Returns the preview plan snapshot (may be null).
     */
    public OperationPlanSnapshot planSnapshot() { return planSnapshot; }

    /**
     * Phase 4.4.1: Returns an optional per-operation drift policy override (may be null).
     */
    public ExecutionDriftPolicy driftPolicyOverride() { return driftPolicyOverride; }

    /**
     * Phase 5.1.0: Returns the atomic operation group id (may be null).
     */
    public String operationGroupId() { return operationGroupId; }

    /**
     * Phase 5.5.1: Returns optional origin/audit metadata for this operation (may be null).
     */
    public OperationOriginAudit originAudit() { return originAudit; }

/**
 * displayTitle.
 *
 * @return TODO
 */
    public String displayTitle() {
        List<Path> src = request.sources();
        int n = src == null ? 0 : src.size();

        String base = request.type().name();
        String srcPart;
        if (n == 0) srcPart = "(none)";
        else if (n == 1) srcPart = safeName(src.get(0));
        else srcPart = n + " items";

        Path target = request.targetDirectory();
        String tgtPart = (target == null) ? "" : (" → " + safeName(target));

        if (request.type() == FileOperationType.RENAME) {
            String nn = request.newName();
            if (nn != null && !nn.isBlank() && n == 1) {
                return "RENAME: " + safeName(src.get(0)) + " → " + nn;
            }
        }

        return base + ": " + srcPart + tgtPart;
    }

/**
 * safeName.
 *
 * @param p TODO
 * @return TODO
 */
    private static String safeName(Path p) {
        if (p == null) return "(null)";
        Path fn = p.getFileName();
        return fn != null ? fn.toString() : p.toString();
    }
}
