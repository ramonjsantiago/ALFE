package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.*;

/**
 * RecycleBinService
 *  - Moves files/folders to OS-specific recycle bin
 *  - Fallback: custom .recycle folder in user home
 */
public class RecycleBinService {

    private final Path fallbackBin = Paths.get(System.getProperty("user.home"), ".recycle");

    public RecycleBinService() {
        try { Files.createDirectories(fallbackBin); } catch (IOException ignored) {}
    }

    public void moveToBin(Path file) throws IOException {
        // TODO: Implement OS-native bin (Windows SHFileOperation, Mac Trash)
        Path target = fallbackBin.resolve(file.getFileName());
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public Path getFallbackBin() {
        return fallbackBin;
    }
}
