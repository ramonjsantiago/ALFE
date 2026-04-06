package com.fileexplorer.ui.fileview.listview;

import com.fileexplorer.ui.fileview.shared.AbstractIconFlowFileViewController;

/**
 * Modular controller for the list file-view surface.
 */
public final class ListFileViewController extends AbstractIconFlowFileViewController {
    @Override
    public String getViewKey() {
        return "list";
    }
}
