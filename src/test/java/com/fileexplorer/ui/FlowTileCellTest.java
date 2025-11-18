package com.fileexplorer.ui;

import org.junit.jupiter.api.Test;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class FlowTileCellTest {

    @Test
    void testUpdateThumbnail() {
        new JFXPanel(); // initializes JavaFX
        Platform.runLater(() -> {
            FlowTileCell cell = new FlowTileCell();
            File f = new File(System.getProperty("user.home"), "test.jpg");
            cell.updateThumbnail(f);
            assertNotNull(cell.getGraphic());
        });
    }
}
