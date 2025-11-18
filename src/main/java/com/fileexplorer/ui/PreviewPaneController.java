package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;

import java.io.File;

public class PreviewPaneController {
    private boolean metadataVisible = true;
    private int thumbSize = 64;

    public void toggleMetadataVisibility(boolean visible) {
        metadataVisible = visible;
        // Re-render current selection to update metadata visibility
        javafx.application.Platform.runLater(() -> showFiles(currentSelection));
    }

    public void setThumbnailSize(int size) {
        thumbSize = size;
        javafx.application.Platform.runLater(() -> showFiles(currentSelection));
    }
    @FXML private javafx.scene.layout.VBox previewBox;

    public void showFiles(java.util.List<java.io.File> files) {
        java.util.List<java.io.File> currentSelection = files;
        previewBox.getChildren().clear();
        if (files == null || files.isEmpty()) return;
        for (java.io.File f : files) {
            javafx.scene.layout.HBox fileRow = new javafx.scene.layout.HBox();
            fileRow.setSpacing(10);
            javafx.scene.image.ImageView thumbView = new javafx.scene.image.ImageView();
            javafx.scene.image.Image img = new ThumbnailGenerator().generateThumbnail(f, thumbSize, thumbSize);
            thumbView.setImage(img);
            javafx.scene.control.Label nameLabel = new javafx.scene.control.Label(f.getName());
            fileRow.getChildren().add(thumbView);
            fileRow.getChildren().add(nameLabel);
            if (metadataVisible) {
                javafx.scene.control.Label sizeLabel = new javafx.scene.control.Label(f.length()/1024 + " KB");
                javafx.scene.control.Label typeLabel = new javafx.scene.control.Label(f.isDirectory() ? "Folder" : "File");
                fileRow.getChildren().addAll(typeLabel, sizeLabel);
            }
            previewBox.getChildren().add(fileRow);
        }
        previewBox.getChildren().clear();
        if (files == null || files.isEmpty()) return;

        for (java.io.File f : files) {
            javafx.scene.layout.HBox fileRow = new javafx.scene.layout.HBox();
            fileRow.setSpacing(10);
            javafx.scene.image.ImageView thumbView = new javafx.scene.image.ImageView();
            javafx.scene.image.Image img = new ThumbnailGenerator().generateThumbnail(f, 64, 64);
            thumbView.setImage(img);
            javafx.scene.control.Label nameLabel = new javafx.scene.control.Label(f.getName());
            javafx.scene.control.Label sizeLabel = new javafx.scene.control.Label(f.length() / 1024 + " KB");
            javafx.scene.control.Label typeLabel = new javafx.scene.control.Label(f.isDirectory() ? "Folder" : "File");
            fileRow.getChildren().addAll(thumbView, nameLabel, typeLabel, sizeLabel);
            previewBox.getChildren().add(fileRow);
        }
    }

    @FXML
    private Label lblFileName;
    @FXML
    private Label lblFileSize;
    @FXML
    private Label lblFileType;
    @FXML
    private ImageView imgPreview;

    public void showFile(File file) {
        if (file == null) {
            lblFileName.setText("");
            lblFileSize.setText("");
            lblFileType.setText("");
            imgPreview.setImage(null);
            return;
        }
        lblFileName.setText(file.getName());
        lblFileSize.setText(file.length() / 1024 + " KB");
        lblFileType.setText(getExtension(file));
        if (file.isFile() && file.getName().matches(".*\\.(png|jpg|jpeg|gif|bmp|tiff)")) {
            Image img = ThumbnailGenerator.loadStaticThumbnail(file, 256, 256);
            imgPreview.setImage(img);
        } else {
            imgPreview.setImage(null);
        }
    }

    private String getExtension(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length()-1) return name.substring(dot+1).toUpperCase();
        return "";
    }
}
