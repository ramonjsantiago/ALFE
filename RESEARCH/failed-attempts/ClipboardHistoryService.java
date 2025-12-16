package com.fileexplorer.ui;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ClipboardHistoryService {

    private final int maxHistory;
    private final Deque<List<Path>> history = new ArrayDeque<>();

    public ClipboardHistoryService() {
        this(20); // default max history
    }

    public ClipboardHistoryService(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public void add(List<Path> items) {
        if (items == null || items.isEmpty()) return;
        history.remove(items); // remove duplicates
        history.addFirst(new ArrayList<>(items));

        while (history.size() > maxHistory) {
            history.removeLast();
        }
    }

    public List<List<Path>> getHistory() {
        return new ArrayList<>(history);
    }

    public void clear() {
        history.clear();
    }
}
