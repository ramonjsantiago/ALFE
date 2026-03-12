package com.fileexplorer.util;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight startup timing helper.
 *
 * Uses System.nanoTime() deltas (monotonic) and logs to stdout to avoid
 * logger initialization overhead impacting first paint.
 */
public final class StartupTrace {

    private static final long T0_NANOS = System.nanoTime();
    private static final AtomicBoolean ENABLED = new AtomicBoolean(
            Boolean.parseBoolean(System.getProperty("fileexplorer.startupTrace", "true"))
    );

    private StartupTrace() {
    }

    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
    }

    public static void mark(String label) {
        if (!ENABLED.get()) {
            return;
        }
        long dtNanos = System.nanoTime() - T0_NANOS;
        double ms = dtNanos / 1_000_000.0;
        // Keep formatting predictable for grep.
        // Wall clock is captured per mark (helps correlate with external logs).
        System.out.printf("[STARTUP] +%8.3f ms | %s | wall=%s%n", ms, label, Instant.now());
    }
}
