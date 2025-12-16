package com.fileexplorer.ui;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class RecentFoldersService {

    private final int maxItems;
    private final Deque<Path> recent = new ArrayDeque<>();

    public RecentFoldersService() {
        this(50); // default limit
    }

    public RecentFoldersService(int limit) {
        this.maxItems = limit;
    }

    public void add(Path folder) {
        if (folder == null) return;
        recent.remove(folder);
        recent.addFirst(folder);

        while (recent.size() > maxItems) {
            recent.removeLast();
        }
    }

    public List<Path> list() {
        return recent.stream().collect(Collectors.toList());
    }

    public void clear() {
        recent.clear();
    }

    public boolean contains(Path folder) {
        return recent.contains(folder);
    }
}
