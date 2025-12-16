package com.fileexplorer.ui;

import java.util.LinkedList;
import java.util.List;

/**
 * SearchHistoryService
 *  - Keeps track of previous search queries
 *  - Supports max history size
 */
public class SearchHistoryService {

    private final int maxSize;
    private final LinkedList<String> history = new LinkedList<>();

    public SearchHistoryService() {
        this(50);
    }

    public SearchHistoryService(int maxSize) {
        this.maxSize = maxSize;
    }

    public void addQuery(String query) {
        history.remove(query);
        history.addFirst(query);
        while (history.size() > maxSize) {
            history.removeLast();
        }
    }

    public List<String> list() {
        return List.copyOf(history);
    }
}
