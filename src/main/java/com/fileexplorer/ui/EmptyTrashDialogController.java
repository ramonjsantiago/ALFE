package com.explorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class EmptyTrashDialogController {

    @FXML private Label messageLabel;
    @FXML private Button confirmBtn;
    @FXML private Button cancelBtn;

    private Runnable onConfirm;

    public void setOnConfirm(Runnable r) {
        this.onConfirm = r;
    }

    @FXML
    private void initialize() {
        messageLabel.setText("Are you sure you want to permanently delete all items in the Trash?");
    }

    @FXML
    private void onConfirmClick() {
        if (onConfirm != null) onConfirm.run();
        close();
    }

    @FXML
    private void onCancelClick() {
        close();
    }

    private void close() {
        Stage stage = (Stage) messageLabel.getScene().getWindow();
        stage.close();
    }
}