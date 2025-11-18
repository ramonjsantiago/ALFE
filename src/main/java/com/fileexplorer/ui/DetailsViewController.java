package com.fileexplorer.ui;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;

public class DetailsViewController {

    @FXML
    private TableView<File> tableView;

    @FXML
    private TableColumn<File, String> nameCol;
    @FXML
    private TableColumn<File, Long> sizeCol;
    @FXML
    private TableColumn<File, String> typeCol;
    @FXML
    private TableColumn<File, String> dateCol;

    private ObservableList<File> fileList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Name column
        nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        nameCol.setSortable(true);

        // Size column
        sizeCol.setCellValueFactory(cell -> new SimpleLongProperty(cell.getValue().length()).asObject());
        sizeCol.setSortable(true);

        // Type column (extension)
        typeCol.setCellValueFactory(cell -> {
            String name = cell.getValue().getName();
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length()-1) ext = name.substring(dot+1);
            return new SimpleStringProperty(ext.toUpperCase());
        });
        typeCol.setSortable(true);

        // Date Modified column
        dateCol.setCellValueFactory(cell -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            return new SimpleStringProperty(sdf.format(cell.getValue().lastModified()));
        });
        dateCol.setSortable(true);

        tableView.setItems(fileList);
    }

    public void setFiles(File[] files) {
        fileList.setAll(Arrays.asList(files));
    }

    // Optional: programmatically sort
    public void sortByColumn(String columnName) {
        switch(columnName) {
            case "Name":
                tableView.getSortOrder().setAll(nameCol); break;
            case "Size":
                tableView.getSortOrder().setAll(sizeCol); break;
            case "Type":
                tableView.getSortOrder().setAll(typeCol); break;
            case "Date":
                tableView.getSortOrder().setAll(dateCol); break;
        }
    }
}
