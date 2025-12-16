package com.fileexplorer.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class RecentSearchesService {

    private final int maxItems;
    private final Deque<String> recent = new ArrayDeque<>();

    public RecentSearchesService() {
        this(20); // default limit
    }

    public RecentSearchesService(int limit) {
        this.maxItems = limit;
    }

    public void add(String query) {
        if (query == null || query.isBlank()) return;
        recent.remove(query);
        recent.addFirst(query);

        while (recent.size() > maxItems) {
            recent.removeLast();
        }
    }

    public List<String> list() {
        return recent.stream().collect(Collectors.toList());
    }

    public void clear() {
        recent.clear();
    }

    public boolean contains(String query) {
        return recent.contains(query);
    }
}