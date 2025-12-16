package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import java.nio.file.Path;
import java.util.ArrayList;

public class BreadcrumbBarController {

    @FXML private HBox breadcrumbRoot;

    private Path currentPath;
    private OnPathSelectedListener listener;

    public interface OnPathSelectedListener {
        void onSelect(Path path);
    }

    public void setOnPathSelected(OnPathSelectedListener l) {
        this.listener = l;
    }

    public void setPath(Path path) {
        this.currentPath = path;
        buildBreadcrumb();
    }

    private void buildBreadcrumb() {
        breadcrumbRoot.getChildren().clear();

        if (currentPath == null) return;

        ArrayList<Path> segments = new ArrayList<>();

        Path p = currentPath;
        while (p != null) {
            segments.add(0, p);
            p = p.getParent();
        }

        for (int i = 0; i < segments.size(); i++) {
            Path seg = segments.get(i);

            Button btn = new Button(seg.getFileName() == null ? seg.toString() : seg.getFileName().toString());
            btn.getStyleClass().add("breadcrumb-btn");

            final int index = i;
            btn.setOnAction(e -> {
                if (listener != null) {
                    listener.onSelect(segments.get(index));
                }
            });

            breadcrumbRoot.getChildren().add(btn);

            if (i < segments.size() - 1) {
                Button sep = new Button(">");
                sep.getStyleClass().add("breadcrumb-separator");
                sep.setDisable(true);
                breadcrumbRoot.getChildren().add(sep);
            }
        }
    }
}
