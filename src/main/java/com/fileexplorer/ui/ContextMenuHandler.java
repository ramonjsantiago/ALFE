package com.fileexplorer.ui;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import java.io.File;

public class ContextMenuHandler {

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
