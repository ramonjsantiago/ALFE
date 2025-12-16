package com.fileexplorer.ui;

import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PreviewCacheService
 *  - Caches image, PDF, and other previews
 *  - Improves performance for repeated preview requests
 */
public class PreviewCacheService {

    private final Map<Path, Image> imageCache = new ConcurrentHashMap<>();

    public void put(Path file, Image preview) {
        imageCache.put(file, preview);
    }

    public Image get(Path file) {
        return imageCache.get(file);
    }

    public boolean contains(Path file) {
        return imageCache.containsKey(file);
    }

    public void clear() {
        imageCache.clear();
    }
}