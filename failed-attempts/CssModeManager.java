package com.fileexplorer.ui;

import javafx.scene.Scene;

public class CssModeManager {

    public enum Mode { LIGHT, DARK, GLASSY }

    private final Scene scene;

    public CssModeManager(Scene scene) {
        this.scene = scene;
    }

    public void apply(Mode mode) {
        scene.getStylesheets().clear();

        switch (mode) {
            case LIGHT -> scene.getStylesheets().addAll(
                    "/com/fil.fileexploreer.css.fileexploreer.enhanced.css",
                    "/com/fil.fileexploreer.css.fileexploreer.icons.css"
            );
            case DARK -> scene.getStylesheets().addAll(
                    "/com/fil.fileexploreer.css.fileexploreer.dark.css",
                    "/com/fil.fileexploreer.css.fileexploreer.icons.css"
            );
            case GLASSY -> scene.getStylesheets().addAll(
                    "/com/fil.fileexploreer.css.fileexploreer.enhanced.css",
                    "/com/fil.fileexploreer.css.fileexploreer.icons.css",
                    "/com/fil.fileexploreer.css.fileexploreer.skins.css"
            );
        }
    }
}