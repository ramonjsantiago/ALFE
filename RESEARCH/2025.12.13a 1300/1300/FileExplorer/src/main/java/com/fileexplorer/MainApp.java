package com.fileexplorer;

import com.fileexplorer.ui.MainController;
import com.fileexplorer.ui.Theme;
import com.fileexplorer.ui.ThemeService;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;

public final class MainApp extends Application {

    // Target sizing: scale to screen, with clamps that work well on 4K.
    private static final double SCREEN_SCALE = 0.75;

    private static final double START_W_MIN = 1800;
    private static final double START_H_MIN = 1100;

    private static final double START_W_MAX = 3000;
    private static final double START_H_MAX = 1800;

    private static final double MIN_W = 1200;
    private static final double MIN_H = 800;

    // Stage property keys (cache the initialized UI)
    private static final String KEY_INITIALIZED = "fx.initialized";
    private static final String KEY_LOADING = "fx.loading";
    private static final String KEY_CONTROLLER = "fx.controller";
    private static final String KEY_SCENE = "fx.scene";

    @Override
    public void start(Stage stage) throws Exception {
        Path home = Path.of(System.getProperty("user.home", ""));
        Theme theme = Theme.fromPreference();
        configureExplorerStage(stage, home, theme);
    }

    public static void configureExplorerStage(Stage stage) throws IOException {
        Path home = Path.of(System.getProperty("user.home", ""));
        Theme theme = Theme.fromPreference();
        configureExplorerStage(stage, home, theme);
    }

    /**
     * Primary entry point used by BreadcrumbBarController/BreadcrumbBar.
     * First call builds the UI; subsequent calls reuse it and just navigate.
     */
    public static void configureExplorerStage(Stage stage, Path initialFolder, Theme theme) throws IOException {
        if (stage == null) {
            return;
        }

        Object loading = stage.getProperties().get(KEY_LOADING);
        if (Boolean.TRUE.equals(loading)) {
            return;
        }

        stage.getProperties().put(KEY_LOADING, Boolean.TRUE);
        try {
            boolean initialized = Boolean.TRUE.equals(stage.getProperties().get(KEY_INITIALIZED));

            if (initialized) {
                Object s = stage.getProperties().get(KEY_SCENE);
                Object c = stage.getProperties().get(KEY_CONTROLLER);

                if (s instanceof Scene) {
                    Scene scene = (Scene) s;
                    applySceneFill(scene, theme);
                }

                if (c instanceof MainController) {
                    MainController controller = (MainController) c;
                    if (s instanceof Scene) {
                        controller.setScene((Scene) s);
                    }
                    if (initialFolder != null) {
                        controller.openInitialFolder(initialFolder);
                    }
                }

                return;
            }

            URL fxml = MainApp.class.getResource("/com/fileexplorer/ui/MainLayout.fxml");
            if (fxml == null) {
                throw new IllegalStateException("Missing FXML: /com/fileexplorer/ui/MainLayout.fxml");
            }

            FXMLLoader loader = new FXMLLoader(fxml);
            Parent root = loader.load(); // throws IOException

            Rectangle2D vb = Screen.getPrimary().getVisualBounds();
            double startW = clamp(vb.getWidth() * SCREEN_SCALE, START_W_MIN, START_W_MAX);
            double startH = clamp(vb.getHeight() * SCREEN_SCALE, START_H_MIN, START_H_MAX);

            Scene scene = new Scene(root, startW, startH);
            applySceneFill(scene, theme);

            MainController controller = loader.getController();
            if (controller != null) {
                controller.setScene(scene);
                if (initialFolder != null) {
                    controller.openInitialFolder(initialFolder);
                }
            }

            stage.setTitle("FileExplorer");
            stage.setScene(scene);

            stage.setResizable(true);
            stage.setMinWidth(MIN_W);
            stage.setMinHeight(MIN_H);

            // Cache for later navigation calls (avoids rebuild loops).
            stage.getProperties().put(KEY_SCENE, scene);
            stage.getProperties().put(KEY_CONTROLLER, controller);
            stage.getProperties().put(KEY_INITIALIZED, Boolean.TRUE);

            stage.show();
        } finally {
            stage.getProperties().put(KEY_LOADING, Boolean.FALSE);
        }
    }

    /**
     * Compatibility overload if some call sites still pass ThemeService.Theme.
     */
    public static void configureExplorerStage(Stage stage, Path initialFolder, ThemeService.Theme theme) throws IOException {
        Theme t = Theme.fromThemeServiceTheme(theme);
        configureExplorerStage(stage, initialFolder, t);
    }

    private static void applySceneFill(Scene scene, Theme theme) {
        if (scene == null) {
            return;
        }
        Color fill = (theme == Theme.DARK) ? Color.web("#15181d") : Color.web("#f6f7f9");
        scene.setFill(fill);
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
