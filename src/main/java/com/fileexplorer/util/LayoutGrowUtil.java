package com.fileexplorer.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

public final class LayoutGrowUtil {

    private static final Logger LOG = Logger.getLogger(LayoutGrowUtil.class.getName());

    private LayoutGrowUtil() {
        LogSupport.enter(LOG, "LayoutGrowUtil");
    }

    /**
     * Walks the node tree and removes common max-size clamps that prevent resizing,
     * and applies VBox/HBox/GridPane/AnchorPane sizing rules so primary containers expand.
     * <p>
     * Call once after FXMLLoader.load() and before creating the Scene.
     */
    public static void makeResizable(Node root) {
        LogSupport.enter(LOG, "makeResizable");
        if (root == null) {
            return;
        }
        makeResizableRecursive(root, null);
    }

    private static void makeResizableRecursive(Node node, Parent parent) {
        LogSupport.enter(LOG, "makeResizableRecursive");
        if (node instanceof Region region) {
            // Remove "fixed max" constraints that cause layouts to stop expanding.
            region.setMinSize(0.0, 0.0);
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            // Let layout compute preferred size normally unless something else forces it.
            region.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        }

        if (node instanceof SplitPane splitPane) {
            splitPane.setMinSize(0.0, 0.0);
            splitPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        // Apply parent-specific sizing constraints to this node.
        if (parent != null) {
            applyParentRules(parent, node);
        }

        if (node instanceof Parent p) {
            List<Node> children = p.getChildrenUnmodifiable();
            for (Node child : children) {
                makeResizableRecursive(child, p);
            }
        }
    }

    private static void applyParentRules(Parent parent, Node child) {
        LogSupport.enter(LOG, "applyParentRules");
        // VBox/HBox only allocate extra space to children with grow priority.
        if (parent instanceof VBox && child instanceof Region) {
            VBox.setVgrow(child, Priority.ALWAYS);
        } else if (parent instanceof HBox && child instanceof Region) {
            HBox.setHgrow(child, Priority.ALWAYS);
        }

        // AnchorPane will NOT resize children unless anchors are set.
        // If this is your root pattern (very common in SceneBuilder), this is the main culprit.
        if (parent instanceof AnchorPane) {
            // Only force anchors on resizable nodes; leave non-resizable nodes alone.
            if (child instanceof Region) {
                setAnchorIfMissing(child, AnchorPane.getTopAnchor(child), Side.TOP);
                setAnchorIfMissing(child, AnchorPane.getRightAnchor(child), Side.RIGHT);
                setAnchorIfMissing(child, AnchorPane.getBottomAnchor(child), Side.BOTTOM);
                setAnchorIfMissing(child, AnchorPane.getLeftAnchor(child), Side.LEFT);
            }
        }

        // GridPane requires grow constraints to distribute extra space.
        if (parent instanceof GridPane && child instanceof Region) {
            GridPane.setHgrow(child, Priority.ALWAYS);
            GridPane.setVgrow(child, Priority.ALWAYS);
            GridPane.setFillWidth(child, true);
            GridPane.setFillHeight(child, true);
        }

        // BorderPane generally resizes center, but children with finite max can still clamp.
        // Making max infinite above is the primary fix; no extra action usually needed here.
        if (parent instanceof BorderPane && child instanceof Region) {
            // No-op: kept for clarity; constraints handled by maxSize above.
        }

        // StackPane will resize managed children; ensure Region max is infinite (handled above).
        if (parent instanceof StackPane && child instanceof Region) {
            // No-op: handled by Region sizing.
        }
    }

    private static void setAnchorIfMissing(Node node, Double existing, Side side) {
        LogSupport.enter(LOG, "setAnchorIfMissing");
        if (existing != null) {
            return;
        }
        double v = 0.0;
        switch (side) {
            case TOP -> AnchorPane.setTopAnchor(node, v);
            case RIGHT -> AnchorPane.setRightAnchor(node, v);
            case BOTTOM -> AnchorPane.setBottomAnchor(node, v);
            case LEFT -> AnchorPane.setLeftAnchor(node, v);
            default -> {
                // no-op
            }
        }
    }

    private enum Side {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT
    }
}
