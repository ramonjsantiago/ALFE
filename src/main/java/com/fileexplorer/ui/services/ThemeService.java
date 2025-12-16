package com.fileexplorer.service;

import java.util.Objects;
import java.util.prefs.Preferences;
import javafx.css.PseudoClass;
import javafx.scene.Scene;

public final class ThemeService {

    private static final String PREF_NODE = "com.fileexplorer";
    private static final String KEY_DARK = "theme.dark";

    // Required layers
    private static final String CSS_BASE = "/com/fileexplorer/ui/css/explorer-base.css";
    private static final String CSS_TABLE = "/com/fileexplorer/ui/css/explorer-table.css";
    private static final String CSS_DARK = "/com/fileexplorer/ui/css/explorer-dark-win.css";
    private static final String CSS_LIGHT = "/com/fileexplorer/ui/css/explorer-light-win.css";

    // Optional layers (loaded if present; do not break startup if absent)
    private static final String CSS_WIN11 = "/com/fileexplorer/ui/css/explorer-win11.css";
    private static final String CSS_FLUENT = "/com/fileexplorer/ui/css/explorer-fluent.css";
    private static final String CSS_WIN = "/com/fileexplorer/ui/css/explorer-win.css";

    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    private final Preferences prefs;

    public ThemeService() {
        this.prefs = Preferences.userRoot().node(PREF_NODE);
    }

    public boolean isDarkPreferred() {
        return prefs.getBoolean(KEY_DARK, false);
    }

    public void setDarkPreferred(boolean dark) {
        prefs.putBoolean(KEY_DARK, dark);
    }

    public void apply(Scene scene) {
        Objects.requireNonNull(scene, "scene");

        String base = toExternalFormRequired(CSS_BASE);
        String table = toExternalFormRequired(CSS_TABLE);
        String dark = toExternalFormRequired(CSS_DARK);
        String light = toExternalFormRequired(CSS_LIGHT);

        String win11 = toExternalFormOptional(CSS_WIN11);
        String fluent = toExternalFormOptional(CSS_FLUENT);
        String win = toExternalFormOptional(CSS_WIN);

        // Remove all managed layers first (prevents duplicates and makes toggling deterministic)
        scene.getStylesheets().removeIf(s ->
            s.equals(base)
                || s.equals(table)
                || s.equals(dark)
                || s.equals(light)
                || (win11 != null && s.equals(win11))
                || (fluent != null && s.equals(fluent))
                || (win != null && s.equals(win))
        );

        // Add shared layers first (lowest precedence)
        scene.getStylesheets().add(base);
        scene.getStylesheets().add(table);
        if (win11 != null) {
            scene.getStylesheets().add(win11);
        }
        if (fluent != null) {
            scene.getStylesheets().add(fluent);
        }
        if (win != null) {
            scene.getStylesheets().add(win);
        }

        // Add theme layer last (highest precedence)
        scene.getStylesheets().add(isDarkPreferred() ? dark : light);

        scene.getRoot().pseudoClassStateChanged(DARK, isDarkPreferred());
    }

    private static String toExternalFormRequired(String resourcePath) {
        var url = ThemeService.class.getResource(resourcePath);
        if (url == null) {
            throw new IllegalStateException("Missing required stylesheet resource: " + resourcePath);
        }
        return url.toExternalForm();
    }

    private static String toExternalFormOptional(String resourcePath) {
        var url = ThemeService.class.getResource(resourcePath);
        if (url == null) {
            return null;
        }
        return url.toExternalForm();
    }
}
