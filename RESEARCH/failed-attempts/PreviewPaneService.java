package com.fileexplorer.ui;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import java.nio.file.Files;
import java.nio.file.Path;

public class PreviewPaneService {

    public Node generatePreview(Path file) {
        try {
            String type = Files.probeContentType(file);
            if (type == null) {
                return new Text("Unknown file type");
            }

            if (type.startsWith("image/")) {
                Image img = new Image(file.toUri().toString(), 300, 300, true, true);
                ImageView iv = new ImageView(img);
                iv.setPreserveRatio(true);
                return iv;
            }

            if (type.equals("application/pdf")) {
                // Placeholder: integrate PDF rendering (PDFBox or similar)
                return new Text("[PDF Preview Not Implemented]");
            }

            if (type.startsWith("text/")) {
                String content = Files.readString(file);
                if (content.length() > 1000) content = content.substring(0, 1000) + "...";
                return new Text(content);
            }

            return new Text("Preview not available");

        } catch (Exception e) {
            return new Text("Error generating preview");
        }
    }
}
