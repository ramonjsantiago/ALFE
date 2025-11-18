package com.fileexplorer.ui;

import com.fileexplorer.thumb.HistoryManager;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;

public class MainController {

    @FXML private BorderPane historyPanel; // fx:include root

    private HistoryPanelController historyPanelController;
    private final HistoryManager historyManager = new HistoryManager();

    @FXML
    public void initialize() {
        // Recommended retrieval of fx:include controller
        Object controller = historyPanel.getProperties().get("fx:controller");
        if (controller instanceof HistoryPanelController hp) {
            historyPanelController = hp;
            historyPanelController.setHistoryManager(historyManager);
            historyPanelController.refresh();
        } else {
            System.err.println("HistoryPanelController not found in fx:include");
        }
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }
}
