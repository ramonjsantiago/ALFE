package com.fileexplorer.ui;

import javafx.scene.input.*;
import javafx.scene.control.ListView;
import java.io.File;
import java.util.List;

public class DragAndDropHandler {
    private MainController controller;
    public void setController(MainController ctrl) { controller = ctrl; }

    public void attachDragAndDrop(javafx.scene.control.ListView<java.io.File> listView) {
        listView.setOnDragDetected(event -> {
            java.util.List<java.io.File> selectedFiles = listView.getSelectionModel().getSelectedItems();
            if (!selectedFiles.isEmpty()) {
                javafx.scene.input.Dragboard db = listView.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                java.util.List<java.io.File> files = new java.util.ArrayList<>(selectedFiles);
                content.putFiles(files);
                db.setContent(content);
            }
            event.consume();
        });

        listView.setOnDragOver(event -> {
            if (event.getGestureSource() != listView && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        listView.setOnDragDropped(event -> {
            javafx.scene.input.Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (java.io.File f : db.getFiles()) {
                    java.io.File target = new java.io.File(listView.getItems().get(0).getParentFile(), f.getName());
                    f.renameTo(target);
                    if (controller != null && controller.getHistoryManager() != null) {
                        controller.getHistoryManager().recordMove(f, target);
                    }
                }
                success = true;
                if (controller != null) controller.refreshPanes();
            }
            event.setDropCompleted(success);
            event.consume();
        });
    private HistoryManager historyManager;
    private MainController controller;
    public void setHistoryManager(HistoryManager hm) { historyManager = hm; }
    public void setController(MainController ctrl) { controller = ctrl; }

    public void enableDragAndDrop(javafx.scene.layout.Pane pane) {
        pane.setOnDragDetected(e -> {
            javafx.scene.input.Dragboard db = pane.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putFiles(controller.getSelectedFiles());
            db.setContent(content);
            e.consume();
        });

        pane.setOnDragOver(e -> {
            if (e.getGestureSource() != pane && e.getDragboard().hasFiles()) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            e.consume();
        });

        pane.setOnDragDropped(e -> {
            javafx.scene.input.Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (java.io.File file : db.getFiles()) {
                    java.io.File target = new java.io.File(controller.getCurrentFolder(), file.getName());
                    if (file.renameTo(target)) {
                        if (historyManager != null) historyManager.recordMove(file, target);
                    }
                }
                success = true;
            }
            e.setDropCompleted(success);
            e.consume();
        });
    public static void handleMultiFileDrop(javafx.scene.control.ListView<java.io.File> targetPane, java.util.List<java.io.File> files, MainController controller) {
        if (files == null || files.isEmpty() || targetPane == null) return;

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {
            @Override protected Void call() throws Exception {
                int total = files.size();
                int count = 0;
                for (java.io.File f : files) {
                    if (isCancelled()) break;
                    java.nio.file.Files.move(f.toPath(), java.nio.file.Paths.get(targetPane.getId(), f.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    updateProgress(++count, total);
                    controller.getHistoryManager().recordAction("Moved via DragDrop: " + f.getAbsolutePath() + " -> " + targetPane.getId());
                }
                javafx.application.Platform.runLater(() -> targetPane.getItems().addAll(files));
                return null;
            }
        };

        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        javafx.scene.control.Button cancelButton = new javafx.scene.control.Button("Cancel");
        cancelButton.setOnAction(e -> task.cancel());

        javafx.scene.layout.VBox dialogBox = new javafx.scene.layout.VBox(progressBar, cancelButton);
        dialogBox.setSpacing(10);
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Moving Files via Drag & Drop");
        stage.setScene(new javafx.scene.Scene(dialogBox, 300, 100));
        stage.initOwner(targetPane.getScene().getWindow());
        stage.show();

        new Thread(task).start();
        task.setOnSucceeded(e -> stage.close());
        task.setOnCancelled(e -> stage.close());
        task.setOnFailed(e -> stage.close());
    }

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
