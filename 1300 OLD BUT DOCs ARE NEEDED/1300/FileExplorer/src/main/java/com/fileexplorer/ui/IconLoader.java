package com.fileexplorer.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Centralised icon handling for the explorer.
 *
 * Responsibilities:
 *  - Provide icons for paths and logical types.
 *  - Prefer real bitmap icons from resources when available.
 *  - Fall back to in-memory placeholders when resources do not exist.
 *  - Support standard Windows-style sizes (16–256 px) and theme-aware
 *    (light/dark) variants.
 */
public final class IconLoader {

    public enum IconType {
        FOLDER,
        FILE,
        IMAGE,
        TEXT,
        ARCHIVE,
        AUDIO,
        VIDEO,
        PDF
    }

    private IconLoader() {
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Load an icon for the given file system path.
     *
     * @param path      the file or directory path
     * @param darkTheme true if dark theme variant should be used
     * @param size      requested size in pixels (will be clamped to a
     *                  standard set: 16, 24, 32, 48, 64, 96, 128, 256)
     * @return an Image suitable for use in JavaFX controls
     */
    public static Image loadForPath(Path path, boolean darkTheme, int size) {
        if (path == null) {
            return load(IconType.FILE, darkTheme, size);
        }

        try {
            if (Files.isDirectory(path)) {
                return load(IconType.FOLDER, darkTheme, size);
            }

            String mime = Files.probeContentType(path);
            String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
            IconType type = inferFrom(mime, name);
            return load(type, darkTheme, size);
        } catch (Exception ex) {
            // On any failure, fall back to generic file icon
            return load(IconType.FILE, darkTheme, size);
        }
    }

    /**
     * Infer a logical icon type from MIME type and/or file name.
     */
    public static IconType inferFrom(String mimeType, String fileName) {
        String lowerName = fileName != null ? fileName.toLowerCase() : "";
        String lowerMime = mimeType != null ? mimeType.toLowerCase() : "";

        // Images
        if (lowerMime.startsWith("image/") ||
                lowerName.endsWith(".png") ||
                lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".gif") ||
                lowerName.endsWith(".bmp") ||
                lowerName.endsWith(".webp")) {
            return IconType.IMAGE;
        }

        // Audio
        if (lowerMime.startsWith("audio/") ||
                lowerName.endsWith(".mp3") ||
                lowerName.endsWith(".wav") ||
                lowerName.endsWith(".flac") ||
                lowerName.endsWith(".m4a") ||
                lowerName.endsWith(".ogg")) {
            return IconType.AUDIO;
        }

        // Video
        if (lowerMime.startsWith("video/") ||
                lowerName.endsWith(".mp4") ||
                lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".mov") ||
                lowerName.endsWith(".avi") ||
                lowerName.endsWith(".wmv")) {
            return IconType.VIDEO;
        }

        // PDF
        if ("application/pdf".equals(lowerMime) || lowerName.endsWith(".pdf")) {
            return IconType.PDF;
        }

        // Archives
        if (lowerName.endsWith(".zip") ||
                lowerName.endsWith(".7z") ||
                lowerName.endsWith(".rar") ||
                lowerName.endsWith(".tar") ||
                lowerName.endsWith(".gz") ||
                lowerName.endsWith(".bz2")) {
            return IconType.ARCHIVE;
        }

        // Text / structured text
        if (lowerMime.startsWith("text/") ||
                lowerName.endsWith(".txt") ||
                lowerName.endsWith(".md") ||
                lowerName.endsWith(".log") ||
                lowerName.endsWith(".xml") ||
                lowerName.endsWith(".json") ||
                lowerName.endsWith(".yml") ||
                lowerName.endsWith(".yaml") ||
                lowerName.endsWith(".csv") ||
                lowerName.endsWith(".ini") ||
                lowerName.endsWith(".cfg")) {
            return IconType.TEXT;
        }

        return IconType.FILE;
    }

    /**
     * Load an icon for a logical type.
     */
    public static Image load(IconType type, boolean darkTheme, int size) {
        int clampedSize = clampSize(size);

        // Try themed bitmap resource first
        String resourceName = resourceNameFor(type, darkTheme, clampedSize);
        Image resourceImage = loadFromResource(resourceName);
        if (resourceImage != null) {
            return resourceImage;
        }

        // Fallback: drawn placeholder
        return drawPlaceholder(type, darkTheme, clampedSize);
    }

    // ---------------------------------------------------------------------
    // Resource loading
    // ---------------------------------------------------------------------

    private static String resourceNameFor(IconType type, boolean darkTheme, int size) {
        String themeSegment = darkTheme ? "dark" : "light";

        String typeSegment;
        switch (type) {
            case FOLDER -> typeSegment = "folder";
            case IMAGE -> typeSegment = "image";
            case TEXT -> typeSegment = "text";
            case ARCHIVE -> typeSegment = "archive";
            case AUDIO -> typeSegment = "audio";
            case VIDEO -> typeSegment = "video";
            case PDF -> typeSegment = "pdf";
            case FILE -> typeSegment = "file";
            default -> typeSegment = "file";
        }

        // Example path:
        //   /com/fileexplorer/ui/icons/light/folder-32.png
        return "/com/fileexplorer/ui/icons/" + themeSegment + "/" + typeSegment + "-" + size + ".png";
    }

    private static Image loadFromResource(String resourcePath) {
        try (InputStream in = IconLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return new Image(in);
        } catch (Exception ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Placeholder drawing (used when bitmaps are missing)
    // ---------------------------------------------------------------------

    private static Image drawPlaceholder(IconType type, boolean darkTheme, int size) {
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();

        Color border = darkTheme ? Color.rgb(200, 200, 200) : Color.rgb(60, 60, 60);
        Color bg = backgroundColorFor(type, darkTheme);
        Color glyph = darkTheme ? Color.WHITE : Color.BLACK;

        int w = size;
        int h = size;

        // Background + border
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean onBorder = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                if (onBorder) {
                    pw.setColor(x, y, border);
                } else {
                    pw.setColor(x, y, bg);
                }
            }
        }

        int inset = size / 4;
        int gx0 = inset;
        int gy0 = inset;
        int gx1 = w - inset;
        int gy1 = h - inset;

        switch (type) {
            case FOLDER -> {
                // Simple folder shape with a tab
                int tabHeight = (gy1 - gy0) / 3;
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if (y < gy0 + tabHeight && x < gx0 + (gx1 - gx0) / 2) {
                            pw.setColor(x, y, glyph.deriveColor(0, 1.0, 1.2, 1.0));
                        } else if (y >= gy0 + tabHeight) {
                            pw.setColor(x, y, glyph.deriveColor(0, 1.0, 0.8, 1.0));
                        }
                    }
                }
            }
            case IMAGE -> {
                // Checkerboard diagonal pattern
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if (((x + y) & 4) == 0) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case TEXT -> {
                // Horizontal lines
                for (int y = gy0; y < gy1; y++) {
                    if ((y - gy0) % 3 == 0) {
                        for (int x = gx0; x < gx1; x++) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case ARCHIVE -> {
                // Checkerboard boxes
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if (((x - gx0) / 3 + (y - gy0) / 3) % 2 == 0) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case AUDIO -> {
                // Vertical bars
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if ((x - gx0) % 4 == 0) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case VIDEO -> {
                // Simple play triangle
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        int relY = y - gy0;
                        int relX = x - gx0;
                        int height = gy1 - gy0;
                        int width = gx1 - gx0;
                        // Diagonal from top-left to bottom-left-ish
                        boolean inTriangle = relX >= (relY * width / height);
                        if (inTriangle) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case PDF, FILE -> {
                // Sparse dot grid
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if ((x - gx0) % 3 == 0 && (y - gy0) % 3 == 0) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            default -> {
                // Fallback: do nothing extra
            }
        }

        return img;
    }

    private static Color backgroundColorFor(IconType type, boolean darkTheme) {
        return switch (type) {
            case FOLDER -> darkTheme
                    ? Color.rgb(60, 90, 150)
                    : Color.rgb(255, 220, 140);
            case IMAGE -> darkTheme
                    ? Color.rgb(40, 120, 80)
                    : Color.rgb(180, 230, 190);
            case TEXT -> darkTheme
                    ? Color.rgb(80, 80, 140)
                    : Color.rgb(190, 200, 255);
            case ARCHIVE -> darkTheme
                    ? Color.rgb(120, 90, 40)
                    : Color.rgb(230, 210, 160);
            case AUDIO -> darkTheme
                    ? Color.rgb(80, 40, 120)
                    : Color.rgb(220, 190, 250);
            case VIDEO -> darkTheme
                    ? Color.rgb(40, 80, 140)
                    : Color.rgb(190, 220, 250);
            case PDF -> darkTheme
                    ? Color.rgb(140, 40, 40)
                    : Color.rgb(250, 190, 190);
            case FILE -> darkTheme
                    ? Color.rgb(90, 90, 90)
                    : Color.rgb(230, 230, 230);
        };
    }

    /**
     * Clamp requested size to the nearest standard icon size.
     */
    private static int clampSize(int size) {
        if (size <= 16) {
            return 16;
        }
        if (size >= 256) {
            return 256;
        }

        int[] allowed = {16, 24, 32, 48, 64, 96, 128, 256};
        int best = allowed[0];
        int bestDist = Math.abs(size - best);

        for (int candidate : allowed) {
            int dist = Math.abs(size - candidate);
            if (dist < bestDist) {
                best = candidate;
                bestDist = dist;
            }
        }

        return best;
    }
}
