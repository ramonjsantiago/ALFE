package com.fileexplorer.ui.tree;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.TreeItem;

/**
 * Helpers for working with a lazily-populated TreeView.
 *
 * <p>The tree uses lazy {@link TreeItem} implementations that replace a single
 * placeholder child with real children asynchronously on expansion. A naive
 * recursive "expand all" will therefore stop at placeholders. The helpers here
 * attach one-shot listeners so expansion continues after children arrive.
 */
public final class TreeViewSupport {

/**
 * TreeViewSupport.
 *
 * @return TODO
 */
    private TreeViewSupport() {
    }

    public static void toggleExpanded(TreeItem<?> item) {
        if (item == null) {
            return;
        }
        item.setExpanded(!item.isExpanded());
    }

    /**
     * Expands the tree down to {@code maxDepth} (0 = just root).
     *
     * <p>Runs on the JavaFX Application Thread.
     */
    public static void expandAllAsync(TreeItem<?> root, int maxDepth) {
        if (root == null) {
            return;
        }
        final int depthLimit = Math.max(0, maxDepth);
        if (Platform.isFxApplicationThread()) {
            expandNodeAsync(root, 0, depthLimit);
        } else {
            Platform.runLater(() -> expandNodeAsync(root, 0, depthLimit));
        }
    }

    /**
     * Collapses all expanded nodes (runs on the JavaFX Application Thread).
     */
    public static void collapseAll(TreeItem<?> root) {
        if (root == null) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> collapseAll(root));
            return;
        }

        // Post-order so we collapse children first (reduces churn).
        Deque<TreeItem<?>> stack = new ArrayDeque<>();
        Deque<TreeItem<?>> post = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TreeItem<?> n = stack.pop();
            post.push(n);
            for (TreeItem<?> c : n.getChildren()) {
                stack.push(c);
            }
        }
        while (!post.isEmpty()) {
            post.pop().setExpanded(false);
        }
    }

/**
 * expandNodeAsync.
 *
 * @param node TODO
 * @param depth TODO
 * @param maxDepth TODO
 */
    private static void expandNodeAsync(TreeItem<?> node, int depth, int maxDepth) {
        Objects.requireNonNull(node, "node");

        if (depth > maxDepth) {
            return;
        }

        // Expanding triggers lazy loaders.
        if (!node.isExpanded()) {
            node.setExpanded(true);
        }

        if (depth == maxDepth) {
            return;
        }

        // One-shot listener: when children change (placeholder -> real), recurse.
        AtomicBoolean fired = new AtomicBoolean(false);
        final ListChangeListener<TreeItem<?>>[] holder = new ListChangeListener[1];
        holder[0] = change -> {
            if (!fired.compareAndSet(false, true)) {
                return;
            }
            node.getChildren().removeListener(holder[0]);
            // Recurse after the children list is stable.
            Platform.runLater(() -> {
                for (TreeItem<?> c : node.getChildren()) {
                    expandNodeAsync(c, depth + 1, maxDepth);
                }
            });
        };
        node.getChildren().addListener(holder[0]);

        // If children are already real (not placeholder), expand immediately and remove listener.
        if (node.getChildren().size() != 1 || node.getChildren().get(0).getValue() != null) {
            if (fired.compareAndSet(false, true)) {
                node.getChildren().removeListener(holder[0]);
                for (TreeItem<?> c : node.getChildren()) {
                    expandNodeAsync(c, depth + 1, maxDepth);
                }
            }
        }
    }
}
