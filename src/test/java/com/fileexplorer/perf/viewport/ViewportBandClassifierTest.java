package com.fileexplorer.perf.viewport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ViewportBandClassifierTest {
    @Test
    void classifiesVisibleNearAndFarBands() {
        ViewportBandClassifier classifier = new ViewportBandClassifier(3);

        assertEquals(RealizationPriorityBand.VISIBLE, classifier.classify(0));
        assertEquals(RealizationPriorityBand.NEAR_VIEWPORT, classifier.classify(1));
        assertEquals(RealizationPriorityBand.NEAR_VIEWPORT, classifier.classify(-3));
        assertEquals(RealizationPriorityBand.FAR_OFFSCREEN, classifier.classify(4));
    }
}
