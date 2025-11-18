package com.fileexplorer.test;

import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

public class MainAppTest extends ApplicationTest {

    @Override
    public void start(Stage stage) throws Exception {
        // Launch MainApp
        com.fileexplorer.MainApp app = new com.fileexplorer.MainApp();
        app.start(stage);
    }

    @Test
    public void testBasicStartup() {
        // Verify main window shows
        verifyThat(".tab-pane", javafx.scene.control.TabPane::isVisible);
    }

    @Test
    public void testKeyboardShortcuts() {
        // Simulate Ctrl+Z (undo) and Ctrl+Y (redo)
        push(javafx.scene.input.KeyCode.Z, javafx.scene.input.KeyCode.CONTROL);
        push(javafx.scene.input.KeyCode.Y, javafx.scene.input.KeyCode.CONTROL);
    }
}
