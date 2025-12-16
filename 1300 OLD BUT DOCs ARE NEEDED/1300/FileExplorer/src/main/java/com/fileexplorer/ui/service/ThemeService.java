package com.fileexplorer.service;

import java.util.Locale;
import java.util.Objects;
import java.util.prefs.Preferences;
import javafx.scene.Scene;

public final class ThemeService {

    public enum Theme {
        LIGHT,
        DARK
    }

    private static final String PREF_KEY = "theme";
    private static final String CSS_BASE = "/com/fileexplorer/ui/css/";

    private static final String BASE_CSS  = CSS_BASE + "explorer-base.css";
    private static final String TABLE_CSS = CSS_BASE + "explorer-table.css";
    private static final String WIN11_CSS = CSS_BASE + "explorer-win11.css";
    private static final String THEME_CSS = CSS_BASE + "explorer-theme.css";
    private static final String LIGHT_CSS = CSS_BASE + "explorer-light-win.css";
    private static final String DARK_CSS  = CSS_BASE + "explorer-dark-win.css";

    private final Preferences prefs;
    private Theme theme;

    public ThemeService() {
        this.prefs = Preferences.userNodeForPackage(ThemeService.class);
        this.theme = fromPreference();
    }

    public static Theme fromPreference() {
        Preferences p = Preferences.userNodeForPackage(ThemeService.class);
        String v = p.get(PREF_KEY, "LIGHT");
        try {
            return Theme.valueOf(v);
        } catch (Exception ex) {
            return Theme.LIGHT;
        }
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        prefs.put(PREF_KEY, this.theme.name());
    }

    public boolean isDarkPreferred() {
        return theme == Theme.DARK;
    }

    public void setDarkPreferred(boolean dark) {
        setTheme(dark ? Theme.DARK : Theme.LIGHT);
    }

    public void apply(Scene scene) {
        Objects.requireNonNull(scene, "scene");

        scene.getStylesheets().removeIf(s ->
                s != null && s.toLowerCase(Locale.ROOT).contains("/com/fileexplorer/ui/css/explorer-"));

        addIfPresent(scene, BASE_CSS);
        addIfPresent(scene, TABLE_CSS);
        addIfPresent(scene, WIN11_CSS);
        addIfPresent(scene, THEME_CSS);

        if (theme == Theme.DARK) {
            addIfPresent(scene, DARK_CSS);
        } else {
            addIfPresent(scene, LIGHT_CSS);
        }
    }

    private void addIfPresent(Scene scene, String resource) {
        var url = ThemeService.class.getResource(resource);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }
}
