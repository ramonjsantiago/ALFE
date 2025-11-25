// package com.fileexplorer.ui;

// import javafx.collections.FXCollections;
// import javafx.collections.ObservableList;
// import javafx.fxml.FXML;
// import javafx.scene.control.*;
// import javafx.scene.input.MouseEvent;
// import javafx.stage.Window;
// import java.io.*;
// import java.nio.file.*;
// import java.util.*;
// import java.util.prefs.Preferences;

// /**
 // * DetailsViewController — TableView with sortable columns and column chooser
 // */
// public class DetailsViewController {

    // @FXML public TableView<java.io.File> detailsTable;
    // @FXML private MenuButton columnChooserButton;

    // private final ObservableList<java.io.File> items = FXCollections.observableArrayList();
    // private final List<TableColumn<java.io.File, ?>> allColumns = new ArrayList<>();
    // private final Preferences prefs = Preferences.userNodeForPackage(DetailsViewController.class);

    // @FXML
    // public void initialize() {
        //Create columns
        // TableColumn<java.io.File, String> nameCol = new TableColumn<>("Name");
        // nameCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getName()));
        // nameCol.setSortable(true);

        // TableColumn<java.io.File, String> sizeCol = new TableColumn<>("Size");
        // sizeCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().isFile() ? Long.toString(cd.getValue().length()) : ""));
        // sizeCol.setSortable(true);

        // TableColumn<java.io.File, String> typeCol = new TableColumn<>("Type");
        // typeCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().isDirectory()? "Folder" : "File"));
        // typeCol.setSortable(false);

        // TableColumn<java.io.File, String> modifiedCol = new TableColumn<>("Date Modified");
        // modifiedCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
            // new java.util.Date(cd.getValue().lastModified()).toString()));
        // modifiedCol.setSortable(true);

        // detailsTable.getColumns().addAll(nameCol, sizeCol, typeCol, modifiedCol);
        // allColumns.addAll(detailsTable.getColumns());

        // detailsTable.setItems(items);
        // detailsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        //Column chooser
        // rebuildColumnChooser();

        //Load persisted column visibility/order
        // restoreColumnPrefs();

        //Double click to open
        // detailsTable.setOnMouseClicked((MouseEvent t) -> {
            // if (t.getClickCount() == 2) {
                // java.io.File selected = detailsTable.getSelectionModel().getSelectedItem();
                // if (selected != null) openFile(selected);
            // }
        // });
    // }

    // private void rebuildColumnChooser() {
        // columnChooserButton.getItems().clear();
        // for (TableColumn<java.io.File, ?> col : allColumns) {
            // CheckMenuItem mi = new CheckMenuItem(col.getText());
            // mi.setSelected(detailsTable.getColumns().contains(col));
            // mi.setOnAction(e -> {
                // if (mi.isSelected()) {
                    // detailsTable.getColumns().add(col);
                // } else {
                    // detailsTable.getColumns().remove(col);
                // }
                // persistColumnPrefs();
            // });
            // columnChooserButton.getItems().add(mi);
        // }
    // }

    // public void setFolder(Path folder) {
        // items.clear();
        // try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder)) {
            // for (Path p : ds) items.add(p.toFile());
        // } catch (IOException e) {
            // e.printStackTrace();
        // }
    // }

    // private void openFile(java.io.File f) {
        // try {
            // java.awt.Desktop.getDesktop().open(f);
        // } catch (Exception ex) {
            // ex.printStackTrace();
        // }
    // }

    // private void persistColumnPrefs() {
        // try {
            // StringBuilder sb = new StringBuilder();
            // for (TableColumn<java.io.File, ?> col : detailsTable.getColumns()) {
                // sb.append(col.getText()).append(";");
            // }
            // prefs.put("details.columns.order", sb.toString());
            // StringBuilder vis = new StringBuilder();
            // for (TableColumn<java.io.File, ?> col : allColumns) {
                // vis.append(detailsTable.getColumns().contains(col) ? "1" : "0");
            // }
            // prefs.put("details.columns.visible", vis.toString());
        // } catch (Exception e) {
            // e.printStackTrace();
        // }
    // }

    // private void restoreColumnPrefs() {
        // String order = prefs.get("details.columns.order", "");
        // String vis = prefs.get("details.columns.visible", "");
        // if (!order.isEmpty()) {
            // Map<String, TableColumn<java.io.File, ?>> map = new HashMap<>();
            // for (TableColumn<java.io.File, ?> c : allColumns) map.put(c.getText(), c);
            // detailsTable.getColumns().clear();
            // for (String name : order.split(";")) {
                // if (map.containsKey(name)) detailsTable.getColumns().add(map.get(name));
            // }
        // }
        // if (!vis.isEmpty()) {
            // for (int i = 0; i < vis.length() && i < allColumns.size(); i++) {
                // boolean show = vis.charAt(i) == '1';
                // TableColumn<java.io.File, ?> c = allColumns.get(i);
                // if (show && !detailsTable.getColumns().contains(c)) detailsTable.getColumns().add(c);
                // if (!show) detailsTable.getColumns().remove(c);
            // }
        // }
        //Ensure chooser reflects final state
        // rebuildColumnChooser();
    // }
// }


package com.fileexplorer.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class DetailsViewController {

    @FXML private ListView<Path> detailsListView;

    private ObservableList<Path> items = FXCollections.observableArrayList();
    private Path currentFolder;

    @FXML
    private void initialize() {
        detailsListView.setItems(items);
    }

    public void setFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) return;
        currentFolder = folder;
        refresh();
    }

    public void refresh() {
        try {
            List<Path> children = Files.list(currentFolder)
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .collect(Collectors.toList());
            items.setAll(children);
        } catch (Exception e) {
            items.clear();
        }
    }

    public Path getSelectedItem() {
        return detailsListView.getSelectionModel().getSelectedItem();
    }

    public List<Path> getSelectedItems() {
        return detailsListView.getSelectionModel().getSelectedItems();
    }

    public ListView<Path> getListView() {
        return detailsListView;
    }
}