package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.DialogPane;
import java.io.File;

public class PropertiesDialogController {
    private java.io.File file;
    public void setFile(java.io.File f) {
        this.file = f;
        fileNameLabel.setText(f.getName());
        filePathLabel.setText(f.getAbsolutePath());
        fileSizeLabel.setText(f.length() + " bytes");
        lastModifiedLabel.setText(new java.util.Date(f.lastModified()).toString());
        isDirectoryLabel.setText(Boolean.toString(f.isDirectory()));
        historyManager.recordAction("PropertiesDialog loaded for: " + f.getAbsolutePath());
    }
    @FXML private DialogPane root;
    @FXML private Label fileNameLabel, fileSizeLabel, filePathLabel;

    public void setFile(File f) {
        fileNameLabel.setText(f.getName());
        filePathLabel.setText(f.getAbsolutePath());
        fileSizeLabel.setText(f.length() + " bytes");
    }

    public void show() {
        root.getScene().getWindow().show();
    }
}
