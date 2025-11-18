package com.fileexplorer.ui;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import java.io.File;

public class ContextMenuHandler {
    public static void attachContextMenu(javafx.scene.control.ListView<java.io.File> pane, MainController controller) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem delete = new javafx.scene.control.MenuItem("Delete");
        delete.setOnAction(e -> controller.deleteSelectedFiles());
        javafx.scene.control.MenuItem rename = new javafx.scene.control.MenuItem("Rename");
        rename.setOnAction(e -> controller.renameSelectedFile());
        javafx.scene.control.MenuItem copy = new javafx.scene.control.MenuItem("Copy");
        copy.setOnAction(e -> controller.copySelectedFiles(controller.selectTargetFolder()));
        javafx.scene.control.MenuItem move = new javafx.scene.control.MenuItem("Move");
        move.setOnAction(e -> controller.moveSelectedFiles(controller.selectTargetFolder()));
        javafx.scene.control.MenuItem properties = new javafx.scene.control.MenuItem("Properties");
        properties.setOnAction(e -> controller.showPropertiesDialog());

        menu.getItems().addAll(delete, rename, copy, move, properties);
        pane.setContextMenu(menu);
    }
    public static void addOpenWithOption(javafx.scene.control.ContextMenu menu, java.io.File file) {
        javafx.scene.control.Menu openWithMenu = new javafx.scene.control.Menu("Open With");
        // Common applications, extend as needed
        javafx.scene.control.MenuItem notepad = new javafx.scene.control.MenuItem("Notepad");
        notepad.setOnAction(e -> openWith(file, "notepad"));
        javafx.scene.control.MenuItem paint = new javafx.scene.control.MenuItem("Paint");
        paint.setOnAction(e -> openWith(file, "mspaint"));
        openWithMenu.getItems().addAll(notepad, paint);
        menu.getItems().add(openWithMenu);
    }

    private static void openWith(java.io.File file, String appCommand) {
        try {
            new ProcessBuilder(appCommand, file.getAbsolutePath()).start();
        } catch (Exception e) {
            System.err.println("Failed to open " + file.getAbsolutePath() + " with " + appCommand);
        }
    }
    private MainController mainController;

    public ContextMenuHandler(MainController controller) {
        this.mainController = controller;
    }

    public javafx.scene.control.ContextMenu createFileContextMenu(File file) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem deleteItem = new javafx.scene.control.MenuItem("Delete");
        deleteItem.setOnAction(e -> mainController.deleteFile(file));
        javafx.scene.control.MenuItem renameItem = new javafx.scene.control.MenuItem("Rename");
        renameItem.setOnAction(e -> mainController.renameFile(file));
        javafx.scene.control.MenuItem copyItem = new javafx.scene.control.MenuItem("Copy");
        copyItem.setOnAction(e -> mainController.copyFile(file));
        javafx.scene.control.MenuItem propsItem = new javafx.scene.control.MenuItem("Properties");
        propsItem.setOnAction(e -> mainController.showProperties(file));
        menu.getItems().addAll(deleteItem, renameItem, copyItem, propsItem);
        return menu;
    }

    public static ContextMenu createForFile(File f) {
        ContextMenu menu = new ContextMenu();
        MenuItem open = new MenuItem("Open");
        MenuItem delete = new MenuItem("Delete");
        MenuItem properties = new MenuItem("Properties");

        open.setOnAction(e -> System.out.println("Open " + f));
        delete.setOnAction(e -> System.out.println("Delete " + f));
        properties.setOnAction(e -> System.out.println("Show Properties " + f));

        menu.getItems().addAll(open, delete, properties);
        return menu;
    }

    public static void attach(javafx.scene.Node node, File f) {
        node.setOnMousePressed((MouseEvent e) -> {
            if (e.isSecondaryButtonDown()) {
                createForFile(f).show(node, e.getScreenX(), e.getScreenY());
            }
        });
    }
}
