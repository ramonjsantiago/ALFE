package com.fileexplorer.lifecycle;

import com.fileexplorer.app.ExplorerContext;

/**
 * Simple lifecycle contract for FXML controllers and UI components.
 *
 * <p>Use {@link #attach(ExplorerContext)} to provide shared application context
 * after construction/FXML injection, and {@link #dispose()} to deterministically
 * release resources (EventBus subscriptions, listeners, background tasks, etc.).</p>
 */
public interface Lifecycle {

    /**
     * Attach a shared {@link ExplorerContext} to this component/controller.
     * Implementations should be idempotent and tolerate repeated calls.
     */
    default void attach(ExplorerContext context) {
        // default no-op
    }

    /**
     * Dispose of resources owned by this component/controller.
     * Implementations should be safe to call multiple times.
     */
    default void dispose() {
        // default no-op
    }
}
