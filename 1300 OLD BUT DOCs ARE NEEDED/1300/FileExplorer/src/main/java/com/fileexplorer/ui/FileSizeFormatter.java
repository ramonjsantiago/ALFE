package com.fileexplorer.ui;

import java.text.DecimalFormat;

/**
 * Utility for formatting file sizes in a Windows-like way.
 */
public final class FileSizeFormatter {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.#");

    private FileSizeFormatter() {
    }

    public static String format(long bytes) {
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
