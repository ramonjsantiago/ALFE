package com.fileexplorer.perf.viewport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ScrollStopCommitCoordinatorTest {
    @Test
    void emitsSingleCommitAfterQuietPeriod() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-04T00:00:00Z"));
        AtomicInteger commitCount = new AtomicInteger();
        AtomicLong latencyNanos = new AtomicLong();

        ScrollStopCommitCoordinator coordinator = new ScrollStopCommitCoordinator(
                clock,
                100,
                TimeUnit.MILLISECONDS,
                latency -> {
                    commitCount.incrementAndGet();
                    latencyNanos.set(latency);
                });

        coordinator.markScrollActivity();
        clock.advanceMillis(50);
        assertFalse(coordinator.pollForScrollStopCommit());

        clock.advanceMillis(60);
        assertTrue(coordinator.pollForScrollStopCommit());
        assertEquals(1, commitCount.get());
        assertEquals(TimeUnit.MILLISECONDS.toNanos(110), latencyNanos.get());

        clock.advanceMillis(200);
        assertFalse(coordinator.pollForScrollStopCommit());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public long millis() {
            return instant.toEpochMilli();
        }
    }
}
