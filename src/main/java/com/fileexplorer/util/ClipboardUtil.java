package com.fileexplorer.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

public final class ClipboardUtil {

    private static final Logger LOG = Logger.getLogger(ClipboardUtil.class.getName());

    private ClipboardUtil() {
        LogSupport.enter(LOG, "ClipboardUtil");
    }

    public static void copyToClipboard(String text) {
        LogSupport.enter(LOG, "copyToClipboard");
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text != null ? text : "");
        clipboard.setContent(content);
    }
}
