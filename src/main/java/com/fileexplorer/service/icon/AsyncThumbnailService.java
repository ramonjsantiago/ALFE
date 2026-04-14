package com.fileexplorer.service.icon;

import com.fileexplorer.util.ImageSupport;
import co.elastic.thumbnails4j.core.Dimensions;
import co.elastic.thumbnails4j.core.Thumbnailer;
import co.elastic.thumbnails4j.doc.DOCThumbnailer;
import co.elastic.thumbnails4j.docx.DOCXThumbnailer;
import co.elastic.thumbnails4j.pptx.PPTXThumbnailer;
import co.elastic.thumbnails4j.xls.XLSThumbnailer;
import co.elastic.thumbnails4j.xlsx.XLSXThumbnailer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
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
import java.util.logging.Level;
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

    /** Base document concurrency knob retained for compatibility with earlier hotfixes. */
    private static final int MAX_DOCUMENT_CONCURRENT =
            intProp("fileexplorer.thumb.doc.maxConcurrent", 1, 1, 4);

    /** Dedicated Office-family lane so slow PDF work cannot monopolize document thumbnails. */
    private static final int MAX_OFFICE_DOCUMENT_CONCURRENT =
            intProp("fileexplorer.thumb.doc.office.maxConcurrent", MAX_DOCUMENT_CONCURRENT, 1, 4);

    /** Dedicated PDF lane so timed-out renders stay quarantined away from Word/Excel/PPT work. */
    private static final int MAX_PDF_DOCUMENT_CONCURRENT =
            intProp("fileexplorer.thumb.doc.pdf.maxConcurrent", 1, 1, 4);

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

    /** Predictive budgeting while the viewport is actively moving. */
    private static final long VIEWPORT_SETTLE_MS =
            longProp("fileexplorer.thumb.viewportSettleMs", 110L, 25L, 4000L);

    private static final long MOVING_BACKGROUND_EXTRA_DELAY_MS =
            longProp("fileexplorer.thumb.movingBackgroundExtraDelayMs", 120L, 0L, 4000L);

    private static final int MOVING_VISIBLE_BACKLOG_LIMIT =
            intProp("fileexplorer.thumb.movingVisibleBacklogLimit", 24, 4, 512);

    /** Delay before background ImageIO capability discovery runs after thumbnails are enabled. */
    private static final long CAPABILITY_INIT_DELAY_MS =
            longProp("fileexplorer.thumb.capabilityInitDelayMs", 1200L, 0L, 30000L);

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

    /** One-shot guard for missing JPEG2000/JPX decoder diagnostics. */
    private static final AtomicBoolean LOGGED_MISSING_JPEG2000_READER = new AtomicBoolean(false);

    /** One-shot guard for missing JBIG2 decoder diagnostics. */
    private static final AtomicBoolean LOGGED_MISSING_JBIG2_READER = new AtomicBoolean(false);

    /** Cached capability probe for JPEG2000/JPX ImageIO reader availability. */
    private static volatile Boolean JPEG2000_READER_AVAILABLE;

    /** Cached capability probe for JBIG2 ImageIO reader availability. */
    private static volatile Boolean JBIG2_READER_AVAILABLE;

    /** Deduplicates recurring PDF thumbnail failure diagnostics. */
    private static final Set<String> LOGGED_PDF_FAILURE_SIGNATURES = ConcurrentHashMap.newKeySet();

    /** Deduplicates recurring PDF timeout diagnostics. */
    private static final Set<String> LOGGED_PDF_TIMEOUT_SIGNATURES = ConcurrentHashMap.newKeySet();

    /** Deduplicates recurring PDF oversize diagnostics. */
    private static final Set<String> LOGGED_PDF_OVERSIZE_SIGNATURES = ConcurrentHashMap.newKeySet();

    /** Deduplicates recurring interrupt/cancellation diagnostics for PDF thumbnails. */
    private static final Set<String> LOGGED_PDF_INTERRUPT_SIGNATURES = ConcurrentHashMap.newKeySet();

    /** Deduplicates recurring large-document recovery diagnostics for PDF thumbnails. */
    private static final Set<String> LOGGED_PDF_LARGE_DOC_SIGNATURES = ConcurrentHashMap.newKeySet();

    /** Short-lived cooldown for PDFs that recently timed out during thumbnail rendering. */
    private static final Map<String, PdfCooldownState> PDF_TIMEOUT_COOLDOWN_UNTIL_MS = new ConcurrentHashMap<>();

    /** Short-lived cooldown for Office-family documents that recently failed thumbnail rendering. */
    private static final Map<String, DocumentCooldownState> DOCUMENT_FAILURE_COOLDOWN_UNTIL_MS = new ConcurrentHashMap<>();

    /** Deduplicates recurring Office-family document thumbnail failure diagnostics. */
    private static final Set<String> LOGGED_DOCUMENT_FAILURE_SIGNATURES = ConcurrentHashMap.newKeySet();

    /** Rolling PDF render-history snapshots used for adaptive timeout and recovery planning. */
    private static final Map<String, PdfRenderHistory> PDF_RENDER_HISTORY = new ConcurrentHashMap<>();

    /** One-shot guard for startup capability summary logging. */
    private static final AtomicBoolean LOGGED_CAPABILITY_SUMMARY = new AtomicBoolean(false);

    /** One-shot guards for missing ImageIO readers discovered on demand. */
    private static final Set<String> LOGGED_MISSING_IMAGEIO_READERS = ConcurrentHashMap.newKeySet();

    /** Byte-scan ceiling for cheap PDF JPX preflight detection. */
    private static final long PDF_JPX_SCAN_LIMIT_BYTES =
            longProp("fileexplorer.thumb.pdf.jpxScanLimitBytes", 32L * 1024L * 1024L, 1024L, 256L * 1024L * 1024L);

    /** Byte-scan ceiling for cheap PDF JBIG2 preflight detection. */
    private static final long PDF_JBIG2_SCAN_LIMIT_BYTES =
            longProp("fileexplorer.thumb.pdf.jbig2ScanLimitBytes", 32L * 1024L * 1024L, 1024L, 256L * 1024L * 1024L);

    /** Maximum PDF size to load fully into memory for isolated thumbnail rendering. */
    private static final long PDF_IN_MEMORY_MAX_BYTES =
            longProp("fileexplorer.thumb.pdf.inMemoryMaxBytes", 64L * 1024L * 1024L, 1024L * 1024L, 1024L * 1024L * 1024L);

    /** Cooldown after a PDF thumbnail timeout so repeated requests do not hammer the renderer. */
    private static final long PDF_TIMEOUT_COOLDOWN_MS =
            longProp("fileexplorer.thumb.pdf.timeoutCooldownMs", 30_000L, 1_000L, 3_600_000L);

    /** Soft large-document threshold where PDF thumbnail planning becomes more conservative. */
    private static final long PDF_LARGE_DOC_SOFT_BYTES =
            longProp("fileexplorer.thumb.pdf.largeDocSoftBytes", 16L * 1024L * 1024L, 1024L * 1024L, PDF_IN_MEMORY_MAX_BYTES);

    /** Hard fallback threshold where PDF thumbnails downgrade directly to the file-type icon. */
    private static final long PDF_LARGE_DOC_HARD_FALLBACK_BYTES =
            longProp("fileexplorer.thumb.pdf.largeDocHardFallbackBytes", 48L * 1024L * 1024L, 1024L * 1024L, PDF_IN_MEMORY_MAX_BYTES);

    /** Page-count threshold where PDF thumbnail planning switches to large-document heuristics. */
    private static final int PDF_LARGE_DOC_PAGE_COUNT_THRESHOLD =
            intProp("fileexplorer.thumb.pdf.largeDocPageCountThreshold", 120, 1, 100_000);

    /** Minimum adaptive budget applied to PDF thumbnail attempts. */
    private static final long PDF_ADAPTIVE_TIMEOUT_MIN_MS =
            longProp("fileexplorer.thumb.pdf.adaptiveTimeoutMinMs", DOCUMENT_TIMEOUT_MS, 250L, 120_000L);

    /** Maximum adaptive budget applied to PDF thumbnail attempts. */
    private static final long PDF_ADAPTIVE_TIMEOUT_MAX_MS =
            longProp("fileexplorer.thumb.pdf.adaptiveTimeoutMaxMs", Math.max(DOCUMENT_TIMEOUT_MS, 9_000L), 250L, 120_000L);

    /** Consecutive timeout streak at which large PDFs enter direct-recovery fallback mode. */
    private static final int PDF_RECOVERY_TIMEOUT_STREAK_THRESHOLD =
            intProp("fileexplorer.thumb.pdf.recoveryTimeoutStreakThreshold", 2, 1, 16);

    /** Small grace window so worker-side PDF budget checks can return cleanly before outer timeout handling fires. */
    private static final long PDF_TIMEOUT_JOIN_GRACE_MS =
            longProp("fileexplorer.thumb.pdf.timeoutJoinGraceMs", 125L, 0L, 5_000L);

    /** Enable low-first/high-later PDF thumbnail planning for visible work. */
    private static final boolean ENABLE_PDF_PROGRESSIVE_UPGRADE =
            boolProp("fileexplorer.thumb.pdf.progressiveUpgrade.enabled", true);

    /** Delay before attempting an idle/settled visible-page promotion pass. */
    private static final long PDF_PROGRESSIVE_PROMOTION_DELAY_MS =
            longProp("fileexplorer.thumb.pdf.progressivePromotionDelayMs", 180L, 25L, 10_000L);

    /** Bound the number of visible PDFs that can be queued for high-tier promotion at once. */
    private static final int PDF_PROGRESSIVE_VISIBLE_TRACK_LIMIT =
            intProp("fileexplorer.thumb.pdf.progressiveVisibleTrackLimit", 24, 1, 512);

    /** Cap concurrently running high-tier promotions so they never starve low-tier visible renders. */
    private static final int PDF_MAX_ACTIVE_HIGH_TIER_PROMOTIONS =
            intProp("fileexplorer.thumb.pdf.maxActiveHighTierPromotions", 1, 1, 8);

    /** Enable low-first/high-later planning for Office-family document thumbnails. */
    private static final boolean ENABLE_OFFICE_PROGRESSIVE_UPGRADE =
            boolProp("fileexplorer.thumb.doc.progressiveUpgrade.enabled", true);

    /** Maximum preview edge used for first-pass Office-family thumbnails before visible promotion. */
    private static final int OFFICE_PROGRESSIVE_LOW_TIER_MAX_SIZE_PX =
            intProp("fileexplorer.thumb.doc.progressiveLowTierMaxSizePx", 128, 32, 512);

    /** Delay before a visible Office-family thumbnail is promoted to the requested resolution. */
    private static final long OFFICE_PROGRESSIVE_PROMOTION_DELAY_MS =
            longProp("fileexplorer.thumb.doc.progressivePromotionDelayMs", 220L, 25L, 10_000L);

    /** Cap concurrent high-tier Office-family promotions so they never starve visible low-tier renders. */
    private static final int OFFICE_MAX_ACTIVE_HIGH_TIER_PROMOTIONS =
            intProp("fileexplorer.thumb.doc.maxActiveHighTierPromotions", 1, 1, 8);

    /** Cooldown after a document-backend failure so repeated paints do not hammer Office renderers. */
    private static final long DOCUMENT_FAILURE_COOLDOWN_MS =
            longProp("fileexplorer.thumb.doc.failureCooldownMs", 20_000L, 1_000L, 3_600_000L);

    /** Per-document high-tier fairness guard. */
    private static final int PDF_MAX_ACTIVE_HIGH_TIER_PER_DOCUMENT =
            intProp("fileexplorer.thumb.pdf.maxActiveHighTierPerDocument", 1, 1, 2);

    /** Per-document total PDF render fairness guard. */
    private static final int PDF_MAX_ACTIVE_RENDERS_PER_DOCUMENT =
            intProp("fileexplorer.thumb.pdf.maxActiveRendersPerDocument", 1, 1, 4);

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

    /** Maximum age for stale temporary disk-cache files left behind by interrupted writes. */
    private static final long DISK_CACHE_TMP_MAX_AGE_HOURS =
            longProp("fileexplorer.thumb.diskCache.tmpMaxAgeHours", 24L, 1L, 24L * 365L);

    /** Refresh disk-cache payload mtimes on read so age-based pruning favors recently used entries. */
    private static final boolean TOUCH_DISK_CACHE_ON_READ =
            boolProp("fileexplorer.thumb.diskCache.touchOnRead", true);

    /** Minimum spacing between touch-on-read mtime refreshes for the same cache file. */
    private static final long DISK_CACHE_TOUCH_MIN_INTERVAL_MINUTES =
            longProp("fileexplorer.thumb.diskCache.touchMinIntervalMinutes", 240L, 1L, 24L * 30L);

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
    private static final String DISK_CACHE_COMPAT_VERSION = "phase4p9ck";

    private static final AsyncThumbnailService INSTANCE = new AsyncThumbnailService();

    private static final Set<String> THUMBNAILS4J_EXTENSIONS = Set.of(
            "doc", "docx", "pptx", "xls", "xlsx"
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

    private enum PdfRenderTier {
        LOW,
        HIGH
    }

    private enum DocumentBackend {
        NONE,
        PDFBOX,
        THUMBNAILS4J
    }

    private enum DocumentRenderTier {
        LOW,
        HIGH
    }

    private enum RenderQuality {
        STANDARD,
        DOC_LOW,
        DOC_HIGH,
        PDF_LOW,
        PDF_HIGH
    }

    private record ThumbKey(String path, int sizePx, long lastModifiedMs, long fileSizeBytes) {}

    private record PdfRenderKey(String path,
                                int sizePx,
                                long lastModifiedMs,
                                long fileSizeBytes,
                                int pageIndex,
                                PdfRenderTier tier,
                                long viewportScope,
                                long generation) {
    }

    private record OfficeRenderKey(String ext,
                                   String path,
                                   int sizePx,
                                   long lastModifiedMs,
                                   long fileSizeBytes,
                                   long viewportScope,
                                   long generation) {
    }

    private record PdfViewportState(int firstVisiblePage,
                                    int lastVisiblePage,
                                    int anchorPage,
                                    long generation,
                                    long lastUpdateNanos) {
        boolean matches(long viewportGeneration) {
            return generation == viewportGeneration;
        }
    }

    private record PdfCooldownState(long lastModifiedMs, long fileSizeBytes, long untilMs) {
        boolean matches(long otherLastModifiedMs, long otherFileSizeBytes) {
            return lastModifiedMs == otherLastModifiedMs && fileSizeBytes == otherFileSizeBytes;
        }
    }

    private record DocumentCooldownState(String ext, long lastModifiedMs, long fileSizeBytes, long untilMs) {
        boolean matches(String otherExt, long otherLastModifiedMs, long otherFileSizeBytes) {
            return Objects.equals(ext, otherExt)
                    && lastModifiedMs == otherLastModifiedMs
                    && fileSizeBytes == otherFileSizeBytes;
        }
    }

    private record PdfRenderHistory(long lastModifiedMs,
                                    long fileSizeBytes,
                                    double averageRenderMs,
                                    int successfulSamples,
                                    int consecutiveTimeouts) {
        boolean matches(long otherLastModifiedMs, long otherFileSizeBytes) {
            return lastModifiedMs == otherLastModifiedMs && fileSizeBytes == otherFileSizeBytes;
        }

        PdfRenderHistory afterSuccess(long renderMs) {
            double boundedRenderMs = Math.max(1.0d, renderMs);
            double nextAverage = successfulSamples <= 0
                    ? boundedRenderMs
                    : ((averageRenderMs * 0.65d) + (boundedRenderMs * 0.35d));
            return new PdfRenderHistory(lastModifiedMs, fileSizeBytes, nextAverage, Math.min(512, successfulSamples + 1), 0);
        }

        PdfRenderHistory afterTimeout(long timeoutBudgetMs) {
            double boundedBudgetMs = Math.max(1.0d, timeoutBudgetMs);
            double seedAverage = averageRenderMs <= 0.0d ? boundedBudgetMs : Math.max(averageRenderMs, boundedBudgetMs);
            return new PdfRenderHistory(lastModifiedMs, fileSizeBytes, seedAverage, successfulSamples, Math.min(32, consecutiveTimeouts + 1));
        }
    }

    private record PdfRenderPlan(int effectiveSizePx,
                                 float scale,
                                 int pageCount,
                                 boolean largeDocument,
                                 boolean budgetReduced,
                                 PdfRenderTier tier) {
    }

    private static final int[] SIZE_BUCKETS_PX = {16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512};

    private record LoadResult(Image image, ThumbnailProvider provider, RenderQuality quality) {
        boolean hasImage() {
            return image != null;
        }

        boolean isLowQualityPreview() {
            return quality == RenderQuality.PDF_LOW || quality == RenderQuality.DOC_LOW;
        }

        boolean isLowQualityPdf() {
            return quality == RenderQuality.PDF_LOW;
        }

        boolean isLowQualityOfficeDocument() {
            return quality == RenderQuality.DOC_LOW;
        }

        boolean isPromotablePdf() {
            return quality == RenderQuality.PDF_LOW || quality == RenderQuality.PDF_HIGH;
        }
    }

    private static final class CachedThumb {
        final long lastModifiedMs;
        final long fileSizeBytes;
        final Image image;
        final long approxBytes;
        final RenderQuality quality;

        CachedThumb(long lastModifiedMs, long fileSizeBytes, Image image, long approxBytes, RenderQuality quality) {
            this.lastModifiedMs = lastModifiedMs;
            this.fileSizeBytes = fileSizeBytes;
            this.image = image;
            this.approxBytes = approxBytes;
            this.quality = quality == null ? RenderQuality.STANDARD : quality;
        }

        boolean isLowQualityPdf() {
            return quality == RenderQuality.PDF_LOW;
        }

        boolean isLowQualityPreview() {
            return quality == RenderQuality.PDF_LOW || quality == RenderQuality.DOC_LOW;
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
    private final ConcurrentHashMap<ThumbKey, RequestPriority> pendingPriorities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ThumbKey, Long> pendingViewportScopes = new ConcurrentHashMap<>();
    private final Set<ThumbKey> running = ConcurrentHashMap.newKeySet();

    private final ThumbCache cache = new ThumbCache(CACHE_BYTES);
    private final Path diskCacheDir = resolveDiskCacheDir();
    private final AtomicBoolean diskPruneQueued = new AtomicBoolean(false);
    private final AtomicBoolean startupDiskPruneScheduled = new AtomicBoolean(false);
    private final AtomicInteger diskWritesSincePrune = new AtomicInteger(0);
    private final ConcurrentHashMap<String, PdfViewportState> pdfViewportStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PdfRenderKey> pdfPromotionRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pdfPromotionPending = new ConcurrentHashMap<>();
    private final Set<String> pdfPromotionRunning = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pdfActiveHighTierPromotions = new AtomicInteger(0);
    private final ConcurrentHashMap<String, OfficeRenderKey> officePromotionRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> officePromotionPending = new ConcurrentHashMap<>();
    private final Set<String> officePromotionRunning = ConcurrentHashMap.newKeySet();
    private final AtomicInteger officeActiveHighTierPromotions = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> pdfActiveDocumentRenders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> pdfActiveDocumentHighTierRenders = new ConcurrentHashMap<>();

    // Priority executor for decode tasks (interaction wins).
    private final ThreadPoolExecutor decodeExecutor;
    private final ExecutorService officeDocumentExecutor;
    private final ExecutorService pdfDocumentExecutor;

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
    private final AtomicLong viewportMovingUntilNanos = new AtomicLong(0L);
    private final AtomicLong viewportScopeGeneration = new AtomicLong(1L);

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
    private final LongAdder diskCacheTempPruned = new LongAdder();
    private final LongAdder diskCacheEmptyDirsPruned = new LongAdder();
    private final LongAdder diskCacheTouchWrite = new LongAdder();
    private final LongAdder diskCacheTouchSkip = new LongAdder();
    private final LongAdder diskCacheTouchFail = new LongAdder();
    private final LongAdder diskCacheManifestWrites = new LongAdder();
    private final LongAdder diskCacheManifestWriteFail = new LongAdder();
    private final LongAdder diskCacheManifestMismatchDetected = new LongAdder();
    private final LongAdder diskCacheManifestMismatchClears = new LongAdder();
    private final AtomicInteger inFlightCount = new AtomicInteger(0);

    private final AtomicLong seq = new AtomicLong(0L);
    private final LongAdder viewportMotionEvents = new LongAdder();
    private final LongAdder viewportIdleTransitions = new LongAdder();
    private final LongAdder viewportScopeAdvances = new LongAdder();
    private final LongAdder movingBackgroundDeferrals = new LongAdder();
    private final LongAdder queueTrimDrops = new LongAdder();
    private final LongAdder staleGenerationDrops = new LongAdder();
    private final LongAdder staleSubscriberDrops = new LongAdder();
    private final LongAdder staleViewportScopeDrops = new LongAdder();
    private final LongAdder staleCompletionDiscards = new LongAdder();
    private final LongAdder staleFileVersionDrops = new LongAdder();
    private final LongAdder pdfCooldownInvalidations = new LongAdder();
    private final LongAdder pdfHistoryResets = new LongAdder();
    private final LongAdder pdfAdaptiveBudgetPlans = new LongAdder();
    private final LongAdder pdfAdaptiveBudgetDownshifts = new LongAdder();
    private final LongAdder pdfLargeDocFallbacks = new LongAdder();
    private final LongAdder pdfLowTierRendered = new LongAdder();
    private final LongAdder pdfHighTierRendered = new LongAdder();
    private final LongAdder officeLowTierRendered = new LongAdder();
    private final LongAdder officeHighTierRendered = new LongAdder();
    private final LongAdder documentFailureCooldownSkips = new LongAdder();
    private final LongAdder pdfPromotionQueuedCount = new LongAdder();
    private final LongAdder pdfPromotionCompleted = new LongAdder();
    private final LongAdder pdfPromotionSkipped = new LongAdder();
    private final LongAdder officePromotionQueuedCount = new LongAdder();
    private final LongAdder officePromotionCompleted = new LongAdder();
    private final LongAdder officePromotionSkipped = new LongAdder();
    private final LongAdder viewportPrunedPending = new LongAdder();
    private final LongAdder viewportPrunedQueued = new LongAdder();
    private final AtomicBoolean imageSupportInitialized = new AtomicBoolean(false);
    private final AtomicBoolean imageSupportInitScheduled = new AtomicBoolean(false);

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

        this.officeDocumentExecutor = java.util.concurrent.Executors.newFixedThreadPool(
                MAX_OFFICE_DOCUMENT_CONCURRENT,
                daemonThreadFactory("thumb-doc-office")
        );

        this.pdfDocumentExecutor = java.util.concurrent.Executors.newFixedThreadPool(
                MAX_PDF_DOCUMENT_CONCURRENT,
                daemonThreadFactory("thumb-doc-pdf")
        );

        this.scheduler = new ScheduledThreadPoolExecutor(1, daemonThreadFactory("thumb-debounce"));

        // HOTFIX184: keep construction cheap. Plugin discovery / capability probing is deferred
        // until after the thumbnail gate opens or until a non-native ImageIO format actually needs it.
        quietPdfThumbnailNoiseLoggers();

        startThumbLoggerIfEnabled();
    }

    private void startThumbLoggerIfEnabled() {
        if (!DEBUG_THUMBS) return;

        ScheduledExecutorService ses = new ScheduledThreadPoolExecutor(1, daemonThreadFactory("thumb-stats"));
        ses.scheduleAtFixedRate(() -> {
            double avgMs = averageDecodeMs();
            LOG.info(() -> String.format(
                    "[Thumbs] req=%d hit=%d miss=%d coalesced=%d bucketReuse=%d throttled=%d viewportCancels=%d viewportMotion=%d viewportIdle=%d movingBgDeferrals=%d queueTrimDrops=%d staleGenerationDrops=%d staleSubscriberDrops=%d staleFileVersionDrops=%d pdfCooldownInvalidations=%d pdfHistoryResets=%d pdfBudgetPlans=%d pdfBudgetDownshifts=%d pdfLargeDocFallbacks=%d pdfLowTierRendered=%d pdfHighTierRendered=%d pdfPromotionQueued=%d pdfPromotionCompleted=%d pdfPromotionSkipped=%d queued=%d inFlight=%d pending=%d rendered=%d failed=%d fallback=%d cancelled=%d docTimeouts=%d provider{fx=%d,doc=%d,imageio=%d} disk{hit=%d,miss=%d,write=%d,writeFail=%d,pruned=%d,pruneRuns=%d,startupPruneRuns=%d,corruptDeletes=%d,startupClearRuns=%d,startupClearDeletes=%d,tempPruned=%d,emptyDirsPruned=%d,touchWrite=%d,touchSkip=%d,touchFail=%d,manifestWrites=%d,manifestWriteFail=%d,manifestMismatchDetected=%d,manifestMismatchClears=%d,enabled=%s,docsOnly=%s,clearOnStartup=%s,clearOnManifestMismatch=%s,touchOnRead=%s} avgDecodeMs=%.2f cache={%s} gates={pdf=%s,word=%s,excel=%s,pptx=%s} docLanes{officeActive=%d,officeQueue=%d,pdfActive=%d,pdfQueue=%d}",
                    requested.sum(),
                    hit.sum(),
                    miss.sum(),
                    coalesced.sum(),
                    bucketReuse.sum(),
                    throttleDeferrals.sum(),
                    viewportCancels.sum(),
                    viewportMotionEvents.sum(),
                    viewportIdleTransitions.sum(),
                    movingBackgroundDeferrals.sum(),
                    queueTrimDrops.sum(),
                    staleGenerationDrops.sum(),
                    staleSubscriberDrops.sum(),
                    staleFileVersionDrops.sum(),
                    pdfCooldownInvalidations.sum(),
                    pdfHistoryResets.sum(),
                    pdfAdaptiveBudgetPlans.sum(),
                    pdfAdaptiveBudgetDownshifts.sum(),
                    pdfLargeDocFallbacks.sum(),
                    pdfLowTierRendered.sum(),
                    pdfHighTierRendered.sum(),
                    pdfPromotionQueuedCount.sum(),
                    pdfPromotionCompleted.sum(),
                    pdfPromotionSkipped.sum(),
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
                    diskCacheTempPruned.sum(),
                    diskCacheEmptyDirsPruned.sum(),
                    diskCacheTouchWrite.sum(),
                    diskCacheTouchSkip.sum(),
                    diskCacheTouchFail.sum(),
                    diskCacheManifestWrites.sum(),
                    diskCacheManifestWriteFail.sum(),
                    diskCacheManifestMismatchDetected.sum(),
                    diskCacheManifestMismatchClears.sum(),
                    onOff(ENABLE_DISK_CACHE),
                    onOff(DISK_CACHE_DOCUMENTS_ONLY),
                    onOff(CLEAR_DISK_CACHE_ON_STARTUP),
                    onOff(CLEAR_ON_MANIFEST_MISMATCH),
                    onOff(TOUCH_DISK_CACHE_ON_READ),
                    avgMs,
                    cache.debugString(),
                    onOff(ENABLE_PDF),
                    onOff(ENABLE_WORD),
                    onOff(ENABLE_EXCEL),
                    onOff(ENABLE_PPTX),
                    executorActiveCount(officeDocumentExecutor),
                    executorQueueSize(officeDocumentExecutor),
                    executorActiveCount(pdfDocumentExecutor),
                    executorQueueSize(pdfDocumentExecutor)
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
                + " viewportScope=" + viewportScopeGeneration.get()
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
                + " viewportMotion=" + viewportMotionEvents.sum()
                + " viewportIdle=" + viewportIdleTransitions.sum()
                + " viewportScopeAdvances=" + viewportScopeAdvances.sum()
                + " movingBgDeferrals=" + movingBackgroundDeferrals.sum()
                + " queueTrimDrops=" + queueTrimDrops.sum()
                + " staleGenerationDrops=" + staleGenerationDrops.sum()
                + " staleSubscriberDrops=" + staleSubscriberDrops.sum()
                + " staleViewportDrops=" + staleViewportScopeDrops.sum()
                + " staleCompletionDiscards=" + staleCompletionDiscards.sum()
                + " staleFileVersionDrops=" + staleFileVersionDrops.sum()
                + " pdfCooldownInvalidations=" + pdfCooldownInvalidations.sum()
                + " pdfHistoryResets=" + pdfHistoryResets.sum()
                + " pdfBudgetPlans=" + pdfAdaptiveBudgetPlans.sum()
                + " pdfBudgetDownshifts=" + pdfAdaptiveBudgetDownshifts.sum()
                + " pdfLargeDocFallbacks=" + pdfLargeDocFallbacks.sum()
                + " pdfLowTierRendered=" + pdfLowTierRendered.sum()
                + " pdfHighTierRendered=" + pdfHighTierRendered.sum()
                + " pdfPromotionQueued=" + pdfPromotionQueuedCount.sum()
                + " pdfPromotionCompleted=" + pdfPromotionCompleted.sum()
                + " pdfPromotionSkipped=" + pdfPromotionSkipped.sum()
                + " viewportPrunedPending=" + viewportPrunedPending.sum()
                + " viewportPrunedQueued=" + viewportPrunedQueued.sum()
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
                + ",tempPruned=" + diskCacheTempPruned.sum()
                + ",emptyDirsPruned=" + diskCacheEmptyDirsPruned.sum()
                + ",touchWrite=" + diskCacheTouchWrite.sum()
                + ",touchSkip=" + diskCacheTouchSkip.sum()
                + ",touchFail=" + diskCacheTouchFail.sum()
                + ",manifestWrites=" + diskCacheManifestWrites.sum()
                + ",manifestWriteFail=" + diskCacheManifestWriteFail.sum()
                + ",manifestMismatchDetected=" + diskCacheManifestMismatchDetected.sum()
                + ",manifestMismatchClears=" + diskCacheManifestMismatchClears.sum()
                + ",enabled=" + onOff(ENABLE_DISK_CACHE)
                + ",docsOnly=" + onOff(DISK_CACHE_DOCUMENTS_ONLY)
                + ",clearOnStartup=" + onOff(CLEAR_DISK_CACHE_ON_STARTUP)
                + ",clearOnManifestMismatch=" + onOff(CLEAR_ON_MANIFEST_MISMATCH)
                + ",touchOnRead=" + onOff(TOUCH_DISK_CACHE_ON_READ) + "}"
                + " gates{pdf=" + onOff(ENABLE_PDF)
                + ",word=" + onOff(ENABLE_WORD)
                + ",excel=" + onOff(ENABLE_EXCEL)
                + ",pptx=" + onOff(ENABLE_PPTX) + "}"
                + " docLanes{officeActive=" + executorActiveCount(officeDocumentExecutor)
                + ",officeQueue=" + executorQueueSize(officeDocumentExecutor)
                + ",pdfActive=" + executorActiveCount(pdfDocumentExecutor)
                + ",pdfQueue=" + executorQueueSize(pdfDocumentExecutor)
                + ",pdfPromotionActive=" + pdfActiveHighTierPromotions.get()
                + ",pdfPromotionPending=" + pdfPromotionPending.size() + "}"
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
        scheduleCurrentViewportPdfPromotions();
    }

    public boolean isViewportMoving() {
        long until = viewportMovingUntilNanos.get();
        return until != 0L && System.nanoTime() < until;
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
            scheduleImageSupportInitialization();
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
        viewportScopeGeneration.incrementAndGet();

        try {
            pending.forEach((k, f) -> {
                try {
                    f.cancel(false);
                } catch (Exception ignored) {
                }
            });
        } finally {
            pending.clear();
            pendingPriorities.clear();
            pendingViewportScopes.clear();
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
                    queueTrimDrops.increment();
                    onDecodeTaskDropped(pr);
                    over--;
                }
            }
            it = q.iterator();
            while (it.hasNext() && over > 0) {
                Runnable r = it.next();
                if (r instanceof PrioritizedRunnable pr && pr.priority == RequestPriority.VISIBLE.p) {
                    it.remove();
                    queueTrimDrops.increment();
                    onDecodeTaskDropped(pr);
                    over--;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private long computeScheduleDelayMs(RequestPriority pr) {
        if (pr == RequestPriority.USER_ACTION || pr == RequestPriority.VISIBLE) {
            return DEBOUNCE_MS;
        }
        return DEBOUNCE_MS + (isViewportMoving() ? MOVING_BACKGROUND_EXTRA_DELAY_MS : 0L);
    }

    private int queuedCountAtOrAbove(int minPriority) {
        int count = 0;
        for (Runnable runnable : decodeExecutor.getQueue()) {
            if (runnable instanceof PrioritizedRunnable pr && pr.priority >= minPriority) {
                count++;
            }
        }
        return count;
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
        final long lastMod = safeLastModifiedMs(abs);
        final long fileSizeBytes = safeFileSizeBytes(abs);
        final ThumbKey key = new ThumbKey(absKey, sizeBucketPx, lastMod, fileSizeBytes);

        CachedThumb cached = cache.get(key);
        if (cached != null && cached.image != null) {
            if (cached.lastModifiedMs == lastMod && cached.fileSizeBytes == fileSizeBytes) {
                hit.increment();
                if (sizeBucketPx != sizePx) {
                    bucketReuse.increment();
                }
                maybeTrackAndPromotePdfVisibleRequest(abs, ext, key, pr, cached.quality, lastMod, fileSizeBytes, viewportScopeFor(pr));
                maybeTrackAndPromoteOfficeVisibleRequest(abs, ext, key, pr, cached.quality, lastMod, fileSizeBytes, viewportScopeFor(pr));
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
            maybeTrackAndPromotePdfVisibleRequest(abs, ext, key, pr, larger.quality, lastMod, fileSizeBytes, viewportScopeFor(pr));
            maybeTrackAndPromoteOfficeVisibleRequest(abs, ext, key, pr, larger.quality, lastMod, fileSizeBytes, viewportScopeFor(pr));
            return CompletableFuture.completedFuture(larger.image);
        }

        Image diskCached = readDiskCachedThumbnail(abs, ext, sizeBucketPx, lastMod, fileSizeBytes, providerFor(ext));
        if (diskCached != null) {
            hit.increment();
            RenderQuality diskQuality = defaultDiskCacheRenderQuality(ext);
            cache.put(key, new CachedThumb(lastMod, fileSizeBytes, diskCached, approxBytes(diskCached), diskQuality));
            maybeTrackAndPromotePdfVisibleRequest(abs, ext, key, pr, diskQuality, lastMod, fileSizeBytes, viewportScopeFor(pr));
            maybeTrackAndPromoteOfficeVisibleRequest(abs, ext, key, pr, diskQuality, lastMod, fileSizeBytes, viewportScopeFor(pr));
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

        final long viewportScope = viewportScopeFor(pr);
        final PdfRenderTier pdfTier = requestedPdfTierFor(ext, pr);
        final DocumentRenderTier documentTier = requestedDocumentTierFor(ext, pr, sizeBucketPx);
        maybeTrackAndPromotePdfVisibleRequest(abs, ext, key, pr, null, lastMod, fileSizeBytes, viewportScope);
        maybeTrackAndPromoteOfficeVisibleRequest(abs, ext, key, pr, null, lastMod, fileSizeBytes, viewportScope);

        // Another request already has this key queued or actively decoding.
        if (!created[0]) {
            if (running.contains(key)) {
                return detached;
            }
            if (pending.containsKey(key)) {
                if (shouldReschedulePendingWork(key, pr, viewportScope)) {
                    scheduleQueuedDecode(key, abs, ext, sizeBucketPx, pr, lastMod, fileSizeBytes, generation.get(), viewportScope, pdfTier, documentTier);
                }
                return detached;
            }
        }

        scheduleQueuedDecode(key, abs, ext, sizeBucketPx, pr, lastMod, fileSizeBytes, generation.get(), viewportScope, pdfTier, documentTier);
        return detached;
    }

    private PdfRenderTier requestedPdfTierFor(String ext, RequestPriority pr) {
        if (!isPdfExtension(ext)) {
            return null;
        }
        if (pr == RequestPriority.USER_ACTION) {
            return PdfRenderTier.HIGH;
        }
        return PdfRenderTier.LOW;
    }

    private DocumentRenderTier requestedDocumentTierFor(String ext, RequestPriority pr, int requestedSizePx) {
        if (!isOfficeDocumentExtension(ext)) {
            return DocumentRenderTier.HIGH;
        }
        if (!ENABLE_OFFICE_PROGRESSIVE_UPGRADE || pr == RequestPriority.USER_ACTION) {
            return DocumentRenderTier.HIGH;
        }
        int safeRequestedSizePx = Math.max(12, requestedSizePx);
        return safeRequestedSizePx > OFFICE_PROGRESSIVE_LOW_TIER_MAX_SIZE_PX
                ? DocumentRenderTier.LOW
                : DocumentRenderTier.HIGH;
    }

    private void maybeTrackAndPromotePdfVisibleRequest(Path abs,
                                                       String ext,
                                                       ThumbKey key,
                                                       RequestPriority pr,
                                                       RenderQuality quality,
                                                       long lastMod,
                                                       long fileSizeBytes,
                                                       long viewportScope) {
        if (!ENABLE_PDF_PROGRESSIVE_UPGRADE || abs == null || !isPdfExtension(ext) || pr != RequestPriority.VISIBLE) {
            return;
        }
        String target = safePath(abs);
        pdfViewportStates.put(target, new PdfViewportState(0, 0, 0, viewportScope, System.nanoTime()));
        trimPdfViewportTrackingIfNeeded();
        if (quality == RenderQuality.PDF_LOW) {
            schedulePdfPromotion(target, abs, key.sizePx(), lastMod, fileSizeBytes, viewportScope, generation.get(), true);
        }
    }

    private void maybeSchedulePdfPromotionAfterLowTier(Path abs,
                                                       String ext,
                                                       ThumbKey key,
                                                       RequestPriority pr,
                                                       long lastMod,
                                                       long fileSizeBytes,
                                                       long viewportScope) {
        if (!ENABLE_PDF_PROGRESSIVE_UPGRADE || abs == null || !isPdfExtension(ext) || pr != RequestPriority.VISIBLE) {
            return;
        }
        schedulePdfPromotion(safePath(abs), abs, key.sizePx(), lastMod, fileSizeBytes, viewportScope, generation.get(), true);
    }

    private void maybeTrackAndPromoteOfficeVisibleRequest(Path abs,
                                                          String ext,
                                                          ThumbKey key,
                                                          RequestPriority pr,
                                                          RenderQuality quality,
                                                          long lastMod,
                                                          long fileSizeBytes,
                                                          long viewportScope) {
        if (!ENABLE_OFFICE_PROGRESSIVE_UPGRADE || abs == null || !isOfficeDocumentExtension(ext) || pr != RequestPriority.VISIBLE) {
            return;
        }
        if (quality == RenderQuality.DOC_LOW) {
            scheduleOfficePromotion(abs, ext, key.sizePx(), lastMod, fileSizeBytes, viewportScope, generation.get(), true);
        }
    }

    private void maybeScheduleOfficePromotionAfterLowTier(Path abs,
                                                          String ext,
                                                          ThumbKey key,
                                                          RequestPriority pr,
                                                          long lastMod,
                                                          long fileSizeBytes,
                                                          long viewportScope) {
        if (!ENABLE_OFFICE_PROGRESSIVE_UPGRADE || abs == null || !isOfficeDocumentExtension(ext) || pr != RequestPriority.VISIBLE) {
            return;
        }
        scheduleOfficePromotion(abs, ext, key.sizePx(), lastMod, fileSizeBytes, viewportScope, generation.get(), true);
    }

    private void scheduleCurrentViewportPdfPromotions() {
        if (!ENABLE_PDF_PROGRESSIVE_UPGRADE || isViewportMoving()) {
            return;
        }
        long currentScope = viewportScopeGeneration.get();
        pdfViewportStates.forEach((target, state) -> {
            if (state == null || !state.matches(currentScope)) {
                return;
            }
            PdfRenderKey request = pdfPromotionRequests.get(target);
            if (request == null || request.viewportScope() != currentScope) {
                return;
            }
            schedulePdfPromotion(target,
                    Path.of(target),
                    request.sizePx(),
                    request.lastModifiedMs(),
                    request.fileSizeBytes(),
                    request.viewportScope(),
                    request.generation(),
                    false);
        });
    }

    private void schedulePdfPromotion(String target,
                                      Path abs,
                                      int sizePx,
                                      long lastMod,
                                      long fileSizeBytes,
                                      long viewportScope,
                                      long generationAtRequest,
                                      boolean fromLowTierRequest) {
        if (!ENABLE_PDF_PROGRESSIVE_UPGRADE || target == null || abs == null) {
            return;
        }
        PdfRenderKey renderKey = new PdfRenderKey(target, Math.max(12, Math.min(512, sizePx)), lastMod, fileSizeBytes, 0, PdfRenderTier.HIGH, viewportScope, generationAtRequest);
        pdfPromotionRequests.put(target, renderKey);
        trimPdfViewportTrackingIfNeeded();
        ScheduledFuture<?> prior = pdfPromotionPending.remove(target);
        if (prior != null) {
            prior.cancel(false);
        }
        long delayMs = Math.max(25L, PDF_PROGRESSIVE_PROMOTION_DELAY_MS + ((fromLowTierRequest || isViewportMoving()) ? VIEWPORT_SETTLE_MS : 0L));
        ScheduledFuture<?> future = scheduler.schedule(() -> runPdfPromotion(target, abs, renderKey), delayMs, TimeUnit.MILLISECONDS);
        pdfPromotionPending.put(target, future);
        pdfPromotionQueuedCount.increment();
    }

    private void runPdfPromotion(String target, Path abs, PdfRenderKey renderKey) {
        ScheduledFuture<?> currentFuture = pdfPromotionPending.remove(target);
        if (currentFuture != null && currentFuture.isCancelled()) {
            pdfPromotionSkipped.increment();
            return;
        }
        if (!ENABLE_PDF_PROGRESSIVE_UPGRADE || renderKey == null || abs == null) {
            pdfPromotionSkipped.increment();
            return;
        }
        if (generation.get() != renderKey.generation()) {
            pdfPromotionSkipped.increment();
            return;
        }
        if (isViewportMoving()) {
            schedulePdfPromotion(target, abs, renderKey.sizePx(), renderKey.lastModifiedMs(), renderKey.fileSizeBytes(), renderKey.viewportScope(), renderKey.generation(), false);
            return;
        }
        PdfViewportState state = pdfViewportStates.get(target);
        if (state == null || !state.matches(renderKey.viewportScope())) {
            pdfPromotionSkipped.increment();
            return;
        }
        if (!isFileVersionCurrent(abs, renderKey.lastModifiedMs(), renderKey.fileSizeBytes())) {
            pdfPromotionSkipped.increment();
            return;
        }
        ThumbKey key = new ThumbKey(target, renderKey.sizePx(), renderKey.lastModifiedMs(), renderKey.fileSizeBytes());
        CachedThumb cached = cache.get(key);
        if (cached != null && cached.image != null && !cached.isLowQualityPdf()) {
            pdfPromotionSkipped.increment();
            return;
        }
        if (!pdfPromotionRunning.add(target)) {
            pdfPromotionSkipped.increment();
            return;
        }
        if (pdfActiveHighTierPromotions.incrementAndGet() > PDF_MAX_ACTIVE_HIGH_TIER_PROMOTIONS) {
            pdfActiveHighTierPromotions.decrementAndGet();
            pdfPromotionRunning.remove(target);
            schedulePdfPromotion(target, abs, renderKey.sizePx(), renderKey.lastModifiedMs(), renderKey.fileSizeBytes(), renderKey.viewportScope(), renderKey.generation(), false);
            return;
        }
        decodeExecutor.execute(new PrioritizedRunnable(RequestPriority.BACKGROUND, seq.incrementAndGet(), renderKey.viewportScope(), key, new CompletableFuture<>(), () -> {
            try {
                LoadResult result = loadThumbnail(abs, "pdf", renderKey.sizePx(), PdfRenderTier.HIGH, DocumentRenderTier.HIGH);
                if (result == null || !result.hasImage()) {
                    pdfPromotionSkipped.increment();
                    return;
                }
                if (generation.get() != renderKey.generation() || !isFileVersionCurrent(abs, renderKey.lastModifiedMs(), renderKey.fileSizeBytes())) {
                    pdfPromotionSkipped.increment();
                    return;
                }
                PdfViewportState currentState = pdfViewportStates.get(target);
                if (currentState == null || !currentState.matches(renderKey.viewportScope())) {
                    pdfPromotionSkipped.increment();
                    return;
                }
                cache.put(key, new CachedThumb(renderKey.lastModifiedMs(), renderKey.fileSizeBytes(), result.image(), approxBytes(result.image()), result.quality()));
                persistDiskCachedThumbnail(abs, "pdf", renderKey.sizePx(), renderKey.lastModifiedMs(), renderKey.fileSizeBytes(), result.provider(), result.image());
                pdfHighTierRendered.increment();
                pdfPromotionCompleted.increment();
            } finally {
                pdfActiveHighTierPromotions.decrementAndGet();
                pdfPromotionRunning.remove(target);
            }
        }));
    }

    private void scheduleOfficePromotion(Path abs,
                                       String ext,
                                       int sizePx,
                                       long lastMod,
                                       long fileSizeBytes,
                                       long viewportScope,
                                       long generationAtRequest,
                                       boolean fromLowTierRequest) {
        if (!ENABLE_OFFICE_PROGRESSIVE_UPGRADE || abs == null || !isOfficeDocumentExtension(ext)) {
            return;
        }
        String target = safePath(abs);
        OfficeRenderKey renderKey = new OfficeRenderKey(ext, target, Math.max(12, Math.min(512, sizePx)), lastMod, fileSizeBytes, viewportScope, generationAtRequest);
        officePromotionRequests.put(target, renderKey);
        ScheduledFuture<?> prior = officePromotionPending.remove(target);
        if (prior != null) {
            prior.cancel(false);
        }
        long delayMs = Math.max(25L, OFFICE_PROGRESSIVE_PROMOTION_DELAY_MS + ((fromLowTierRequest || isViewportMoving()) ? VIEWPORT_SETTLE_MS : 0L));
        ScheduledFuture<?> future = scheduler.schedule(() -> runOfficePromotion(abs, renderKey), delayMs, TimeUnit.MILLISECONDS);
        officePromotionPending.put(target, future);
        officePromotionQueuedCount.increment();
    }

    private void runOfficePromotion(Path abs, OfficeRenderKey renderKey) {
        if (renderKey == null || abs == null) {
            officePromotionSkipped.increment();
            return;
        }
        String target = renderKey.path();
        ScheduledFuture<?> currentFuture = officePromotionPending.remove(target);
        if (currentFuture != null && currentFuture.isCancelled()) {
            officePromotionSkipped.increment();
            return;
        }
        if (!ENABLE_OFFICE_PROGRESSIVE_UPGRADE || generation.get() != renderKey.generation()) {
            officePromotionSkipped.increment();
            return;
        }
        if (renderKey.viewportScope() != viewportScopeGeneration.get()) {
            officePromotionSkipped.increment();
            return;
        }
        if (isViewportMoving()) {
            scheduleOfficePromotion(abs, renderKey.ext(), renderKey.sizePx(), renderKey.lastModifiedMs(), renderKey.fileSizeBytes(), renderKey.viewportScope(), renderKey.generation(), false);
            return;
        }
        if (!isFileVersionCurrent(abs, renderKey.lastModifiedMs(), renderKey.fileSizeBytes())) {
            officePromotionSkipped.increment();
            return;
        }
        ThumbKey key = new ThumbKey(target, renderKey.sizePx(), renderKey.lastModifiedMs(), renderKey.fileSizeBytes());
        CachedThumb cached = cache.get(key);
        if (cached != null && cached.image != null && !cached.isLowQualityPreview()) {
            officePromotionSkipped.increment();
            return;
        }
        if (!officePromotionRunning.add(target)) {
            officePromotionSkipped.increment();
            return;
        }
        if (officeActiveHighTierPromotions.incrementAndGet() > OFFICE_MAX_ACTIVE_HIGH_TIER_PROMOTIONS) {
            officeActiveHighTierPromotions.decrementAndGet();
            officePromotionRunning.remove(target);
            scheduleOfficePromotion(abs, renderKey.ext(), renderKey.sizePx(), renderKey.lastModifiedMs(), renderKey.fileSizeBytes(), renderKey.viewportScope(), renderKey.generation(), false);
            return;
        }
        decodeExecutor.execute(new PrioritizedRunnable(RequestPriority.BACKGROUND, seq.incrementAndGet(), renderKey.viewportScope(), key, new CompletableFuture<>(), () -> {
            try {
                LoadResult result = loadThumbnail(abs, renderKey.ext(), renderKey.sizePx(), null, DocumentRenderTier.HIGH);
                if (result == null || !result.hasImage()) {
                    officePromotionSkipped.increment();
                    return;
                }
                if (generation.get() != renderKey.generation() || renderKey.viewportScope() != viewportScopeGeneration.get()) {
                    officePromotionSkipped.increment();
                    return;
                }
                if (!isFileVersionCurrent(abs, renderKey.lastModifiedMs(), renderKey.fileSizeBytes())) {
                    officePromotionSkipped.increment();
                    return;
                }
                cache.put(key, new CachedThumb(renderKey.lastModifiedMs(), renderKey.fileSizeBytes(), result.image(), approxBytes(result.image()), result.quality()));
                if (!result.isLowQualityPreview()) {
                    persistDiskCachedThumbnail(abs, renderKey.ext(), renderKey.sizePx(), renderKey.lastModifiedMs(), renderKey.fileSizeBytes(), result.provider(), result.image());
                }
                officeHighTierRendered.increment();
                officePromotionCompleted.increment();
            } finally {
                officeActiveHighTierPromotions.decrementAndGet();
                officePromotionRunning.remove(target);
            }
        }));
    }

    private void trimPdfViewportTrackingIfNeeded() {
        int limit = Math.max(1, PDF_PROGRESSIVE_VISIBLE_TRACK_LIMIT);
        int over = pdfViewportStates.size() - limit;
        if (over <= 0) {
            return;
        }
        ArrayList<Map.Entry<String, PdfViewportState>> entries = new ArrayList<>(pdfViewportStates.entrySet());
        entries.sort(Comparator.comparingLong(e -> e.getValue() == null ? Long.MIN_VALUE : e.getValue().lastUpdateNanos()));
        for (Map.Entry<String, PdfViewportState> entry : entries) {
            if (over-- <= 0) {
                break;
            }
            String target = entry.getKey();
            if (target == null) {
                continue;
            }
            pdfViewportStates.remove(target, entry.getValue());
            pdfPromotionRequests.remove(target);
            ScheduledFuture<?> future = pdfPromotionPending.remove(target);
            if (future != null) {
                future.cancel(false);
            }
        }
    }


    private boolean shouldReschedulePendingWork(ThumbKey key, RequestPriority requestedPriority, long requestedViewportScope) {
        RequestPriority existingPriority = pendingPriorities.get(key);
        Long existingScope = pendingViewportScopes.get(key);
        if (existingPriority == null) {
            return true;
        }
        if (requestedPriority.p > existingPriority.p) {
            return true;
        }
        return !Objects.equals(existingScope, requestedViewportScope);
    }

    private void scheduleQueuedDecode(ThumbKey key,
                                      Path abs,
                                      String ext,
                                      int sizePx,
                                      RequestPriority pr,
                                      long lastMod,
                                      long fileSizeBytes,
                                      long genAtSchedule,
                                      long viewportScope,
                                      PdfRenderTier pdfTier,
                                      DocumentRenderTier documentTier) {
        ScheduledFuture<?> prev = pending.get(key);
        if (prev != null) {
            prev.cancel(false);
        }
        final long seqNo = seq.incrementAndGet();
        ScheduledFuture<?> scheduled = scheduler.schedule(() ->
                startQueuedDecode(key, abs, ext, sizePx, pr, lastMod, fileSizeBytes, genAtSchedule, seqNo, viewportScope, pdfTier, documentTier),
                computeScheduleDelayMs(pr), TimeUnit.MILLISECONDS);

        pending.put(key, scheduled);
        pendingPriorities.put(key, pr);
        pendingViewportScopes.put(key, viewportScope);
    }

    private void startQueuedDecode(ThumbKey key,
                                   Path abs,
                                   String ext,
                                   int sizePx,
                                   RequestPriority pr,
                                   long lastMod,
                                   long fileSizeBytes,
                                   long genAtSchedule,
                                   long seqNo,
                                   long viewportScope,
                                   PdfRenderTier pdfTier,
                                   DocumentRenderTier documentTier) {
        // If preempted, drop.
        if (generation.get() != genAtSchedule) {
            staleGenerationDrops.increment();
            completeAndCleanupPreempted(key);
            return;
        }
        if (!isViewportScopeCurrent(pr, viewportScope)) {
            staleViewportScopeDrops.increment();
            completeAndCleanupPreempted(key);
            return;
        }

        // If no one cares anymore, cancel pending work and clean up.
        if (subscriberCount(key) <= 0) {
            staleSubscriberDrops.increment();
            completeAndCleanupPreempted(key);
            return;
        }

        if (pr == RequestPriority.BACKGROUND && isViewportMoving()
                && queuedCountAtOrAbove(RequestPriority.VISIBLE.p) >= MOVING_VISIBLE_BACKLOG_LIMIT) {
            movingBackgroundDeferrals.increment();
            ScheduledFuture<?> retry = scheduler.schedule(
                    () -> startQueuedDecode(key, abs, ext, sizePx, pr, lastMod, fileSizeBytes, genAtSchedule, seq.incrementAndGet(), viewportScope, pdfTier, documentTier),
                    MOVING_BACKGROUND_EXTRA_DELAY_MS,
                    TimeUnit.MILLISECONDS
            );
            pending.put(key, retry);
            pendingPriorities.put(key, pr);
            pendingViewportScopes.put(key, viewportScope);
            return;
        }

        CompletableFuture<Image> current = inFlight.get(key);
        if (current == null || current.isDone()) {
            pending.remove(key);
            pendingPriorities.remove(key);
            pendingViewportScopes.remove(key);
            return;
        }

        if (!tryAcquireStartSlot(pr)) {
            throttleDeferrals.increment();
            ScheduledFuture<?> retry = scheduler.schedule(
                    () -> startQueuedDecode(key, abs, ext, sizePx, pr, lastMod, fileSizeBytes, genAtSchedule, seq.incrementAndGet(), viewportScope, pdfTier, documentTier),
                    THROTTLE_RETRY_MS,
                    TimeUnit.MILLISECONDS
            );
            pending.put(key, retry);
            pendingPriorities.put(key, pr);
            pendingViewportScopes.put(key, viewportScope);
            return;
        }

        running.add(key);
        queued.increment();
        long startNanos = System.nanoTime();
        inFlightCount.incrementAndGet();

        // Hard cap queue depth (soak/leak guardrail).
        trimDecodeQueueIfNeeded();

        // Submit a comparable task so the executor orders by priority.
        decodeExecutor.execute(new PrioritizedRunnable(pr, seqNo, viewportScope, key, current, () -> {
            LoadResult result = null;
            try {
                // If preempted mid-flight, skip decode.
                if (generation.get() != genAtSchedule) {
                    staleGenerationDrops.increment();
                    return;
                }
                if (!isViewportScopeCurrent(pr, viewportScope)) {
                    staleViewportScopeDrops.increment();
                    return;
                }

                result = loadThumbnail(abs, ext, sizePx, pdfTier, documentTier);
            } catch (Throwable ignored) {
                result = new LoadResult(null, providerFor(ext), renderQualityFor(ext, pdfTier, documentTier));
            } finally {
                try {
                    long dur = System.nanoTime() - startNanos;
                    decodeCount.increment();
                    decodeNanos.add(dur);

                    if (generation.get() != genAtSchedule) {
                        // stale completion: do not cache
                        staleGenerationDrops.increment();
                        current.complete(null);
                        fallbackUsed.increment();
                        failed.increment();
                        return;
                    }
                    if (!isViewportScopeCurrent(pr, viewportScope)) {
                        staleViewportScopeDrops.increment();
                        staleCompletionDiscards.increment();
                        current.complete(null);
                        return;
                    }

                    if (!isFileVersionCurrent(abs, lastMod, fileSizeBytes)) {
                        staleFileVersionDrops.increment();
                        staleCompletionDiscards.increment();
                        current.complete(null);
                        return;
                    }

                    if (result != null && result.hasImage()) {
                        long approxBytes = approxBytes(result.image());
                        cache.put(key, new CachedThumb(lastMod, fileSizeBytes, result.image(), approxBytes, result.quality()));
                        if (!result.isLowQualityPreview()) {
                            persistDiskCachedThumbnail(abs, ext, sizePx, lastMod, fileSizeBytes, result.provider(), result.image());
                        }
                        if (result.quality() == RenderQuality.PDF_LOW) {
                            pdfLowTierRendered.increment();
                            maybeSchedulePdfPromotionAfterLowTier(abs, ext, key, pr, lastMod, fileSizeBytes, viewportScope);
                        } else if (result.quality() == RenderQuality.PDF_HIGH) {
                            pdfHighTierRendered.increment();
                        } else if (result.quality() == RenderQuality.DOC_LOW) {
                            officeLowTierRendered.increment();
                            maybeScheduleOfficePromotionAfterLowTier(abs, ext, key, pr, lastMod, fileSizeBytes, viewportScope);
                        } else if (result.quality() == RenderQuality.DOC_HIGH) {
                            officeHighTierRendered.increment();
                        }
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
                    pendingPriorities.remove(key);
                    pendingViewportScopes.remove(key);
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
        prunePdfPromotionState(currentScope);
        pruneOfficePromotionState(currentScope);
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
            java.util.Iterator<Runnable> iterator = decodeExecutor.getQueue().iterator();
            while (iterator.hasNext()) {
                Runnable runnable = iterator.next();
                if (!(runnable instanceof PrioritizedRunnable task)) {
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

    private void prunePdfPromotionState(long currentScope) {
        pdfViewportStates.forEach((target, state) -> {
            if (state != null && state.generation() == currentScope) {
                return;
            }
            pdfViewportStates.remove(target, state);
            pdfPromotionRequests.remove(target);
            ScheduledFuture<?> future = pdfPromotionPending.remove(target);
            if (future != null) {
                future.cancel(false);
            }
        });
    }

    private void pruneOfficePromotionState(long currentScope) {
        officePromotionRequests.forEach((target, request) -> {
            if (request != null && request.viewportScope() == currentScope) {
                return;
            }
            officePromotionRequests.remove(target, request);
            ScheduledFuture<?> future = officePromotionPending.remove(target);
            if (future != null) {
                future.cancel(false);
            }
        });
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
        pendingPriorities.remove(key);
        pendingViewportScopes.remove(key);
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
            pendingPriorities.remove(key);
            pendingViewportScopes.remove(key);

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
            pendingPriorities.remove(pr.key);
            pendingViewportScopes.remove(pr.key);
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

    private boolean isFileVersionCurrent(Path path, long lastModifiedMs, long fileSizeBytes) {
        return safeLastModifiedMs(path) == lastModifiedMs && safeFileSizeBytes(path) == fileSizeBytes;
    }

    private ExecutorService documentExecutorFor(String ext) {
        return isPdfExtension(ext) ? pdfDocumentExecutor : officeDocumentExecutor;
    }

    private int executorQueueSize(ExecutorService executor) {
        if (executor instanceof ThreadPoolExecutor tpe) {
            return tpe.getQueue().size();
        }
        return -1;
    }

    private int executorActiveCount(ExecutorService executor) {
        if (executor instanceof ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return -1;
    }

    private boolean tryEnterPdfRender(String target, PdfRenderTier tier) {
        AtomicInteger totalCounter = pdfActiveDocumentRenders.computeIfAbsent(target, ignored -> new AtomicInteger(0));
        int totalActive = totalCounter.incrementAndGet();
        if (totalActive > PDF_MAX_ACTIVE_RENDERS_PER_DOCUMENT) {
            if (totalCounter.decrementAndGet() <= 0) {
                pdfActiveDocumentRenders.remove(target, totalCounter);
            }
            return false;
        }
        if (tier == PdfRenderTier.HIGH) {
            AtomicInteger highCounter = pdfActiveDocumentHighTierRenders.computeIfAbsent(target, ignored -> new AtomicInteger(0));
            int highActive = highCounter.incrementAndGet();
            if (highActive > PDF_MAX_ACTIVE_HIGH_TIER_PER_DOCUMENT) {
                if (highCounter.decrementAndGet() <= 0) {
                    pdfActiveDocumentHighTierRenders.remove(target, highCounter);
                }
                if (totalCounter.decrementAndGet() <= 0) {
                    pdfActiveDocumentRenders.remove(target, totalCounter);
                }
                return false;
            }
        }
        return true;
    }

    private void exitPdfRender(String target, PdfRenderTier tier) {
        AtomicInteger totalCounter = pdfActiveDocumentRenders.get(target);
        if (totalCounter != null && totalCounter.decrementAndGet() <= 0) {
            pdfActiveDocumentRenders.remove(target, totalCounter);
        }
        if (tier == PdfRenderTier.HIGH) {
            AtomicInteger highCounter = pdfActiveDocumentHighTierRenders.get(target);
            if (highCounter != null && highCounter.decrementAndGet() <= 0) {
                pdfActiveDocumentHighTierRenders.remove(target, highCounter);
            }
        }
    }

    private LoadResult loadThumbnail(Path path, String ext, int sizePx, PdfRenderTier pdfTier, DocumentRenderTier documentTier) {
        try {
            if (!Files.isRegularFile(path)) {
                return new LoadResult(null, ThumbnailProvider.UNSUPPORTED, RenderQuality.STANDARD);
            }
        } catch (Exception ex) {
            return new LoadResult(null, ThumbnailProvider.UNSUPPORTED, RenderQuality.STANDARD);
        }

        ThumbnailProvider provider = providerFor(ext);
        try {
            return switch (provider) {
                case JAVAFX_NATIVE -> new LoadResult(loadJavaFxNativeThumbnail(path, sizePx), provider, RenderQuality.STANDARD);
                case THUMBNAILS4J_DOCUMENT -> new LoadResult(loadDocumentThumbnail(path, ext, sizePx, pdfTier, documentTier), provider, renderQualityFor(ext, pdfTier, documentTier));
                case IMAGEIO -> new LoadResult(loadImageIoThumbnail(path, sizePx), provider, RenderQuality.STANDARD);
                case DISABLED, UNSUPPORTED -> new LoadResult(null, provider, renderQualityFor(ext, pdfTier, documentTier));
            };
        } catch (Throwable ignored) {
            return new LoadResult(null, provider, renderQualityFor(ext, pdfTier, documentTier));
        }
    }

    private RenderQuality renderQualityFor(String ext, PdfRenderTier pdfTier, DocumentRenderTier documentTier) {
        if (isPdfExtension(ext)) {
            return pdfTier == PdfRenderTier.HIGH ? RenderQuality.PDF_HIGH : RenderQuality.PDF_LOW;
        }
        if (isOfficeDocumentExtension(ext)) {
            return documentTier == DocumentRenderTier.LOW ? RenderQuality.DOC_LOW : RenderQuality.DOC_HIGH;
        }
        return RenderQuality.STANDARD;
    }

    private RenderQuality defaultDiskCacheRenderQuality(String ext) {
        if (isPdfExtension(ext)) {
            return RenderQuality.PDF_HIGH;
        }
        if (isOfficeDocumentExtension(ext)) {
            return RenderQuality.DOC_HIGH;
        }
        return RenderQuality.STANDARD;
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

    private Image loadDocumentThumbnail(Path path, String ext, int sizePx, PdfRenderTier pdfTier, DocumentRenderTier documentTier) {
        Future<BufferedImage> future = null;
        long lastMod = safeLastModifiedMs(path);
        long fileSizeBytes = safeFileSizeBytes(path);
        long timeoutBudgetMs = timeoutBudgetMsFor(path, ext, sizePx, lastMod, fileSizeBytes, pdfTier);
        long waitBudgetMs = isPdfExtension(ext)
                ? timeoutBudgetMs + Math.max(0L, PDF_TIMEOUT_JOIN_GRACE_MS)
                : DOCUMENT_TIMEOUT_MS;
        try {
            if (isPdfExtension(ext) && shouldFallbackLargePdf(path, lastMod, fileSizeBytes)) {
                return null;
            }
            if (isOfficeDocumentExtension(ext) && isDocumentInFailureCooldown(path, ext, lastMod, fileSizeBytes)) {
                documentFailureCooldownSkips.increment();
                return null;
            }

            future = documentExecutorFor(ext).submit(() -> renderDocumentThumbnail(path, ext, sizePx, timeoutBudgetMs, pdfTier, documentTier));
            BufferedImage bi = future.get(waitBudgetMs, TimeUnit.MILLISECONDS);
            if (bi == null) {
                if (isOfficeDocumentExtension(ext)) {
                    markDocumentFailureCooldown(path, ext, lastMod, fileSizeBytes, null);
                }
                return null;
            }
            if (isOfficeDocumentExtension(ext)) {
                clearDocumentFailureCooldown(path, ext, lastMod, fileSizeBytes);
            }
            return SwingFXUtils.toFXImage(bi, null);
        } catch (TimeoutException te) {
            documentTimeouts.increment();
            if (isPdfExtension(ext)) {
                recordPdfRenderTimeout(path, lastMod, fileSizeBytes, timeoutBudgetMs);
                markPdfTimeoutCooldown(path, lastMod, fileSizeBytes);
                logPdfTimeout(path, te, lastMod, fileSizeBytes, timeoutBudgetMs);
            } else if (isOfficeDocumentExtension(ext)) {
                markDocumentFailureCooldown(path, ext, lastMod, fileSizeBytes, te);
            }
            if (future != null) {
                try {
                    future.cancel(false);
                } catch (Throwable ignored) {
                }
            }
            return null;
        } catch (Throwable t) {
            if (isPdfExtension(ext) && isInterruptRelatedPdfFailure(t)) {
                recordPdfRenderTimeout(path, lastMod, fileSizeBytes, timeoutBudgetMs);
                markPdfTimeoutCooldown(path, lastMod, fileSizeBytes);
                logPdfInterrupt(path, t, lastMod, fileSizeBytes);
                clearThreadInterruptFlag();
            } else if (isOfficeDocumentExtension(ext)) {
                markDocumentFailureCooldown(path, ext, lastMod, fileSizeBytes, t);
            }
            if (future != null) {
                try {
                    future.cancel(false);
                } catch (Throwable ignored2) {
                }
            }
            return null;
        }
    }

    private BufferedImage renderDocumentThumbnail(Path path, String ext, int sizePx, long timeoutBudgetMs, PdfRenderTier pdfTier, DocumentRenderTier documentTier) {
        try {
            DocumentBackend backend = documentBackendFor(ext);
            if (backend == DocumentBackend.PDFBOX) {
                return renderPdfThumbnail(path, sizePx, timeoutBudgetMs, pdfTier);
            }
            if (backend != DocumentBackend.THUMBNAILS4J) {
                return null;
            }

            Thumbnailer thumbnailer = newDocumentThumbnailer(ext);
            if (thumbnailer == null) {
                return null;
            }
            int backendRequestSizePx = computeOfficeBackendRequestSizePx(sizePx, documentTier);
            java.util.List<?> thumbs = thumbnailer.getThumbnails(
                    path.toFile(),
                    java.util.List.of(new Dimensions(backendRequestSizePx, backendRequestSizePx))
            );
            if (thumbs == null || thumbs.isEmpty()) {
                return null;
            }
            Object first = thumbs.get(0);
            if (!(first instanceof BufferedImage bi)) {
                return null;
            }
            return bi;
        } catch (Throwable t) {
            if (isPdfExtension(ext)) {
                if (isMissingJpeg2000Reader(t)) {
                    logMissingJpeg2000Reader(path, t);
                } else if (isMissingJbig2Reader(t)) {
                    logMissingJbig2Reader(path, t);
                } else if (isMalformedPdf(t)) {
                    logMalformedPdf(path, t);
                }
            }
            return null;
        }
    }

    private BufferedImage renderPdfThumbnail(Path path, int sizePx, long timeoutBudgetMs, PdfRenderTier pdfTier) {
        if (path == null || sizePx <= 0) {
            return null;
        }
        long lastMod = safeLastModifiedMs(path);
        long fileSizeBytes = safeFileSizeBytes(path);
        if (isPdfInTimeoutCooldown(path, lastMod, fileSizeBytes)) {
            return null;
        }
        if (shouldFallbackLargePdf(path, lastMod, fileSizeBytes)) {
            return null;
        }
        if (!hasJpeg2000Reader() && pdfLikelyContainsJpx(path)) {
            logMissingJpeg2000Reader(path, null);
            return null;
        }
        if (!hasJbig2Reader() && pdfLikelyContainsJbig2(path)) {
            logMissingJbig2Reader(path, null);
            return null;
        }
        long renderStartNanos = System.nanoTime();
        String target = safePath(path);
        if (!tryEnterPdfRender(target, pdfTier == null ? PdfRenderTier.LOW : pdfTier)) {
            return null;
        }
        try {
            byte[] pdfBytes = readPdfBytesForThumbnail(path, lastMod, fileSizeBytes);
        if (pdfBytes == null) {
            return null;
        }
        if (shouldAbortPdfForBudget(path, lastMod, fileSizeBytes, timeoutBudgetMs, renderStartNanos)) {
            return null;
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() <= 0) {
                logMalformedPdf(path, new IllegalStateException("PDF has no pages"));
                return null;
            }
            PdfRenderPlan plan = buildPdfRenderPlan(path, document, sizePx, lastMod, fileSizeBytes, pdfTier);
            if (shouldAbortPdfForBudget(path, lastMod, fileSizeBytes, timeoutBudgetMs, renderStartNanos)) {
                return null;
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage rendered = renderer.renderImage(0, plan.scale(), ImageType.RGB);
            if (rendered == null) {
                return null;
            }
            if (shouldAbortPdfForBudget(path, lastMod, fileSizeBytes, timeoutBudgetMs, renderStartNanos)) {
                return null;
            }
            recordPdfRenderSuccess(path, lastMod, fileSizeBytes, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - renderStartNanos));
            return scaleToFitSquare(rendered, sizePx);
        } catch (Throwable t) {
            if (isInterruptRelatedPdfFailure(t)) {
                recordPdfRenderTimeout(path, lastMod, fileSizeBytes, timeoutBudgetMs);
                markPdfTimeoutCooldown(path, lastMod, fileSizeBytes);
                logPdfInterrupt(path, t, lastMod, fileSizeBytes);
                clearThreadInterruptFlag();
                return null;
            }
            if (isMissingJpeg2000Reader(t)) {
                logMissingJpeg2000Reader(path, t);
                return null;
            }
            if (isMissingJbig2Reader(t)) {
                logMissingJbig2Reader(path, t);
                return null;
            }
            if (isMalformedPdf(t)) {
                logMalformedPdf(path, t);
                return null;
            }
            LOG.log(Level.FINE, t, () -> "[Thumbs] PDF thumbnail render failed for " + safePath(path));
            return null;
        }
        } finally {
            exitPdfRender(target, pdfTier == null ? PdfRenderTier.LOW : pdfTier);
        }
    }


    private PdfRenderPlan buildPdfRenderPlan(Path path, PDDocument document, int requestedSizePx, long lastMod, long fileSizeBytes, PdfRenderTier pdfTier) {
        int safeRequestedSizePx = Math.max(12, requestedSizePx);
        int pageCount = Math.max(0, document == null ? 0 : document.getNumberOfPages());
        float firstPageWidthPts = 612.0f;
        float firstPageHeightPts = 792.0f;
        if (document != null && pageCount > 0) {
            try {
                PDPage page = document.getPage(0);
                if (page != null) {
                    PDRectangle cropBox = page.getCropBox();
                    if (cropBox != null) {
                        firstPageWidthPts = Math.max(1.0f, cropBox.getWidth());
                        firstPageHeightPts = Math.max(1.0f, cropBox.getHeight());
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        PdfRenderHistory history = currentPdfRenderHistory(path, lastMod, fileSizeBytes);
        double averageRenderMs = history == null ? 0.0d : history.averageRenderMs();
        int consecutiveTimeouts = history == null ? 0 : history.consecutiveTimeouts();
        PdfRenderTier effectiveTier = pdfTier == null ? PdfRenderTier.LOW : pdfTier;
        int effectiveSizePx = computeAdaptivePdfTargetSizePx(
                safeRequestedSizePx,
                fileSizeBytes,
                pageCount,
                firstPageWidthPts,
                firstPageHeightPts,
                averageRenderMs,
                consecutiveTimeouts
        );
        float baseScale = computePdfRenderScale(document, effectiveSizePx);
        float scaleCap = computeAdaptivePdfScaleCap(
                fileSizeBytes,
                pageCount,
                firstPageWidthPts,
                firstPageHeightPts,
                averageRenderMs,
                consecutiveTimeouts
        );
        if (effectiveTier == PdfRenderTier.LOW) {
            effectiveSizePx = Math.min(effectiveSizePx, Math.min(safeRequestedSizePx, 96));
            scaleCap = Math.min(scaleCap, isViewportMoving() ? 0.85f : 0.95f);
        } else {
            scaleCap = Math.max(scaleCap, 1.05f);
        }
        float finalScale = clampFloat(Math.min(baseScale, scaleCap), effectiveTier == PdfRenderTier.LOW ? 0.55f : 0.75f, 2.0f);
        boolean budgetReduced = effectiveSizePx < safeRequestedSizePx || finalScale + 0.0001f < baseScale;
        pdfAdaptiveBudgetPlans.increment();
        if (budgetReduced) {
            pdfAdaptiveBudgetDownshifts.increment();
        }
        boolean largeDocument = fileSizeBytes >= PDF_LARGE_DOC_SOFT_BYTES || pageCount >= PDF_LARGE_DOC_PAGE_COUNT_THRESHOLD;
        return new PdfRenderPlan(effectiveSizePx, finalScale, pageCount, largeDocument, budgetReduced, effectiveTier);
    }

    private long timeoutBudgetMsFor(Path path, String ext, int sizePx, long lastMod, long fileSizeBytes, PdfRenderTier pdfTier) {
        if (!isPdfExtension(ext)) {
            return DOCUMENT_TIMEOUT_MS;
        }
        PdfRenderHistory history = currentPdfRenderHistory(path, lastMod, fileSizeBytes);
        double averageRenderMs = history == null ? 0.0d : history.averageRenderMs();
        long budget = computeAdaptivePdfTimeoutMs(fileSizeBytes, sizePx, averageRenderMs, pdfTier);
        return clampLong(
                budget,
                Math.min(PDF_ADAPTIVE_TIMEOUT_MIN_MS, PDF_ADAPTIVE_TIMEOUT_MAX_MS),
                Math.max(PDF_ADAPTIVE_TIMEOUT_MIN_MS, PDF_ADAPTIVE_TIMEOUT_MAX_MS)
        );
    }

    private long computeAdaptivePdfTimeoutMs(long fileSizeBytes, int sizePx, double averageRenderMs, PdfRenderTier pdfTier) {
        long budget = DOCUMENT_TIMEOUT_MS;
        if (fileSizeBytes >= (8L * 1024L * 1024L)) {
            budget += 500L;
        }
        if (fileSizeBytes >= PDF_LARGE_DOC_SOFT_BYTES) {
            budget += 750L;
        }
        if (fileSizeBytes >= (PDF_LARGE_DOC_HARD_FALLBACK_BYTES / 2L)) {
            budget += 750L;
        }
        if (sizePx >= 128) {
            budget += 250L;
        }
        if (sizePx >= 256) {
            budget += 500L;
        }
        if (averageRenderMs > 0.0d) {
            budget = Math.max(budget, Math.round(averageRenderMs * 1.6d));
        }
        if (pdfTier == PdfRenderTier.LOW) {
            budget = Math.max(PDF_ADAPTIVE_TIMEOUT_MIN_MS, Math.min(budget, DOCUMENT_TIMEOUT_MS + 500L));
        } else if (pdfTier == PdfRenderTier.HIGH) {
            budget += 500L;
        }
        return budget;
    }

    private int computeAdaptivePdfTargetSizePx(int requestedSizePx,
                                               long fileSizeBytes,
                                               int pageCount,
                                               float firstPageWidthPts,
                                               float firstPageHeightPts,
                                               double averageRenderMs,
                                               int consecutiveTimeouts) {
        int effectiveSizePx = Math.max(12, requestedSizePx);
        boolean largeDocument = fileSizeBytes >= PDF_LARGE_DOC_SOFT_BYTES || pageCount >= PDF_LARGE_DOC_PAGE_COUNT_THRESHOLD;
        boolean hugeFirstPage = Math.max(firstPageWidthPts, firstPageHeightPts) >= 1_400.0f
                || (firstPageWidthPts * firstPageHeightPts) >= 900_000.0f;
        if (largeDocument) {
            effectiveSizePx = Math.min(effectiveSizePx, 128);
        }
        if (hugeFirstPage) {
            effectiveSizePx = Math.min(effectiveSizePx, 128);
        }
        if (pageCount >= (PDF_LARGE_DOC_PAGE_COUNT_THRESHOLD * 2)) {
            effectiveSizePx = Math.min(effectiveSizePx, 96);
        }
        if (averageRenderMs >= (DOCUMENT_TIMEOUT_MS * 0.75d)) {
            effectiveSizePx = Math.min(effectiveSizePx, 96);
        }
        if (consecutiveTimeouts >= PDF_RECOVERY_TIMEOUT_STREAK_THRESHOLD) {
            effectiveSizePx = Math.min(effectiveSizePx, 96);
        }
        return effectiveSizePx;
    }

    private float computeAdaptivePdfScaleCap(long fileSizeBytes,
                                             int pageCount,
                                             float firstPageWidthPts,
                                             float firstPageHeightPts,
                                             double averageRenderMs,
                                             int consecutiveTimeouts) {
        float cap = 2.0f;
        boolean largeDocument = fileSizeBytes >= PDF_LARGE_DOC_SOFT_BYTES || pageCount >= PDF_LARGE_DOC_PAGE_COUNT_THRESHOLD;
        boolean hugeFirstPage = Math.max(firstPageWidthPts, firstPageHeightPts) >= 1_400.0f
                || (firstPageWidthPts * firstPageHeightPts) >= 900_000.0f;
        if (largeDocument) {
            cap = Math.min(cap, 1.0f);
        }
        if (hugeFirstPage) {
            cap = Math.min(cap, 0.90f);
        }
        if (averageRenderMs >= (DOCUMENT_TIMEOUT_MS * 0.75d)) {
            cap = Math.min(cap, 0.90f);
        }
        if (consecutiveTimeouts >= PDF_RECOVERY_TIMEOUT_STREAK_THRESHOLD) {
            cap = Math.min(cap, 0.85f);
        }
        return cap;
    }

    private boolean shouldAbortPdfForBudget(Path path,
                                            long lastMod,
                                            long fileSizeBytes,
                                            long timeoutBudgetMs,
                                            long renderStartNanos) {
        if (timeoutBudgetMs <= 0L) {
            return false;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - renderStartNanos);
        if (elapsedMs < timeoutBudgetMs) {
            return false;
        }
        documentTimeouts.increment();
        recordPdfRenderTimeout(path, lastMod, fileSizeBytes, timeoutBudgetMs);
        markPdfTimeoutCooldown(path, lastMod, fileSizeBytes);
        logPdfTimeout(path, null, lastMod, fileSizeBytes, timeoutBudgetMs);
        return true;
    }

    private boolean shouldFallbackLargePdf(Path path, long lastMod, long fileSizeBytes) {
        if (path == null || fileSizeBytes <= 0L) {
            return false;
        }
        PdfRenderHistory history = currentPdfRenderHistory(path, lastMod, fileSizeBytes);
        if (fileSizeBytes >= PDF_LARGE_DOC_HARD_FALLBACK_BYTES) {
            pdfLargeDocFallbacks.increment();
            logPdfLargeDocumentFallback(path, lastMod, fileSizeBytes, history, "size-threshold");
            return true;
        }
        if (history != null
                && history.consecutiveTimeouts() >= PDF_RECOVERY_TIMEOUT_STREAK_THRESHOLD
                && fileSizeBytes >= PDF_LARGE_DOC_SOFT_BYTES) {
            pdfLargeDocFallbacks.increment();
            logPdfLargeDocumentFallback(path, lastMod, fileSizeBytes, history, "timeout-recovery");
            return true;
        }
        return false;
    }

    private PdfRenderHistory currentPdfRenderHistory(Path path, long lastMod, long fileSizeBytes) {
        String target = safePath(path);
        PdfRenderHistory history = PDF_RENDER_HISTORY.get(target);
        if (history == null) {
            return null;
        }
        if (history.matches(lastMod, fileSizeBytes)) {
            return history;
        }
        if (PDF_RENDER_HISTORY.remove(target, history)) {
            pdfHistoryResets.increment();
        }
        return null;
    }

    private void recordPdfRenderSuccess(Path path, long lastMod, long fileSizeBytes, long renderMs) {
        String target = safePath(path);
        PDF_RENDER_HISTORY.compute(target, (ignored, existing) -> {
            PdfRenderHistory base = existing;
            if (base != null && !base.matches(lastMod, fileSizeBytes)) {
                pdfHistoryResets.increment();
                base = null;
            }
            if (base == null) {
                return new PdfRenderHistory(lastMod, fileSizeBytes, Math.max(1.0d, renderMs), 1, 0);
            }
            return base.afterSuccess(renderMs);
        });
    }

    private void recordPdfRenderTimeout(Path path, long lastMod, long fileSizeBytes, long timeoutBudgetMs) {
        String target = safePath(path);
        PDF_RENDER_HISTORY.compute(target, (ignored, existing) -> {
            PdfRenderHistory base = existing;
            if (base != null && !base.matches(lastMod, fileSizeBytes)) {
                pdfHistoryResets.increment();
                base = null;
            }
            if (base == null) {
                return new PdfRenderHistory(lastMod, fileSizeBytes, Math.max(1.0d, timeoutBudgetMs), 0, 1);
            }
            return base.afterTimeout(timeoutBudgetMs);
        });
    }

    private byte[] readPdfBytesForThumbnail(Path path, long lastMod, long fileSizeBytes) {
        if (path == null) {
            return null;
        }
        try {
            long size = Files.size(path);
            if (size <= 0L) {
                logMalformedPdf(path, new IllegalStateException("PDF is empty"));
                return null;
            }
            if (size > PDF_IN_MEMORY_MAX_BYTES) {
                logPdfOversize(path, size, lastMod, fileSizeBytes);
                return null;
            }
            return Files.readAllBytes(path);
        } catch (Throwable t) {
            if (isMalformedPdf(t)) {
                logMalformedPdf(path, t);
            } else {
                LOG.log(Level.FINE, t, () -> "[Thumbs] Failed to stage PDF bytes for thumbnail rendering: " + safePath(path));
            }
            return null;
        }
    }

    private boolean isPdfInTimeoutCooldown(Path path, long lastMod, long fileSizeBytes) {
        String target = safePath(path);
        PdfCooldownState state = PDF_TIMEOUT_COOLDOWN_UNTIL_MS.get(target);
        if (state == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (state.untilMs() <= now) {
            PDF_TIMEOUT_COOLDOWN_UNTIL_MS.remove(target, state);
            return false;
        }
        if (!state.matches(lastMod, fileSizeBytes)) {
            PDF_TIMEOUT_COOLDOWN_UNTIL_MS.remove(target, state);
            pdfCooldownInvalidations.increment();
            return false;
        }
        return true;
    }

    private void markPdfTimeoutCooldown(Path path, long lastMod, long fileSizeBytes) {
        PDF_TIMEOUT_COOLDOWN_UNTIL_MS.put(safePath(path), new PdfCooldownState(lastMod, fileSizeBytes, System.currentTimeMillis() + PDF_TIMEOUT_COOLDOWN_MS));
    }

    private void clearThreadInterruptFlag() {
        if (Thread.currentThread().isInterrupted()) {
            Thread.interrupted();
        }
    }

    private void quietPdfThumbnailNoiseLoggers() {
        Logger.getLogger("org.apache.pdfbox.cos.COSObject").setLevel(Level.OFF);
        Logger.getLogger("org.apache.pdfbox.contentstream.PDFStreamEngine").setLevel(Level.OFF);
        Logger.getLogger("org.apache.pdfbox.rendering.PageDrawer").setLevel(Level.OFF);
        Logger.getLogger("org.apache.pdfbox.pdfparser.COSParser").setLevel(Level.OFF);
    }

    private void scheduleImageSupportInitialization() {
        if (imageSupportInitialized.get()) {
            return;
        }
        if (!imageSupportInitScheduled.compareAndSet(false, true)) {
            return;
        }
        long delayMs = Math.max(0L, CAPABILITY_INIT_DELAY_MS);
        scheduler.schedule(() -> {
            try {
                ensureImageSupportInitializedNow();
            } finally {
                imageSupportInitScheduled.set(false);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void ensureImageSupportInitializedNow() {
        if (imageSupportInitialized.get()) {
            return;
        }
        synchronized (this) {
            if (imageSupportInitialized.get()) {
                return;
            }
            ImageSupport.scanForPlugins();
            ImageSupport.primePreferredThumbnailReaderCapabilities();
            primeJpeg2000ReaderAvailability();
            logThumbnailCapabilitySummaryOnce();
            imageSupportInitialized.set(true);
        }
    }

    private void ensureImageSupportInitializedForExtension(String normalizedExt) {
        if (normalizedExt == null || normalizedExt.isBlank()) {
            return;
        }
        if (!ImageSupport.isImageIoManagedExtension(normalizedExt)) {
            return;
        }
        ensureImageSupportInitializedNow();
    }

    private float computePdfRenderScale(PDDocument document, int sizePx) {
        if (document == null || sizePx <= 0) {
            return 1.0f;
        }
        try {
            PDPage page = document.getPage(0);
            if (page == null) {
                return 1.0f;
            }
            PDRectangle cropBox = page.getCropBox();
            if (cropBox == null) {
                return 1.0f;
            }
            float width = Math.max(1.0f, cropBox.getWidth());
            float height = Math.max(1.0f, cropBox.getHeight());
            float targetScale = Math.max(sizePx / width, sizePx / height) * 1.25f;
            return Math.max(0.75f, Math.min(2.0f, targetScale));
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    private void primeJpeg2000ReaderAvailability() {
        hasJpeg2000Reader();
        hasJbig2Reader();
    }

    private void logThumbnailCapabilitySummaryOnce() {
        if (!LOGGED_CAPABILITY_SUMMARY.compareAndSet(false, true)) {
            return;
        }
        if (!LOG.isLoggable(Level.INFO)) {
            return;
        }
        Map<String, Boolean> capabilities = ImageSupport.preferredThumbnailReaderSupportSnapshot();
        boolean jpeg2000 = hasJpeg2000Reader();
        boolean jbig2 = hasJbig2Reader();
        LOG.info(() -> "[Thumbs] ImageIO capabilities: " + summarizeCapabilities(capabilities)
                + ", jpeg2000=" + jpeg2000
                + ", jbig2=" + jbig2
                + " | AVIF/HEIF requires NightMonkeys native access and native libraries on java.library.path.");
    }

    private String summarizeCapabilities(Map<String, Boolean> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return "<none>";
        }
        StringBuilder sb = new StringBuilder(96);
        boolean first = true;
        for (Map.Entry<String, Boolean> entry : capabilities.entrySet()) {
            if (!first) {
                sb.append(',').append(' ');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(Boolean.TRUE.equals(entry.getValue()));
        }
        return sb.toString();
    }

    private boolean hasJpeg2000Reader() {
        Boolean cached = JPEG2000_READER_AVAILABLE;
        if (cached != null) {
            return cached;
        }
        synchronized (AsyncThumbnailService.class) {
            cached = JPEG2000_READER_AVAILABLE;
            if (cached != null) {
                return cached;
            }
            boolean available = hasImageReaderByFormat("JPEG2000")
                    || hasImageReaderByFormat("jpeg2000")
                    || hasImageReaderByFormat("JP2")
                    || hasImageReaderBySuffix("jp2")
                    || hasImageReaderBySuffix("j2k")
                    || hasImageReaderByMimeType("image/jp2")
                    || hasImageReaderByMimeType("image/jpeg2000");
            JPEG2000_READER_AVAILABLE = available;
            return available;
        }
    }

    private boolean hasJbig2Reader() {
        Boolean cached = JBIG2_READER_AVAILABLE;
        if (cached != null) {
            return cached;
        }
        synchronized (AsyncThumbnailService.class) {
            cached = JBIG2_READER_AVAILABLE;
            if (cached != null) {
                return cached;
            }
            boolean available = hasImageReaderByFormat("JBIG2")
                    || hasImageReaderByFormat("jbig2")
                    || hasImageReaderBySuffix("jb2")
                    || hasImageReaderBySuffix("jbig2")
                    || hasImageReaderByMimeType("image/x-jbig2");
            JBIG2_READER_AVAILABLE = available;
            return available;
        }
    }

    private boolean hasImageReaderByFormat(String formatName) {
        try {
            return ImageIO.getImageReadersByFormatName(formatName).hasNext();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasImageReaderBySuffix(String suffix) {
        try {
            return ImageIO.getImageReadersBySuffix(suffix).hasNext();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasImageReaderByMimeType(String mimeType) {
        try {
            return ImageIO.getImageReadersByMIMEType(mimeType).hasNext();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isPdfExtension(String ext) {
        return ext != null && "pdf".equalsIgnoreCase(ext);
    }

    private boolean pdfLikelyContainsJpx(Path path) {
        if (path == null) {
            return false;
        }
        try {
            long size = Files.size(path);
            if (size <= 0L) {
                return false;
            }
            long scanLimit = Math.min(size, PDF_JPX_SCAN_LIMIT_BYTES);
            byte[] data = new byte[8192];
            int carry = 0;
            long remaining = scanLimit;
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                while (remaining > 0L) {
                    int toRead = (int) Math.min(data.length - carry, remaining);
                    int read = raf.read(data, carry, toRead);
                    if (read <= 0) {
                        break;
                    }
                    int total = carry + read;
                    if (containsAsciiToken(data, total, "/JPXDecode")
                            || containsAsciiToken(data, total, "/JPX ")
                            || containsAsciiToken(data, total, "/JPX\n")
                            || containsAsciiToken(data, total, "/JPX\r")) {
                        return true;
                    }
                    carry = Math.min(15, total);
                    if (carry > 0) {
                        System.arraycopy(data, total - carry, data, 0, carry);
                    }
                    remaining -= read;
                }
            }
        } catch (Throwable ignored) {
            // best effort preflight only
        }
        return false;
    }

    private boolean pdfLikelyContainsJbig2(Path path) {
        if (path == null) {
            return false;
        }
        try {
            long size = Files.size(path);
            if (size <= 0L) {
                return false;
            }
            long scanLimit = Math.min(size, PDF_JBIG2_SCAN_LIMIT_BYTES);
            byte[] data = new byte[8192];
            int carry = 0;
            long remaining = scanLimit;
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                while (remaining > 0L) {
                    int toRead = (int) Math.min(data.length - carry, remaining);
                    int read = raf.read(data, carry, toRead);
                    if (read <= 0) {
                        break;
                    }
                    int total = carry + read;
                    if (containsAsciiToken(data, total, "/JBIG2Decode")
                            || containsAsciiToken(data, total, "/JBIG2 ")
                            || containsAsciiToken(data, total, "/JBIG2\n")
                            || containsAsciiToken(data, total, "/JBIG2\r")) {
                        return true;
                    }
                    carry = Math.min(15, total);
                    if (carry > 0) {
                        System.arraycopy(data, total - carry, data, 0, carry);
                    }
                    remaining -= read;
                }
            }
        } catch (Throwable ignored) {
            // best effort preflight only
        }
        return false;
    }

    private boolean containsAsciiToken(byte[] data, int length, String token) {
        if (data == null || token == null || token.isEmpty() || length <= 0) {
            return false;
        }
        byte[] needle = token.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int max = length - needle.length;
        for (int i = 0; i <= max; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private boolean isInterruptRelatedPdfFailure(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof ClosedByInterruptException
                    || cursor instanceof ClosedChannelException
                    || cursor instanceof InterruptedException) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null && (message.contains("ClosedByInterruptException")
                    || message.contains("ClosedChannelException")
                    || message.contains("interrupted"))) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean isMissingJpeg2000Reader(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            String className = cursor.getClass().getName();
            String message = cursor.getMessage();
            if ("org.apache.pdfbox.filter.MissingImageReaderException".equals(className)
                    || (message != null && message.contains("Cannot read JPEG2000 image"))
                    || (message != null && message.contains("JAI Image I/O Tools are not installed"))) {
                JPEG2000_READER_AVAILABLE = Boolean.FALSE;
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean isMissingJbig2Reader(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            String className = cursor.getClass().getName();
            String message = cursor.getMessage();
            if ("org.apache.pdfbox.filter.MissingImageReaderException".equals(className)
                    || (message != null && message.contains("Cannot read JBIG2 image"))
                    || (message != null && message.contains("jbig2-imageio is not installed"))) {
                JBIG2_READER_AVAILABLE = Boolean.FALSE;
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean isMalformedPdf(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && (
                    message.contains("End-of-File")
                            || message.contains("expected line at offset")
                            || message.contains("Missing root object")
                            || message.contains("Header doesn't contain versioninfo")
                            || message.contains("PDF header signature not found")
                            || message.contains("Trailer not found")
                            || message.contains("Cannot parse malformed PDF"))) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private void logPdfTimeout(Path path, Throwable t, long lastMod, long fileSizeBytes, long timeoutBudgetMs) {
        String signature = pdfLogSignature("timeout", path, lastMod, fileSizeBytes);
        if (!LOGGED_PDF_TIMEOUT_SIGNATURES.add(signature)) {
            return;
        }
        String cause = t == null ? "Timeout" : t.getClass().getSimpleName();
        LOG.warning(() -> "[Thumbs] PDF thumbnail exceeded the adaptive " + timeoutBudgetMs
                + " ms budget; falling back to the file-type icon and suppressing reattempts for "
                + PDF_TIMEOUT_COOLDOWN_MS + " ms: " + safePath(path) + " | cause=" + cause);
    }

    private void logPdfLargeDocumentFallback(Path path,
                                             long lastMod,
                                             long fileSizeBytes,
                                             PdfRenderHistory history,
                                             String reason) {
        String signature = pdfLogSignature("large-doc-" + reason, path, lastMod, fileSizeBytes);
        if (!LOGGED_PDF_LARGE_DOC_SIGNATURES.add(signature)) {
            return;
        }
        int timeoutStreak = history == null ? 0 : history.consecutiveTimeouts();
        double averageRenderMs = history == null ? 0.0d : history.averageRenderMs();
        LOG.warning(() -> "[Thumbs] Skipping PDF thumbnail and falling back to the file-type icon because the document is in large-document recovery mode: "
                + safePath(path)
                + " | reason=" + reason
                + " | sizeBytes=" + fileSizeBytes
                + " | timeoutStreak=" + timeoutStreak
                + " | avgRenderMs=" + Math.round(averageRenderMs));
    }

    private void logPdfOversize(Path path, long sizeBytes, long lastMod, long fileSizeBytes) {
        String signature = pdfLogSignature("oversize", path, lastMod, fileSizeBytes);
        if (!LOGGED_PDF_OVERSIZE_SIGNATURES.add(signature)) {
            return;
        }
        LOG.warning(() -> "[Thumbs] Skipping PDF thumbnail because the file is larger than the in-memory render ceiling (size="
                + sizeBytes + " bytes, limit=" + PDF_IN_MEMORY_MAX_BYTES + " bytes): " + safePath(path));
    }

    private void logPdfInterrupt(Path path, Throwable t, long lastMod, long fileSizeBytes) {
        String signature = pdfLogSignature("interrupt", path, lastMod, fileSizeBytes);
        if (!LOGGED_PDF_INTERRUPT_SIGNATURES.add(signature)) {
            return;
        }
        String cause = t == null ? "<unknown>" : t.getClass().getSimpleName();
        LOG.warning(() -> "[Thumbs] PDF thumbnail render was interrupted or its channel closed mid-render; falling back to the file-type icon and collapsing library noise: "
                + safePath(path) + " | cause=" + cause);
    }

    private void logMissingJpeg2000Reader(Path path, Throwable t) {
        if (!LOGGED_MISSING_JPEG2000_READER.compareAndSet(false, true)) {
            return;
        }
        String target = safePath(path);
        if (t == null) {
            LOG.warning(() -> "[Thumbs] Skipping PDF thumbnail for JPX/JPEG2000-backed PDF because no JPEG2000 ImageIO reader is available: " + target);
        } else {
            LOG.warning(() -> "[Thumbs] Skipping PDF thumbnail for JPX/JPEG2000-backed PDF because no JPEG2000 ImageIO reader is available: " + target + " | cause=" + t.getClass().getSimpleName());
        }
    }

    private void logMissingJbig2Reader(Path path, Throwable t) {
        if (!LOGGED_MISSING_JBIG2_READER.compareAndSet(false, true)) {
            return;
        }
        String target = safePath(path);
        if (t == null) {
            LOG.warning(() -> "[Thumbs] Skipping PDF thumbnail for JBIG2-backed PDF because no JBIG2 ImageIO reader is available: " + target);
        } else {
            LOG.warning(() -> "[Thumbs] Skipping PDF thumbnail for JBIG2-backed PDF because no JBIG2 ImageIO reader is available: " + target + " | cause=" + t.getClass().getSimpleName());
        }
    }

    private void logMalformedPdf(Path path, Throwable t) {
        long lastMod = safeLastModifiedMs(path);
        long fileSizeBytes = safeFileSizeBytes(path);
        String signature = pdfLogSignature("malformed", path, lastMod, fileSizeBytes);
        if (!LOGGED_PDF_FAILURE_SIGNATURES.add(signature)) {
            return;
        }
        String message = t == null ? "<unknown>" : String.valueOf(t.getMessage());
        LOG.warning(() -> "[Thumbs] Skipping malformed PDF thumbnail and falling back to the file-type icon: " + safePath(path) + " | cause=" + message);
    }

    private String safePath(Path path) {
        return path == null ? "<unknown>" : path.toAbsolutePath().normalize().toString();
    }

    private String pdfLogSignature(String category, Path path, long lastMod, long fileSizeBytes) {
        return category + "|" + safePath(path) + "|" + lastMod + "|" + fileSizeBytes;
    }

    private String documentLogSignature(String ext, Path path, long lastMod, long fileSizeBytes) {
        String normalizedExt = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        return normalizedExt + "|" + safePath(path) + "|" + lastMod + "|" + fileSizeBytes;
    }

    private String documentCooldownKey(String ext, Path path) {
        String normalizedExt = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        return normalizedExt + "|" + safePath(path);
    }

    private boolean isOfficeDocumentExtension(String ext) {
        return documentBackendFor(ext) == DocumentBackend.THUMBNAILS4J;
    }

    private DocumentBackend documentBackendFor(String ext) {
        if (ext == null || ext.isBlank()) {
            return DocumentBackend.NONE;
        }
        String normalized = ext.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pdf" -> isDocumentExtensionEnabled(normalized) ? DocumentBackend.PDFBOX : DocumentBackend.NONE;
            case "doc", "docx", "pptx", "xls", "xlsx" -> isDocumentExtensionEnabled(normalized) ? DocumentBackend.THUMBNAILS4J : DocumentBackend.NONE;
            default -> DocumentBackend.NONE;
        };
    }

    private boolean isDocumentInFailureCooldown(Path path, String ext, long lastMod, long fileSizeBytes) {
        String key = documentCooldownKey(ext, path);
        DocumentCooldownState state = DOCUMENT_FAILURE_COOLDOWN_UNTIL_MS.get(key);
        if (state == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (!state.matches(ext == null ? null : ext.toLowerCase(Locale.ROOT), lastMod, fileSizeBytes) || state.untilMs() <= now) {
            DOCUMENT_FAILURE_COOLDOWN_UNTIL_MS.remove(key, state);
            return false;
        }
        return true;
    }

    private void clearDocumentFailureCooldown(Path path, String ext, long lastMod, long fileSizeBytes) {
        String key = documentCooldownKey(ext, path);
        DocumentCooldownState state = DOCUMENT_FAILURE_COOLDOWN_UNTIL_MS.get(key);
        if (state != null && state.matches(ext == null ? null : ext.toLowerCase(Locale.ROOT), lastMod, fileSizeBytes)) {
            DOCUMENT_FAILURE_COOLDOWN_UNTIL_MS.remove(key, state);
        }
    }

    private void markDocumentFailureCooldown(Path path, String ext, long lastMod, long fileSizeBytes, Throwable cause) {
        if (!isOfficeDocumentExtension(ext)) {
            return;
        }
        String normalizedExt = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        String key = documentCooldownKey(normalizedExt, path);
        long untilMs = System.currentTimeMillis() + DOCUMENT_FAILURE_COOLDOWN_MS;
        DOCUMENT_FAILURE_COOLDOWN_UNTIL_MS.put(key, new DocumentCooldownState(normalizedExt, lastMod, fileSizeBytes, untilMs));
        String signature = documentLogSignature(normalizedExt, path, lastMod, fileSizeBytes);
        if (LOGGED_DOCUMENT_FAILURE_SIGNATURES.add(signature)) {
            String reason = cause == null ? "backend returned no thumbnail" : cause.getClass().getSimpleName();
            LOG.warning(() -> "[Thumbs] Backing off document thumbnail retries for ." + normalizedExt
                    + " via " + documentBackendFor(normalizedExt)
                    + " for " + safePath(path)
                    + " | cooldownMs=" + DOCUMENT_FAILURE_COOLDOWN_MS
                    + " | cause=" + reason);
        }
    }

    private int computeOfficeBackendRequestSizePx(int requestedSizePx, DocumentRenderTier documentTier) {
        int safeRequestedSizePx = Math.max(12, Math.min(512, requestedSizePx));
        if (!ENABLE_OFFICE_PROGRESSIVE_UPGRADE || documentTier != DocumentRenderTier.LOW) {
            return safeRequestedSizePx;
        }
        return Math.min(safeRequestedSizePx, Math.max(32, OFFICE_PROGRESSIVE_LOW_TIER_MAX_SIZE_PX));
    }

    private Thumbnailer newDocumentThumbnailer(String ext) {
        if (ext == null) {
            return null;
        }
        String normalized = ext.toLowerCase(Locale.ROOT);
        if (documentBackendFor(normalized) != DocumentBackend.THUMBNAILS4J) {
            return null;
        }
        return switch (normalized) {
            case "doc" -> new DOCThumbnailer();
            case "docx" -> new DOCXThumbnailer();
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
            return documentBackendFor(normalized) != DocumentBackend.NONE
                    ? ThumbnailProvider.THUMBNAILS4J_DOCUMENT
                    : ThumbnailProvider.DISABLED;
        }
        if (ImageSupport.isImageIoManagedExtension(normalized)) {
            ensureImageSupportInitializedForExtension(normalized);
            if (ImageSupport.hasImageReaderForExtension(normalized)) {
                return ThumbnailProvider.IMAGEIO;
            }
            logMissingImageReaderOnce(normalized);
            return ThumbnailProvider.UNSUPPORTED;
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

    private void logMissingImageReaderOnce(String normalizedExt) {
        if (normalizedExt == null || normalizedExt.isBlank()) {
            return;
        }
        String ext = normalizedExt.toLowerCase(Locale.ROOT);
        if (!LOGGED_MISSING_IMAGEIO_READERS.add(ext)) {
            return;
        }
        String guidance = switch (ext) {
            case "avif", "heif", "heic" ->
                    "NightMonkeys HEIF/AVIF support requires Java 22+ native access plus the platform native libraries on java.library.path.";
            case "webp" ->
                    "Make sure the TwelveMonkeys imageio-webp plugin is resolved on the runtime classpath.";
            default ->
                    "Make sure the matching ImageIO plugin is resolved on the runtime classpath.";
        };
        LOG.warning(() -> "[Thumbs] No ImageIO reader is registered for ." + ext + " thumbnails. " + guidance);
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
                Long.toString(PDF_LARGE_DOC_SOFT_BYTES),
                Long.toString(PDF_LARGE_DOC_HARD_FALLBACK_BYTES),
                Integer.toString(PDF_LARGE_DOC_PAGE_COUNT_THRESHOLD),
                Long.toString(PDF_ADAPTIVE_TIMEOUT_MIN_MS),
                Long.toString(PDF_ADAPTIVE_TIMEOUT_MAX_MS),
                Integer.toString(PDF_RECOVERY_TIMEOUT_STREAK_THRESHOLD),
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
            props.setProperty("tmpMaxAgeHours", Long.toString(DISK_CACHE_TMP_MAX_AGE_HOURS));
            props.setProperty("touchOnRead", Boolean.toString(TOUCH_DISK_CACHE_ON_READ));
            props.setProperty("touchMinIntervalMinutes", Long.toString(DISK_CACHE_TOUCH_MIN_INTERVAL_MINUTES));
            props.setProperty("deleteCorruptOnRead", Boolean.toString(DELETE_CORRUPT_ON_READ));
            props.setProperty("enablePdf", Boolean.toString(ENABLE_PDF));
            props.setProperty("enableWord", Boolean.toString(ENABLE_WORD));
            props.setProperty("enableExcel", Boolean.toString(ENABLE_EXCEL));
            props.setProperty("enablePptx", Boolean.toString(ENABLE_PPTX));
            props.setProperty("documentTimeoutMs", Long.toString(DOCUMENT_TIMEOUT_MS));
            props.setProperty("pdfInMemoryMaxBytes", Long.toString(PDF_IN_MEMORY_MAX_BYTES));
            props.setProperty("pdfTimeoutCooldownMs", Long.toString(PDF_TIMEOUT_COOLDOWN_MS));
            props.setProperty("pdfLargeDocSoftBytes", Long.toString(PDF_LARGE_DOC_SOFT_BYTES));
            props.setProperty("pdfLargeDocHardFallbackBytes", Long.toString(PDF_LARGE_DOC_HARD_FALLBACK_BYTES));
            props.setProperty("pdfLargeDocPageCountThreshold", Integer.toString(PDF_LARGE_DOC_PAGE_COUNT_THRESHOLD));
            props.setProperty("pdfAdaptiveTimeoutMinMs", Long.toString(PDF_ADAPTIVE_TIMEOUT_MIN_MS));
            props.setProperty("pdfAdaptiveTimeoutMaxMs", Long.toString(PDF_ADAPTIVE_TIMEOUT_MAX_MS));
            props.setProperty("pdfRecoveryTimeoutStreakThreshold", Integer.toString(PDF_RECOVERY_TIMEOUT_STREAK_THRESHOLD));
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

    private void maybeTouchDiskCacheFileOnRead(Path cacheFile) {
        if (!TOUCH_DISK_CACHE_ON_READ || cacheFile == null || isDiskCacheManifestFile(cacheFile) || isDiskCacheTempFile(cacheFile)) {
            return;
        }
        long now = System.currentTimeMillis();
        long minIntervalMs = Math.max(60_000L, DISK_CACHE_TOUCH_MIN_INTERVAL_MINUTES * 60_000L);
        try {
            long prior = Files.getLastModifiedTime(cacheFile).toMillis();
            if (now - prior < minIntervalMs) {
                diskCacheTouchSkip.increment();
                return;
            }
            Files.setLastModifiedTime(cacheFile, java.nio.file.attribute.FileTime.fromMillis(now));
            diskCacheTouchWrite.increment();
        } catch (Throwable ignored) {
            diskCacheTouchFail.increment();
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
            maybeTouchDiskCacheFileOnRead(cacheFile);
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
            long tmpMaxAgeMs = Math.max(1L, DISK_CACHE_TMP_MAX_AGE_HOURS) * 60L * 60L * 1000L;

            java.util.List<Path> payloadFiles = new ArrayList<>();
            java.util.List<Path> tempFiles = new ArrayList<>();
            try (java.util.stream.Stream<Path> stream = Files.walk(diskCacheDir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    if (isDiskCacheManifestFile(p)) {
                        return;
                    }
                    if (isDiskCacheTempFile(p)) {
                        tempFiles.add(p);
                        return;
                    }
                    if (isDiskCachePayloadFile(p)) {
                        payloadFiles.add(p);
                    }
                });
            }

            for (Path f : tempFiles) {
                try {
                    long ageMs = now - Files.getLastModifiedTime(f).toMillis();
                    if (ageMs > tmpMaxAgeMs && Files.deleteIfExists(f)) {
                        diskCacheTempPruned.increment();
                    }
                } catch (Throwable ignored) {
                }
            }

            long totalBytes = 0L;
            java.util.List<Path> retainedPayloadFiles = new ArrayList<>(payloadFiles.size());
            for (Path f : payloadFiles) {
                try {
                    long ageMs = now - Files.getLastModifiedTime(f).toMillis();
                    if (ageMs > maxAgeMs) {
                        if (Files.deleteIfExists(f)) {
                            diskCachePruned.increment();
                        }
                        continue;
                    }
                    totalBytes += Files.size(f);
                    retainedPayloadFiles.add(f);
                } catch (Throwable ignored) {
                }
            }
            if (totalBytes > DISK_CACHE_MAX_BYTES) {
                retainedPayloadFiles.sort(Comparator.comparingLong(this::safeFileMtimeForSort));
                for (Path f : retainedPayloadFiles) {
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
            }
            pruneEmptyDiskCacheDirectories();
        } catch (Throwable ignored) {
        }
    }

    private void pruneEmptyDiskCacheDirectories() {
        Path root = diskCacheDir;
        if (root == null) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            java.util.List<Path> dirs = stream.filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path dir : dirs) {
                if (root.equals(dir)) {
                    continue;
                }
                try (java.util.stream.Stream<Path> children = Files.list(dir)) {
                    if (!children.findAny().isPresent() && Files.deleteIfExists(dir)) {
                        diskCacheEmptyDirsPruned.increment();
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

    private boolean isDiskCacheManifestFile(Path path) {
        Path manifest = diskCacheManifestFile();
        if (manifest == null || path == null) {
            return false;
        }
        try {
            return manifest.toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize());
        } catch (Throwable ignored) {
            return manifest.equals(path);
        }
    }

    private boolean isDiskCacheTempFile(Path path) {
        if (path == null) {
            return false;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".tmp");
    }

    private boolean isDiskCachePayloadFile(Path path) {
        if (path == null) {
            return false;
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") && !isDiskCacheManifestFile(path);
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
        sb.append(" touchOnRead=").append(onOff(TOUCH_DISK_CACHE_ON_READ));
        sb.append(" touchMinIntervalMinutes=").append(DISK_CACHE_TOUCH_MIN_INTERVAL_MINUTES);
        sb.append(" manifestEnabled=").append(onOff(ENABLE_DISK_CACHE_MANIFEST));
        sb.append(" compatVersion=").append(DISK_CACHE_COMPAT_VERSION);
        sb.append(" fingerprint=").append(currentDiskCacheCompatibilityFingerprint());
        Path manifest = diskCacheManifestFile();
        sb.append(" manifest=").append(manifest == null ? "<none>" : manifest.toAbsolutePath());
        sb.append(" manifestExists=").append(manifest != null && Files.exists(manifest));
        sb.append(" maxBytes=").append(DISK_CACHE_MAX_BYTES);
        sb.append(" maxAgeDays=").append(DISK_CACHE_MAX_AGE_DAYS);
        sb.append(" pdfLargeDocSoftBytes=").append(PDF_LARGE_DOC_SOFT_BYTES);
        sb.append(" pdfLargeDocHardFallbackBytes=").append(PDF_LARGE_DOC_HARD_FALLBACK_BYTES);
        sb.append(" pdfLargeDocPageCountThreshold=").append(PDF_LARGE_DOC_PAGE_COUNT_THRESHOLD);
        sb.append(" pdfAdaptiveTimeoutMinMs=").append(PDF_ADAPTIVE_TIMEOUT_MIN_MS);
        sb.append(" pdfAdaptiveTimeoutMaxMs=").append(PDF_ADAPTIVE_TIMEOUT_MAX_MS);
        if (dir != null && Files.isDirectory(dir)) {
            long payloadFileCount = 0L;
            long tempFileCount = 0L;
            long totalBytes = 0L;
            long newestMs = -1L;
            try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
                java.util.Iterator<Path> it = stream.filter(Files::isRegularFile).iterator();
                while (it.hasNext()) {
                    Path p = it.next();
                    if (isDiskCacheManifestFile(p)) {
                        continue;
                    }
                    if (isDiskCacheTempFile(p)) {
                        tempFileCount++;
                        try {
                            newestMs = Math.max(newestMs, Files.getLastModifiedTime(p).toMillis());
                        } catch (Throwable ignored) {
                        }
                        continue;
                    }
                    if (!isDiskCachePayloadFile(p)) {
                        continue;
                    }
                    payloadFileCount++;
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
            sb.append(" files=").append(payloadFileCount);
            sb.append(" tempFiles=").append(tempFileCount);
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
        officeDocumentExecutor.shutdownNow();
        pdfDocumentExecutor.shutdownNow();
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
        final long viewportScope;
        final ThumbKey key;
        final CompletableFuture<Image> future;
        final Runnable delegate;

        PrioritizedRunnable(RequestPriority pr, long seqNo, long viewportScope, ThumbKey key, CompletableFuture<Image> future, Runnable delegate) {
            this.priority = (pr == null) ? RequestPriority.BACKGROUND.p : pr.p;
            this.seqNo = seqNo;
            this.viewportScope = viewportScope;
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

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
