package com.fileexplorer.perf.viewport;

/**
 * Mutable per-frame budget tracker used to cap realization, decode promotion, and settle work.
 *
 * <p>The class is intentionally tiny so it can be instantiated once per pulse, render pass, or
 * scheduler tick without introducing allocation pressure.
 */
public final class FrameBudget {
    private final long budgetNanos;
    private long spentNanos;

    /**
     * Creates a frame budget.
     *
     * @param budgetNanos the maximum budget for the frame in nanoseconds. Negative values are not
     *     allowed.
     */
    public FrameBudget(long budgetNanos) {
        if (budgetNanos < 0L) {
            throw new IllegalArgumentException("budgetNanos must be >= 0");
        }
        this.budgetNanos = budgetNanos;
    }

    /**
     * Returns the total budget in nanoseconds.
     *
     * @return the total frame budget.
     */
    public long budgetNanos() {
        return budgetNanos;
    }

    /**
     * Returns the amount spent so far.
     *
     * @return consumed budget in nanoseconds.
     */
    public long spentNanos() {
        return spentNanos;
    }

    /**
     * Returns the remaining unspent budget.
     *
     * @return remaining budget in nanoseconds, never negative.
     */
    public long remainingNanos() {
        return Math.max(0L, budgetNanos - spentNanos);
    }

    /**
     * Returns whether the requested amount fits in the remaining budget.
     *
     * @param nanos the proposed spend amount in nanoseconds.
     * @return {@code true} when the amount fits; otherwise {@code false}.
     */
    public boolean canSpend(long nanos) {
        if (nanos < 0L) {
            throw new IllegalArgumentException("nanos must be >= 0");
        }
        return nanos <= remainingNanos();
    }

    /**
     * Attempts to spend budget.
     *
     * @param nanos the amount to consume.
     * @return {@code true} when the amount was accepted and recorded; otherwise {@code false}.
     */
    public boolean trySpend(long nanos) {
        if (!canSpend(nanos)) {
            return false;
        }
        spentNanos += nanos;
        return true;
    }

    /**
     * Records elapsed work even if it exceeds the budget.
     *
     * <p>This method is useful when a task has already run and the caller wants accurate telemetry.
     *
     * @param nanos the elapsed amount to record.
     */
    public void recordActualSpend(long nanos) {
        if (nanos < 0L) {
            throw new IllegalArgumentException("nanos must be >= 0");
        }
        spentNanos += nanos;
    }

    /**
     * Returns whether the frame budget is exhausted.
     *
     * @return {@code true} when no budget remains.
     */
    public boolean exhausted() {
        return remainingNanos() == 0L;
    }
}
