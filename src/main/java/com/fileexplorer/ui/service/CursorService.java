package com.fileexplorer.ui.service;

import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;

/**
 * Cursor service for the UI layer.
 *
 * Compile-safe baseline:
 * - Uses JavaFX built-in cursors.
 * - No .cur decoding dependency here (you can add later).
 */
public final class CursorService {

    private volatile double uiScale;

    public CursorService() {
        this.uiScale = 1.0;
    }

    public void setUiScale(double uiScale) {
        this.uiScale = Math.max(0.5, Math.min(3.0, uiScale));
    }

    public double getUiScale() {
        return uiScale;
    }

    public void install(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        applyToNodeTree(scene.getRoot());

        // Re-apply after CSS/skins exist (SplitPane dividers, etc.).
        Platform.runLater(() -> applyToNodeTree(scene.getRoot()));
    }

    public Cursor cursorArrow() { return Cursor.DEFAULT; }
    public Cursor cursorHand() { return Cursor.HAND; }
    public Cursor cursorText() { return Cursor.TEXT; }

    public Cursor cursorEwResize() { return Cursor.E_RESIZE; }
    public Cursor cursorNsResize() { return Cursor.N_RESIZE; }

    /** JavaFX uses SE_RESIZE for the NWSE diagonal. */
    public Cursor cursorNwseResize() { return Cursor.SE_RESIZE; }

    public Cursor cursorNeswResize() { return Cursor.SW_RESIZE; }

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
            Cursor dividerCursor = sp.getOrientation() == javafx.geometry.Orientation.HORIZONTAL
                    ? cursorEwResize()
                    : cursorNsResize();
            sp.lookupAll(".split-pane-divider").forEach(d -> d.setCursor(dividerCursor));
        } else if (node instanceof TableView<?>) {
            node.lookupAll(".column-resize-line").forEach(n -> n.setCursor(cursorEwResize()));
        }

        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                applyToNodeTree(child);
            }
        }
    }
}
