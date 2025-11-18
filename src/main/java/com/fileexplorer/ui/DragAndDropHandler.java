package com.fileexplorer.ui;

import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.*;
import javafx.scene.Node;
import javafx.scene.control.Tab;

import java.io.File;
import java.util.List;

/**
 * Handles drag-and-drop for dual panes and updates HistoryManager.
 */
public class DragAndDropHandler {

    private final TabPane leftPane;
    private final TabPane rightPane;
    private final VBox previewPane;
    private final HistoryManager historyManager;

    public DragAndDropHandler(TabPane left, TabPane right, VBox preview, HistoryManager history) {
        this.leftPane = left;
        this.rightPane = right;
        this.previewPane = preview;
        this.historyManager = history;
    }

    public void enableDragAndDrop() {
        enableForPane(leftPane, true);
        enableForPane(rightPane, false);
    }

    private void enableForPane(TabPane pane, boolean isLeft) {
        pane.setOnDragOver(event -> {
            if (event.getGestureSource() != pane && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        pane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                // Demo: print dropped files
                for (File f : files) {
                    System.out.println("[DragDrop] Dropped file: " + f.getAbsolutePath() + " on " + (isLeft?"Left":"Right")+" pane");
                    if (historyManager != null) historyManager.recordAction("Dropped: "+f.getName());
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
            // update preview after drop
            updatePreview();
        });
    }

    private void updatePreview() {
        previewPane.getChildren().clear();
        Tab leftTab = leftPane.getSelectionModel().getSelectedItem();
        Tab rightTab = rightPane.getSelectionModel().getSelectedItem();
        javafx.scene.control.Label lbl = new javafx.scene.control.Label(
            "Preview (DragDrop) - Left: "+(leftTab!=null?leftTab.getText():"None")+" | Right: "+(rightTab!=null?rightTab.getText():"None")
        );
        previewPane.getChildren().add(lbl);
    }
}
