package com.fileexplorer.service.icon;

import javafx.scene.image.Image;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

/**
 * Bounded in-memory icon cache.
 *
 * <p>JavaFX {@link Image} instances are immutable and safe to reuse across controls.
 * This cache avoids repeatedly decoding the same bitmap resources and repeatedly
 * generating placeholder images.</p>
 *
 * <p>Important: this cache is intentionally bounded and stores values as {@link SoftReference}s
 * so the JVM can reclaim memory under pressure.</p>
 */
public final class IconCacheService {

    private static final Logger LOG = Logger.getLogger(IconCacheService.class.getName());

    /**
     * System property to control maximum cache entries.
     * Example: -Dfileexplorer.iconCache.maxEntries=512
     */
    public static final String PROP_MAX_ENTRIES = "fileexplorer.iconCache.maxEntries";

    private static final int DEFAULT_MAX_ENTRIES = 256;
    private static final int MIN_MAX_ENTRIES = 32;
    private static final int MAX_MAX_ENTRIES = 4096;

    private static final IconCacheService INSTANCE = new IconCacheService(readMaxEntries());

    private final int maxEntries;
    private final LinkedHashMap<IconKey, SoftReference<Image>> lru;

/**
 * IconCacheService.
 *
 * @param maxEntries TODO
 * @return TODO
 */
    private IconCacheService(int maxEntries) {
        LogSupport.enter(LOG, "IconCacheService");
        this.maxEntries = clamp(maxEntries, MIN_MAX_ENTRIES, MAX_MAX_ENTRIES);
        this.lru = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
/**
 * removeEldestEntry.
 *
 * @param eldest TODO
 * @return TODO
 */
            protected boolean removeEldestEntry(Map.Entry<IconKey, SoftReference<Image>> eldest) {
                LogSupport.enter(LOG, "removeEldestEntry");
                return size() > IconCacheService.this.maxEntries;
            }
        };
    }

/**
 * getInstance.
 *
 * @return TODO
 */
    public static IconCacheService getInstance() {
        LogSupport.enter(LOG, "getInstance");
        return INSTANCE;
    }

/**
 * getMaxEntries.
 *
 * @return TODO
 */
    public int getMaxEntries() {
        LogSupport.enter(LOG, "getMaxEntries");
        return maxEntries;
    }

    /**
     * Fetch an icon from cache or load it using {@code loader}.
     *
     * @param id     stable identifier for the icon (e.g., "type:FOLDER", "ext:pdf", "ext:txt", "resource:icons/light/folder_24.png")
     * @param dark   theme flag (part of the cache key)
     * @param size   clamped size (part of the cache key)
     * @param loader icon creation function (only executed on cache miss)
     * @return cached or newly created icon image
     */
    public Image getOrLoad(String id, boolean dark, int size, Supplier<Image> loader) {
        LogSupport.enter(LOG, "getOrLoad");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(loader, "loader");

        IconKey key = new IconKey(id, dark, size);

        // Fast path
        Image existing = getIfPresent(key);
        if (existing != null) {
            return existing;
        }

        // Load outside of map mutation to keep lock short, but still prevent duplicate loads:
        // perform a second check inside the synchronized block.
        synchronized (lru) {
            Image second = getIfPresentUnsafe(key);
            if (second != null) {
                return second;
            }
        }

        Image created = loader.get();
        if (created == null) {
            return null;
        }

        synchronized (lru) {
            lru.put(key, new SoftReference<>(created));
            // Opportunistic cleanup of cleared references.
            trimStaleUnsafe();
        }
        return created;
    }

/**
 * clear.
 *
 */
    public void clear() {
        LogSupport.enter(LOG, "clear");
        synchronized (lru) {
            lru.clear();
        }
    }

    /**
     * Remove any entries whose {@link SoftReference} has been cleared.
     */
    public void trimStale() {
        LogSupport.enter(LOG, "trimStale");
        synchronized (lru) {
            trimStaleUnsafe();
        }
    }

    /**
     * Current entry count (after removing cleared {@link SoftReference}s).
     */
    public int size() {
        synchronized (lru) {
            trimStaleUnsafe();
            return lru.size();
        }
    }

    /**
     * Lightweight diagnostics for perf HUD.
     */
    public String debugString() {
        synchronized (lru) {
            trimStaleUnsafe();
            return "entries=" + lru.size() + " maxEntries=" + maxEntries;
        }
    }

/**
 * getIfPresent.
 *
 * @param key TODO
 * @return TODO
 */
    private Image getIfPresent(IconKey key) {
        LogSupport.enter(LOG, "getIfPresent");
        synchronized (lru) {
            return getIfPresentUnsafe(key);
        }
    }

/**
 * getIfPresentUnsafe.
 *
 * @param key TODO
 * @return TODO
 */
    private Image getIfPresentUnsafe(IconKey key) {
        LogSupport.enter(LOG, "getIfPresentUnsafe");
        SoftReference<Image> ref = lru.get(key);
        if (ref == null) {
            return null;
        }
        Image img = ref.get();
        if (img == null) {
            lru.remove(key);
        }
        return img;
    }

/**
 * trimStaleUnsafe.
 *
 */
    private void trimStaleUnsafe() {
        LogSupport.enter(LOG, "trimStaleUnsafe");
        Iterator<Map.Entry<IconKey, SoftReference<Image>>> it = lru.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IconKey, SoftReference<Image>> e = it.next();
            SoftReference<Image> ref = e.getValue();
            if (ref == null || ref.get() == null) {
                it.remove();
            }
        }
    }

/**
 * readMaxEntries.
 *
 * @return TODO
 */
    private static int readMaxEntries() {
        LogSupport.enter(LOG, "readMaxEntries");
        String raw = System.getProperty(PROP_MAX_ENTRIES);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_ENTRIES;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_MAX_ENTRIES;
        }
    }

/**
 * clamp.
 *
 * @param v TODO
 * @param lo TODO
 * @param hi TODO
 * @return TODO
 */
    private static int clamp(int v, int lo, int hi) {
        LogSupport.enter(LOG, "clamp");
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    /**
      * Cache key for icons.
      */
        public record IconKey(String id, boolean dark, int size) {
/**
 * IconKey.
 *
 * @param id TODO
 * @param dark TODO
 * @param size TODO
 * @return TODO
 */
            public IconKey(String id, boolean dark, int size) {
                LogSupport.enter(LOG, "IconKey");
                this.id = Objects.requireNonNull(id, "id");
                this.dark = dark;
                this.size = size;
            }

            @Override
/**
 * id.
 *
 * @return TODO
 */
            public String id() {
                LogSupport.enter(LOG, "getId");
                return id;
            }

            @Override
/**
 * dark.
 *
 * @return TODO
 */
            public boolean dark() {
                LogSupport.enter(LOG, "isDark");
                return dark;
            }

            @Override
/**
 * size.
 *
 * @return TODO
 */
            public int size() {
                LogSupport.enter(LOG, "getSize");
                return size;
            }

            @Override
/**
 * equals.
 *
 * @param o TODO
 * @return TODO
 */
            public boolean equals(Object o) {
                LogSupport.enter(LOG, "equals");
                if (this == o) {
                    return true;
                }
                if (!(o instanceof IconKey other)) {
                    return false;
                }
                return dark == other.dark
                        && size == other.size
                        && id.equals(other.id);
            }

            @Override
/**
 * hashCode.
 *
 * @return TODO
 */
            public int hashCode() {
                LogSupport.enter(LOG, "hashCode");
                int result = id.hashCode();
                result = 31 * result + (dark ? 1 : 0);
                result = 31 * result + size;
                return result;
            }

            @Override
/**
 * toString.
 *
 * @return TODO
 */
            public String toString() {
                LogSupport.enter(LOG, "toString");
                return "IconKey{id='" + id + "', dark=" + dark + ", size=" + size + "}";
            }
        }
}
