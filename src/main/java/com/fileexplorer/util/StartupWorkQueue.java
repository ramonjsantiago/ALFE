package com.fileexplorer.util;

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
 * Phase 4A.2: A tiny scheduler for startup work that must not block first paint.
 *
 * <p>Features:
 * <ul>
 *   <li>runAfterUiReady: tasks run after the main Scene is installed</li>
 *   <li>runIdle: tasks run only after a short user-idle window (no input/scroll)</li>
 *   <li>attachToScene: hooks input events to reset the idle timer</li>
 * </ul>
 *
 * <p>This intentionally stays lightweight and does not use extra threads; callers should
 * dispatch long-running work to their IO executors.</p>
 */
public final class StartupWorkQueue {

    private final Queue<Runnable> afterUiReady = new ConcurrentLinkedQueue<>();
    private final Queue<Runnable> idle = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean uiReady = new AtomicBoolean(false);
    private final AtomicBoolean idlePumpScheduled = new AtomicBoolean(false);

    private final PauseTransition idleTimer;

    public StartupWorkQueue() {
        long idleDelayMs = longProp("fileexplorer.startup.idleDelayMs", 450L);
        this.idleTimer = new PauseTransition(Duration.millis(Math.max(50, idleDelayMs)));
        this.idleTimer.setOnFinished(_ -> pumpIdle());
    }

    /**
     * Attach basic input listeners so background work does not compete with active interaction.
     */
    public void attachToScene(Scene scene) {
        if (scene == null) return;

        // Any input resets the idle timer.
        scene.addEventFilter(InputEvent.ANY, e -> notifyUserActivity(e));
        scene.addEventFilter(ScrollEvent.ANY, e -> notifyUserActivity(e));
        scene.addEventFilter(MouseEvent.ANY, e -> notifyUserActivity(e));
        scene.addEventFilter(KeyEvent.ANY, e -> notifyUserActivity(e));

        // Start the idle timer once we have a scene.
        Platform.runLater(this::restartIdleTimer);
    }

    /**
     * Mark the UI as ready (typically immediately after stage.setScene(mainScene)).
     */
    public void markUiReady() {
        if (uiReady.compareAndSet(false, true)) {
            // Drain queued tasks.
            Runnable r;
            while ((r = afterUiReady.poll()) != null) {
                runFx(r);
            }
        }
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
     * Run after a short idle window (no user input). Intended for low-priority startup work.
     */
    public void runIdle(Runnable task) {
        Objects.requireNonNull(task, "task");
        idle.add(task);
        // Ensure the timer is armed.
        restartIdleTimer();
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
        idlePumpScheduled.set(false);
        idleTimer.stop();
        idleTimer.playFromStart();
    }

    private void pumpIdle() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::pumpIdle);
            return;
        }
        // Avoid re-entrancy.
        if (!idlePumpScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            Runnable r;
            int max = intProp("fileexplorer.startup.idleBatch", 6);
            int ran = 0;
            while (ran < max && (r = idle.poll()) != null) {
                try {
                    r.run();
                } catch (Throwable ignored) {
                }
                ran++;
            }
        } finally {
            idlePumpScheduled.set(false);
            if (!idle.isEmpty()) {
                // Keep pumping, but only if we remain idle.
                idleTimer.playFromStart();
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
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long longProp(String key, long def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
