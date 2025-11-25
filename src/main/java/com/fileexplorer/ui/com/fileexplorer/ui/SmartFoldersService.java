package com.fileexplorer.ui;

import java.nio.file.Path;
import java.util.*;

/**
 * SmartFoldersService
 *  - Stores saved searches or virtual folders
 *  - Each smart folder has a name and a predicate (represented as interface)
 */
public class SmartFoldersService {

    public interface Filter {
        boolean matches(Path file);
    }

    private final Map<String, Filter> smartFolders = new HashMap<>();

    public void add(String name, Filter filter) {
        smartFolders.put(name, filter);
    }

    public void remove(String name) {
        smartFolders.remove(name);
    }

    public Filter get(String name) {
        return smartFolders.get(name);
    }

    public Set<String> listNames() {
        return Set.copyOf(smartFolders.keySet());
    }
}