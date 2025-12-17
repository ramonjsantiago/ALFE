package com.fileexplorer;

import com.fileexplorer.ui.MainController;
import com.fileexplorer.ui.service.ThemeService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

public final class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        configureExplorerStage(stage);
        stage.show();
    }

    public void configureExplorerStage(Stage stage) throws IOException {
        ThemeService themeService = new ThemeService();
        configureExplorerStage(stage, null, themeService);
    }

    public void configureExplorerStage(Stage stage, Path initialFolder, ThemeService themeService) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/fileexplorer/ui/MainLayout.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);

        themeService.apply(scene);
        themeService.installCtrlZoom(scene);

        MainController controller = loader.getController();
        controller.postConstruct(stage, themeService, initialFolder);

        stage.setTitle("ALFE");
        stage.setMinWidth(256);
        stage.setMinHeight(256);

        // Reasonable default for 4K; you can tune.
        stage.setWidth(1500);
        stage.setHeight(950);

        stage.setScene(scene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
