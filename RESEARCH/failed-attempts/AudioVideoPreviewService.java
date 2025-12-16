package com.fileexplorer.ui;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import java.nio.file.Path;

/**
 * AudioVideoPreviewService
 *  - Plays audio/video previews in PreviewPane
 *  - Returns MediaView to embed in scene
 */
public class AudioVideoPreviewService {

    public MediaView preview(Path file) {
        try {
            Media media = new Media(file.toUri().toString());
            MediaPlayer player = new MediaPlayer(media);
            player.setAutoPlay(true);
            MediaView view = new MediaView(player);
            return view;
        } catch (Exception e) {
            return null;
        }
    }
}