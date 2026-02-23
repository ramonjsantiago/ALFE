package com.fileexplorer.util;

import com.fileexplorer.service.icon.IconCacheService;
import com.fileexplorer.util.ResourceAudit;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

/**
 * Centralised icon handling for the explorer.
 * <p>
 * Key behaviors:
 *  - Extension-level caching via identity keys like "ext:pdf", "ext:txt".
 *  - Identity drives actual icon selection: ext:pdf resolves to PDF icon type, etc.
 */
public final class IconLoader {

    private static final Logger LOG = Logger.getLogger(IconLoader.class.getName());

/**
 * IconType.
 * <p>
 * Auto-generated API documentation for this type.
 */
    public enum IconType {
        FOLDER,
        FILE,
        IMAGE,
        TEXT,
        ARCHIVE,
        AUDIO,
        VIDEO,
        PDF,
        WORD,
        EXCEL,
        POWERPOINT,
        CODE,
        EXECUTABLE,
        LINK
    }

/**
 * IconLoader.
 *
 * @return TODO
 */
    private IconLoader() {
        LogSupport.enter(LOG, "IconLoader");
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Load an icon for the given file system path.
     * Derives an identity (type:... or ext:...) and delegates to loadForIdentity.
     */
    public static Image loadForPath(Path path, boolean darkTheme, int size) {
        LogSupport.enter(LOG, "loadForPath");
        int clamped = clampSize(size);

        String identity;
        if (path == null) {
            identity = "type:" + IconType.FILE.name();
        } else if (safeIsDirectory(path)) {
            LogSupport.enter(LOG, "safeIsDirectory");
            identity = "type:" + IconType.FOLDER.name();
        } else {
            String ext = extensionLower(fileNameOrPath(path));
            if (!ext.isBlank()) {
                identity = "ext:" + ext;
            } else {
                // Extensionless: fall back to generic file
                identity = "type:" + IconType.FILE.name();
            }
        }

        return loadForIdentity(identity, darkTheme, clamped);
    }

    /**
     * Backward-compatible alias for older code paths.
     */
    public static Image forPath(Path path, boolean darkTheme, int size) {
        LogSupport.enter(LOG, "forPath");
        return loadForPath(path, darkTheme, size);
    }

    /**
     * Load an icon based on an explicit identity:
     *  - type:FOLDER
     *  - ext:pdf
     *  - ext:txt
     * etc.
     * <p>
     * Identity drives BOTH:
     *  - caching key (identity is the cache key)
     *  - selection (ext:pdf resolves to PDF icon type, etc.)
     */
    public static Image loadForIdentity(String identity, boolean darkTheme, int size) {
        LogSupport.enter(LOG, "loadForIdentity");
        int clamped = clampSize(size);

        String id = normalizeIdentity(identity);
        IconType type = iconTypeForIdentity(id);

        IconCacheService cache = IconCacheService.getInstance();
        return cache.getOrLoad(
                id,
                darkTheme,
                clamped,
                () -> loadUncached(type, darkTheme, clamped)
        );
    }

    /**
     * Load by logical icon type (still cached).
     */
    public static Image load(IconType type, boolean darkTheme, int size) {
        LogSupport.enter(LOG, "load");
        IconType safeType = (type == null) ? IconType.FILE : type;
        int clamped = clampSize(size);

        IconCacheService cache = IconCacheService.getInstance();
        String id = "type:" + safeType.name();
        return cache.getOrLoad(id, darkTheme, clamped, () -> loadUncached(safeType, darkTheme, clamped));
    }

    // ---------------------------------------------------------------------
    // Identity -> Type mapping (selection)
    // ---------------------------------------------------------------------

/**
 * normalizeIdentity.
 *
 * @param identity TODO
 * @return TODO
 */
    private static String normalizeIdentity(String identity) {
        LogSupport.enter(LOG, "normalizeIdentity");
        if (identity == null || identity.isBlank()) {
            return "type:" + IconType.FILE.name();
        }

        String raw = identity.trim();

        if (raw.regionMatches(true, 0, "type:", 0, 5)) {
            String v = raw.substring(5).trim().toUpperCase(Locale.ROOT);
            if (v.isBlank()) {
                return "type:" + IconType.FILE.name();
            }
            return "type:" + v;
        }

        if (raw.regionMatches(true, 0, "ext:", 0, 4)) {
            String v = raw.substring(4).trim().toLowerCase(Locale.ROOT);
            if (v.isBlank()) {
                return "type:" + IconType.FILE.name();
            }
            return "ext:" + v;
        }

        // If someone passes "pdf" or ".pdf", treat it as ext
        if (raw.startsWith(".")) {
            String v = raw.substring(1).trim().toLowerCase(Locale.ROOT);
            return v.isBlank() ? "type:" + IconType.FILE.name() : "ext:" + v;
        }

        return "type:" + IconType.FILE.name();
    }

/**
 * iconTypeForIdentity.
 *
 * @param normalizedIdentity TODO
 * @return TODO
 */
    private static IconType iconTypeForIdentity(String normalizedIdentity) {
        LogSupport.enter(LOG, "iconTypeForIdentity");
        if (normalizedIdentity == null || normalizedIdentity.isBlank()) {
            return IconType.FILE;
        }

        if (normalizedIdentity.startsWith("type:")) {
            String v = normalizedIdentity.substring(5).trim();
            try {
                return IconType.valueOf(v);
            } catch (IllegalArgumentException ex) {
                return IconType.FILE;
            }
        }

        if (normalizedIdentity.startsWith("ext:")) {
            String ext = normalizedIdentity.substring(4).trim().toLowerCase(Locale.ROOT);
            return iconTypeForExtension(ext);
        }

        return IconType.FILE;
    }

/**
 * iconTypeForExtension.
 *
 * @param extLower TODO
 * @return TODO
 */
    private static IconType iconTypeForExtension(String extLower) {
        LogSupport.enter(LOG, "iconTypeForExtension");
        if (extLower == null || extLower.isBlank()) {
            return IconType.FILE;
        }

        String e = extLower.toLowerCase(Locale.ROOT);

/**
 * switch.
 *
 * @param e TODO
 * @return TODO
 */
        return switch (e) {
            case "pdf" -> IconType.PDF;

            // Microsoft Office
            case "doc", "docx", "dot", "dotx", "odt" -> IconType.WORD;
            case "xls", "xlsx", "xlt", "xltx", "ods" -> IconType.EXCEL;
            case "ppt", "pptx", "pps", "ppsx", "odp" -> IconType.POWERPOINT;

            // Images
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff", "svg", "ico" -> IconType.IMAGE;

            // Text-ish documents
            case "txt", "md", "log", "rtf", "ini", "cfg", "conf", "csv", "tsv", "json", "xml", "yaml", "yml", "properties" -> IconType.TEXT;

            // Archives
            case "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst" -> IconType.ARCHIVE;

            // Audio/video
            case "mp3", "wav", "flac", "m4a", "ogg", "aac", "wma" -> IconType.AUDIO;
            case "mp4", "mkv", "mov", "avi", "wmv", "webm", "m4v" -> IconType.VIDEO;

            // Code
            case "java", "kt", "kts", "groovy", "scala", "py", "js", "ts", "tsx", "jsx", "c", "h", "cpp", "hpp",
                 "cs", "go", "rs", "swift", "php", "rb", "pl", "sh", "bash", "zsh", "ps1", "sql", "html", "htm",
                 "css", "scss", "less", "vue" -> IconType.CODE;

            // Executables / installers / scripts
            case "exe", "msi", "bat", "cmd", "com", "jar", "app", "apk" -> IconType.EXECUTABLE;

            // Links / shortcuts
            case "lnk", "url", "webloc", "desktop" -> IconType.LINK;

            default -> IconType.FILE;
        };

    }

    // ---------------------------------------------------------------------
    // Resource loading + placeholders
    // ---------------------------------------------------------------------

/**
 * loadUncached.
 *
 * @param type TODO
 * @param darkTheme TODO
 * @param clampedSize TODO
 * @return TODO
 */
    private static Image loadUncached(IconType type, boolean darkTheme, int clampedSize) {
        LogSupport.enter(LOG, "loadUncached");
        String resourceName = resourceNameFor(type, darkTheme, clampedSize);
        Image resourceImage = loadFromResource(resourceName);
        if (resourceImage != null) {
            return resourceImage;
        }
        return drawPlaceholder(type, darkTheme, clampedSize);
    }

/**
 * resourceNameFor.
 *
 * @param type TODO
 * @param darkTheme TODO
 * @param size TODO
 * @return TODO
 */
    private static String resourceNameFor(IconType type, boolean darkTheme, int size) {
        LogSupport.enter(LOG, "resourceNameFor");
        String themeSegment = darkTheme ? "dark" : "light";

        String typeSegment;
        switch (type) {
            case FOLDER      -> typeSegment = "folder";
            case IMAGE       -> typeSegment = "image";
            case TEXT        -> typeSegment = "text";
            case ARCHIVE     -> typeSegment = "archive";
            case AUDIO       -> typeSegment = "audio";
            case VIDEO       -> typeSegment = "video";
            case PDF         -> typeSegment = "pdf";
            case WORD        -> typeSegment = "word";
            case EXCEL       -> typeSegment = "excel";
            case POWERPOINT  -> typeSegment = "powerpoint";
            case CODE        -> typeSegment = "code";
            case EXECUTABLE  -> typeSegment = "exe";
            case LINK        -> typeSegment = "link";
            case FILE        -> typeSegment = "file";
            default          -> typeSegment = "file";
        }

        return "/com/fileexplorer/ui/icons/" + themeSegment + "/" + typeSegment + "-" + size + ".png";
    }

/**
 * loadFromResource.
 *
 * @param resourcePath TODO
 * @return TODO
 */
    private static Image loadFromResource(String resourcePath) {
        LogSupport.enter(LOG, "loadFromResource");
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }

        ResourceAudit.install(IconLoader.class);
        URL url = ResourceAudit.resourceUrl(IconLoader.class, resourcePath);
        if (url == null) {
            return null;
        }

        ResourceAudit.logImageLoaded("IconLoader", resourcePath, url.toExternalForm());

        try (InputStream in = url.openStream()) {
            return new Image(in);
        } catch (Exception ex) {
            return null;
        }
    }

/**
 * drawPlaceholder.
 *
 * @param type TODO
 * @param darkTheme TODO
 * @param size TODO
 * @return TODO
 */
    private static Image drawPlaceholder(IconType type, boolean darkTheme, int size) {
        LogSupport.enter(LOG, "drawPlaceholder");
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();

        Color border = darkTheme ? Color.rgb(200, 200, 200) : Color.rgb(60, 60, 60);
        Color bg = backgroundColorFor(type, darkTheme);
        Color glyph = darkTheme ? Color.WHITE : Color.BLACK;

        int w = size;
        int h = size;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean onBorder = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                pw.setColor(x, y, onBorder ? border : bg);
            }
        }

        int inset = size / 4;
        int gx0 = inset;
        int gy0 = inset;
        int gx1 = w - inset;
        int gy1 = h - inset;

        switch (type) {
            case FOLDER -> {
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
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if (((x + y) & 4) == 0) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case TEXT -> {
                for (int y = gy0; y < gy1; y++) {
                    if ((y - gy0) % 3 == 0) {
                        for (int x = gx0; x < gx1; x++) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case ARCHIVE -> {
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if (((x - gx0) / 3 + (y - gy0) / 3) % 2 == 0) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case AUDIO -> {
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if ((x - gx0) % 4 == 0) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case VIDEO -> {
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        int relY = y - gy0;
                        int relX = x - gx0;
                        int height = gy1 - gy0;
                        int width = gx1 - gx0;
                        boolean inTriangle = relX >= (relY * width / height);
                        if (inTriangle) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case PDF -> {
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if ((x - gx0) % 3 == 0 && (y - gy0) % 3 == 0) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case WORD -> {
                // vertical stripes
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if ((x - gx0) % 3 == 0) pw.setColor(x, y, glyph);
                    }
                }
            }
            case EXCEL -> {
                // grid
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if ((x - gx0) % 4 == 0 || (y - gy0) % 4 == 0) pw.setColor(x, y, glyph);
                    }
                }
            }
            case POWERPOINT -> {
                // diagonal
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if (((x - gx0) - (y - gy0)) % 5 == 0) pw.setColor(x, y, glyph);
                    }
                }
            }
            case CODE -> {
                // brackets-like pattern
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        boolean edge = (x == gx0 || x == gx1 - 1);
                        boolean mid = (y == gy0 || y == gy1 - 1);
                        if (edge || (mid && ((x - gx0) % 2 == 0))) pw.setColor(x, y, glyph);
                    }
                }
            }
            case EXECUTABLE -> {
                // bold X
                int w2 = gx1 - gx0;
                int h2 = gy1 - gy0;
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        int rx = x - gx0;
                        int ry = y - gy0;
                        if (Math.abs(rx - ry) <= 1 || Math.abs((w2 - 1 - rx) - ry) <= 1) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            case LINK -> {
                // chain-ish dots
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if (((x + y) % 6) == 0) pw.setColor(x, y, glyph);
                    }
                }
            }
            case FILE -> {
                for (int y = gy0; y < gy1; y++) {
                    for (int x = gx0; x < gx1; x++) {
                        if ((x - gx0) % 3 == 0 && (y - gy0) % 3 == 0) {
                            pw.setColor(x, y, glyph);
                        }
                    }
                }
            }
            default -> {
                // no-op
            }
        }

        return img;
    }

/**
 * backgroundColorFor.
 *
 * @param type TODO
 * @param darkTheme TODO
 * @return TODO
 */
    private static Color backgroundColorFor(IconType type, boolean darkTheme) {
        LogSupport.enter(LOG, "backgroundColorFor");
        return switch (type) {
            case FOLDER -> darkTheme ? Color.rgb(60, 90, 150) : Color.rgb(255, 220, 140);
            case IMAGE -> darkTheme ? Color.rgb(40, 120, 80) : Color.rgb(180, 230, 190);
            case TEXT -> darkTheme ? Color.rgb(80, 80, 140) : Color.rgb(190, 200, 255);
            case ARCHIVE -> darkTheme ? Color.rgb(120, 90, 40) : Color.rgb(230, 210, 160);
            case AUDIO -> darkTheme ? Color.rgb(80, 40, 120) : Color.rgb(220, 190, 250);
            case VIDEO -> darkTheme ? Color.rgb(40, 80, 140) : Color.rgb(190, 220, 250);
            case PDF -> darkTheme ? Color.rgb(140, 40, 40) : Color.rgb(250, 190, 190);
            case WORD -> darkTheme ? Color.rgb(35, 85, 160) : Color.rgb(190, 220, 255);
            case EXCEL -> darkTheme ? Color.rgb(30, 120, 70) : Color.rgb(190, 250, 210);
            case POWERPOINT -> darkTheme ? Color.rgb(170, 70, 20) : Color.rgb(255, 215, 190);
            case CODE -> darkTheme ? Color.rgb(90, 70, 140) : Color.rgb(220, 210, 255);
            case EXECUTABLE -> darkTheme ? Color.rgb(100, 100, 100) : Color.rgb(230, 230, 230);
            case LINK -> darkTheme ? Color.rgb(40, 120, 140) : Color.rgb(190, 240, 250);
            case FILE -> darkTheme ? Color.rgb(90, 90, 90) : Color.rgb(230, 230, 230);
        };
    }

/**
 * safeIsDirectory.
 *
 * @param p TODO
 * @return TODO
 */
    private static boolean safeIsDirectory(Path p) {
        LogSupport.enter(LOG, "safeIsDirectory");
        try {
            return p != null && Files.isDirectory(p);
        } catch (Exception ex) {
            return false;
        }
    }

/**
 * fileNameOrPath.
 *
 * @param p TODO
 * @return TODO
 */
    private static String fileNameOrPath(Path p) {
        LogSupport.enter(LOG, "fileNameOrPath");
        if (p == null) {
            return "";
        }
        Path fn = p.getFileName();
        return (fn != null) ? fn.toString() : p.toString();
    }

/**
 * extensionLower.
 *
 * @param name TODO
 * @return TODO
 */
    private static String extensionLower(String name) {
        LogSupport.enter(LOG, "extensionLower");
        if (name == null || name.isBlank()) {
            return "";
        }
        int lastSep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        int lastDot = name.lastIndexOf('.');
        if (lastDot <= lastSep || lastDot < 0 || lastDot == name.length() - 1) {
            return "";
        }
        return name.substring(lastDot + 1).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Clamp requested size to nearest standard icon size.
     */
    private static int clampSize(int size) {
        LogSupport.enter(LOG, "clampSize");
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
