package com.fileexplorer.service.ops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6.5.0: Minimal, headless regression tests.
 */
public class RegressionOpsPersistenceTest {

    @Test
    void operationQueuePersistence_roundTrip_v4(@TempDir Path home) {
        String original = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());

            OperationQueuePersistence p = new OperationQueuePersistence();
            p.clear();

            FileOperationRequest req = new FileOperationRequest(
                    FileOperationType.MOVE,
                    List.of(Paths.get("/tmp/src1")),
                    Paths.get("/tmp/dst"),
                    null,
                    false,
                    false,
                    false
            );

            OperationQueuePersistence.SavedOperation saved = new OperationQueuePersistence.SavedOperation(
                    "op-test-1",
                    OperationStatus.QUEUED,
                    req,
                    false
            );

            p.saveSaved(List.of(saved));
            List<OperationQueuePersistence.SavedOperation> loaded = p.loadSaved();

            assertEquals(1, loaded.size());
            assertEquals("op-test-1", loaded.get(0).operationId());
            assertEquals(OperationStatus.QUEUED, loaded.get(0).status());
            assertEquals(FileOperationType.MOVE, loaded.get(0).request().type());
        } finally {
            if (original != null) System.setProperty("user.home", original);
        }
    }
}
