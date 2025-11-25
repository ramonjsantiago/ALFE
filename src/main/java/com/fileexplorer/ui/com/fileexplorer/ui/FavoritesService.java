package com.fileexplorer.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * FavoritesService
 *  - Keeps a list of user-starred folders
 *  - Integrated with NavigationPaneController
 *  - Persistent storage can be added later (JSON or config file)
 */
public class FavoritesService {

    private final List<Path> favorites = new ArrayList<>();

    public void add(Path folder) {
        if (!favorites.contains(folder)) {
            favorites.add(folder);
        }
    }

    public void remove(Path folder) {
        favorites.remove(folder);
    }

    public List<Path> list() {
        return List.copyOf(favorites);
    }

    public boolean isFavorite(Path folder) {
        return favorites.contains(folder);
    }
}
