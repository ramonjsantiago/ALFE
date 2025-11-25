package com.fileexplorer.ui;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThumbnailCache {
    private final int maxStrongEntries;
    private final LinkedHashMap<Path, Image> strongCache;
    private final Map<Path, SoftReference<Image>> softCache;

    public ThumbnailCache(int maxStrongEntries) {
        this.maxStrongEntries = maxStrongEntries;
        this.strongCache = new LinkedHashMap<>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<Path, Image> eldest) {
                boolean remove = size() > ThumbnailCache.this.maxStrongEntries;
                if (remove) softCache.put(eldest.getKey(), new SoftReference<>(eldest.getValue()));
                return remove;
            }
        };
        this.softCache = new LinkedHashMap<>();
    }

    public synchronized void put(Path p, Image img) {
        strongCache.put(p, img);
    }

    public synchronized Image get(Path p) {
        Image img = strongCache.get(p);
        if (img != null) return img;
        SoftReference<Image> ref = softCache.get(p);
        return ref != null ? ref.get() : null;
    }
}
