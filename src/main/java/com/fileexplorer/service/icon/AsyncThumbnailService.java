package com.fileexplorer.service.icon;

import com.fileexplorer.util.ImageSupport;
import co.elastic.thumbnails4j.core.Dimensions;
import co.elastic.thumbnails4j.core.Thumbnailer;
import co.elastic.thumbnails4j.doc.DOCThumbnailer;
import co.elastic.thumbnails4j.docx.DOCXThumbnailer;
import co.elastic.thumbnails4j.pdf.PDFThumbnailer;
import co.elastic.thumbnails4j.pptx.PPTXThumbnailer;
import co.elastic.thumbnails4j.xls.XLSThumbnailer;
import co.elastic.thumbnails4j.xlsx.XLSXThumbnailer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;
import java.security.MessageDigest;

/**
 * Lazy thumbnail loading for image files and supported document formats.
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

    /** Separate small pool for document thumbnails so long-running document work is isolated. */
    private static final int MAX_DOCUMENT_CONCURRENT =
            intProp("fileexplorer.thumb.doc.maxConcurrent", 1, 1, 4);

    /** Document thumbnail timeout guard. */
    private static final long DOCUMENT_TIMEOUT_MS =
            longProp("fileexplorer.thumb.doc.timeoutMs", 4000L, 250L, 60000L);

    /** Start-window guardrail so fast scroll bursts do not flood the decode executor. */
    private static final long START_WINDOW_MS =
            longProp("fileexplorer.thumb.startWindowMs", 125L, 25L, 5000L);

    /** Max thumbnail starts per rolling start window for non-user-action work. */
    private static final int MAX_STARTS_PER_WINDOW =
            intProp("fileexplorer.thumb.maxStartsPerWindow", 20, 4, 500);

    /** Retry delay when start-window throttling defers work. */
    private static final long THROTTLE_RETRY_MS =
            longProp("fileexplorer.thumb.throttleRetryMs", 40L, 10L, 1000L);

    /** Approximate max thumbnail cache bytes (memory protection). Default 128 MiB. */
    private static final long CACHE_BYTES =
            longProp("fileexplorer.thumb.cacheBytes", 128L * 1024L * 1024L, 16L * 1024L * 1024L, 2L * 1024L * 1024L * 1024L);

    /** Hard cap the decode queue to avoid unbounded memory growth during soak. */
    private static final int MAX_QUEUE =
            intProp("fileexplorer.thumb.maxQueue", 4000, 256, 200000);

    /** Per-format capability switches for thumbnails4j-backed document thumbnails. */
    private static final boolean ENABLE_PDF =
            boolProp("fileexplorer.thumb.enablePdf", true);
    private static final boolean ENABLE_WORD =
            boolProp("fileexplorer.thumb.enableWord", true);
    private static final boolean ENABLE_EXCEL =
            boolProp("fileexplorer.thumb.enableExcel", true);
    private static final boolean ENABLE_PPTX =
            boolProp("fileexplorer.thumb.enablePptx", true);

    /** Carefully reintroduced disk cache, scoped to successful document thumbnails only by default. */
    private static final boolean ENABLE_DISK_CACHE =
            boolProp("fileexplorer.thumb.diskCache.enabled", true);

    private static final boolean DISK_CACHE_DOCUMENTS_ONLY =
            boolProp("fileexplorer.thumb.diskCache.documentsOnly", true);

    private static final long DISK_CACHE_MAX_BYTES =
            longProp("fileexplorer.thumb.diskCache.maxBytes", 512L * 1024L * 1024L, 32L * 1024L * 1024L, 8L * 1024L * 1024L * 1024L);

    private static final int DISK_CACHE_MAX_AGE_DAYS =
            intProp("fileexplorer.thumb.diskCache.maxAgeDays", 21, 1, 3650);

    private static final int DISK_CACHE_PRUNE_INTERVAL_WRITES =
            intProp("fileexplorer.thumb.diskCache.pruneEveryWrites", 24, 1, 10000);

    /** Deferred startup prune so old/stale cache files are cleaned even before the next write burst. */
    private static final boolean ENABLE_STARTUP_PRUNE =
            boolProp("fileexplorer.thumb.diskCache.startupPrune.enabled", true);

    private static final long STARTUP_PRUNE_DELAY_MS =
            longProp("fileexplorer.thumb.diskCache.startupPrune.delayMs", 2500L, 250L, 120000L);

    /** Best-effort self-heal for corrupt cache entries encountered during reads. */
    private static final boolean DELETE_CORRUPT_ON_READ =
            boolProp("fileexplorer.thumb.diskCache.deleteCorruptOnRead", true);

    /** Optional one-shot startup reset for troubleshooting stale cache behavior. */
    private static final boolean CLEAR_DISK_CACHE_ON_STARTUP =
            boolProp("fileexplorer.thumb.diskCache.clearOnStartup", false);

    /** Write a lightweight compatibility manifest into the disk-cache root. */
    private static final boolean ENABLE_DISK_CACHE_MANIFEST =
            boolProp("fileexplorer.thumb.diskCache.manifest.enabled", true);

    /** On startup, clear the disk cache when the saved manifest fingerprint no longer matches the current pipeline. */
    private static final boolean CLEAR_ON_MANIFEST_MISMATCH =
            boolProp("fileexplorer.thumb.diskCache.clearOnManifestMismatch", true);

    private static final String DISK_CACHE_MANIFEST_FILE_NAME = "thumbcache-manifest.properties";
    private static final String DISK_CACHE_COMPAT_VERSION = "phase4o10";

    private static final AsyncThumbnailService INSTANCE = new AsyncThumbnailService();

    private static final Set<String> THUMBNAILS4J_EXTENSIONS = Set.of(
            "doc", "docx", "pdf", "pptx", "xls", "xlsx"
    );

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

    private enum ThumbnailProvider {
        JAVAFX_NATIVE("javafx"),
        THUMBNAILS4J_DOCUMENT("doc"),
        IMAGEIO("imageio"),
        DISABLED("disabled"),
        UNSUPPORTED("unsupported");

        final String tag;

        ThumbnailProvider(String tag) {
            this.tag = tag;
        }
    }

    private record ThumbKey(String path, int sizePx) {}

    private static final int[] SIZE_BUCKETS_PX = {16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512};

    private record LoadResult(Image image, ThumbnailProvider provider) {
        boolean hasImage() {
            return image != null;
        }
    }

    private static final class CachedThumb {
        final long lastModifiedMs;
        final long fileSizeBytes;
        final Image image;
        final long approxBytes;

        CachedThumb(long lastModifiedMs, long fileSizeBytes, Image image, long approxBytes) {
            this.lastModifiedMs = lastModifiedMs;
            this.fileSizeBytes = fileSizeBytes;
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
    private final Path diskCacheDir = resolveDiskCacheDir();
    private final AtomicBoolean diskPruneQueued = new AtomicBoolean(false);
    private final AtomicBoolean startupDiskPruneScheduled = new AtomicBoolean(false);
    private final AtomicInteger diskWritesSincePrune = new AtomicInteger(0);

    // Priority executor for decode tasks (interaction wins).
    private final ThreadPoolExecutor decodeExecutor;
    private final ExecutorService documentExecutor;

    // Debounce to avoid excessive work during rapid scrolling / cell churn.
    private static final long DEBOUNCE_MS = 75L;
    private final ConcurrentHashMap<ThumbKey, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    // Gate thumbnail decoding until after first full UI render.
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final CompletableFuture<Void> enabledGate = new CompletableFuture<>();

    // Generation token for preemption: navigation increments generation; stale work is dropped.
    private final AtomicLong generation = new AtomicLong(0L);
    private final AtomicLong startWindowEpochMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger startWindowCount = new AtomicInteger(0);

    // ---- metrics ----
    private final LongAdder requested = new LongAdder();
    private final LongAdder hit = new LongAdder();
    private final LongAdder miss = new LongAdder();
    private final LongAdder coalesced = new LongAdder();
    private final LongAdder bucketReuse = new LongAdder();
    private final LongAdder throttleDeferrals = new LongAdder();
    private final LongAdder viewportCancels = new LongAdder();
    private final LongAdder queued = new LongAdder();
    private final LongAdder rendered = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder fallbackUsed = new LongAdder();
    private final LongAdder cancelled = new LongAdder();
    private final LongAdder documentTimeouts = new LongAdder();
    private final LongAdder decodeNanos = new LongAdder();
    private final LongAdder decodeCount = new LongAdder();
    private final LongAdder renderedJavaFx = new LongAdder();
    private final LongAdder renderedDocument = new LongAdder();
    private final LongAdder renderedImageIo = new LongAdder();
    private final LongAdder diskCacheHit = new LongAdder();
    private final LongAdder diskCacheMiss = new LongAdder();
    private final LongAdder diskCacheWrite = new LongAdder();
    private final LongAdder diskCacheWriteFail = new LongAdder();
    private final LongAdder diskCachePruned = new LongAdder();
    private final LongAdder diskCachePruneRuns = new LongAdder();
    private final LongAdder diskCacheStartupPruneRuns = new LongAdder();
    private final LongAdder diskCacheCorruptDeletes = new LongAdder();
    private final LongAdder diskCacheStartupClearRuns = new LongAdder();
    private final LongAdder diskCacheStartupClearDeletes = new LongAdder();
    private final LongAdder diskCacheManifestWrites = new LongAdder();
    private final LongAdder diskCacheManifestWriteFail = new LongAdder();
    private final LongAdder diskCacheManifestMismatchDetected = new LongAdder();
    private final LongAdder diskCacheManifestMismatchClears = new LongAdder();
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

        this.documentExecutor = java.util.concurrent.Executors.newFixedThreadPool(
                MAX_DOCUMENT_CONCURRENT,
                daemonThreadFactory("thumb-doc")
        );

        this.scheduler = new ScheduledThreadPoolExecutor(1, daemonThreadFactory("thumb-debounce"));

        // Ensure ImageIO sees TwelveMonkeys and JAI Image I/O SPI plugins.
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
            double avgMs = averageDecodeMs();
            LOG.info(() -> String.format(
                    "[Thumbs] req=%d hit=%d miss=%d coalesced=%d bucketReuse=%d throttled=%d viewportCancels=%d queued=%d inFlight=%d pending=%d rendered=%d failed=%d fallback=%d cancelled=%d docTimeouts=%d provider{fx=%d,doc=%d,imageio=%d} disk{hit=%d,miss=%d,write=%d,writeFail=%d,pruned=%d,pruneRuns=%d,startupPruneRuns=%d,corruptDeletes=%d,startupClearRuns=%d,startupClearDeletes=%d,manifestWrites=%d,manifestWriteFail=%d,manifestMismatchDetected=%d,manifestMismatchClears=%d,enabled=%s,docsOnly=%s,clearOnStartup=%s,clearOnManifestMismatch=%s} avgDecodeMs=%.2f cache={%s} gates={pdf=%s,word=%s,excel=%s,pptx=%s}",
                    requested.sum(),
                    hit.sum(),
                    miss.sum(),
                    coalesced.sum(),
                    bucketReuse.sum(),
                    throttleDeferrals.sum(),
                    viewportCancels.sum(),
                    queued.sum(),
                    inFlightCount.get(),
                    pending.size(),
                    rendered.sum(),
                    failed.sum(),
                    fallbackUsed.sum(),
                    cancelled.sum(),
                    documentTimeouts.sum(),
                    renderedJavaFx.sum(),
                    renderedDocument.sum(),
                    renderedImageIo.sum(),
                    diskCacheHit.sum(),
                    diskCacheMiss.sum(),
                    diskCacheWrite.sum(),
                    diskCacheWriteFail.sum(),
                    diskCachePruned.sum(),
                    diskCachePruneRuns.sum(),
                    diskCacheStartupPruneRuns.sum(),
                    diskCacheCorruptDeletes.sum(),
                    diskCacheStartupClearRuns.sum(),
                    diskCacheStartupClearDeletes.sum(),
                    diskCacheManifestWrites.sum(),
                    diskCacheManifestWriteFail.sum(),
                    diskCacheManifestMismatchDetected.sum(),
                    diskCacheManifestMismatchClears.sum(),
                    onOff(ENABLE_DISK_CACHE),
                    onOff(DISK_CACHE_DOCUMENTS_ONLY),
                    onOff(CLEAR_DISK_CACHE_ON_STARTUP),
                    onOff(CLEAR_ON_MANIFEST_MISMATCH),
                    avgMs,
                    cache.debugString(),
                    onOff(ENABLE_PDF),
                    onOff(ENABLE_WORD),
                    onOff(ENABLE_EXCEL),
                    onOff(ENABLE_PPTX)
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
                + " req=" + requested.sum()
                + " hit=" + hit.sum()
                + " miss=" + miss.sum()
                + " coalesced=" + coalesced.sum()
                + " bucketReuse=" + bucketReuse.sum()
                + " throttled=" + throttleDeferrals.sum()
                + " viewportCancels=" + viewportCancels.sum()
                + " queued=" + queued.sum()
                + " rendered=" + rendered.sum()
                + " failed=" + failed.sum()
                + " fallback=" + fallbackUsed.sum()
                + " cancelled=" + cancelled.sum()
                + " docTimeouts=" + documentTimeouts.sum()
                + " provider{fx=" + renderedJavaFx.sum() + ",doc=" + renderedDocument.sum() + ",imageio=" + renderedImageIo.sum() + "}"
                + " disk{hit=" + diskCacheHit.sum()
                + ",miss=" + diskCacheMiss.sum()
                + ",write=" + diskCacheWrite.sum()
                + ",writeFail=" + diskCacheWriteFail.sum()
                + ",pruned=" + diskCachePruned.sum()
                + ",pruneRuns=" + diskCachePruneRuns.sum()
                + ",startupPruneRuns=" + diskCacheStartupPruneRuns.sum()
                + ",corruptDeletes=" + diskCacheCorruptDeletes.sum()
                + ",startupClearRuns=" + diskCacheStartupClearRuns.sum()
                + ",startupClearDeletes=" + diskCacheStartupClearDeletes.sum()
                + ",manifestWrites=" + diskCacheManifestWrites.sum()
                + ",manifestWriteFail=" + diskCacheManifestWriteFail.sum()
                + ",manifestMismatchDetected=" + diskCacheManifestMismatchDetected.sum()
                + ",manifestMismatchClears=" + diskCacheManifestMismatchClears.sum()
                + ",enabled=" + onOff(ENABLE_DISK_CACHE)
                + ",docsOnly=" + onOff(DISK_CACHE_DOCUMENTS_ONLY)
                + ",clearOnStartup=" + onOff(CLEAR_DISK_CACHE_ON_STARTUP)
                + ",clearOnManifestMismatch=" + onOff(CLEAR_ON_MANIFEST_MISMATCH) + "}"
                + " gates{pdf=" + onOff(ENABLE_PDF)
                + ",word=" + onOff(ENABLE_WORD)
                + ",excel=" + onOff(ENABLE_EXCEL)
                + ",pptx=" + onOff(ENABLE_PPTX) + "}"
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

    public void noteViewportCancellation() {
        viewportCancels.increment();
    }

    /**
     * Enables/disables thumbnail decoding.
     *
     * <p>We only support transitioning from disabled -> enabled for this app lifecycle.</p>
     */
    public void setEnabled(boolean enable) {
        if (enable && enabled.compareAndSet(false, true)) {
            enabledGate.complete(null);
            scheduleStartupDiskCacheMaintenance();
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
                } catch (Exception ignored) {
                }
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
                    onDecodeTaskDropped(pr);
                    over--;
                }
            }
            it = q.iterator();
            while (it.hasNext() && over > 0) {
                Runnable r = it.next();
                if (r instanceof PrioritizedRunnable pr && pr.priority == RequestPriority.VISIBLE.p) {
                    it.remove();
                    onDecodeTaskDropped(pr);
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
     * Best-effort background warmup for a bounded set of current-folder thumbnail candidates.
     * Existing cache/in-flight coalescing and generation cancellation semantics still apply.
     */
    public void warm(Iterable<Path> paths, int sizePx) {
        if (paths == null) {
            return;
        }
        for (Path path : paths) {
            if (path == null) {
                continue;
            }
            try {
                request(path, sizePx, RequestPriority.BACKGROUND).whenComplete((img, ex) -> {
                    // detached best-effort warmup: nothing else to do
                });
            } catch (Throwable ignored) {
                // fail-open: warmup must never affect normal icon fallback behavior
            }
        }
    }

    /**
     * Request a thumbnail with explicit priority lane.
     */
    public CompletableFuture<Image> request(Path path, int sizePx, RequestPriority priority) {
        if (path == null) return CompletableFuture.completedFuture(null);
        final int clamped = Math.max(12, Math.min(512, sizePx));
        final String ext = ImageSupport.extensionOf(path);
        final ThumbnailProvider provider = providerFor(ext);
        if (provider == ThumbnailProvider.UNSUPPORTED || provider == ThumbnailProvider.DISABLED) {
            return CompletableFuture.completedFuture(null);
        }

        final RequestPriority pr = (priority == null) ? RequestPriority.BACKGROUND : priority;

        // Defer actual decoding until after first UI render enablement.
        return enabledGate.thenCompose(v -> requestEnabled(path, ext, clamped, pr));
    }

    private CompletableFuture<Image> requestEnabled(Path path, String ext, int sizePx, RequestPriority pr) {
        Objects.requireNonNull(ext, "ext");

        requested.increment();

        final Path abs = path.toAbsolutePath();
        final String absKey = abs.toString();
        final int sizeBucketPx = sizeBucket(sizePx);
        final ThumbKey key = new ThumbKey(absKey, sizeBucketPx);

        final long lastMod = safeLastModifiedMs(abs);
        final long fileSizeBytes = safeFileSizeBytes(abs);

        CachedThumb cached = cache.get(key);
        if (cached != null && cached.image != null) {
            if (cached.lastModifiedMs == lastMod && cached.fileSizeBytes == fileSizeBytes) {
                hit.increment();
                if (sizeBucketPx != sizePx) {
                    bucketReuse.increment();
                }
                return CompletableFuture.completedFuture(cached.image);
            }
            cache.remove(key);
        }

        // Reuse nearest larger cached thumbnail when switching to smaller views (avoid re-decode).
        CachedThumb larger = cache.getNearestLarger(absKey, sizeBucketPx);
        if (larger != null && larger.image != null
                && larger.lastModifiedMs == lastMod
                && larger.fileSizeBytes == fileSizeBytes) {
            hit.increment();
            bucketReuse.increment();
            return CompletableFuture.completedFuture(larger.image);
        }

        Image diskCached = readDiskCachedThumbnail(abs, ext, sizeBucketPx, lastMod, fileSizeBytes, providerFor(ext));
        if (diskCached != null) {
            hit.increment();
            cache.put(key, new CachedThumb(lastMod, fileSizeBytes, diskCached, approxBytes(diskCached)));
            return CompletableFuture.completedFuture(diskCached);
        }

        miss.increment();

        // Debounced in-flight computation: multiple requests for the same (path,size bucket) during
        // rapid scrolling collapse into a single decode.
        final boolean[] created = {false};
        CompletableFuture<Image> shared = inFlight.computeIfAbsent(key, k -> {
            created[0] = true;
            return new CompletableFuture<>();
        });
        if (!created[0]) {
            coalesced.increment();
        }

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

        // Another request already has this key queued or actively decoding.
        if (!created[0] && (running.contains(key) || pending.containsKey(key))) {
            return detached;
        }

        // (Re)schedule the decode after a short debounce delay.
        ScheduledFuture<?> prev = pending.get(key);
        if (prev != null) prev.cancel(false);

        final long genAtSchedule = generation.get();
        final long seqNo = seq.incrementAndGet();

        ScheduledFuture<?> scheduled = scheduler.schedule(() ->
                startQueuedDecode(key, abs, ext, sizeBucketPx, pr, lastMod, fileSizeBytes, genAtSchedule, seqNo),
                DEBOUNCE_MS, TimeUnit.MILLISECONDS);

        pending.put(key, scheduled);
        return detached;
    }

    private void startQueuedDecode(ThumbKey key,
                                   Path abs,
                                   String ext,
                                   int sizePx,
                                   RequestPriority pr,
                                   long lastMod,
                                   long fileSizeBytes,
                                   long genAtSchedule,
                                   long seqNo) {
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

        if (!tryAcquireStartSlot(pr)) {
            throttleDeferrals.increment();
            ScheduledFuture<?> retry = scheduler.schedule(
                    () -> startQueuedDecode(key, abs, ext, sizePx, pr, lastMod, fileSizeBytes, genAtSchedule, seq.incrementAndGet()),
                    THROTTLE_RETRY_MS,
                    TimeUnit.MILLISECONDS
            );
            pending.put(key, retry);
            return;
        }

        running.add(key);
        queued.increment();
        long startNanos = System.nanoTime();
        inFlightCount.incrementAndGet();

        // Hard cap queue depth (soak/leak guardrail).
        trimDecodeQueueIfNeeded();

        // Submit a comparable task so the executor orders by priority.
        decodeExecutor.execute(new PrioritizedRunnable(pr, seqNo, key, current, () -> {
            LoadResult result = null;
            try {
                // If preempted mid-flight, skip decode.
                if (generation.get() != genAtSchedule) return;

                result = loadThumbnail(abs, ext, sizePx);
            } catch (Throwable ignored) {
                result = new LoadResult(null, providerFor(ext));
            } finally {
                try {
                    long dur = System.nanoTime() - startNanos;
                    decodeCount.increment();
                    decodeNanos.add(dur);

                    if (generation.get() != genAtSchedule) {
                        // stale completion: do not cache
                        current.complete(null);
                        fallbackUsed.increment();
                        failed.increment();
                        return;
                    }

                    if (result != null && result.hasImage()) {
                        long approxBytes = approxBytes(result.image());
                        cache.put(key, new CachedThumb(lastMod, fileSizeBytes, result.image(), approxBytes));
                        persistDiskCachedThumbnail(abs, ext, sizePx, lastMod, fileSizeBytes, result.provider(), result.image());
                        rendered.increment();
                        incrementProviderCounter(result.provider());
                        current.complete(result.image());
                    } else {
                        failed.increment();
                        fallbackUsed.increment();
                        current.complete(null);
                    }
                } catch (Throwable ignored) {
                    failed.increment();
                    fallbackUsed.increment();
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
    }

    private boolean tryAcquireStartSlot(RequestPriority pr) {
        if (pr == RequestPriority.USER_ACTION) {
            return true;
        }
        final long now = System.currentTimeMillis();
        while (true) {
            long epoch = startWindowEpochMs.get();
            if (now - epoch >= START_WINDOW_MS) {
                if (startWindowEpochMs.compareAndSet(epoch, now)) {
                    startWindowCount.set(0);
                    continue;
                }
                continue;
            }
            int current = startWindowCount.get();
            if (current >= MAX_STARTS_PER_WINDOW) {
                return false;
            }
            if (startWindowCount.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void completeAndCleanupPreempted(ThumbKey key) {
        ScheduledFuture<?> pf = pending.remove(key);
        if (pf != null) pf.cancel(false);

        CompletableFuture<Image> s = inFlight.remove(key);
        if (s != null && !s.isDone()) s.complete(null);

        cancelled.increment();
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
                cancelled.increment();
                shared.complete(null);
            }
        }
    }

    private int subscriberCount(ThumbKey key) {
        AtomicInteger ai = subscribers.get(key);
        return ai == null ? 0 : ai.get();
    }

    private void onDecodeTaskDropped(PrioritizedRunnable pr) {
        if (pr == null) return;
        cancelled.increment();
        try {
            running.remove(pr.key);
            CompletableFuture<Image> current = inFlight.remove(pr.key);
            if (current != null && !current.isDone()) {
                current.complete(null);
            }
        } catch (Throwable ignored) {
        } finally {
            ScheduledFuture<?> pf = pending.remove(pr.key);
            if (pf != null) {
                try {
                    pf.cancel(false);
                } catch (Throwable ignored) {
                }
            }
            subscribers.remove(pr.key);
        }
    }

    private static long safeLastModifiedMs(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static long safeFileSizeBytes(Path path) {
        try {
            return Files.size(path);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private LoadResult loadThumbnail(Path path, String ext, int sizePx) {
        try {
            if (!Files.isRegularFile(path)) {
                return new LoadResult(null, ThumbnailProvider.UNSUPPORTED);
            }
        } catch (Exception ex) {
            return new LoadResult(null, ThumbnailProvider.UNSUPPORTED);
        }

        ThumbnailProvider provider = providerFor(ext);
        try {
            return switch (provider) {
                case JAVAFX_NATIVE -> new LoadResult(loadJavaFxNativeThumbnail(path, sizePx), provider);
                case THUMBNAILS4J_DOCUMENT -> new LoadResult(loadDocumentThumbnail(path, ext, sizePx), provider);
                case IMAGEIO -> new LoadResult(loadImageIoThumbnail(path, sizePx), provider);
                case DISABLED, UNSUPPORTED -> new LoadResult(null, provider);
            };
        } catch (Throwable ignored) {
            return new LoadResult(null, provider);
        }
    }

    private Image loadJavaFxNativeThumbnail(Path path, int sizePx) {
        // Use JavaFX decoder directly. We are already off the FX thread.
        String url = path.toUri().toString();
        Image img = new Image(url, sizePx, sizePx, true, true, false);
        if (img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) {
            return null;
        }
        return img;
    }

    private Image loadImageIoThumbnail(Path path, int sizePx) {
        BufferedImage bi;
        try (InputStream in = Files.newInputStream(path)) {
            bi = ImageIO.read(in);
        } catch (Throwable ignored) {
            return null;
        }
        if (bi == null) return null;

        BufferedImage scaled = scaleToFitSquare(bi, sizePx);
        return SwingFXUtils.toFXImage(scaled, null);
    }

    private Image loadDocumentThumbnail(Path path, String ext, int sizePx) {
        Future<BufferedImage> future = null;
        try {
            future = documentExecutor.submit(() -> renderDocumentThumbnail(path, ext, sizePx));
            BufferedImage bi = future.get(DOCUMENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (bi == null) {
                return null;
            }
            return SwingFXUtils.toFXImage(bi, null);
        } catch (TimeoutException te) {
            documentTimeouts.increment();
            if (future != null) {
                try {
                    future.cancel(true);
                } catch (Throwable ignored) {
                }
            }
            return null;
        } catch (Throwable ignored) {
            if (future != null) {
                try {
                    future.cancel(true);
                } catch (Throwable ignored2) {
                }
            }
            return null;
        }
    }

    private BufferedImage renderDocumentThumbnail(Path path, String ext, int sizePx) {
        try {
            Thumbnailer thumbnailer = newDocumentThumbnailer(ext);
            if (thumbnailer == null) {
                return null;
            }
            java.util.List<?> thumbs = thumbnailer.getThumbnails(
                    path.toFile(),
                    java.util.List.of(new Dimensions(sizePx, sizePx))
            );
            if (thumbs == null || thumbs.isEmpty()) {
                return null;
            }
            Object first = thumbs.get(0);
            if (!(first instanceof BufferedImage bi)) {
                return null;
            }
            return bi;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Thumbnailer newDocumentThumbnailer(String ext) {
        if (ext == null) {
            return null;
        }
        String normalized = ext.toLowerCase(Locale.ROOT);
        if (!THUMBNAILS4J_EXTENSIONS.contains(normalized)) {
            return null;
        }
        if (!isDocumentExtensionEnabled(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "doc" -> new DOCThumbnailer();
            case "docx" -> new DOCXThumbnailer();
            case "pdf" -> new PDFThumbnailer();
            case "pptx" -> new PPTXThumbnailer();
            case "xls" -> new XLSThumbnailer();
            case "xlsx" -> new XLSXThumbnailer();
            default -> null;
        };
    }

    private ThumbnailProvider providerFor(String ext) {
        if (ext == null || ext.isBlank()) {
            return ThumbnailProvider.UNSUPPORTED;
        }
        String normalized = ext.toLowerCase(Locale.ROOT);
        if (ImageSupport.isJavaFxNativeExtension(normalized)) {
            return ThumbnailProvider.JAVAFX_NATIVE;
        }
        if (ImageSupport.isDocumentThumbnailExtension(normalized)) {
            return isDocumentExtensionEnabled(normalized)
                    ? ThumbnailProvider.THUMBNAILS4J_DOCUMENT
                    : ThumbnailProvider.DISABLED;
        }
        if (ImageSupport.isThumbCandidateExtension(normalized)) {
            return ThumbnailProvider.IMAGEIO;
        }
        return ThumbnailProvider.UNSUPPORTED;
    }

    private boolean isDocumentExtensionEnabled(String normalizedExt) {
        return switch (normalizedExt) {
            case "pdf" -> ENABLE_PDF;
            case "doc", "docx" -> ENABLE_WORD;
            case "xls", "xlsx" -> ENABLE_EXCEL;
            case "pptx" -> ENABLE_PPTX;
            default -> false;
        };
    }

    private void incrementProviderCounter(ThumbnailProvider provider) {
        if (provider == null) return;
        switch (provider) {
            case JAVAFX_NATIVE -> renderedJavaFx.increment();
            case THUMBNAILS4J_DOCUMENT -> renderedDocument.increment();
            case IMAGEIO -> renderedImageIo.increment();
            default -> {
            }
        }
    }

    private boolean isDiskCacheEligible(ThumbnailProvider provider) {
        if (!ENABLE_DISK_CACHE) {
            return false;
        }
        if (provider == null || provider == ThumbnailProvider.UNSUPPORTED || provider == ThumbnailProvider.DISABLED) {
            return false;
        }
        if (DISK_CACHE_DOCUMENTS_ONLY) {
            return provider == ThumbnailProvider.THUMBNAILS4J_DOCUMENT;
        }
        return true;
    }

    private void scheduleStartupDiskCacheMaintenance() {
        if (!ENABLE_DISK_CACHE || diskCacheDir == null) {
            return;
        }
        if (!startupDiskPruneScheduled.compareAndSet(false, true)) {
            return;
        }
        scheduler.schedule(() -> {
            try {
                boolean cleared = false;
                if (CLEAR_DISK_CACHE_ON_STARTUP) {
                    clearPersistentDiskCacheInternal(true);
                    cleared = true;
                }
                if (!cleared && maybeClearDiskCacheOnManifestMismatch()) {
                    cleared = true;
                }
                if (ENABLE_DISK_CACHE_MANIFEST) {
                    writeDiskCacheManifest();
                }
                if (ENABLE_STARTUP_PRUNE) {
                    diskCacheStartupPruneRuns.increment();
                    pruneDiskCache();
                }
            } finally {
                startupDiskPruneScheduled.set(false);
            }
        }, STARTUP_PRUNE_DELAY_MS, TimeUnit.MILLISECONDS);
    }


    private boolean maybeClearDiskCacheOnManifestMismatch() {
        if (!ENABLE_DISK_CACHE_MANIFEST || !CLEAR_ON_MANIFEST_MISMATCH) {
            return false;
        }
        String savedFingerprint = readDiskCacheManifestFingerprint();
        if (savedFingerprint == null || savedFingerprint.isBlank()) {
            return false;
        }
        String currentFingerprint = currentDiskCacheCompatibilityFingerprint();
        if (Objects.equals(savedFingerprint, currentFingerprint)) {
            return false;
        }
        diskCacheManifestMismatchDetected.increment();
        clearPersistentDiskCacheInternal(false);
        diskCacheManifestMismatchClears.increment();
        return true;
    }

    private Path diskCacheManifestFile() {
        Path dir = diskCacheDir;
        return dir == null ? null : dir.resolve(DISK_CACHE_MANIFEST_FILE_NAME);
    }

    private String currentDiskCacheCompatibilityFingerprint() {
        return stableCacheHash(String.join("|",
                DISK_CACHE_COMPAT_VERSION,
                onOff(ENABLE_PDF),
                onOff(ENABLE_WORD),
                onOff(ENABLE_EXCEL),
                onOff(ENABLE_PPTX),
                onOff(DISK_CACHE_DOCUMENTS_ONLY),
                Long.toString(DOCUMENT_TIMEOUT_MS),
                String.join(",", THUMBNAILS4J_EXTENSIONS.stream().sorted().toList())
        ));
    }

    private String readDiskCacheManifestFingerprint() {
        Path manifest = diskCacheManifestFile();
        if (manifest == null || !Files.isRegularFile(manifest)) {
            return null;
        }
        java.util.Properties props = new java.util.Properties();
        try (InputStream in = Files.newInputStream(manifest, StandardOpenOption.READ)) {
            props.load(in);
            return props.getProperty("fingerprint", "").trim();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void writeDiskCacheManifest() {
        if (!ENABLE_DISK_CACHE_MANIFEST) {
            return;
        }
        Path manifest = diskCacheManifestFile();
        if (manifest == null) {
            return;
        }
        try {
            Files.createDirectories(manifest.getParent());
            java.util.Properties props = new java.util.Properties();
            props.setProperty("manifestVersion", "1");
            props.setProperty("compatVersion", DISK_CACHE_COMPAT_VERSION);
            props.setProperty("fingerprint", currentDiskCacheCompatibilityFingerprint());
            props.setProperty("enabled", Boolean.toString(ENABLE_DISK_CACHE));
            props.setProperty("documentsOnly", Boolean.toString(DISK_CACHE_DOCUMENTS_ONLY));
            props.setProperty("clearOnStartup", Boolean.toString(CLEAR_DISK_CACHE_ON_STARTUP));
            props.setProperty("clearOnManifestMismatch", Boolean.toString(CLEAR_ON_MANIFEST_MISMATCH));
            props.setProperty("startupPruneEnabled", Boolean.toString(ENABLE_STARTUP_PRUNE));
            props.setProperty("deleteCorruptOnRead", Boolean.toString(DELETE_CORRUPT_ON_READ));
            props.setProperty("enablePdf", Boolean.toString(ENABLE_PDF));
            props.setProperty("enableWord", Boolean.toString(ENABLE_WORD));
            props.setProperty("enableExcel", Boolean.toString(ENABLE_EXCEL));
            props.setProperty("enablePptx", Boolean.toString(ENABLE_PPTX));
            props.setProperty("documentTimeoutMs", Long.toString(DOCUMENT_TIMEOUT_MS));
            props.setProperty("maxAgeDays", Integer.toString(DISK_CACHE_MAX_AGE_DAYS));
            props.setProperty("maxBytes", Long.toString(DISK_CACHE_MAX_BYTES));
            props.setProperty("generatedAtMs", Long.toString(System.currentTimeMillis()));
            props.setProperty("formats", String.join(",", THUMBNAILS4J_EXTENSIONS.stream().sorted().toList()));
            Path tmp = manifest.resolveSibling(manifest.getFileName().toString() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                props.store(out, "FileExplorer thumbnail cache manifest");
            }
            try {
                Files.move(tmp, manifest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable moveIgnored) {
                Files.move(tmp, manifest, StandardCopyOption.REPLACE_EXISTING);
            }
            diskCacheManifestWrites.increment();
        } catch (Throwable ignored) {
            diskCacheManifestWriteFail.increment();
        }
    }

    public void clearPersistentDiskCache() {
        clearPersistentDiskCacheInternal(false);
    }

    private void clearPersistentDiskCacheInternal(boolean startupTriggered) {
        Path dir = diskCacheDir;
        if (dir == null) {
            return;
        }
        long deleted = 0L;
        try {
            cache.clearAll();
        } catch (Throwable ignored) {
        }
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
                java.util.List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path p : paths) {
                    try {
                        if (Files.deleteIfExists(p)) {
                            deleted++;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (startupTriggered) {
                diskCacheStartupClearRuns.increment();
                diskCacheStartupClearDeletes.add(deleted);
            }
        }
    }

    private void deleteDiskCacheFileQuietly(Path cacheFile) {
        if (!DELETE_CORRUPT_ON_READ || cacheFile == null) {
            return;
        }
        try {
            if (Files.deleteIfExists(cacheFile)) {
                diskCacheCorruptDeletes.increment();
            }
        } catch (Throwable ignored) {
        }
    }

    private Image readDiskCachedThumbnail(Path path, String ext, int sizePx, long lastMod, long fileSizeBytes, ThumbnailProvider provider) {
        if (!isDiskCacheEligible(provider)) {
            return null;
        }
        Path cacheFile = diskCacheFile(path, ext, sizePx, lastMod, fileSizeBytes, provider);
        if (cacheFile == null) {
            return null;
        }
        if (!Files.isRegularFile(cacheFile)) {
            diskCacheMiss.increment();
            return null;
        }
        try {
            if (Files.size(cacheFile) <= 0L) {
                diskCacheMiss.increment();
                deleteDiskCacheFileQuietly(cacheFile);
                return null;
            }
        } catch (Throwable ignored) {
            diskCacheMiss.increment();
            deleteDiskCacheFileQuietly(cacheFile);
            return null;
        }
        try (InputStream in = Files.newInputStream(cacheFile, StandardOpenOption.READ)) {
            Image img = new Image(in);
            if (img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) {
                diskCacheMiss.increment();
                deleteDiskCacheFileQuietly(cacheFile);
                return null;
            }
            diskCacheHit.increment();
            return img;
        } catch (Throwable ignored) {
            diskCacheMiss.increment();
            deleteDiskCacheFileQuietly(cacheFile);
            return null;
        }
    }

    private void persistDiskCachedThumbnail(Path path, String ext, int sizePx, long lastMod, long fileSizeBytes, ThumbnailProvider provider, Image image) {
        if (image == null || !isDiskCacheEligible(provider)) {
            return;
        }
        Path cacheFile = diskCacheFile(path, ext, sizePx, lastMod, fileSizeBytes, provider);
        if (cacheFile == null) {
            return;
        }
        try {
            Files.createDirectories(cacheFile.getParent());
            BufferedImage bi = SwingFXUtils.fromFXImage(image, null);
            if (bi == null) {
                diskCacheWriteFail.increment();
                return;
            }
            Path tmp = cacheFile.resolveSibling(cacheFile.getFileName().toString() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                if (!ImageIO.write(bi, "png", out)) {
                    diskCacheWriteFail.increment();
                    try { Files.deleteIfExists(tmp); } catch (Throwable ignored) {}
                    return;
                }
            }
            try {
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable moveIgnored) {
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
            diskCacheWrite.increment();
            maybeQueueDiskCachePrune();
        } catch (Throwable ignored) {
            diskCacheWriteFail.increment();
        }
    }

    private void maybeQueueDiskCachePrune() {
        if (!ENABLE_DISK_CACHE || diskCacheDir == null) {
            return;
        }
        if (diskWritesSincePrune.incrementAndGet() < DISK_CACHE_PRUNE_INTERVAL_WRITES) {
            return;
        }
        diskWritesSincePrune.set(0);
        if (!diskPruneQueued.compareAndSet(false, true)) {
            return;
        }
        scheduler.execute(() -> {
            try {
                pruneDiskCache();
            } finally {
                diskPruneQueued.set(false);
            }
        });
    }

    private void pruneDiskCache() {
        if (diskCacheDir == null) {
            return;
        }
        diskCachePruneRuns.increment();
        try {
            if (!Files.isDirectory(diskCacheDir)) {
                return;
            }
            long now = System.currentTimeMillis();
            long maxAgeMs = Math.max(1L, DISK_CACHE_MAX_AGE_DAYS) * 24L * 60L * 60L * 1000L;
            java.util.List<Path> files = new ArrayList<>();
            try (java.util.stream.Stream<Path> stream = Files.walk(diskCacheDir)) {
                stream.filter(Files::isRegularFile).forEach(files::add);
            }
            long totalBytes = 0L;
            for (Path f : files) {
                try {
                    long ageMs = now - Files.getLastModifiedTime(f).toMillis();
                    if (ageMs > maxAgeMs) {
                        if (Files.deleteIfExists(f)) {
                            diskCachePruned.increment();
                        }
                        continue;
                    }
                    totalBytes += Files.size(f);
                } catch (Throwable ignored) {
                }
            }
            if (totalBytes <= DISK_CACHE_MAX_BYTES) {
                return;
            }
            files.clear();
            try (java.util.stream.Stream<Path> stream = Files.walk(diskCacheDir)) {
                stream.filter(Files::isRegularFile).forEach(files::add);
            }
            files.sort(Comparator.comparingLong(this::safeFileMtimeForSort));
            for (Path f : files) {
                if (totalBytes <= DISK_CACHE_MAX_BYTES) {
                    break;
                }
                try {
                    long size = Files.size(f);
                    if (Files.deleteIfExists(f)) {
                        totalBytes -= size;
                        diskCachePruned.increment();
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private long safeFileMtimeForSort(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Throwable ignored) {
            return Long.MAX_VALUE;
        }
    }

    private Path diskCacheFile(Path path, String ext, int sizePx, long lastMod, long fileSizeBytes, ThumbnailProvider provider) {
        Path dir = diskCacheDir;
        if (dir == null || path == null) {
            return null;
        }
        String hash = stableCacheHash(path.toAbsolutePath().toString() + "|" + ext + "|" + sizePx + "|" + lastMod + "|" + fileSizeBytes + "|" + provider.tag);
        if (hash.length() < 8) {
            return null;
        }
        return dir.resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4)).resolve(hash + ".png");
    }

    private Path resolveDiskCacheDir() {
        if (!ENABLE_DISK_CACHE) {
            return null;
        }
        String configured = System.getProperty("fileexplorer.thumb.diskCache.dir", "").trim();
        try {
            if (!configured.isEmpty()) {
                return Path.of(configured);
            }
        } catch (Throwable ignored) {
        }
        String userHome = System.getProperty("user.home", "").trim();
        if (!userHome.isEmpty()) {
            try {
                return Path.of(userHome, ".fileexplorer", "thumbcache-v2");
            } catch (Throwable ignored) {
            }
        }
        String tmp = System.getProperty("java.io.tmpdir", "").trim();
        if (!tmp.isEmpty()) {
            try {
                return Path.of(tmp, "fileexplorer-thumbcache-v2");
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String stableCacheHash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                int v = b & 0xFF;
                if (v < 16) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }


    public String thumbnailDiagnosticsSnapshot() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("Thumbnail diagnostics\n");
        sb.append("summary=").append(debugString()).append('\n');
        sb.append("diskCache=").append(persistentDiskCacheDebugString()).append('\n');
        sb.append("manifest=\n").append(diskCacheManifestSnapshot());
        return sb.toString();
    }

    public String diskCacheManifestSnapshot() {
        Path manifest = diskCacheManifestFile();
        if (manifest == null) {
            return "<disabled>\n";
        }
        if (!Files.isRegularFile(manifest)) {
            return "<missing>\n";
        }
        try {
            return Files.readString(manifest);
        } catch (Throwable ignored) {
            return "<unreadable>\n";
        }
    }
    public String persistentDiskCacheDebugString() {
        Path dir = diskCacheDir;
        StringBuilder sb = new StringBuilder(256);
        sb.append("dir=").append(dir == null ? "<disabled>" : dir.toAbsolutePath());
        sb.append(" exists=").append(dir != null && Files.exists(dir));
        sb.append(" enabled=").append(onOff(ENABLE_DISK_CACHE));
        sb.append(" docsOnly=").append(onOff(DISK_CACHE_DOCUMENTS_ONLY));
        sb.append(" clearOnStartup=").append(onOff(CLEAR_DISK_CACHE_ON_STARTUP));
        sb.append(" clearOnManifestMismatch=").append(onOff(CLEAR_ON_MANIFEST_MISMATCH));
        sb.append(" startupPrune=").append(onOff(ENABLE_STARTUP_PRUNE));
        sb.append(" manifestEnabled=").append(onOff(ENABLE_DISK_CACHE_MANIFEST));
        sb.append(" compatVersion=").append(DISK_CACHE_COMPAT_VERSION);
        sb.append(" fingerprint=").append(currentDiskCacheCompatibilityFingerprint());
        Path manifest = diskCacheManifestFile();
        sb.append(" manifest=").append(manifest == null ? "<none>" : manifest.toAbsolutePath());
        sb.append(" manifestExists=").append(manifest != null && Files.exists(manifest));
        sb.append(" maxBytes=").append(DISK_CACHE_MAX_BYTES);
        sb.append(" maxAgeDays=").append(DISK_CACHE_MAX_AGE_DAYS);
        if (dir != null && Files.isDirectory(dir)) {
            long fileCount = 0L;
            long totalBytes = 0L;
            long newestMs = -1L;
            try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
                java.util.Iterator<Path> it = stream.filter(Files::isRegularFile).iterator();
                while (it.hasNext()) {
                    Path p = it.next();
                    fileCount++;
                    try {
                        totalBytes += Files.size(p);
                    } catch (Throwable ignored) {
                    }
                    try {
                        newestMs = Math.max(newestMs, Files.getLastModifiedTime(p).toMillis());
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            sb.append(" files=").append(fileCount);
            sb.append(" bytes=").append(totalBytes);
            sb.append(" newestMs=").append(newestMs);
        }
        return sb.toString();
    }

    private double averageDecodeMs() {
        long dc = decodeCount.sum();
        if (dc <= 0L) {
            return 0.0;
        }
        return (decodeNanos.sum() / 1_000_000.0) / (double) dc;
    }

    private static String onOff(boolean enabled) {
        return enabled ? "on" : "off";
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

    private static int sizeBucket(int sizePx) {
        int clamped = Math.max(12, Math.min(512, sizePx));
        for (int bucket : SIZE_BUCKETS_PX) {
            if (clamped <= bucket) {
                return bucket;
            }
        }
        return 512;
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
        documentExecutor.shutdownNow();
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
        final ThumbKey key;
        final CompletableFuture<Image> future;
        final Runnable delegate;

        PrioritizedRunnable(RequestPriority pr, long seqNo, ThumbKey key, CompletableFuture<Image> future, Runnable delegate) {
            this.priority = (pr == null) ? RequestPriority.BACKGROUND.p : pr.p;
            this.seqNo = seqNo;
            this.key = key;
            this.future = future;
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

    private static boolean boolProp(String key, boolean def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> def;
        };
    }
}
