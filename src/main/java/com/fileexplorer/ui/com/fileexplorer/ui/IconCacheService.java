package com.fileexplorer.ui;

import javafx.scene.image.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class IconCacheService {

    private final Map<String, Image> iconCache = new HashMap<>();
    private final Image defaultFileIcon;
    private final Image folderIcon;

    public IconCacheService() {
        defaultFileIcon = new Image(getClass().getResource("/icons/file.png").toExternalForm());
        folderIcon = new Image(getClass().getResource("/icons/folder.png").toExternalForm());
    }

    public Image getIcon(Path file) {
        try {
            if (Files.isDirectory(file)) {
                return folderIcon;
            }

            String ext = getExtension(file.getFileName().toString());
            if (iconCache.containsKey(ext)) {
                return iconCache.get(ext);
            }

            // For now, return default for unknown types
            iconCache.put(ext, defaultFileIcon);
            return defaultFileIcon;

        } catch (Exception e) {
            return defaultFileIcon;
        }
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx > 0 && idx < filename.length() - 1) {
            return filename.substring(idx + 1).toLowerCase();
        }
        return "";
    }
}