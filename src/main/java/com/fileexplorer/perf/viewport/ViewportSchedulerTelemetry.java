package com.fileexplorer.perf.viewport;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe counters and timers for the HOTFIX176 viewport scheduler.
 *
 * <p>The telemetry surface is intentionally compact: it gives the surrounding application enough data
 * to emit performance HUD values, debug logs, or one-shot diagnostics without coupling the scheduler
 * to a specific metrics framework.
 */
public final class ViewportSchedulerTelemetry {
    private final AtomicLong realizeRuns = new AtomicLong();
    private final AtomicLong decodePromotions = new AtomicLong();
    private final AtomicLong decodePromotionDrops = new AtomicLong();
    private final AtomicLong budgetOverruns = new AtomicLong();
    private final AtomicLong scrollStopCommits = new AtomicLong();
    private final AtomicLong scrollStopCommitLatencyNanos = new AtomicLong();

    /** Records a realization execution. */
    public void recordRealizeRun() {
        realizeRuns.incrementAndGet();
    }

    /** Records a decode promotion execution. */
    public void recordDecodePromotion() {
        decodePromotions.incrementAndGet();
    }

    /** Records a skipped promotion due to budget or policy pressure. */
    public void recordDecodePromotionDrop() {
        decodePromotionDrops.incrementAndGet();
    }

    /** Records a frame-budget overrun. */
    public void recordBudgetOverrun() {
        budgetOverruns.incrementAndGet();
    }

    /**
     * Records a scroll-stop commit and its latency.
     *
     * @param latencyNanos elapsed time between the last scroll sample and the resulting settle
     *     commit.
     */
    public void recordScrollStopCommit(long latencyNanos) {
        scrollStopCommits.incrementAndGet();
        scrollStopCommitLatencyNanos.addAndGet(Math.max(0L, latencyNanos));
    }

    /**
     * Returns a point-in-time immutable telemetry snapshot.
     *
     * @return current telemetry values.
     */
    public Snapshot snapshot() {
        long commits = scrollStopCommits.get();
        long totalLatency = scrollStopCommitLatencyNanos.get();
        long avgLatency = commits == 0L ? 0L : totalLatency / commits;
        return new Snapshot(
                realizeRuns.get(),
                decodePromotions.get(),
                decodePromotionDrops.get(),
                budgetOverruns.get(),
                commits,
                avgLatency);
    }

    /** Immutable telemetry view. */
    public record Snapshot(
            long realizeRuns,
            long decodePromotions,
            long decodePromotionDrops,
            long budgetOverruns,
            long scrollStopCommits,
            long averageScrollStopCommitLatencyNanos) {}
}
