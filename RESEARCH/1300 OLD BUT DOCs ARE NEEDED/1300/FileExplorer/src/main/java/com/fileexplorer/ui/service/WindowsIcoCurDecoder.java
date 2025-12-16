package com.fileexplorer.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

public final class WindowsIcoCurDecoder {

    private static final byte[] PNG_SIG = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private WindowsIcoCurDecoder() {
    }

    public static Image decodeBestImageFromIcoOrCur(byte[] containerBytes, int desiredPx) {
        if (containerBytes == null || containerBytes.length < 6) {
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(containerBytes).order(ByteOrder.LITTLE_ENDIAN);
        int reserved = u16(bb.getShort(0));
        int type = u16(bb.getShort(2));   // 1=ICO, 2=CUR
        int count = u16(bb.getShort(4));

        if (reserved != 0 || (type != 1 && type != 2) || count <= 0) {
            return null;
        }

        Entry best = selectBestEntry(bb, count, desiredPx);
        if (best == null) {
            return null;
        }

        byte[] imageBytes = slice(containerBytes, best.imageOffset, best.bytesInRes);
        if (imageBytes == null) {
            return null;
        }

        // If the image data is PNG, JavaFX can decode it directly.
        if (startsWith(imageBytes, PNG_SIG)) {
            return new Image(new ByteArrayInputStream(imageBytes));
        }

        // Otherwise, it's typically a DIB (BITMAPINFOHEADER + pixel data + AND mask)
        try {
            return decodeDibAsFxImage(imageBytes);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static Cursor decodeBestCursorFromCur(byte[] curBytes, int desiredPx, Cursor fallback) {
        if (curBytes == null || curBytes.length < 6) {
            return fallback;
        }

        ByteBuffer bb = ByteBuffer.wrap(curBytes).order(ByteOrder.LITTLE_ENDIAN);
        int reserved = u16(bb.getShort(0));
        int type = u16(bb.getShort(2));   // 2=CUR
        int count = u16(bb.getShort(4));

        if (reserved != 0 || type != 2 || count <= 0) {
            return fallback;
        }

        Entry best = selectBestEntry(bb, count, desiredPx);
        if (best == null) {
            return fallback;
        }

        byte[] imageBytes = slice(curBytes, best.imageOffset, best.bytesInRes);
        if (imageBytes == null) {
            return fallback;
        }

        Image img;
        if (startsWith(imageBytes, PNG_SIG)) {
            img = new Image(new ByteArrayInputStream(imageBytes));
        } else {
            img = decodeDibAsFxImage(imageBytes);
        }

        if (img == null) {
            return fallback;
        }

        double hotX = best.hotspotX;
        double hotY = best.hotspotY;
        return new ImageCursor(img, hotX, hotY);
    }

    private static Entry selectBestEntry(ByteBuffer bb, int count, int desiredPx) {
        // ICO/CUR directory entries start at offset 6, 16 bytes each.
        int base = 6;

        Entry chosen = null;

        for (int i = 0; i < count; i++) {
            int off = base + (i * 16);
            if (off + 16 > bb.capacity()) {
                break;
            }

            int w = u8(bb.get(off));
            int h = u8(bb.get(off + 1));
            if (w == 0) {
                w = 256;
            }
            if (h == 0) {
                h = 256;
            }

            // For ICO: planes & bitcount. For CUR: hotspotX & hotspotY.
            int w1 = u16(bb.getShort(off + 4));
            int w2 = u16(bb.getShort(off + 6));

            long bytesInRes = u32(bb.getInt(off + 8));
            long imageOffset = u32(bb.getInt(off + 12));

            Entry e = new Entry();
            e.width = w;
            e.height = h;
            e.bytesInRes = bytesInRes;
            e.imageOffset = imageOffset;
            e.hotspotX = w1; // correct for CUR; harmless for ICO
            e.hotspotY = w2;

            // Choose the smallest icon >= desiredPx; otherwise the largest available.
            boolean fits = (w >= desiredPx && h >= desiredPx);
            if (chosen == null) {
                chosen = e;
                continue;
            }

            boolean chosenFits = (chosen.width >= desiredPx && chosen.height >= desiredPx);

            if (fits && !chosenFits) {
                chosen = e;
                continue;
            }

            if (fits && chosenFits) {
                int area = w * h;
                int chosenArea = chosen.width * chosen.height;
                if (area < chosenArea) {
                    chosen = e;
                }
                continue;
            }

            if (!fits && !chosenFits) {
                int area = w * h;
                int chosenArea = chosen.width * chosen.height;
                if (area > chosenArea) {
                    chosen = e;
                }
            }
        }

        return chosen;
    }

    private static Image decodeDibAsFxImage(byte[] dibBytes) {
        if (dibBytes == null || dibBytes.length < 40) {
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(dibBytes).order(ByteOrder.LITTLE_ENDIAN);

        int headerSize = bb.getInt(0);
        if (headerSize < 40 || dibBytes.length < headerSize) {
            return null;
        }

        int width = bb.getInt(4);
        int heightTotal = bb.getInt(8);
        int planes = u16(bb.getShort(12));
        int bpp = u16(bb.getShort(14));
        int compression = bb.getInt(16);

        if (width <= 0 || heightTotal == 0 || planes < 1) {
            return null;
        }

        // In ICO/CUR DIBs, height is stored as (XOR height + AND height) => double the visible height.
        int absHeightTotal = Math.abs(heightTotal);
        int height = absHeightTotal / 2;
        if (height <= 0) {
            height = absHeightTotal;
        }

        // Only implement 32bpp BI_RGB (and optionally BI_BITFIELDS treated as 32bpp).
        if (bpp != 32) {
            return null;
        }
        if (compression != 0 && compression != 3) {
            return null;
        }

        int pixelOffset = headerSize; // no palette for 32bpp
        int rowStride = width * 4;    // already 4-byte aligned
        int xorBytes = rowStride * height;

        if (pixelOffset + xorBytes > dibBytes.length) {
            return null;
        }

        int maskOffset = pixelOffset + xorBytes;
        int maskRowStride = ((width + 31) / 32) * 4;
        int maskBytes = maskRowStride * height;
        boolean hasMask = (maskOffset + maskBytes) <= dibBytes.length;

        WritableImage out = new WritableImage(width, height);
        PixelWriter pw = out.getPixelWriter();

        boolean bottomUp = heightTotal > 0;

        // Heuristic: if all alpha bytes are 0, use AND mask for transparency.
        boolean allAlphaZero = true;
        for (int i = 3; i < xorBytes; i += 4) {
            int a = u8(dibBytes[pixelOffset + i]);
            if (a != 0) {
                allAlphaZero = false;
                break;
            }
        }

        for (int y = 0; y < height; y++) {
            int srcY = bottomUp ? (height - 1 - y) : y;
            int rowBase = pixelOffset + (srcY * rowStride);

            for (int x = 0; x < width; x++) {
                int p = rowBase + (x * 4);
                int b = u8(dibBytes[p + 0]);
                int g = u8(dibBytes[p + 1]);
                int r = u8(dibBytes[p + 2]);
                int a = u8(dibBytes[p + 3]);

                if (allAlphaZero && hasMask) {
                    // AND mask bit 1 => transparent.
                    int maskRow = maskOffset + (srcY * maskRowStride);
                    int byteIndex = maskRow + (x / 8);
                    int bit = 7 - (x % 8);
                    int m = (u8(dibBytes[byteIndex]) >> bit) & 1;
                    a = (m == 1) ? 0 : 255;
                }

                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                pw.setArgb(x, y, argb);
            }
        }

        return out;
    }

    private static int u8(byte b) {
        return b & 0xFF;
    }

    private static int u16(short s) {
        return s & 0xFFFF;
    }

    private static long u32(int i) {
        return i & 0xFFFFFFFFL;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || prefix == null || data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] slice(byte[] src, long off, long len) {
        if (src == null) {
            return null;
        }
        if (off < 0 || len <= 0 || off + len > src.length) {
            return null;
        }
        byte[] out = new byte[(int) len];
        System.arraycopy(src, (int) off, out, 0, (int) len);
        return out;
    }

    private static final class Entry {
        private int width;
        private int height;
        private long bytesInRes;
        private long imageOffset;
        private int hotspotX;
        private int hotspotY;
    }
}
