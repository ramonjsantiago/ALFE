package com.fileexplorer;

import com.fileexplorer.ui.MainController;
import com.fileexplorer.ui.service.ThemeService;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static final String MAIN_FXML = "/com/fileexplorer/ui/MainLayout.fxml";
    private static final String APP_TITLE = "FileExplorer";

    private static final double SCREEN_SCALE = 0.86;

    private static final double START_W_MIN = 1024;
    private static final double START_W_MAX = 1700;

    private static final double START_H_MIN = 720;
    private static final double START_H_MAX = 1200;

    @Override
    public void start(Stage stage) throws IOException {
        configureExplorerStage(stage, null, null);
    }

    public static void configureExplorerStage(Stage stage, Path initialFolder) throws IOException {
        configureExplorerStage(stage, initialFolder, null);
    }

    /**
     * Opens an Explorer window.
     *
     * @param stage         destination stage (required)
     * @param initialFolder folder to open initially (nullable)
     * @param darkOverride  if non-null, applies this theme to the new window without changing persisted preference
     */
    public static void configureExplorerStage(Stage stage, Path initialFolder, Boolean darkOverride) throws IOException {
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }

        URL fxml = MainApp.class.getResource(MAIN_FXML);
        if (fxml == null) {
            throw new IllegalStateException("Cannot find FXML on classpath: " + MAIN_FXML);
        }

        FXMLLoader loader = new FXMLLoader(fxml);
        Parent root = loader.load();
        MainController controller = loader.getController();

        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double startW = clamp(vb.getWidth() * SCREEN_SCALE, START_W_MIN, START_W_MAX);
        double startH = clamp(vb.getHeight() * SCREEN_SCALE, START_H_MIN, START_H_MAX);

        Scene scene = new Scene(root, startW, startH);

        // Single source of truth for theming.
        ThemeService themeService = new ThemeService();
        if (darkOverride != null) {
            themeService.apply(scene, darkOverride.booleanValue());
        } else {
            themeService.apply(scene);
        }

        if (controller != null) {
            controller.setScene(scene);
            if (initialFolder != null) {
                controller.openInitialFolder(initialFolder);
            }
        }

        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
