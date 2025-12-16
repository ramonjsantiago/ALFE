package com.fileexplorer.ui;

import javafx.scene.image.Image;
import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThumbnailCache {
    private java.util.Map<String, javafx.scene.image.Image> strongCache = new java.util.LinkedHashMap<String, javafx.scene.image.Image>(16,0.75f,true) {
        protected boolean removeEldestEntry(java.util.Map.Entry<String, javafx.scene.image.Image> eldest) {
            return size() > 100; // max 100 strong entries
        }
    };
    private java.util.Map<String, java.lang.ref.SoftReference<javafx.scene.image.Image>> softCache = new java.util.HashMap<>();

    public javafx.scene.image.Image get(String path) {
        javafx.scene.image.Image img = strongCache.get(path);
        if (img != null) return img;
        java.lang.ref.SoftReference<javafx.scene.image.Image> ref = softCache.get(path);
        if (ref != null) { img = ref.get(); if (img != null) return img; }
        return null;
    }

    public void put(String path, javafx.scene.image.Image img) {
        strongCache.put(path, img);
        softCache.put(path, new java.lang.ref.SoftReference<>(img));
    }

    private final int maxStrongEntries;
//    private final LinkedHashMap<String, Image> strongCache;
//    private final Map<String, SoftReference<Image>> softCache;

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

//    public synchronized void put(String key, Image img) {
//        strongCache.put(key, img);
//    }
//
//    public synchronized Image get(String key) {
//        Image img = strongCache.get(key);
//        if (img != null) return img;
//        SoftReference<Image> ref = softCache.get(key);
//        if (ref != null) {
//            img = ref.get();
//            if (img != null) {
//                strongCache.put(key, img);
//                softCache.remove(key);
//                return img;
//            } else {
//                softCache.remove(key);
//            }
//        }
//        return null;
//    }

    public synchronized void clear() {
        strongCache.clear();
        softCache.clear();
    }
}
