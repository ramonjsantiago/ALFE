package com.fileexplorer.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;

public final class CursorService {

    private static final String BASE = "/com/fileexplorer/ui/cursors/";

    private final ConcurrentMap<String, ImageCursor> cache;
    private volatile double uiScale;

    public CursorService() {
        this.cache = new ConcurrentHashMap<>();
        this.uiScale = 1.0;
    }

    public void setUiScale(double uiScale) {
        this.uiScale = Math.max(0.5, Math.min(2.5, uiScale));
    }

    public void install(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        applyToNodeTree(scene.getRoot());
        Platform.runLater(() -> applyToNodeTree(scene.getRoot()));
    }

    public Cursor cursorArrow() {
        ImageCursor c = loadCurVariant("aero_arrow");
        return c != null ? c : Cursor.DEFAULT;
    }

    public Cursor cursorHand() {
        ImageCursor c = loadCurVariant("aero_link");
        return c != null ? c : Cursor.HAND;
    }

    public Cursor cursorText() {
        ImageCursor c = loadCurVariant("aero_ibeam");
        return c != null ? c : Cursor.TEXT;
    }

    public Cursor cursorEwResize() {
        ImageCursor c = loadCurVariant("aero_ew");
        return c != null ? c : Cursor.E_RESIZE;
    }

    public Cursor cursorNsResize() {
        ImageCursor c = loadCurVariant("aero_ns");
        return c != null ? c : Cursor.N_RESIZE;
    }

    public Cursor cursorNwseResize() {
        ImageCursor c = loadCurVariant("aero_nwse");
        return c != null ? c : Cursor.SE_RESIZE;
    }

    public Cursor cursorNeswResize() {
        ImageCursor c = loadCurVariant("aero_nesw");
        return c != null ? c : Cursor.SW_RESIZE;
    }

    private void applyToNodeTree(Node node) {
        if (node == null) return;

        if (node.getCursor() == null) {
            node.setCursor(cursorArrow());
        }

        if (node instanceof ButtonBase || node instanceof Hyperlink) {
            node.setCursor(cursorHand());
        } else if (node instanceof TextInputControl) {
            node.setCursor(cursorText());
        } else if (node instanceof SplitPane sp) {
            Cursor c = sp.getOrientation() == javafx.geometry.Orientation.HORIZONTAL
                    ? cursorEwResize()
                    : cursorNsResize();
            sp.lookupAll(".split-pane-divider").forEach(d -> d.setCursor(c));
        } else if (node instanceof TableView<?>) {
            node.lookupAll(".column-resize-line").forEach(n -> n.setCursor(cursorEwResize()));
        }

        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                applyToNodeTree(child);
            }
        }
    }

    private ImageCursor loadCurVariant(String baseName) {
        String key = baseName + "@" + variantSuffix();
        return cache.computeIfAbsent(key, k -> {
            String suffix = variantSuffix();
            String file = baseName + suffix + ".cur";
            ImageCursor c = tryLoad(file);
            if (c != null) return c;
            return tryLoad(baseName + ".cur");
        });
    }

    private String variantSuffix() {
        double s = uiScale;
        if (s >= 1.5) return "_xl";
        if (s >= 1.2) return "_l";
        return "";
    }

    private ImageCursor tryLoad(String fileName) {
        String res = BASE + fileName;
        try (InputStream in = CursorService.class.getResourceAsStream(res)) {
            if (in == null) return null;
            IcoCurDecoder.Decoded d = IcoCurDecoder.decodeCur(in, desiredSize());
            if (d == null || d.image() == null) return null;
            return new ImageCursor(d.image(), d.hotspotX(), d.hotspotY());
        } catch (IOException ex) {
            return null;
        }
    }

    private int desiredSize() {
        double s = uiScale;
        if (s >= 1.5) return 64;
        if (s >= 1.2) return 48;
        return 32;
    }
}
