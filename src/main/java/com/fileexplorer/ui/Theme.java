package com.fileexplorer.ui;

import com.fileexplorer.ui.service.ThemeService;

/**
 * Compatibility wrapper. Prefer ThemeService.Theme going forward.
 */
@Deprecated
public enum Theme {
    LIGHT,
    DARK;

    public ThemeService.Theme toServiceTheme() {
        return (this == DARK) ? ThemeService.Theme.DARK : ThemeService.Theme.LIGHT;
    }

    public static Theme fromServiceTheme(ThemeService.Theme t) {
        if (t == null) return DARK;
        return t.isDark() ? DARK : LIGHT;
    }

    public static Theme fromPreference() {
        return fromServiceTheme(ThemeService.fromPreference());
    }
}
