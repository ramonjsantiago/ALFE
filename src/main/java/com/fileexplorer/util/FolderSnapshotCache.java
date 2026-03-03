package com.fileexplorer.util;

import com.fileexplorer.model.FileItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FolderSnapshotCache
 *
 * Bounded LRU cache of recently visited folder listings so Back/Forward
 * can paint immediately and then hydrate with fresh results.
 */
public final class FolderSnapshotCache {

    public static final String PROP_MAX_ENTRIES = "fileexplorer.snapshot.maxEntries";
    public static final String PROP_MAX_ITEMS_PER_SNAPSHOT = "fileexplorer.snapshot.maxItemsPerSnapshot";

    private final int maxEntries;
    private final int maxItemsPerSnapshot;

    private final LinkedHashMap<Path, FolderSnapshot> lru;

    public FolderSnapshotCache() {
        this(
                Integer.getInteger(PROP_MAX_ENTRIES, 25),
                Integer.getInteger(PROP_MAX_ITEMS_PER_SNAPSHOT, 5000)
        );
    }

    public FolderSnapshotCache(int maxEntries, int maxItemsPerSnapshot) {
        this.maxEntries = Math.max(0, maxEntries);
        this.maxItemsPerSnapshot = Math.max(0, maxItemsPerSnapshot);
        this.lru = new LinkedHashMap<>(Math.max(16, this.maxEntries), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, FolderSnapshot> eldest) {
                return FolderSnapshotCache.this.maxEntries > 0 && size() > FolderSnapshotCache.this.maxEntries;
            }
        };
    }

    public synchronized FolderSnapshot get(Path dir) {
        if (dir == null) return null;
        return lru.get(dir.normalize());
    }

    public synchronized void put(Path dir, FolderSnapshot snapshot) {
        if (maxEntries <= 0) return;
        if (dir == null || snapshot == null) return;

        Path key = dir.normalize();
        if (maxItemsPerSnapshot > 0 && snapshot.items() != null && snapshot.items().size() > maxItemsPerSnapshot) {
            // Skip caching extremely large folders to protect heap.
            lru.remove(key);
            return;
        }
        lru.put(key, snapshot);
    }

    public synchronized void remove(Path dir) {
        if (dir == null) return;
        lru.remove(dir.normalize());
    }

    public synchronized void clear() {
        lru.clear();
    }

    public static FolderSnapshot snapshotOf(List<FileItem> items, Path primarySelection, int anchorIndex, long dirLastModifiedMillis) {
        List<FileItem> safe = (items == null) ? List.of() : items;
        List<FileItem> copy = new ArrayList<>(safe.size());
        copy.addAll(safe);
        return new FolderSnapshot(copy, primarySelection, Math.max(0, anchorIndex), dirLastModifiedMillis, copy.size());
    }

    /** Convenience overload when you don't have dir mtime (stores -1). */
    public static FolderSnapshot snapshotOf(List<FileItem> items, Path primarySelection, int anchorIndex) {
        return snapshotOf(items, primarySelection, anchorIndex, -1L);
    }

    public record FolderSnapshot(
            List<FileItem> items,
            Path primarySelection,
            int anchorIndex,
            long dirLastModifiedMillis,
            int itemCount
    ) { }
}
