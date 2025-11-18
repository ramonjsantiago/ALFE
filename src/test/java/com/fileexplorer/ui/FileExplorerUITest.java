package com.fileexplorer.ui;

import javafx.scene.control.TabPane;
import javafx.scene.control.Label;
import org.junit.jupiter.api.*;
import org.testfx.framework.junit5.ApplicationTest;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import static org.junit.jupiter.api.Assertions.*;

class FileExplorerUITest extends ApplicationTest {

    private MainController controller;

    @Override
    public void start(Stage stage) throws Exception {
        controller = new MainController();
        BorderPane root = new BorderPane();
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void testTabCreationAndRibbonUndoRedo() {
        Platform.runLater(() -> {
            controller.addTabToLeft("Test Folder");
            TabPane left = controller.leftTabPane;
            assertEquals(2, left.getTabs().size()); // initial + new
            controller.historyManager.recordAction("Test Action");
            controller.onRibbonUndo();
            controller.onRibbonRedo();
        });
    }

    @Test
    void testThemeSwitching() {
        Platform.runLater(() -> {
            controller.setTheme("Dark");
            assertEquals("Dark", controller.getCurrentTheme());
            controller.setTheme("Glassy");
            assertEquals("Glassy", controller.getCurrentTheme());
            controller.setTheme("Light");
            assertEquals("Light", controller.getCurrentTheme());
        });
    }
}
