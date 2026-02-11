package com.fileexplorer.service.coordinator;

import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.event.events.DirectoryLoadFailed;
import com.fileexplorer.service.event.events.DirectoryLoadRequested;
import com.fileexplorer.service.event.events.DirectoryLoadStarted;
import com.fileexplorer.service.event.events.DirectoryLoadSucceeded;
import com.fileexplorer.service.filesystem.DirectoryLoadManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates directory load requests and publishes lifecycle events.
 *
 * This is intentionally thin: it delegates cancellation, throttling, and I/O to DirectoryLoadManager.
 */
public final class DirectoryCoordinator {

    private final EventBus bus;
    private final DirectoryLoadManager loadManager;
    private final AtomicLong seq = new AtomicLong(0);

    public DirectoryCoordinator(EventBus bus, DirectoryLoadManager loadManager) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.loadManager = Objects.requireNonNull(loadManager, "loadManager");
    }

    public long requestLoad(Path directory, boolean showHidden) {
        if (directory == null) return 0L;
        long requestId = seq.incrementAndGet();
        bus.publish(new DirectoryLoadRequested(requestId, directory, showHidden));
        long startNanos = System.nanoTime();
        bus.publish(new DirectoryLoadStarted(requestId, directory));

        loadManager.load(
                directory,
                showHidden,
                children -> {
                    long durMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    bus.publish(new DirectoryLoadSucceeded(requestId, directory, children, durMs));
                },
                error -> bus.publish(new DirectoryLoadFailed(requestId, directory, error))
        );

        return requestId;
    }

    public long requestLoad(Path directory) {
        return requestLoad(directory, true);
    }
}
