package com.fileexplorer.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * PreviewPaneController — supports images, text, and basic PDF preview via WebView fallback.
 * Note: For full PDF rendering consider integrating PDFBox or OpenViewerFX.
 */
public class PreviewPaneController {

    @FXML private StackPane previewContainer;
    @FXML private ImageView imageView;
    @FXML private TextArea textArea;
    @FXML private WebView webView;
    @FXML private Label messageLabel;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @FXML
    public void initialize() {
        clearPreview();
    }

    public void show(File file) {
        clearPreview();
        if (file == null || !file.exists()) return;
        String name = file.getName().toLowerCase();
        if (name.matches(".*\\.(png|jpg|jpeg|gif|bmp|tiff)$")) {
            // Load image off FX thread
            executor.submit(() -> {
                try {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(file.toURI().toString(), true);
                    Platform.runLater(() -> {
                        imageView.setImage(img);
                        imageView.setVisible(true);
                        animateFade();
                    });
                } catch (Exception e) { showMessage("Unable to load image"); }
            });
        } else if (name.matches(".*\\.(txt|log|md|csv|java|py|json)$")) {
            executor.submit(() -> {
                try {
                    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    Platform.runLater(() -> {
                        textArea.setText(content);
                        textArea.setVisible(true);
                        animateFade();
                    });
                } catch (Exception e) { showMessage("Unable to load text"); }
            });
        } else if (name.endsWith(".pdf")) {
            // Try opening PDF in WebView as fallback (relies on system plugins / browser support)
            Platform.runLater(() -> {
                try {
                    webView.getEngine().load(file.toURI().toString());
                    webView.setVisible(true);
                    animateFade();
                } catch (Exception e) { showMessage("PDF preview not available"); }
            });
        } else {
            showMessage("No preview available for this file type");
        }
    }

    private void showMessage(String msg) {
        Platform.runLater(() -> {
            messageLabel.setText(msg);
            messageLabel.setVisible(true);
        });
    }

    private void clearPreview() {
        Platform.runLater(() -> {
            imageView.setVisible(false); imageView.setImage(null);
            textArea.setVisible(false); textArea.clear();
            webView.setVisible(false); webView.getEngine().load("about:blank");
            messageLabel.setVisible(false); messageLabel.setText("");
        });
    }

    private void animateFade() {
        Platform.runLater(() -> {
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(220), previewContainer);
            ft.setFromValue(0.0); ft.setToValue(1.0); ft.play();
        });
    }

    public void shutdown() { executor.shutdownNow(); }
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