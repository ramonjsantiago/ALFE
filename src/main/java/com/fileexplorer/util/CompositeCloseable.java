package com.fileexplorer.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small utility to aggregate AutoCloseables (EventBus subscriptions, listeners, etc.)
 * and close them as a group.
 * <p>
 * This is intentionally dependency-free and safe to call multiple times.
 */
public final class CompositeCloseable implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final List<AutoCloseable> closeables = new ArrayList<>();

    /**
     * Adds a closeable to this composite.
     *
     * @return the same closeable for fluent call sites
     */
    public synchronized <T extends AutoCloseable> T add(T closeable) {
        if (closeable == null) {
            return null;
        }
        if (closed.get()) {
            // If already closed, close immediately so callers don't leak.
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
            return closeable;
        }
        closeables.add(closeable);
        return closeable;
    }

    /**
     * Closes and clears all aggregated closeables.
     * Safe to call multiple times.
     */
    @Override
/**
 * close.
 *
 */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<AutoCloseable> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(closeables);
            closeables.clear();
        }
        for (AutoCloseable c : snapshot) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }
}
