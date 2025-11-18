package com.fileexplorer.ui;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.fxml.FXML;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Navigation Tree with Pin/Quick Access like MS File Explorer
 */
public class NavigationTreeController {

    @FXML
    private TreeView<File> treeView;

    private final List<File> pinnedItems = new ArrayList<>();
    private MainController mainController;

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    @FXML
    public void initialize() {
        TreeItem<File> rootItem = new TreeItem<>(new File(System.getProperty("user.home")));
        rootItem.setExpanded(true);
        populateTree(rootItem);
        treeView.setRoot(rootItem);
        treeView.setShowRoot(true);

        treeView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                showContextMenu(treeView.getSelectionModel().getSelectedItem());
            } else if (event.getClickCount() == 2) {
                TreeItem<File> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null && mainController != null) {
                    mainController.addTabToLeft(selected.getValue().getAbsolutePath());
                }
            }
        });
    }

    private void populateTree(TreeItem<File> parent) {
        File[] files = parent.getValue().listFiles(File::isDirectory);
        if (files != null) {
            for (File f : files) {
                TreeItem<File> child = new TreeItem<>(f);
                parent.getChildren().add(child);
            }
        }
    }

    private void showContextMenu(TreeItem<File> item) {
        if (item == null) return;
        ContextMenu menu = new ContextMenu();
        MenuItem open = new MenuItem("Open");
        open.setOnAction(e -> mainController.addTabToLeft(item.getValue().getAbsolutePath()));
        MenuItem pin = new MenuItem(pinnedItems.contains(item.getValue()) ? "Unpin" : "Pin");
        pin.setOnAction(e -> {
            if (pinnedItems.contains(item.getValue())) pinnedItems.remove(item.getValue());
            else pinnedItems.add(item.getValue());
            System.out.println("[NavigationTree] Pinned items: "+pinnedItems);
        });
        MenuItem rename = new MenuItem("Rename");
        rename.setOnAction(e -> System.out.println("[NavigationTree] Rename "+item.getValue()));
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> {
            System.out.println("[NavigationTree] Delete "+item.getValue());
            if (mainController != null && mainController.historyManager != null)
                mainController.historyManager.recordAction("Delete "+item.getValue().getName());
        });
        MenuItem properties = new MenuItem("Properties");
        properties.setOnAction(e -> System.out.println("[NavigationTree] Properties "+item.getValue()));

        MenuItem newFolder = new MenuItem("New Folder");
        newFolder.setOnAction(e -> System.out.println("[NavigationTree] New Folder in "+item.getValue()));

        menu.getItems().addAll(open, pin, rename, delete, properties, newFolder);
        menu.show(treeView, 200, 200); // rough positioning
    }
}
