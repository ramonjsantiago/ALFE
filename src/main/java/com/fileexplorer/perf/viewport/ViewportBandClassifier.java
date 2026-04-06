package com.fileexplorer.perf.viewport;

import java.util.Objects;

/**
 * Classifies work into realization priority bands based on distance from the active viewport.
 *
 * <p>Distance is expressed in logical cells (rows, tiles, or any other stable viewport unit) and is
 * always treated as absolute distance. A distance of {@code 0} means the item is visible now.
 */
public final class ViewportBandClassifier {
    private final int nearViewportThresholdCells;

    /**
     * Creates a classifier.
     *
     * @param nearViewportThresholdCells the maximum absolute distance, in logical cells, that still
     *     qualifies an item for the near-viewport band. Values lower than {@code 1} are rejected.
     */
    public ViewportBandClassifier(int nearViewportThresholdCells) {
        if (nearViewportThresholdCells < 1) {
            throw new IllegalArgumentException("nearViewportThresholdCells must be >= 1");
        }
        this.nearViewportThresholdCells = nearViewportThresholdCells;
    }

    /**
     * Returns the near-viewport threshold used by this classifier.
     *
     * @return the threshold in logical cells.
     */
    public int getNearViewportThresholdCells() {
        return nearViewportThresholdCells;
    }

    /**
     * Classifies a single distance value.
     *
     * @param distanceFromViewportCells absolute or signed distance from the visible viewport in
     *     logical cells. {@code 0} means visible.
     * @return the applicable realization priority band.
     */
    public RealizationPriorityBand classify(int distanceFromViewportCells) {
        int distance = Math.abs(distanceFromViewportCells);
        if (distance == 0) {
            return RealizationPriorityBand.VISIBLE;
        }
        if (distance <= nearViewportThresholdCells) {
            return RealizationPriorityBand.NEAR_VIEWPORT;
        }
        return RealizationPriorityBand.FAR_OFFSCREEN;
    }

    /**
     * Classifies a viewport work item.
     *
     * @param item the work item to classify.
     * @return the applicable realization priority band.
     */
    public RealizationPriorityBand classify(ViewportWorkItem item) {
        Objects.requireNonNull(item, "item");
        return classify(item.distanceFromViewportCells());
    }
}
