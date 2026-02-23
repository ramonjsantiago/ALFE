package com.fileexplorer.service.template;

import java.util.Objects;

/**
 * Global scheduler settings.
 *
 * <p>These settings control the background template scheduler tick cadence and execution limits.
 * Values are persisted via {@link SchedulerSettingsService}.</p>
 */
public final class SchedulerSettings {

    /** Default tick seconds for due-evaluation. */
    public static final int DEFAULT_TICK_SECONDS = 5;

    /** Default maximum concurrent template executions. */
    public static final int DEFAULT_MAX_PARALLEL = 2;

    /** Default maximum retry attempts for recurring runs. */
    public static final int DEFAULT_MAX_RETRY = 3;

    /** Default base backoff in milliseconds. */
    public static final long DEFAULT_RETRY_BASE_MILLIS = 30_000L;

    /** Default max backoff in milliseconds. */
    public static final long DEFAULT_RETRY_MAX_MILLIS = 60 * 60_000L;

    /** Default run history retention (max entries to keep). */
    public static final int DEFAULT_HISTORY_RETENTION_ENTRIES = 2000;

    private final int tickSeconds;
    private final int maxParallel;
    private final int maxRetryAttempts;
    private final long retryBaseMillis;
    private final long retryMaxMillis;
    private final int historyRetentionEntries;

    public SchedulerSettings(
            int tickSeconds,
            int maxParallel,
            int maxRetryAttempts,
            long retryBaseMillis,
            long retryMaxMillis,
            int historyRetentionEntries
    ) {
        this.tickSeconds = clamp(tickSeconds, 1, 60);
        this.maxParallel = clamp(maxParallel, 1, 16);
        this.maxRetryAttempts = clamp(maxRetryAttempts, 0, 20);
        this.retryBaseMillis = clampLong(retryBaseMillis, 1_000L, 24 * 60 * 60_000L);
        this.retryMaxMillis = clampLong(retryMaxMillis, this.retryBaseMillis, 24 * 60 * 60_000L);
        this.historyRetentionEntries = clamp(historyRetentionEntries, 100, 100_000);
    }

    public int tickSeconds() {
        return tickSeconds;
    }

    public int maxParallel() {
        return maxParallel;
    }

    public int maxRetryAttempts() {
        return maxRetryAttempts;
    }

    public long retryBaseMillis() {
        return retryBaseMillis;
    }

    public long retryMaxMillis() {
        return retryMaxMillis;
    }

    public int historyRetentionEntries() {
        return historyRetentionEntries;
    }

    public SchedulerSettings withTickSeconds(int v) {
        return new SchedulerSettings(v, maxParallel, maxRetryAttempts, retryBaseMillis, retryMaxMillis, historyRetentionEntries);
    }

    public SchedulerSettings withMaxParallel(int v) {
        return new SchedulerSettings(tickSeconds, v, maxRetryAttempts, retryBaseMillis, retryMaxMillis, historyRetentionEntries);
    }

    public SchedulerSettings withRetryPolicy(int attempts, long baseMillis, long maxMillis) {
        return new SchedulerSettings(tickSeconds, maxParallel, attempts, baseMillis, maxMillis, historyRetentionEntries);
    }

    public SchedulerSettings withHistoryRetentionEntries(int v) {
        return new SchedulerSettings(tickSeconds, maxParallel, maxRetryAttempts, retryBaseMillis, retryMaxMillis, v);
    }

    public static SchedulerSettings defaults() {
        return new SchedulerSettings(
                DEFAULT_TICK_SECONDS,
                DEFAULT_MAX_PARALLEL,
                DEFAULT_MAX_RETRY,
                DEFAULT_RETRY_BASE_MILLIS,
                DEFAULT_RETRY_MAX_MILLIS,
                DEFAULT_HISTORY_RETENTION_ENTRIES
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchedulerSettings that)) return false;
        return tickSeconds == that.tickSeconds
                && maxParallel == that.maxParallel
                && maxRetryAttempts == that.maxRetryAttempts
                && retryBaseMillis == that.retryBaseMillis
                && retryMaxMillis == that.retryMaxMillis
                && historyRetentionEntries == that.historyRetentionEntries;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tickSeconds, maxParallel, maxRetryAttempts, retryBaseMillis, retryMaxMillis, historyRetentionEntries);
    }

    @Override
    public String toString() {
        return "SchedulerSettings{" +
                "tickSeconds=" + tickSeconds +
                ", maxParallel=" + maxParallel +
                ", maxRetryAttempts=" + maxRetryAttempts +
                ", retryBaseMillis=" + retryBaseMillis +
                ", retryMaxMillis=" + retryMaxMillis +
                ", historyRetentionEntries=" + historyRetentionEntries +
                '}';
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static long clampLong(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }
}
