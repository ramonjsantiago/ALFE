package com.fileexplorer.perf.viewport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BudgetedViewportSchedulerTest {
    @Test
    void runsVisibleWorkBeforeNearAndFarWorkAndDropsDecodeWhenBudgetRunsOut() {
        ViewportBandClassifier classifier = new ViewportBandClassifier(2);
        ViewportSchedulerTelemetry telemetry = new ViewportSchedulerTelemetry();
        AtomicInteger scrollStopCommitCount = new AtomicInteger();
        MutableClock clock = new MutableClock(Instant.parse("2026-04-04T00:00:00Z"));

        BudgetedViewportScheduler scheduler = new BudgetedViewportScheduler(
                classifier,
                telemetry,
                clock,
                100,
                TimeUnit.MILLISECONDS,
                snapshot -> scrollStopCommitCount.incrementAndGet());

        StringBuilder execution = new StringBuilder();
        scheduler.submit(List.of(
                new ViewportWorkItem.Basic("visible", 0, 10, 10, true, true,
                        () -> execution.append("VR"),
                        () -> execution.append("VD")),
                new ViewportWorkItem.Basic("near", 2, 10, 50, true, true,
                        () -> execution.append("NR"),
                        () -> execution.append("ND")),
                new ViewportWorkItem.Basic("far", 5, 10, 10, true, true,
                        () -> execution.append("FR"),
                        () -> execution.append("FD"))));

        scheduler.markScrollActivity();
        scheduler.runFrame(70L);
        assertEquals("VRVDNRFR", execution.toString());
        assertEquals(1L, telemetry.snapshot().decodePromotionDrops());

        clock.advanceMillis(110);
        scheduler.runFrame(70L);
        assertEquals(1, scrollStopCommitCount.get());
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
