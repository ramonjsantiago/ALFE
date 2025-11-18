package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.DialogPane;
import java.io.File;

public class PropertiesDialogController {
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
