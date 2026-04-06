package com.fileexplorer.ui.fileview.details;

import com.fileexplorer.model.FileItem;
import com.fileexplorer.ui.fileview.api.FileViewComponent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;

/**
 * Modular controller for the Details file view surface.
 */
public final class DetailsViewController implements FileViewComponent {
    @FXML
    private StackPane detailsViewShell;

    @FXML
    private TableView<FileItem> fileTable;

    @FXML
    private TableColumn<FileItem, ?> colName;

    @FXML
    private TableColumn<FileItem, ?> colStatus;

    @FXML
    private TableColumn<FileItem, ?> colType;

    @FXML
    private TableColumn<FileItem, ?> colSize;

    @FXML
    private TableColumn<FileItem, ?> colModified;

    public StackPane getDetailsViewShell() {
        return detailsViewShell;
    }

    public TableView<FileItem> getFileTable() {
        return fileTable;
    }

    public TableColumn<FileItem, ?> getColName() {
        return colName;
    }

    public TableColumn<FileItem, ?> getColStatus() {
        return colStatus;
    }

    public TableColumn<FileItem, ?> getColType() {
        return colType;
    }

    public TableColumn<FileItem, ?> getColSize() {
        return colSize;
    }

    public TableColumn<FileItem, ?> getColModified() {
        return colModified;
    }

    @Override
    public Node getRoot() {
        return detailsViewShell;
    }
}
