package com.fileexplorer.ui.motion;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.MenuButton;
import javafx.util.Duration;

/**
 * Adds small Fluent-style micro-motion to interactive nodes so hover/press states
 * feel softer than the default JavaFX snap.
 */
public final class FluentMotionSupport {

    private static final String KEY_INSTALLED = "fileexplorer.fluentMotion.installed";
    private static final String KEY_TIMELINE = "fileexplorer.fluentMotion.timeline";
    private static final String KEY_CHILD_WATCH = "fileexplorer.fluentMotion.childWatch";

    private FluentMotionSupport() {
    }

    public static void install(Node root) {
        if (root == null) {
            return;
        }
        installRecursive(root);
    }

    private static void installRecursive(Node node) {
        if (node == null) {
            return;
        }
        installIfEligible(node);
        if (node instanceof Parent parent) {
            if (parent.getProperties().putIfAbsent(KEY_CHILD_WATCH, Boolean.TRUE) == null) {
                parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            for (Node added : change.getAddedSubList()) {
                                installRecursive(added);
                            }
                        }
                    }
                });
            }
            for (Node child : parent.getChildrenUnmodifiable()) {
                installRecursive(child);
            }
        }
    }

    private static void installIfEligible(Node node) {
        if (!isEligible(node)) {
            return;
        }
        if (node.getProperties().putIfAbsent(KEY_INSTALLED, Boolean.TRUE) != null) {
            return;
        }

        Runnable sync = () -> animateTo(node, targetScale(node), targetOpacity(node), durationMillis(node));

        node.hoverProperty().addListener((obs, oldV, newV) -> sync.run());
        if (node instanceof ButtonBase buttonBase) {
            buttonBase.armedProperty().addListener((obs, oldV, newV) -> sync.run());
            buttonBase.pressedProperty().addListener((obs, oldV, newV) -> sync.run());
        }
        sync.run();
    }

    private static boolean isEligible(Node node) {
        if (node instanceof ButtonBase) {
            return true;
        }
        if (node instanceof MenuButton) {
            return true;
        }
        return node.getStyleClass().contains("breadcrumb-button")
                || node.getStyleClass().contains("breadcrumb-separator-button")
                || node.getStyleClass().contains("fluent-command-button");
    }

    private static boolean isPressed(Node node) {
        return node instanceof ButtonBase buttonBase && (buttonBase.isPressed() || buttonBase.isArmed());
    }

    private static double targetScale(Node node) {
        if (isPressed(node)) {
            return 0.982;
        }
        if (node.isHover()) {
            return 0.994;
        }
        return 1.0;
    }

    private static double targetOpacity(Node node) {
        if (isPressed(node)) {
            return 0.965;
        }
        if (node.isHover()) {
            return 0.992;
        }
        return 1.0;
    }

    private static double durationMillis(Node node) {
        return isPressed(node) ? 72.0 : (node.isHover() ? 110.0 : 150.0);
    }

    private static void animateTo(Node node, double scale, double opacity, double millis) {
        Object existing = node.getProperties().get(KEY_TIMELINE);
        if (existing instanceof Timeline timeline) {
            timeline.stop();
        }
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(millis),
                        new KeyValue(node.scaleXProperty(), scale, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(), scale, Interpolator.EASE_BOTH),
                        new KeyValue(node.opacityProperty(), opacity, Interpolator.EASE_BOTH))
        );
        node.getProperties().put(KEY_TIMELINE, timeline);
        timeline.play();
    }
}
