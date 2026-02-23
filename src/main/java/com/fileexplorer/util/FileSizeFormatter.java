package com.fileexplorer.util;

import java.text.DecimalFormat;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

/**
 * Utility for formatting file sizes in a Windows-like way.
 */
public final class FileSizeFormatter {
    private static final Logger LOG = Logger.getLogger(FileSizeFormatter.class.getName());

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.#");

/**
 * FileSizeFormatter.
 *
 * @return TODO
 */
    private FileSizeFormatter() {
        LogSupport.enter(LOG, "FileSizeFormatter");
    }

/**
 * format.
 *
 * @param bytes TODO
 * @return TODO
 */
    public static String format(long bytes) {
        LogSupport.enter(LOG, "format");
        if (bytes < 0) {
            return "";
        }
        if (bytes == 0) {
            return "0 B";
        }

        double value = bytes;
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < UNITS.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }

        return DECIMAL_FORMAT.format(value) + " " + UNITS[unitIndex];
    }
}
