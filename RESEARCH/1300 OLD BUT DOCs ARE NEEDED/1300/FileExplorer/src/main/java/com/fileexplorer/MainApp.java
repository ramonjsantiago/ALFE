package com.fileexplorer;

import com.fileexplorer.service.ThemeService;
import com.fileexplorer.ui.MainController;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class MainApp extends javafx.application.Application {

    private static final double MIN_W = 256.0;
    private static final double MIN_H = 256.0;

    @Override
    public void start(Stage stage) throws Exception {
        configureExplorerStage(stage);
    }

    public static void configureExplorerStage(Stage stage) throws IOException {
        Path home = Path.of(System.getProperty("user.home"));
        configureExplorerStage(stage, home, new ThemeService().getTheme());
    }

    public static void configureExplorerStage(Stage stage, Path initialFolder, ThemeService.Theme theme) throws IOException {
        Objects.requireNonNull(stage, "stage");

        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/fileexplorer/ui/MainLayout.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);

        stage.setTitle("FileExplorer");
        stage.setScene(scene);

        stage.setMinWidth(MIN_W);
        stage.setMinHeight(MIN_H);

        stage.setWidth(1400);
        stage.setHeight(900);
        stage.setResizable(true);

        MainController controller = loader.getController();
        if (controller != null) {
            controller.setStage(stage);
            controller.setScene(scene);

            // Ensure theme is applied before showing
            ThemeService ts = new ThemeService();
            ts.setTheme(theme);
            ts.apply(scene);

            controller.openInitialFolder(initialFolder);
        }

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
