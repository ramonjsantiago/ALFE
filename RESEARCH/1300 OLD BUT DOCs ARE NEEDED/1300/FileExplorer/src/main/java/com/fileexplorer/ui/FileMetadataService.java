package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Optional;

/**
 * Helper for retrieving file system metadata with sensible fallbacks.
 */
public final class FileMetadataService {

    public static final class FileMetadata {
        private final Path path;
        private final boolean directory;
        private final long size;
        private final Instant created;
        private final Instant modified;
        private final String type;

        public FileMetadata(Path path,
                            boolean directory,
                            long size,
                            Instant created,
                            Instant modified,
                            String type) {
            this.path = path;
            this.directory = directory;
            this.size = size;
            this.created = created;
            this.modified = modified;
            this.type = type;
        }

        public Path getPath() {
            return path;
        }

        public boolean isDirectory() {
            return directory;
        }

        public long getSize() {
            return size;
        }

        public Instant getCreated() {
            return created;
        }

        public Instant getModified() {
            return modified;
        }

        public String getType() {
            return type;
        }
    }

    public Optional<FileMetadata> read(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            boolean dir = attrs.isDirectory();
            long size = dir ? 0L : attrs.size();
            Instant created = attrs.creationTime().toInstant();
            Instant modified = attrs.lastModifiedTime().toInstant();
            String type = dir ? "File folder" : detectFileType(path);
            return Optional.of(new FileMetadata(path, dir, size, created, modified, type));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    /**
     * Detects a human-friendly file type string (e.g. "PNG image", "Text document").
     */
    public String detectFileType(Path file) {
        if (file == null) {
            return "";
        }
        String fileName = file.getFileName() != null ? file.getFileName().toString() : "";
        String mime = null;
        try {
            mime = Files.probeContentType(file);
        } catch (IOException ignored) {
        }

        if (mime == null) {
            int idx = fileName.lastIndexOf('.');
            if (idx > 0 && idx < fileName.length() - 1) {
                String ext = fileName.substring(idx + 1).toUpperCase();
                return ext + " file";
            }
            return "File";
        }

        if (mime.startsWith("image/")) {
            return "Image";
        }
        if (mime.startsWith("text/")) {
            return "Text document";
        }
        if (mime.startsWith("audio/")) {
            return "Audio";
        }
        if (mime.startsWith("video/")) {
            return "Video";
        }
        if ("application/pdf".equals(mime)) {
            return "PDF document";
        }

        return mime;
    }
}
