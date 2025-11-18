package com.fileexplorer.ui;

import com.fileexplorer.thumb.HistoryManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.nio.file.Path;

public class MainController {

    @FXML private BorderPane historyPanel;
    @FXML private ToolBar ribbonBar;
    @FXML private HBox statusBar;
    @FXML private Label statusLabel;
    @FXML private ChoiceBox<String> themeSelector;

    private HistoryPanelController historyPanelController;
    private final HistoryManager historyManager = new HistoryManager();

    @FXML
    public void initialize() {
        // Recommended fx:include controller retrieval
        Object controller = historyPanel.getProperties().get("fx:controller");
        if (controller instanceof HistoryPanelController hp) {
            historyPanelController = hp;
            historyPanelController.setHistoryManager(historyManager);
            historyPanelController.refresh();
        }

        themeSelector.getSelectionModel().select("Light");
    }

    @FXML
    private void onNewFolder() {
        statusLabel.setText("New Folder clicked");
        // Stub: implement folder creation logic
    }

    @FXML
    private void onRefresh() {
        statusLabel.setText("Refresh clicked");
        if (historyPanelController != null) historyPanelController.refresh();
    }

    @FXML
    private void onDelete() {
        statusLabel.setText("Delete clicked");
        // Stub: integrate with FlowTileCell selection + HistoryManager
    }

    @FXML
    private void onUndo() {
        try {
            if (historyManager.canUndo()) {
                historyManager.undo();
                statusLabel.setText("Undo performed");
                if (historyPanelController != null) historyPanelController.refresh();
            } else {
                statusLabel.setText("Nothing to undo");
            }
        } catch (Exception e) {
            statusLabel.setText("Undo failed: " + e.getMessage());
        }
    }

    @FXML
    private void onThemeChange() {
        String theme = themeSelector.getValue();
        statusLabel.setText("Theme switched to " + theme);
        MainApp app = (MainApp) statusBar.getScene().getWindow().getScene().getWindow().getUserData();
        if (app != null) app.setTheme(theme);
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    // Method for FlowTileCell or HistoryPanel to update status
    public void updateStatus(String msg) {
        statusLabel.setText(msg);
    }
}
