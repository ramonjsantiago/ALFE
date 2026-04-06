package com.fileexplorer.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized helpers for determining whether a file is a thumbnail candidate we should attempt to render.
 */
public final class ImageSupport {

    private ImageSupport() {}

    /** Extensions that JavaFX can decode natively via {@code javafx.scene.image.Image}. */
    private static final Set<String> JAVAFX_NATIVE = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp"
    );

    /** Extensions we will attempt to decode via ImageIO (TwelveMonkeys + JAI Image I/O Tools + NightMonkeys on the classpath expand this). */
    private static final Set<String> IMAGEIO_FALLBACK = Set.of(
            "tif", "tiff", "psd", "ico", "cur",
            "pbm", "pgm", "ppm", "pnm",
            "jpe", "jfif", "pcx",
            "webp", "avif", "heif", "heic"
    );

    /** Extensions handled via thumbnails4j document renderers. */
    private static final Set<String> DOCUMENT_THUMBNAILS = Set.of(
            "doc", "docx", "pdf", "pptx", "xls", "xlsx"
    );

    private static final Map<String, List<String>> FORMAT_NAME_ALIASES = Map.of(
            "jpg", List.of("JPEG", "JPG"),
            "jpe", List.of("JPEG", "JPG"),
            "jfif", List.of("JPEG", "JPG"),
            "tif", List.of("TIFF", "TIF"),
            "tiff", List.of("TIFF", "TIF"),
            "webp", List.of("WEBP"),
            "avif", List.of("AVIF", "HEIF"),
            "heif", List.of("HEIF", "AVIF"),
            "heic", List.of("HEIC", "HEIF", "AVIF")
    );

    private static final Map<String, List<String>> MIME_TYPE_ALIASES = Map.of(
            "webp", List.of("image/webp"),
            "avif", List.of("image/avif"),
            "heif", List.of("image/heif", "image/heic"),
            "heic", List.of("image/heic", "image/heif")
    );

    private static final List<String> PREFERRED_THUMB_READER_EXTENSIONS = List.of(
            "webp", "avif", "heif", "heic",
            "tif", "tiff", "psd", "ico", "pcx"
    );

    private static final ConcurrentHashMap<String, Boolean> IMAGEIO_READER_CACHE = new ConcurrentHashMap<>();

    public static boolean isJavaFxNativeExtension(String ext) {
        if (ext == null) return false;
        return JAVAFX_NATIVE.contains(ext.toLowerCase(Locale.ROOT));
    }

    public static boolean isDocumentThumbnailExtension(String ext) {
        if (ext == null) return false;
        return DOCUMENT_THUMBNAILS.contains(ext.toLowerCase(Locale.ROOT));
    }

    public static boolean isImageIoManagedExtension(String ext) {
        if (ext == null) return false;
        return IMAGEIO_FALLBACK.contains(ext.toLowerCase(Locale.ROOT));
    }

    public static boolean isThumbCandidateExtension(String ext) {
        if (ext == null) return false;
        String e = ext.toLowerCase(Locale.ROOT);
        return JAVAFX_NATIVE.contains(e) || IMAGEIO_FALLBACK.contains(e) || DOCUMENT_THUMBNAILS.contains(e);
    }

    public static String extensionOf(Path p) {
        if (p == null) return null;
        Path name = p.getFileName();
        String s = (name != null) ? name.toString() : p.toString();
        int dot = s.lastIndexOf('.');
        if (dot <= 0 || dot >= s.length() - 1) return null;
        String ext = s.substring(dot + 1).trim();
        return ext.isEmpty() ? null : ext.toLowerCase(Locale.ROOT);
    }

    public static boolean isThumbCandidate(Path p) {
        return isThumbCandidateExtension(extensionOf(p));
    }

    /**
     * Refresh ImageIO SPI discovery and clear cached capability probes.
     */
    public static void scanForPlugins() {
        try {
            ImageIO.scanForPlugins();
        } catch (Throwable ignored) {
            // best effort
        } finally {
            IMAGEIO_READER_CACHE.clear();
        }
    }

    /**
     * Warm the preferred thumbnail-reader capability probes so request-time checks stay cheap.
     */
    public static void primePreferredThumbnailReaderCapabilities() {
        for (String ext : PREFERRED_THUMB_READER_EXTENSIONS) {
            hasImageReaderForExtension(ext);
        }
    }

    /**
     * Returns whether ImageIO currently has a registered reader for the given extension.
     */
    public static boolean hasImageReaderForExtension(String ext) {
        if (ext == null || ext.isBlank()) {
            return false;
        }
        String normalized = ext.toLowerCase(Locale.ROOT);
        return IMAGEIO_READER_CACHE.computeIfAbsent(normalized, ImageSupport::detectReaderSupport);
    }

    /**
     * Returns a stable capability snapshot for the preferred non-native thumbnail formats.
     */
    public static Map<String, Boolean> preferredThumbnailReaderSupportSnapshot() {
        LinkedHashMap<String, Boolean> snapshot = new LinkedHashMap<>();
        for (String ext : PREFERRED_THUMB_READER_EXTENSIONS) {
            snapshot.put(ext, hasImageReaderForExtension(ext));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    private static boolean detectReaderSupport(String normalizedExt) {
        if (hasImageReaderBySuffix(normalizedExt)) {
            return true;
        }
        if (hasImageReaderByFormatName(normalizedExt)) {
            return true;
        }
        for (String alias : FORMAT_NAME_ALIASES.getOrDefault(normalizedExt, List.of())) {
            if (hasImageReaderByFormatName(alias)) {
                return true;
            }
        }
        for (String mimeType : MIME_TYPE_ALIASES.getOrDefault(normalizedExt, List.of())) {
            if (hasImageReaderByMimeType(mimeType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasImageReaderBySuffix(String suffix) {
        try {
            return ImageIO.getImageReadersBySuffix(suffix).hasNext();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasImageReaderByFormatName(String formatName) {
        try {
            return ImageIO.getImageReadersByFormatName(formatName).hasNext();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasImageReaderByMimeType(String mimeType) {
        try {
            return ImageIO.getImageReadersByMIMEType(mimeType).hasNext();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Scale down an Image to fit within {@code maxSizePx} while preserving aspect ratio.
     * Never upscales. Best-effort; returns the original image on failure.
     *
     * <p>This is intended for background-thread usage in thumbnail pipelines; it uses
     * AWT scaling via {@link SwingFXUtils}.</p>
     */
    public static Image scaleDownPreservingAspect(Image img, int maxSizePx) {
        if (img == null) return null;
        int box = Math.max(1, maxSizePx);

        double w = img.getWidth();
        double h = img.getHeight();
        if (w <= 0 || h <= 0) return img;
        if (w <= box && h <= box) return img;

        double scale = Math.min((double) box / w, (double) box / h);
        if (scale >= 1.0) return img;

        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));

        try {
            BufferedImage src = SwingFXUtils.fromFXImage(img, null);
            if (src == null) return img;

            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(src, 0, 0, tw, th, null);
            } finally {
                g.dispose();
            }

            WritableImage wi = new WritableImage(tw, th);
            return SwingFXUtils.toFXImage(out, wi);
        } catch (Throwable t) {
            return img;
        }
    }
}
