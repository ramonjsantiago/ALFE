package com.fileexplorer.ui.fileview.host;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
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
        host.getChildren().add(0, root);
        loadedViews.put(key, root);
        controller = loader.getController();
        loadedControllers.put(key, controller);
        return controller;
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
