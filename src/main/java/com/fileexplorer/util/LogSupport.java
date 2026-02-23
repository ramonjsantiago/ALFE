package com.fileexplorer.util;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Small helper to keep entry logging lightweight.
 *
 * <p>Use {@link #enter(Logger, String)} at the beginning of methods.
 */
public final class LogSupport {

    /**
     * Global switch for entry-tracing.
     *
     * <p>Many core code paths (cell factories, caches, sort keys) are invoked at extremely high frequency.
     * Entry-logging every invocation can overwhelm the console and create significant allocation pressure.
     *
     * <p>Enable only when actively diagnosing control flow:
     * {@code -Dfileexplorer.log.enter=true}
     */
    private static final String PROP_ENTER = "fileexplorer.log.enter";

/**
 * LogSupport.
 *
 * @return TODO
 */
    private LogSupport() {
        // utility
    }

    /**
     * Logs a method entry at {@link Level#FINER}. Uses a Supplier so the message is only built if enabled.
     */
    public static void enter(Logger log, String methodName) {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(methodName, "methodName");

        // Hard gate to prevent accidental log storms.
        if (!Boolean.getBoolean(PROP_ENTER)) {
            return;
        }

        if (!log.isLoggable(Level.FINER)) {
            return;
        }

        log.log(Level.FINER, () -> "ENTER " + methodName + " [thread=" + Thread.currentThread().getName() + "]");
    }

    /**
     * Overload that includes lightweight arguments (stringified) in the entry log.
     *
     * <p>Note: this is intentionally simple and best-effort (null-safe String.valueOf()).
     * Keep arguments small; this is meant for quick debugging traces.
     */
    public static void enter(Logger log, String methodName, Object... args) {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(methodName, "methodName");

        // Hard gate to prevent accidental log storms.
        if (!Boolean.getBoolean(PROP_ENTER)) {
            return;
        }

        if (!log.isLoggable(Level.FINER)) {
            return;
        }

        log.log(Level.FINER, () -> {
            StringBuilder sb = new StringBuilder(128);
            sb.append("ENTER ").append(methodName)
                    .append(" [thread=").append(Thread.currentThread().getName()).append(']');

            if (args != null && args.length > 0) {
                sb.append(" args=");
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(String.valueOf(args[i]));
                }
            }
            return sb.toString();
        });
    }

    /**
     * Convenience: log a message at FINER with supplier.
     */
    public static void finer(Logger log, java.util.function.Supplier<String> msgSupplier) {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(msgSupplier, "msgSupplier");

        if (!log.isLoggable(Level.FINER)) {
            return;
        }
        log.log(Level.FINER, msgSupplier);
    }
}
