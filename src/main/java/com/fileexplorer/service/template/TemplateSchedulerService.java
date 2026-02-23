package com.fileexplorer.service.template;

import com.fileexplorer.service.ops.FileOperationRequest;
import com.fileexplorer.service.ops.OperationOriginAudit;
import com.fileexplorer.service.ops.OperationHandle;
import com.fileexplorer.service.ops.OperationQueueService;
import com.fileexplorer.service.ops.conflict.ConflictPolicyAction;
import com.fileexplorer.service.ops.conflict.ConflictPolicyConfig;
import com.fileexplorer.service.ops.conflict.ConflictPolicyProfile;
import com.fileexplorer.service.ops.preview.OperationPlanSnapshot;
import com.fileexplorer.service.ops.preview.OperationPreviewService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Phase 5.2.1+: Background scheduler for templates (one-shot + recurring) with run history.
 *
 * <p><strong>Phase 5.4.0 correctness</strong></p>
 * <ul>
 *   <li>Recurring schedules are evaluated on a periodic "tick" against persisted next-due timestamps
 *       (epoch-millis) rather than relying on {@code scheduleAtFixedRate}.</li>
 *   <li>Next-run computation is deterministic and DST-safe (Instant/epoch time).</li>
 *   <li>After downtime, missed intervals cause <em>one</em> execution to run immediately and the next-due time
 *       is advanced to the first interval strictly after "now" (no catch-up storms).</li>
 * </ul>
 *
 * <p><strong>Phase 5.4.1 concurrency + locking</strong></p>
 * <ul>
 *   <li>Due-evaluation (tick) is never blocked by template execution; runs are submitted onto a separate executor.</li>
 *   <li>Single-flight per {@code templateId}: at most one in-flight execution per template at a time, across
 *       due-runs and manual run-now triggers.</li>
 *   <li>In-flight tracking supports safe shutdown: outstanding executions are cancelled best-effort on close.</li>
 * </ul>
 *
 * <p>Designed to be best-effort: failures are logged, not thrown.</p>
 */
public final class TemplateSchedulerService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(TemplateSchedulerService.class.getName());

    private final SchedulerSettingsService settingsService = new SchedulerSettingsService();
    private volatile SchedulerSettings settings = SchedulerSettings.defaults();

    /** If system clock rewinds by more than this, recompute next-due timestamps. */
    private static final long CLOCK_REWIND_TOLERANCE_MILLIS = 5 * 60_000L;

    private final OperationTemplateService templateService;
    private final OperationQueueService queue;
    private final TemplateRecurringScheduleService recurringScheduleService;
    private final TemplateRunHistoryService historyService;

    /**
     * Single-thread scheduler for tick + state mutation.
     *
     * <p>All schedule-state mutations (nextDue/lastRun/enable) are serialized through this executor.</p>
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fileexplorer-template-scheduler");
        t.setDaemon(true);
        return t;
    });

    /**
     * Executor for running template executions so that the scheduler tick is never blocked.
     *
     * <p>Phase 5.4.1 uses a bounded pool to keep resource usage predictable.</p>
     */
    private volatile ExecutorService runExecutor;

    private final List<ScheduledFuture<?>> oneShots = new ArrayList<>();

    /** In-memory schedule states keyed by templateId. */
    private final ConcurrentHashMap<String, ScheduleState> schedules = new ConcurrentHashMap<>();

    /** In-flight executions (single-flight per template). */
    private final ConcurrentHashMap<String, InFlightExecution> inFlight = new ConcurrentHashMap<>();

    /** Convenience view for quick checks when only the key-set matters. */
    private final Set<String> inFlightIds = inFlight.keySet();

    private volatile long lastTickNowMillis = 0L;
    private volatile ScheduledFuture<?> tickFuture;

    public TemplateSchedulerService(
            OperationTemplateService templateService,
            OperationQueueService queue,
            TemplateRecurringScheduleService recurringScheduleService,
            TemplateRunHistoryService historyService,
            boolean safeMode
    ) {
        this.templateService = Objects.requireNonNull(templateService, "templateService");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.recurringScheduleService = Objects.requireNonNull(recurringScheduleService, "recurringScheduleService");
        this.historyService = Objects.requireNonNull(historyService, "historyService");

        // Load settings best-effort.
        try {
            this.settings = settingsService.load();
        } catch (Exception ignored) {
        }
        this.runExecutor = newRunExecutor(settings.maxParallel());
        try {
            this.historyService.setMaxEntries(settings.historyRetentionEntries());
        } catch (Exception ignored) {
        }

        // Always restore persisted schedules so the UI can render schedule state.
        restoreRecurringSchedulesBestEffort();

        // Safe mode disables background due-execution.
        if (!safeMode) {
            startTick();
        } else {
            LOG.info("Safe mode: scheduler tick disabled (no background due execution).");
        }
    }

    /**
     * Run a template immediately.
     *
     * <p>If the template has a recurring schedule, this "run now" also pushes the next-due time forward
     * to avoid an immediate double-run on the next tick.</p>
     */
    public void runNow(String templateId) {
        Objects.requireNonNull(templateId, "templateId");
        scheduler.execute(() -> {
            bumpNextDueAfterManualRun(templateId);
            submitExecutionIfNotRunning(templateId, Trigger.MANUAL);
        });
    }

    /**
     * Schedule a one-shot execution after a delay in minutes.
     */
    public void scheduleOnceInMinutes(String templateId, long minutes) {
        Objects.requireNonNull(templateId, "templateId");
        long delay = Math.max(0, minutes);
        ScheduledFuture<?> f = scheduler.schedule(() -> submitExecutionIfNotRunning(templateId, Trigger.ONESHOT), delay, TimeUnit.MINUTES);
        oneShots.add(f);
    }

    /**
     * Schedule recurring execution at a fixed period (minutes).
     *
     * <p>Persists schedule so it is restored on next startup.</p>
     */
    public void scheduleRecurringEveryMinutes(String templateId, long minutes) {
        Objects.requireNonNull(templateId, "templateId");
        long periodMinutes = Math.max(1, minutes);
        long periodMillis = periodMinutes * 60_000L;

        long now = System.currentTimeMillis();
        ScheduleState prev = schedules.get(templateId);
        long lastRun = prev == null ? 0L : prev.lastRunMillis;
        long nextDue = now + periodMillis;

        ScheduleState st = new ScheduleState(periodMinutes, periodMillis, lastRun, nextDue, true, 0, 0L, null);
        schedules.put(templateId, st);

        // Persist with next-due (Phase 5.4.0)
        recurringScheduleService.setSchedule(templateId, periodMinutes, lastRun, nextDue);

        historyService.log(TemplateRunHistoryEntry.now(templateId, null, "SCHEDULED", "Recurring every " + periodMinutes + " min", null));
    }

    /**
     * Cancel recurring schedule for a template.
     */
    public void cancelRecurring(String templateId) {
        Objects.requireNonNull(templateId, "templateId");

        schedules.remove(templateId);
        recurringScheduleService.removeRecurring(templateId);

        historyService.log(TemplateRunHistoryEntry.now(templateId, null, "SCHEDULED", "Recurring cancelled", null));
    }

    /**
     * Minutes period for a template's recurring schedule (if any).
     */
    public OptionalLongValue recurringMinutes(String templateId) {
        Objects.requireNonNull(templateId, "templateId");
        ScheduleState st = schedules.get(templateId);
        if (st != null && st.enabled && st.minutes > 0) {
            return OptionalLongValue.of(java.util.OptionalLong.of(st.minutes));
        }
        return OptionalLongValue.of(recurringScheduleService.getRecurringMinutes(templateId));
    }

    /**
     * Phase 5.4.0: Next due timestamp (epoch millis) for a template's recurring schedule (if known).
     */
    public OptionalLongValue nextDueMillis(String templateId) {
        Objects.requireNonNull(templateId, "templateId");
        ScheduleState st = schedules.get(templateId);
        if (st == null || !st.enabled) return OptionalLongValue.of(java.util.OptionalLong.empty());
        long v = st.nextDueMillis;
        return OptionalLongValue.of(v > 0 ? java.util.OptionalLong.of(v) : java.util.OptionalLong.empty());
    }

    /**
     * Returns true if the template has an in-flight execution.
     */
    public boolean isInFlight(String templateId) {
        Objects.requireNonNull(templateId, "templateId");
        return inFlightIds.contains(templateId);
    }

    /**
     * Restore persisted recurring schedules (best effort).
     */
    private void restoreRecurringSchedulesBestEffort() {
        try {
            long now = System.currentTimeMillis();
            var m = recurringScheduleService.listSchedules();
            for (var e : m.entrySet()) {
                String templateId = e.getKey();
                var s = e.getValue();
                long minutes = s.minutes();
                if (minutes <= 0) continue;

                long periodMillis = minutes * 60_000L;

                long lastRun = Math.max(0L, s.lastRunEpochMillis());
                long nextDue = Math.max(0L, s.nextDueEpochMillis());
                int retryCount = Math.max(0, s.retryCount());
                long backoffUntil = Math.max(0L, s.backoffUntilEpochMillis());
                String lastFailureCategory = s.lastFailureCategory();

                if (nextDue <= 0L) {
                    long base = lastRun > 0 ? lastRun : now;
                    nextDue = base + periodMillis;
                }

                // If we are already past due, we will run once immediately (on tick) and advance nextDue.
                ScheduleState st = new ScheduleState(minutes, periodMillis, lastRun, nextDue, true, retryCount, backoffUntil, lastFailureCategory);
                schedules.put(templateId, st);
            }
        } catch (Throwable ex) {
            LOG.log(Level.WARNING, "Failed to restore recurring template schedules", ex);
        }
    }

    private void startTick() {
        tickFuture = scheduler.scheduleWithFixedDelay(this::tick, settings.tickSeconds(), settings.tickSeconds(), TimeUnit.SECONDS);
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();

            // Detect significant clock rewind: recompute next-due to avoid getting "stuck" far in the future.
            long prevNow = lastTickNowMillis;
            if (prevNow > 0 && now + CLOCK_REWIND_TOLERANCE_MILLIS < prevNow) {
                for (var e : schedules.entrySet()) {
                    ScheduleState st = e.getValue();
                    if (st == null || !st.enabled || st.periodMillis <= 0) continue;
                    long base = st.lastRunMillis > 0 ? st.lastRunMillis : now;
                    st.nextDueMillis = base + st.periodMillis;
                    persistScheduleState(e.getKey(), st);
                    historyService.log(TemplateRunHistoryEntry.now(e.getKey(), null, "SCHEDULED",
                            "Clock change detected; recomputed next due", null));
                }
            }
            lastTickNowMillis = now;

            for (var entry : schedules.entrySet()) {
                String templateId = entry.getKey();
                ScheduleState st = entry.getValue();
                if (st == null || !st.enabled || st.periodMillis <= 0) continue;

                long nextDue = st.nextDueMillis;
                if (nextDue <= 0) {
                    long base = st.lastRunMillis > 0 ? st.lastRunMillis : now;
                    nextDue = base + st.periodMillis;
                    st.nextDueMillis = nextDue;
                    persistScheduleState(templateId, st);
                }

                if (now < nextDue) continue;

                // Phase 5.4.1: advance nextDue first to avoid storms, then attempt single-flight execution.
                long advanced = advanceNextDue(nextDue, st.periodMillis, now);
                st.nextDueMillis = advanced;
                persistScheduleState(templateId, st);

                historyService.log(TemplateRunHistoryEntry.now(templateId, null, "DUE", "Recurring schedule due", null));

                submitExecutionIfNotRunning(templateId, Trigger.DUE);
            }

        } catch (Throwable ex) {
            LOG.log(Level.WARNING, "Scheduler tick failed", ex);
        }
    }

    private void bumpNextDueAfterManualRun(String templateId) {
        ScheduleState st = schedules.get(templateId);
        if (st == null || !st.enabled || st.periodMillis <= 0) return;
        long now = System.currentTimeMillis();
        st.nextDueMillis = now + st.periodMillis;
        st.retryCount = 0;
        st.backoffUntilMillis = 0L;
        st.lastFailureCategory = null;
        persistScheduleState(templateId, st);
    }

    private void persistScheduleState(String templateId, ScheduleState st) {
        try {
            recurringScheduleService.setSchedule(templateId, st.minutes, st.lastRunMillis, st.nextDueMillis, st.retryCount, st.backoffUntilMillis, st.lastFailureCategory);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Phase 5.4.1: Submit a template execution to the run executor if it's not already running.
     */
    private void submitExecutionIfNotRunning(String templateId, Trigger trigger) {
        // Single-flight per templateId.
        InFlightExecution marker = new InFlightExecution(System.currentTimeMillis(), null);
        InFlightExecution existing = inFlight.putIfAbsent(templateId, marker);
        if (existing != null) {
            historyService.log(TemplateRunHistoryEntry.now(templateId, null, "SKIP", "Already running", null));
            return;
        }

        Future<?> f = runExecutor.submit(() -> {
            ExecutionOutcome outcome = ExecutionOutcome.retryableFailure(FailureCategory.UNKNOWN, "Unknown failure");
            try {
                outcome = executeTemplate(templateId, trigger);
            } finally {
                // release single-flight marker regardless of outcome
                inFlight.remove(templateId);
            }

            // Serialize state updates (retry counters/nextDue) onto the scheduler thread.
            final ExecutionOutcome finalOutcome = outcome;
            scheduler.execute(() -> afterExecution(templateId, trigger, finalOutcome));
        });

        // Best-effort: record the future for shutdown cancellation.
        marker.future = f;
    }

    /**
     * Compute the next due time, advancing by full periods until it is strictly in the future.
     */
    private static long advanceNextDue(long currentNextDue, long periodMillis, long nowMillis) {
        if (periodMillis <= 0) return nowMillis + 60_000L;
        long next = currentNextDue;
        if (next > nowMillis) return next;
        long delta = nowMillis - next;
        long steps = (delta / periodMillis) + 1;
        return next + (steps * periodMillis);
    }

    private void afterExecution(String templateId, Trigger trigger, ExecutionOutcome outcome) {
        if (outcome == null) return;
        ScheduleState st = schedules.get(templateId);
        if (st == null || !st.enabled || st.periodMillis <= 0) return;

        // Reset retry state on success.
        if (outcome.success) {
            if (st.retryCount != 0 || st.backoffUntilMillis != 0L || st.lastFailureCategory != null) {
                st.retryCount = 0;
                st.backoffUntilMillis = 0L;
                st.lastFailureCategory = null;
                persistScheduleState(templateId, st);
            }
            return;
        }

        // Failure policy applies only to scheduled triggers (not manual).
        if (trigger == Trigger.MANUAL) return;
        if (!outcome.retryable) return;

        long now = System.currentTimeMillis();
        int attempt = st.retryCount + 1;

        if (attempt > settings.maxRetryAttempts()) {
            // Give up for this interval; reset retry counters and continue normal scheduling cadence.
            st.retryCount = 0;
            st.backoffUntilMillis = 0L;
            st.lastFailureCategory = outcome.category == null ? null : outcome.category.name();
            st.nextDueMillis = now + st.periodMillis;
            persistScheduleState(templateId, st);

            historyService.log(TemplateRunHistoryEntry.now(templateId, null, "RETRY_GIVEUP",
                    "Max retries exceeded; next run scheduled normally", null));
            return;
        }

        long backoffMillis = computeBackoffMillis(attempt);
        long next = now + backoffMillis;

        st.retryCount = attempt;
        st.backoffUntilMillis = next;
        st.lastFailureCategory = outcome.category == null ? null : outcome.category.name();
        st.nextDueMillis = next;

        persistScheduleState(templateId, st);

        historyService.log(TemplateRunHistoryEntry.now(templateId, null, "RETRY",
                "Retry " + attempt + "/" + settings.maxRetryAttempts() + " in " + (backoffMillis / 1000) + "s (" +
                        (st.lastFailureCategory == null ? "UNKNOWN" : st.lastFailureCategory) + ")", null));
    }

    private long computeBackoffMillis(int attempt) {
        int n = Math.max(1, attempt);
        long v = settings.retryBaseMillis();
        // exponential backoff: base * 2^(attempt-1)
        long mul = 1L << Math.min(20, n - 1);
        long out;
        try {
            out = Math.multiplyExact(v, mul);
        } catch (ArithmeticException ex) {
            out = settings.retryMaxMillis();
        }
        return Math.min(settings.retryMaxMillis(), Math.max(settings.retryBaseMillis(), out));
    }


/**
 * Phase 5.4.3: Resolve a per-template conflict policy override.
 *
 * <p>If the template declares {@code conflictProfileId}, this attempts to interpret it as a
 * {@link ConflictPolicyProfile} enum name. If parsing fails, no override is applied.</p>
 *
 * <p>Scheduled executions should not rely on interactive prompting; however, this codebase routes
 * {@link ConflictPolicyAction#PROMPT} into the Conflict Queue UI, so PROMPT is safe for background runs.</p>
 */
private static ConflictPolicyConfig resolveConflictPolicyOverride(OperationTemplate t) {
    if (t == null) return null;
    String raw = t.conflictProfileId();
    if (raw == null || raw.isBlank()) return null;
    try {
        ConflictPolicyProfile profile = ConflictPolicyProfile.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        return new ConflictPolicyConfig(profile, ConflictPolicyAction.PROMPT);
    } catch (Exception ignored) {
        return null;
    }
}

    private static String safeMsg(Throwable t) {
        if (t == null) return "";
        String m = t.getMessage();
        return m == null ? "" : m;
    }

    private enum FailureCategory {
        CONFIG(false),
        IO(true),
        TRANSIENT(true),
        UNKNOWN(true);

        final boolean retryable;

        FailureCategory(boolean retryable) {
            this.retryable = retryable;
        }

        boolean isRetryable() {
            return retryable;
        }

        static FailureCategory fromThrowable(Throwable t) {
            if (t == null) return UNKNOWN;
            // Configuration / programmer errors should not be retried.
            if (t instanceof IllegalArgumentException || t instanceof IllegalStateException) return CONFIG;
            // IO-ish errors are often transient.
            if (t instanceof java.io.IOException) return IO;
            // Heuristic: unwrap common wrappers.
            Throwable c = t.getCause();
            if (c != null && c != t) return fromThrowable(c);
            return UNKNOWN;
        }
    }

    private static final class ExecutionOutcome {
        final boolean success;
        final boolean retryable;
        final FailureCategory category;
        final String message;

        private ExecutionOutcome(boolean success, boolean retryable, FailureCategory category, String message) {
            this.success = success;
            this.retryable = retryable;
            this.category = category;
            this.message = message;
        }

        static ExecutionOutcome success() {
            return new ExecutionOutcome(true, false, null, null);
        }

        static ExecutionOutcome retryableFailure(FailureCategory category, String message) {
            return new ExecutionOutcome(false, true, category == null ? FailureCategory.UNKNOWN : category, message);
        }

        static ExecutionOutcome permanentFailure(FailureCategory category, String message) {
            return new ExecutionOutcome(false, false, category == null ? FailureCategory.UNKNOWN : category, message);
        }
    }

    private enum Trigger { MANUAL, ONESHOT, DUE }

    private static final class ScheduleState {
        final long minutes;
        final long periodMillis;
        volatile long lastRunMillis;
        volatile long nextDueMillis;
        volatile boolean enabled;

        // Phase 5.4.2: failure policy state (persisted for recurring schedules)
        volatile int retryCount;
        volatile long backoffUntilMillis;
        volatile String lastFailureCategory;

        ScheduleState(
                long minutes,
                long periodMillis,
                long lastRunMillis,
                long nextDueMillis,
                boolean enabled,
                int retryCount,
                long backoffUntilMillis,
                String lastFailureCategory
        ) {
            this.minutes = minutes;
            this.periodMillis = periodMillis;
            this.lastRunMillis = lastRunMillis;
            this.nextDueMillis = nextDueMillis;
            this.enabled = enabled;
            this.retryCount = Math.max(0, retryCount);
            this.backoffUntilMillis = Math.max(0L, backoffUntilMillis);
            this.lastFailureCategory = lastFailureCategory;
        }
    }

    private static final class InFlightExecution {
        final long startedAtMillis;
        volatile Future<?> future;

        InFlightExecution(long startedAtMillis, Future<?> future) {
            this.startedAtMillis = startedAtMillis;
            this.future = future;
        }
    }

    private ExecutionOutcome executeTemplate(String templateId, Trigger trigger) {
        long startTs = System.currentTimeMillis();

        // Update schedule lastRun for recurring schedules (best effort) before enqueueing.
        ScheduleState st = schedules.get(templateId);
        if (st != null && st.enabled) {
            st.lastRunMillis = startTs;
            persistScheduleState(templateId, st);
        }

        try {
            Optional<OperationTemplate> opt = templateService.read(templateId);
            if (opt.isEmpty()) {
                historyService.log(TemplateRunHistoryEntry.now(templateId, null, "SKIP", "Template not found", null));
                return ExecutionOutcome.permanentFailure(FailureCategory.CONFIG, "Template not found");
            }
            OperationTemplate t = opt.get();

            String trig = trigger == null ? "RUN" : trigger.name();
            historyService.log(TemplateRunHistoryEntry.now(templateId, t.name(), "START", trig, null));

            // Minimal: build request with sensible defaults.
            List<Path> src = new ArrayList<>();
            for (String s : t.sources()) {
                try {
                    src.add(Path.of(s));
                } catch (Exception ignored) {
                }
            }

            Path target = null;
            try {
                if (t.target() != null && !t.target().isBlank()) {
                    target = Path.of(t.target());
                }
            } catch (Exception ignored) {
            }

            if (src.isEmpty()) {
                historyService.log(TemplateRunHistoryEntry.now(templateId, t.name(), "SKIP", "No valid source paths", null));
                return ExecutionOutcome.permanentFailure(FailureCategory.CONFIG, "No valid source paths");
            }

            
// FileOperationRequest signature is stable in this codebase; use conservative defaults.
FileOperationRequest req = new FileOperationRequest(
        t.type(),
        src,
        target,
        "Template: " + t.name(),
        true,
        false,
        false
);

// Phase 5.4.3: Apply template-level conflict policy profile (if set) and attach a dry-run preview snapshot.
ConflictPolicyConfig policyOverride = resolveConflictPolicyOverride(t);

OperationPlanSnapshot snapshot = null;
try {
    // Use the deterministic preview engine to surface conflicts/warnings and enable deterministic execution.
    OperationPreviewService preview = new OperationPreviewService();
    snapshot = preview.previewPlan(req, policyOverride);
} catch (Throwable ex) {
    // Preview is best-effort; we can still enqueue without a snapshot.
    LOG.log(Level.FINE, "Template preview failed for " + templateId + ": " + ex.getMessage(), ex);
}

if (snapshot != null) {
    int conflicts = snapshot.counts() == null ? 0 : snapshot.counts().conflicts();
    int warnings = snapshot.warnings() == null ? 0 : snapshot.warnings().size();
    if (conflicts > 0) {
        historyService.log(TemplateRunHistoryEntry.now(templateId, t.name(), "CONFLICTS",
                "Preview found " + conflicts + " conflict(s); policy=" + snapshot.policy(), null));
    }
    if (warnings > 0) {
        historyService.log(TemplateRunHistoryEntry.now(templateId, t.name(), "WARN",
                "Preview produced " + warnings + " warning(s)", null));
    }
}

// Apply batch transaction preference if requested.
boolean prevBatch = queue.isBatchTransactionMode();
if (t.batchTransaction()) queue.setBatchTransactionMode(true);

// Phase 5.5.1: Attach origin/audit metadata for operation history.
long recurrenceMinutes = st == null ? 0L : st.minutes;
int retryAttempt = st == null ? 0 : Math.max(0, st.retryCount);
OperationOriginAudit originAudit = OperationOriginAudit.of(
        "TEMPLATE_SCHEDULER",
        templateId,
        templateId,
        trigger == null ? "RUN" : trigger.name(),
        recurrenceMinutes,
        (trigger == Trigger.MANUAL) ? 0 : retryAttempt
);

// Enqueue with optional per-operation conflict policy override + preview snapshot + drift policy override + origin metadata.
OperationHandle handle = queue.enqueue(
        req,
        "Template: " + t.name(),
        "template:" + templateId,
        policyOverride,
        snapshot,
        t.driftPolicy(),
        originAudit
);

historyService.log(TemplateRunHistoryEntry.now(templateId, t.name(), "ENQUEUED",
        "Enqueued operation" + (snapshot == null ? "" : " (preview attached)"),
        handle == null ? null : handle.id()));

// If we are in batch mode, caller must commit; templates default to immediate commit.
if (t.batchTransaction()) {
    queue.commitCurrentGroup();
}

if (t.batchTransaction()) queue.setBatchTransactionMode(prevBatch);

            return ExecutionOutcome.success();

        } catch (Throwable ex) {
            LOG.log(Level.WARNING, "Template execution failed: " + templateId, ex);
            try {
                historyService.log(TemplateRunHistoryEntry.now(templateId, null, "FAIL", ex.getClass().getSimpleName() + ": " + ex.getMessage(), null));
            } catch (Throwable ignored) {
            }

            FailureCategory cat = FailureCategory.fromThrowable(ex);
            boolean retryable = cat.isRetryable();
            return retryable
                    ? ExecutionOutcome.retryableFailure(cat, ex.getClass().getSimpleName() + ": " + safeMsg(ex))
                    : ExecutionOutcome.permanentFailure(cat, ex.getClass().getSimpleName() + ": " + safeMsg(ex));
        }
    }

    

    /**
     * Get the current scheduler settings snapshot.
     */
    public SchedulerSettings getSettings() {
        return settings;
    }

    /**
     * Apply new scheduler settings.
     *
     * <p>This call is safe from any thread; changes are serialized onto the scheduler thread.</p>
     */
    public void applySettings(SchedulerSettings newSettings) {
        Objects.requireNonNull(newSettings, "newSettings");
        scheduler.execute(() -> {
            SchedulerSettings prev = this.settings;
            this.settings = newSettings;
            try {
                settingsService.save(newSettings);
            } catch (Exception ignored) {
            }

            try {
                historyService.setMaxEntries(newSettings.historyRetentionEntries());
            } catch (Exception ignored) {
            }

            if (prev.tickSeconds() != newSettings.tickSeconds()) {
                if (tickFuture != null) tickFuture.cancel(false);
                startTick();
            }

            if (prev.maxParallel() != newSettings.maxParallel()) {
                ExecutorService old = runExecutor;
                runExecutor = newRunExecutor(newSettings.maxParallel());
                if (old != null) old.shutdownNow();
            }
        });
    }


    /**
     * Phase 5.6.1: Force a best-effort trim of the scheduler history log now.
     *
     * @return true if a trim was attempted, false otherwise
     */
    public boolean maintenanceTrimHistoryNow() {
        try {
            return historyService.trimNow();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Phase 5.6.1: Validate and repair the recurring schedule store, then reload in-memory schedules.
     *
     * <p>This call is serialized onto the scheduler thread.</p>
     *
     * @return validation report
     */
    public TemplateRecurringScheduleService.ValidationReport maintenanceValidateAndRepairSchedules() {
        final java.util.concurrent.CompletableFuture<TemplateRecurringScheduleService.ValidationReport> f = new java.util.concurrent.CompletableFuture<>();
        scheduler.execute(() -> {
            TemplateRecurringScheduleService.ValidationReport r;
            try {
                r = recurringScheduleService.validateAndRepair();
            } catch (Throwable ex) {
                r = new TemplateRecurringScheduleService.ValidationReport(0, 0, 0, 1);
            }
            try {
                schedules.clear();
                restoreRecurringSchedulesBestEffort();
            } catch (Throwable ignored) {
            }
            f.complete(r);
        });
        try {
            return f.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ex) {
            return new TemplateRecurringScheduleService.ValidationReport(0, 0, 0, 1);
        }
    }

    /**
     * Phase 5.6.1: Recompute and persist nextDue timestamps for all enabled schedules.
     *
     * <p>Useful when schedules were edited manually or after a settings migration.</p>
     */
    public void maintenanceRecomputeNextDueAll() {
        scheduler.execute(() -> {
            long now = System.currentTimeMillis();
            for (var e : schedules.entrySet()) {
                String templateId = e.getKey();
                ScheduleState st = e.getValue();
                if (st == null || !st.enabled || st.periodMillis <= 0) continue;
                long base = st.lastRunMillis > 0 ? st.lastRunMillis : now;
                st.nextDueMillis = base + st.periodMillis;
                st.retryCount = 0;
                st.backoffUntilMillis = 0L;
                st.lastFailureCategory = null;
                persistScheduleState(templateId, st);
            }
            try {
                historyService.log(TemplateRunHistoryEntry.now("*", null, "MAINT", "Recomputed next due for all schedules", null));
            } catch (Throwable ignored) {
            }
        });
    }

    private static ExecutorService newRunExecutor(int maxParallel) {
        int threads = Math.max(1, Math.min(16, maxParallel));
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "fileexplorer-template-exec");
            t.setDaemon(true);
            return t;
        });
    }

@Override
    public void close() {
        try {
            if (tickFuture != null) tickFuture.cancel(false);
        } catch (Throwable ignored) {
        }

        for (ScheduledFuture<?> f : oneShots) {
            try {
                f.cancel(false);
            } catch (Throwable ignored) {
            }
        }
        oneShots.clear();

        // Best-effort cancel outstanding template executions.
        for (var e : inFlight.entrySet()) {
            try {
                Future<?> f = e.getValue() == null ? null : e.getValue().future;
                if (f != null) f.cancel(true);
            } catch (Throwable ignored) {
            }
        }
        inFlight.clear();
        schedules.clear();

        try {
            runExecutor.shutdownNow();
        } catch (Throwable ignored) {
        }

        scheduler.shutdownNow();
    }

    /**
     * Tiny wrapper so UI code can consume OptionalLong without bringing it into older call sites.
     */
    public record OptionalLongValue(java.util.OptionalLong value) {
        public static OptionalLongValue of(java.util.OptionalLong v) {
            return new OptionalLongValue(v == null ? java.util.OptionalLong.empty() : v);
        }

        public boolean isPresent() { return value.isPresent(); }
        public long getAsLong() { return value.getAsLong(); }
    }
}
