package com.fileexplorer.service.icon;

import com.fileexplorer.util.IconLoader;
import javafx.scene.image.Image;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Phase 4P.9CA: asynchronous, generation-aware icon loading with visible-first backpressure.
 *
 * <p>Requests are keyed by (identity, darkTheme, size). Multiple callers share one in-flight
 * decode and receive detached futures so a recycled cell can cancel its own interest without
 * aborting the shared work for everyone else.</p>
 */
public final class AsyncIconService {

    private static final Logger LOG = Logger.getLogger(AsyncIconService.class.getName());
    private static final AsyncIconService INSTANCE = new AsyncIconService();

    private static final int MAX_CONCURRENT = intProp("fileexplorer.icon.maxConcurrent", Math.max(2, Runtime.getRuntime().availableProcessors() / 3), 1, 8);
    private static final int MAX_QUEUE = intProp("fileexplorer.icon.maxQueue", 256, 32, 8192);
    private static final int MAX_PREFETCH_QUEUE = intProp("fileexplorer.icon.maxPrefetchQueue", 96, 16, 2048);
    private static final long PREFETCH_DEBOUNCE_MS = longProp("fileexplorer.icon.prefetchDebounceMs", 70L, 0L, 2000L);
    private static final long BACKGROUND_DEBOUNCE_MS = longProp("fileexplorer.icon.backgroundDebounceMs", 110L, 0L, 4000L);
    private static final long VIEWPORT_SETTLE_MS = longProp("fileexplorer.icon.viewportSettleMs", 95L, 25L, 4000L);
    private static final long MOVING_PREFETCH_EXTRA_DELAY_MS = longProp("fileexplorer.icon.movingPrefetchExtraDelayMs", 45L, 0L, 4000L);
    private static final long MOVING_BACKGROUND_EXTRA_DELAY_MS = longProp("fileexplorer.icon.movingBackgroundExtraDelayMs", 90L, 0L, 4000L);
    private static final int MOVING_VISIBLE_BACKLOG_LIMIT = intProp("fileexplorer.icon.movingVisibleBacklogLimit", 24, 4, 512);
    private static final boolean DEBUG_ICONS = boolProp("fileexplorer.debug.icons", false);

    public enum RequestPriority {
        USER_ACTION(3),
        VISIBLE(2),
        PREFETCH(1),
        BACKGROUND(0);

        final int lane;

        RequestPriority(int lane) {
            this.lane = lane;
        }
    }

    public static AsyncIconService getInstance() {
        return INSTANCE;
    }

    private record IconKey(String identity, boolean darkTheme, int size) { }

    private static final class PrioritizedTask implements Runnable, Comparable<PrioritizedTask> {
        final int priority;
        final long seq;
        final long viewportScope;
        final IconKey key;
        final Runnable delegate;

        private PrioritizedTask(int priority, long seq, long viewportScope, IconKey key, Runnable delegate) {
            this.priority = priority;
            this.seq = seq;
            this.viewportScope = viewportScope;
            this.key = key;
            this.delegate = delegate;
        }

        @Override
        public int compareTo(PrioritizedTask other) {
            int byPriority = Integer.compare(other.priority, this.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            return Long.compare(this.seq, other.seq);
        }

        @Override
        public void run() {
            delegate.run();
        }
    }

    private final ConcurrentHashMap<IconKey, CompletableFuture<Image>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IconKey, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IconKey, AtomicInteger> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IconKey, RequestPriority> pendingPriorities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IconKey, Long> pendingViewportScopes = new ConcurrentHashMap<>();
    private final Set<IconKey> running = ConcurrentHashMap.newKeySet();

    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final CompletableFuture<Void> enabledGate = new CompletableFuture<>();
    private final AtomicLong generation = new AtomicLong(1L);
    private final AtomicLong seq = new AtomicLong(0L);
    private final AtomicLong viewportMovingUntilNanos = new AtomicLong(0L);
    private final AtomicLong viewportScopeGeneration = new AtomicLong(1L);
    private final LongAdder requested = new LongAdder();
    private final LongAdder completed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder cancelled = new LongAdder();
    private final LongAdder droppedForBackpressure = new LongAdder();
    private final LongAdder droppedForGeneration = new LongAdder();
    private final LongAdder droppedForNoSubscribers = new LongAdder();
    private final LongAdder trimmedQueueDrops = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder viewportMotionEvents = new LongAdder();
    private final LongAdder viewportIdleTransitions = new LongAdder();
    private final LongAdder viewportScopeAdvances = new LongAdder();
    private final LongAdder staleViewportScopeDrops = new LongAdder();
    private final LongAdder staleCompletionDiscards = new LongAdder();
    private final LongAdder viewportPrunedPending = new LongAdder();
    private final LongAdder viewportPrunedQueued = new LongAdder();
    private final LongAdder decodeCount = new LongAdder();
    private final LongAdder decodeNanos = new LongAdder();

    private AsyncIconService() {
        this.executor = new ThreadPoolExecutor(
                Math.max(1, MAX_CONCURRENT),
                Math.max(1, MAX_CONCURRENT),
                30L,
                TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(),
                new DaemonThreadFactory("fe-icon")
        );
        this.executor.setRejectedExecutionHandler((r, e) -> {
            rejected.increment();
            if (r instanceof PrioritizedTask task) {
                CompletableFuture<Image> shared = inFlight.remove(task.key);
                if (shared != null && !shared.isDone()) {
                    shared.complete(null);
                }
                running.remove(task.key);
                subscribers.remove(task.key);
                pendingPriorities.remove(task.key);
                pendingViewportScopes.remove(task.key);
            }
            LOG.warning("Icon work rejected");
        });

        ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("fe-icon-scheduler"));
        scheduled.setRemoveOnCancelPolicy(true);
        this.scheduler = scheduled;

        if (DEBUG_ICONS && scheduled instanceof ScheduledThreadPoolExecutor debugScheduler) {
            debugScheduler.scheduleAtFixedRate(() -> LOG.info(debugString()), 5L, 5L, TimeUnit.SECONDS);
        }
    }

    public void setEnabled(boolean enable) {
        if (enable && enabled.compareAndSet(false, true)) {
            enabledGate.complete(null);
        }
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void noteViewportMotion() {
        viewportMotionEvents.increment();
        viewportMovingUntilNanos.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(VIEWPORT_SETTLE_MS));
        advanceViewportScope();
    }

    public void noteViewportIdle() {
        long previous = viewportMovingUntilNanos.getAndSet(0L);
        if (previous != 0L) {
            viewportIdleTransitions.increment();
        }
    }

    public boolean isViewportMoving() {
        long until = viewportMovingUntilNanos.get();
        return until != 0L && System.nanoTime() < until;
    }

    public String debugString() {
        double avgDecodeMs = decodeCount.sum() <= 0L ? 0.0 : (decodeNanos.sum() / 1_000_000.0) / Math.max(1L, decodeCount.sum());
        return "[Icons] enabled=" + enabled.get()
                + " moving=" + isViewportMoving()
                + " viewportScope=" + viewportScopeGeneration.get()
                + " inFlight=" + inFlight.size()
                + " running=" + running.size()
                + " pending=" + pending.size()
                + " queue=" + executor.getQueue().size()
                + " req=" + requested.sum()
                + " done=" + completed.sum()
                + " failed=" + failed.sum()
                + " cancelled=" + cancelled.sum()
                + " backpressureDrops=" + droppedForBackpressure.sum()
                + " generationDrops=" + droppedForGeneration.sum()
                + " staleViewportDrops=" + staleViewportScopeDrops.sum()
                + " staleCompletionDiscards=" + staleCompletionDiscards.sum()
                + " subscriberDrops=" + droppedForNoSubscribers.sum()
                + " trimDrops=" + trimmedQueueDrops.sum()
                + " rejected=" + rejected.sum()
                + " viewportMotion=" + viewportMotionEvents.sum()
                + " viewportIdle=" + viewportIdleTransitions.sum()
                + " viewportScopeAdvances=" + viewportScopeAdvances.sum()
                + " viewportPrunedPending=" + viewportPrunedPending.sum()
                + " viewportPrunedQueued=" + viewportPrunedQueued.sum()
                + " avgDecodeMs=" + String.format(java.util.Locale.ROOT, "%.2f", avgDecodeMs);
    }

    /**
     * Best-effort cancellation for pending and stale completions across navigation / viewport scope changes.
     */
    public void cancelAll() {
        generation.incrementAndGet();
        viewportScopeGeneration.incrementAndGet();
        pending.forEach((key, future) -> {
            if (future != null) {
                try {
                    future.cancel(false);
                } catch (Exception ignored) {
                }
            }
        });
        pending.clear();
        pendingPriorities.clear();
        pendingViewportScopes.clear();
    }

    public CompletableFuture<Image> request(String identity, boolean darkTheme, int size) {
        return request(identity, darkTheme, size, RequestPriority.VISIBLE);
    }

    public CompletableFuture<Image> request(String identity, boolean darkTheme, int size, RequestPriority priority) {
        final String id = Objects.requireNonNullElse(identity, "type:" + IconLoader.IconType.FILE.name());
        final int clamped = Math.max(12, Math.min(128, size));
        final RequestPriority pr = priority == null ? RequestPriority.BACKGROUND : priority;
        return enabledGate.thenCompose(v -> requestEnabled(id, darkTheme, clamped, pr));
    }

    public void warm(Collection<String> identities, boolean darkTheme, int size, RequestPriority priority) {
        if (identities == null || identities.isEmpty()) {
            return;
        }
        for (String identity : identities) {
            if (identity == null || identity.isBlank()) {
                continue;
            }
            try {
                request(identity, darkTheme, size, priority).whenComplete((img, ex) -> {
                    // best-effort warmup only
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private CompletableFuture<Image> requestEnabled(String identity, boolean darkTheme, int size, RequestPriority priority) {
        requested.increment();
        final IconKey key = new IconKey(identity, darkTheme, size);
        final boolean[] created = {false};
        CompletableFuture<Image> shared = inFlight.computeIfAbsent(key, k -> {
            created[0] = true;
            return new CompletableFuture<>();
        });

        incSubscriber(key);
        CompletableFuture<Image> detached = new CompletableFuture<>();
        detached.whenComplete((img, ex) -> decSubscriber(key));

        shared.whenComplete((img, ex) -> {
            if (detached.isCancelled()) {
                return;
            }
            if (ex != null) {
                detached.complete(null);
            } else {
                detached.complete(img);
            }
        });

        if (shared.isDone()) {
            return detached;
        }

        long viewportScope = viewportScopeFor(priority);
        if (!created[0]) {
            if (running.contains(key)) {
                return detached;
            }
            if (pending.containsKey(key)) {
                if (shouldReschedulePendingWork(key, priority, viewportScope)) {
                    scheduleDecode(key, shared, priority, viewportScope);
                }
                return detached;
            }
        }

        scheduleDecode(key, shared, priority, viewportScope);
        return detached;
    }

    private boolean shouldReschedulePendingWork(IconKey key, RequestPriority requestedPriority, long requestedViewportScope) {
        RequestPriority existingPriority = pendingPriorities.get(key);
        Long existingScope = pendingViewportScopes.get(key);
        if (existingPriority == null) {
            return true;
        }
        if (requestedPriority.lane > existingPriority.lane) {
            return true;
        }
        return !Objects.equals(existingScope, requestedViewportScope);
    }

    private void scheduleDecode(IconKey key, CompletableFuture<Image> shared, RequestPriority priority, long viewportScope) {
        ScheduledFuture<?> previous = pending.get(key);
        if (previous != null) {
            previous.cancel(false);
        }

        long genAtSchedule = generation.get();
        long seqNo = seq.incrementAndGet();
        long delayMs = switch (priority) {
            case USER_ACTION, VISIBLE -> 0L;
            case PREFETCH -> PREFETCH_DEBOUNCE_MS + (isViewportMoving() ? MOVING_PREFETCH_EXTRA_DELAY_MS : 0L);
            case BACKGROUND -> BACKGROUND_DEBOUNCE_MS + (isViewportMoving() ? MOVING_BACKGROUND_EXTRA_DELAY_MS : 0L);
        };

        ScheduledFuture<?> future = scheduler.schedule(
                () -> startDecode(key, shared, priority, genAtSchedule, seqNo, viewportScope),
                delayMs,
                TimeUnit.MILLISECONDS
        );
        pending.put(key, future);
        pendingPriorities.put(key, priority);
        pendingViewportScopes.put(key, viewportScope);
    }

    private void startDecode(IconKey key,
                             CompletableFuture<Image> shared,
                             RequestPriority priority,
                             long genAtSchedule,
                             long seqNo,
                             long viewportScope) {
        if (generation.get() != genAtSchedule) {
            droppedForGeneration.increment();
            completeCancelled(key, shared);
            return;
        }
        if (!isViewportScopeCurrent(priority, viewportScope)) {
            staleViewportScopeDrops.increment();
            completeCancelled(key, shared);
            return;
        }
        if (subscriberCount(key) <= 0) {
            droppedForNoSubscribers.increment();
            completeCancelled(key, shared);
            return;
        }
        trimQueueIfNeeded();
        if (shouldDropForBackpressure(priority)) {
            completeCancelled(key, shared);
            return;
        }

        running.add(key);
        executor.execute(new PrioritizedTask(priority.lane, seqNo, viewportScope, key, () -> {
            long startedAt = System.nanoTime();
            try {
                if (generation.get() != genAtSchedule) {
                    droppedForGeneration.increment();
                    shared.complete(null);
                    return;
                }
                if (!isViewportScopeCurrent(priority, viewportScope)) {
                    staleViewportScopeDrops.increment();
                    shared.complete(null);
                    return;
                }
                Image img = IconLoader.loadForIdentity(key.identity(), key.darkTheme(), key.size());
                if (generation.get() != genAtSchedule) {
                    droppedForGeneration.increment();
                    shared.complete(null);
                    return;
                }
                if (!isViewportScopeCurrent(priority, viewportScope)) {
                    staleViewportScopeDrops.increment();
                    staleCompletionDiscards.increment();
                    shared.complete(null);
                    return;
                }
                completed.increment();
                shared.complete(img);
            } catch (CancellationException ce) {
                cancelled.increment();
                shared.complete(null);
            } catch (Throwable t) {
                failed.increment();
                LOG.log(Level.FINE, "Async icon decode failed for " + key.identity(), t);
                shared.complete(null);
            } finally {
                decodeCount.increment();
                decodeNanos.add(System.nanoTime() - startedAt);
                running.remove(key);
                inFlight.remove(key);
                ScheduledFuture<?> pendingFuture = pending.remove(key);
                if (pendingFuture != null) {
                    pendingFuture.cancel(false);
                }
                pendingPriorities.remove(key);
                pendingViewportScopes.remove(key);
                if (subscriberCount(key) <= 0) {
                    subscribers.remove(key);
                }
            }
        }));
    }


    private long viewportScopeFor(RequestPriority priority) {
        return priority == RequestPriority.USER_ACTION ? 0L : viewportScopeGeneration.get();
    }

    private boolean isViewportScopeCurrent(RequestPriority priority, long viewportScope) {
        return priority == RequestPriority.USER_ACTION || viewportScope == viewportScopeGeneration.get();
    }

    private void advanceViewportScope() {
        long currentScope = viewportScopeGeneration.incrementAndGet();
        viewportScopeAdvances.increment();
        prunePendingViewportScopedWork(currentScope);
        pruneQueuedViewportScopedWork(currentScope);
    }

    private void prunePendingViewportScopedWork(long currentScope) {
        pending.forEach((key, future) -> {
            RequestPriority priority = pendingPriorities.get(key);
            Long scope = pendingViewportScopes.get(key);
            if (priority == null || priority == RequestPriority.USER_ACTION || scope == null || scope == currentScope) {
                return;
            }
            try {
                if (future != null) {
                    future.cancel(false);
                }
            } catch (Exception ignored) {
            }
            pending.remove(key, future);
            pendingPriorities.remove(key);
            pendingViewportScopes.remove(key);
            CompletableFuture<Image> shared = inFlight.remove(key);
            if (shared != null && !shared.isDone()) {
                shared.complete(null);
            }
            subscribers.remove(key);
            viewportPrunedPending.increment();
            cancelled.increment();
        });
    }

    private void pruneQueuedViewportScopedWork(long currentScope) {
        try {
            java.util.Iterator<Runnable> iterator = executor.getQueue().iterator();
            while (iterator.hasNext()) {
                Runnable runnable = iterator.next();
                if (!(runnable instanceof PrioritizedTask task)) {
                    continue;
                }
                if (task.viewportScope == 0L || task.viewportScope == currentScope) {
                    continue;
                }
                iterator.remove();
                pendingPriorities.remove(task.key);
                pendingViewportScopes.remove(task.key);
                CompletableFuture<Image> shared = inFlight.remove(task.key);
                if (shared != null && !shared.isDone()) {
                    shared.complete(null);
                }
                ScheduledFuture<?> future = pending.remove(task.key);
                if (future != null) {
                    future.cancel(false);
                }
                subscribers.remove(task.key);
                running.remove(task.key);
                viewportPrunedQueued.increment();
                cancelled.increment();
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean shouldDropForBackpressure(RequestPriority priority) {
        int queueSize = executor.getQueue().size();
        if (priority == RequestPriority.USER_ACTION || priority == RequestPriority.VISIBLE) {
            return false;
        }
        int visibleBacklog = queuedWorkAtOrAbove(RequestPriority.VISIBLE.lane);
        if (isViewportMoving() && visibleBacklog >= MOVING_VISIBLE_BACKLOG_LIMIT) {
            droppedForBackpressure.increment();
            return true;
        }
        if (priority == RequestPriority.PREFETCH && queueSize >= MAX_PREFETCH_QUEUE) {
            droppedForBackpressure.increment();
            return true;
        }
        if (queueSize >= MAX_QUEUE) {
            droppedForBackpressure.increment();
            return true;
        }
        return false;
    }

    private int queuedWorkAtOrAbove(int minPriorityLane) {
        int count = 0;
        for (Runnable runnable : executor.getQueue()) {
            if (runnable instanceof PrioritizedTask task && task.priority >= minPriorityLane) {
                count++;
            }
        }
        return count;
    }

    private void trimQueueIfNeeded() {
        try {
            while (executor.getQueue().size() > MAX_QUEUE) {
                Runnable r = executor.getQueue().poll();
                if (r instanceof PrioritizedTask task) {
                    trimmedQueueDrops.increment();
                    pendingPriorities.remove(task.key);
                    pendingViewportScopes.remove(task.key);
                    CompletableFuture<Image> shared = inFlight.remove(task.key);
                    if (shared != null && !shared.isDone()) {
                        shared.complete(null);
                    }
                    ScheduledFuture<?> future = pending.remove(task.key);
                    if (future != null) {
                        future.cancel(false);
                    }
                    subscribers.remove(task.key);
                    running.remove(task.key);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void completeCancelled(IconKey key, CompletableFuture<Image> shared) {
        ScheduledFuture<?> future = pending.remove(key);
        if (future != null) {
            future.cancel(false);
        }
        running.remove(key);
        inFlight.remove(key);
        subscribers.remove(key);
        pendingPriorities.remove(key);
        pendingViewportScopes.remove(key);
        if (shared != null && !shared.isDone()) {
            cancelled.increment();
            shared.complete(null);
        }
    }

    private void incSubscriber(IconKey key) {
        subscribers.compute(key, (k, v) -> {
            if (v == null) {
                return new AtomicInteger(1);
            }
            v.incrementAndGet();
            return v;
        });
    }

    private void decSubscriber(IconKey key) {
        AtomicInteger current = subscribers.get(key);
        if (current == null) {
            return;
        }
        int remaining = current.decrementAndGet();
        if (remaining > 0) {
            return;
        }
        subscribers.remove(key);
        if (!running.contains(key)) {
            ScheduledFuture<?> future = pending.remove(key);
            if (future != null) {
                future.cancel(false);
            }
            pendingPriorities.remove(key);
            pendingViewportScopes.remove(key);
            CompletableFuture<Image> shared = inFlight.remove(key);
            if (shared != null && !shared.isDone()) {
                cancelled.increment();
                shared.complete(null);
            }
        }
    }

    private int subscriberCount(IconKey key) {
        AtomicInteger current = subscribers.get(key);
        return current == null ? 0 : current.get();
    }

    public void shutdownNow() {
        scheduler.shutdownNow();
        executor.shutdownNow();
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String baseName;
        private final AtomicInteger counter = new AtomicInteger(1);

        private DaemonThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, baseName + "-" + counter.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    }

    private static int intProp(String key, int def, int min, int max) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return Math.max(min, Math.min(max, def));
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(v.trim())));
        } catch (NumberFormatException e) {
            return Math.max(min, Math.min(max, def));
        }
    }

    private static long longProp(String key, long def, long min, long max) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return Math.max(min, Math.min(max, def));
        }
        try {
            return Math.max(min, Math.min(max, Long.parseLong(v.trim())));
        } catch (NumberFormatException e) {
            return Math.max(min, Math.min(max, def));
        }
    }


    private static boolean boolProp(String key, boolean def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        return switch (v.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> def;
        };
    }
}
