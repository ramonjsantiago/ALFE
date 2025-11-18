package com.explorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;

public class PropertiesSidebarController {

    @FXML private Label fileName;
    @FXML private Label fileType;
    @FXML private Label fileSize;
    @FXML private Label fileModified;
    @FXML private ImageView fileIcon;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public void showDetails(Path path, Image icon) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

            fileName.setText(path.getFileName().toString());
            fileType.setText(attrs.isDirectory() ? "Folder" : "File");
            fileSize.setText(attrs.isDirectory() ? "-" : formatSize(attrs.size()));
            fileModified.setText(fmt.format(attrs.lastModifiedTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()));

            if (icon != null) {
                fileIcon.setImage(icon);
            }

        } catch (Exception e) {
            fileName.setText("Error");
            fileType.setText("");
            fileSize.setText("");
            fileModified.setText("");
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int unit = 1024;
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}