package com.fileexplorer.ui;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import java.nio.file.Path;
import java.util.function.Consumer;

public class ContextMenuFactory {

    public static ContextMenu createFileContextMenu(Path file, Consumer<Path> onOpen, Consumer<Path> onDelete) {
        ContextMenu menu = new ContextMenu();

        MenuItem openItem = new MenuItem("Open");
        openItem.setOnAction(e -> onOpen.accept(file));

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> onDelete.accept(file));

        menu.getItems().addAll(openItem, deleteItem);
        return menu;
    }

    public static void attachContextMenu(javafx.scene.Node node, ContextMenu menu) {
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                menu.show(node, event.getScreenX(), event.getScreenY());
            } else {
                menu.hide();
            }
        });
    }
}