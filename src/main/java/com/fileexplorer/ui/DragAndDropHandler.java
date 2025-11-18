package com.fileexplorer.ui;

import javafx.scene.input.*;
import javafx.scene.control.ListView;
import java.io.File;
import java.util.List;

public class DragAndDropHandler {

    public static void enableDragAndDrop(ListView<File> sourcePane, ListView<File> targetPane, MainController controller) {

        // Drag detected
        sourcePane.setOnDragDetected(event -> {
            List<File> selectedFiles = sourcePane.getSelectionModel().getSelectedItems();
            if (selectedFiles.isEmpty()) return;
            Dragboard db = sourcePane.startDragAndDrop(TransferMode.COPY_OR_MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putFiles(selectedFiles);
            db.setContent(content);
            event.consume();
        });

        // Drag over target
        targetPane.setOnDragOver(event -> {
            if (event.getGestureSource() != targetPane && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        // Drag dropped
        targetPane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                File targetDir = targetPane.getItems().isEmpty() ? new File(System.getProperty("user.home")) : targetPane.getItems().get(0).getParentFile();
                boolean move = event.getTransferMode() == TransferMode.MOVE;
                for (File src : files) {
                    File dest = new File(targetDir, src.getName());
                    try {
                        if (move) {
                            src.renameTo(dest);
                            controller.getHistoryManager().recordAction("Moved: " + src.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                        } else {
                            java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            controller.getHistoryManager().recordAction("Copied: " + src.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                controller.refreshCurrentPane();
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        // Drag done
        sourcePane.setOnDragDone(DragEvent::consume);
    }
}
