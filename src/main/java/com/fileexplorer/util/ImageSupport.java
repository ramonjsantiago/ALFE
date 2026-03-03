package com.fileexplorer.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Centralized helpers for determining whether a file is an image we should attempt to thumbnail.
 */
public final class ImageSupport {

    private ImageSupport() {}

    /** Extensions that JavaFX can decode natively via {@code javafx.scene.image.Image}. */
    private static final Set<String> JAVAFX_NATIVE = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp"
    );

    /** Extensions we will attempt to decode via ImageIO (TwelveMonkeys on classpath expands this). */
    private static final Set<String> IMAGEIO_FALLBACK = Set.of(
            "tif", "tiff", "psd", "ico", "cur",
            "pbm", "pgm", "ppm", "pnm",
            "jpe", "jfif"
    );

    public static boolean isJavaFxNativeExtension(String ext) {
        if (ext == null) return false;
        return JAVAFX_NATIVE.contains(ext.toLowerCase(Locale.ROOT));
    }

    public static boolean isThumbCandidateExtension(String ext) {
        if (ext == null) return false;
        String e = ext.toLowerCase(Locale.ROOT);
        return JAVAFX_NATIVE.contains(e) || IMAGEIO_FALLBACK.contains(e);
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
