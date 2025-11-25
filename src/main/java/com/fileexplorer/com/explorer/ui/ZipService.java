package com.explorer.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * ZipService
 *  - Create ZIP archives
 *  - Extract ZIP archives
 */
public class ZipService {

    public void zip(Path destinationZip, Path... sources) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destinationZip))) {
            for (Path src : sources) {
                if (Files.isDirectory(src)) {
                    Files.walk(src).forEach(p -> {
                        try {
                            if (Files.isRegularFile(p)) {
                                ZipEntry entry = new ZipEntry(src.getParent().relativize(p).toString());
                                zos.putNextEntry(entry);
                                Files.copy(p, zos);
                                zos.closeEntry();
                            }
                        } catch (IOException ignored) {}
                    });
                } else {
                    ZipEntry entry = new ZipEntry(src.getFileName().toString());
                    zos.putNextEntry(entry);
                    Files.copy(src, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    public void unzip(Path zipFile, Path targetFolder) throws IOException {
        Files.createDirectories(targetFolder);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = targetFolder.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
