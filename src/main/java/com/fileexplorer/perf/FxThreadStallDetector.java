package com.fileexplorer.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects JavaFX Application Thread stalls by sampling a heartbeat and dumping stack traces.
 *
 * Enable with: -Dfileexplorer.perf.fxStallDetector=true
 * Tune with:
 *   -Dfileexplorer.perf.fxStallPollMs=10
 *   -Dfileexplorer.perf.fxStallMs=100
 */
public final class FxThreadStallDetector {

    private static final Logger LOG = Logger.getLogger(FxThreadStallDetector.class.getName());

    private final ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
    private final ScheduledExecutorService scheduler;
    private final long fxThreadId;
    private final long pollMillis;
    private final long stallMillis;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastProgressNanos = new AtomicLong(System.nanoTime());

    private volatile ScheduledFuture<?> task;

    public FxThreadStallDetector(Thread fxThread, Duration pollInterval, Duration stallThreshold) {
        Objects.requireNonNull(fxThread, "fxThread");
        Objects.requireNonNull(pollInterval, "pollInterval");
        Objects.requireNonNull(stallThreshold, "stallThreshold");

        this.fxThreadId = fxThread.getId();
        this.pollMillis = Math.max(5, pollInterval.toMillis());
        this.stallMillis = Math.max(25, stallThreshold.toMillis());

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FxStallDetector");
            t.setDaemon(true);
            return t;
        });

        try {
            if (mxBean.isThreadCpuTimeSupported() && !mxBean.isThreadCpuTimeEnabled()) {
                mxBean.setThreadCpuTimeEnabled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Call from FX thread periodically to indicate progress. */
    public void heartbeat() {
        lastProgressNanos.set(System.nanoTime());
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;

        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                long sinceProgressMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastProgressNanos.get());
                if (sinceProgressMs >= stallMillis) {
                    dumpFxThread("FX thread stall detected: ~" + sinceProgressMs + "ms without heartbeat");
                    lastProgressNanos.set(System.nanoTime());
                }
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "FxThreadStallDetector failure", t);
            }
        }, pollMillis, pollMillis, TimeUnit.MILLISECONDS);

        LOG.info(() -> "FxThreadStallDetector started poll=" + pollMillis + "ms stall=" + stallMillis + "ms");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        try {
            if (task != null) task.cancel(true);
        } catch (Throwable ignored) {}
        scheduler.shutdownNow();
        LOG.info("FxThreadStallDetector stopped");
    }

    private void dumpFxThread(String reason) {
        ThreadInfo info = mxBean.getThreadInfo(fxThreadId, Integer.MAX_VALUE);
        if (info == null) {
            LOG.warning(reason + " (threadInfo=null)");
            return;
        }

        StringBuilder sb = new StringBuilder(4096);
        sb.append(reason).append('\n');
        sb.append("thread=").append(info.getThreadName())
          .append(" state=").append(info.getThreadState()).append('\n');

        for (StackTraceElement ste : info.getStackTrace()) {
            sb.append("  at ").append(ste).append('\n');
        }

        LOG.warning(sb.toString());
    }
}
