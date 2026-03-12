package com.fileexplorer.util;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 4M: startup scheduler with bounded deferral.
 *
 * <p>Goals:
 * <ul>
 *   <li>critical work runs promptly once the main UI is installed</li>
 *   <li>idle work waits for a short quiet window when possible</li>
 *   <li>idle work is still forced through after a maximum deferral so startup does not stretch forever</li>
 *   <li>each pump respects a small batch and time budget to avoid long FX-thread stalls</li>
 * </ul>
 */
public final class StartupWorkQueue {

    private final Queue<Runnable> afterUiReady = new ConcurrentLinkedQueue<>();
    private final Queue<Runnable> critical = new ConcurrentLinkedQueue<>();
    private final Queue<QueuedTask> idle = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean uiReady = new AtomicBoolean(false);
    private final AtomicBoolean criticalPumpScheduled = new AtomicBoolean(false);
    private final AtomicBoolean idlePumpScheduled = new AtomicBoolean(false);

    private final PauseTransition idleTimer;
    private final PauseTransition maxDeferralTimer;
    private final long idleBatchBudgetNanos;

    public StartupWorkQueue() {
        long idleDelayMs = longProp("fileexplorer.startup.idleDelayMs", 450L);
        long maxDeferralMs = longProp("fileexplorer.startup.maxIdleDeferralMs", 2200L);
        long idleBudgetMs = longProp("fileexplorer.startup.idleBudgetMs", 12L);

        this.idleBatchBudgetNanos = Math.max(1L, idleBudgetMs) * 1_000_000L;

        this.idleTimer = new PauseTransition(Duration.millis(Math.max(50L, idleDelayMs)));
        this.idleTimer.setOnFinished(_ -> pumpIdle(false));

        this.maxDeferralTimer = new PauseTransition(Duration.millis(Math.max(Math.max(250L, idleDelayMs), maxDeferralMs)));
        this.maxDeferralTimer.setOnFinished(_ -> pumpIdle(true));
    }

    /**
     * Attach basic input listeners so background work does not compete with active interaction.
     */
    public void attachToScene(Scene scene) {
        if (scene == null) {
            return;
        }

        scene.addEventFilter(InputEvent.ANY, this::notifyUserActivity);
        scene.addEventFilter(ScrollEvent.ANY, this::notifyUserActivity);
        scene.addEventFilter(MouseEvent.ANY, this::notifyUserActivity);
        scene.addEventFilter(KeyEvent.ANY, this::notifyUserActivity);

        Platform.runLater(() -> {
            restartIdleTimer();
            armMaxDeferralTimerIfNeeded(false);
        });
    }

    /**
     * Mark the UI as ready (typically immediately after the main root is installed).
     */
    public void markUiReady() {
        if (!uiReady.compareAndSet(false, true)) {
            return;
        }

        Runnable r;
        while ((r = afterUiReady.poll()) != null) {
            runFx(r);
        }
        scheduleCriticalPump();
    }

    /** Run on the FX thread after the main scene is installed. */
    public void runAfterUiReady(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (uiReady.get()) {
            runFx(task);
        } else {
            afterUiReady.add(task);
        }
    }

    /**
     * Run promptly after the UI is ready, but still on a later pulse so root swaps and scene wiring finish first.
     */
    public void runCritical(Runnable task) {
        Objects.requireNonNull(task, "task");
        critical.add(task);
        if (uiReady.get()) {
            scheduleCriticalPump();
        }
    }

    /**
     * Run after a short idle window. A separate maximum-deferral timer ensures that queued startup
     * work still drains within a bounded time even if the user keeps moving the mouse.
     */
    public void runIdle(Runnable task) {
        Objects.requireNonNull(task, "task");
        idle.add(new QueuedTask(task));
        restartIdleTimer();
        armMaxDeferralTimerIfNeeded(false);
    }

    /** Call when the user does something (scroll/mouse/key). */
    public void notifyUserActivity(Event e) {
        restartIdleTimer();
    }

    private void restartIdleTimer() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::restartIdleTimer);
            return;
        }
        idleTimer.stop();
        if (!idle.isEmpty()) {
            idleTimer.playFromStart();
        }
    }

    private void armMaxDeferralTimerIfNeeded(boolean reset) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> armMaxDeferralTimerIfNeeded(reset));
            return;
        }
        if (idle.isEmpty()) {
            maxDeferralTimer.stop();
            return;
        }
        if (reset || maxDeferralTimer.getStatus() != Animation.Status.RUNNING) {
            maxDeferralTimer.stop();
            maxDeferralTimer.playFromStart();
        }
    }

    private void scheduleCriticalPump() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::scheduleCriticalPump);
            return;
        }
        if (!uiReady.get()) {
            return;
        }
        if (criticalPumpScheduled.compareAndSet(false, true)) {
            Platform.runLater(this::pumpCritical);
        }
    }

    private void pumpCritical() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::pumpCritical);
            return;
        }
        criticalPumpScheduled.set(false);

        Runnable r;
        int max = intProp("fileexplorer.startup.criticalBatch", 4);
        int ran = 0;
        while (ran < max && (r = critical.poll()) != null) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
            ran++;
        }

        if (!critical.isEmpty()) {
            scheduleCriticalPump();
        }
    }

    private void pumpIdle(boolean forced) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> pumpIdle(forced));
            return;
        }
        if (!idlePumpScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            QueuedTask queued;
            int max = intProp("fileexplorer.startup.idleBatch", 3);
            int ran = 0;
            long deadline = System.nanoTime() + idleBatchBudgetNanos;
            while (ran < max && (queued = idle.poll()) != null) {
                try {
                    queued.task.run();
                } catch (Throwable ignored) {
                }
                ran++;
                if (System.nanoTime() >= deadline) {
                    break;
                }
            }
        } finally {
            idlePumpScheduled.set(false);
            if (idle.isEmpty()) {
                idleTimer.stop();
                maxDeferralTimer.stop();
            } else {
                idleTimer.playFromStart();
                armMaxDeferralTimerIfNeeded(true);
            }
        }
    }

    private static void runFx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    private static int intProp(String key, int def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long longProp(String key, long def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static final class QueuedTask {
        private final Runnable task;

        private QueuedTask(Runnable task) {
            this.task = task;
        }
    }
}
