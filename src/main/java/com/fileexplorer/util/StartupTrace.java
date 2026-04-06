package com.fileexplorer.util;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight startup timing helper.
 *
 * Uses {@link System#nanoTime()} deltas (monotonic) and emits them through JUL so startup timing stays
 * on the same logging pipeline as the rest of the application.
 */
public final class StartupTrace {

    private static final Logger LOG = Logger.getLogger(StartupTrace.class.getName());
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
        if (!ENABLED.get() || !LOG.isLoggable(Level.INFO)) {
            return;
        }
        long dtNanos = System.nanoTime() - T0_NANOS;
        double ms = dtNanos / 1_000_000.0;
        LOG.log(Level.INFO, () -> String.format("[STARTUP] +%8.3f ms | %s | wall=%s", ms, label, Instant.now()));
    }
}
