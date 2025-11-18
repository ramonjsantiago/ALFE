package com.fileexplorer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    // --- ThemeManager integration (added by chunk136) ---
    private void initThemeManager(javafx.stage.Stage stage) {
        try {
            javafx.scene.Scene scene = stage.getScene();
            if (scene != null) com.fileexplorer.ui.ThemeManager.get().registerScene(scene);
        } catch (Exception e) { e.printStackTrace(); }
    }
    private Scene scene;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fileexplorer/ui/MainLayout.fxml"));
        scene = new Scene(loader.load(), 1200, 800);

        // Default theme: light
        scene.getStylesheets().add(getClass().getResource("/com/fileexplorer/ui/css/light.css").toExternalForm());

        stage.setTitle("Ultra File Explorer");
        stage.setScene(scene);
        stage.show();
        // ThemeManager: register primary scene
        try { initThemeManager(stage); } catch(Exception e) { e.printStackTrace(); }
    }

    public void setTheme(String theme) {
        scene.getStylesheets().clear();
        switch (theme.toLowerCase()) {
            case "dark":
                scene.getStylesheets().add(getClass().getResource("/com/fileexplorer/ui/css/dark.css").toExternalForm());
                break;
            case "glassy":
                scene.getStylesheets().add(getClass().getResource("/com/fileexplorer/ui/css/glassy.css").toExternalForm());
                break;
            default:
                scene.getStylesheets().add(getClass().getResource("/com/fileexplorer/ui/css/light.css").toExternalForm());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
