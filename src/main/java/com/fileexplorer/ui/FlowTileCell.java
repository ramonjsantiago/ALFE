package com.fileexplorer.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.concurrent.Future;

public class FlowTileCell extends StackPane {
    public final Path path;
    private final ImageView imageView;
    private final Label label;
    private Future<?> loadingTask;

    public interface Loader {
        Future<?> load(Path p, java.util.function.Consumer<Image> cb);
    }

    public FlowTileCell(Path path, Loader loader) {
        this.path = path;
        setPrefSize(140, 140);
        getStyleClass().add("flow-tile-cell");

        imageView = new ImageView();
        imageView.setFitWidth(120);
        imageView.setFitHeight(90);
        imageView.setPreserveRatio(true);

        label = new Label(path.getFileName().toString());
        label.setWrapText(true);
        label.setMaxWidth(120);

        VBox box = new VBox(imageView, label);
        box.setAlignment(Pos.CENTER);
        getChildren().add(box);

        loadingTask = loader.load(path, img -> Platform.runLater(() -> imageView.setImage(img)));

        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                System.out.println("Open: " + path);
            }
        });
    }

    public void cancel() {
        if (loadingTask != null) loadingTask.cancel(true);
    }
}
