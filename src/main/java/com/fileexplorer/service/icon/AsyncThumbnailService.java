package com.fileexplorer.service.icon;

import com.fileexplorer.util.ImageSupport;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Lazy thumbnail loading for image files.
 *
 * <p>IMPORTANT: To avoid impacting startup/first paint, thumbnail decoding is gated until
 * {@link #setEnabled(boolean)} is called with {@code true}. Calls to {@link #request(Path, int)}
 * (or {@link #request(Path, int, RequestPriority)}) made before enablement will queue and start
 * after enablement.</p>
 */
public final class AsyncThumbnailService {

    private static final Logger LOG = Logger.getLogger(AsyncThumbnailService.class.getName());

    /** Enable periodic thumbnail metrics logging. */
    private static final boolean DEBUG_THUMBS = Boolean.getBoolean("fileexplorer.debug.thumbs");

    /** Max concurrent decodes (CPU protection). */
    private static final int MAX_CONCURRENT =
            intProp("fileexplorer.thumb.maxConcurrent", 2, 1, 16);

    /** Approximate max thumbnail cache bytes (memory protection). Default 128 MiB. */
    private static final long CACHE_BYTES =
            longProp("fileexplorer.thumb.cacheBytes", 128L * 1024L * 1024L, 16L * 1024L * 1024L, 2L * 1024L * 1024L * 1024L);

    /** Hard cap the decode queue to avoid unbounded memory growth during soak. */
    private static final int MAX_QUEUE =
            intProp("fileexplorer.thumb.maxQueue", 4000, 256, 200000);

    private static final AsyncThumbnailService INSTANCE = new AsyncThumbnailService();

    public static AsyncThumbnailService getInstance() {
        return INSTANCE;
    }

    /** Priority lane for work. Higher = sooner. */
    public enum RequestPriority {
        USER_ACTION(3),
        VISIBLE(2),
        BACKGROUND(1);

        final int p;
        RequestPriority(int p) { this.p = p; }
    }

    private record ThumbKey(String path, int sizePx) {}

    private static final class CachedThumb {
        final long lastModifiedMs;
        final Image image;
        final long approxBytes;

        CachedThumb(long lastModifiedMs, Image image, long approxBytes) {
            this.lastModifiedMs = lastModifiedMs;
            this.image = image;
            this.approxBytes = approxBytes;
        }
    }

    /**
     * Byte-bounded LRU thumbnail cache.
     *
     * <p>Keyed by (absolutePath,sizePx). Entries store lastModified to invalidate on change.</p>
     */
    private static final class ThumbCache {

        private final LinkedHashMap<ThumbKey, CachedThumb> lru = new LinkedHashMap<>(512, 0.75f, true);
        private long maxBytes;
        private long bytes;

        ThumbCache(long maxBytes) {
            this.maxBytes = Math.max(16L * 1024L * 1024L, maxBytes);
        }

        synchronized CachedThumb get(ThumbKey key) {
            return lru.get(key);
        }

        synchronized CachedThumb getNearestLarger(String absPath, int requestedSizePx) {
            // best-effort: scan keys and return the smallest cached size >= requested size
            int bestSize = Integer.MAX_VALUE;
            CachedThumb best = null;
            for (Map.Entry<ThumbKey, CachedThumb> e : lru.entrySet()) {
                ThumbKey k = e.getKey();
                if (!Objects.equals(k.path, absPath)) continue;
                if (k.sizePx < requestedSizePx) continue;
                if (k.sizePx < bestSize) {
                    bestSize = k.sizePx;
                    best = e.getValue();
                }
            }
            return best;
        }

        synchronized void put(ThumbKey key, CachedThumb entry) {
            CachedThumb prior = lru.remove(key);
            if (prior != null) bytes -= prior.approxBytes;

            lru.put(key, entry);
            bytes += entry.approxBytes;

            evict();
        }

        synchronized void remove(ThumbKey key) {
            CachedThumb prior = lru.remove(key);
            if (prior != null) bytes -= prior.approxBytes;
        }

        synchronized void clearAll() {
            lru.clear();
            bytes = 0L;
        }

        synchronized String debugString() {
            return "entries=" + lru.size() + " bytes=" + bytes + " maxBytes=" + maxBytes;
        }

        private void evict() {
            while (bytes > maxBytes && !lru.isEmpty()) {
                Map.Entry<ThumbKey, CachedThumb> eldest = lru.entrySet().iterator().next();
                CachedThumb ct = eldest.getValue();
                lru.remove(eldest.getKey());
                if (ct != null) bytes -= ct.approxBytes;
            }
        }
    }

    // ---- state ----
    private final ConcurrentHashMap<ThumbKey, CompletableFuture<Image>> inFlight = new ConcurrentHashMap<>();
    // Subscriber counts allow us to cancel pending (debounced) work when cells scroll away.
    private final ConcurrentHashMap<ThumbKey, AtomicInteger> subscribers = new ConcurrentHashMap<>();
    private final Set<ThumbKey> running = ConcurrentHashMap.newKeySet();

    private final ThumbCache cache = new ThumbCache(CACHE_BYTES);

    // Priority executor for decode tasks (interaction wins).
    private final ThreadPoolExecutor decodeExecutor;

    // Debounce to avoid excessive work during rapid scrolling / cell churn.
    private static final long DEBOUNCE_MS = 75L;
    private final ConcurrentHashMap<ThumbKey, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    // Gate thumbnail decoding until after first full UI render.
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final CompletableFuture<Void> enabledGate = new CompletableFuture<>();

    // Generation token for preemption: navigation increments generation; stale work is dropped.
    private final AtomicLong generation = new AtomicLong(0L);

    // ---- metrics ----
    private final LongAdder req = new LongAdder();
    private final LongAdder hit = new LongAdder();
    private final LongAdder miss = new LongAdder();
    private final LongAdder ok = new LongAdder();
    private final LongAdder fail = new LongAdder();
    private final LongAdder decodeNanos = new LongAdder();
    private final LongAdder decodeCount = new LongAdder();
    private final AtomicInteger inFlightCount = new AtomicInteger(0);

    private final AtomicLong seq = new AtomicLong(0L);

    private AsyncThumbnailService() {
        // Priority queue ensures visible/user tasks run first.
        PriorityBlockingQueue<Runnable> q = new PriorityBlockingQueue<>(256);
        this.decodeExecutor = new ThreadPoolExecutor(
                MAX_CONCURRENT, MAX_CONCURRENT,
                30L, TimeUnit.SECONDS,
                q,
                daemonThreadFactory("thumb-decode")
        );
        this.decodeExecutor.allowCoreThreadTimeOut(true);

        this.scheduler = new ScheduledThreadPoolExecutor(1, daemonThreadFactory("thumb-debounce"));

        // Ensure ImageIO sees TwelveMonkeys plugins.
        try {
            ImageIO.scanForPlugins();
        } catch (Throwable ignored) {
            // best effort
        }

        startThumbLoggerIfEnabled();
    }

    private void startThumbLoggerIfEnabled() {
        if (!DEBUG_THUMBS) return;

        ScheduledExecutorService ses = new ScheduledThreadPoolExecutor(1, daemonThreadFactory("thumb-stats"));
        ses.scheduleAtFixedRate(() -> {
            long rc = req.sum();
            long hc = hit.sum();
            long mc = miss.sum();
            long okc = ok.sum();
            long fc = fail.sum();
            long dn = decodeNanos.sum();
            long dc = decodeCount.sum();
            double avgMs = (dc == 0) ? 0.0 : (dn / 1_000_000.0) / (double) dc;

            LOG.info(() -> String.format(
                    "[Thumbs] req=%d hit=%d miss=%d inFlight=%d pending=%d ok=%d fail=%d avgDecodeMs=%.2f cache={%s}",
                    rc, hc, mc, inFlightCount.get(), pending.size(), okc, fc, avgMs, cache.debugString()
            ));
        }, 2, 2, TimeUnit.SECONDS);
    }

    /**
     * Lightweight diagnostics for perf HUD / troubleshooting.
     */
    public String debugString() {
        int q;
        try {
            q = decodeExecutor.getQueue().size();
        } catch (Throwable t) {
            q = -1;
        }
        int active;
        try {
            active = decodeExecutor.getActiveCount();
        } catch (Throwable t) {
            active = -1;
        }
        return "enabled=" + enabled.get()
                + " inFlight=" + inFlightCount.get()
                + " running=" + running.size()
                + " queue=" + q
                + " active=" + active
                + " cache{" + cache.debugString() + "}";
    }

    /**
     * Best-effort cache trim used under memory pressure.
     */
    public void trimCacheUnderPressure() {
        try {
            cache.clearAll();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Enables/disables thumbnail decoding.
     *
     * <p>We only support transitioning from disabled -> enabled for this app lifecycle.</p>
     */
    public void setEnabled(boolean enable) {
        if (enable && enabled.compareAndSet(false, true)) {
            enabledGate.complete(null);
        }
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Best-effort preemption: cancels queued/debounced work and invalidates in-flight completions.
     *
     * <p>This does not clear the cache by default.</p>
     */
    public void cancelAll() {
        generation.incrementAndGet();

        try {
            pending.forEach((k, f) -> {
                try {
                    f.cancel(false);
                } catch (Exception ignored) {}
            });
        } finally {
            pending.clear();
        }
    }

    private void trimDecodeQueueIfNeeded() {
        try {
            java.util.concurrent.BlockingQueue<Runnable> q = decodeExecutor.getQueue();
            int over = q.size() - MAX_QUEUE;
            if (over <= 0) return;
            // Remove BACKGROUND tasks first, then VISIBLE. Keep USER_ACTION.
            java.util.Iterator<Runnable> it = q.iterator();
            while (it.hasNext() && over > 0) {
                Runnable r = it.next();
                if (r instanceof PrioritizedRunnable pr && pr.priority <= RequestPriority.BACKGROUND.p) {
                    it.remove();
                    over--;
                }
            }
            it = q.iterator();
            while (it.hasNext() && over > 0) {
                Runnable r = it.next();
                if (r instanceof PrioritizedRunnable pr && pr.priority == RequestPriority.VISIBLE.p) {
                    it.remove();
                    over--;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Request a thumbnail for {@code path} sized to fit in a square of {@code sizePx}.
     * Returns {@code null} if the thumbnail cannot be loaded.
     */
    public CompletableFuture<Image> request(Path path, int sizePx) {
        return request(path, sizePx, RequestPriority.BACKGROUND);
    }

    /**
     * Request a thumbnail with explicit priority lane.
     */
    public CompletableFuture<Image> request(Path path, int sizePx, RequestPriority priority) {
        if (path == null) return CompletableFuture.completedFuture(null);
        final int clamped = Math.max(12, Math.min(512, sizePx));
        final String ext = ImageSupport.extensionOf(path);
        if (!ImageSupport.isThumbCandidateExtension(ext)) {
            return CompletableFuture.completedFuture(null);
        }

        final RequestPriority pr = (priority == null) ? RequestPriority.BACKGROUND : priority;

        // Defer actual decoding until after first UI render enablement.
        return enabledGate.thenCompose(v -> requestEnabled(path, ext, clamped, pr));
    }

    private CompletableFuture<Image> requestEnabled(Path path, String ext, int sizePx, RequestPriority pr) {
        Objects.requireNonNull(ext, "ext");

        req.increment();

        final Path abs = path.toAbsolutePath();
        final String absKey = abs.toString();
        final ThumbKey key = new ThumbKey(absKey, sizePx);

        final long lastMod = safeLastModifiedMs(abs);

        CachedThumb cached = cache.get(key);
        if (cached != null && cached.image != null) {
            if (cached.lastModifiedMs == lastMod) {
                hit.increment();
                return CompletableFuture.completedFuture(cached.image);
            }
            cache.remove(key);
        }

        // Reuse nearest larger cached thumbnail when switching to smaller views (avoid re-decode).
        CachedThumb larger = cache.getNearestLarger(absKey, sizePx);
        if (larger != null && larger.image != null && larger.lastModifiedMs == lastMod) {
            hit.increment();
            return CompletableFuture.completedFuture(larger.image);
        }

        miss.increment();

        // Debounced in-flight computation: multiple requests for the same (path,size) during
        // rapid scrolling collapse into a single decode.
        CompletableFuture<Image> shared = inFlight.computeIfAbsent(key, k -> new CompletableFuture<>());

        // Each caller gets a detached future so a cell can cancel its interest without cancelling other callers.
        incSubscriber(key);
        CompletableFuture<Image> detached = new CompletableFuture<>();
        detached.whenComplete((vImg, vEx) -> decSubscriber(key));

        // If already done, complete detached immediately.
        if (shared.isDone()) {
            shared.whenComplete((img, ex) -> {
                if (detached.isCancelled()) return;
                detached.complete(img);
            });
            return detached;
        }

        // Forward completion from shared to detached.
        shared.whenComplete((img, ex) -> {
            if (detached.isCancelled()) return;
            detached.complete(img);
        });

        // (Re)schedule the decode after a short debounce delay.
        ScheduledFuture<?> prev = pending.get(key);
        if (prev != null) prev.cancel(false);

        final long genAtSchedule = generation.get();
        final long seqNo = seq.incrementAndGet();

        ScheduledFuture<?> scheduled = scheduler.schedule(() -> {
            // If preempted, drop.
            if (generation.get() != genAtSchedule) {
                completeAndCleanupPreempted(key);
                return;
            }

            // If no one cares anymore, cancel pending work and clean up.
            if (subscriberCount(key) <= 0) {
                completeAndCleanupPreempted(key);
                return;
            }

            CompletableFuture<Image> current = inFlight.get(key);
            if (current == null || current.isDone()) {
                pending.remove(key);
                return;
            }

            running.add(key);
            long startNanos = System.nanoTime();
            inFlightCount.incrementAndGet();

            // Hard cap queue depth (soak/leak guardrail).
            trimDecodeQueueIfNeeded();

            // Submit a comparable task so the executor orders by priority.
            decodeExecutor.execute(new PrioritizedRunnable(pr, seqNo, () -> {
                Image img = null;
                Throwable err = null;
                try {
                    // If preempted mid-flight, skip decode.
                    if (generation.get() != genAtSchedule) return;

                    img = loadThumbnail(abs, ext, sizePx);
                } catch (Throwable t) {
                    err = t;
                } finally {
                    try {
                        long dur = System.nanoTime() - startNanos;
                        decodeCount.increment();
                        decodeNanos.add(dur);

                        if (generation.get() != genAtSchedule) {
                            // stale completion: do not cache
                            current.complete(null);
                            fail.increment();
                            return;
                        }

                        if (img != null) {
                            long approxBytes = approxBytes(img);
                            cache.put(key, new CachedThumb(lastMod, img, approxBytes));
                            ok.increment();
                            current.complete(img);
                        } else {
                            fail.increment();
                            current.complete(null);
                        }
                    } catch (Throwable t) {
                        fail.increment();
                        current.complete(null);
                    } finally {
                        running.remove(key);
                        inFlightCount.decrementAndGet();
                        inFlight.remove(key);
                        ScheduledFuture<?> pf = pending.remove(key);
                        if (pf != null) pf.cancel(false);
                    }
                }
            }));
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);

        pending.put(key, scheduled);
        return detached;
    }

    private void completeAndCleanupPreempted(ThumbKey key) {
        ScheduledFuture<?> pf = pending.remove(key);
        if (pf != null) pf.cancel(false);

        CompletableFuture<Image> s = inFlight.remove(key);
        if (s != null && !s.isDone()) s.complete(null);

        running.remove(key);
        subscribers.remove(key);
    }

    private void incSubscriber(ThumbKey key) {
        subscribers.compute(key, (k, v) -> {
            if (v == null) return new AtomicInteger(1);
            v.incrementAndGet();
            return v;
        });
    }

    private void decSubscriber(ThumbKey key) {
        AtomicInteger ai = subscribers.get(key);
        if (ai == null) return;
        int n = ai.decrementAndGet();
        if (n > 0) return;

        subscribers.remove(key);
        // If no one is waiting anymore and the task hasn't started, cancel any pending scheduled decode.
        if (!running.contains(key)) {
            ScheduledFuture<?> pf = pending.remove(key);
            if (pf != null) pf.cancel(false);

            CompletableFuture<Image> shared = inFlight.remove(key);
            if (shared != null && !shared.isDone()) {
                shared.complete(null);
            }
        }
    }

    private int subscriberCount(ThumbKey key) {
        AtomicInteger ai = subscribers.get(key);
        return ai == null ? 0 : ai.get();
    }

    private static long safeLastModifiedMs(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private Image loadThumbnail(Path path, String ext, int sizePx) {
        try {
            if (!Files.isRegularFile(path)) return null;
        } catch (Exception ex) {
            return null;
        }

        try {
            if (ImageSupport.isJavaFxNativeExtension(ext)) {
                // Use JavaFX decoder directly. We are already off the FX thread.
                String url = path.toUri().toString();
                Image img = new Image(url, sizePx, sizePx, true, true, false);
                if (img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) return null;
                return img;
            }

            // Fallback: ImageIO (TwelveMonkeys expands supported formats).
            BufferedImage bi;
            try (InputStream in = Files.newInputStream(path)) {
                bi = ImageIO.read(in);
            }
            if (bi == null) return null;

            BufferedImage scaled = scaleToFitSquare(bi, sizePx);
            return SwingFXUtils.toFXImage(scaled, null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage scaleToFitSquare(BufferedImage src, int box) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) return src;

        double scale = Math.min((double) box / (double) w, (double) box / (double) h);
        if (scale >= 1.0) {
            // Do not upscale aggressively; return as-is.
            return src;
        }
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static long approxBytes(Image img) {
        if (img == null) return 0L;
        double w = img.getWidth();
        double h = img.getHeight();
        if (w <= 0 || h <= 0) return 0L;
        // Approx for BGRA premultiplied: 4 bytes per pixel.
        return (long) Math.max(1L, Math.round(w * h * 4.0));
    }

    public void shutdownNow() {
        decodeExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

    private static java.util.concurrent.ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger n = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName(prefix + "-" + n.getAndIncrement());
            return t;
        };
    }

    private static final class PrioritizedRunnable implements Runnable, Comparable<PrioritizedRunnable> {
        final int priority;
        final long seqNo;
        final Runnable delegate;

        PrioritizedRunnable(RequestPriority pr, long seqNo, Runnable delegate) {
            this.priority = (pr == null) ? RequestPriority.BACKGROUND.p : pr.p;
            this.seqNo = seqNo;
            this.delegate = delegate;
        }

        @Override public void run() { delegate.run(); }

        @Override public int compareTo(PrioritizedRunnable o) {
            // higher priority first; then lower seqNo first.
            int dp = Integer.compare(o.priority, this.priority);
            if (dp != 0) return dp;
            return Long.compare(this.seqNo, o.seqNo);
        }
    }

    private static int intProp(String key, int def, int min, int max) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try {
            int n = Integer.parseInt(v.trim());
            return Math.max(min, Math.min(max, n));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long longProp(String key, long def, long min, long max) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try {
            long n = Long.parseLong(v.trim());
            return Math.max(min, Math.min(max, n));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
