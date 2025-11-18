package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;

import java.io.File;

public class PreviewPaneController {

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
