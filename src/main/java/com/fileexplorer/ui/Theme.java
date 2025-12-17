package com.fileexplorer.ui;

import java.util.Objects;

import com.fileexplorer.ui.service.ThemeService;

/**
 * UI-facing theme enum kept intentionally lightweight.
 *
 * Notes:
 * - No Preferences usage here (that belongs in ThemeService).
 * - Provides explicit bridging helpers to ThemeService.Theme to avoid type confusion.
 */
public enum Theme {
    LIGHT,
    DARK;

    public Theme toggle() {
        return this == DARK ? LIGHT : DARK;
    }

    public ThemeService.Theme toServiceTheme() {
        return this == DARK ? ThemeService.Theme.DARK : ThemeService.Theme.LIGHT;
    }

    public static Theme fromServiceTheme(ThemeService.Theme theme) {
        Objects.requireNonNull(theme, "theme");
        return theme == ThemeService.Theme.DARK ? DARK : LIGHT;
    }

    public static Theme parseOrDefault(String value, Theme fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Theme.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
