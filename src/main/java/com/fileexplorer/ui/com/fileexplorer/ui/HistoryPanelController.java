package com.fileexplorer.ui;

import com.fileexplorer.thumb.HistoryManager;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.collections.FXCollections;

public class HistoryPanelController {

    @FXML private BorderPane root;
    @FXML private ListView<String> actionListView;

    private HistoryManager historyManager;

    public void setHistoryManager(HistoryManager hm) {
        this.historyManager = hm;
    }

    @FXML
    public void initialize() {
        actionListView.setItems(FXCollections.observableArrayList());
    }

    public void refresh() {
        if (historyManager != null) {
            actionListView.getItems().clear();
            historyManager.getActions().forEach(a -> {
                String entry = (a.dest != null ? "Renamed: " : "Deleted: ") + a.src;
                actionListView.getItems().add(entry);
            });
        }
    }
}
