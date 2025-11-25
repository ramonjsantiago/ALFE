package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ClipboardService
 *  - Supports Copy, Cut, Paste, Delete
 *  - Maintains internal clipboard list
 *  - Uses Files.copy() and Files.move()
 */
public class ClipboardService {

    private final List<Path> clipboard = new ArrayList<>();
    private boolean cutMode = false;

    public void copy(List<Path> items) {
        clipboard.clear();
        clipboard.addAll(items);
        cutMode = false;
    }

    public void cut(List<Path> items) {
        clipboard.clear();
        clipboard.addAll(items);
        cutMode = true;
    }

    public void clear() {
        clipboard.clear();
        cutMode = false;
    }

    public boolean hasContent() {
        return !clipboard.isEmpty();
    }

    public void paste(Path destination) throws IOException {
        if (!Files.isDirectory(destination))
            throw new IOException("Destination is not a directory");

        for (Path src : clipboard) {
            Path dest = destination.resolve(src.getFileName());

            if (cutMode) {
                Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }

        if (cutMode) clear();
    }

    public void delete(List<Path> items) throws IOException {
        for (Path p : items) {
            if (Files.isDirectory(p)) {
                try (var s = Files.walk(p)) {
                    s.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                     .forEach(x -> { try { Files.delete(x); } catch (Exception ignored) {} });
                }
            } else {
                Files.deleteIfExists(p);
            }
        }
    }
}
