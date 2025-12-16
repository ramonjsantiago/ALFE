package com.fileexplorer.ui;

import java.nio.file.Path;
import java.util.*;

/**
 * TaggingService
 *  - Adds/removes user-defined tags to files/folders
 *  - Tags stored in-memory, persist via ConfigService if desired
 */
public class TaggingService {

    private final Map<Path, Set<String>> tagMap = new HashMap<>();

    public void addTag(Path file, String tag) {
        tagMap.computeIfAbsent(file, k -> new HashSet<>()).add(tag);
    }

    public void removeTag(Path file, String tag) {
        Set<String> tags = tagMap.get(file);
        if (tags != null) {
            tags.remove(tag);
            if (tags.isEmpty()) tagMap.remove(file);
        }
    }

    public Set<String> getTags(Path file) {
        return tagMap.getOrDefault(file, Set.of());
    }

    public boolean hasTag(Path file, String tag) {
        return getTags(file).contains(tag);
    }
}
