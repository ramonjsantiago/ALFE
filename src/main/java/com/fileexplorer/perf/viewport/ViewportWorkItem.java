package com.fileexplorer.perf.viewport;

import java.util.Objects;

/**
 * Generic unit of viewport work used by the HOTFIX176 scheduler.
 *
 * <p>The interface is intentionally transport-oriented. The surrounding File Explorer code can map a
 * row, tile, or cell to one of these work items without the scheduler needing to know anything about
 * JavaFX controls, thumbnail pipelines, or model objects.
 */
public interface ViewportWorkItem {
    /**
     * Returns a stable identifier used for telemetry and de-duplication.
     *
     * @return a non-null stable key.
     */
    String id();

    /**
     * Returns the signed or absolute distance from the viewport, in logical cells.
     *
     * <p>{@code 0} means visible now. The scheduler normalizes signed values internally.
     *
     * @return the distance in cells.
     */
    int distanceFromViewportCells();

    /**
     * Returns a rough estimate of realization cost.
     *
     * <p>This estimate is used before work starts in order to make frame-budget decisions.
     *
     * @return estimated realization cost in nanoseconds.
     */
    long estimatedRealizeCostNanos();

    /**
     * Returns a rough estimate of decode promotion cost.
     *
     * @return estimated decode promotion cost in nanoseconds.
     */
    long estimatedDecodePromotionCostNanos();

    /**
     * Returns whether the item still needs realization work.
     *
     * @return {@code true} when realization should run.
     */
    boolean needsRealization();

    /**
     * Returns whether the item still needs decode promotion.
     *
     * @return {@code true} when decode promotion should run.
     */
    boolean needsDecodePromotion();

    /**
     * Performs realization work.
     *
     * <p>Typical examples include populating a reused cell, binding text, resetting state, or
     * ensuring the item has a valid placeholder image.
     */
    void realize();

    /**
     * Performs decode promotion work.
     *
     * <p>Typical examples include promoting a thumbnail request to a higher priority queue, kicking a
     * decode task onto a fast lane, or synchronously binding an already-cached image into a visible
     * cell.
     */
    void promoteDecode();

    /**
     * Lightweight immutable implementation suitable for tests and simple integrations.
     */
    final class Basic implements ViewportWorkItem {
        private final String id;
        private final int distanceFromViewportCells;
        private final long estimatedRealizeCostNanos;
        private final long estimatedDecodePromotionCostNanos;
        private final boolean needsRealization;
        private final boolean needsDecodePromotion;
        private final Runnable realizeAction;
        private final Runnable promoteDecodeAction;

        /**
         * Creates a basic work item.
         *
         * @param id stable identifier.
         * @param distanceFromViewportCells distance from the viewport in logical cells.
         * @param estimatedRealizeCostNanos estimated realization cost.
         * @param estimatedDecodePromotionCostNanos estimated decode promotion cost.
         * @param needsRealization whether realization is still needed.
         * @param needsDecodePromotion whether decode promotion is still needed.
         * @param realizeAction action invoked by {@link #realize()}.
         * @param promoteDecodeAction action invoked by {@link #promoteDecode()}.
         */
        public Basic(
                String id,
                int distanceFromViewportCells,
                long estimatedRealizeCostNanos,
                long estimatedDecodePromotionCostNanos,
                boolean needsRealization,
                boolean needsDecodePromotion,
                Runnable realizeAction,
                Runnable promoteDecodeAction) {
            this.id = Objects.requireNonNull(id, "id");
            if (estimatedRealizeCostNanos < 0L) {
                throw new IllegalArgumentException("estimatedRealizeCostNanos must be >= 0");
            }
            if (estimatedDecodePromotionCostNanos < 0L) {
                throw new IllegalArgumentException("estimatedDecodePromotionCostNanos must be >= 0");
            }
            this.distanceFromViewportCells = distanceFromViewportCells;
            this.estimatedRealizeCostNanos = estimatedRealizeCostNanos;
            this.estimatedDecodePromotionCostNanos = estimatedDecodePromotionCostNanos;
            this.needsRealization = needsRealization;
            this.needsDecodePromotion = needsDecodePromotion;
            this.realizeAction = Objects.requireNonNull(realizeAction, "realizeAction");
            this.promoteDecodeAction = Objects.requireNonNull(promoteDecodeAction, "promoteDecodeAction");
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public int distanceFromViewportCells() {
            return distanceFromViewportCells;
        }

        @Override
        public long estimatedRealizeCostNanos() {
            return estimatedRealizeCostNanos;
        }

        @Override
        public long estimatedDecodePromotionCostNanos() {
            return estimatedDecodePromotionCostNanos;
        }

        @Override
        public boolean needsRealization() {
            return needsRealization;
        }

        @Override
        public boolean needsDecodePromotion() {
            return needsDecodePromotion;
        }

        @Override
        public void realize() {
            realizeAction.run();
        }

        @Override
        public void promoteDecode() {
            promoteDecodeAction.run();
        }
    }
}
