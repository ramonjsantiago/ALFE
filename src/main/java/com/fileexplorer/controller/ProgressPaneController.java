package com.fileexplorer.controller;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.lifecycle.Lifecycle;
import com.fileexplorer.service.ops.OperationHandle;
import com.fileexplorer.service.ops.OperationProgress;
import com.fileexplorer.service.ops.OperationStatus;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.OperationQueueService;
import com.fileexplorer.service.ops.conflict.ConflictPolicyAction;
import com.fileexplorer.service.ops.conflict.ConflictPolicyProfile;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

/**
 * Phase 3.6.6.2:
 * - Parallel queue UI (multiple active operations)
 * - Active (k/N) header
 * - Aggregate throughput/remaining/ETA line for COPY/MOVE
 * - User-configurable concurrency with persistence
 */
public final class ProgressPaneController implements Lifecycle {

    private static final String PREF_NODE = "com.fileexplorer.operations";
    private static final String PREF_CONCURRENCY = "operations.concurrency";
    private static final String PREF_RECOVERY_POLICY = "operations.recoveryPolicy";

    @FXML private Label activeHeaderLabel;
    @FXML private Label aggregateLabel;
    @FXML private ComboBox<Integer> concurrencyCombo;
    @FXML private ComboBox<String> recoveryPolicyCombo;

    @FXML private Button openRecoveryManagerButton;
    @FXML private Button openTemplatesButton;
     private Button openSchedulerButton;

    // Phase 5.1.1: Batch transaction (atomic operation groups)
    @FXML private CheckBox batchTransactionModeCheckBox;
    @FXML private Label currentGroupIdLabel;
    @FXML private Button commitGroupButton;
    @FXML private Button discardGroupButton;


    // Phase 4.2.1: global conflict policy selector
    @FXML private ComboBox<ConflictPolicyProfile> conflictPolicyProfileCombo;
    @FXML private ComboBox<ConflictPolicyAction> conflictPolicyCustomActionCombo;
    @FXML private Button editConflictPolicyButton;

    @FXML private HBox orphanTempBar;
    @FXML private Label orphanTempLabel;
    @FXML private ComboBox<String> orphanPolicyCombo;
    @FXML private Button cleanupOrphanButton;
    @FXML private Button ignoreOrphanButton;

        @FXML private HBox recoveredBar;
        @FXML private Label recoveredLabel;
        @FXML private Button resumeRecoveredButton;
        @FXML private Button resumeQueuedOnlyButton;
        @FXML private Button resumeAllRecoveredButton;
        @FXML private Button discardRecoveredButton;

    @FXML private ListView<OperationHandle> activeListView;

    @FXML private ListView<OperationHandle> queueListView;
    @FXML private Button clearFinishedButton;
    @FXML private Button cancelAllButton;
    @FXML private ToggleButton pauseToggleButton;
    @FXML private Button cancelSelectedButton;
    @FXML private Button clearSavedButton;

    private ExplorerContext context;
    private boolean updatingPauseToggle;

    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    // Coalesced refresh to avoid spamming ListView.refresh()
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);

    // Track listeners attached to running operations so list cells update as progress changes
    private final Map<OperationHandle, InvalidationListener> progressListeners = new HashMap<>();
    private final Map<OperationHandle, InvalidationListener> statusListeners = new HashMap<>();

    @Override
/**
 * attach.
 *
 * @param context TODO
 */
    public void attach(ExplorerContext context) {
        if (context == null) return;
        if (this.context == context) return;
        this.context = context;
        wire();
    }

/**
 * wire.
 *
 */
    private void wire() {
        if (context == null) return;

        // Active operations list (parallel)
        //noinspection unchecked
        activeListView.setItems((javafx.collections.ObservableList<OperationHandle>) context.operationQueueService().getActiveOperations());

        // Queue list
        //noinspection unchecked
        queueListView.setItems((javafx.collections.ObservableList<OperationHandle>) context.operationQueueService().getQueue());

        // Active header: Active (k/N)
        var activeItems = (javafx.collections.ObservableList<?>) activeListView.getItems();
        activeHeaderLabel.textProperty().bind(
                Bindings.createStringBinding(
                        () -> "Active (" + activeListView.getItems().size() + "/" + context.operationQueueService().getMaxParallelism() + ")",
                        Bindings.size(activeItems),
                        context.operationQueueService().maxParallelismProperty()
                )
        );

        // Aggregate status line (COPY/MOVE only)
        if (aggregateLabel != null) {
            aggregateLabel.textProperty().bind(context.operationQueueService().aggregateStatusProperty());
        }

        // Phase 3.6.7: recovered queue banner
        if (recoveredBar != null) {
            recoveredBar.visibleProperty().bind(
                javafx.beans.binding.Bindings.and(
                        javafx.beans.binding.Bindings.greaterThan(context.operationQueueService().recoveredCountProperty(), 0),
                        context.operationQueueService().pausedProperty()
                )
        );
        recoveredBar.managedProperty().bind(recoveredBar.visibleProperty());
        }
        if (recoveredLabel != null) {
            recoveredLabel.textProperty().bind(Bindings.createStringBinding(
                    () -> {
                        int total = context.operationQueueService().getRecoveredCount();
                        int running = context.operationQueueService().getRecoveredRunningCount();
                        if (running > 0) {
                            return "Recovered " + total + " operations (" + running + " were running) from last session. Previously-running operations require confirmation.";
                        }
                        return "Recovered " + total + " operations from last session.";
                    },
                    context.operationQueueService().recoveredCountProperty(),
                    context.operationQueueService().recoveredRunningCountProperty()
            ));
        }

        // Phase 3.6.10: orphan temp banner
        if (orphanTempBar != null) {
            orphanTempBar.visibleProperty().bind(
                    Bindings.and(
                            Bindings.greaterThan(context.operationQueueService().orphanTempCountProperty(), 0),
                            Bindings.not(context.operationQueueService().orphanTempIgnoredThisSessionProperty())
                    )
            );
            orphanTempBar.managedProperty().bind(orphanTempBar.visibleProperty());
        }
        if (orphanTempLabel != null) {
            orphanTempLabel.textProperty().bind(Bindings.createStringBinding(
                    () -> {
                        int n = context.operationQueueService().getOrphanTempCount();
                        if (n <= 0) return "";
                        return "Found " + n + " orphan temp file" + (n == 1 ? "" : "s") + " from interrupted atomic copy operations.";
                    },
                    context.operationQueueService().orphanTempCountProperty()
            ));
        }
        if (orphanPolicyCombo != null) {
            orphanPolicyCombo.getItems().setAll(
                    "Ask",
                    "Auto-clean",
                    "Never"
            );
            // initialize selection
            OperationQueueService.OrphanTempPolicy pol = context.operationQueueService().getOrphanTempPolicy();
            orphanPolicyCombo.getSelectionModel().select(switch (pol) {
                case AUTO_CLEAN -> "Auto-clean";
                case NEVER -> "Never";
                default -> "Ask";
            });
            orphanPolicyCombo.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV == null) return;
                OperationQueueService.OrphanTempPolicy np = switch (newV) {
                    case "Auto-clean" -> OperationQueueService.OrphanTempPolicy.AUTO_CLEAN;
                    case "Never" -> OperationQueueService.OrphanTempPolicy.NEVER;
                    default -> OperationQueueService.OrphanTempPolicy.ASK;
                };
                context.operationQueueService().setOrphanTempPolicy(np);
                // re-scan immediately to reflect policy
                context.operationQueueService().scanForOrphanTempFiles();
            });
        }
        if (cleanupOrphanButton != null) {
            cleanupOrphanButton.setOnAction(e -> context.operationQueueService().cleanupOrphanTempFiles());
            cleanupOrphanButton.disableProperty().bind(Bindings.lessThanOrEqual(context.operationQueueService().orphanTempCountProperty(), 0));
        }
        if (ignoreOrphanButton != null) {
            ignoreOrphanButton.setOnAction(e -> context.operationQueueService().ignoreOrphanTempsThisSession());
        }
                if (resumeRecoveredButton != null) {
            resumeRecoveredButton.setOnAction(e -> context.operationQueueService().resume());
        }

        if (resumeQueuedOnlyButton != null) {
            resumeQueuedOnlyButton.setOnAction(e -> context.operationQueueService().resumeRecoveredQueuedOnly());
        }

        if (resumeAllRecoveredButton != null) {
            resumeAllRecoveredButton.setOnAction(e -> context.operationQueueService().resumeRecoveredAllIncludingRunning());
            // Only meaningful when some recovered operations were previously RUNNING.
            resumeAllRecoveredButton.disableProperty().bind(Bindings.lessThanOrEqual(context.operationQueueService().recoveredRunningCountProperty(), 0));
        }

if (discardRecoveredButton != null) {
            discardRecoveredButton.setOnAction(e -> context.operationQueueService().discardRecoveredAndClearQueue());
        }

        // Concurrency selector (persisted)
        if (concurrencyCombo != null) {
            concurrencyCombo.getItems().setAll(1, 2, 3, 4, 6, 8);

            int saved = prefs.getInt(PREF_CONCURRENCY, context.operationQueueService().getMaxParallelism());
            if (!concurrencyCombo.getItems().contains(saved)) {
                concurrencyCombo.getItems().add(saved);
                concurrencyCombo.getItems().sort(Integer::compareTo);
            }

            int initial = Math.max(1, saved);
            concurrencyCombo.setValue(initial);
            context.operationQueueService().setMaxParallelism(initial);

            concurrencyCombo.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV == null) return;
                int v = Math.max(1, newV);
                prefs.putInt(PREF_CONCURRENCY, v);
                context.operationQueueService().setMaxParallelism(v);
            });
        }

        // Phase 3.6.7.2: Recovery policy selector (persisted for next startup)
        // ASK / ALWAYS_RESUME / ALWAYS_DISCARD
        if (recoveryPolicyCombo != null) {
            recoveryPolicyCombo.getItems().setAll(
                    "Ask every time",
                    "Always resume (safe)",
                    "Always resume all (unsafe)",
                    "Always resume queued only",
                    "Always discard"
            );

            String saved = prefs.get(PREF_RECOVERY_POLICY, "ASK");
            recoveryPolicyCombo.setValue(toRecoveryLabel(saved));

            recoveryPolicyCombo.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV == null) return;
                String code = fromRecoveryLabel(newV);
                prefs.put(PREF_RECOVERY_POLICY, code);
            });
        }


        if (openRecoveryManagerButton != null) {
            openRecoveryManagerButton.setOnAction(e ->
                    RecoveryManagerController.show(openRecoveryManagerButton.getScene().getWindow(), context));
        }

        if (openTemplatesButton != null) {
            openTemplatesButton.setOnAction(e ->
                    TemplateManagerController.show(openTemplatesButton.getScene().getWindow(), context));
        }

        

        if (openSchedulerButton != null) {
            openSchedulerButton.setOnAction(e ->
                    SchedulerDashboardController.show(openSchedulerButton.getScene().getWindow(), context));
        }

wireBatchTransactionControls();


        // -------------------------
        // Phase 4.2.1: Conflict policy selector (global)
        // -------------------------
        if (conflictPolicyProfileCombo != null) {
            conflictPolicyProfileCombo.getItems().setAll(ConflictPolicyProfile.values());
            conflictPolicyProfileCombo.getSelectionModel().select(context.operationQueueService().getConflictPolicyProfile());
            conflictPolicyProfileCombo.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV == null) return;
                context.operationQueueService().setConflictPolicyProfile(newV);
                updateCustomActionEnabled();
            });
        }

        if (conflictPolicyCustomActionCombo != null) {
            conflictPolicyCustomActionCombo.getItems().setAll(
                    ConflictPolicyAction.PROMPT,
                    ConflictPolicyAction.SKIP,
                    ConflictPolicyAction.OVERWRITE,
                    ConflictPolicyAction.RENAME
            );
            conflictPolicyCustomActionCombo.getSelectionModel().select(context.operationQueueService().getCustomConflictDefaultAction());
            conflictPolicyCustomActionCombo.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV == null) return;
                context.operationQueueService().setCustomConflictDefaultAction(newV);
            });
        }

        if (editConflictPolicyButton != null) {
            editConflictPolicyButton.setOnAction(e ->
                    ConflictPolicyEditorController.show(editConflictPolicyButton.getScene().getWindow(), context.operationQueueService())
            );
        }

        updateCustomActionEnabled();

        // Cell factories
        activeListView.setCellFactory(lv -> new ActiveOpCell());
        queueListView.setCellFactory(lv -> new QueueOpCell());

        // Keep list cells updating when progress changes
        context.operationQueueService().getActiveOperations().addListener((ListChangeListener<OperationHandle>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (OperationHandle h : c.getAddedSubList()) attachOpListeners(h);
                }
                if (c.wasRemoved()) {
                    for (OperationHandle h : c.getRemoved()) detachOpListeners(h);
                }
            }
            scheduleRefresh();
        });

        // Also refresh queue list as progress/status updates change there too
        context.operationQueueService().getQueue().addListener((ListChangeListener<OperationHandle>) c -> scheduleRefresh());

        clearFinishedButton.setOnAction(e -> context.operationQueueService().clearFinished());
        cancelAllButton.setOnAction(e -> context.operationQueueService().cancelAll());

        if (pauseToggleButton != null) {
            // selected=true means "paused"
            updatingPauseToggle = true;
            pauseToggleButton.setSelected(context.operationQueueService().isPaused());
            updatingPauseToggle = false;

            // Reflect service paused state into the toggle + label
            pauseToggleButton.textProperty().bind(
                    Bindings.when(context.operationQueueService().pausedProperty())
                            .then("Resume")
                            .otherwise("Pause")
            );
            context.operationQueueService().pausedProperty().addListener((obs, was, isPaused) -> {
                if (updatingPauseToggle) return;
                updatingPauseToggle = true;
                pauseToggleButton.setSelected(isPaused);
                updatingPauseToggle = false;
            });

            pauseToggleButton.setOnAction(ev -> {
                if (updatingPauseToggle) return;
                boolean pausedNow = pauseToggleButton.isSelected();
                if (pausedNow) context.operationQueueService().pause();
                else context.operationQueueService().resume();
            });
        }

        if (cancelSelectedButton != null) {
            cancelSelectedButton.setOnAction(ev -> {
                OperationHandle sel = null;
                if (activeListView != null) sel = activeListView.getSelectionModel().getSelectedItem();
                if (sel == null && queueListView != null) sel = queueListView.getSelectionModel().getSelectedItem();
                if (sel != null && sel.cancellableProperty().get()) {
                    sel.cancel();
                }
            });
        }

        if (clearSavedButton != null) {
            clearSavedButton.setOnAction(e -> context.operationQueueService().discardSavedQueue());
        }

        // Initial listener attachment for any already-running ops (rare but safe)
        for (OperationHandle h : activeListView.getItems()) attachOpListeners(h);

        scheduleRefresh();
    }

/**
 * attachOpListeners.
 *
 * @param h TODO
 */
    private void attachOpListeners(OperationHandle h) {
        if (h == null) return;

        InvalidationListener pl = obs -> scheduleRefresh();
        InvalidationListener sl = obs -> scheduleRefresh();

        progressListeners.put(h, pl);
        statusListeners.put(h, sl);

        h.progressProperty().addListener(pl);
        h.statusProperty().addListener(sl);
    }

/**
 * detachOpListeners.
 *
 * @param h TODO
 */
    private void detachOpListeners(OperationHandle h) {
        if (h == null) return;

        InvalidationListener pl = progressListeners.remove(h);
        if (pl != null) h.progressProperty().removeListener(pl);

        InvalidationListener sl = statusListeners.remove(h);
        if (sl != null) h.statusProperty().removeListener(sl);
    }

/**
 * scheduleRefresh.
 *
 */
    private void scheduleRefresh() {
        if (!refreshScheduled.compareAndSet(false, true)) return;

        Platform.runLater(() -> {
            try {
                activeListView.refresh();
                queueListView.refresh();
            } finally {
                refreshScheduled.set(false);
            }
        });
    }

/**
 * toRecoveryLabel.
 *
 * @param code TODO
 * @return TODO
 */
    private static String toRecoveryLabel(String code) {
        if (code == null) return "Ask every time";
        String c = code.trim().toUpperCase();
        return switch (c) {
            case "ALWAYS_RESUME" -> "Always resume (safe)";
            case "ALWAYS_RESUME_ALL" -> "Always resume all (unsafe)";
            case "ALWAYS_RESUME_QUEUED_ONLY" -> "Always resume queued only";
            case "ALWAYS_DISCARD" -> "Always discard";
            default -> "Ask every time";
        };
    }

/**
 * fromRecoveryLabel.
 *
 * @param label TODO
 * @return TODO
 */
    private static String fromRecoveryLabel(String label) {
        if (label == null) return "ASK";
        String l = label.trim().toLowerCase();
        if (l.contains("always") && l.contains("resume") && l.contains("queued")) return "ALWAYS_RESUME_QUEUED_ONLY";
        if (l.contains("always") && l.contains("resume")) return "ALWAYS_RESUME";
        if (l.contains("always") && l.contains("discard")) return "ALWAYS_DISCARD";
        return "ASK";
    }


    /**
     * Phase 4.2.1: Enable/disable the CUSTOM default-action combo depending on selected profile.
     */
    private void updateCustomActionEnabled() {
        try {
            boolean isCustom = conflictPolicyProfileCombo != null
                    && conflictPolicyProfileCombo.getValue() == ConflictPolicyProfile.CUSTOM;

            if (conflictPolicyCustomActionCombo != null) {
                conflictPolicyCustomActionCombo.setDisable(!isCustom);
                // If we just switched to CUSTOM, ensure a value is selected.
                if (isCustom && conflictPolicyCustomActionCombo.getValue() == null && context != null) {
                    conflictPolicyCustomActionCombo.getSelectionModel()
                            .select(context.operationQueueService().getCustomConflictDefaultAction());
                }
            }
        } catch (Exception ignored) {
        }
    }

    // -------------------------
    // Phase 5.1.1: Batch transaction controls
    // -------------------------
/**
 * wireBatchTransactionControls.
 *
 */
    private void wireBatchTransactionControls() {
        if (context == null) return;

        OperationQueueService oqs = context.operationQueueService();

        if (batchTransactionModeCheckBox != null) {
            batchTransactionModeCheckBox.setSelected(oqs.isBatchTransactionMode());
            batchTransactionModeCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
                oqs.setBatchTransactionMode(Boolean.TRUE.equals(newV));
                updateBatchTransactionUi();
            });
        }

        if (commitGroupButton != null) {
            commitGroupButton.setOnAction(e -> {
                oqs.commitCurrentGroup();
                updateBatchTransactionUi();
            });
        }

        if (discardGroupButton != null) {
            discardGroupButton.setOnAction(e -> {
                oqs.discardCurrentGroup();
                updateBatchTransactionUi();
            });
        }

        updateBatchTransactionUi();
    }

/**
 * updateBatchTransactionUi.
 *
 */
    private void updateBatchTransactionUi() {
        if (context == null) return;
        OperationQueueService oqs = context.operationQueueService();

        boolean enabled = oqs.isBatchTransactionMode();
        String gid = oqs.currentGroupId();
        String gidLabel = (gid == null || gid.isBlank()) ? "-" : gid;

        if (currentGroupIdLabel != null) {
            currentGroupIdLabel.setText(gidLabel);
        }
        if (commitGroupButton != null) {
            commitGroupButton.setDisable(!enabled);
        }
        if (discardGroupButton != null) {
            discardGroupButton.setDisable(!enabled);
        }
    }


    private static final class ActiveOpCell extends ListCell<OperationHandle> {
        @Override
/**
 * updateItem.
 *
 * @param item TODO
 * @param empty TODO
 */
        protected void updateItem(OperationHandle item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }

            Node graphic = buildActiveGraphic(item);
            setGraphic(graphic);
            setText(null);

            // Context menu: Cancel
            MenuItem cancelItem = new MenuItem("Cancel");
            cancelItem.setOnAction(e -> item.cancel());
            OperationStatus st = item.statusProperty().get();
            cancelItem.setDisable(st != OperationStatus.RUNNING && st != OperationStatus.QUEUED);
            setContextMenu(new ContextMenu(cancelItem));
        }

/**
 * buildActiveGraphic.
 *
 * @param item TODO
 * @return TODO
 */
        private Node buildActiveGraphic(OperationHandle item) {
            String title = item.item().displayTitle();

            OperationProgress pr = item.progressProperty().get();
            double frac = (pr == null) ? 0.0 : pr.fraction();
            String msg = (pr == null || pr.message() == null) ? "" : pr.message();

            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("active-op-title");

            ProgressBar bar = new ProgressBar();
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setProgress(frac < 0 ? -1 : frac);

            Label msgLabel = new Label(msg);
            msgLabel.setWrapText(true);
            msgLabel.getStyleClass().add("active-op-msg");

            Button cancelBtn = new Button("Cancel");
            cancelBtn.setOnAction(e -> item.cancel());
            OperationStatus st = item.statusProperty().get();
            cancelBtn.setDisable(st != OperationStatus.RUNNING && st != OperationStatus.QUEUED);

            HBox actions = new HBox(8);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            actions.getChildren().addAll(spacer, cancelBtn);

            VBox box = new VBox(6, titleLabel, bar, msgLabel, actions);
            box.setPadding(new Insets(8));
            box.getStyleClass().add("active-op-row");

            return box;
        }
    }

    private final class QueueOpCell extends ListCell<OperationHandle> {
        @Override
/**
 * updateItem.
 *
 * @param item TODO
 * @param empty TODO
 */
        protected void updateItem(OperationHandle item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }

            String title = item.item().displayTitle();
            OperationStatus st = item.statusProperty().get();
            OperationProgress pr = item.progressProperty().get();

            String pct = (pr == null || pr.fraction() < 0) ? "" : String.format("  %.0f%%", pr.fraction() * 100.0);
            String msg = (pr == null || pr.message() == null || pr.message().isBlank()) ? "" : (" — " + pr.message());

            boolean needsReview = context != null && context.operationQueueService().isRecoveredRunningBlocked(item.id());
            String warn = needsReview ? "⚠ " : "";

            setText(warn + "[" + st + "] " + title + pct + msg);
            setGraphic(null);

            // Recovery actions for previously-running recovered operations
            boolean isMove = item.item() != null
                    && item.item().request() != null
                    && item.item().request().type() == FileOperationType.MOVE;

            MenuItem allowRecoveredItem = new MenuItem("Allow recovered op");
            allowRecoveredItem.setOnAction(e -> context.operationQueueService().allowRecoveredOperation(item.id()));
            allowRecoveredItem.setDisable(!needsReview);

            MenuItem convertMoveToCopyItem = new MenuItem("Convert MOVE → COPY");
            convertMoveToCopyItem.setOnAction(e -> context.operationQueueService().convertRecoveredMoveToCopy(item.id()));
            convertMoveToCopyItem.setDisable(!(needsReview && isMove));

            MenuItem discardRecoveredItem = new MenuItem("Discard recovered op");
            discardRecoveredItem.setOnAction(e -> context.operationQueueService().discardRecoveredOperation(item.id()));
            boolean isRecovered = context != null && context.operationQueueService().isRecovered(item.id());
            discardRecoveredItem.setDisable(!isRecovered || st == OperationStatus.RUNNING);

            MenuItem cancelItem = new MenuItem("Cancel");
            cancelItem.setOnAction(e -> item.cancel());
            cancelItem.setDisable(st != OperationStatus.QUEUED && st != OperationStatus.RUNNING);

            MenuItem removeItem = new MenuItem("Remove");
            removeItem.setOnAction(e -> context.operationQueueService().remove(item));
            removeItem.setDisable(st == OperationStatus.RUNNING);

            MenuItem retryItem = new MenuItem("Retry");
            retryItem.setOnAction(e -> context.operationQueueService().retry(item));
            retryItem.setDisable(!(st == OperationStatus.FAILED || st == OperationStatus.CANCELLED));

            setContextMenu(new ContextMenu(allowRecoveredItem, convertMoveToCopyItem, discardRecoveredItem, new SeparatorMenuItem(), cancelItem, removeItem, retryItem));
        }
    }
}
