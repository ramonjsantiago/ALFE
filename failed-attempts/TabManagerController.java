package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.Node;

/**
 * TabManagerController — manages TabPane: chrome-style tabs, new tab button, drag reorder.
 */
public class TabManagerController {

    @FXML private TabPane tabPane;
    @FXML private Button btnNewTab;

    @FXML
    public void initialize() {
        btnNewTab.setOnAction(e -> createNewTab(null));
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);

        // Allow drag reordering (built-in in JavaFX 8+)
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);

        // Seed with a default tab
        if (tabPane.getTabs().isEmpty()) createNewTab(System.getProperty("user.home"));
    }

    public void createNewTab(String initialFolder) {
        String title = initialFolder != null ? initialFolder : "New Tab";
        Tab t = new Tab(title);
        // Simple content placeholder (in real app you'd inject a file pane)
        ListView<java.io.File> list = new ListView<>();
        t.setContent(list);
        t.setClosable(true);
        tabPane.getTabs().add(t);
        tabPane.getSelectionModel().select(t);

        // Add a custom graphic (close icon) if desired — left as simple default
    }
}
