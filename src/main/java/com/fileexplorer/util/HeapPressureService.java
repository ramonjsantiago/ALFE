package com.fileexplorer.util;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 4C.1: coarse heap pressure monitor.
 *
 * <p>When heap usage exceeds a threshold, triggers best-effort cache trimming to avoid
 * long-run memory creep and GC spikes.</p>
 */
public final class HeapPressureService implements AutoCloseable {

    public interface Listener {
        void onPressure(double usedFrac);
    }

    private final ScheduledExecutorService ses;
    private final double threshold;
    private final long intervalMs;
    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public HeapPressureService(double threshold, long intervalMs, Listener listener) {
        this.threshold = clamp(threshold, 0.50, 0.98);
        this.intervalMs = Math.max(250L, intervalMs);
        this.listener = Objects.requireNonNull(listener, "listener");
        this.ses = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "heap-pressure");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        ses.scheduleAtFixedRate(this::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        if (closed.get()) return;
        try {
            Runtime rt = Runtime.getRuntime();
            long max = rt.maxMemory();
            long used = rt.totalMemory() - rt.freeMemory();
            if (max <= 0L) return;
            double frac = (double) used / (double) max;
            if (frac >= threshold) {
                listener.onPressure(frac);
            }
        } catch (Throwable ignored) {
            // best effort
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try { ses.shutdownNow(); } catch (Throwable ignored) {}
        }
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
