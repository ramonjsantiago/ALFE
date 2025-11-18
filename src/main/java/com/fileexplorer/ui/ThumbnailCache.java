package com.fileexplorer.ui;

import javafx.scene.image.Image;
import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThumbnailCache {

    private final int maxStrongEntries;
    private final LinkedHashMap<String, Image> strongCache;
    private final Map<String, SoftReference<Image>> softCache;

    public ThumbnailCache(int maxStrongEntries) {
        this.maxStrongEntries = maxStrongEntries;
        this.strongCache = new LinkedHashMap<>(maxStrongEntries, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                if (size() > ThumbnailCache.this.maxStrongEntries) {
                    softCache.put(eldest.getKey(), new SoftReference<>(eldest.getValue()));
                    return true;
                }
                return false;
            }
        };
        this.softCache = new LinkedHashMap<>();
    }

    public synchronized void put(String key, Image img) {
        strongCache.put(key, img);
    }

    public synchronized Image get(String key) {
        Image img = strongCache.get(key);
        if (img != null) return img;
        SoftReference<Image> ref = softCache.get(key);
        if (ref != null) {
            img = ref.get();
            if (img != null) {
                strongCache.put(key, img);
                softCache.remove(key);
                return img;
            } else {
                softCache.remove(key);
            }
        }
        return null;
    }

    public synchronized void clear() {
        strongCache.clear();
        softCache.clear();
    }
}
