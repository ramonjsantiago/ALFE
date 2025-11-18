import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import javafx.scene.image.ImageView;
import javafx.embed.swing.SwingFXUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@FXML private StackPane previewContainer; // parent of ImageView, MediaView, etc.
@FXML private ImageView previewImage;

private MediaPlayer mediaPlayer;

public void showPreview(Path file) {
    previewContainer.getChildren().clear();
    String name = file.getFileName().toString().toLowerCase();
    try {
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
            name.endsWith(".bmp") || name.endsWith(".tiff") || name.endsWith(".gif")) {
            // Image preview
            previewImage.setImage(IconLoader.loadIcon(file.toFile()));
            previewContainer.getChildren().add(previewImage);
        } else if (name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".m4v") || name.endsWith(".avi")) {
            // Video preview
            Media media = new Media(file.toUri().toString());
            if (mediaPlayer != null) mediaPlayer.dispose();
            mediaPlayer = new MediaPlayer(media);
            MediaView mediaView = new MediaView(mediaPlayer);
            mediaView.setPreserveRatio(true);
            mediaView.setFitWidth(previewContainer.getWidth());
            mediaView.setFitHeight(previewContainer.getHeight());
            previewContainer.getChildren().add(mediaView);
            mediaPlayer.setAutoPlay(true);
        } else if (name.endsWith(".pdf")) {
            // PDF preview using PDFBox
            PDDocument document = PDDocument.load(file.toFile());
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 150);
            ImageView pdfView = new ImageView(SwingFXUtils.toFXImage(image, null));
            pdfView.setPreserveRatio(true);
            pdfView.setFitWidth(previewContainer.getWidth());
            pdfView.setFitHeight(previewContainer.getHeight());
            previewContainer.getChildren().add(pdfView);
            document.close();
        } else {
            // Fallback
            previewContainer.getChildren().add(new Label("No preview available"));
        }
    } catch (IOException | RuntimeException e) {
        previewContainer.getChildren().add(new Label("Preview failed: " + e.getMessage()));
    }
}

// Call this to stop media when tab is closed
public void cleanup() {
    if (mediaPlayer != null) {
        mediaPlayer.stop();
        mediaPlayer.dispose();
    }
}
