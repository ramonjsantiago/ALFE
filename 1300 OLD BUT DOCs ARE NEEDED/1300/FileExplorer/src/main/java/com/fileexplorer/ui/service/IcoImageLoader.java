package com.fileexplorer.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * Minimal ICO loader for JavaFX.
 *
 * Supports:
 *  - ICO entries containing embedded PNG data (common for modern .ico)
 *  - 32-bit DIB/BMP entries (BGRA) as fallback
 *
 * If an ICO contains only uncommon formats (paletted, RLE, etc) it will fall back to null.
 */
public final class IcoImageLoader {

    private static final byte[] PNG_SIG = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private final Map<String, Image> cache = new ConcurrentHashMap<>();

    public Image loadFromResource(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        Image cached = cache.get(resourcePath);
        if (cached != null) {
            return cached;
        }

        byte[] bytes = readAllBytes(resourcePath);
        if (bytes == null || bytes.length < 6) {
            return null;
        }

        Image img = decodeIco(bytes);
        if (img != null) {
            cache.put(resourcePath, img);
        }
        return img;
    }

    private byte[] readAllBytes(String resourcePath) {
        String rp = resourcePath.startsWith("/") ? resourcePath : ("/" + resourcePath);
        try (InputStream in = IcoImageLoader.class.getResourceAsStream(rp)) {
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

    private Image decodeIco(byte[] icoBytes) {
        ByteBuffer bb = ByteBuffer.wrap(icoBytes).order(ByteOrder.LITTLE_ENDIAN);

        int reserved = u16(bb);
        int type = u16(bb);
        int count = u16(bb);

        if (reserved != 0 || (type != 1 && type != 2) || count <= 0) {
            return null;
        }

        // Choose "best" entry: prefer largest (most likely highest quality),
        // which JavaFX can scale down cleanly for small icons.
        int bestIndex = -1;
        int bestArea = -1;

        int[] widths = new int[count];
        int[] heights = new int[count];
        int[] sizes = new int[count];
        int[] offsets = new int[count];

        for (int i = 0; i < count; i++) {
            int w = u8(bb);
            int h = u8(bb);
            bb.get(); // colorCount
            bb.get(); // reserved
            u16(bb);  // planes (or hotspotX for CUR)
            u16(bb);  // bitCount (or hotspotY for CUR)
            int bytesInRes = bb.getInt();
            int imageOffset = bb.getInt();

            if (w == 0) w = 256;
            if (h == 0) h = 256;

            widths[i] = w;
            heights[i] = h;
            sizes[i] = bytesInRes;
            offsets[i] = imageOffset;

            int area = w * h;
            if (area > bestArea) {
                bestArea = area;
                bestIndex = i;
            }
        }

        if (bestIndex < 0) {
            return null;
        }

        int off = offsets[bestIndex];
        int len = sizes[bestIndex];
        if (off < 0 || len <= 0 || off + len > icoBytes.length) {
            return null;
        }

        byte[] payload = Arrays.copyOfRange(icoBytes, off, off + len);

        // Embedded PNG?
        if (payload.length >= 8 && isPng(payload)) {
            return new Image(new ByteArrayInputStream(payload));
        }

        // Otherwise DIB/BMP without BMP file header.
        return decodeDibToFxImage(payload);
    }

    private static boolean isPng(byte[] data) {
        if (data.length < PNG_SIG.length) return false;
        for (int i = 0; i < PNG_SIG.length; i++) {
            if (data[i] != PNG_SIG[i]) return false;
        }
        return true;
    }

    private Image decodeDibToFxImage(byte[] dib) {
        if (dib.length < 40) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(dib).order(ByteOrder.LITTLE_ENDIAN);

        int headerSize = bb.getInt();
        if (headerSize < 40) {
            return null;
        }
        int width = bb.getInt();
        int heightAll = bb.getInt(); // includes XOR+AND, so actual height is half for ICO DIB
        int planes = u16(bb);
        int bitCount = u16(bb);
        int compression = bb.getInt();
        bb.getInt(); // imageSize (may be 0)
        bb.getInt(); // xPelsPerMeter
        bb.getInt(); // yPelsPerMeter
        bb.getInt(); // clrUsed
        bb.getInt(); // clrImportant

        if (planes != 1) {
            // uncommon; still might work, but keep strict
            return null;
        }
        if (compression != 0) {
            // BI_RGB only
            return null;
        }

        int height = Math.abs(heightAll) / 2;
        if (width <= 0 || height <= 0) {
            return null;
        }

        // Pixel data begins after header (and color table if present; not handled here for paletted formats).
        int pixelOffset = headerSize;
        if (pixelOffset < 40 || pixelOffset > dib.length) {
            return null;
        }

        if (bitCount == 32) {
            return decode32BitDib(dib, pixelOffset, width, height);
        }

        // If your icon set includes only PNG-in-ICO (common), you won't hit this.
        // Add 24-bit support later if needed.
        return null;
    }

    private Image decode32BitDib(byte[] dib, int pixelOffset, int width, int height) {
        // Rows are bottom-up, 4 bytes per pixel.
        int rowBytes = width * 4;
        int pixelBytes = rowBytes * height;
        int andMaskOffset = pixelOffset + pixelBytes;

        if (andMaskOffset > dib.length) {
            return null;
        }

        int[] argb = new int[width * height];

        for (int y = 0; y < height; y++) {
            int srcY = height - 1 - y; // bottom-up
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

        // AND mask: 1 bpp, padded to 32-bit boundaries per row.
        int maskRowBytes = ((width + 31) / 32) * 4;
        int maskBytes = maskRowBytes * height;
        if (andMaskOffset + maskBytes <= dib.length) {
            for (int y = 0; y < height; y++) {
                int srcY = height - 1 - y; // bottom-up
                int rowStart = andMaskOffset + srcY * maskRowBytes;
                for (int x = 0; x < width; x++) {
                    int byteIndex = rowStart + (x / 8);
                    int bit = 7 - (x % 8);
                    int m = (dib[byteIndex] >> bit) & 0x01;
                    if (m == 1) {
                        // transparent
                        argb[y * width + x] = argb[y * width + x] & 0x00FFFFFF;
                    }
                }
            }
        }

        WritableImage img = new WritableImage(width, height);
        img.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        return img;
    }

    private static int u8(ByteBuffer bb) {
        return bb.get() & 0xFF;
    }

    private static int u16(ByteBuffer bb) {
        return bb.getShort() & 0xFFFF;
    }
}
