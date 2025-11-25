package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * MetadataService:
 *  - Provides basic file metadata
 *  - Lightweight and synchronous
 *  - Used by DetailsView, PreviewPane, and right-side metadata panels
 *
 * Future extensions:
 *  - Image EXIF extraction
 *  - Audio metadata (ID3)
 *  - PDF info
 *  - Hashing for integrity info
 */
public class MetadataService {

    public Map<String, Object> readMetadata(Path path) {
        Map<String, Object> meta = new HashMap<>();

        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

            meta.put("isDirectory", attrs.isDirectory());
            meta.put("isRegularFile", attrs.isRegularFile());
            meta.put("size", attrs.size());
            meta.put("created", Instant.ofEpochMilli(attrs.creationTime().toMillis()));
            meta.put("modified", Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()));
            meta.put("accessed", Instant.ofEpochMilli(attrs.lastAccessTime().toMillis()));

            String type = Files.probeContentType(path);
            meta.put("contentType", type != null ? type : "unknown");

            try {
                meta.put("fileName", path.getFileName() != null ? path.getFileName().toString() : "");
                meta.put("absolutePath", path.toAbsolutePath().toString());
            } catch (Exception e) {
                meta.put("fileName", "");
                meta.put("absolutePath", "");
            }

        } catch (IOException e) {
            meta.put("error", "Unable to read metadata");
        }

        return meta;
    }
}
