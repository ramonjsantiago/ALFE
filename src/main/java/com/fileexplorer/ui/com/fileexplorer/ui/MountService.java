package com.fileexplorer.ui;

import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MountService
 *  - Lists mounted drives/volumes
 *  - Works on Linux/Mac/Windows
 */
public class MountService {

    public List<Path> listMounts() {
        List<Path> mounts = new ArrayList<>();
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            try {
                mounts.add(Path.of(store.toString()));
            } catch (Exception ignored) {}
        }
        return mounts;
    }
}
