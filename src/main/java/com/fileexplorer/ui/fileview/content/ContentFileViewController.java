package com.fileexplorer.ui.fileview.content;

import com.fileexplorer.ui.fileview.shared.AbstractIconFlowFileViewController;

/**
 * Modular controller for the content file-view surface.
 */
public final class ContentFileViewController extends AbstractIconFlowFileViewController {
    @Override
    public String getViewKey() {
        return "content";
    }
}
