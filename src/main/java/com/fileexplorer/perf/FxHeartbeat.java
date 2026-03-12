package com.fileexplorer.perf;

import javafx.animation.AnimationTimer;
import java.util.Objects;

/** Lightweight FX-thread heartbeat using AnimationTimer. */
public final class FxHeartbeat {

    private final FxThreadStallDetector detector;
    private final AnimationTimer timer;

    public FxHeartbeat(FxThreadStallDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector");
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                FxHeartbeat.this.detector.heartbeat();
            }
        };
    }

    public void start() { timer.start(); }
    public void stop() { timer.stop(); }
}
