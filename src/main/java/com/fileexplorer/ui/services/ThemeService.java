package com.fileexplorer.ui.service;

import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Single source of truth for UI theme selection and stylesheet application.
 *
 * Location is intentionally: com.fileexplorer.ui.service
 */
public final class ThemeService {

    public enum Theme {
        LIGHT,
        DARK;

        public boolean isDark() {
            return this == DARK;
        }

        public Theme toggle() {
            return (this == DARK) ? LIGHT : DARK;
        }
    }

    private static final String PREF_NODE = "com.fileexplorer";
    private static final String PREF_KEY_THEME = "ui.theme"; // "LIGHT" or "DARK"

    // Adjust these paths to match your resources layout.
    // These assume: src/main/resources/com/fileexplorer/ui/css/...
    private static final String CSS_BASE = "/com/fileexplorer/ui/css/explorer-base.css";
    private static final String CSS_TABLE = "/com/fileexplorer/ui/css/explorer-table.css";
    private static final String CSS_WIN11 = "/com/fileexplorer/ui/css/explorer-win11.css";
    private static final String CSS_THEME = "/com/fileexplorer/ui/css/explorer-theme.css";
    private static final String CSS_LIGHT = "/com/fileexplorer/ui/css/explorer-light-win.css";
    private static final String CSS_DARK = "/com/fileexplorer/ui/css/explorer-dark-win.css";

    private final Preferences prefs;
    private Theme theme;

    public ThemeService() {
        this.prefs = Preferences.userRoot().node(PREF_NODE);
        this.theme = fromPreference();
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        savePreference(theme);
    }

    public Theme toggleTheme() {
        Theme next = (this.theme == null) ? Theme.DARK : this.theme.toggle();
        setTheme(next);
        return next;
    }

    public boolean isDarkPreferred() {
        return getTheme().isDark();
    }

    public void setDarkPreferred(boolean dark) {
        setTheme(dark ? Theme.DARK : Theme.LIGHT);
    }

    /**
     * Apply theme stylesheets to a Scene (recommended entry point).
     */
    public void apply(Scene scene) {
        if (scene == null) {
            return;
        }

        // Remove any previous explorer stylesheets so we can re-apply cleanly.
        removeExplorerStylesheets(scene);

        List<String> sheets = buildStylesheetsFor(theme);
        scene.getStylesheets().addAll(sheets);
    }

    /**
     * Convenience overload (applies to stage.getScene()).
     */
    public void apply(Stage stage) {
        if (stage == null) {
            return;
        }
        apply(stage.getScene());
    }

    /**
     * Reads preference without requiring callers to instantiate the service.
     */
    public static Theme fromPreference() {
        Preferences p = Preferences.userRoot().node(PREF_NODE);
        String raw = p.get(PREF_KEY_THEME, Theme.DARK.name());
        try {
            return Theme.valueOf(raw.trim().toUpperCase());
        } catch (Exception ignored) {
            return Theme.DARK;
        }
    }

    private void savePreference(Theme t) {
        prefs.put(PREF_KEY_THEME, t.name());
    }

    private static List<String> buildStylesheetsFor(Theme theme) {
        Theme effective = (theme == null) ? Theme.DARK : theme;

        List<String> sheets = new ArrayList<>(8);
        // Always-on baseline
        sheets.add(css(CSS_BASE));
        sheets.add(css(CSS_TABLE));
        sheets.add(css(CSS_WIN11));
        sheets.add(css(CSS_THEME));

        // Theme-specific
        sheets.add(effective.isDark() ? css(CSS_DARK) : css(CSS_LIGHT));

        return sheets;
    }

    private static String css(String resourcePath) {
        URL url = ThemeService.class.getResource(resourcePath);
        if (url == null) {
            throw new IllegalStateException("Missing CSS resource on classpath: " + resourcePath);
        }
        return url.toExternalForm();
    }

    private static void removeExplorerStylesheets(Scene scene) {
        // Remove by filename to avoid relying on exact URL forms.
        String[] names = {
                "explorer-base.css",
                "explorer-table.css",
                "explorer-win11.css",
                "explorer-theme.css",
                "explorer-light-win.css",
                "explorer-dark-win.css"
        };

        for (Iterator<String> it = scene.getStylesheets().iterator(); it.hasNext(); ) {
            String s = it.next();
            for (String n : names) {
                if (s != null && s.endsWith(n)) {
                    it.remove();
                    break;
                }
            }
        }
    }
}
