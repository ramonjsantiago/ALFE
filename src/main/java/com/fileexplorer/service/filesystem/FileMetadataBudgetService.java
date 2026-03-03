package com.fileexplorer.service.filesystem;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Phase 4B.2: Low-CPU, budgeted metadata fetching.
 *
 * <p>Directory listing avoids per-entry stat calls. This service fetches size/mtime/type
 * on-demand under a strict rate limit, and supports simple priority ordering.</p>
 */
public final class FileMetadataBudgetService implements AutoCloseable {

    public enum Priority {
        USER(0),
        VISIBLE(1),
        BACKGROUND(2);

        final int rank;
        Priority(int rank) { this.rank = rank; }
    }

    public record Metadata(String type, String size, String modified) { }

    private static final AtomicLong SEQ = new AtomicLong(0L);

    private final FileMetadataService metadataService;
    private final Executor ioExecutor;

    private final PriorityBlockingQueue<Task> queue = new PriorityBlockingQueue<>();
    private final ScheduledExecutorService scheduler;

    private final int permitsPerSecond;
    private final int maxInFlight;
    private final int maxQueue;

    // Per-folder scope token: increments on navigation to drop stale work.
    private final java.util.concurrent.atomic.AtomicLong scopeToken = new java.util.concurrent.atomic.AtomicLong(0L);

    private final java.util.concurrent.Semaphore inFlight;
    private final java.util.concurrent.atomic.AtomicInteger permits = new java.util.concurrent.atomic.AtomicInteger(0);


    // Lowest-CPU mode: only process non-USER metadata when the user is idle.
    private final boolean idleOnly;
    private final long idleDelayMs;
    private volatile long lastActivityNanos = System.nanoTime();

    // De-dup: keep only the latest requested task per path.
    private final java.util.concurrent.ConcurrentHashMap<Path, Task> latestByPath = new java.util.concurrent.ConcurrentHashMap<>();
    public FileMetadataBudgetService(FileMetadataService metadataService, Executor ioExecutor) {
        this.metadataService = Objects.requireNonNull(metadataService, "metadataService");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");

        this.permitsPerSecond = intProp("fileexplorer.metadata.statsPerSecond", 10);
        this.maxInFlight = Math.max(1, intProp("fileexplorer.metadata.maxInFlight", 1));
        this.maxQueue = Math.max(100, intProp("fileexplorer.metadata.maxQueue", 20000));
        this.inFlight = new java.util.concurrent.Semaphore(this.maxInFlight);


        this.idleOnly = boolProp("fileexplorer.metadata.idleOnly", true);
        this.idleDelayMs = longProp("fileexplorer.metadata.idleDelayMs", 250L);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fe-metadata-budget");
            t.setDaemon(true);
            return t;
        });

        // Refill permits and attempt to drain.
        scheduler.scheduleAtFixedRate(() -> {
            try {
                permits.set(permitsPerSecond);
                drain();
            } catch (Throwable ignored) {
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Begin a new folder navigation scope. All previously queued work is dropped.
     */
    public void beginScope(long newScopeToken) {
        scopeToken.set(newScopeToken);
        cancelAll();
    }

    /** Drop any queued work immediately. In-flight work will naturally drain. */
    public void cancelAll() {
        try { queue.clear(); } catch (Exception ignored) {}
        try { latestByPath.clear(); } catch (Exception ignored) {}
    }

    /**
     * Enqueue a metadata request. Callback is invoked on the IO executor thread.
     */
    public void request(Path path, Priority priority, Consumer<Metadata> onReady) {
        if (path == null || onReady == null) return;
        Priority pr = (priority == null ? Priority.BACKGROUND : priority);
        Task t = new Task(path, pr, scopeToken.get(), SEQ.incrementAndGet(), onReady);
        latestByPath.put(path, t);
        queue.offer(t);
        drain();
    }


    /** Notify the service that the user is actively interacting (scrolling/typing). */
    public void notifyUserActivity() {
        lastActivityNanos = System.nanoTime();
    }

    
    private void trimQueueIfNeeded() {
        int over = queue.size() - maxQueue;
        if (over <= 0) return;
        try {
            java.util.Iterator<Task> it = queue.iterator();
            while (it.hasNext() && over > 0) {
                Task t = it.next();
                if (t.priority == Priority.BACKGROUND) {
                    it.remove();
                    latestByPath.remove(t.path, t);
                    over--;
                }
            }
            it = queue.iterator();
            while (it.hasNext() && over > 0) {
                Task t = it.next();
                if (t.priority == Priority.VISIBLE) {
                    it.remove();
                    latestByPath.remove(t.path, t);
                    over--;
                }
            }
        } catch (Throwable ignored) {}
    }

    private void drain() {
        // Only the scheduler thread calls drain periodically, but we also call it opportunistically.
        // Keep it lock-free and best-effort.
        while (permits.get() > 0 && inFlight.tryAcquire()) {
            Task t = queue.poll();
            if (t == null) {
                inFlight.release();
                return;
            }

            // Drop tasks from a previous navigation scope.
            if (t.scopeToken != scopeToken.get()) {
                inFlight.release();
                continue;
            }

            // Drop stale/duplicate tasks (only execute the latest requested task per Path).
            Task latest = latestByPath.get(t.path);
            if (latest != t) {
                inFlight.release();
                continue;
            }

            // Lowest CPU: during active interaction, only allow USER priority.
            if (idleOnly && !isIdle() && t.priority != Priority.USER) {
                inFlight.release();
                // Put it back and stop draining for now; next idle tick will pick it up.
                queue.offer(t);
                return;
            }

            if (permits.decrementAndGet() < 0) {
                // return token and requeue
                permits.incrementAndGet();
                inFlight.release();
                queue.offer(t);
                return;
            }

            ioExecutor.execute(() -> {
                try {
                    String type = safeType(t.path);
                    String size = safeSize(t.path);
                    String mod = safeModified(t.path);
                    t.onReady.accept(new Metadata(type, size, mod));
                } catch (Throwable ignored) {
                    // swallow
                } finally {
                    // clear if still latest
                    latestByPath.remove(t.path, t);
                    inFlight.release();
                }
            });
        }
    }

    private boolean isIdle() {
        long ageMs = (System.nanoTime() - lastActivityNanos) / 1_000_000L;
        return ageMs >= idleDelayMs;
    }


    private String safeType(Path p) {
        try { return metadataService.detectFileType(p); } catch (Exception ex) { return ""; }
    }

    private String safeSize(Path p) {
        try { return metadataService.humanReadableSizeForTable(p); } catch (Exception ex) { return ""; }
    }

    private String safeModified(Path p) {
        try { return metadataService.lastModifiedLocalString(p); } catch (Exception ex) { return ""; }
    }

    @Override
    public void close() {
        try { scheduler.shutdownNow(); } catch (Exception ignored) {}
    }

    private static int intProp(String key, int def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static long longProp(String key, long def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static boolean boolProp(String key, boolean def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        return Boolean.parseBoolean(v.trim());
    }

    /**
     * Lightweight diagnostics for perf HUD.
     */
    public String debugString() {
        int q;
        try {
            q = queue.size();
        } catch (Throwable t) {
            q = -1;
        }
        long p;
        try {
            p = permits.get();
        } catch (Throwable t) {
            p = -1;
        }
        return "queue=" + q
                + " permits=" + p
                + " perSec=" + permitsPerSecond
                + " maxInFlight=" + maxInFlight
                + " idleOnly=" + idleOnly
                + " maxQueue=" + maxQueue;
    }

    private static final class Task implements Comparable<Task> {
        final Path path;
        final Priority priority;
        final long scopeToken;
        final long seq;
        final Consumer<Metadata> onReady;

        Task(Path path, Priority priority, long scopeToken, long seq, Consumer<Metadata> onReady) {
            this.path = path;
            this.priority = priority;
            this.scopeToken = scopeToken;
            this.seq = seq;
            this.onReady = onReady;
        }

        @Override
        public int compareTo(Task o) {
            if (o == null) return -1;
            int pr = Integer.compare(this.priority.rank, o.priority.rank);
            if (pr != 0) return pr;
            return Long.compare(this.seq, o.seq);
        }
    }
}
