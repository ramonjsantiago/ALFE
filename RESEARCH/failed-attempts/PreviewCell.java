package com.fileexplorer.ui;

import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import java.io.File;

public class PreviewCell extends ListCell<File> {

    private final ImageView imageView = new ImageView();
    private MediaView mediaView;

    public PreviewCell() {
        imageView.setFitWidth(150);
        imageView.setFitHeight(150);
        imageView.setPreserveRatio(true);
    }

    @Override
    protected void updateItem(File file, boolean empty) {
        super.updateItem(file, empty);
        if (empty || file == null) {
            setGraphic(null);
            return;
        }

        String name = file.getName().toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif")) {
            Image img = ThumbnailCacheSingleton.getInstance().get(file.getAbsolutePath());
            if (img == null) img = ThumbnailGenerator.generateThumbnail(file);
            setGraphic(new ImageView(img));
        } else if (name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".m4v")) {
            try {
                Media media = new Media(file.toURI().toString());
                MediaPlayer player = new MediaPlayer(media);
                mediaView = new MediaView(player);
                mediaView.setFitWidth(150);
                mediaView.setFitHeight(150);
                mediaView.setPreserveRatio(true);
                player.setAutoPlay(false);
                setGraphic(mediaView);
            } catch (Exception e) {
                setGraphic(IconLoader.getFileIconView());
            }
        } else {
            setGraphic(IconLoader.getFileIconView());
        }
    }
}
