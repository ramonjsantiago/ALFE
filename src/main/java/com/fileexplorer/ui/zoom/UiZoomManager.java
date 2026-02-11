package com.fileexplorer.ui.zoom;

import java.util.List;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Screen;
import javafx.stage.Stage;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

public final class UiZoomManager {

    private static final Logger LOG = Logger.getLogger(UiZoomManager.class.getName());

    private static final String PROP_BASE_STYLE = "ui.zoom.baseStyle";

    private final ZoomRoot zoomRoot;
    private final Stage stageOrNull;

    // Minimum zoom is 1.0 so baseline font size (24px) is the minimum.
    private final double minZoom;
    private final double maxZoom;
    private final double step;

    private Scene sceneOrNull;

    private double zoom;

    // Baseline minimum font size
    private final double baseFontPx;

    public UiZoomManager(ZoomRoot zoomRoot, Stage stageOrNull) {
        LogSupport.enter(LOG, "UiZoomManager");
        this(zoomRoot, stageOrNull, 1.0, 1.0, 3.0, 0.1, 24.0);
    }

    public UiZoomManager(
            ZoomRoot zoomRoot,
            Stage stageOrNull,
            double initialZoom,
            double minZoom,
            double maxZoom,
            double step,
            double baseFontPx
    ) {
        LogSupport.enter(LOG, "UiZoomManager");
        if (zoomRoot == null) {
            throw new IllegalArgumentException("zoomRoot must not be null");
        }
        if (minZoom <= 0.0 || maxZoom <= 0.0 || step <= 0.0) {
            throw new IllegalArgumentException("minZoom/maxZoom/step must be > 0");
        }
        if (minZoom > maxZoom) {
            throw new IllegalArgumentException("minZoom must be <= maxZoom");
        }
        if (!Double.isFinite(baseFontPx) || baseFontPx <= 0.0) {
            throw new IllegalArgumentException("baseFontPx must be > 0");
        }

        this.zoomRoot = zoomRoot;
        this.stageOrNull = stageOrNull;

        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.step = step;

        this.baseFontPx = baseFontPx;

        this.zoom = clamp(initialZoom, minZoom, maxZoom);
    }

    public void install(Scene scene) {
        LogSupport.enter(LOG, "install");
        if (scene == null) {
            throw new IllegalArgumentException("scene must not be null");
        }
        this.sceneOrNull = scene;

        Object existing = scene.getRoot().getProperties().get(PROP_BASE_STYLE);
        if (!(existing instanceof String)) {
            String base = scene.getRoot().getStyle();
            if (base == null) {
                base = "";
            }
            scene.getRoot().getProperties().put(PROP_BASE_STYLE, base);
        }

        register(scene, new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN), this::zoomIn);
        register(scene, new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN), this::zoomIn);
        register(scene, new KeyCodeCombination(KeyCode.ADD, KeyCombination.CONTROL_DOWN), this::zoomIn);

        register(scene, new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN), this::zoomOut);
        register(scene, new KeyCodeCombination(KeyCode.SUBTRACT, KeyCombination.CONTROL_DOWN), this::zoomOut);

        register(scene, new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN), this::reset);
        register(scene, new KeyCodeCombination(KeyCode.NUMPAD0, KeyCombination.CONTROL_DOWN), this::reset);

        // Apply baseline zoom immediately (24px minimum).
        applyCssZoom(1.0);
    }

    public double getZoom() {
        LogSupport.enter(LOG, "getZoom");
        return zoom;
    }

    public void zoomIn() {
        LogSupport.enter(LOG, "zoomIn");
        setZoom(zoom + step);
    }

    public void zoomOut() {
        LogSupport.enter(LOG, "zoomOut");
        setZoom(zoom - step);
    }

    public void reset() {
        LogSupport.enter(LOG, "reset");
        setZoom(1.0);
    }

    public void setZoom(double newZoom) {
        LogSupport.enter(LOG, "setZoom");
        double oldZoom = this.zoom;
        this.zoom = clamp(newZoom, minZoom, maxZoom);
        applyCssZoom(oldZoom);
    }

    private void applyCssZoom(double oldZoom) {
        LogSupport.enter(LOG, "applyCssZoom");
        Scene scene = this.sceneOrNull;
        if (scene == null) {
            return;
        }

        // Do NOT use transform zoom (it causes toolbar/breadcrumb clipping).
        zoomRoot.setZoom(1.0);

        String baseStyle = "";
        Object stored = scene.getRoot().getProperties().get(PROP_BASE_STYLE);
        if (stored instanceof String) {
            baseStyle = (String) stored;
        }

        String style = baseStyle == null ? "" : baseStyle.trim();
        if (!style.isEmpty() && !style.endsWith(";")) {
            style = style + ";";
        }

        double fontPx = baseFontPx * zoom;
        fontPx = Math.round(fontPx * 10.0) / 10.0;

        style = style + " -fx-font-size: " + fontPx + "px;";
        scene.getRoot().setStyle(style);

        // Force re-measure/re-layout to stop clipping (breadcrumb/toolbars)
        scene.getRoot().applyCss();
        scene.getRoot().layout();

        // Optional: keep Stage size proportional to zoom (clamped to usable screen area)
        if (stageOrNull != null) {
            if (stageOrNull.isMaximized()) {
                stageOrNull.setMaximized(false);
            }

            double w = stageOrNull.getWidth();
            double h = stageOrNull.getHeight();

            if (Double.isFinite(w) && Double.isFinite(h) && w > 50.0 && h > 50.0) {
                double oz = (Double.isFinite(oldZoom) && oldZoom > 0.0) ? oldZoom : 1.0;

                double baseW = w / oz;
                double baseH = h / oz;

                Rectangle2D vb = getVisualBoundsForStage(stageOrNull);

                double targetW = baseW * zoom;
                double targetH = baseH * zoom;

                double clampedW = clamp(targetW, 800.0, vb.getWidth());
                double clampedH = clamp(targetH, 600.0, vb.getHeight());

                stageOrNull.setWidth(clampedW);
                stageOrNull.setHeight(clampedH);
            }
        }
    }

    private static Rectangle2D getVisualBoundsForStage(Stage stage) {
        LogSupport.enter(LOG, "getVisualBoundsForStage");
        double x = stage.getX();
        double y = stage.getY();
        double w = stage.getWidth();
        double h = stage.getHeight();

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(w) || !Double.isFinite(h)) {
            return Screen.getPrimary().getVisualBounds();
        }

        List<Screen> screens = Screen.getScreensForRectangle(x, y, Math.max(1.0, w), Math.max(1.0, h));
        if (screens == null || screens.isEmpty()) {
            return Screen.getPrimary().getVisualBounds();
        }
        return screens.getFirst().getVisualBounds();
    }

    private static void register(Scene scene, KeyCombination kc, Runnable action) {
        LogSupport.enter(LOG, "register");
        scene.getAccelerators().put(kc, action);
    }

    private static double clamp(double v, double min, double max) {
        LogSupport.enter(LOG, "clamp");
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }
}
