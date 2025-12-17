package com.fileexplorer.ui.service;

import java.net.URL;
import java.util.Objects;
import java.util.prefs.Preferences;

import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * Central theme coordinator for the UI.
 *
 * Expected CSS resources on classpath:
 *   /com/fileexplorer/ui/css/explorer-base.css
 *   /com/fileexplorer/ui/css/explorer-win11.css
 *   /com/fileexplorer/ui/css/explorer-light-win.css
 *   /com/fileexplorer/ui/css/explorer-dark-win.css
 */
public final class ThemeService {

    public enum Theme {
        LIGHT,
        DARK;

        public Theme toggle() {
            return this == DARK ? LIGHT : DARK;
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

    private static final String PREF_KEY_THEME = "fileexplorer.theme";
    private static final Theme DEFAULT_THEME = Theme.LIGHT;

    private final Preferences prefs;
    private Theme theme;

    public ThemeService() {
        this(Preferences.userNodeForPackage(ThemeService.class));
    }

    public ThemeService(Preferences prefs) {
        this.prefs = Objects.requireNonNull(prefs, "prefs");
        this.theme = Theme.parseOrDefault(prefs.get(PREF_KEY_THEME, DEFAULT_THEME.name()), DEFAULT_THEME);
    }

    public Theme getTheme() {
        return theme;
    }

    public boolean isDark() {
        return theme == Theme.DARK;
    }

    public void setTheme(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        prefs.put(PREF_KEY_THEME, this.theme.name());
    }

    public Theme toggleTheme() {
        setTheme(getTheme().toggle());
        return getTheme();
    }

    /**
     * Apply the currently selected theme to the provided scene:
     * - clears stylesheets
     * - re-adds base + win11 + light/dark
     * - updates root style classes
     */
    public void apply(Scene scene) {
        if (scene == null) {
            return;
        }

        scene.getStylesheets().clear();

        // Base + target OS styling first:
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-base.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-win11.css");

        // Theme-specific:
        if (isDark()) {
            addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-dark-win.css");
        } else {
            addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-light-win.css");
        }

        // Normalize a single root class for theme-dependent selectors.
        Parent root = scene.getRoot();
        if (root != null) {
            root.getStyleClass().removeAll("theme-light", "theme-dark");
            root.getStyleClass().add(isDark() ? "theme-dark" : "theme-light");
        }
    }

    /**
     * Convenience: set + apply.
     */
    public void apply(Scene scene, Theme theme) {
        setTheme(theme);
        apply(scene);
    }

    private static void addStylesheet(Scene scene, String classpathResource) {
        URL url = ThemeService.class.getResource(classpathResource);
        if (url == null) {
            // Fail-soft: app still runs; you will see missing styling.
            System.err.println("[ThemeService] Missing stylesheet: " + classpathResource);
            return;
        }
        scene.getStylesheets().add(url.toExternalForm());
    }
}
