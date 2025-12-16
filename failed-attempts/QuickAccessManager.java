package com.fileexplorer.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class QuickAccessManager {
    private final List<File> favorites = new ArrayList<>();

    public void pinFolder(File folder) {
        if (!favorites.contains(folder)) favorites.add(folder);
    }

    public void unpinFolder(File folder) {
        favorites.remove(folder);
    }

    public List<File> getFavorites() {
        return new ArrayList<>(favorites);
    }

    public boolean isPinned(File folder) {
        return favorites.contains(folder);
    }
}
