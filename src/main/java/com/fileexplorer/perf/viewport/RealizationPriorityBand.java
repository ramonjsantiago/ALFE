package com.fileexplorer.perf.viewport;

/**
 * Priority bands for viewport work.
 *
 * <p>The intended policy for HOTFIX176 is:
 * <ul>
 *   <li>{@link #VISIBLE}: items intersecting the current viewport and therefore eligible for the
 *   most aggressive realization and decode promotion.</li>
 *   <li>{@link #NEAR_VIEWPORT}: items just outside the viewport that may be realized or decoded only
 *   when there is headroom within the current frame budget.</li>
 *   <li>{@link #FAR_OFFSCREEN}: items well away from the viewport that should remain queued, cheap,
 *   and interruptible.</li>
 * </ul>
 */
public enum RealizationPriorityBand {
    /** Work that directly affects currently visible cells. */
    VISIBLE,

    /** Work that may become visible soon, typically within a small scroll-ahead window. */
    NEAR_VIEWPORT,

    /** Work that is far enough away from the viewport to be treated as background. */
    FAR_OFFSCREEN
}
