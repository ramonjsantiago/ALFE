package com.fileexplorer.ui;

import com.fileexplorer.service.ThemeService;

public enum Theme {
    LIGHT,
    DARK;

    public static Theme fromPreference() {
        ThemeService.Theme t = ThemeService.fromPreference();
        return t == ThemeService.Theme.DARK ? DARK : LIGHT;
    }
}
