package com.fileexplorer.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

/**
 * ClipboardUtil.
 * <p>
 * Auto-generated API documentation for this type.
 */
public final class ClipboardUtil {

    private static final Logger LOG = Logger.getLogger(ClipboardUtil.class.getName());

/**
 * ClipboardUtil.
 *
 * @return TODO
 */
    private ClipboardUtil() {
        LogSupport.enter(LOG, "ClipboardUtil");
    }

/**
 * copyToClipboard.
 *
 * @param text TODO
 */
    public static void copyToClipboard(String text) {
        LogSupport.enter(LOG, "copyToClipboard");
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text != null ? text : "");
        clipboard.setContent(content);
    }
}
