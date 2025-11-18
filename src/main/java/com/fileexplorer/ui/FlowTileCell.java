package com.fileexplorer.ui;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class FlowTileCell extends StackPane {
    private static int globalIconSize = 64;
    public static void setGlobalIconSize(int size) { globalIconSize = size; }
    public static int getGlobalIconSize() { return globalIconSize; }
    private enum IconSize { SMALL, MEDIUM, LARGE }
    private static IconSize currentSize = IconSize.MEDIUM;

    public static void setIconSize(IconSize size) { currentSize = size; }

    private void updateIconSize() {
        if (getItem() == null) return;
        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(getItem().isDirectory() ? IconLoader.getFolderIcon() : IconLoader.getFileIcon());
        switch(currentSize) {
            case SMALL: iv.setFitWidth(32); iv.setFitHeight(32); break;
            case MEDIUM: iv.setFitWidth(64); iv.setFitHeight(64); break;
            case LARGE: iv.setFitWidth(128); iv.setFitHeight(128); break;
        }
        setGraphic(iv);
    }

    @Override
    protected void updateItem(java.io.File item, boolean empty) {
        super.updateItem(item, empty);
        if (!empty && item != null) updateIconSize();
    }
    private static int lastSelectedIndex = -1;

    @Override
    protected void updateItem(File item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            setText(item.getName());
            setOnMouseClicked(event -> {
                if (multiSelectMode && event.isControlDown()) {
                    selected = !selected;
                    lastSelectedIndex = getIndex();
                    updateSelectionStyle();
                } else if (multiSelectMode && event.isShiftDown()) {
                    if (lastSelectedIndex >= 0) {
                        int start = Math.min(lastSelectedIndex, getIndex());
                        int end = Math.max(lastSelectedIndex, getIndex());
                        getListView().getSelectionModel().clearSelection();
                        getListView().getSelectionModel().selectRange(start, end+1);
                    }
                } else {
                    getListView().getSelectionModel().clearSelection();
                    getListView().getSelectionModel().select(getIndex());
                    lastSelectedIndex = getIndex();
                }
            });
        }
    }
    private boolean selected = false;
    private boolean multiSelectMode = false;

    public void setMultiSelectMode(boolean enabled) { this.multiSelectMode = enabled; }

    @Override
    protected void updateItem(File item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            setText(item.getName());
            setOnMouseClicked(event -> {
                if (multiSelectMode && event.isControlDown()) {
                    selected = !selected;
                    updateSelectionStyle();
                } else if (multiSelectMode && event.isShiftDown()) {
                    // shift-select range handled in MainController
                } else {
                    getListView().getSelectionModel().clearSelection();
                    getListView().getSelectionModel().select(getIndex());
                }
            });
        }
    }

    private void updateSelectionStyle() {
        if (selected) setStyle("-fx-background-color: -fx-accent; -fx-text-fill: white;");
        else setStyle("");
    }
    private javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
    public void updateThumbnail(java.io.File file) {
        javafx.scene.image.Image img = thumbnailGenerator.loadThumbnail(file, 128, 128);
        imageView.setImage(img);
        imageView.setFitWidth(128); imageView.setFitHeight(128);
        imageView.setPreserveRatio(true);
        setGraphic(imageView);
    }
    private javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
    public void updateThumbnail(java.io.File file) {
        javafx.scene.image.Image img = thumbnailGenerator.loadThumbnail(file, 128, 128);
        imageView.setImage(img);
        imageView.setFitWidth(128); imageView.setFitHeight(128);
        imageView.setPreserveRatio(true);
        setGraphic(imageView);
    }

    public final Path path;
    private final ImageView imageView = new ImageView();
    private Future<?> thumbnailFuture;

    // ExecutorService shared, uses virtual threads
    private static final ExecutorService thumbnailExecutor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

    public FlowTileCell(Path path, BiFunction<Path, Consumer<Image>, Void> thumbnailLoader) {
        this.path = path;
        getChildren().add(imageView);

        loadThumbnail(thumbnailLoader);
    }

    private void loadThumbnail(BiFunction<Path, Consumer<Image>, Void> thumbnailLoader) {
        // Cancel previous task if any
        if (thumbnailFuture != null && !thumbnailFuture.isDone()) thumbnailFuture.cancel(true);

        thumbnailFuture = thumbnailExecutor.submit(() -> {
            try {
                thumbnailLoader.apply(path, img -> Platform.runLater(() -> imageView.setImage(img)));
            } catch (Exception e) {
                // Fallback placeholder
                Platform.runLater(() -> imageView.setImage(IconLoader.getPlaceholder()));
            }
        });
    }

    public void cancelThumbnail() {
        if (thumbnailFuture != null && !thumbnailFuture.isDone()) thumbnailFuture.cancel(true);
    }

    public void dispose() {
        cancelThumbnail();
    }
}
