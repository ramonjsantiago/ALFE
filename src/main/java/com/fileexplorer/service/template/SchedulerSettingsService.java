package com.fileexplorer.service.template;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Persists and loads global {@link SchedulerSettings}.
 */
public final class SchedulerSettingsService {

    private static final String KEY_TICK_SECONDS = "scheduler.tickSeconds";
    private static final String KEY_MAX_PARALLEL = "scheduler.maxParallel";
    private static final String KEY_MAX_RETRY = "scheduler.maxRetryAttempts";
    private static final String KEY_RETRY_BASE = "scheduler.retryBaseMillis";
    private static final String KEY_RETRY_MAX = "scheduler.retryMaxMillis";
    private static final String KEY_HISTORY_RETENTION = "scheduler.historyRetentionEntries";

    private final Preferences prefs;

    public SchedulerSettingsService() {
        this(Preferences.userNodeForPackage(SchedulerSettingsService.class));
    }

    public SchedulerSettingsService(Preferences prefs) {
        this.prefs = Objects.requireNonNull(prefs, "prefs");
    }

    public SchedulerSettings load() {
        SchedulerSettings d = SchedulerSettings.defaults();
        return new SchedulerSettings(
                prefs.getInt(KEY_TICK_SECONDS, d.tickSeconds()),
                prefs.getInt(KEY_MAX_PARALLEL, d.maxParallel()),
                prefs.getInt(KEY_MAX_RETRY, d.maxRetryAttempts()),
                prefs.getLong(KEY_RETRY_BASE, d.retryBaseMillis()),
                prefs.getLong(KEY_RETRY_MAX, d.retryMaxMillis()),
                prefs.getInt(KEY_HISTORY_RETENTION, d.historyRetentionEntries())
        );
    }

    public void save(SchedulerSettings settings) {
        Objects.requireNonNull(settings, "settings");
        prefs.putInt(KEY_TICK_SECONDS, settings.tickSeconds());
        prefs.putInt(KEY_MAX_PARALLEL, settings.maxParallel());
        prefs.putInt(KEY_MAX_RETRY, settings.maxRetryAttempts());
        prefs.putLong(KEY_RETRY_BASE, settings.retryBaseMillis());
        prefs.putLong(KEY_RETRY_MAX, settings.retryMaxMillis());
        prefs.putInt(KEY_HISTORY_RETENTION, settings.historyRetentionEntries());
    }
}
