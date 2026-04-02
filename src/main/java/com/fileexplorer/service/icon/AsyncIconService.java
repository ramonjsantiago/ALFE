package com.fileexplorer.service.icon;

import com.fileexplorer.util.IconLoader;
import javafx.scene.image.Image;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 3.5.2: Asynchronous, deduplicated icon loading.
 *
 * <p>Icons are keyed by (identity, darkTheme, size). Requests for the same key share a single
 * background computation. Results still benefit from {@link IconCacheService} via IconLoader.</p>
 *
 * <p>This service never touches JavaFX scene graph off the FX thread; callers must apply images
 * on the FX thread.</p>
 */
public final class AsyncIconService {

    private static final AsyncIconService INSTANCE = new AsyncIconService();

/**
 * getInstance.
 *
 * @return TODO
 */
    public static AsyncIconService getInstance() {
        return INSTANCE;
    }

    private record IconKey(String identity, boolean darkTheme, int size) { }

    private final ConcurrentHashMap<IconKey, CompletableFuture<Image>> inFlight = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final CompletableFuture<Void> enabledGate = new CompletableFuture<>();

/**
 * AsyncIconService.
 *
 * @return TODO
 */
    private AsyncIconService() {
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                new DaemonThreadFactory("fe-icon")
        );
    }

    /**
     * Enables or disables asynchronous icon upgrades.
     *
     * <p>The application only transitions from disabled to enabled during normal startup.
     * Requests made before enablement are deferred behind a single gate so placeholder icons
     * remain cheap while the first visible batch is being committed.</p>
     */
    public void setEnabled(boolean enable) {
        if (enable && enabled.compareAndSet(false, true)) {
            enabledGate.complete(null);
        }
    }

    /**
     * Returns whether the asynchronous icon upgrade gate is open.
     */
    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Request an icon for the given identity. The returned future is shared across callers requesting
     * the same (identity,dark,size) while the computation is in-flight.
     */
    public CompletableFuture<Image> request(String identity, boolean darkTheme, int size) {
        final String id = Objects.requireNonNullElse(identity, "type:" + IconLoader.IconType.FILE.name());
        final int clamped = Math.max(12, Math.min(48, size));
        final IconKey key = new IconKey(id, darkTheme, clamped);

        return enabledGate.thenCompose(v -> inFlight.computeIfAbsent(key, k -> CompletableFuture
                .supplyAsync(() -> IconLoader.loadForIdentity(id, darkTheme, clamped), executor)
                .whenComplete((img, ex) -> inFlight.remove(k))));
    }

    /** Best-effort shutdown (not required for normal app exit). */
    public void shutdownNow() {
        executor.shutdownNow();
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String baseName;
        private int n = 1;

/**
 * DaemonThreadFactory.
 *
 * @param baseName TODO
 * @return TODO
 */
        private DaemonThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
/**
 * newThread.
 *
 * @param r TODO
 * @return TODO
 */
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, baseName + "-" + (n++));
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    }
}
