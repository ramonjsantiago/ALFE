package com.fileexplorer.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * Minimal Windows .cur decoder for JavaFX.
 *
 * Supports:
 *  - CUR entries containing embedded PNG payloads
 *  - 32-bit BI_RGB DIB payloads (BGRA) with AND mask
 *
 * Produces an ImageCursor with correct hotspot.
 */
public final class CurImageLoader {

    private static final byte[] PNG_SIG = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private final Map<String, ImageCursor> cursorCache = new ConcurrentHashMap<>();

    public ImageCursor loadCursorFromResource(String resourcePath, int targetPx) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        String key = resourcePath + "#t" + Math.max(1, targetPx);
        ImageCursor cached = cursorCache.get(key);
        if (cached != null) {
            return cached;
        }

        byte[] bytes = readAllBytes(resourcePath);
        if (bytes == null || bytes.length < 6) {
            return null;
        }

        CursorPayload payload = decodeCur(bytes, targetPx);
        if (payload == null || payload.image == null) {
            return null;
        }

        ImageCursor cursor = new ImageCursor(payload.image, payload.hotspotX, payload.hotspotY);
        cursorCache.put(key, cursor);
        return cursor;
    }

    private byte[] readAllBytes(String resourcePath) {
        String rp = resourcePath.startsWith("/") ? resourcePath : ("/" + resourcePath);
        try (InputStream in = CurImageLoader.class.getResourceAsStream(rp)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) >= 0) {
                bos.write(buf, 0, r);
            }
            return bos.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }

    private CursorPayload decodeCur(byte[] curBytes, int targetPx) {
        ByteBuffer bb = ByteBuffer.wrap(curBytes).order(ByteOrder.LITTLE_ENDIAN);

        int reserved = u16(bb);
        int type = u16(bb);   // 2 for CUR
        int count = u16(bb);

        if (reserved != 0 || type != 2 || count <= 0) {
            return null;
        }

        Entry best = null;

        for (int i = 0; i < count; i++) {
            int w = u8(bb);
            int h = u8(bb);
            bb.get(); // colorCount
            bb.get(); // reserved
            int hotX = u16(bb);
            int hotY = u16(bb);
            int bytesInRes = bb.getInt();
            int imageOffset = bb.getInt();

            if (w == 0) w = 256;
            if (h == 0) h = 256;

            Entry e = new Entry(w, h, hotX, hotY, bytesInRes, imageOffset);

            // Pick entry closest to target but prefer >= target (sharper downscale).
            if (best == null) {
                best = e;
            } else {
                best = pickBetter(best, e, targetPx);
            }
        }

        if (best == null) {
            return null;
        }

        if (best.offset < 0 || best.size <= 0 || best.offset + best.size > curBytes.length) {
            return null;
        }

        byte[] payload = Arrays.copyOfRange(curBytes, best.offset, best.offset + best.size);

        // If embedded PNG:
        if (payload.length >= PNG_SIG.length && isPng(payload)) {
            Image img = new Image(new ByteArrayInputStream(payload));
            // Hotspot is defined in CUR directory and already in pixel coordinates.
            return new CursorPayload(img, clampHotspot(best.hotX, img.getWidth()), clampHotspot(best.hotY, img.getHeight()));
        }

        // Otherwise DIB (no BMP file header).
        CursorPayload dib = decodeDib(payload, best.hotX, best.hotY);
        if (dib == null) {
            return null;
        }
        return dib;
    }

    private static Entry pickBetter(Entry a, Entry b, int targetPx) {
        int ta = scoreEntry(a, targetPx);
        int tb = scoreEntry(b, targetPx);
        if (tb < ta) return b;
        if (ta < tb) return a;

        // Tie-breaker: larger area
        int aa = a.w * a.h;
        int ba = b.w * b.h;
        return (ba > aa) ? b : a;
    }

    /**
     * Lower score is better.
     * Prefer sizes >= target (downscale) over < target (upscale).
     */
    private static int scoreEntry(Entry e, int targetPx) {
        int size = Math.max(e.w, e.h);
        if (size >= targetPx) {
            return (size - targetPx);              // small downscale preferred
        }
        return 10_000 + (targetPx - size);         // penalize upscales heavily
    }

    private static boolean isPng(byte[] data) {
        for (int i = 0; i < PNG_SIG.length; i++) {
            if (data[i] != PNG_SIG[i]) return false;
        }
        return true;
    }

    private CursorPayload decodeDib(byte[] dib, int hotX, int hotY) {
        if (dib.length < 40) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(dib).order(ByteOrder.LITTLE_ENDIAN);

        int headerSize = bb.getInt();
        if (headerSize < 40) {
            return null;
        }

        int width = bb.getInt();
        int heightAll = bb.getInt(); // XOR+AND => actual height is half
        int planes = u16(bb);
        int bitCount = u16(bb);
        int compression = bb.getInt();
        bb.getInt(); // imageSize
        bb.getInt(); // xPelsPerMeter
        bb.getInt(); // yPelsPerMeter
        bb.getInt(); // clrUsed
        bb.getInt(); // clrImportant

        if (planes != 1) {
            return null;
        }
        if (compression != 0) { // BI_RGB only
            return null;
        }

        int height = Math.abs(heightAll) / 2;
        if (width <= 0 || height <= 0) {
            return null;
        }

        int pixelOffset = headerSize;
        if (pixelOffset < 40 || pixelOffset > dib.length) {
            return null;
        }

        if (bitCount != 32) {
            // Your aero_*.cur set uses 32-bit; add 24/8/4/1-bit support if needed later.
            return null;
        }

        Image img = decode32BitDib(dib, pixelOffset, width, height);

        double hx = clampHotspot(hotX, img.getWidth());
        double hy = clampHotspot(hotY, img.getHeight());
        return new CursorPayload(img, hx, hy);
    }

    private Image decode32BitDib(byte[] dib, int pixelOffset, int width, int height) {
        int rowBytes = width * 4;
        int pixelBytes = rowBytes * height;
        int andMaskOffset = pixelOffset + pixelBytes;

        if (andMaskOffset > dib.length) {
            return null;
        }

        int[] argb = new int[width * height];

        // XOR bitmap: bottom-up, BGRA.
        for (int y = 0; y < height; y++) {
            int srcY = height - 1 - y;
            int rowStart = pixelOffset + srcY * rowBytes;
            for (int x = 0; x < width; x++) {
                int i = rowStart + x * 4;
                int b = dib[i] & 0xFF;
                int g = dib[i + 1] & 0xFF;
                int r = dib[i + 2] & 0xFF;
                int a = dib[i + 3] & 0xFF;
                argb[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        // AND mask: 1bpp, padded to 32-bit boundaries per row. 1 => transparent.
        int maskRowBytes = ((width + 31) / 32) * 4;
        int maskBytes = maskRowBytes * height;
        if (andMaskOffset + maskBytes <= dib.length) {
            for (int y = 0; y < height; y++) {
                int srcY = height - 1 - y;
                int rowStart = andMaskOffset + srcY * maskRowBytes;

                for (int x = 0; x < width; x++) {
                    int byteIndex = rowStart + (x / 8);
                    int bit = 7 - (x % 8);
                    int m = (dib[byteIndex] >> bit) & 0x01;
                    if (m == 1) {
                        argb[y * width + x] = argb[y * width + x] & 0x00FFFFFF;
                    }
                }
            }
        }

        WritableImage img = new WritableImage(width, height);
        Objects.requireNonNull(img.getPixelWriter())
                .setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        return img;
    }

    private static double clampHotspot(double v, double max) {
        if (max <= 0) return 0.0;
        if (v < 0) return 0.0;
        if (v > max - 1) return max - 1;
        return v;
    }

    private static int u8(ByteBuffer bb) {
        return bb.get() & 0xFF;
    }

    private static int u16(ByteBuffer bb) {
        return bb.getShort() & 0xFFFF;
    }

    private static final class Entry {
        final int w;
        final int h;
        final int hotX;
        final int hotY;
        final int size;
        final int offset;

        Entry(int w, int h, int hotX, int hotY, int size, int offset) {
            this.w = w;
            this.h = h;
            this.hotX = hotX;
            this.hotY = hotY;
            this.size = size;
            this.offset = offset;
        }
    }

    private static final class CursorPayload {
        final Image image;
        final double hotspotX;
        final double hotspotY;

        CursorPayload(Image image, double hotspotX, double hotspotY) {
            this.image = image;
            this.hotspotX = hotspotX;
            this.hotspotY = hotspotY;
        }
    }
}
