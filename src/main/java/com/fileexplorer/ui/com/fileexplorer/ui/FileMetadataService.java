package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class FileMetadataService {

    private final DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Optional<FileMetadata> getMetadata(Path file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

            FileMetadata meta = new FileMetadata(
                file.getFileName().toString(),
                Files.size(file),
                attrs.isDirectory(),
                attrs.creationTime().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(formatter),
                attrs.lastModifiedTime().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(formatter),
                attrs.lastAccessTime().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(formatter)
            );

            return Optional.of(meta);

        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // record for metadata
    public record FileMetadata(
        String name,
        long size,
        boolean directory,
        String created,
        String modified,
        String accessed
    ) {}
}
