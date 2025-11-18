package com.fileexplorer.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FavoritesManager {
    private final List<File> favorites = new ArrayList<>();

    public List<File> getFavorites() { return favorites; }

    public void addFavorite(File f) {
        if (f != null && !favorites.contains(f)) favorites.add(f);
    }

    public void removeFavorite(File f) {
        favorites.remove(f);
    }

    public boolean isFavorite(File f) {
        return favorites.contains(f);
    }
}
