package com.fileexplorer.ui.fileview.host;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Lazy loader / activator for modular file-view surfaces hosted inside the main view stack.
 */
public final class FileViewHost {
    private final StackPane host;
    private final Map<String, Parent> loadedViews = new LinkedHashMap<>();
    private final Map<String, Object> loadedControllers = new LinkedHashMap<>();

    public FileViewHost(StackPane host) {
        this.host = host;
    }

    public Object ensureLoaded(String key, String fxmlResource) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (fxmlResource == null || fxmlResource.isBlank()) {
            throw new IllegalArgumentException("fxmlResource must not be blank");
        }
        Object controller = loadedControllers.get(key);
        if (controller != null) {
            return controller;
        }
        URL resource = FileViewHost.class.getResource(fxmlResource);
        if (resource == null) {
            throw new IOException("Missing FXML resource: " + fxmlResource);
        }
        FXMLLoader loader = new FXMLLoader(resource);
        Parent root = loader.load();
        root.setVisible(false);
        root.setManaged(false);
        configureRootForHostFill(root);
        host.getChildren().add(0, root);
        loadedViews.put(key, root);
        controller = loader.getController();
        loadedControllers.put(key, controller);
        return controller;
    }

    private void configureRootForHostFill(Parent root) {
        if (root == null) {
            return;
        }
        StackPane.setAlignment(root, Pos.TOP_LEFT);
        if (root instanceof Region region) {
            region.setMinWidth(0.0);
            region.setMinHeight(0.0);
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
            if (!region.prefWidthProperty().isBound()) {
                region.prefWidthProperty().bind(host.widthProperty());
            }
            if (!region.prefHeightProperty().isBound()) {
                region.prefHeightProperty().bind(host.heightProperty());
            }
        }
        if (root instanceof ScrollPane scrollPane) {
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
        }
    }

    public void activate(String key) {
        for (Map.Entry<String, Parent> entry : loadedViews.entrySet()) {
            boolean active = entry.getKey().equals(key);
            Parent root = entry.getValue();
            if (root != null) {
                root.setVisible(active);
                root.setManaged(active);
                if (active) {
                    root.toFront();
                }
            }
        }
    }

    public Parent getViewRoot(String key) {
        return loadedViews.get(key);
    }
}
