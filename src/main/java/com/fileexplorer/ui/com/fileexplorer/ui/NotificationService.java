package com.fileexplorer.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 * NotificationService
 *  - Displays popups, alerts, or toast messages
 */
public class NotificationService {

    public void info(String message) {
        show(AlertType.INFORMATION, "Info", message);
    }

    public void warning(String message) {
        show(AlertType.WARNING, "Warning", message);
    }

    public void error(String message) {
        show(AlertType.ERROR, "Error", message);
    }

    private void show(AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        });
    }
}