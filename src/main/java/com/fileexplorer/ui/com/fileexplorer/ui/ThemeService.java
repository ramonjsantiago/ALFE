package com.fileexplorer.ui;

import javafx.scene.Scene;

/**
 * ThemeService
 *  - Manages dark/light mode and custom themes
 *  - Applies stylesheets to JavaFX scenes
 */
public class ThemeService {

    public enum Theme {LIGHT, DARK}

    private Theme current = Theme.LIGHT;

    public void apply(Scene scene, Theme theme) {
        scene.getStylesheets().clear();
        switch (theme) {
            case LIGHT -> scene.getStylesheets().add(getClass().getResource("/css/light.css").toExternalForm());
            case DARK -> scene.getStylesheets().add(getClass().getResource("/css/dark.css").toExternalForm());
        }
        current = theme;
    }

    public Theme getCurrentTheme() {
        return current;
    }
}
