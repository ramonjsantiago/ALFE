package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.DialogPane;
import java.io.File;

public class PropertiesDialogController {
    private java.io.File file;
    @FXML private javafx.scene.control.Label nameLabel;
    @FXML private javafx.scene.control.Label pathLabel;
    @FXML private javafx.scene.control.Label sizeLabel;
    @FXML private javafx.scene.control.Label typeLabel;
    @FXML private javafx.scene.control.Label modifiedLabel;

    public void setFile(java.io.File file) {
        this.file = file;
        nameLabel.setText(file.getName());
        pathLabel.setText(file.getAbsolutePath());
        sizeLabel.setText(file.isDirectory() ? "--" : java.text.NumberFormat.getInstance().format(file.length()) + " bytes");
        typeLabel.setText(file.isDirectory() ? "Folder" : getFileExtension(file));
        modifiedLabel.setText(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(file.lastModified())));
    }

    private String getFileExtension(java.io.File file) {
        String name = file.getName();
        int idx = name.lastIndexOf(.);
        return idx > 0 ? name.substring(idx+1).toUpperCase() : "Unknown";
    }
    private java.io.File file;
    public PropertiesDialogController(java.io.File f) { this.file = f; }

    @FXML public void initialize() {
        nameLabel.setText(file.getName());
        pathLabel.setText(file.getAbsolutePath());
        sizeLabel.setText(Long.toString(file.length()/1024) + " KB");
        modifiedLabel.setText(java.time.Instant.ofEpochMilli(file.lastModified()).toString());
        readableCheck.setSelected(file.canRead());
        writableCheck.setSelected(file.canWrite());
        executableCheck.setSelected(file.canExecute());
        typeLabel.setText(file.isDirectory() ? "Folder" : "File");
    }
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
