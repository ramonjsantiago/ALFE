package com.fileexplorer.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * NavigationPaneController — tree with Quick Access / Pinned items
 */
public class NavigationPaneController {

    @FXML private TreeView<File> treeView;
    @FXML private Button btnPin;
    @FXML private Button btnAddFavorite;

    private final Set<File> pinned = new HashSet<>();
    private final List<File> favorites = new ArrayList<>();
    private final Preferences prefs = Preferences.userNodeForPackage(NavigationPaneController.class);

    @FXML
    public void initialize() {
        TreeItem<File> root = new TreeItem<>(new File(System.getProperty("user.home")));
        treeView.setRoot(root);
        treeView.setShowRoot(true);
        populateHome(root);

        treeView.getSelectionModel().selectedItemProperty().addListener((_,_,newV) -> {
            updatePinButton(newV != null ? newV.getValue() : null);
        });

        btnPin.setOnAction(_ -> {
            TreeItem<File> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            File f = selected.getValue();
            if (pinned.contains(f)) { pinned.remove(f); } else { pinned.add(f); }
            refreshPinnedSection();
            persistPins();
        });

        btnAddFavorite.setOnAction(_ -> {
            TreeItem<File> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                favorites.add(selected.getValue());
                persistFavorites();
                refreshFavoritesSection();
            }
        });

        restorePinsAndFavorites();
    }

    private void populateHome(TreeItem<File> root) {
        // Add common folders
        File home = root.getValue();
        for (File f : home.listFiles(pathname -> pathname.isDirectory())) {
            TreeItem<File> t = new TreeItem<>(f);
            root.getChildren().add(t);
        }
    }

    private void updatePinButton(File f) {
        if (f == null) { btnPin.setText("Pin"); return; }
        btnPin.setText(pinned.contains(f) ? "Unpin" : "Pin");
    }

    private void refreshPinnedSection() {
        TreeItem<File> pinnedRoot = new TreeItem<>(new File("Pinned"));
        for (File f : pinned) pinnedRoot.getChildren().add(new TreeItem<>(f));
        // remove old pinned section if present
        treeView.getRoot().getChildren().removeIf(t -> t.getValue().getName().equals("Pinned"));
        treeView.getRoot().getChildren().add(0, pinnedRoot);
    }

    private void refreshFavoritesSection() {
        TreeItem<File> favRoot = new TreeItem<>(new File("Favorites"));
        for (File f : favorites) favRoot.getChildren().add(new TreeItem<>(f));
        treeView.getRoot().getChildren().removeIf(t -> t.getValue().getName().equals("Favorites"));
        treeView.getRoot().getChildren().add(1, favRoot);
    }

    private void persistPins() {
        try {
            StringBuilder sb = new StringBuilder();
            for (File f : pinned) sb.append(f.getAbsolutePath()).append(";");
            prefs.put("nav.pins", sb.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void persistFavorites() {
        try {
            StringBuilder sb = new StringBuilder();
            for (File f : favorites) sb.append(f.getAbsolutePath()).append(";");
            prefs.put("nav.favorites", sb.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void restorePinsAndFavorites() {
        String pinStr = prefs.get("nav.pins", "");
        if (!pinStr.isEmpty()) for (String p : pinStr.split(";")) if (!p.isBlank()) pinned.add(new File(p));
        String favStr = prefs.get("nav.favorites", "");
        if (!favStr.isEmpty()) for (String p : favStr.split(";")) if (!p.isBlank()) favorites.add(new File(p));
        refreshPinnedSection();
        refreshFavoritesSection();
    }
}


// package com.explorer.ui;

// import javafx.fxml.FXML;
// import javafx.scene.control.TextArea;
// import javafx.scene.image.Image;
// import javafx.scene.image.ImageView;
// import javafx.scene.layout.StackPane;

// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Path;

// public class PreviewPaneController {

    // @FXML private StackPane previewRoot;
    // private TextArea textPreview;
    // private ImageView imagePreview;

    // @FXML
    // private void initialize() {
        // textPreview = new TextArea();
        // textPreview.setEditable(false);
        // textPreview.setVisible(false);

        // imagePreview = new ImageView();
        // imagePreview.setPreserveRatio(true);
        // imagePreview.setFitWidth(400);
        // imagePreview.setFitHeight(300);
        // imagePreview.setVisible(false);

        // previewRoot.getChildren().addAll(textPreview, imagePreview);
    // }

    // public void showPreview(Path file) {
        // if (file == null || !Files.exists(file)) {
            // clearPreview();
            // return;
        // }

        // try {
            // String type = Files.probeContentType(file);
            // if (type != null && type.startsWith("text")) {
                // textPreview.setText(Files.readString(file));
                // textPreview.setVisible(true);
                // imagePreview.setVisible(false);
            // } else if (type != null && type.startsWith("image")) {
                // Image img = new Image(file.toUri().toString());
                // imagePreview.setImage(img);
                // imagePreview.setVisible(true);
                // textPreview.setVisible(false);
            // } else {
                // clearPreview();
            // }
        // } catch (IOException e) {
            // clearPreview();
        // }
    // }

    // public void clearPreview() {
        // textPreview.clear();
        // textPreview.setVisible(false);
        // imagePreview.setImage(null);
        // imagePreview.setVisible(false);
    // }
// }