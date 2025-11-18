package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import java.io.File;

public class StatusBarController {

    @FXML private Label selectionCountLabel;
    @FXML private Label totalSizeLabel;
    @FXML private Label currentFolderLabel;

    public void bindSelection(ObservableList<File> selectedFiles, File currentFolder) {
        updateStatus(selectedFiles, currentFolder);

        selectedFiles.addListener((ListChangeListener.Change<? extends File> c) -> {
            updateStatus(selectedFiles, currentFolder);
        });
    }

    public void setCurrentFolder(File folder) {
        currentFolderLabel.setText(folder.getAbsolutePath());
    }

    private void updateStatus(ObservableList<File> selectedFiles, File currentFolder) {
        selectionCountLabel.setText("Selected: " + selectedFiles.size());
        long totalSize = selectedFiles.stream().filter(f -> f.isFile()).mapToLong(File::length).sum();
        totalSizeLabel.setText("Total Size: " + totalSize / 1024 + " KB");
        currentFolderLabel.setText(currentFolder != null ? currentFolder.getAbsolutePath() : "");
    }
}
