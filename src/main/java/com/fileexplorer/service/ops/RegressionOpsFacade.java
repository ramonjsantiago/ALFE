package com.fileexplorer.service.ops;

import java.nio.file.Paths;
import java.util.List;

/**
 * Phase 6.5.0: Headless regression helpers for ops persistence.
 *
 * <p>This facade lives in the {@code com.fileexplorer.service.ops} package so it can
 * exercise package-private persistence helpers without expanding their visibility.</p>
 */
public final class RegressionOpsFacade {

    private RegressionOpsFacade() {}

    /**
     * Performs a deterministic save/load round-trip of the operation queue persistence.
     *
     * @throws AssertionError if an invariant is violated
     */
    public static void assertQueuePersistenceRoundTrip() {
        OperationQueuePersistence p = new OperationQueuePersistence();
        p.clear();

        FileOperationRequest r = new FileOperationRequest(
                FileOperationType.COPY,
                List.of(Paths.get("/tmp/sourceA"), Paths.get("/tmp/sourceB")),
                Paths.get("/tmp/target"),
                "newName",
                true,
                false,
                true
        );

        OperationQueuePersistence.SavedOperation saved = new OperationQueuePersistence.SavedOperation(
                "op-reg-1",
                OperationStatus.QUEUED,
                r,
                false
        );

        p.saveSaved(List.of(saved));

        var loaded = p.loadSaved();
        if (loaded.size() != 1) throw new AssertionError("Expected 1 saved op, got " + loaded.size());
        var x = loaded.get(0);
        if (!"op-reg-1".equals(x.operationId())) throw new AssertionError("opId mismatch");
        if (x.status() != OperationStatus.QUEUED) throw new AssertionError("status mismatch");
        if (x.request() == null || x.request().type() != FileOperationType.COPY) throw new AssertionError("request mismatch");
    }
}
