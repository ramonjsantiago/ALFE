package com.fileexplorer;

import com.fileexplorer.ui.FlowTileCell;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class FlowTileCellTest {

    @BeforeAll
    static void initToolkit() {
        new JFXPanel(); // initialize JavaFX runtime
    }

    @Test
    void testCellCreation() throws Exception {
        Path dummy = Path.of("dummy.png");
        FlowTileCell.Loader loader = (p, cb) -> {
            cb.accept(new Image("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB"));
            return Executors.newSingleThreadExecutor().submit(() -> {});
        };
        FlowTileCell cell = new FlowTileCell(dummy, loader);
        assertNotNull(cell);
    }
}
