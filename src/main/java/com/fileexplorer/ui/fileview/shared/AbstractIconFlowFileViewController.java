package com.fileexplorer.ui.fileview.shared;

import com.fileexplorer.ui.fileview.api.FileViewComponent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;

/**
 * Shared controller base for icon-family and row-based file views backed by a ScrollPane + FlowPane.
 */
public abstract class AbstractIconFlowFileViewController implements FileViewComponent {
    @FXML
    private ScrollPane iconScroll;

    @FXML
    private FlowPane iconFlow;

    public ScrollPane getIconScroll() {
        return iconScroll;
    }

    public FlowPane getIconFlow() {
        return iconFlow;
    }

    @Override
    public Node getRoot() {
        return iconScroll;
    }

    public abstract String getViewKey();
}
