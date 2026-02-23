package com.fileexplorer.service.ops;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.awt.Desktop;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static java.nio.file.StandardCopyOption.*;

import com.fileexplorer.service.ops.history.OperationHistoryService;

import com.fileexplorer.service.ops.history.OperationHistoryEntry;

import com.fileexplorer.service.ops.conflict.ConflictPolicyAction;
import com.fileexplorer.service.ops.conflict.ConflictPolicyConfig;
import com.fileexplorer.service.ops.conflict.ConflictPolicyEngine;
import com.fileexplorer.service.ops.conflict.ConflictPolicyProfile;
import com.fileexplorer.service.ops.conflict.ConflictPolicyStore;
import com.fileexplorer.service.ops.preview.OperationPlanAction;
import com.fileexplorer.service.ops.preview.OperationPlanItem;
import com.fileexplorer.service.ops.preview.OperationPlanSnapshot;
import com.fileexplorer.service.ops.OperationOriginAudit;
import com.fileexplorer.service.ops.journal.OperationJournalService;
import com.fileexplorer.service.ops.journal.OperationRecoveryService;
import com.fileexplorer.service.ops.rollback.OperationRollbackService;
import com.fileexplorer.service.ops.rollback.RollbackStep;
import com.fileexplorer.service.ops.rollback.RollbackMode;

/**
 * Phase 3.6.2: Single-threaded operation queue + JavaFX-friendly observable state.
 *
 * <p>Runs COPY/MOVE/DELETE/RENAME sequentially. Cancellation is best-effort.</p>
 */
public final class OperationQueueService implements AutoCloseable {

    private static final String TMP_SUFFIX = ".__fe_tmp__";

/**
 * VerifyMode.
 * <p>
 * Auto-generated API documentation for this type.
 */
    public enum VerifyMode {
        SIZE_ONLY,
        SHA256
    }

/**
 * parseVerifyMode.
 *
 * @param v TODO
 * @return TODO
 */
    private static VerifyMode parseVerifyMode(String v) {
        if (v == null) return VerifyMode.SIZE_ONLY;
        String s = v.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "sha", "sha256", "sha-256" -> VerifyMode.SHA256;
            case "size", "size_only", "size-only" -> VerifyMode.SIZE_ONLY;
            default -> VerifyMode.SIZE_ONLY;
        };
    }

    private static final VerifyMode VERIFY_MODE = parseVerifyMode(System.getProperty("fileexplorer.ops.verify", "size"));

    // Phase 3.6.10: orphan temp cleanup policy
    private static final String PREF_NODE = "com.fileexplorer.operations";
    private static final String PREF_ORPHAN_POLICY = "operations.orphanTempPolicy";

    public enum OrphanTempPolicy {
        ASK,
        AUTO_CLEAN,
        NEVER
    }

/**
 * parseOrphanPolicy.
 *
 * @param v TODO
 * @return TODO
 */
    private static OrphanTempPolicy parseOrphanPolicy(String v) {
        if (v == null) return OrphanTempPolicy.ASK;
        String s = v.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "auto", "auto_clean", "auto-clean", "autoclean" -> OrphanTempPolicy.AUTO_CLEAN;
            case "never", "ignore" -> OrphanTempPolicy.NEVER;
            default -> OrphanTempPolicy.ASK;
        };
    }

    
    private volatile OperationHistoryService historyService;
private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    // Phase 4.4.1: execution drift policy persistence
    private static final String DRIFT_POLICY_KEY = "execution.driftPolicy";
    private final java.util.concurrent.atomic.AtomicReference<ExecutionDriftPolicy> globalDriftPolicy =
            new java.util.concurrent.atomic.AtomicReference<>(ExecutionDriftPolicy.SKIP_AFFECTED);

/**
 * loadDriftPolicyFromPrefs.
 *
 */
    private void loadDriftPolicyFromPrefs() {
        try {
            String v = prefs.get(DRIFT_POLICY_KEY, ExecutionDriftPolicy.SKIP_AFFECTED.name());
            globalDriftPolicy.set(ExecutionDriftPolicy.valueOf(v));
        } catch (Exception ignored) {
            globalDriftPolicy.set(ExecutionDriftPolicy.SKIP_AFFECTED);
        }
    }




    // Phase 4.5.0: transaction journal + recovery
    private final OperationJournalService journalService = new OperationJournalService();
    private final OperationRecoveryService recoveryService = new OperationRecoveryService(journalService);

    // Phase 5.0.1: rollback mode control (Preferences-backed)
    // Backward compatible with Phase 5.0.0 boolean: execution.rollbackEnabled
    private static final String ROLLBACK_ENABLED_KEY = "execution.rollbackEnabled";
    private static final String ROLLBACK_MODE_KEY = "execution.rollbackMode";

    private final java.util.concurrent.atomic.AtomicReference<RollbackMode> rollbackMode =
            new java.util.concurrent.atomic.AtomicReference<>(RollbackMode.ALWAYS);

    private final OperationRollbackService rollbackService = new OperationRollbackService(journalService);

    // Phase 5.1.0: atomic operation groups ("batch transactions")
    private static final String BATCH_MODE_KEY = "execution.batchTransactionMode";
    private final java.util.concurrent.atomic.AtomicBoolean batchTransactionMode =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicReference<String> activeGroupId =
            new java.util.concurrent.atomic.AtomicReference<>(null);

    // Tracks completed handles per group to enable group rollback on failure (best-effort).
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.Deque<OperationHandleImpl>> completedByGroup =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean> groupRollbackTriggered =
            new java.util.concurrent.ConcurrentHashMap<>();


/**
 * loadRollbackModeFromPrefs.
 *
 */
    private void loadRollbackModeFromPrefs() {
        // Prefer mode, fallback to old boolean.
        try {
            String v = prefs.get(ROLLBACK_MODE_KEY, null);
            if (v != null && !v.isBlank()) {
                rollbackMode.set(RollbackMode.valueOf(v));
                return;
            }
        } catch (Exception ignored) { }

        try {
            boolean enabled = prefs.getBoolean(ROLLBACK_ENABLED_KEY, true);
            rollbackMode.set(enabled ? RollbackMode.ALWAYS : RollbackMode.NEVER);
        } catch (Exception ignored) {
            rollbackMode.set(RollbackMode.ALWAYS);
        }
    }

/**
 * loadBatchModeFromPrefs.
 *
 */
    private void loadBatchModeFromPrefs() {
        try {
            boolean enabled = prefs.getBoolean(BATCH_MODE_KEY, false);
            batchTransactionMode.set(enabled);
        } catch (Exception ignored) {
            batchTransactionMode.set(false);
        }
    }

/**
 * isBatchTransactionMode.
 *
 * @return TODO
 */
    public boolean isBatchTransactionMode() {
        return batchTransactionMode.get();
    }

    /**
     * Enable/disable batch transaction mode (Phase 5.1.0).
     *
     * <p>When enabled, newly enqueued operations are tagged with a group id and will not
     * start executing until {@link #commitCurrentGroup()} is called.</p>
     */
    public void setBatchTransactionMode(boolean enabled) {
        batchTransactionMode.set(enabled);
        try { prefs.putBoolean(BATCH_MODE_KEY, enabled); } catch (Exception ignored) {}
        if (!enabled) {
            // If disabling while a group is active, allow queue to run.
            fx(this::pump);
        } else {
            // If enabling, ensure a group id exists for new operations.
            if (activeGroupId.get() == null) activeGroupId.set(java.util.UUID.randomUUID().toString());
        }
    }

/**
 * currentGroupId.
 *
 * @return TODO
 */
    public String currentGroupId() {
        return activeGroupId.get();
    }

    /**
     * Commit the current group: queued items tagged with the current group id are allowed to execute.
     */
    public void commitCurrentGroup() {
        String gid = activeGroupId.get();
        if (gid == null) return;

        // Journal group start best-effort.
        try {
            java.util.Map<String, String> f = new java.util.HashMap<>();
            f.put("groupId", gid);
            f.put("mode", "COMMIT");
            journalService.append(gid, com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_START, f);
        } catch (Throwable ignored) {}

        // Allow execution.
        activeGroupId.set(java.util.UUID.randomUUID().toString());
        fx(this::pump);
    }

    /**
     * Discard the current group: remove queued items that belong to the current group id.
     */
    public void discardCurrentGroup() {
        String gid = activeGroupId.get();
        if (gid == null) return;
        fx(() -> {
            queue.removeIf(h -> h != null
                    && h.item() != null
                    && gid.equals(h.item().operationGroupId())
                    && h.status.get() == OperationStatus.QUEUED);
            schedulePersist();
        });

        // Journal group completion as discarded (best-effort).
        try {
            java.util.Map<String, String> f = new java.util.HashMap<>();
            f.put("groupId", gid);
            f.put("result", "DISCARDED");
            journalService.append(gid, com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_COMPLETE, f);
        } catch (Throwable ignored) {}

        activeGroupId.set(java.util.UUID.randomUUID().toString());
    }

/**
 * getRollbackMode.
 *
 * @return TODO
 */
    public RollbackMode getRollbackMode() {
        return rollbackMode.get();
    }

/**
 * setRollbackMode.
 *
 * @param mode TODO
 */
    public void setRollbackMode(RollbackMode mode) {
        if (mode == null) mode = RollbackMode.ALWAYS;
        rollbackMode.set(mode);
        try { prefs.put(ROLLBACK_MODE_KEY, mode.name()); } catch (Exception ignored) {}
        // Keep the legacy boolean aligned for any older UI hooks.
        try { prefs.putBoolean(ROLLBACK_ENABLED_KEY, mode != RollbackMode.NEVER); } catch (Exception ignored) {}
    }

    /** Legacy API preserved for existing UI code paths. */
    public boolean isRollbackEnabled() { return rollbackMode.get() != RollbackMode.NEVER; }

    /** Legacy API preserved for existing UI code paths. */
    public void setRollbackEnabled(boolean enabled) {
        setRollbackMode(enabled ? RollbackMode.ALWAYS : RollbackMode.NEVER);
    }

    // Phase 4.2.0: conflict policy profiles (Preferences-backed)
    private final ConflictPolicyStore conflictPolicyStore = new ConflictPolicyStore(prefs);
    private final ConflictPolicyEngine conflictPolicyEngine = new ConflictPolicyEngine();

    private final ObservableList<Path> orphanTempFiles = FXCollections.observableArrayList();
    private final ObservableList<Path> orphanTempFilesReadOnly = FXCollections.unmodifiableObservableList(orphanTempFiles);
    private final ReadOnlyIntegerWrapper orphanTempCount = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyBooleanWrapper orphanTempIgnoredThisSession = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyIntegerWrapper maxParallelism;
    private final java.util.concurrent.ThreadPoolExecutor worker;

    private final ObservableList<OperationHandleImpl> queue = FXCollections.observableArrayList();
    private final ObservableList<OperationHandleImpl> queueReadOnly = FXCollections.unmodifiableObservableList(queue);

    /**
     * For backward compatibility with existing UI, this represents the most recently started RUNNING operation (or null).
     */
    private final ReadOnlyObjectWrapper<OperationHandleImpl> active = new ReadOnlyObjectWrapper<>(null);

    /**
     * True when at least one operation is RUNNING.
     */
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);

    /**
     * When paused, the dispatcher will not start new operations. Running operations are not forcibly stopped;
     * pausing is primarily used for startup recovery flow (Phase 3.6.7.1) and user-controlled throttling.
     */
    private final ReadOnlyBooleanWrapper paused = new ReadOnlyBooleanWrapper(false);


    /**
     * Aggregate status line for active COPY/MOVE operations (throughput/remaining/ETA).
     */
    private final ReadOnlyStringWrapper aggregateStatus = new ReadOnlyStringWrapper("");

    /**
     * Running operations (FX-thread owned list).
     */
    private final ObservableList<OperationHandleImpl> activeOperations = FXCollections.observableArrayList();
    private final ObservableList<OperationHandleImpl> activeOperationsReadOnly = FXCollections.unmodifiableObservableList(activeOperations);

    /**
     * Destination path locks to prevent concurrent writers clobbering the same target path.
     */
    private final java.util.concurrent.ConcurrentHashMap<Path, java.util.concurrent.locks.ReentrantLock> destLocks = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicBoolean pumpScheduled = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean aggregateRecomputeScheduled = new java.util.concurrent.atomic.AtomicBoolean(false);

    // Phase 3.6.8: conflict resolution UI (serialized dialogs across parallel ops)
    private static final Semaphore ROLLBACK_DIALOG_SEMAPHORE = new Semaphore(1, true);
    private static final Semaphore CONFLICT_DIALOG_SEMAPHORE = new Semaphore(1, true);

    private enum ConflictAction { SKIP, OVERWRITE, RENAME }
    private record ConflictDecision(ConflictAction action, boolean applyToAll) {}
    private record ResolvedDestination(Path path, boolean overwrite) {}


    /**
     * Phase 4.1.4: Conflict queue resolution session.
     *
     * <p>Conflicts are queued per-operation and resolved in a single dialog with navigation and
     * bulk "resolve all remaining" actions. The operation worker thread blocks until a decision
     * is provided for each destination path.</p>
     */
    private static final class ConflictResolutionSession {

        private final OperationHandleImpl handle;

        private final Object lock = new Object();

        // destination -> decision
        private final java.util.Map<Path, ConflictDecision> decisions = new java.util.HashMap<>();

        // insertion-order list for navigation
        private final ObservableList<ConflictEntry> entries = FXCollections.observableArrayList();

        private volatile boolean cancelled = false;

        // FX-only fields
        private volatile Dialog<Void> dialog;
        private volatile ListView<ConflictEntry> listView;
        private volatile Label detailLabel;
        private volatile CheckBox applyAllCheck;

        private final AtomicBoolean dialogOpen = new AtomicBoolean(false);
        private final AtomicBoolean semaphoreHeld = new AtomicBoolean(false);

        ConflictResolutionSession(OperationHandleImpl handle) {
            this.handle = handle;
        }

/**
 * requestDecision.
 *
 * @param source TODO
 * @param dst TODO
 * @param overwriteDefault TODO
 * @return TODO
 */
        ConflictDecision requestDecision(Path source, Path dst, boolean overwriteDefault) {
            if (source == null || dst == null) {
                return new ConflictDecision(ConflictAction.SKIP, false);
            }

            // Fast-path: apply-to-all already chosen for this operation
            if (handle != null && handle.rememberedConflictApplyToAll && handle.rememberedConflictAction != null) {
                return new ConflictDecision(handle.rememberedConflictAction, true);
            }

            addEntryIfAbsent(source, dst);

            ensureDialogOpenFx();

            synchronized (lock) {
                while (!cancelled && !decisions.containsKey(dst)) {
                    try {
                        lock.wait(250L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        cancelled = true;
                        break;
                    }
                }
                return cancelled ? null : decisions.get(dst);
            }
        }

/**
 * addEntryIfAbsent.
 *
 * @param source TODO
 * @param dst TODO
 */
        private void addEntryIfAbsent(Path source, Path dst) {
            synchronized (lock) {
                // already decided
                if (decisions.containsKey(dst)) return;

                for (ConflictEntry e : entries) {
                    if (dst.equals(e.destination)) return;
                }
                entries.add(new ConflictEntry(source, dst));
                lock.notifyAll();
            }
        }

/**
 * ensureDialogOpenFx.
 *
 */
        private void ensureDialogOpenFx() {
            if (dialogOpen.get()) {
                fx(this::refreshFx);
                return;
            }

            // Acquire global semaphore once per visible dialog
            if (!semaphoreHeld.get()) {
                try {
                    CONFLICT_DIALOG_SEMAPHORE.acquire();
                    semaphoreHeld.set(true);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    cancelled = true;
                    synchronized (lock) { lock.notifyAll(); }
                    return;
                }
            }

            dialogOpen.set(true);

            fx(() -> {
                try {
                    buildAndShowDialogFx();
                } catch (Throwable t) {
                    cancelled = true;
                    synchronized (lock) { lock.notifyAll(); }
                    closeDialogFx();
                }
            });
        }

/**
 * refreshFx.
 *
 */
        private void refreshFx() {
            if (listView != null) {
                listView.refresh();
                ConflictEntry sel = listView.getSelectionModel().getSelectedItem();
                if (detailLabel != null) {
                    detailLabel.setText(sel == null ? "" : sel.detailText());
                }
            }
        }

/**
 * buildAndShowDialogFx.
 *
 */
        private void buildAndShowDialogFx() {
            Dialog<Void> d = new Dialog<>();
            d.setTitle("Conflict queue");
            d.setHeaderText("Resolve file conflicts (one operation): " + safeLabelForHandle(handle));

            ListView<ConflictEntry> lv = new ListView<>(entries);
            lv.setPrefWidth(560);
            lv.setPrefHeight(280);
            lv.setCellFactory(list -> new ListCell<>() {
                @Override protected void updateItem(ConflictEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        return;
                    }
                    setText(item.displayText());
                }
            });

            Label details = new Label();
            details.setWrapText(true);
            details.setPrefWidth(480);

            applyAllCheck = new CheckBox("Apply this choice to all conflicts in this operation");

            Button skipBtn = new Button("Use destination (skip)");
            Button overwriteBtn = new Button("Use source (overwrite)");
            Button renameBtn = new Button("Keep both (rename)");

            Button resolveAllSkip = new Button("Resolve ALL remaining: Skip");
            Button resolveAllOverwrite = new Button("Resolve ALL remaining: Overwrite");
            Button resolveAllRename = new Button("Resolve ALL remaining: Rename");

            Button prev = new Button("Previous");
            Button next = new Button("Next");

            Button cancelOp = new Button("Cancel operation");

            skipBtn.setMaxWidth(Double.MAX_VALUE);
            overwriteBtn.setMaxWidth(Double.MAX_VALUE);
            renameBtn.setMaxWidth(Double.MAX_VALUE);
            resolveAllSkip.setMaxWidth(Double.MAX_VALUE);
            resolveAllOverwrite.setMaxWidth(Double.MAX_VALUE);
            resolveAllRename.setMaxWidth(Double.MAX_VALUE);
            cancelOp.setMaxWidth(Double.MAX_VALUE);

            VBox actions = new VBox(8,
                    details,
                    applyAllCheck,
                    new Separator(),
                    skipBtn,
                    overwriteBtn,
                    renameBtn,
                    new Separator(),
                    resolveAllSkip,
                    resolveAllOverwrite,
                    resolveAllRename,
                    new Separator(),
                    new HBox(8, prev, next),
                    new Separator(),
                    cancelOp
            );
            actions.setPadding(new Insets(8));
            actions.setPrefWidth(500);

            HBox root = new HBox(10, lv, actions);
            root.setPadding(new Insets(10));

            d.getDialogPane().setContent(root);
            d.getDialogPane().getButtonTypes().setAll(new ButtonType("Hide", ButtonBar.ButtonData.CANCEL_CLOSE));

            this.dialog = d;
            this.listView = lv;
            this.detailLabel = details;

            lv.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                details.setText(newV == null ? "" : newV.detailText());
            });

            if (!entries.isEmpty()) {
                lv.getSelectionModel().selectFirst();
                details.setText(entries.get(0).detailText());
            }

            prev.setOnAction(e -> moveSelection(lv, -1));
            next.setOnAction(e -> moveSelection(lv, +1));

            skipBtn.setOnAction(e -> applyDecisionFromFx(lv, ConflictAction.SKIP, false));
            overwriteBtn.setOnAction(e -> applyDecisionFromFx(lv, ConflictAction.OVERWRITE, false));
            renameBtn.setOnAction(e -> applyDecisionFromFx(lv, ConflictAction.RENAME, false));

            resolveAllSkip.setOnAction(e -> applyDecisionFromFx(lv, ConflictAction.SKIP, true));
            resolveAllOverwrite.setOnAction(e -> applyDecisionFromFx(lv, ConflictAction.OVERWRITE, true));
            resolveAllRename.setOnAction(e -> applyDecisionFromFx(lv, ConflictAction.RENAME, true));

            cancelOp.setOnAction(e -> {
                cancelled = true;
                if (handle != null) handle.cancelled.set(true);
                synchronized (lock) { lock.notifyAll(); }
                closeDialogFx();
            });

            d.setOnHidden(e -> closeDialogFx());

            d.show();
        }

/**
 * moveSelection.
 *
 * @param lv TODO
 * @param delta TODO
 */
        private void moveSelection(ListView<ConflictEntry> lv, int delta) {
            if (lv == null) return;
            int n = lv.getItems().size();
            if (n <= 0) return;
            int i = lv.getSelectionModel().getSelectedIndex();
            if (i < 0) i = 0;
            int next = Math.max(0, Math.min(n - 1, i + delta));
            lv.getSelectionModel().select(next);
            refreshFx();
        }

/**
 * applyDecisionFromFx.
 *
 * @param lv TODO
 * @param action TODO
 * @param bulk TODO
 */
        private void applyDecisionFromFx(ListView<ConflictEntry> lv, ConflictAction action, boolean bulk) {
            if (lv == null) return;

            boolean applyAll = (applyAllCheck != null) && applyAllCheck.isSelected();
            if (bulk) applyAll = true;

            if (applyAll && handle != null) {
                handle.rememberedConflictApplyToAll = true;
                handle.rememberedConflictAction = action;
            }

            synchronized (lock) {
                if (bulk) {
                    for (ConflictEntry e : entries) {
                        if (e == null || e.isResolved()) continue;
                        decisions.put(e.destination, new ConflictDecision(action, true));
                        e.setDecision(action);
                    }
                } else {
                    ConflictEntry sel = lv.getSelectionModel().getSelectedItem();
                    if (sel == null) return;
                    decisions.put(sel.destination, new ConflictDecision(action, applyAll));
                    sel.setDecision(action);
                }
                lock.notifyAll();
            }

            if (bulk) {
                refreshFx();
                closeDialogFx();
                return;
            }

            selectNextPendingFx(lv);
            refreshFx();

            if (!hasPending()) {
                closeDialogFx();
            }
        }

/**
 * selectNextPendingFx.
 *
 * @param lv TODO
 */
        private void selectNextPendingFx(ListView<ConflictEntry> lv) {
            if (lv == null) return;
            int n = lv.getItems().size();
            if (n <= 0) return;

            int start = Math.max(0, lv.getSelectionModel().getSelectedIndex());
            for (int k = 0; k < n; k++) {
                int i = (start + 1 + k) % n;
                ConflictEntry e = lv.getItems().get(i);
                if (e != null && !e.isResolved()) {
                    lv.getSelectionModel().select(i);
                    return;
                }
            }
        }

/**
 * hasPending.
 *
 * @return TODO
 */
        private boolean hasPending() {
            for (ConflictEntry e : entries) {
                if (e != null && !e.isResolved()) return true;
            }
            return false;
        }

/**
 * closeDialogFx.
 *
 */
        private void closeDialogFx() {
            dialogOpen.set(false);

            Dialog<Void> d = this.dialog;
            this.dialog = null;
            this.listView = null;
            this.detailLabel = null;
            this.applyAllCheck = null;

            if (d != null) {
                try { d.close(); } catch (Throwable ignored) {}
            }

            if (semaphoreHeld.getAndSet(false)) {
                CONFLICT_DIALOG_SEMAPHORE.release();
            }
        }

/**
 * safeLabelForHandle.
 *
 * @param handle TODO
 * @return TODO
 */
        private static String safeLabelForHandle(OperationHandleImpl handle) {
            try {
                if (handle == null || handle.item == null) return "Operation";
                FileOperationRequest r = handle.item.request();
                if (r == null) return "Operation";
                String t = r.type() == null ? "Operation" : r.type().name();
                Path target = r.targetDirectory();
                return t + (target == null ? "" : (" → " + target));
            } catch (Throwable t) {
                return "Operation";
            }
        }

        private static final class ConflictEntry {
            private final Path source;
            private final Path destination;
            private volatile ConflictAction decision;

            ConflictEntry(Path source, Path destination) {
                this.source = source;
                this.destination = destination;
            }

            boolean isResolved() { return decision != null; }

            void setDecision(ConflictAction action) { this.decision = action; }

/**
 * displayText.
 *
 * @return TODO
 */
            String displayText() {
                String name = safeName(destination);
                String state = (decision == null) ? "PENDING" : decision.name();
                return name + "  [" + state + "]";
            }

/**
 * detailText.
 *
 * @return TODO
 */
            String detailText() {
                StringBuilder sb = new StringBuilder();
                sb.append("Destination exists:\n").append(destination).append("\n");
                sb.append(metaLine(destination));
                sb.append("\n\nIncoming source:\n").append(source).append("\n");
                sb.append(metaLine(source));
                sb.append("\n\nChoose an action for this conflict.");
                return sb.toString();
            }

/**
 * metaLine.
 *
 * @param p TODO
 * @return TODO
 */
            private static String metaLine(Path p) {
                if (p == null) return "";
                try {
                    boolean isDir = Files.isDirectory(p);
                    String type = isDir ? "Directory" : "File";
                    String size = isDir ? "" : ("  Size: " + Files.size(p) + " bytes");
                    String mod = "";
                    try { mod = "  Modified: " + Files.getLastModifiedTime(p).toString(); } catch (Exception ignored) {}
                    return type + size + mod;
                } catch (Exception ex) {
                    return "";
                }
            }
        }
    }

    /** Phase 3.9.4: Per-item execution results for multi-source operations. */
    private record ItemResult(OperationStatus status, String message) {}

    // Phase 3.9.7: app-managed recycle bin to support DELETE undo.
    private final RecycleBinService recycleBin = new RecycleBinService();


    /* Phase 3.6.7: persist queue across restarts (best-effort). */
    private final OperationQueuePersistence persistence = new OperationQueuePersistence();
    private final ReadOnlyIntegerWrapper recoveredCount = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper recoveredRunningCount = new ReadOnlyIntegerWrapper(0);
    private final java.util.Set<String> recoveredRunningIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final java.util.Set<String> recoveredIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> recoveredRunningAllowedIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Phase 3.6.7.4: Safer recovery. Previously-running operations are blocked from auto-dispatch unless explicitly allowed.
    private final ReadOnlyBooleanWrapper recoveredRunningAllowed = new ReadOnlyBooleanWrapper(false);
    private final java.util.concurrent.atomic.AtomicBoolean persistScheduled = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile boolean suppressPersist;


/**
 * OperationQueueService.
 *
 * @return TODO
 */
    public OperationQueueService() {
        this(2);
    }

/**
 * setHistoryService.
 *
 * @param historyService TODO
 */
public void setHistoryService(OperationHistoryService historyService) {
    this.historyService = historyService;
}

    /** Phase 3.9.7: Resolve latest recycled location for an original path (best-effort). */
    public Optional<Path> resolveLatestRecycled(Path original) {
        return recycleBin.resolveLatestRecycled(original);
}

    /** Phase 3.9.7: True if an item can likely be restored from recycle bin. */
    public boolean canRestoreFromRecycleBin(Path original) {
        return resolveLatestRecycled(original).isPresent();
}

    /** Phase 3.9.7: Expose recycle-bin directory path for UI/diagnostics. */
    public Path recycleBinDirectory() {
        return recycleBin.recycleDir();
}



/**
 * OperationQueueService.
 *
 * @param maxParallelism TODO
 * @return TODO
 */
    public OperationQueueService(int maxParallelism) {
        int initial = Math.max(1, maxParallelism);
        this.maxParallelism = new ReadOnlyIntegerWrapper(initial);

        this.worker = new java.util.concurrent.ThreadPoolExecutor(
                initial,
                initial,
                30L,
                java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "fileexplorer-operation-queue");
                    t.setDaemon(true);
                    return t;
                }
        );
        this.worker.allowCoreThreadTimeOut(true);
        loadDriftPolicyFromPrefs();
        loadRollbackModeFromPrefs();
        loadBatchModeFromPrefs();
}

    // -------------------------
    // Phase 3.6.10: orphan temp detection/cleanup
    // -------------------------

/**
 * orphanTempCountProperty.
 *
 * @return TODO
 */
    public ReadOnlyIntegerProperty orphanTempCountProperty() {
        return orphanTempCount.getReadOnlyProperty();
}

/**
 * getOrphanTempCount.
 *
 * @return TODO
 */
    public int getOrphanTempCount() {
        return orphanTempCount.get();
}

/**
 * getOrphanTempFiles.
 *
 * @return TODO
 */
    public ObservableList<Path> getOrphanTempFiles() {
        return orphanTempFilesReadOnly;
    }

/**
 * orphanTempIgnoredThisSessionProperty.
 *
 * @return TODO
 */
    public ReadOnlyBooleanProperty orphanTempIgnoredThisSessionProperty() {
        return orphanTempIgnoredThisSession.getReadOnlyProperty();
    }

/**
 * ignoreOrphanTempsThisSession.
 *
 */
    public void ignoreOrphanTempsThisSession() {
        fx(() -> orphanTempIgnoredThisSession.set(true));
    }

/**
 * getOrphanTempPolicy.
 *
 * @return TODO
 */
    public OrphanTempPolicy getOrphanTempPolicy() {
        return parseOrphanPolicy(prefs.get(PREF_ORPHAN_POLICY, "ask"));
    }

/**
 * setOrphanTempPolicy.
 *
 * @param policy TODO
 */
    public void setOrphanTempPolicy(OrphanTempPolicy policy) {
        if (policy == null) policy = OrphanTempPolicy.ASK;
        OrphanTempPolicy finalPolicy = policy;
        fx(() -> prefs.put(PREF_ORPHAN_POLICY, finalPolicy.name()));
    }

    // -------------------------
    // Phase 4.2.0: conflict policy profiles (public API for UI/controllers)
    // -------------------------

/**
 * getConflictPolicyProfile.
 *
 * @return TODO
 */
    public ConflictPolicyProfile getConflictPolicyProfile() {
        return conflictPolicyStore.getProfile();
    }

/**
 * setConflictPolicyProfile.
 *
 * @param profile TODO
 */
    public void setConflictPolicyProfile(ConflictPolicyProfile profile) {
        ConflictPolicyProfile p = (profile == null) ? ConflictPolicyProfile.DEFAULT : profile;
        fx(() -> conflictPolicyStore.setProfile(p));
    }

/**
 * getCustomConflictDefaultAction.
 *
 * @return TODO
 */
    public ConflictPolicyAction getCustomConflictDefaultAction() {
        return conflictPolicyStore.getCustomDefaultAction();
    }

/**
 * setCustomConflictDefaultAction.
 *
 * @param action TODO
 */
    public void setCustomConflictDefaultAction(ConflictPolicyAction action) {
        ConflictPolicyAction a = (action == null) ? ConflictPolicyAction.PROMPT : action;
        fx(() -> conflictPolicyStore.setCustomDefaultAction(a));
    }


/**
 * Phase 7.0.0: Returns the current ordered CUSTOM conflict rules.
 */
public java.util.List<com.fileexplorer.service.ops.conflict.ConflictRule> getCustomConflictRules() {
    return conflictPolicyStore.getCustomRules();
}

/**
 * Phase 7.0.0: Persists the ordered CUSTOM conflict rules.
 */
public void setCustomConflictRules(java.util.List<com.fileexplorer.service.ops.conflict.ConflictRule> rules) {
    fx(() -> conflictPolicyStore.setCustomRules(rules));
}


    /**
     * Scans for orphan temp files created by the atomic-copy strategy (Phase 3.6.9).
     *
     * <p>Best-effort: scans destination directories referenced by persisted/recovered queue items and
     * the target directories of current queued items.</p>
     */
    public int scanForOrphanTempFiles() {
        OrphanTempPolicy policy = getOrphanTempPolicy();
        if (policy == OrphanTempPolicy.NEVER) {
            fx(() -> {
                orphanTempFiles.clear();
                orphanTempCount.set(0);
            });
            return 0;
        }

        Set<Path> dirs = new LinkedHashSet<>();

        for (OperationHandleImpl h : queue) {
            FileOperationRequest r = h.item.request();
            if (r != null && r.targetDirectory() != null) {
                dirs.add(r.targetDirectory());
            }
        }

        try {
            List<OperationQueuePersistence.SavedOperation> persisted = persistence.loadSaved();
            for (OperationQueuePersistence.SavedOperation so : persisted) {
                if (so == null || so.request() == null) continue;
                if (so.request().targetDirectory() != null) dirs.add(so.request().targetDirectory());
            }
        } catch (Exception ignored) {
        }

        List<Path> found = new ArrayList<>();
        for (Path dir : dirs) {
            if (dir == null) continue;
            try {
                if (!Files.isDirectory(dir)) continue;
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                    for (Path p : ds) {
                        if (p == null) continue;
                        String name = String.valueOf(p.getFileName());
                        if (!name.contains(TMP_SUFFIX)) continue;
                        try {
                            if (Files.isRegularFile(p)) found.add(p);
                        } catch (Exception ignored2) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (policy == OrphanTempPolicy.AUTO_CLEAN && !found.isEmpty()) {
            cleanupOrphanTempFiles(found);
            return 0;
        }

        fx(() -> {
            orphanTempIgnoredThisSession.set(false);
            orphanTempFiles.setAll(found);
            orphanTempCount.set(found.size());
        });
        return found.size();
    }

/**
 * cleanupOrphanTempFiles.
 *
 */
    public void cleanupOrphanTempFiles() {
        cleanupOrphanTempFiles(new ArrayList<>(orphanTempFiles));
        fx(() -> {
            orphanTempFiles.clear();
            orphanTempCount.set(0);
        });
    }

/**
 * cleanupOrphanTempFiles.
 *
 * @param files TODO
 */
    private void cleanupOrphanTempFiles(List<Path> files) {
        for (Path p : files) {
            try {
                if (p != null && Files.exists(p) && Files.isRegularFile(p)) {
                    String name = String.valueOf(p.getFileName());
                    if (name.contains(TMP_SUFFIX)) {
                        Files.deleteIfExists(p);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
/**
 * getQueue.
 *
 * @return TODO
 */
public ObservableList<? extends OperationHandle> getQueue() {
        return queueReadOnly;
    }

    

/**
 * getMaxParallelism.
 *
 * @return TODO
 */
    public int getMaxParallelism() {
        return maxParallelism.get();
    }

/**
 * maxParallelismProperty.
 *
 * @return TODO
 */
    public ReadOnlyIntegerProperty maxParallelismProperty() {
        return maxParallelism.getReadOnlyProperty();
    }

/**
 * setMaxParallelism.
 *
 * @param value TODO
 */
    public void setMaxParallelism(int value) {
        int v = Math.max(1, value);
        fx(() -> maxParallelism.set(v));
        // Resize pool (does not cancel running tasks).
        worker.setCorePoolSize(v);
        worker.setMaximumPoolSize(v);
        // Try to start more queued operations if capacity increased.
        pump();
    }

/**
 * getActiveOperations.
 *
 * @return TODO
 */
public ObservableList<? extends OperationHandle> getActiveOperations() {
        return activeOperationsReadOnly;
    }
public ReadOnlyObjectProperty<? extends OperationHandle> activeOperationProperty() {
        return active.getReadOnlyProperty();
    }

/**
 * runningProperty.
 *
 * @return TODO
 */
    public ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    
/**
 * pausedProperty.
 *
 * @return TODO
 */
    public ReadOnlyBooleanProperty pausedProperty() {
        return paused.getReadOnlyProperty();
    }

/**
 * isPaused.
 *
 * @return TODO
 */
    public boolean isPaused() {
        return paused.get();
    }

/**
 * pause.
 *
 */
    public void pause() {
        fx(() -> paused.set(true));
    }

/**
 * resume.
 *
 */
    public void resume() {
        fx(() -> {
            recoveredRunningAllowed.set(false);
            recoveredRunningAllowedIds.clear();
            paused.set(false);
            // Resume dispatching queued work now that we're unpaused.
            pump();
        });
    }
/**
 * aggregateStatusProperty.
 *
 * @return TODO
 */
public ReadOnlyStringProperty aggregateStatusProperty() {
        return aggregateStatus.getReadOnlyProperty();
    }

/**
 * getRecoveredCount.
 *
 * @return TODO
 */
    public int getRecoveredCount() {
        return recoveredCount.get();
    }

/**
 * recoveredCountProperty.
 *
 * @return TODO
 */
    public ReadOnlyIntegerProperty recoveredCountProperty() {
        return recoveredCount.getReadOnlyProperty();
    }

/**
 * getRecoveredRunningCount.
 *
 * @return TODO
 */
    public int getRecoveredRunningCount() {
        return recoveredRunningCount.get();
    }

/**
 * recoveredRunningCountProperty.
 *
 * @return TODO
 */
    public ReadOnlyIntegerProperty recoveredRunningCountProperty() {
        return recoveredRunningCount.getReadOnlyProperty();
    }

/**
 * isRecoveredRunningAllowed.
 *
 * @return TODO
 */
    public boolean isRecoveredRunningAllowed() {
        return recoveredRunningAllowed.get();
    }

/**
 * recoveredRunningAllowedProperty.
 *
 * @return TODO
 */
    public ReadOnlyBooleanProperty recoveredRunningAllowedProperty() {
        return recoveredRunningAllowed.getReadOnlyProperty();
    }

/**
 * isRecoveredFromRunning.
 *
 * @param operationId TODO
 * @return TODO
 */
    public boolean isRecoveredFromRunning(String operationId) {
        if (operationId == null) return false;
        return recoveredRunningIds.contains(operationId);
    }

/**
 * isRecovered.
 *
 * @param operationId TODO
 * @return TODO
 */
    public boolean isRecovered(String operationId) {
        if (operationId == null) return false;
        return recoveredIds.contains(operationId);
    }

    /** True if this recovered op was RUNNING last session and is currently blocked pending user confirmation. */
    public boolean isRecoveredRunningBlocked(String operationId) {
        if (operationId == null) return false;
        if (!recoveredRunningIds.contains(operationId)) return false;
        return !(recoveredRunningAllowed.get() || recoveredRunningAllowedIds.contains(operationId));
    }

    /** Allow a specific previously-running recovered operation to dispatch (safe, surgical). */
    public void allowRecoveredOperation(String operationId) {
        if (operationId == null) return;
        fx(() -> {
            recoveredRunningAllowedIds.add(operationId);
            // If we were paused waiting for recovery, we keep paused unless the user resumes.
            // But allowing may enable dispatch once resumed.
            schedulePersist();
            pump();
            recomputeAggregateFx();
        });
    }

    /** Discard a specific recovered operation (removes from queue and persistence). */
    public void discardRecoveredOperation(String operationId) {
        if (operationId == null) return;
        fx(() -> {
            OperationHandleImpl h = findById(operationId);
            if (h != null && h.status.get() != OperationStatus.RUNNING) {
                queue.remove(h);
            }
            recoveredIds.remove(operationId);
            recoveredRunningIds.remove(operationId);
            recoveredRunningAllowedIds.remove(operationId);
            recomputeRecoveredCountsFx();
            schedulePersist();
            recomputeAggregateFx();
        });
    }

    /** Convert a recovered MOVE operation into COPY (safer) by replacing the queued request. */
    public void convertRecoveredMoveToCopy(String operationId) {
        if (operationId == null) return;
        fx(() -> {
            OperationHandleImpl h = findById(operationId);
            if (h == null) return;
            if (h.status.get() == OperationStatus.RUNNING) return;

            FileOperationRequest req = h.item.request();
            if (req == null) return;
            if (req.type() != FileOperationType.MOVE) return;

            // Replace by removing old handle and enqueueing a COPY request.
            int idx = queue.indexOf(h);
            queue.remove(h);

            FileOperationRequest copyReq = new FileOperationRequest(
                    FileOperationType.COPY,
                    req.sources(),
                    req.targetDirectory(),
                    req.newName(),
                    req.overwrite(),
                    req.skipConflicts(),
                    req.sendToTrash()
            );

            OperationHandle newHandle = enqueue(copyReq);
            // Keep the new handle near the previous position if possible.
            if (newHandle instanceof OperationHandleImpl nhi && idx >= 0 && idx < queue.size()) {
                queue.remove(nhi);
                queue.add(idx, nhi);
            }

            // Converted op is safe to run; no "was running" block applies.
            recoveredIds.remove(operationId);
            recoveredRunningIds.remove(operationId);
            recoveredRunningAllowedIds.remove(operationId);

            recomputeRecoveredCountsFx();
            schedulePersist();
            pump();
            recomputeAggregateFx();
        });
    }

/**
 * findById.
 *
 * @param operationId TODO
 * @return TODO
 */
    private OperationHandleImpl findById(String operationId) {
        for (OperationHandleImpl h : queue) {
            if (h != null && operationId.equals(h.id())) return h;
        }
        return null;
    }

/**
 * recomputeRecoveredCountsFx.
 *
 */
    private void recomputeRecoveredCountsFx() {
        int total = recoveredIds.size();
        int runningLike = recoveredRunningIds.size();
        recoveredCount.set(total);
        recoveredRunningCount.set(runningLike);
    }


    /**
     * Load any persisted operations from the previous session and enqueue them.
     * Returns the number of recovered operations.
     */
    public int restoreSavedQueue() {
        java.util.List<OperationQueuePersistence.SavedOperation> saved = persistence.loadSaved();
        if (saved.isEmpty()) {
            fx(() -> {
                recoveredCount.set(0);
            recoveredRunningCount.set(0);
            recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
            recoveredRunningAllowed.set(false);
            recoveredRunningAllowedIds.clear();
            recoveredRunningCount.set(0);
            recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
                recoveredRunningCount.set(0);
            });
            recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
            return 0;
        }

        // Phase 3.6.7.1: start in paused mode if we recovered operations, so the user can choose Resume/Discard.
        fx(() -> paused.set(true));

        suppressPersist = true;
        int runningMarked = 0;
        try {
            for (OperationQueuePersistence.SavedOperation op : saved) {
                if (op == null || op.request() == null) continue;
                OperationHandle h = enqueueRecovered(op);
                if (h != null) { recoveredIds.add(h.id()); }
                if (op.wasRunning() && h != null) {
                    recoveredRunningIds.add(h.id());
                    runningMarked++;
                }
            }
        } finally {
            suppressPersist = false;
        }

        int total = recoveredIds.size();
        int runningFinal = runningMarked;
        fx(() -> {
            recoveredCount.set(total);
            recoveredRunningCount.set(runningFinal);
            recoveredRunningAllowed.set(false);
            recoveredRunningAllowedIds.clear();
            if (!recoveredRunningIds.isEmpty()) {
                for (OperationHandleImpl h : queue) {
                    if (h != null && recoveredRunningIds.contains(h.id())) {
                        h.progress.set(new OperationProgress(0, 0, "Recovered (was running) — requires confirmation"));
                    }
                }
            }
        });
        schedulePersist();
        return total;
    }

    /**
     * Discard persisted queue state on disk (does not alter the current in-memory queue).
     */
    public void discardSavedQueue() {
        persistence.clear();
        recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
        fx(() -> {
            recoveredRunningAllowed.set(false);
            recoveredRunningAllowedIds.clear();
            recoveredCount.set(0);
            recoveredRunningCount.set(0);
            recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
            recoveredRunningCount.set(0);
            recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
            recoveredRunningCount.set(0);
        });
    }

    // Phase 4.5.0: crash recovery via transaction journal

    /** Journal directory on disk (for Recovery Manager). */
    public java.nio.file.Path journalDirectory() {
        return journalService.journalDir();
    }

    /** Delete a journal file (best-effort). */
    public boolean deleteJournal(String operationId) {
        return journalService.deleteJournal(operationId);
    }


    /** Returns incomplete transaction journals discovered on disk. */
    public java.util.List<com.fileexplorer.service.ops.journal.OperationJournalService.RecoveryCandidate> findRecoveryCandidates() {
        return recoveryService.findRecoveryCandidates();
    }

    /** Mark an incomplete journal as failed and close it (best-effort). */
    public void markRecoveryFailed(String operationId) {
        if (operationId == null || operationId.isBlank()) return;
        journalService.writeComplete(operationId, "FAILED", "");
    }

    /**
     * Resume a crashed snapshot operation by reconstructing its plan from the journal and enqueueing
     * a new deterministic operation. Returns 1 if enqueued, else 0.
     */
    public int resumeFromJournal(String operationId, ExecutionDriftPolicy driftPolicyOverride) {
        if (operationId == null || operationId.isBlank()) return 0;
        if (journalService.isComplete(operationId)) return 0;

        // Phase 4.5.1: idempotent resume
        java.util.List<com.fileexplorer.service.ops.journal.OperationJournalEntry> entries = journalService.readAll(operationId);
        java.util.Set<Integer> doneIdx = new java.util.HashSet<>();
        for (var e : entries) {
            if (e == null) continue;
            if (e.type() == com.fileexplorer.service.ops.journal.OperationJournalRecordType.ITEM_SUCCESS) {
                try {
                    String s = e.fields().get("idx");
                    if (s != null) doneIdx.add(Integer.parseInt(s));
                } catch (Exception ignored) {
                }
            }
        }

        com.fileexplorer.service.ops.preview.OperationPlanSnapshot snap = journalService.reconstructSnapshot(operationId);
        if (snap == null || snap.actions() == null || snap.actions().isEmpty()) return 0;

        java.util.List<com.fileexplorer.service.ops.preview.OperationPlanItem> remaining = new java.util.ArrayList<>();
        int idx = 0;
        for (var it : snap.actions()) {
            if (it == null) { idx++; continue; }
            if (doneIdx.contains(idx)) { idx++; continue; }

            // Best-effort idempotency check: if destination already exists for copy/move/rename/overwrite, treat as done.
            try {
                java.nio.file.Path dst = it.destination();
                java.nio.file.Path src = it.source();
                var act = it.action();
                boolean dstExists = (dst != null) && java.nio.file.Files.exists(dst);
                boolean srcExists = (src != null) && java.nio.file.Files.exists(src);

                boolean treatDone = false;
                if (act != null) {
                    switch (act) {
                        case COPY, MOVE, OVERWRITE, RENAME -> treatDone = dstExists;
                        case DELETE -> treatDone = !srcExists;
                        default -> {}
                    }
                }

                if (!treatDone) {
                    remaining.add(it);
                }
            } catch (Exception ex) {
                remaining.add(it);
            }
            idx++;
        }

        if (remaining.isEmpty()) {
            journalService.writeComplete(operationId, "RECOVERED_ALREADY_COMPLETE", "");
            return 0;
        }

        // Create a trimmed snapshot used for deterministic replay.
        com.fileexplorer.service.ops.preview.PreviewCounts counts = new com.fileexplorer.service.ops.preview.PreviewCounts(
                remaining.size(), 0, 0, 0, 0, 0, 0, false, false, false
        );
        com.fileexplorer.service.ops.preview.OperationPlanSnapshot trimmed = new com.fileexplorer.service.ops.preview.OperationPlanSnapshot(
                snap.type(),
                snap.targetDirectory(),
                snap.policy(),
                counts,
                remaining,
                snap.conflicts(),
                java.util.List.of("Recovered remaining items from journal", "OriginalOperationId=" + operationId),
                null
        );

        FileOperationRequest req = new FileOperationRequest(
                trimmed.type(),
                trimmed.actions().stream().map(a -> a == null ? null : a.source()).filter(java.util.Objects::nonNull).toList(),
                trimmed.targetDirectory(),
                null,
                false,
                false,
                false
        );

        journalService.writeComplete(operationId, "RECOVERED", "");
        enqueue(req, "Recovery: " + operationId, null, null, trimmed, driftPolicyOverride);
        return 1;
    }





    /**
     * Phase 3.6.7.1: Discard recovered operations by clearing both the persisted queue file and the current in-memory
     * queue items that are not finished. Safe to call while paused.
     */


    /**
     * Phase 3.6.7.3: Resume only operations that were previously QUEUED. Any operations that were RUNNING when the
     * app last exited (marked during restore) will be removed from the recovered queue before resuming.
     *
     * Safe to call while paused.
     */
    public void resumeRecoveredQueuedOnly() {
        fx(() -> {
            // Remove only the recovered operations that were previously RUNNING.
            if (!recoveredRunningIds.isEmpty()) {
                queue.removeIf(h -> h != null && recoveredRunningIds.contains(h.id()));
                recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
                recoveredRunningCount.set(0);
            }
            paused.set(false);
            schedulePersist();
            pump();
            recomputeAggregateFx();
        });
    }

    /**
     * Phase 3.6.7.4: Resume all recovered operations, including those that were RUNNING at the last shutdown.
     * This is the explicit "unsafe" path and must be user-triggered.
     */
    public void resumeRecoveredAllIncludingRunning() {
        fx(() -> {
            recoveredRunningAllowed.set(true);
            paused.set(false);
            schedulePersist();
            pump();
            recomputeAggregateFx();
        });
    }
/**
 * discardRecoveredAndClearQueue.
 *
 */
    public void discardRecoveredAndClearQueue() {
        // Stop any future work and cancel running/queued operations cooperatively.
        cancelAll();
        persistence.clear();
        fx(() -> {
            // Remove any operations that are not finished.
            queue.removeIf(h -> {
                OperationStatus s = h.status.get();
                return s == OperationStatus.QUEUED || s == OperationStatus.RUNNING || s == OperationStatus.CANCELLED || s == OperationStatus.FAILED;
            });
            recoveredCount.set(0);
            recoveredRunningCount.set(0);
            recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
            recoveredRunningCount.set(0);
            recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
            paused.set(false);
            recomputeAggregateFx();
        });
    }
/**
 * enqueue.
 *
 * @param request TODO
 * @return TODO
 */
public OperationHandle enqueue(FileOperationRequest request) {
        return enqueue(request, null, null);
    }

    public OperationHandle enqueue(FileOperationRequest request, String historyLabelOverride) {
        return enqueue(request, historyLabelOverride, null);
    }

    /**
     * Enqueue an operation with optional history label override and optional commandId for linking.
     */
    public OperationHandle enqueue(FileOperationRequest request, String historyLabelOverride, String commandId) {
        return enqueue(request, historyLabelOverride, commandId, null);
    }

    /**
     * Phase 4.2.1: Enqueue an operation with an optional per-operation conflict policy override.
     *
     * <p>If {@code policyOverride} is non-null, it will be snapshotted into the handle and used by the
     * conflict policy engine for this operation only. Otherwise the current global policy snapshot is used.</p>
     */
    public OperationHandle enqueue(FileOperationRequest request, String historyLabelOverride, String commandId, ConflictPolicyConfig policyOverride) {
        return enqueue(request, historyLabelOverride, commandId, policyOverride, null);
    }

    /**
     * Phase 4.4.0: Enqueue an operation with an optional per-operation conflict policy override and optional
     * preview plan snapshot for deterministic execution.
     *
     * <p>If {@code planSnapshot} is non-null, it will be attached to the queue item and used during execution
     * (when compatible) to avoid re-evaluating conflicts and rename decisions at runtime.</p>
     */
    public OperationHandle enqueue(FileOperationRequest request, String historyLabelOverride, String commandId,
                                   ConflictPolicyConfig policyOverride, OperationPlanSnapshot planSnapshot) {
        return enqueue(request, historyLabelOverride, commandId, policyOverride, planSnapshot, null);
    }

    /**
     * Phase 4.4.1: Enqueue an operation with optional conflict policy override, optional preview snapshot,
     * and optional per-operation execution drift policy override.
     */
    public OperationHandle enqueue(FileOperationRequest request, String historyLabelOverride, String commandId,
                                   ConflictPolicyConfig policyOverride, OperationPlanSnapshot planSnapshot,
                                   ExecutionDriftPolicy driftPolicyOverride) {

        return enqueue(request, historyLabelOverride, commandId, policyOverride, planSnapshot, driftPolicyOverride, null);
    }

    /**
     * Phase 5.5.1: Enqueue an operation with optional origin/audit metadata (used by scheduled runs).
     */
    public OperationHandle enqueue(FileOperationRequest request, String historyLabelOverride, String commandId,
                                   ConflictPolicyConfig policyOverride, OperationPlanSnapshot planSnapshot,
                                   ExecutionDriftPolicy driftPolicyOverride,
                                   OperationOriginAudit originAudit) {

        Objects.requireNonNull(request, "request");
        String id = UUID.randomUUID().toString();
        String gid = null;
        if (batchTransactionMode.get()) {
            gid = activeGroupId.get();
            if (gid == null) {
                gid = java.util.UUID.randomUUID().toString();
                activeGroupId.set(gid);
            }
        }

        OperationItem item = new OperationItem(id, request, planSnapshot, driftPolicyOverride, gid, originAudit);

        ConflictPolicyConfig policyCfg = (policyOverride != null) ? policyOverride : conflictPolicyStore.snapshot();

        ExecutionDriftPolicy driftPolicy = (driftPolicyOverride != null) ? driftPolicyOverride : globalDriftPolicy.get();

        OperationHandleImpl handle = new OperationHandleImpl(item, this::scheduleAggregateRecompute, this::schedulePersist, commandId, policyCfg, conflictPolicyEngine, driftPolicy, journalService);
        if (historyLabelOverride != null && !historyLabelOverride.isBlank()) {
            handle.setHistoryLabelOverride(historyLabelOverride);
        }

        fx(() -> {
            queue.add(handle);
            schedulePersist();
            if (!batchTransactionMode.get()) {
                pump();
            }

        });

        return handle;
    }

    /**
     * Phase 6.0.0: Enqueue a recovered/persisted operation with a stable id (best-effort).
     *
     * <p>This improves restart reliability and provides a simple idempotency guard: if an id is already present
     * in the in-memory queue, a new id will be generated.</p>
     */
    private OperationHandle enqueueRecovered(OperationQueuePersistence.SavedOperation so) {
        if (so == null || so.request() == null) return null;

        String id = so.operationId();
        if (id == null || id.isBlank()) {
            id = java.util.UUID.randomUUID().toString();
        } else if (findById(id) != null) {
            // Defensive: avoid duplicates if the queue already contains this id.
            id = java.util.UUID.randomUUID().toString();
        }

        // Recovery items should never be part of an active atomic batch group.
        String gid = null;

        OperationOriginAudit originAudit = OperationOriginAudit.of("RECOVERY", "", "", "RESTORE", 0L, 0);

        OperationItem item = new OperationItem(id, so.request(), null, null, gid, originAudit);

        ConflictPolicyConfig policyCfg = conflictPolicyStore.snapshot();
        ExecutionDriftPolicy driftPolicy = globalDriftPolicy.get();

        OperationHandleImpl handle = new OperationHandleImpl(
                item,
                this::scheduleAggregateRecompute,
                this::schedulePersist,
                null,
                policyCfg,
                conflictPolicyEngine,
                driftPolicy,
                journalService
        );

        // Restore last-known status best-effort. Running operations are never auto-resumed; they require confirmation.
        OperationStatus persisted = so.status();
        boolean wasRunning = so.wasRunning() || persisted == OperationStatus.RUNNING;

        if (wasRunning) {
            handle.progress.set(new OperationProgress(0, 0, "Recovered (was running) — requires confirmation"));
            handle.setStatusFx(OperationStatus.QUEUED);
        } else if (persisted == OperationStatus.QUEUED || persisted == null) {
            handle.setStatusFx(OperationStatus.QUEUED);
        } else {
            // Any other in-progress-ish state is normalized back to QUEUED.
            handle.setStatusFx(OperationStatus.QUEUED);
        }

        fx(() -> {
            queue.add(handle);
            schedulePersist();
            // Don't auto-run recovered items; recovery always starts paused until user explicitly resumes.
            recomputeAggregateFx();
        });

        return handle;
    }

    /**
     * Enqueue an operation with a history label override.
     * <p>
     * This is primarily used by the Operation History "Retry" UX to ensure replayed
     * multi-source operations are grouped under a meaningful batch label.
     */
    
    public void clearFinished() {
        fx(() -> {
            queue.removeIf(h ->
                    h.status.get() == OperationStatus.COMPLETED
                            || h.status.get() == OperationStatus.FAILED
                            || h.status.get() == OperationStatus.CANCELLED
                            || h.status.get() == OperationStatus.SKIPPED
            );
            schedulePersist();
        });
    }



    /**
     * Cancel all queued and running operations.
     * - RUNNING operations receive a cooperative cancel request.
     * - QUEUED operations are marked CANCELLED immediately.
     */
    public void cancelAll() {
        fx(() -> {
            for (OperationHandleImpl h : queue) {
                OperationStatus st = h.status.get();
                if (st == OperationStatus.RUNNING) {
                    h.cancel();
                } else if (st == OperationStatus.QUEUED) {
                    h.cancelled.set(true);
                    h.status.set(OperationStatus.CANCELLED);
                    h.cancellable.set(false);
                    h.progress.set(new OperationProgress(0, 0, "Cancelled"));
                }
            }
            schedulePersist();
        });
    }


    /**
     * Remove an operation from the queue list.
     * Only allowed when the op is not RUNNING.
     */
    public boolean remove(OperationHandle handle) {
        if (!(handle instanceof OperationHandleImpl impl)) return false;
        if (impl.status.get() == OperationStatus.RUNNING) return false;

        fx(() -> {
            queue.remove(impl);
            schedulePersist();
        });
        return true;
    }

    /**
     * Re-enqueue a copy of the original request from a completed/failed/cancelled operation.
     */
    public OperationHandle retry(OperationHandle handle) {
        if (handle == null) return null;
        return enqueue(handle.item().request());
    }

/**
 * cancelActive.
 *
 */
    public void cancelActive() {
        OperationHandleImpl h = active.get();
        if (h != null) h.cancel();
    }

/**
 * pump.
 *
 */
    private void pump() {
        // Ensure pump runs on FX thread.
        if (!Platform.isFxApplicationThread()) {
            fx(this::pump);
            return;
        }

        // Coalesce pump calls to avoid churn when many ops complete quickly.
        if (!pumpScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            startRunnableOperationsFx();
        } finally {
            pumpScheduled.set(false);
        }
    }

/**
 * startRunnableOperationsFx.
 *
 */
    private void startRunnableOperationsFx() {
        // FX thread only.
                if (paused.get()) {
            return;
        }
while (activeOperations.size() < maxParallelism.get()) {
            OperationHandleImpl next = null;
            for (OperationHandleImpl h : queue) {
                if (h.status.get() == OperationStatus.QUEUED) {
                    // Phase 3.6.7.4: safer recovery - block previously RUNNING operations until explicitly allowed.
                    if (isRecoveredRunningBlocked(h.id())) {
                        continue;
                    }
                    next = h;
                    break;
                }
            }
            if (next == null) {
                break;
            }

            OperationHandleImpl handle = next;

            // If it was cancelled while queued, mark cancelled and keep going.
            if (handle.cancelled.get()) {
                handle.status.set(OperationStatus.CANCELLED);
                handle.cancellable.set(false);
                handle.progress.set(new OperationProgress(0, 0, "Cancelled"));
                continue;
            }

            handle.setStatusFx(OperationStatus.RUNNING);
            // Phase 5.1.0: group journal marker (best-effort)
            try {
                if (handle.item() != null && handle.item().operationGroupId() != null) {
                    java.util.Map<String, String> f = new java.util.HashMap<>();
                    f.put("groupId", handle.item().operationGroupId());
                    f.put("operationId", handle.id());
                    journalService.append(handle.item().operationGroupId(),
                            com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_OPERATION_START, f);
                }
            } catch (Throwable ignored) {}
            activeOperations.add(handle);
                recomputeAggregateFx();
            running.set(!activeOperations.isEmpty());
            active.set(handle); // most recently started

            
worker.submit(() -> {
    OperationStatus finalStatus = OperationStatus.FAILED;
    Throwable finalError = null;
    try {
        runOperation(handle);

        // Phase 3.9.4: If this was a multi-source batch, determine outcome from per-item results.
        if (handle.cancelled.get()) {
            finalStatus = OperationStatus.CANCELLED;
            handle.setStatusFx(OperationStatus.CANCELLED);
        } else if (handle.hasAnyPerItemFailure()) {
            finalStatus = OperationStatus.FAILED;
            attemptRollback(handle);
            handleGroupFailure(handle);
            handle.setStatusFx(OperationStatus.FAILED);
        } else {
            finalStatus = OperationStatus.COMPLETED;
            recordGroupCompletion(handle);
            handle.setStatusFx(OperationStatus.COMPLETED);
        }
    } catch (Throwable t) {
        finalError = t;
        handle.setErrorFx(t);
        finalStatus = OperationStatus.FAILED;
        handle.setStatusFx(OperationStatus.FAILED);
        attemptRollback(handle);
        handleGroupFailure(handle);
    } finally {
        recordHistory(handle, finalStatus, finalError);
        fx(() -> {
                        activeOperations.remove(handle);
                recomputeAggregateFx();
                        running.set(!activeOperations.isEmpty());

                        // Keep "active" pointing at some running op for existing UI.
                        if (active.get() == handle) {
                            OperationHandleImpl replacement = activeOperations.isEmpty() ? null : activeOperations.get(activeOperations.size() - 1);
                            active.set(replacement);
                        }

                        // Start more if capacity available.
                        pump();
                    });
                }
            });
        }
    }


    

/**
 * recordHistory.
 *
 * @param handle TODO
 * @param finalStatus TODO
 * @param error TODO
 */
private void recordHistory(OperationHandleImpl handle, OperationStatus finalStatus, Throwable error) {
    OperationHistoryService hs = this.historyService;
    if (hs == null || handle == null) return;

    fx(() -> {
        try {
            OperationProgress p = handle.progress.get();
            long processed = p == null ? 0L : p.processedUnits();
            long total = p == null ? 0L : p.totalUnits();

            FileOperationRequest req = handle.item.request();
            List<Path> sources = (req.sources() == null) ? List.of() : req.sources();

            String tgtSummary = req.targetDirectory() == null ? "" : req.targetDirectory().toString();

            java.time.Instant started = handle.startedAt;
            java.time.Instant ended = java.time.Instant.now();
            long durationMs = started == null ? 0L : java.time.Duration.between(started, ended).toMillis();

            boolean verifyOk = (finalStatus == OperationStatus.COMPLETED);
            String verifyMode = VERIFY_MODE == null ? "SIZE" : VERIFY_MODE.name();

            String msg = "";
            if (error != null) {
                msg = (error.getMessage() == null) ? error.toString() : error.getMessage();
            } else if (p != null && p.message() != null) {
                msg = p.message();
            // Phase 4.1.4: add conflict resolution summary (if any)
            try {
                String cs = handle == null ? "" : handle.conflictSummaryForHistory();
                if (cs != null && !cs.isBlank()) {
                    if (msg == null || msg.isBlank()) msg = cs;
                    else msg = msg + " | " + cs;
                }
            } catch (Exception ignored) { }


            // Phase 4.4.0: snapshot execution metadata (best-effort)
            try {
                if (handle != null) {
                    String ph = handle.previewHash;
                    long drift = handle.driftCount.get();
                    if ((ph != null && !ph.isBlank()) || handle.executedFromSnapshot || drift > 0) {
                        String meta = "PreviewHash=" + (ph == null ? "" : ph)
                                + " ExecutedFromSnapshot=" + handle.executedFromSnapshot
                                + " DriftPolicy=" + (handle.driftPolicy == null ? "" : handle.driftPolicy.name())
                                + " DriftEvents=" + drift
                                + " ReplayIntegrity=" + (handle.replayIntegrity == null ? "" : handle.replayIntegrity.name());
                        if (msg == null || msg.isBlank()) msg = meta;
                        else msg = msg + " | " + meta;
                    }
                }
            } catch (Exception ignored) { }

            }

            // Phase 3.9.2: wire real batching for multi-source operations.
            if (sources.size() > 1 && (req.type() == FileOperationType.COPY || req.type() == FileOperationType.MOVE || req.type() == FileOperationType.DELETE)) {
                String label = handle.historyLabelOverride();
                if (label == null || label.isBlank()) {
                    if (req.type() == FileOperationType.DELETE) {
                        label = "Delete " + sources.size() + " items" + (req.sendToTrash() ? " (Recycle Bin)" : "");
                    } else {
                        String verb = req.type() == null ? "Op" : (req.type().name().charAt(0) + req.type().name().substring(1).toLowerCase());
                        label = verb + " " + sources.size() + " items → " + (tgtSummary.isBlank() ? "(no target)" : tgtSummary);
                    }
                }

                List<OperationHistoryEntry> batch = new ArrayList<>(sources.size());
                for (int i = 0; i < sources.size(); i++) {
    Path src = sources.get(i);
    if (src == null) continue;

    String opId = handle.id() + ":" + (i + 1);
    String srcStr = src.toString();

    // Phase 3.9.4: true per-item execution results.
    // If available, use the per-item result captured by the operation runner.
    ItemResult r = handle.getPerItemResult(srcStr);

    OperationStatus itemStatus;
    String itemMsg;
    boolean itemVerifyOk;

    if (r != null && r.status() != null) {
        itemStatus = r.status();
        itemMsg = (r.message() == null) ? "" : r.message();
        itemVerifyOk = (itemStatus == OperationStatus.COMPLETED);
    } else {
        // Fallback: if no per-item result, infer conservatively.
        itemStatus = finalStatus;
        itemMsg = msg;
        itemVerifyOk = verifyOk;
        if (finalStatus == OperationStatus.FAILED && i > 0) {
            itemStatus = OperationStatus.SKIPPED;
            itemMsg = "Skipped (no per-item result)";
            itemVerifyOk = false;
        }
        if (finalStatus == OperationStatus.CANCELLED && i > 0) {
            itemStatus = OperationStatus.SKIPPED;
            itemMsg = "Skipped due to cancellation";
            itemVerifyOk = false;
        }
    }

    OperationOriginAudit oa = handle.item.originAudit();
    batch.add(new OperationHistoryEntry(

            opId,
            req.type(),
            itemStatus,
            started,
            ended,
            durationMs,
            processed,
            total,
            srcStr,
            tgtSummary,
            verifyMode,
            itemVerifyOk,
            itemMsg,

            // request reconstruction per-item
            List.of(srcStr),
            req.targetDirectory() == null ? "" : req.targetDirectory().toString(),
            req.newName() == null ? "" : req.newName(),
            req.overwrite(),
            req.sendToTrash(),

            // origin/audit metadata (best-effort)
            oa == null ? "" : oa.originType(),
            oa == null ? "" : oa.templateId(),
            oa == null ? "" : oa.scheduleId(),
            oa == null ? "" : oa.triggerType(),
            oa == null ? 0L : oa.recurrenceMinutes(),
            oa == null ? 0 : oa.retryAttempt(),

            // batch metadata is enriched by OperationHistoryService.addBatch(...)
            "",
            "",
            0,
            1,
            handle.commandId
    ));
}

                // entries are already oldest-first in 'sources' order.
                hs.addBatch(batch, label);
                return;
            }

            // Single-entry history record (includes single-source operations and rename).
            String srcSummary = "";
            if (sources != null) {
                int n = sources.size();
                if (n == 1) srcSummary = sources.get(0) == null ? "" : sources.get(0).toString();
                else if (n > 1) srcSummary = n + " items";
            }

            OperationOriginAudit oa = handle.item.originAudit();
            hs.add(new OperationHistoryEntry(
                    handle.id(),
                    req.type(),
                    finalStatus,
                    started,
                    ended,
                    durationMs,
                    processed,
                    total,
                    srcSummary,
                    tgtSummary,
                    verifyMode,
                    verifyOk,
                    msg,
                    sources.stream().map(pth -> pth == null ? "" : pth.toString()).toList(),
                    req.targetDirectory() == null ? "" : req.targetDirectory().toString(),
                    req.newName() == null ? "" : req.newName(),
                    req.overwrite(),
                    req.sendToTrash(),

                    // origin/audit metadata (best-effort)
                    oa == null ? "" : oa.originType(),
                    oa == null ? "" : oa.templateId(),
                    oa == null ? "" : oa.scheduleId(),
                    oa == null ? "" : oa.triggerType(),
                    oa == null ? 0L : oa.recurrenceMinutes(),
                    oa == null ? 0 : oa.retryAttempt(),

                    "",
                    "",
                    0,
                    1,
                    handle.commandId == null ? "" : handle.commandId
            ));
        } catch (Throwable ignored) {
            // best effort
        }
    });
}

/**
 * runOperation.
 *
 * @param handle TODO
 */
private void runOperation(OperationHandleImpl handle) throws IOException {
        FileOperationRequest req = handle.item.request();

        // Phase 4.4.0: deterministic execution from preview snapshot when available.
        OperationPlanSnapshot snap = null;
        try {
            snap = handle.item.planSnapshot();
        } catch (Exception ignored) { }
        if (snap != null && snap.actions() != null && !snap.actions().isEmpty()) {
            runOperationFromSnapshot(handle, req, snap);
            return;
        }

        List<Path> sources = req.sources() == null ? List.of() : req.sources();
        if (sources.isEmpty()) {
            handle.setProgressFx(new OperationProgress(0, 0, "No sources."));
            return;
        }

        // Estimate total units (files + directories) for better progress feel.
        long total = estimateTotalUnits(sources, handle);
        if (total <= 0) total = sources.size();

        AtomicLong processed = new AtomicLong(0);
        handle.setProgressFx(new OperationProgress(0, total, "Starting…"));

        switch (req.type()) {
            case COPY -> {
                if (sources.size() > 1) {
                    copyManyWithResults(sources, req.targetDirectory(), req.overwrite(), handle, processed, total);
                } else {
                    copyMany(sources, req.targetDirectory(), req.overwrite(), handle, processed, total);
                }
            }
            case MOVE -> {
                if (sources.size() > 1) {
                    moveManyWithResults(sources, req.targetDirectory(), req.overwrite(), handle, processed, total);
                } else {
                    moveMany(sources, req.targetDirectory(), req.overwrite(), handle, processed, total);
                }
            }
            case DELETE -> {
                if (sources.size() > 1) {
                    deleteManyWithResults(sources, req.sendToTrash(), handle, processed, total);
                } else {
                    deleteMany(sources, req.sendToTrash(), handle, processed, total);
                }
            }
            case RENAME -> renameOne(sources, req.newName(), req.overwrite(), handle, processed, total);
            default -> throw new IOException("Unsupported FileOperationType: " + req.type());
        }
    }


    /**
     * Phase 4.4.0: Execute deterministically from a preview plan snapshot.
     *
     * <p>This is best-effort deterministic: if runtime state drifts from the snapshot, we record drift and
     * apply the existing conflict resolution flow (policy/queue) where necessary.</p>
     */
    private void runOperationFromSnapshot(OperationHandleImpl handle, FileOperationRequest req, OperationPlanSnapshot snap) throws IOException {
        handle.executedFromSnapshot = true;
        if (snap.previewHash() != null && !snap.previewHash().isBlank()) {
            handle.previewHash = snap.previewHash();
        }

        List<OperationPlanItem> actions = (snap.actions() == null) ? List.of() : snap.actions();
        if (actions.isEmpty()) {
            handle.setProgressFx(new OperationProgress(0, 0, "No planned actions."));
            return;
        }

        long total = actions.size();
        AtomicLong processed = new AtomicLong(0);
        handle.setProgressFx(new OperationProgress(0, total, "Starting (snapshot)…"));

        // Phase 4.5.0: write-ahead journal for crash recovery (best-effort).
        try {
            journalService.writeOperationStart(handle.id(), snap, handle.driftPolicy());
        } catch (Throwable ignored) { }

        switch (req.type()) {
            case COPY -> executeSnapshotCopyMove(handle, req, actions, false, processed, total);
            case MOVE -> executeSnapshotCopyMove(handle, req, actions, true, processed, total);
            case DELETE -> executeSnapshotDelete(handle, req, actions, processed, total);
            case RENAME -> executeSnapshotRename(handle, req, actions, processed, total);
            default -> throw new IOException("Unsupported FileOperationType (snapshot): " + req.type());
        }

        computeReplayIntegrity(handle, snap);

        // Phase 4.5.0: close the journal (best-effort).
        try {
            String st = handle.cancelled.get() ? "CANCELLED" : "DONE";
            String ri = (handle.replayIntegrity() == null) ? "" : handle.replayIntegrity().name();
            journalService.writeComplete(handle.id(), st, ri);
        } catch (Throwable ignored) { }
        handle.setProgressFx(new OperationProgress(processed.get(), total, "Completed (snapshot)"));
    }


/**
 * computeReplayIntegrity.
 *
 * @param handle TODO
 * @param snap TODO
 */
    private void computeReplayIntegrity(OperationHandleImpl handle, com.fileexplorer.service.ops.preview.OperationPlanSnapshot snap) {
        if (handle == null || snap == null || snap.counts() == null) {
            return;
        }
        try {
            long plannedOverwrite = snap.counts().overwritePlanned();
            long plannedRename = snap.counts().renamePlanned();
            long plannedSkip = snap.counts().skipPlanned();
            long plannedEsc = snap.counts().escalations();

            long execOverwrite = handle.executedOverwriteCount.get();
            long execRename = handle.executedRenameCount.get();
            long execSkip = handle.executedSkipCount.get();
            long execEsc = handle.executedEscalateCount.get();

            boolean ok = (plannedOverwrite == execOverwrite)
                    && (plannedRename == execRename)
                    && (plannedSkip == execSkip)
                    && (plannedEsc == execEsc);

            handle.replayIntegrity = ok ? ReplayIntegrity.PASS : ReplayIntegrity.WARN;
        } catch (Exception e) {
            handle.replayIntegrity = ReplayIntegrity.WARN;
        }
    }


    // Phase 5.0.1: decide whether to rollback on failure.
/**
 * shouldRollbackOnFailure.
 *
 * @param handle TODO
 * @return TODO
 */
    private boolean shouldRollbackOnFailure(OperationHandleImpl handle) {
        RollbackMode mode = getRollbackMode();
        if (mode == RollbackMode.ALWAYS) return true;
        if (mode == RollbackMode.NEVER) return false;
        return promptRollbackDecisionFx(handle);
    }

/**
 * promptRollbackDecisionFx.
 *
 * @param handle TODO
 * @return TODO
 */
    private static boolean promptRollbackDecisionFx(OperationHandleImpl handle) {
        try {
            ROLLBACK_DIALOG_SEMAPHORE.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }

        AtomicReference<Boolean> out = new AtomicReference<>(Boolean.FALSE);
        CountDownLatch latch = new CountDownLatch(1);

        fx(() -> {
            try {
                String title = "Operation failed";
                String header = "Rollback completed changes?";

                String msg = "This operation failed after applying some changes.";
                if (handle != null) {
                    int steps = (handle.rollbackSteps() == null) ? 0 : handle.rollbackSteps().size();
                    msg += "\nRollback steps available: " + steps;
                }

                ButtonType rollbackBtn = new ButtonType("Rollback now", ButtonBar.ButtonData.OK_DONE);
                ButtonType keepBtn = new ButtonType("Keep changes", ButtonBar.ButtonData.CANCEL_CLOSE);

                Dialog<ButtonType> dialog = new Dialog<>();
                dialog.setTitle(title);
                dialog.setHeaderText(header);

                Label lbl = new Label(msg);
                lbl.setWrapText(true);
                VBox box = new VBox(10, lbl);
                box.setPadding(new Insets(10));
                dialog.getDialogPane().setContent(box);

                dialog.getDialogPane().getButtonTypes().setAll(rollbackBtn, keepBtn);

                Optional<ButtonType> res = dialog.showAndWait();
                out.set(res.isPresent() && res.get() == rollbackBtn);
            } catch (Throwable ignored) {
                out.set(Boolean.FALSE);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            ROLLBACK_DIALOG_SEMAPHORE.release();
        }

        return Boolean.TRUE.equals(out.get());
    }
    // Phase 5.0.0: attempt best-effort rollback for snapshot execution failures.
/**
 * attemptRollback.
 *
 * @param handle TODO
 */
    private void attemptRollback(OperationHandleImpl handle) {
        if (handle == null) return;
        if (!isRollbackEnabled()) return;
        if (!shouldRollbackOnFailure(handle)) {
            // ASK mode declined by user.
            handle.rollbackAttempted = true;
            handle.rollbackOk = false;
            try { handle.recordDrift("Rollback skipped by user"); } catch (Throwable ignored) {}
            return;
        }
        if (handle.cancelled.get()) return;
        if (!handle.executedFromSnapshot) return;
        if (handle.rollbackAttempted) return;

        handle.rollbackAttempted = true;
        boolean ok = false;
        try {
            handle.setProgressFx(new OperationProgress(0, 0, "Rolling back…"));
        } catch (Throwable ignored) {}
        try {
            ok = rollbackService.rollback(handle, handle.rollbackSteps());
        } catch (Throwable ignored) {
            ok = false;
        }
        handle.rollbackOk = ok;
        try {
            if (ok) handle.recordDrift("Rollback completed (best-effort)");
            else handle.recordDrift("Rollback attempted with failures (best-effort)");
        } catch (Throwable ignored) {}
    }


    // Phase 5.1.0: group transaction bookkeeping / rollback (best-effort)
/**
 * recordGroupCompletion.
 *
 * @param handle TODO
 */
    private void recordGroupCompletion(OperationHandleImpl handle) {
        if (handle == null || handle.item() == null) return;
        String gid = handle.item().operationGroupId();
        if (gid == null || gid.isBlank()) return;

        completedByGroup.computeIfAbsent(gid, k -> new java.util.ArrayDeque<>()).addLast(handle);

        try {
            java.util.Map<String, String> f = new java.util.HashMap<>();
            f.put("groupId", gid);
            f.put("operationId", handle.id());
            f.put("status", "COMPLETED");
            journalService.append(gid, com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_OPERATION_COMPLETE, f);
        } catch (Throwable ignored) {}
    }

/**
 * handleGroupFailure.
 *
 * @param failingHandle TODO
 */
    private void handleGroupFailure(OperationHandleImpl failingHandle) {
        if (failingHandle == null || failingHandle.item() == null) return;
        String gid = failingHandle.item().operationGroupId();
        if (gid == null || gid.isBlank()) return;

        // Ensure we only trigger group rollback once.
        java.util.concurrent.atomic.AtomicBoolean flag = groupRollbackTriggered.computeIfAbsent(gid, k -> new java.util.concurrent.atomic.AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) return;

        if (!isRollbackEnabled()) {
            try {
                java.util.Map<String, String> f = new java.util.HashMap<>();
                f.put("groupId", gid);
                f.put("result", "FAILED_NO_ROLLBACK");
                journalService.append(gid, com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_COMPLETE, f);
            } catch (Throwable ignored) {}
            return;
        }

        // Respect ASK/ALWAYS/NEVER using the failing handle as the prompt context.
        if (!shouldRollbackOnFailure(failingHandle)) {
            try {
                java.util.Map<String, String> f = new java.util.HashMap<>();
                f.put("groupId", gid);
                f.put("result", "FAILED_USER_DECLINED");
                journalService.append(gid, com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_COMPLETE, f);
            } catch (Throwable ignored) {}
            return;
        }

        java.util.Deque<OperationHandleImpl> done = completedByGroup.getOrDefault(gid, new java.util.ArrayDeque<>());

        try {
            java.util.Map<String, String> f = new java.util.HashMap<>();
            f.put("groupId", gid);
            f.put("failedOperationId", failingHandle.id());
            f.put("completedCount", String.valueOf(done.size()));
            journalService.append(gid, com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_ROLLBACK_START, f);
        } catch (Throwable ignored) {}

        boolean allOk = true;
        java.util.List<OperationHandleImpl> rev = new java.util.ArrayList<>(done);
        java.util.Collections.reverse(rev);

        for (OperationHandleImpl h : rev) {
            boolean ok = false;
            try {
                if (h != null && h.executedFromSnapshot && h.rollbackSteps() != null && !h.rollbackSteps().isEmpty()) {
                    ok = rollbackService.rollback(h, h.rollbackSteps());
                } else {
                    ok = true; // nothing to do
                }
            } catch (Throwable ignored) {
                ok = false;
            }
            allOk &= ok;

            try {
                java.util.Map<String, String> f = new java.util.HashMap<>();
                f.put("groupId", gid);
                f.put("operationId", (h == null) ? "" : h.id());
                f.put("ok", String.valueOf(ok));
                journalService.append(gid,
                        ok ? com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_ROLLBACK_ITEM_OK
                           : com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_ROLLBACK_ITEM_FAIL,
                        f);
            } catch (Throwable ignored) {}
        }

        try {
            java.util.Map<String, String> f = new java.util.HashMap<>();
            f.put("groupId", gid);
            f.put("result", allOk ? "ROLLED_BACK" : "ROLLBACK_PARTIAL");
            journalService.append(gid, com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_ROLLBACK_COMPLETE, f);
            journalService.append(gid, com.fileexplorer.service.ops.journal.OperationJournalRecordType.GROUP_COMPLETE, f);
        } catch (Throwable ignored) {}
    }


    // Phase 5.0.0: rollback filesystem helpers
/**
 * rollbackBaseDir.
 *
 * @param handle TODO
 * @return TODO
 */
    private java.nio.file.Path rollbackBaseDir(OperationHandleImpl handle) {
        return java.nio.file.Paths.get(System.getProperty("user.home"), ".fileexplorer", "rollback", handle.id());
    }

    private java.nio.file.Path createBackupPath(OperationHandleImpl handle, java.nio.file.Path dest) throws java.io.IOException {
        java.nio.file.Path base = rollbackBaseDir(handle).resolve("backup");
        java.nio.file.Files.createDirectories(base);
        String name = (dest == null || dest.getFileName() == null) ? "backup" : dest.getFileName().toString();
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return base.resolve(safe + "_" + System.nanoTime());
    }



    private void executeSnapshotCopyMove(OperationHandleImpl handle, FileOperationRequest req, List<OperationPlanItem> plan,
                                        boolean isMove, AtomicLong processed, long total) throws IOException {
        Path targetDir = req.targetDirectory();
        requireTargetDir(targetDir, isMove ? "MOVE" : "COPY");

        ExecutionDriftPolicy dp = handle.driftPolicy();
        boolean anyFailed = false;

        for (OperationPlanItem it : plan) {
            if (handle.cancelled.get()) return;

            Path src = it.source();
            Path dst = it.destination();

            String srcKey = (src == null) ? "" : src.toString();

            try {
                if (src == null) {
                    handle.recordDrift("Plan item missing source");
                    inc(handle, processed, total, "Skipped: (null source)");
                    if (!srcKey.isBlank()) handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Null source");
                    continue;
                }

                if (!Files.exists(src)) {
                    handle.recordDrift("Source missing at execution: " + src);
                    if (dp == ExecutionDriftPolicy.FAIL_FAST) {
                        throw new IOException("Drift (FAIL_FAST): source missing at execution: " + src);
                    }
                    if (dp == ExecutionDriftPolicy.REPLAN_REQUIRED) {
                        throw new IOException("Drift (REPLAN_REQUIRED): source missing at execution: " + src);
                    }
                    // SKIP_AFFECTED or ESCALATE_TO_QUEUE (not resolvable) => skip
                    handle.executedSkipCount.incrementAndGet();
                    inc(handle, processed, total, "Missing: " + safeName(src));
                    handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Missing source at execution");
                    continue;
                }

                // Determine destination; if null, default to targetDir + filename.
                if (dst == null) {
                    dst = targetDir.resolve(src.getFileName() != null ? src.getFileName().toString() : "item");
                }

                OperationPlanAction a = it.action();
                if (a == null) a = isMove ? OperationPlanAction.MOVE : OperationPlanAction.COPY;

                // Drift: destination exists unexpectedly for non-conflict actions.
                if ((a == OperationPlanAction.COPY || a == OperationPlanAction.MOVE) && Files.exists(dst) && !req.overwrite()) {
                    handle.recordDrift("Destination newly exists at execution: " + dst);
                    if (dp == ExecutionDriftPolicy.FAIL_FAST) {
                        throw new IOException("Drift (FAIL_FAST): destination newly exists at execution: " + dst);
                    }
                    if (dp == ExecutionDriftPolicy.REPLAN_REQUIRED) {
                        throw new IOException("Drift (REPLAN_REQUIRED): destination newly exists at execution: " + dst);
                    }
                    if (dp == ExecutionDriftPolicy.SKIP_AFFECTED) {
                        handle.executedSkipCount.incrementAndGet();
                        inc(handle, processed, total, "Skipped (drift exists): " + safeName(src));
                        handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Skipped due to drift (dest exists)");
                        continue;
                    }
                    // ESCALATE_TO_QUEUE => resolve interactively

                    handle.recordDrift("Destination newly exists at execution: " + dst);
                    ResolvedDestination rd = resolveWithConflict(dst.getParent(), dst.getFileName().toString(), req.overwrite(), handle, src);
                    handle.executedEscalateCount.incrementAndGet();
                    if (rd == null || rd.path() == null) {
                        handle.executedSkipCount.incrementAndGet();
                        inc(handle, processed, total, "Skipped (drift conflict): " + safeName(src));
                        handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Skipped due to drift conflict");
                        continue;
                    }
                    // Count actual decision best-effort
                    if (rd.overwrite()) handle.executedOverwriteCount.incrementAndGet();
                    else if (rd.path() != null && !rd.path().equals(dst)) handle.executedRenameCount.incrementAndGet();

                    dst = rd.path();
                    // overwrite flag follows resolution
                    boolean ow = rd.overwrite();
                    if (Files.isDirectory(src)) {
                        if (ow && Files.exists(dst) && Files.isDirectory(dst)) {
                            deleteDirectoryRecursive(dst, handle, processed, total);
                            if (handle.cancelled.get()) return;
                        }
                        copyDirectoryRecursive(src, dst, ow, handle, processed, total);
                        if (isMove && !handle.cancelled.get()) {
                            deleteDirectoryRecursive(src, handle, processed, total);
                        }
                    } else {
                            java.nio.file.Path backup = null;
                            if (isRollbackEnabled() && Files.exists(dst) && !Files.isDirectory(dst)) {
                                try {
                                    backup = createBackupPath(handle, dst);
                                    Files.move(dst, backup, REPLACE_EXISTING);
                                    handle.rollbackSteps().addLast(RollbackStep.restoreBackup(backup, dst, "restore overwritten destination"));
                                } catch (Exception ignored) {
                                    backup = null;
                                }
                            }
                            copyFileWithProgress(src, dst, ow, handle, processed, total, isMove ? "Moving" : "Copying");
                        if (isMove && !handle.cancelled.get()) {
                            Files.deleteIfExists(src);
                            inc(handle, processed, total, "Deleted: " + safeName(src));
                        }
                    }
                    handle.setPerItemResult(srcKey, OperationStatus.COMPLETED, "OK (drift resolved)");
                    continue;
                }

                switch (a) {
                    case SKIP -> {
                        handle.executedSkipCount.incrementAndGet();
                        inc(handle, processed, total, "Skipped: " + safeName(src));
                        handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Skipped (plan)");
                    }
                    case ESCALATE -> {
                        // Force prompt/policy evaluation now.
                        handle.executedEscalateCount.incrementAndGet();
                        handle.recordDrift("Escalated item executed interactively: " + dst);
                        ResolvedDestination rd = resolveWithConflict(dst.getParent(), dst.getFileName().toString(), req.overwrite(), handle, src);
                    handle.executedEscalateCount.incrementAndGet();
                        if (rd == null || rd.path() == null) {
                            inc(handle, processed, total, "Skipped (escalation): " + safeName(src));
                            handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Skipped (escalation)");
                            continue;
                        }
                        Path resolved = rd.path();
                        boolean ow = rd.overwrite();
                        if (Files.isDirectory(src)) {
                            if (ow && Files.exists(resolved) && Files.isDirectory(resolved)) {
                                deleteDirectoryRecursive(resolved, handle, processed, total);
                                if (handle.cancelled.get()) return;
                            }
                            copyDirectoryRecursive(src, resolved, ow, handle, processed, total);
                            if (isMove && !handle.cancelled.get()) {
                                deleteDirectoryRecursive(src, handle, processed, total);
                            }
                        } else {
                            copyFileWithProgress(src, resolved, ow, handle, processed, total, isMove ? "Moving" : "Copying");
                            if (isMove && !handle.cancelled.get()) {
                                Files.deleteIfExists(src);
                                inc(handle, processed, total, "Deleted: " + safeName(src));
                            }
                        }
                        handle.setPerItemResult(srcKey, OperationStatus.COMPLETED, "OK (escalated)");
                    }
                    case RENAME -> {
                        // dst already computed uniquely in snapshot.
                        handle.executedRenameCount.incrementAndGet();
                        if (Files.isDirectory(src)) {
                            copyDirectoryRecursive(src, dst, false, handle, processed, total);
                            if (isMove && !handle.cancelled.get()) {
                                deleteDirectoryRecursive(src, handle, processed, total);
                            }
                        } else {
                            copyFileWithProgress(src, dst, false, handle, processed, total, isMove ? "Moving" : "Copying");
                            if (isMove && !handle.cancelled.get()) {
                                Files.deleteIfExists(src);
                                inc(handle, processed, total, "Deleted: " + safeName(src));
                            }
                        }
                        handle.setPerItemResult(srcKey, OperationStatus.COMPLETED, "OK (rename)");
                    }
                    case OVERWRITE -> {
                        handle.executedOverwriteCount.incrementAndGet();
                        boolean ow = true;
                        if (Files.isDirectory(src)) {
                            if (Files.exists(dst) && Files.isDirectory(dst)) {
                                deleteDirectoryRecursive(dst, handle, processed, total);
                                if (handle.cancelled.get()) return;
                            }
                            copyDirectoryRecursive(src, dst, true, handle, processed, total);
                            if (isMove && !handle.cancelled.get()) {
                                deleteDirectoryRecursive(src, handle, processed, total);
                            }
                        } else {
                            copyFileWithProgress(src, dst, ow, handle, processed, total, isMove ? "Moving" : "Copying");
                            if (isMove && !handle.cancelled.get()) {
                                Files.deleteIfExists(src);
                                inc(handle, processed, total, "Deleted: " + safeName(src));
                            }
                        }
                        handle.setPerItemResult(srcKey, OperationStatus.COMPLETED, "OK (overwrite)");
                    }
                    case COPY, MOVE, DELETE -> {
                        // Treat as nominal copy/move
                        boolean ow = req.overwrite();
                        if (Files.isDirectory(src)) {
                            if (ow && Files.exists(dst) && Files.isDirectory(dst)) {
                                deleteDirectoryRecursive(dst, handle, processed, total);
                                if (handle.cancelled.get()) return;
                            }
                            copyDirectoryRecursive(src, dst, ow, handle, processed, total);
                            if (isMove && !handle.cancelled.get()) {
                                deleteDirectoryRecursive(src, handle, processed, total);
                            }
                        } else {
                            copyFileWithProgress(src, dst, ow, handle, processed, total, isMove ? "Moving" : "Copying");
                            if (isMove && !handle.cancelled.get()) {
                                Files.deleteIfExists(src);
                                inc(handle, processed, total, "Deleted: " + safeName(src));
                            }
                        }
                        handle.setPerItemResult(srcKey, OperationStatus.COMPLETED, "OK");
                        if (isRollbackEnabled()) {
                            try {
                                handle.rollbackSteps().addLast(isMove ? RollbackStep.moveBack(dst, src, "move back") : RollbackStep.deleteCreated(dst, "delete created"));
                            } catch (Exception ignored) { }
                        }
                    }
                }
            } catch (Throwable ex) {
                anyFailed = true;
                String msg = ex.getMessage();
                if (msg == null || msg.isBlank()) msg = ex.getClass().getSimpleName();
                if (!srcKey.isBlank()) handle.setPerItemResult(srcKey, OperationStatus.FAILED, msg);
            }
        }

        if (anyFailed && !handle.cancelled.get()) {
            throw new IOException("One or more items failed during " + (isMove ? "MOVE" : "COPY") + " (snapshot).");
        }
    }

    private void executeSnapshotDelete(OperationHandleImpl handle, FileOperationRequest req, List<OperationPlanItem> plan,
                                       AtomicLong processed, long total) throws IOException {
        ExecutionDriftPolicy dp = handle.driftPolicy();
        boolean anyFailed = false;

        for (OperationPlanItem it : plan) {
            if (handle.cancelled.get()) return;

            Path src = it.source();
            if (src == null) {
                handle.recordDrift("Delete plan item missing source");
                inc(handle, processed, total, "Skipped: (null source)");
                continue;
            }
            String srcKey = src.toString();

            try {
                if (!Files.exists(src)) {
                    handle.recordDrift("Source missing at delete execution: " + src);
                    inc(handle, processed, total, "Missing: " + safeName(src));
                    handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Missing source at execution");
                    continue;
                }

                OperationPlanAction a = it.action();
                if (a == OperationPlanAction.SKIP) {
                    inc(handle, processed, total, "Skipped: " + safeName(src));
                    handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Skipped (plan)");
                    continue;
                }

                // Use existing delete implementation so recycle bin wiring remains correct.
                deleteOne(src, req.sendToTrash(), handle, processed, total);
                handle.setPerItemResult(srcKey, OperationStatus.COMPLETED, "OK");
            } catch (Throwable ex) {
                anyFailed = true;
                String msg = ex.getMessage();
                if (msg == null || msg.isBlank()) msg = ex.getClass().getSimpleName();
                handle.setPerItemResult(srcKey, OperationStatus.FAILED, msg);
            }
        }

        if (anyFailed && !handle.cancelled.get()) {
            throw new IOException("One or more items failed during DELETE (snapshot).");
        }
    }

    private void executeSnapshotRename(OperationHandleImpl handle, FileOperationRequest req, List<OperationPlanItem> plan,
                                       AtomicLong processed, long total) throws IOException {
        if (plan.isEmpty()) return;

        OperationPlanItem it = plan.get(0);
        Path src = it.source();
        Path dst = it.destination();

        if (src == null) {
            handle.recordDrift("Rename plan missing source");
            return;
        }
        if (dst == null) {
            // fallback to request's newName if available
            Path parent = src.getParent();
            String newName = req.newName();
            if (parent != null && newName != null && !newName.isBlank()) {
                dst = parent.resolve(newName);
            }
        }
        if (dst == null) {
            handle.recordDrift("Rename plan missing destination");
            return;
        }

        if (!Files.exists(src)) {
            handle.recordDrift("Source missing at rename execution: " + src);
            handle.setPerItemResult(src.toString(), OperationStatus.SKIPPED, "Missing source at execution");
            return;
        }

        boolean overwrite = (it.action() == OperationPlanAction.OVERWRITE) || req.overwrite();
        try {
            if (overwrite) {
                Files.move(src, dst, REPLACE_EXISTING);
            } else {
                Files.move(src, dst);
            }
            inc(handle, processed, total, "Renamed: " + safeName(src) + " -> " + safeName(dst));
            handle.setPerItemResult(src.toString(), OperationStatus.COMPLETED, "OK");
        } catch (IOException ex) {
            throw ex;
        }
    }

/**
 * estimateTotalUnits.
 *
 * @param sources TODO
 * @param handle TODO
 * @return TODO
 */
    private static long estimateTotalUnits(List<Path> sources, OperationHandleImpl handle) {
        FileOperationType t = handle.item.request().type();
        boolean byteMode = (t == FileOperationType.COPY || t == FileOperationType.MOVE);

        AtomicLong total = new AtomicLong(0);

        for (Path src : sources) {
            if (handle.cancelled.get()) return total.get();
            if (src == null) continue;

            try {
                if (!Files.exists(src)) continue;

                if (byteMode) {
                    total.addAndGet(estimateBytesForPath(src, handle));
                } else {
                    // Item-based estimate: count files + dirs touched
                    if (Files.isDirectory(src)) {
                        Files.walkFileTree(src, new SimpleFileVisitor<>() {
                            @Override
/**
 * preVisitDirectory.
 *
 * @param dir TODO
 * @param attrs TODO
 * @return TODO
 */
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                if (handle.cancelled.get()) return FileVisitResult.TERMINATE;
                                total.incrementAndGet();
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
/**
 * visitFile.
 *
 * @param file TODO
 * @param attrs TODO
 * @return TODO
 */
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (handle.cancelled.get()) return FileVisitResult.TERMINATE;
                                total.incrementAndGet();
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } else {
                        total.incrementAndGet();
                    }
                }
            } catch (IOException ignored) {
                // best-effort estimate
                total.incrementAndGet();
            }
        }

        return total.get();
    }

/**
 * estimateBytesForPath.
 *
 * @param src TODO
 * @param handle TODO
 * @return TODO
 */
    private static long estimateBytesForPath(Path src, OperationHandleImpl handle) {
        if (src == null) return 0L;
        try {
            if (!Files.exists(src)) return 0L;
            if (!Files.isDirectory(src)) {
                return Math.max(0L, Files.size(src));
            }

            AtomicLong bytes = new AtomicLong(0);
            Files.walkFileTree(src, new SimpleFileVisitor<>() {
                @Override
/**
 * visitFile.
 *
 * @param file TODO
 * @param attrs TODO
 * @return TODO
 */
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (handle.cancelled.get()) return FileVisitResult.TERMINATE;
                    try {
                        bytes.addAndGet(Math.max(0L, Files.size(file)));
                    } catch (IOException ignored) {
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return bytes.get();
        } catch (IOException ignored) {
            return 0L;
        }
    }


    private static void copyMany(List<Path> sources, Path targetDir, boolean overwrite,
                                 OperationHandleImpl handle, AtomicLong processed, long total) throws IOException {
        requireTargetDir(targetDir, "COPY");
        for (Path src : sources) {
            if (handle.cancelled.get()) return;
            if (src == null) continue;

            ResolvedDestination rd = resolveWithConflict(targetDir,
                    src.getFileName() != null ? src.getFileName().toString() : "item",
                    overwrite, handle, src);
            if (rd == null || rd.path() == null) {
                continue;
            }
            Path dst = rd.path();
            boolean ow = rd.overwrite();


            if (Files.isDirectory(src)) {
                if (ow && Files.exists(dst) && Files.isDirectory(dst)) {
                    // overwrite folder: remove existing destination to avoid stale merges
                    deleteDirectoryRecursive(dst, handle, processed, total);
                    if (handle.cancelled.get()) return;
                }
                copyDirectoryRecursive(src, dst, ow, handle, processed, total);
            } else {
                copyFileWithProgress(src, dst, ow, handle, processed, total, "Copying");
            }
        }
    }

    // Phase 3.9.4: multi-source copy with per-item results.
    private static void copyManyWithResults(List<Path> sources, Path targetDir, boolean overwrite,
                                            OperationHandleImpl handle, AtomicLong processed, long total) throws IOException {
        requireTargetDir(targetDir, "COPY");
        boolean anyFailed = false;

        for (Path src : sources) {
            if (src == null) continue;
            String srcKey = src.toString();

            if (handle.cancelled.get()) {
                handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Cancelled before processing");
                continue;
            }

            try {
                ResolvedDestination rd = resolveWithConflict(targetDir,
                        src.getFileName() != null ? src.getFileName().toString() : "item",
                        overwrite, handle, src);
                if (rd == null || rd.path() == null) {
                    handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Skipped (conflict policy)");
                    continue;
                }
                Path dst = rd.path();
                boolean ow = rd.overwrite();

                if (Files.isDirectory(src)) {
                    if (ow && Files.exists(dst) && Files.isDirectory(dst)) {
                        deleteDirectoryRecursive(dst, handle, processed, total);
                        if (handle.cancelled.get()) {
                            handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Cancelled during copy");
                            continue;
                        }
                    }
                    copyDirectoryRecursive(src, dst, ow, handle, processed, total);
                } else {
                    copyFileWithProgress(src, dst, ow, handle, processed, total, "Copying");
                }

                handle.setPerItemResult(srcKey, OperationStatus.COMPLETED, "OK");
            } catch (Throwable ex) {
                anyFailed = true;
                String msg = ex.getMessage();
                if (msg == null || msg.isBlank()) msg = ex.getClass().getSimpleName();
                handle.setPerItemResult(srcKey, OperationStatus.FAILED, msg);
            }
        }

        if (anyFailed && !handle.cancelled.get()) {
            throw new IOException("One or more items failed during COPY.");
        }
    }

    private static void moveMany(List<Path> sources, Path targetDir, boolean overwrite,
                                 OperationHandleImpl handle, AtomicLong processed, long total) throws IOException {
        requireTargetDir(targetDir, "MOVE");
        for (Path src : sources) {
            if (handle.cancelled.get()) return;
            if (src == null) continue;

            ResolvedDestination rd = resolveWithConflict(targetDir,
                    src.getFileName() != null ? src.getFileName().toString() : "item",
                    overwrite, handle, src);
            if (rd == null || rd.path() == null) {
                continue;
            }
            Path dst = rd.path();
            boolean ow = rd.overwrite();

            try {
                CopyOption[] opts = ow ? new CopyOption[]{REPLACE_EXISTING} : new CopyOption[0];
                if (ow && Files.exists(dst) && Files.isDirectory(src) && Files.isDirectory(dst)) {
                    deleteDirectoryRecursive(dst, handle, processed, total);
                    if (handle.cancelled.get()) return;
                }
                Files.move(src, dst, opts);
                long delta = estimateBytesForPath(src, handle);
                long p = processed.addAndGet(Math.max(1L, delta));
                reportProgress(handle, p, total, "Moved: " + safeName(src));
            } catch (FileSystemException ex) {
                // Cross-volume or other move limitations -> copy + delete.
                if (Files.isDirectory(src)) {
                    copyDirectoryRecursive(src, dst, ow, handle, processed, total);
                    deleteDirectoryRecursive(src, handle, processed, total);
                } else {
                    copyFileWithProgress(src, dst, ow, handle, processed, total, "Moving");
                    if (handle.cancelled.get()) return;
                    Files.deleteIfExists(src);
                    reportProgress(handle, processed.get(), total, "Moved: " + safeName(src));
                }
            }
        }
    }

    // Phase 3.9.4: multi-source move with per-item results.
    private static void moveManyWithResults(List<Path> sources, Path targetDir, boolean overwrite,
                                            OperationHandleImpl handle, AtomicLong processed, long total) throws IOException {
        requireTargetDir(targetDir, "MOVE");
        boolean anyFailed = false;

        for (Path src : sources) {
            if (src == null) continue;
            String srcKey = src.toString();

            if (handle.cancelled.get()) {
                handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Cancelled before processing");
                continue;
            }

            try {
                ResolvedDestination rd = resolveWithConflict(targetDir,
                        src.getFileName() != null ? src.getFileName().toString() : "item",
                        overwrite, handle, src);
                if (rd == null || rd.path() == null) {
                    handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Skipped (conflict policy)");
                    continue;
                }
                Path dst = rd.path();
                boolean ow = rd.overwrite();

                try {
                    CopyOption[] opts = ow ? new CopyOption[]{REPLACE_EXISTING} : new CopyOption[0];
                    if (ow && Files.exists(dst) && Files.isDirectory(src) && Files.isDirectory(dst)) {
                        deleteDirectoryRecursive(dst, handle, processed, total);
                        if (handle.cancelled.get()) {
                            handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Cancelled during move");
                            continue;
                        }
                    }
                    Files.move(src, dst, opts);
                    long delta = estimateBytesForPath(src, handle);
                    long p = processed.addAndGet(Math.max(1L, delta));
                    reportProgress(handle, p, total, "Moved: " + safeName(src));
                } catch (FileSystemException ex) {
                    // Cross-volume or other move limitations -> copy + delete.
                    if (Files.isDirectory(src)) {
                        copyDirectoryRecursive(src, dst, ow, handle, processed, total);
                        deleteDirectoryRecursive(src, handle, processed, total);
                    } else {
                        copyFileWithProgress(src, dst, ow, handle, processed, total, "Moving");
                        if (handle.cancelled.get()) {
                            handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Cancelled during move");
                            continue;
                        }
                        Files.deleteIfExists(src);
                        reportProgress(handle, processed.get(), total, "Moved: " + safeName(src));
                    }
                }

                handle.setPerItemResult(srcKey, OperationStatus.COMPLETED, "OK");
            } catch (Throwable ex) {
                anyFailed = true;
                String msg = ex.getMessage();
                if (msg == null || msg.isBlank()) msg = ex.getClass().getSimpleName();
                handle.setPerItemResult(srcKey, OperationStatus.FAILED, msg);
            }
        }

        if (anyFailed && !handle.cancelled.get()) {
            throw new IOException("One or more items failed during MOVE.");
        }
    }

    private void deleteMany(List<Path> sources, boolean sendToTrash,
                                   OperationHandleImpl handle, AtomicLong processed, long total) throws IOException {
        for (Path src : sources) {
            if (handle.cancelled.get()) return;
            if (src == null) continue;

            deleteOne(src, sendToTrash, handle, processed, total);
        }
    }

    // Phase 3.9.4: multi-source delete with per-item results.
    private void deleteManyWithResults(List<Path> sources, boolean sendToTrash,
                                              OperationHandleImpl handle, AtomicLong processed, long total) throws IOException {
        boolean anyFailed = false;

        for (Path src : sources) {
            if (src == null) continue;
            String srcKey = src.toString();

            if (handle.cancelled.get()) {
                handle.setPerItemResult(srcKey, OperationStatus.SKIPPED, "Cancelled before processing");
                continue;
            }

            try {
                deleteOne(src, sendToTrash, handle, processed, total);
                handle.setPerItemResult(srcKey, OperationStatus.COMPLETED, "OK");
            } catch (Throwable ex) {
                anyFailed = true;
                String msg = ex.getMessage();
                if (msg == null || msg.isBlank()) msg = ex.getClass().getSimpleName();
                handle.setPerItemResult(srcKey, OperationStatus.FAILED, msg);
            }
        }

        if (anyFailed && !handle.cancelled.get()) {
            throw new IOException("One or more items failed during DELETE.");
        }
    }

    private static void renameOne(List<Path> sources, String newName, boolean overwrite,
                                  OperationHandleImpl handle, AtomicLong processed, long total) throws IOException {
        if (sources.size() != 1) {
            throw new IOException("Rename expects exactly one source.");
        }
        Path src = sources.get(0);
        if (src == null) throw new IOException("Rename source is null.");
        if (newName == null || newName.isBlank()) throw new IOException("Rename requires newName.");

        if (handle.cancelled.get()) return;

        Path parent = src.getParent();
        if (parent == null) throw new IOException("Cannot rename root.");
        ResolvedDestination rd = resolveWithConflict(parent, newName, overwrite, handle, src);
        if (rd == null || rd.path() == null) {
            return;
        }
        Path dst = rd.path();
        boolean ow = rd.overwrite();
        CopyOption[] opts = ow ? new CopyOption[]{REPLACE_EXISTING} : new CopyOption[0];
        Files.move(src, dst, opts);
        inc(handle, processed, total, "Renamed: " + safeName(src));
    }

    private void deleteOne(Path src, boolean sendToTrash,
                                  OperationHandleImpl handle, AtomicLong processed, long total) throws IOException {
        if (handle.cancelled.get()) return;

        // Prefer app-managed recycle bin when requested (supports Undo restore).
        if (sendToTrash) {
            try {
                Path recycled = recycleBin.moveToRecycle(src);
                inc(handle, processed, total, "Recycled: " + safeName(src));
                // record per-item result for 3.9.4 semantics if caller captures it
                handle.setPerItemResult(src.toString(), OperationStatus.COMPLETED, "Recycled to: " + recycled.toString());
                return;
            } catch (Throwable ignored) {
                // fall through to permanent delete
            }
        }

        if (Files.isDirectory(src)) {
            deleteDirectoryRecursive(src, handle, processed, total);
        } else {
            Files.deleteIfExists(src);
            inc(handle, processed, total, "Deleted: " + safeName(src));
        }
    }

    private static void copyFileWithProgress(Path src, Path dst, boolean overwrite,
                                             OperationHandleImpl handle, AtomicLong processed, long total,
                                             String verb) throws IOException {
        // verb: "Copying" or "Moving" for progress text
        Files.createDirectories(dst.getParent());

        // Atomic copy strategy: stream into a temp sibling file, then move into place.
        String tmpTag = handle.id();
        if (tmpTag.length() > 8) tmpTag = tmpTag.substring(0, 8);
        Path tmp = dst.resolveSibling(dst.getFileName().toString() + TMP_SUFFIX + "_" + tmpTag);
        // Clean up any prior crashed temp file.
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
        }

        // Avoid overwriting if not allowed (final destination)
        if (!overwrite && Files.exists(dst)) {
            throw new FileAlreadyExistsException(dst.toString());
        }

        OpenOption[] opts = new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};

        final long[] lastUiMs = {0L};
        final long uiIntervalMs = 120L;

        try (InputStream in = Files.newInputStream(src);
             OutputStream out = Files.newOutputStream(tmp, opts)) {

            byte[] buf = new byte[1024 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                if (handle.cancelled.get()) return;
                out.write(buf, 0, r);

                long p = processed.addAndGet(r);
                long now = System.currentTimeMillis();
                if (now - lastUiMs[0] >= uiIntervalMs) {
                    lastUiMs[0] = now;
                    reportProgress(handle, p, total, verb + ": " + safeName(src));
                }
            }
        }

        // Best-effort attribute preservation (portable)
        try {
            Files.setLastModifiedTime(tmp, Files.getLastModifiedTime(src));
        } catch (IOException ignored) {
        }

        // Move temp into place (try atomic, fallback to non-atomic).
        try {
            if (overwrite) {
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(tmp, dst, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException e) {
            if (overwrite) {
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(tmp, dst);
            }
        }

        // Verification (size always; optional SHA-256 based on system property).
        long srcSize = -1L;
        long dstSize = -2L;
        try { srcSize = Files.size(src); } catch (IOException ignored) {}
        try { dstSize = Files.size(dst); } catch (IOException ignored) {}
        if (srcSize >= 0 && dstSize >= 0 && srcSize != dstSize) {
            throw new IOException("Verification failed (size mismatch): " + safeName(src));
        }

        if (VERIFY_MODE == VerifyMode.SHA256) {
            String a = sha256Hex(src);
            String b = sha256Hex(dst);
            if (!a.equalsIgnoreCase(b)) {
                throw new IOException("Verification failed (SHA-256 mismatch): " + safeName(src));
            }
        }

        reportProgress(handle, processed.get(), total, (verb.equals("Moving") ? "Moved: " : "Copied: ") + safeName(src) + " (verified)");
    }

private static void copyDirectoryRecursive(Path srcDir, Path dstDir, boolean overwrite,
                                               OperationHandleImpl handle, AtomicLong processed, long total) throws IOException {
        Files.createDirectories(dstDir);
        Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
            @Override
/**
 * preVisitDirectory.
 *
 * @param dir TODO
 * @param attrs TODO
 * @return TODO
 */
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (handle.cancelled.get()) return FileVisitResult.TERMINATE;
                Path rel = srcDir.relativize(dir);
                Path target = dstDir.resolve(rel);
                Files.createDirectories(target);
                reportProgress(handle, processed.get(), total, "Copying… " + safeName(dir));
                return FileVisitResult.CONTINUE;
            }

            @Override
/**
 * visitFile.
 *
 * @param file TODO
 * @param attrs TODO
 * @return TODO
 */
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (handle.cancelled.get()) return FileVisitResult.TERMINATE;
                Path rel = srcDir.relativize(file);
                Path target = dstDir.resolve(rel);
                Files.createDirectories(target.getParent());
                copyFileWithProgress(file, target, overwrite, handle, processed, total, "Copying");
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectoryRecursive(Path dir, OperationHandleImpl handle,
                                                AtomicLong processed, long total) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
/**
 * visitFile.
 *
 * @param file TODO
 * @param attrs TODO
 * @return TODO
 */
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (handle.cancelled.get()) return FileVisitResult.TERMINATE;
                Files.deleteIfExists(file);
                if (handle.item.request().type() == FileOperationType.MOVE) {
                    reportProgress(handle, processed.get(), total, "Deleted: " + safeName(file));
                } else {
                    inc(handle, processed, total, "Deleted: " + safeName(file));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
/**
 * postVisitDirectory.
 *
 * @param d TODO
 * @param exc TODO
 * @return TODO
 */
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (handle.cancelled.get()) return FileVisitResult.TERMINATE;
                Files.deleteIfExists(d);
                inc(handle, processed, total, "Deleted: " + safeName(d));
                return FileVisitResult.CONTINUE;
            }
        });
    }

/**
 * resolveNonColliding.
 *
 * @param parentDir TODO
 * @param name TODO
 * @param overwrite TODO
 * @return TODO
 */
    private static Path resolveNonColliding(Path parentDir, String name, boolean overwrite) throws IOException {
        Path dst = parentDir.resolve(name);
        if (overwrite) return dst;

        if (!Files.exists(dst)) return dst;

        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }

        int i = 2;
        while (true) {
            Path candidate = parentDir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) return candidate;
            i++;
            if (i > 10_000) throw new IOException("Too many name collisions for: " + name);
        }
    }

    /**
     * Phase 3.6.8: Resolve collisions with a user-facing conflict dialog (Skip / Overwrite / Rename),
     * with optional per-operation "apply to all".
     *
     * @return destination path to use, or null if user chose to skip
     */
    private static ResolvedDestination resolveWithConflict(Path parentDir, String name, boolean overwriteDefault,
                                           OperationHandleImpl handle, Path source) throws IOException {
        Path dst = parentDir.resolve(name);
        if (!Files.exists(dst)) return new ResolvedDestination(dst, overwriteDefault);

        // If overwrite is forced by request, no prompt.
        if (overwriteDefault) return new ResolvedDestination(dst, true);

        // Phase 4.1.1: if caller explicitly chose "skip conflicts", do so without prompting.
        if (handle != null && handle.autoSkipConflicts) {
            return applyConflictDecision(ConflictAction.SKIP, parentDir, name, handle, source);
        }

        // Apply remembered decision if user chose "apply to all" for this operation.
        if (handle.rememberedConflictApplyToAll && handle.rememberedConflictAction != null) {
            return applyConflictDecision(handle.rememberedConflictAction, parentDir, name, handle, source);
        }

        // Phase 4.2.0: policy profile may auto-resolve before escalating to UI.
        try {
            ConflictPolicyAction pa = handle.policyEngine.decide(handle.policyConfig, handle.item.request(), source, dst, overwriteDefault);
            if (pa != null && pa != ConflictPolicyAction.PROMPT) {
                handle.policyAutoResolvedCount.incrementAndGet();
                return applyConflictDecision(toConflictAction(pa), parentDir, name, handle, source);
            }
        } catch (Exception ignored) {
            // Fall back to prompting
        }
        handle.policyEscalatedCount.incrementAndGet();

        ConflictDecision decision = promptConflictDecisionFx(handle, source, dst, overwriteDefault);
        if (decision == null) {
            // treat as cancel
            handle.cancelled.set(true);
            return null;
        }

        if (decision.applyToAll()) {
            handle.rememberedConflictApplyToAll = true;
            handle.rememberedConflictAction = decision.action();
        }

        return applyConflictDecision(decision.action(), parentDir, name, handle, source);
    }

    private static ResolvedDestination applyConflictDecision(ConflictAction action, Path parentDir, String name,
                                             OperationHandleImpl handle, Path source) throws IOException {
/**
 * switch.
 *
 * @param action TODO
 * @return TODO
 */
        return switch (action) {
            case SKIP -> {
                if (handle != null) handle.conflictSkipCount.incrementAndGet();
                // Keep progress stable; skipping means we simply do not perform this item.
                if (handle != null) handle.setProgressFx(new OperationProgress(0, 0, "Skipped (exists): " + name));
                yield new ResolvedDestination(null, false);
            }
            case OVERWRITE -> {
                if (handle != null) handle.conflictOverwriteCount.incrementAndGet();
                yield new ResolvedDestination(parentDir.resolve(name), true);
            }
            case RENAME -> {
                if (handle != null) handle.conflictRenameCount.incrementAndGet();
                yield new ResolvedDestination(resolveNonColliding(parentDir, name, false), false);
            }
        };
    }


/**
 * toConflictAction.
 *
 * @param a TODO
 * @return TODO
 */
    private static ConflictAction toConflictAction(ConflictPolicyAction a) {
        if (a == null) return ConflictAction.SKIP;
        return switch (a) {
            case SKIP -> ConflictAction.SKIP;
            case OVERWRITE -> ConflictAction.OVERWRITE;
            case RENAME -> ConflictAction.RENAME;
            case PROMPT -> ConflictAction.SKIP;
        };
    }

    private static ConflictDecision promptConflictDecisionFx(OperationHandleImpl handle, Path source, Path dst,
                                                             boolean overwriteDefault) {
        // Phase 4.1.4: queue-based conflict resolver (single dialog per operation).
        if (handle != null && handle.conflictSession != null) {
            return handle.conflictSession.requestDecision(source, dst, overwriteDefault);
        }

        // Fallback: serialize a single dialog across operations.
        try {
            CONFLICT_DIALOG_SEMAPHORE.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new ConflictDecision(ConflictAction.SKIP, false);
        }

        AtomicReference<ConflictDecision> out = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);

        fx(() -> {
            try {
                String title = "File conflict";
                String header = "Destination already exists";
                String content = "Source: " + safeName(source) + "\nDestination: " + dst;

                ButtonType skipBtn = new ButtonType("Skip", ButtonBar.ButtonData.NO);
                ButtonType overwriteBtn = new ButtonType("Overwrite", ButtonBar.ButtonData.OK_DONE);
                ButtonType renameBtn = new ButtonType("Rename", ButtonBar.ButtonData.OTHER);
                ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                Dialog<ButtonType> dialog = new Dialog<>();
                dialog.setTitle(title);
                dialog.setHeaderText(header);

                CheckBox applyAll = new CheckBox("Apply to all conflicts in this operation");

                Label lbl = new Label(content);
                lbl.setWrapText(true);

                VBox box = new VBox(10, lbl, applyAll);
                box.setPadding(new Insets(10));

                dialog.getDialogPane().setContent(box);
                dialog.getDialogPane().getButtonTypes().setAll(skipBtn, overwriteBtn, renameBtn, cancelBtn);

                dialog.setResultConverter(bt -> bt);

                ButtonType chosen = dialog.showAndWait().orElse(cancelBtn);

                if (chosen == cancelBtn) {
                    out.set(null);
                } else if (chosen == overwriteBtn) {
                    out.set(new ConflictDecision(ConflictAction.OVERWRITE, applyAll.isSelected()));
                } else if (chosen == renameBtn) {
                    out.set(new ConflictDecision(ConflictAction.RENAME, applyAll.isSelected()));
                } else {
                    out.set(new ConflictDecision(ConflictAction.SKIP, applyAll.isSelected()));
                }
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new ConflictDecision(ConflictAction.SKIP, false);
        } finally {
            CONFLICT_DIALOG_SEMAPHORE.release();
        }

        return out.get();
    }




/**
 * requireTargetDir.
 *
 * @param targetDir TODO
 * @param op TODO
 */
    private static void requireTargetDir(Path targetDir, String op) throws IOException {
        if (targetDir == null) throw new IOException(op + " requires a target directory.");
        if (!Files.exists(targetDir)) Files.createDirectories(targetDir);
        if (!Files.isDirectory(targetDir)) throw new IOException("Target is not a directory: " + targetDir);
    }

    
/**
 * lockForDest.
 *
 * @param p TODO
 * @return TODO
 */
    private java.util.concurrent.locks.ReentrantLock lockForDest(Path p) {
        if (p == null) {
            return null;
        }
        Path key;
        try {
            key = p.toAbsolutePath().normalize();
        } catch (Exception ex) {
            key = p;
        }
        return destLocks.computeIfAbsent(key, k -> new java.util.concurrent.locks.ReentrantLock());
    }

/**
 * withDestLock.
 *
 * @param p TODO
 * @param action TODO
 */
    private void withDestLock(Path p, IOConsumer action) throws IOException {
        java.util.concurrent.locks.ReentrantLock lock = lockForDest(p);
        if (lock == null) {
            action.accept();
            return;
        }
        lock.lock();
        try {
            action.accept();
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    private interface IOConsumer {
/**
 * accept.
 *
 */
        void accept() throws IOException;
    }

/**
 * safeName.
 *
 * @param p TODO
 * @return TODO
 */
private static String safeName(Path p) {
        if (p == null) return "(null)";
        Path fn = p.getFileName();
        return fn != null ? fn.toString() : p.toString();
    }

/**
 * sha256Hex.
 *
 * @param p TODO
 * @return TODO
 */
    private static String sha256Hex(Path p) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
        byte[] buf = new byte[1024 * 1024];
        try (InputStream in = Files.newInputStream(p)) {
            int r;
            while ((r = in.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }


/**
 * onHandleUpdated.
 *
 */
    private void onHandleUpdated() {
        scheduleAggregateRecompute();
        schedulePersist();
    }

/**
 * schedulePersist.
 *
 */
    private void schedulePersist() {
        if (suppressPersist) return;
        if (!persistScheduled.compareAndSet(false, true)) return;
        fx(() -> {
            try {
                persistNowFx();
            } finally {
                persistScheduled.set(false);
            }
        });
    }

/**
 * persistNowFx.
 *
 */
    private void persistNowFx() {
        // Persist only non-finished operations (queued + running), and remember if they were running.
        java.util.List<OperationQueuePersistence.SavedOperation> snapshot = new java.util.ArrayList<>();
        for (OperationHandleImpl h : queue) {
            if (h == null) continue;
            OperationStatus st = h.status.get();
            if (st == OperationStatus.QUEUED || st == OperationStatus.RUNNING) {
                snapshot.add(new OperationQueuePersistence.SavedOperation(h.item.id(), st, h.item.request(), st == OperationStatus.RUNNING));
            }
        }
        persistence.saveSaved(snapshot);
        if (snapshot.isEmpty()) {
            recoveredCount.set(0);
            recoveredRunningCount.set(0);
            recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
            recoveredRunningCount.set(0);
            recoveredRunningIds.clear();
            recoveredIds.clear();
            recoveredRunningAllowedIds.clear();
        }
    }

/**
 * scheduleAggregateRecompute.
 *
 */
    private void scheduleAggregateRecompute() {
        if (!aggregateRecomputeScheduled.compareAndSet(false, true)) {
            return;
        }
        fx(() -> {
            try {
                recomputeAggregateFx();
            } finally {
                aggregateRecomputeScheduled.set(false);
            }
        });
    }

/**
 * recomputeAggregateFx.
 *
 */
private void recomputeAggregateFx() {
    // Aggregate only COPY/MOVE (byte mode). Other ops are ignored in aggregate bytes.
    long remainingBytes = 0L;
    double totalBytesPerSec = 0.0;

    for (OperationHandleImpl h : activeOperations) {
        if (h == null) continue;
        FileOperationType t = h.item.request().type();
        if (!(t == FileOperationType.COPY || t == FileOperationType.MOVE)) continue;

        OperationProgress p = h.progress.get();
        if (p == null) continue;
        long total = p.totalUnits();
        long done = p.processedUnits();
        if (total <= 0) continue;

        remainingBytes += Math.max(0L, total - done);

        Instant started = h.startedAt;
        if (started != null) {
            long elapsedMs = Math.max(1L, java.time.Duration.between(started, Instant.now()).toMillis());
            double perSec = (done * 1000.0) / elapsedMs;
            if (perSec > 0.0) totalBytesPerSec += perSec;
        }
    }

    if (remainingBytes <= 0L || totalBytesPerSec <= 0.0) {
        aggregateStatus.set("");
        return;
    }

    long etaSec = (long) Math.ceil(remainingBytes / totalBytesPerSec);
    String line = "Total: " + formatBytes((long) totalBytesPerSec) + "/s" +
            " • Remaining: " + formatBytes(remainingBytes) +
            " • ETA: " + formatEta(etaSec);
    aggregateStatus.set(line);
}

/**
 * formatEta.
 *
 * @param seconds TODO
 * @return TODO
 */
private static String formatEta(long seconds) {
    if (seconds < 0) return "";
    long s = seconds;
    long m = s / 60;
    long h = m / 60;
    s = s % 60;
    m = m % 60;
    if (h > 0) return h + "h " + m + "m";
    if (m > 0) return m + "m " + s + "s";
    return s + "s";
}

/**
 * fx.
 *
 * @param r TODO
 */
private static void fx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

/**
 * inc.
 *
 * @param handle TODO
 * @param processed TODO
 * @param total TODO
 * @param msg TODO
 */
    private static void inc(OperationHandleImpl handle, AtomicLong processed, long total, String msg) {
        // item-based increment (delete/rename/queue actions)
        long p = processed.incrementAndGet();
        reportProgress(handle, p, total, msg);
    }

    private static void reportProgress(OperationHandleImpl handle, long processedUnits, long totalUnits, String msg) {
        // Throttle UI updates (especially important with parallel operations and streaming byte progress).
        long now = System.nanoTime();
        long last = handle.lastProgressPublishNanos.get();
        boolean isFinal = (totalUnits > 0 && processedUnits >= totalUnits);
        if (!isFinal && last != 0L && (now - last) < java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(75)) {
            return;
        }
        handle.lastProgressPublishNanos.set(now);
        String decorated = msg;

        Instant started = handle.startedAt;
        if (started != null && totalUnits > 0) {
            long elapsedMs = Math.max(1L, java.time.Duration.between(started, Instant.now()).toMillis());
            double perSec = (processedUnits * 1000.0) / elapsedMs;
            long remaining = Math.max(0L, totalUnits - processedUnits);
            long etaSec = perSec <= 0.0 ? -1L : (long) Math.ceil(remaining / perSec);

            FileOperationType t = handle.item.request().type();
            boolean byteMode = (t == FileOperationType.COPY || t == FileOperationType.MOVE);

            if (byteMode) {
                String prefix = formatBytes(processedUnits) + " / " + formatBytes(totalUnits);
                String rateStr = " | " + formatBytes((long) perSec) + "/s";
                String etaStr = etaSec < 0 ? "" : (" | ETA " + etaSec + "s");
                decorated = prefix + " — " + msg + rateStr + etaStr;
            } else {
                String rateStr = String.format(" | %.1f items/s", perSec);
                String etaStr = etaSec < 0 ? "" : (" | ETA " + etaSec + "s");
                decorated = msg + rateStr + etaStr;
            }
        }

        handle.setProgressFx(new OperationProgress(processedUnits, totalUnits, decorated));
    }

/**
 * formatBytes.
 *
 * @param b TODO
 * @return TODO
 */
    private static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        double v = b;
        String[] units = {"KB","MB","GB","TB","PB"};
        int u = -1;
        while (v >= 1024.0 && u < units.length - 1) {
            v /= 1024.0;
            u++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", v, units[u]);
    }


    @Override
/**
 * close.
 *
 */
    public void close() {
        worker.shutdownNow();
    }

    // -------------------------
    // internal handle impl
    // -------------------------
    private static final class OperationHandleImpl implements OperationHandle {
        private final OperationItem item;

        // 4.0.5: optional link back to Command id
        private final String commandId;


        private final ReadOnlyObjectWrapper<OperationStatus> status =
                new ReadOnlyObjectWrapper<>(OperationStatus.QUEUED);

        private final ReadOnlyObjectWrapper<OperationProgress> progress =
                new ReadOnlyObjectWrapper<>(new OperationProgress(0, 0, "Queued"));

        private final ReadOnlyBooleanWrapper cancellable =
                new ReadOnlyBooleanWrapper(true);

        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        // Phase 3.6.8: per-operation remembered conflict decision
        private volatile ConflictAction rememberedConflictAction = null;
        private volatile boolean rememberedConflictApplyToAll = false;


        // Phase 4.1.4: conflict queue session + resolution stats for audit
        private final ConflictResolutionSession conflictSession = new ConflictResolutionSession(this);
        private final java.util.concurrent.atomic.AtomicLong conflictSkipCount = new java.util.concurrent.atomic.AtomicLong(0L);
        private final java.util.concurrent.atomic.AtomicLong conflictOverwriteCount = new java.util.concurrent.atomic.AtomicLong(0L);
        private final java.util.concurrent.atomic.AtomicLong conflictRenameCount = new java.util.concurrent.atomic.AtomicLong(0L);

        // Phase 4.2.0: policy profile applied to this operation (snapshot at enqueue)
        private final ConflictPolicyConfig policyConfig;
        private final ConflictPolicyEngine policyEngine;
        private final java.util.concurrent.atomic.AtomicLong policyAutoResolvedCount = new java.util.concurrent.atomic.AtomicLong(0L);
        private final java.util.concurrent.atomic.AtomicLong policyEscalatedCount = new java.util.concurrent.atomic.AtomicLong(0L);

        // Phase 4.4.0: snapshot execution metadata + drift tracking
        private volatile boolean executedFromSnapshot = false;
        private volatile String previewHash = "";
        private final java.util.concurrent.atomic.AtomicLong driftCount = new java.util.concurrent.atomic.AtomicLong(0L);
        private final java.util.List<String> driftEvents = new java.util.ArrayList<>();

        // Phase 4.4.1: drift policy + replay integrity + executed counters
        private final ExecutionDriftPolicy driftPolicy;

         private final OperationJournalService journal;

        // Phase 5.0.0: rollback steps captured during execution (applied in reverse on failure).
        private final java.util.Deque<RollbackStep> rollbackSteps = new java.util.ArrayDeque<>();
        private volatile boolean rollbackAttempted = false;
        private volatile boolean rollbackOk = false;

        java.util.Deque<RollbackStep> rollbackSteps() { return rollbackSteps; }
        boolean rollbackAttempted() { return rollbackAttempted; }
        boolean rollbackOk() { return rollbackOk; }

        private volatile ReplayIntegrity replayIntegrity = ReplayIntegrity.PASS;
        private final java.util.concurrent.atomic.AtomicLong executedSkipCount = new java.util.concurrent.atomic.AtomicLong(0L);
        private final java.util.concurrent.atomic.AtomicLong executedEscalateCount = new java.util.concurrent.atomic.AtomicLong(0L);
        private final java.util.concurrent.atomic.AtomicLong executedOverwriteCount = new java.util.concurrent.atomic.AtomicLong(0L);
        private final java.util.concurrent.atomic.AtomicLong executedRenameCount = new java.util.concurrent.atomic.AtomicLong(0L);


        ExecutionDriftPolicy driftPolicy() { return driftPolicy; }
        ReplayIntegrity replayIntegrity() { return replayIntegrity; }

/**
 * recordDrift.
 *
 * @param message TODO
 */
        void recordDrift(String message) {
            if (message == null || message.isBlank()) message = "drift";
            driftCount.incrementAndGet();
            try {
                journal.writeDrift(id(), message);
            } catch (Throwable ignored) { }
            synchronized (driftEvents) {
                if (driftEvents.size() < 250) driftEvents.add(message);
            }
        }

/**
 * conflictSummaryForHistory.
 *
 * @return TODO
 */
        String conflictSummaryForHistory() {
            long s = conflictSkipCount.get();
            long o = conflictOverwriteCount.get();
            long r = conflictRenameCount.get();
            long a = policyAutoResolvedCount.get();
            long e = policyEscalatedCount.get();
            if (s == 0 && o == 0 && r == 0 && a == 0 && e == 0) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("Conflicts resolved: ");
            sb.append("overwrite=").append(o).append(", rename=").append(r).append(", skip=").append(s);
            if (rememberedConflictApplyToAll && rememberedConflictAction != null) {
                sb.append(" (applyToAll=").append(rememberedConflictAction.name()).append(")");
            }
            if (policyConfig != null && policyConfig.profile() != null) {
                sb.append(" | Policy=").append(policyConfig.profile().name());
                if (a != 0 || e != 0) {
                    sb.append(" (auto=").append(a).append(", escalated=").append(e).append(")");
                }
            }
            return sb.toString();
        }

        // Phase 4.1.1: optional auto-skip conflicts without prompting.
        private final boolean autoSkipConflicts;

        private final Runnable onProgressUpdated;
        private final Runnable onStatusChanged;
        private final java.util.concurrent.atomic.AtomicLong lastProgressPublishNanos = new java.util.concurrent.atomic.AtomicLong(0L);

        private volatile Throwable error;
        private volatile Instant startedAt;

        // Phase 3.9.4: per-item results captured during multi-source execution.
        private final java.util.Map<String, ItemResult> perItemResults = new java.util.LinkedHashMap<>();

        // Phase 3.9.5: optional override for the batch label written into Operation History.
        private volatile String historyLabelOverride;

        OperationHandleImpl(OperationItem item, Runnable onProgressUpdated, Runnable onStatusChanged, String commandId,
                         ConflictPolicyConfig policyConfig, ConflictPolicyEngine policyEngine,
                         ExecutionDriftPolicy driftPolicy, OperationJournalService journal) {
            this.item = item;
            this.onProgressUpdated = (onProgressUpdated == null) ? () -> {} : onProgressUpdated;
            this.onStatusChanged = (onStatusChanged == null) ? () -> {} : onStatusChanged;
            this.commandId = (commandId == null) ? "" : commandId;
            this.policyConfig = (policyConfig == null) ? new ConflictPolicyConfig(ConflictPolicyProfile.DEFAULT, ConflictPolicyAction.PROMPT) : policyConfig;
            this.policyEngine = (policyEngine == null) ? new ConflictPolicyEngine() : policyEngine;
            // OperationHandleImpl is a static nested class; drift policy must be resolved by the caller.
            this.driftPolicy = (driftPolicy == null) ? ExecutionDriftPolicy.FAIL_FAST : driftPolicy;
            this.journal = (journal == null) ? new OperationJournalService() : journal;
            FileOperationRequest r = (item == null) ? null : item.request();
            this.autoSkipConflicts = (r != null) && r.skipConflicts();
        }

        @Override public String id() { return item.id(); }
        @Override public OperationItem item() { return item; }

        String historyLabelOverride() { return historyLabelOverride; }
        void setHistoryLabelOverride(String v) { this.historyLabelOverride = v; }

        @Override public ReadOnlyObjectProperty<OperationStatus> statusProperty() { return status.getReadOnlyProperty(); }
        @Override public ReadOnlyObjectProperty<OperationProgress> progressProperty() { return progress.getReadOnlyProperty(); }
        @Override public ReadOnlyBooleanProperty cancellableProperty() { return cancellable.getReadOnlyProperty(); }

        @Override
/**
 * cancel.
 *
 */
        public void cancel() {
            cancelled.set(true);
        }

/**
 * getPerItemResult.
 *
 * @param sourcePath TODO
 * @return TODO
 */
        ItemResult getPerItemResult(String sourcePath) {
            if (sourcePath == null) return null;
            synchronized (perItemResults) {
                return perItemResults.get(sourcePath);
            }
        }

/**
 * setPerItemResult.
 *
 * @param sourcePath TODO
 * @param status TODO
 * @param message TODO
 */
        void setPerItemResult(String sourcePath, OperationStatus status, String message) {
            if (sourcePath == null) return;
            if (status == null) status = OperationStatus.FAILED;
            synchronized (perItemResults) {
                perItemResults.put(sourcePath, new ItemResult(status, message));
            }
        }

/**
 * hasAnyPerItemFailure.
 *
 * @return TODO
 */
        boolean hasAnyPerItemFailure() {
            synchronized (perItemResults) {
                for (ItemResult r : perItemResults.values()) {
                    if (r != null && r.status() == OperationStatus.FAILED) return true;
                }
            }
            return false;
        }


/**
 * setStatusFx.
 *
 * @param s TODO
 */
        void setStatusFx(OperationStatus s) {
            fx(() -> {
                status.set(s);
                onProgressUpdated.run();
                onStatusChanged.run();
                if (s == OperationStatus.RUNNING) {
                    startedAt = Instant.now();
                }
                if (s == OperationStatus.COMPLETED || s == OperationStatus.FAILED || s == OperationStatus.CANCELLED || s == OperationStatus.SKIPPED) {
                    cancellable.set(false);
                    if (startedAt != null) {
                        Duration d = Duration.between(startedAt, Instant.now());
                        OperationProgress cur = progress.get();
                        String suffix = " (" + d.toSeconds() + "s)";
                        String msg = cur.message() == null ? "" : cur.message();
                        progress.set(new OperationProgress(cur.processedUnits(), cur.totalUnits(), msg + suffix));
                    }
                }
            });
        }

/**
 * setProgressFx.
 *
 * @param p TODO
 */
        void setProgressFx(OperationProgress p) {
            fx(() -> {
                progress.set(p);
                onProgressUpdated.run();
            });
        }

/**
 * setErrorFx.
 *
 * @param t TODO
 */
        void setErrorFx(Throwable t) {
            this.error = t;
            fx(() -> {
                OperationProgress cur = progress.get();
                String msg = (t.getMessage() != null) ? t.getMessage() : t.getClass().getSimpleName();
                progress.set(new OperationProgress(cur.processedUnits(), cur.totalUnits(), "Error: " + msg));
            });
        }
    }
}
