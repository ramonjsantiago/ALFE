package com.fileexplorer.ui;

import javafx.application.HostServices;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * DetailsViewController manages the TableView that lists files with metadata columns.
 */
public class DetailsViewController {

    @FXML private TableView<TabContentController.FileMetadata> detailsTable;
    @FXML private TableColumn<TabContentController.FileMetadata, String> colName;
    @FXML private TableColumn<TabContentController.FileMetadata, String> colType;
    @FXML private TableColumn<TabContentController.FileMetadata, Long> colSize;
    @FXML private TableColumn<TabContentController.FileMetadata, String> colModified;

    private final ObservableList<TabContentController.FileMetadata> items = FXCollections.observableArrayList();
    private HostServices hostServices;

    @FXML
    public void initialize() {
        detailsTable.setItems(items);

        colName.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getName()));
        colType.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getType()));
        colSize.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getSize()));
        colModified.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getModified()));

        // Sorting
        colName.setComparator(Comparator.nullsFirst(String::compareToIgnoreCase));
        colSize.setComparator(Comparator.nullsFirst(Long::compareTo));
        colModified.setComparator(Comparator.nullsFirst(String::compareTo));

        detailsTable.setRowFactory(tv -> {
            TableRow<TabContentController.FileMetadata> row = new TableRow<>();
            row.setOnMouseClicked(evt -> {
                if (! row.isEmpty() && evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) {
                    TabContentController.FileMetadata fm = row.getItem();
                    if (fm.getFile().isDirectory()) {
                        // navigate into folder
                        try {
                            // locate parent TabContentController via scene lookup: simple approach - find controller and call loadFolder
                            // This is a lightweight action — in the TabContentController the loadFolder method exists.
                            // Unsafe reflection-free approach omitted for brevity — prefer calling from TabContentController directly.
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        // open file with host OS
                        try {
                            java.awt.Desktop.getDesktop().open(fm.getFile());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });
            return row;
        });
    }

    public void setItemsFromDirectory(Path folder) {
        items.clear();
        try {
            Files.list(folder).forEach(p -> items.add(new TabContentController.FileMetadata(p.toFile())));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clear() { items.clear(); }
}
