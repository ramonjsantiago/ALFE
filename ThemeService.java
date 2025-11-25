package com.explorer.ui;

import javafx.scene.Scene;

public class ThemeService {

    public enum Theme {
        LIGHT,
        DARK
    }

    private Theme currentTheme = Theme.LIGHT;

    public void applyTheme(Scene scene, Theme theme) {
        currentTheme = theme;
        scene.getStylesheets().clear();

        switch (theme) {
            case DARK -> scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            case LIGHT -> scene.getStylesheets().add(getClass().getResource("/css/light-theme.css").toExternalForm());
        }
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void toggleTheme(Scene scene) {
        if (currentTheme == Theme.LIGHT) {
            applyTheme(scene, Theme.DARK);
        } else {
            applyTheme(scene, Theme.LIGHT);
        }
    }
}
