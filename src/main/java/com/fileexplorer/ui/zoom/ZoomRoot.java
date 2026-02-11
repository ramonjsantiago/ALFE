package com.fileexplorer.ui.zoom;

import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.transform.Scale;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

public final class ZoomRoot extends Region {

    private static final Logger LOG = Logger.getLogger(ZoomRoot.class.getName());

    private final Node content;
    private final Scale scale;

    private double zoom;

    public ZoomRoot(Node content) {
        LogSupport.enter(LOG, "ZoomRoot");
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }

        this.content = content;
        this.zoom = 1.0;

        this.scale = new Scale(1.0, 1.0, 0.0, 0.0);
        this.content.getTransforms().add(this.scale);

        getChildren().add(this.content);

        // Allow this root to always expand to the Scene size.
        setMinSize(0.0, 0.0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public Region getRoot() {
        LogSupport.enter(LOG, "getRoot");
        return this;
    }

    public double getZoom() {
        LogSupport.enter(LOG, "getZoom");
        return zoom;
    }

    public void setZoom(double zoom) {
        LogSupport.enter(LOG, "setZoom");
        double z = zoom;
        if (!Double.isFinite(z) || z <= 0.0) {
            z = 1.0;
        }

        this.zoom = z;
        this.scale.setX(z);
        this.scale.setY(z);

        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        LogSupport.enter(LOG, "layoutChildren");
        double z = zoom;
        if (!Double.isFinite(z) || z <= 0.0) {
            z = 1.0;
        }

        // Resize the content to the "unscaled" size so that after scaling it fills the available space.
        double w = getWidth() / z;
        double h = getHeight() / z;

        if (!Double.isFinite(w) || w < 0.0) {
            w = 0.0;
        }
        if (!Double.isFinite(h) || h < 0.0) {
            h = 0.0;
        }

        layoutInArea(content, 0.0, 0.0, w, h, 0.0, HPos.LEFT, VPos.TOP);
    }

    @Override
    protected double computeMinWidth(double height) {
        LogSupport.enter(LOG, "computeMinWidth");
        return 0.0;
    }

    @Override
    protected double computeMinHeight(double width) {
        LogSupport.enter(LOG, "computeMinHeight");
        return 0.0;
    }
}
