package com.fileexplorer.perf.viewport;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

/**
 * Debounces scroll activity and emits a single deterministic scroll-stop commit callback.
 *
 * <p>The coordinator is deliberately polling-friendly. A JavaFX integration can feed every scroll,
 * drag, wheel, or viewport mutation into {@link #markScrollActivity()} and then call
 * {@link #pollForScrollStopCommit()} from an {@code AnimationTimer}, pulse listener, or existing
 * scheduler tick.
 */
public final class ScrollStopCommitCoordinator {
    private final Clock clock;
    private final long quietPeriodNanos;
    private final LongConsumer onCommit;

    private long lastScrollActivityNanos = Long.MIN_VALUE;
    private long lastCommittedActivityNanos = Long.MIN_VALUE;

    /**
     * Creates a coordinator.
     *
     * @param clock time source.
     * @param quietPeriod duration of quiescence required before a commit is emitted, in nanoseconds.
     * @param onCommit callback receiving the measured latency from the most recent scroll activity to
     *     the emitted commit.
     */
    public ScrollStopCommitCoordinator(Clock clock, long quietPeriod, TimeUnit quietPeriodUnit, LongConsumer onCommit) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (quietPeriod < 0L) {
            throw new IllegalArgumentException("quietPeriod must be >= 0");
        }
        this.quietPeriodNanos = Objects.requireNonNull(quietPeriodUnit, "quietPeriodUnit").toNanos(quietPeriod);
        this.onCommit = Objects.requireNonNull(onCommit, "onCommit");
    }

    /**
     * Marks new scroll or viewport activity at the current time.
     */
    public void markScrollActivity() {
        lastScrollActivityNanos = nowNanos();
    }

    /**
     * Polls the coordinator and emits at most one commit for the latest scroll burst.
     *
     * @return {@code true} when a new commit was emitted; otherwise {@code false}.
     */
    public boolean pollForScrollStopCommit() {
        if (lastScrollActivityNanos == Long.MIN_VALUE) {
            return false;
        }
        if (lastScrollActivityNanos == lastCommittedActivityNanos) {
            return false;
        }
        long now = nowNanos();
        if (now - lastScrollActivityNanos < quietPeriodNanos) {
            return false;
        }
        lastCommittedActivityNanos = lastScrollActivityNanos;
        onCommit.accept(Math.max(0L, now - lastScrollActivityNanos));
        return true;
    }

    /**
     * Returns the time since the last scroll activity.
     *
     * @return elapsed nanoseconds, or {@code Long.MAX_VALUE} when no activity has been observed.
     */
    public long nanosSinceLastScrollActivity() {
        if (lastScrollActivityNanos == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, nowNanos() - lastScrollActivityNanos);
    }

    private long nowNanos() {
        long millis = clock.millis();
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }
}
