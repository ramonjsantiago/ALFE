package com.fileexplorer.ui;

import java.util.Locale;
import java.util.prefs.Preferences;

public enum Theme {
    LIGHT,
    DARK;

    private static final String PREF_NODE = "com.fileexplorer";
    private static final String KEY_DARK = "theme.dark";

    public static Theme fromPreference() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        boolean dark = prefs.getBoolean(KEY_DARK, false);
        return dark ? DARK : LIGHT;
    }

    public static Theme fromString(String value, Theme fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if ("dark".equals(v)) {
            return DARK;
        }
        if ("light".equals(v)) {
            return LIGHT;
        }
        return fallback;
    }

    public static Theme fromThemeServiceTheme(ThemeService.Theme theme) {
        if (theme == null) {
            return LIGHT;
        }
        String name = theme.name();
        return fromString(name, LIGHT);
    }

    public ThemeService.Theme toThemeServiceTheme() {
        try {
            return ThemeService.Theme.valueOf(this.name());
        } catch (Exception ex) {
            // If your ThemeService.Theme has different names, fall back safely.
            return ThemeService.Theme.valueOf("LIGHT");
        }
    }
}
