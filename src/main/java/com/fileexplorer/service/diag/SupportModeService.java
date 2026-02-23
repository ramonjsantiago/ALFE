package com.fileexplorer.service.diag;

import java.util.prefs.Preferences;

/**
 * Preferences-backed "Support Mode" toggle.
 *
 * <p>Support mode is intentionally lightweight: it is used to enable extra
 * diagnostics UI affordances and best-effort troubleshooting bundle generation.
 * It must never break normal app execution.</p>
 */
public final class SupportModeService {

    private static final String KEY_ENABLED = "supportMode.enabled";

    private final Preferences prefs;

    public SupportModeService() {
        this(Preferences.userNodeForPackage(SupportModeService.class));
    }

    public SupportModeService(Preferences prefs) {
        this.prefs = prefs;
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.putBoolean(KEY_ENABLED, enabled);
    }
}
