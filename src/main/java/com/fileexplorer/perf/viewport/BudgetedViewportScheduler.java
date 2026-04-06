package com.fileexplorer.perf.viewport;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * HOTFIX176 scheduler that enforces realization priority bands, frame-budgeted decode promotion, and
 * a single scroll-stop commit point.
 *
 * <p>The scheduler is intentionally generic and does not depend on JavaFX node types. A controller or
 * view-model layer can adapt rows, tiles, and thumbnail requests into {@link ViewportWorkItem}
 * instances, then call {@link #submit(Collection)} whenever the active viewport changes.
 */
public final class BudgetedViewportScheduler {
    private final ViewportBandClassifier classifier;
    private final ViewportSchedulerTelemetry telemetry;
    private final ScrollStopCommitCoordinator scrollStopCommitCoordinator;
    private final Consumer<ViewportSchedulerTelemetry.Snapshot> onScrollStopCommit;

    private final ArrayDeque<ViewportWorkItem> visibleQueue = new ArrayDeque<>();
    private final ArrayDeque<ViewportWorkItem> nearViewportQueue = new ArrayDeque<>();
    private final ArrayDeque<ViewportWorkItem> farOffscreenQueue = new ArrayDeque<>();

    /**
     * Creates a scheduler.
     *
     * @param classifier classifier that maps distance to priority band.
     * @param telemetry telemetry collector.
     * @param clock time source for scroll-stop coordination.
     * @param scrollQuietPeriod amount of quiescence required before scroll-stop commit.
     * @param scrollQuietPeriodUnit unit for {@code scrollQuietPeriod}.
     * @param onScrollStopCommit callback invoked once per scroll burst after settle work is ready to
     *     commit. The callback receives a telemetry snapshot that already includes the commit record.
     */
    public BudgetedViewportScheduler(
            ViewportBandClassifier classifier,
            ViewportSchedulerTelemetry telemetry,
            Clock clock,
            long scrollQuietPeriod,
            TimeUnit scrollQuietPeriodUnit,
            Consumer<ViewportSchedulerTelemetry.Snapshot> onScrollStopCommit) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.onScrollStopCommit = Objects.requireNonNull(onScrollStopCommit, "onScrollStopCommit");
        this.scrollStopCommitCoordinator = new ScrollStopCommitCoordinator(
                Objects.requireNonNull(clock, "clock"),
                scrollQuietPeriod,
                Objects.requireNonNull(scrollQuietPeriodUnit, "scrollQuietPeriodUnit"),
                this::handleScrollStopCommit);
    }

    /**
     * Replaces the scheduler queues with a newly sorted snapshot of viewport work.
     *
     * <p>This method is intended to be called from the UI thread whenever the realized window changes
     * because of scroll, resize, navigation, or selection-anchor restoration.
     *
     * @param items latest viewport work candidates.
     */
    public void submit(Collection<? extends ViewportWorkItem> items) {
        Objects.requireNonNull(items, "items");
        visibleQueue.clear();
        nearViewportQueue.clear();
        farOffscreenQueue.clear();

        List<? extends ViewportWorkItem> ordered = items.stream()
                .sorted(Comparator.comparingInt(item -> Math.abs(item.distanceFromViewportCells())))
                .toList();

        for (ViewportWorkItem item : ordered) {
            switch (classifier.classify(item)) {
                case VISIBLE -> visibleQueue.addLast(item);
                case NEAR_VIEWPORT -> nearViewportQueue.addLast(item);
                case FAR_OFFSCREEN -> farOffscreenQueue.addLast(item);
            }
        }
    }

    /**
     * Signals fresh scroll or viewport activity.
     *
     * <p>The typical JavaFX integration point is inside mouse-wheel handlers, scrollbar listeners,
     * virtual flow listeners, and layout-driven viewport-range updates.
     */
    public void markScrollActivity() {
        scrollStopCommitCoordinator.markScrollActivity();
    }

    /**
     * Executes a single scheduler tick within the supplied frame budget.
     *
     * <p>Policy order:
     * <ol>
     *   <li>Visible realization.</li>
     *   <li>Visible decode promotion.</li>
     *   <li>Near-viewport realization.</li>
     *   <li>Near-viewport decode promotion.</li>
     *   <li>Far-offscreen realization only when budget remains.</li>
     *   <li>Emit one scroll-stop commit if the viewport has become quiet.</li>
     * </ol>
     *
     * @param frameBudgetNanos total budget for the tick.
     * @return snapshot of telemetry after the tick.
     */
    public ViewportSchedulerTelemetry.Snapshot runFrame(long frameBudgetNanos) {
        FrameBudget budget = new FrameBudget(frameBudgetNanos);

        realizeUntilBudget(visibleQueue, budget);
        promoteDecodeUntilBudget(visibleQueue, budget);

        realizeUntilBudget(nearViewportQueue, budget);
        promoteDecodeUntilBudget(nearViewportQueue, budget);

        realizeUntilBudget(farOffscreenQueue, budget);

        if (budget.spentNanos() > budget.budgetNanos()) {
            telemetry.recordBudgetOverrun();
        }

        scrollStopCommitCoordinator.pollForScrollStopCommit();
        return telemetry.snapshot();
    }

    /**
     * Returns the number of queued visible items.
     *
     * @return visible queue size.
     */
    public int visibleQueueSize() {
        return visibleQueue.size();
    }

    /**
     * Returns the number of queued near-viewport items.
     *
     * @return near queue size.
     */
    public int nearViewportQueueSize() {
        return nearViewportQueue.size();
    }

    /**
     * Returns the number of queued far-offscreen items.
     *
     * @return far queue size.
     */
    public int farOffscreenQueueSize() {
        return farOffscreenQueue.size();
    }

    private void realizeUntilBudget(ArrayDeque<ViewportWorkItem> queue, FrameBudget budget) {
        for (ViewportWorkItem item : queue) {
            if (!item.needsRealization()) {
                continue;
            }
            long estimate = item.estimatedRealizeCostNanos();
            if (!budget.trySpend(estimate)) {
                telemetry.recordBudgetOverrun();
                return;
            }
            item.realize();
            telemetry.recordRealizeRun();
        }
    }

    private void promoteDecodeUntilBudget(ArrayDeque<ViewportWorkItem> queue, FrameBudget budget) {
        for (ViewportWorkItem item : queue) {
            if (!item.needsDecodePromotion()) {
                continue;
            }
            long estimate = item.estimatedDecodePromotionCostNanos();
            if (!budget.trySpend(estimate)) {
                telemetry.recordDecodePromotionDrop();
                return;
            }
            item.promoteDecode();
            telemetry.recordDecodePromotion();
        }
    }

    private void handleScrollStopCommit(long latencyNanos) {
        telemetry.recordScrollStopCommit(latencyNanos);
        onScrollStopCommit.accept(telemetry.snapshot());
    }
}
