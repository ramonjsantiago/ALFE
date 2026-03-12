package com.fileexplorer.perf;

import javafx.application.Platform;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Startup orchestration:
 *  - Phase A: minimal bootstrap (FX thread)
 *  - Phase B: input-ready gate (FX thread)
 *  - Phase C: background hydration (async)
 */
public final class StartupOrchestrator {

    private static final Logger LOG = Logger.getLogger(StartupOrchestrator.class.getName());

    private final ExecutorService bg;
    private final AtomicBoolean inputReady = new AtomicBoolean(false);

    public StartupOrchestrator(ExecutorService bgExecutor) {
        this.bg = Objects.requireNonNull(bgExecutor, "bgExecutor");
    }

    public boolean isInputReady() {
        return inputReady.get();
    }

    public void runMinimalFx(Runnable minimalFx) {
        Objects.requireNonNull(minimalFx, "minimalFx");
        long t0 = System.nanoTime();
        minimalFx.run();
        long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        LOG.info(() -> "Startup minimal FX completed in " + ms + "ms");
    }

    public void markInputReady(Runnable afterInputEnabledFx) {
        Objects.requireNonNull(afterInputEnabledFx, "afterInputEnabledFx");
        Platform.runLater(() -> {
            long t0 = System.nanoTime();
            inputReady.set(true);
            afterInputEnabledFx.run();
            long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
            LOG.info(() -> "Startup input-ready gate completed in " + ms + "ms");
        });
    }

    public Future<?> runBackground(String name, Runnable task) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(task, "task");

        return bg.submit(() -> {
            long t0 = System.nanoTime();
            try {
                task.run();
                long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
                LOG.info(() -> "Startup BG task '" + name + "' completed in " + ms + "ms");
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Startup BG task '" + name + "' failed", t);
            }
        });
    }
}
