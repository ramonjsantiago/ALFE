package com.fileexplorer.ui.fileview.api;

import javafx.scene.Node;

/**
 * Marker interface for lazily loaded file-view modules.
 */
public interface FileViewComponent {
    /**
     * Returns the root node contributed by the file-view module.
     *
     * @return root node
     */
    Node getRoot();
}
