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
