package com.fileexplorer.service.ops;

import java.time.Instant;

/**
 * Lightweight progress model for the operation queue UI.
 *
 * <p>Units are "items" (files/dirs), not bytes.</p>
 */
public final class OperationProgress {

    private final long processedUnits;
    private final long totalUnits;
    private final String message;
    private final Instant timestamp;

/**
 * OperationProgress.
 *
 * @param processedUnits TODO
 * @param totalUnits TODO
 * @param message TODO
 * @return TODO
 */
    public OperationProgress(long processedUnits, long totalUnits, String message) {
        this.processedUnits = processedUnits;
        this.totalUnits = totalUnits;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public long processedUnits() { return processedUnits; }
    public long totalUnits() { return totalUnits; }
    public String message() { return message; }
    public Instant timestamp() { return timestamp; }

    /**
     * @return fraction in [0..1], or -1 if total is unknown.
     */
    public double fraction() {
        if (totalUnits <= 0) return -1.0;
        double f = (double) processedUnits / (double) totalUnits;
        if (f < 0.0) return 0.0;
        if (f > 1.0) return 1.0;
        return f;
    }
}
