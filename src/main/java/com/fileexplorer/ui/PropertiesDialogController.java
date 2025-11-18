package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.DialogPane;
import java.io.File;

public class PropertiesDialogController {
    public void showProperties(java.util.List<java.io.File> files) {
        if (files == null || files.isEmpty()) return;
        // If multiple files, summarize size, type counts, and earliest/latest modified dates
        long totalSize = 0;
        java.util.Map<String,Integer> typeCounts = new java.util.HashMap<>();
        long earliest = Long.MAX_VALUE, latest = Long.MIN_VALUE;
        for (java.io.File f : files) {
            totalSize += f.length();
            String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf(.)+1) : "<no ext>";
            typeCounts.put(ext, typeCounts.getOrDefault(ext,0)+1);
            earliest = Math.min(earliest, f.lastModified());
            latest = Math.max(latest, f.lastModified());
        }
        // Display properties in dialog (simplified)
        System.out.println("Properties for " + files.size() + " files:");
        System.out.println("Total size: " + totalSize);
        System.out.println("Type counts: " + typeCounts);
        System.out.println("Modified range: " + new java.util.Date(earliest) + " - " + new java.util.Date(latest));
    }
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
