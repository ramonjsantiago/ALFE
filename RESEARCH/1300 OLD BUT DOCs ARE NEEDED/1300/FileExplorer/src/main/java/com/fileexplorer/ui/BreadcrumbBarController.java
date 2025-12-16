package com.fileexplorer.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;

public final class BreadcrumbBarController {

    @FXML private ScrollPane scroll;
    @FXML private HBox crumbs;

    private Consumer<Path> onNavigate;
    private Path currentPath;

    public BreadcrumbBarController() {
        this.onNavigate = null;
        this.currentPath = null;
    }

    public void setOnNavigate(Consumer<Path> onNavigate) {
        this.onNavigate = onNavigate;
    }

    public void setPath(Path path) {
        this.currentPath = path;
        rebuild();
    }

    private void rebuild() {
        if (crumbs == null) {
            return;
        }
        crumbs.getChildren().clear();

        if (currentPath == null) {
            return;
        }

        List<Path> chain = buildChain(currentPath);
        for (int i = 0; i < chain.size(); i++) {
            Path p = chain.get(i);

            Button b = new Button(displayName(p));
            b.getStyleClass().add("breadcrumb-button");
            b.setFocusTraversable(false);
            b.setOnAction(e -> navigateTo(p));
            crumbs.getChildren().add(b);

            if (i < chain.size() - 1) {
                Button sep = new Button("›");
                sep.getStyleClass().add("breadcrumb-sep");
                sep.setFocusTraversable(false);
                sep.setDisable(true);
                crumbs.getChildren().add(sep);
            }
        }

        if (scroll != null) {
            scroll.setHvalue(1.0);
        }
    }

    private void navigateTo(Path p) {
        Consumer<Path> cb = this.onNavigate;
        if (cb != null && p != null) {
            cb.accept(p);
        }
    }

    private static List<Path> buildChain(Path leaf) {
        Objects.requireNonNull(leaf, "leaf");
        List<Path> list = new ArrayList<>();
        Path p = leaf;

        // Build reversed
        while (p != null) {
            list.add(0, p);
            p = p.getParent();
        }
        return list;
    }

    private static String displayName(Path p) {
        if (p == null) {
            return "";
        }
        Path fn = p.getFileName();
        if (fn == null) {
            String s = p.toString();
            return s.isBlank() ? "Computer" : s;
        }
        String name = fn.toString();
        return name.isBlank() ? p.toString() : name;
    }
}
