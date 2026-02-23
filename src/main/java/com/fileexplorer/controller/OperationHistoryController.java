package com.fileexplorer.controller;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.service.ops.FileOperationRequest;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.OperationStatus;
import com.fileexplorer.service.ops.history.OperationHistoryEntry;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.layout.GridPane;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import com.fileexplorer.util.DialogTheme;

import javafx.application.Platform;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OperationHistoryController.
 * <p>
 * Auto-generated API documentation for this type.
 */
public final class OperationHistoryController {

    @FXML private TextField filterField;

    @FXML private ComboBox<String> typeFilter;
    @FXML private ComboBox<String> statusFilter;

    @FXML private Button clearButton;
    @FXML private MenuButton exportMenu;
    @FXML private Button revealButton;
    @FXML private Button copyButton;
    @FXML private Button retryButton;

    @FXML private Label healthLabel;

    @FXML private Spinner<Integer> maxEntriesSpinner;
    @FXML private Spinner<Integer> maxDaysSpinner;

    @FXML private TreeTableView<HistoryRow> table;
    @FXML private TreeTableColumn<HistoryRow, String> typeCol;
    @FXML private TreeTableColumn<HistoryRow, String> statusCol;
        @FXML private TreeTableColumn<HistoryRow, String> undoableCol;
@FXML private TreeTableColumn<HistoryRow, String> startedCol;
    @FXML private TreeTableColumn<HistoryRow, String> durationCol;
    @FXML private TreeTableColumn<HistoryRow, String> bytesCol;
    @FXML private TreeTableColumn<HistoryRow, String> targetCol;
    @FXML private TreeTableColumn<HistoryRow, String> messageCol;

    private ExplorerContext context;
    private Timeline healthTimer;
    private FilteredList<OperationHistoryEntry> filtered;

    public record HistoryRow(
            boolean group,
            String groupId,
            String groupLabel,
            int groupCount,
            Instant groupStartedAt,
            long groupDurationMillis,
            long groupProcessedBytes,
            long groupTotalBytes,
            boolean undoable,
            OperationHistoryEntry entry
    ) {
/**
 * ofEntry.
 *
 * @param e TODO
 * @param undoable TODO
 * @return TODO
 */
        static HistoryRow ofEntry(OperationHistoryEntry e, boolean undoable) {
            return new HistoryRow(false, "", "", 0, null, 0L, 0L, 0L, undoable, e);
        }
        static HistoryRow ofGroup(String id, String label, int count) {
            return new HistoryRow(
                    true,
                    id == null ? "" : id,
                    label == null ? "" : label,
                    Math.max(0, count),
                    null,
                    0L,
                    0L,
                    0L,
                    false,
                    null
            );
        }
        boolean isEntry() { return !group; }
    }

    private static final DateTimeFormatter TS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault());

/**
 * attach.
 *
 * @param ctx TODO
 */
    public void attach(ExplorerContext ctx) {
        this.context = ctx;

        this.filtered = new FilteredList<>(ctx.operationHistoryService().entries(), e -> true);
        rebuildTree();

        // rebuild tree on underlying changes
        ctx.operationHistoryService().entries().addListener((javafx.collections.ListChangeListener<OperationHistoryEntry>) c -> rebuildTree());

        // retention UI defaults
        int maxEntries = ctx.operationHistoryService().maxEntries();
        int maxDays = ctx.operationHistoryService().maxDays();
        maxEntriesSpinner.getValueFactory().setValue(maxEntries);
        maxDaysSpinner.getValueFactory().setValue(maxDays);

        maxEntriesSpinner.valueProperty().addListener((obs, o, n) -> {
            if (n != null) ctx.operationHistoryService().setMaxEntries(n);
        });
        maxDaysSpinner.valueProperty().addListener((obs, o, n) -> {
            if (n != null) ctx.operationHistoryService().setMaxDays(n);
        });

        updateActionEnablement();
        refreshHealthBanner();

        // stop timer when window closes
        try {
            if (table != null && table.getScene() != null && table.getScene().getWindow() != null) {
                table.getScene().getWindow().setOnHidden(ev -> { if (healthTimer != null) healthTimer.stop(); });
            }
        } catch (Throwable ignored) { }
    }

    /**
     * Select the first history row (entry or child) that matches the given commandId.
     * Best-effort: no-op if not found.
     */
    public void selectByCommandId(String commandId) {
        if (commandId == null || commandId.isBlank() || table == null) return;
        Platform.runLater(() -> {
            TreeItem<HistoryRow> root = table.getRoot();
            if (root == null) return;
            TreeItem<HistoryRow> found = findByCommandId(root, commandId);
            if (found != null) {
                table.getSelectionModel().select(found);
                int row = table.getRow(found);
                if (row >= 0) table.scrollTo(row);
            }
        });
    }

/**
 * findByCommandId.
 *
 * @param node TODO
 * @param commandId TODO
 * @return TODO
 */
    private TreeItem<HistoryRow> findByCommandId(TreeItem<HistoryRow> node, String commandId) {
        if (node == null) return null;
        HistoryRow v = node.getValue();
        if (v != null && !v.group() && v.entry() != null) {
            String cid = v.entry().commandId();
            if (commandId.equals(cid)) return node;
        }
        for (TreeItem<HistoryRow> ch : node.getChildren()) {
            TreeItem<HistoryRow> r = findByCommandId(ch, commandId);
            if (r != null) return r;
        }
        return null;
    }


    @FXML
/**
 * initialize.
 *
 */
    private void initialize() {
        // Ensure Spinner value factories exist (FXML may not create them reliably across JFX builds)
        if (maxEntriesSpinner.getValueFactory() == null) {
            maxEntriesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 200000, 5000, 100));
        }
        if (maxDaysSpinner.getValueFactory() == null) {
            maxDaysSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3650, 30, 1));
        }
        maxEntriesSpinner.setEditable(true);
        maxDaysSpinner.setEditable(true);

        typeCol.setCellValueFactory(cd -> str(renderType(cd.getValue().getValue())));
        statusCol.setCellValueFactory(cd -> str(renderStatus(cd.getValue().getValue())));
        undoableCol.setCellValueFactory(cd -> str(renderUndoable(cd.getValue().getValue())));
        startedCol.setCellValueFactory(cd -> str(renderStarted(cd.getValue().getValue())));
        durationCol.setCellValueFactory(cd -> str(renderDuration(cd.getValue().getValue())));
        bytesCol.setCellValueFactory(cd -> str(renderBytes(cd.getValue().getValue())));
        targetCol.setCellValueFactory(cd -> str(renderTarget(cd.getValue().getValue())));
        messageCol.setCellValueFactory(cd -> str(renderMessage(cd.getValue().getValue())));

        filterField.textProperty().addListener((obs, o, n) -> { applyFilter(); rebuildTree(); });

        typeFilter.setItems(FXCollections.observableArrayList(buildTypeOptions()));
        typeFilter.getSelectionModel().selectFirst();
        typeFilter.valueProperty().addListener((obs, o, n) -> { applyFilter(); rebuildTree(); });

        statusFilter.setItems(FXCollections.observableArrayList(buildStatusOptions()));
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.valueProperty().addListener((obs, o, n) -> { applyFilter(); rebuildTree(); });


        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateActionEnablement());
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Column resize policy: set in code (FXML coercion is inconsistent across JavaFX builds)
        try {
            table.setColumnResizePolicy(javafx.scene.control.TreeTableView.CONSTRAINED_RESIZE_POLICY);
        } catch (Throwable ignored) { }

        table.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case C -> { if (ev.isControlDown()) { copySelected(); ev.consume(); } }
                case ENTER -> { revealSelected(); ev.consume(); }
                case R -> { if (ev.isControlDown()) { retrySelected(RetryMode.FAILED_AND_SKIPPED); ev.consume(); } }
                case Z -> { if (ev.isControlDown()) { undoSelectedAtomic(); ev.consume(); } }
                case Y -> { if (ev.isControlDown()) { redoSelectedAtomic(); ev.consume(); } }
                default -> { }
            }
        });
        table.setRowFactory(tv -> {
            TreeTableRow<HistoryRow> row = new TreeTableRow<>();
            ContextMenu cm = new ContextMenu();

            MenuItem copy = new MenuItem("Copy details");
            copy.setOnAction(e -> copySelected());
            MenuItem reveal = new MenuItem("Reveal target folder");
            reveal.setOnAction(e -> revealSelected());

            MenuItem revealStore = new MenuItem("Reveal storage folder");
            revealStore.setOnAction(e -> revealStorageFolder());

            MenuItem showCmd = new MenuItem("Show Command Details…");
            showCmd.setOnAction(e -> showCommandDetailsSelected());
            MenuItem copyCmdId = new MenuItem("Copy Command ID");
            copyCmdId.setOnAction(e -> copyCommandIdSelected());

            // Enable only if a commandId is available on the selected entry
            var selProp = table.getSelectionModel().selectedItemProperty();
            javafx.beans.binding.BooleanBinding noCmd = javafx.beans.binding.Bindings.createBooleanBinding(() -> {
                OperationHistoryEntry se = selectedEntry();
                return se == null || se.commandId() == null || se.commandId().isBlank();
            }, selProp);
            showCmd.disableProperty().bind(noCmd);
            copyCmdId.disableProperty().bind(noCmd);
            Menu retry = buildRetryMenu(null);
            Menu undo = buildUndoMenu(null);

            cm.getItems().addAll(copy, reveal, revealStore, showCmd, copyCmdId, new SeparatorMenuItem(), undo, retry);
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null).otherwise(cm));
            return row;
        });

        // Phase 3.8.4: periodic health banner refresh
        healthTimer = new Timeline(new KeyFrame(Duration.seconds(1.0), ev -> refreshHealthBanner()));
        healthTimer.setCycleCount(Timeline.INDEFINITE);
        healthTimer.play();
    }

/**
 * rebuildTree.
 *
 */
    private void rebuildTree() {
        if (context == null || table == null) return;

        // Apply filter predicate to the backing list first
        applyFilter();

        TreeItem<HistoryRow> root = new TreeItem<>(HistoryRow.ofGroup("root", "root", 0));
        root.setExpanded(true);

        // Preserve display order (newest-first) while grouping by batchId.
        Map<String, TreeItem<HistoryRow>> groups = new LinkedHashMap<>();
        Map<String, List<OperationHistoryEntry>> groupEntries = new HashMap<>();

        for (OperationHistoryEntry e : filtered) {
            if (e == null) continue;
            String bid = e.batchId() == null ? "" : e.batchId().trim();
            if (!bid.isEmpty() && e.batchSize() > 1) {
                TreeItem<HistoryRow> g = groups.get(bid);
                if (g == null) {
                    String lbl = e.batchLabel() == null ? "" : e.batchLabel();
                    int cnt = Math.max(0, e.batchSize());
                    g = new TreeItem<>(HistoryRow.ofGroup(bid, lbl, cnt));
                    g.setExpanded(false);
                    groups.put(bid, g);
                    root.getChildren().add(g);
                }
                groupEntries.computeIfAbsent(bid, k -> new ArrayList<>()).add(e);
            } else {
                root.getChildren().add(new TreeItem<>(HistoryRow.ofEntry(e, computeUndoable(e))));
            }
        }

        // Populate group children sorted by batchIndex (oldest-first)
        for (var ent : groupEntries.entrySet()) {
            TreeItem<HistoryRow> g = groups.get(ent.getKey());
            if (g == null) continue;
            List<OperationHistoryEntry> list = ent.getValue();
            list.sort(Comparator.comparingInt(OperationHistoryEntry::batchIndex));
            g.getChildren().clear();

            Instant newestStarted = null;
            long dur = 0L;
            long processed = 0L;
            long total = 0L;
            for (OperationHistoryEntry e : list) {
                g.getChildren().add(new TreeItem<>(HistoryRow.ofEntry(e, computeUndoable(e))));

                if (e != null) {
                    if (e.startedAt() != null && (newestStarted == null || e.startedAt().isAfter(newestStarted))) {
                        newestStarted = e.startedAt();
                    }
                    dur += Math.max(0L, e.durationMillis());
                    processed += Math.max(0L, e.processedBytes());
                    total += Math.max(0L, e.totalBytes());
                }
            }

            // update group summary row with computed stats and roll up child statuses
int okCount = 0;
int failCount = 0;
int cancelCount = 0;
int skipCount = 0;
for (OperationHistoryEntry e : list) {
    if (e == null || e.status() == null) continue;
    switch (e.status()) {
        case COMPLETED -> okCount++;
        case FAILED -> failCount++;
        case CANCELLED -> cancelCount++;
        case SKIPPED -> skipCount++;
        default -> { }
    }
}

HistoryRow gr = g.getValue();
if (gr != null) {
    String base = gr.groupLabel() == null ? "" : gr.groupLabel().trim();
    String rollup = "Success: " + okCount + "  Failed: " + failCount + "  Skipped: " + skipCount + "  Cancelled: " + cancelCount;
    String newLabel = base.isEmpty() ? rollup : (base + "   (" + rollup + ")");
    boolean groupUndoable = list.stream().allMatch(this::computeUndoable);
    g.setValue(new HistoryRow(true, gr.groupId(), newLabel, gr.groupCount(),
            newestStarted, dur, processed, total, groupUndoable, null));
}
        }

        table.setRoot(root);
        table.setShowRoot(false);
        updateActionEnablement();
    }

/**
 * buildTypeOptions.
 *
 * @return TODO
 */
    private List<String> buildTypeOptions() {
        List<String> r = new ArrayList<>();
        r.add("All");
        for (FileOperationType t : FileOperationType.values()) r.add(t.name());
        return r;
    }

/**
 * buildStatusOptions.
 *
 * @return TODO
 */
    private List<String> buildStatusOptions() {
        List<String> r = new ArrayList<>();
        r.add("All");
        for (OperationStatus s : OperationStatus.values()) r.add(s.name());
        return r;
    }

/**
 * applyFilter.
 *
 */
    private void applyFilter() {
        if (filtered == null) return;

        String needle = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
        String typeSel = typeFilter.getValue() == null ? "All" : typeFilter.getValue();
        String statusSel = statusFilter.getValue() == null ? "All" : statusFilter.getValue();

        filtered.setPredicate(e -> {
            if (e == null) return false;

            if (!"All".equals(typeSel)) {
                String t = e.type() == null ? "" : e.type().name();
                if (!typeSel.equalsIgnoreCase(t)) return false;
            }
            if (!"All".equals(statusSel)) {
                String s = e.status() == null ? "" : e.status().name();
                if (!statusSel.equalsIgnoreCase(s)) return false;
            }

            if (needle.isEmpty()) return true;

            String hay = (safe(e.type() == null ? "" : e.type().name()) + " "
                    + safe(e.status() == null ? "" : e.status().name()) + " "
                    + safe(e.sourcesSummary()) + " "
                    + safe(e.targetSummary()) + " "
                    + safe(e.message())).toLowerCase(Locale.ROOT);
            return hay.contains(needle);
        });

        // Tree rebuild is triggered by attach listener and also by filter changes.
        if (context != null && table != null) {
            // avoid recursion if called from rebuildTree
        }
    }

/**
 * updateActionEnablement.
 *
 */
    private void updateActionEnablement() {
        OperationHistoryEntry sel = selectedEntry();
        boolean has = sel != null;
        copyButton.setDisable(!has);
        revealButton.setDisable(!has);
        retryButton.setDisable(!has || !canRetry(sel));
    }

/**
 * selectedEntry.
 *
 * @return TODO
 */
    private OperationHistoryEntry selectedEntry() {
        if (table == null) return null;
        TreeItem<HistoryRow> it = table.getSelectionModel().getSelectedItem();
        if (it == null || it.getValue() == null) return null;
        HistoryRow r = it.getValue();
        if (r.group()) {
            // For group row selection, operate on the first child (oldest) for reveal/copy metadata.
            if (it.getChildren() == null || it.getChildren().isEmpty()) return null;
            HistoryRow child = it.getChildren().get(0).getValue();
            return child == null ? null : child.entry();
        }
        return r.entry();
    }

/**
 * selectedEntriesExpanded.
 *
 * @return TODO
 */
    private List<OperationHistoryEntry> selectedEntriesExpanded() {
        if (table == null) return List.of();
        List<TreeItem<HistoryRow>> items = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (items.isEmpty()) return List.of();
        List<OperationHistoryEntry> out = new ArrayList<>();
        for (TreeItem<HistoryRow> it : items) {
            if (it == null || it.getValue() == null) continue;
            HistoryRow r = it.getValue();
            if (r.group()) {
                for (TreeItem<HistoryRow> ch : it.getChildren()) {
                    if (ch != null && ch.getValue() != null && ch.getValue().entry() != null) out.add(ch.getValue().entry());
                }
            } else if (r.entry() != null) {
                out.add(r.entry());
            }
        }
        return out;
    }

/**
 * renderType.
 *
 * @param r TODO
 * @return TODO
 */
    private String renderType(HistoryRow r) {
        if (r == null) return "";
        if (r.group()) return "BATCH";
        OperationHistoryEntry e = r.entry();
        return e == null || e.type() == null ? "" : e.type().name();
    }
/**
 * renderStatus.
 *
 * @param r TODO
 * @return TODO
 */
    private String renderStatus(HistoryRow r) {
    if (r == null) return "";
    if (r.group()) {
        String lbl = r.groupLabel() == null ? "" : r.groupLabel();
        // If the label already contains a rollup suffix, prefer that.
        if (lbl.contains("Success:") || lbl.contains("Failed:") || lbl.contains("Skipped:") || lbl.contains("Cancelled:")) {
            int idx = lbl.indexOf("Success:");
            if (idx >= 0) return lbl.substring(idx).trim();
        }
        return r.groupCount() + " item(s)";
    }
    OperationHistoryEntry e = r.entry();
    return e == null || e.status() == null ? "" : e.status().name();
}

/**
 * renderUndoable.
 *
 * @param r TODO
 * @return TODO
 */
private String renderUndoable(HistoryRow r) {
    if (r == null) return "";
    return r.undoable() ? "Yes" : "No";
}

/**
 * renderStarted.
 *
 * @param r TODO
 * @return TODO
 */
private String renderStarted(HistoryRow r) {
        if (r == null) return "";
        if (r.group()) {
            return r.groupStartedAt() == null ? "" : TS.format(r.groupStartedAt());
        }
        OperationHistoryEntry e = r.entry();
        return e == null || e.startedAt() == null ? "" : TS.format(e.startedAt());
    }
/**
 * renderDuration.
 *
 * @param r TODO
 * @return TODO
 */
    private String renderDuration(HistoryRow r) {
        if (r == null) return "";
        if (r.group()) return formatDuration(r.groupDurationMillis());
        OperationHistoryEntry e = r.entry();
        return e == null ? "" : formatDuration(e.durationMillis());
    }
/**
 * renderBytes.
 *
 * @param r TODO
 * @return TODO
 */
    private String renderBytes(HistoryRow r) {
        if (r == null) return "";
        if (r.group()) return formatBytes(r.groupProcessedBytes(), r.groupTotalBytes());
        OperationHistoryEntry e = r.entry();
        return e == null ? "" : formatBytes(e.processedBytes(), e.totalBytes());
    }
/**
 * renderTarget.
 *
 * @param r TODO
 * @return TODO
 */
    private String renderTarget(HistoryRow r) {
        if (r == null) return "";
        if (r.group()) return "";
        OperationHistoryEntry e = r.entry();
        return e == null ? "" : safe(e.targetSummary());
    }
/**
 * renderMessage.
 *
 * @param r TODO
 * @return TODO
 */
    private String renderMessage(HistoryRow r) {
        if (r == null) return "";
        if (r.group()) return safe(r.groupLabel());
        OperationHistoryEntry e = r.entry();
        return e == null ? "" : safe(e.message());
    }

/**
 * canRetry.
 *
 * @param e TODO
 * @return TODO
 */
    private boolean canRetry(OperationHistoryEntry e) {
        if (context == null || e == null) return false;
        if (e.requestSources() == null || e.requestSources().isEmpty()) return false;
        // target dir required for copy/move; rename needs newName; delete doesn't require target
        if (e.type() == FileOperationType.RENAME) {
            return e.requestNewName() != null && !e.requestNewName().isBlank();
        }
        if (e.type() == FileOperationType.COPY || e.type() == FileOperationType.MOVE) {
            return e.requestTargetDirectory() != null && !e.requestTargetDirectory().isBlank();
        }
        return true;
    }

    /**
     * Best-effort rule for whether a history entry is undoable by the current Command/ops stack.
     * This is intentionally conservative (only COMPLETED operations with sufficient request data).
     */
    private boolean computeUndoable(OperationHistoryEntry e) {
        if (context == null || e == null) return false;
        if (e.status() != OperationStatus.COMPLETED) return false;
        if (e.requestSources() == null || e.requestSources().isEmpty()) return false;

        return switch (e.type()) {
            case COPY, MOVE -> e.requestTargetDirectory() != null && !e.requestTargetDirectory().isBlank();
            case DELETE -> e.requestSendToTrash();
            case RENAME -> e.requestNewName() != null && !e.requestNewName().isBlank();
            default -> false;
        };
    }

    @FXML
/**
 * onClear.
 *
 * @param e TODO
 */
    private void onClear(ActionEvent e) {
        if (context == null) return;
        context.operationHistoryService().clear();
    }

    @FXML
    private void onExportJsonl(ActionEvent e) { export("JSON Lines (*.jsonl)", "*.jsonl", "operation-history.jsonl", ExportKind.JSONL); }
    @FXML
    private void onExportJson(ActionEvent e) { export("JSON (*.json)", "*.json", "operation-history.json", ExportKind.JSON); }
    @FXML
    private void onExportCsv(ActionEvent e) { export("CSV (*.csv)", "*.csv", "operation-history.csv", ExportKind.CSV); }

    @FXML
    private void onExportSelectedJsonl(ActionEvent e) { exportSelected("JSON Lines (*.jsonl)", "*.jsonl", "operation-history-selected.jsonl", ExportKind.JSONL); }
    @FXML
    private void onExportSelectedJson(ActionEvent e) { exportSelected("JSON (*.json)", "*.json", "operation-history-selected.json", ExportKind.JSON); }
    @FXML
    private void onExportSelectedCsv(ActionEvent e) { exportSelected("CSV (*.csv)", "*.csv", "operation-history-selected.csv", ExportKind.CSV); }

    @FXML
    private void onRevealStorageFolder(ActionEvent e) { revealStorageFolder(); }

/**
 * refreshHealthBanner.
 *
 */
    private void refreshHealthBanner() {
        if (healthLabel == null) return;
        if (context == null) {
            healthLabel.setText("History: not attached");
            return;
        }
        String s = context.operationHistoryService().healthSummary();
        healthLabel.setText(s == null || s.isBlank() ? "History: OK" : s);
    }

/**
 * diagnosticsTextWithCommands.
 *
 * @return TODO
 */
        private String diagnosticsTextWithCommands() {
        if (context == null) return "";
        String base = context.operationHistoryService().diagnosticsText();
        StringBuilder sb = new StringBuilder(base.length() + 512);
        sb.append(base);
        sb.append('\n').append('\n');
        sb.append("Command Stack").append('\n');
        try {
            var cm = context.commandManager();
            if (cm == null) {
                sb.append("  (no CommandManager)\n");
            } else {
                sb.append("  Undo depth: ").append(cm.undoDepth()).append('\n');
                sb.append("  Redo depth: ").append(cm.redoDepth()).append('\n');
                sb.append("  Load status: ").append(cm.lastLoadStatus()).append('\n');
                sb.append("  Load message: ").append(cm.lastLoadMessage()).append('\n');
                sb.append("  Restored undo: ").append(cm.lastLoadRestoredUndo()).append('\n');
                sb.append("  Restored redo: ").append(cm.lastLoadRestoredRedo()).append('\n');
                sb.append("  Dropped: ").append(cm.lastLoadDropped()).append('\n');
                sb.append("  Store file: ").append(cm.storeFile()).append('\n');

sb.append("  Recent commandIds:").append('\n');
var recent = cm.recentCommandIds(10);
if (recent.isEmpty()) {
    sb.append("    (none)\n");
} else {
    for (String cid : recent) {
        sb.append("    ").append(cid);
        sb.append(cm.lookupByCommandId(cid) != null ? "  [ok]\n" : "  [missing]\n");
    }
}

            }
        } catch (Throwable t) {
            sb.append("  (error reading CommandManager: ").append(t).append(")\n");
        }
        return sb.toString();
    }

/**
 * copyDiagnosticsToClipboard.
 *
 */
private void copyDiagnosticsToClipboard() {
        if (context == null) return;
        String d = diagnosticsTextWithCommands();
        ClipboardContent cc = new ClipboardContent();
        cc.putString(d);
        Clipboard.getSystemClipboard().setContent(cc);
    }

/**
 * forceCheckpointNow.
 *
 */
    private void forceCheckpointNow() {
        if (context == null) return;
        context.operationHistoryService().forceCheckpointBestEffort("manual");
        refreshHealthBanner();

        // stop timer when window closes
        try {
            if (table != null && table.getScene() != null && table.getScene().getWindow() != null) {
                table.getScene().getWindow().setOnHidden(ev -> { if (healthTimer != null) healthTimer.stop(); });
            }
        } catch (Throwable ignored) { }
    }

/**
 * showDiagnosticsDialog.
 *
 */
    private void showDiagnosticsDialog() {
        if (context == null) return;
        String d = diagnosticsTextWithCommands();

        Window owner = null;
        try {
            if (table != null && table.getScene() != null) {
                owner = table.getScene().getWindow();
            }
        } catch (Throwable ignored) { }

        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("History Diagnostics");
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Copy to clipboard", ButtonBar.ButtonData.LEFT),
                new ButtonType("Force checkpoint", ButtonBar.ButtonData.LEFT),
                ButtonType.CLOSE);

        DialogTheme.apply(dialog, owner);

        TextArea ta = new TextArea(d);
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setPrefColumnCount(110);
        ta.setPrefRowCount(24);

        dialog.getDialogPane().setContent(ta);

        Button copyBtn = (Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(0));
        copyBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            copyDiagnosticsToClipboard();
            ev.consume();
        });
        Button chkBtn = (Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(1));
        chkBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            forceCheckpointNow();
            ta.setText(diagnosticsTextWithCommands());
            ev.consume();
        });

        dialog.showAndWait();
    }

    @FXML
    private void onShowDiagnostics(ActionEvent e) { showDiagnosticsDialog(); }

    @FXML
    private void onCopyDiagnostics(ActionEvent e) { copyDiagnosticsToClipboard(); }

    @FXML
    private void onForceCheckpoint(ActionEvent e) { forceCheckpointNow(); }


@FXML
private void onShowPersistenceSettings(ActionEvent e) { showPersistenceSettingsDialog(); }

    private enum ExportKind { JSONL, JSON, CSV }

/**
 * export.
 *
 * @param label TODO
 * @param pattern TODO
 * @param defaultName TODO
 * @param kind TODO
 */
    private void export(String label, String pattern, String defaultName, ExportKind kind) {
        if (context == null) return;
        Window w = table.getScene() == null ? null : table.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Operation History");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(label, pattern));
        fc.setInitialFileName(defaultName);
        File f = fc.showSaveDialog(w);
        if (f == null) return;

        try {
            Path out = f.toPath();
            switch (kind) {
                case JSONL -> context.operationHistoryService().exportJsonl(out);
                case JSON -> context.operationHistoryService().exportJsonPretty(out);
                case CSV -> context.operationHistoryService().exportCsv(out);
            }
        } catch (IOException ignored) {
        }
    }

/**
 * exportSelected.
 *
 * @param label TODO
 * @param pattern TODO
 * @param defaultName TODO
 * @param kind TODO
 */
    private void exportSelected(String label, String pattern, String defaultName, ExportKind kind) {
        if (context == null) return;
        List<OperationHistoryEntry> sel = selectedEntriesExpanded();
        if (sel.isEmpty()) return;

        Window w = table.getScene() == null ? null : table.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Selected Operation History");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(label, pattern));
        fc.setInitialFileName(defaultName);
        File f = fc.showSaveDialog(w);
        if (f == null) return;

        try {
            Path out = f.toPath();
            switch (kind) {
                case JSONL -> context.operationHistoryService().exportJsonl(out, sel);
                case JSON -> context.operationHistoryService().exportJsonPretty(out, sel);
                case CSV -> context.operationHistoryService().exportCsv(out, sel);
            }
        } catch (IOException ignored) {
        }
    }

    @FXML
    private void onReveal(ActionEvent e) { revealSelected(); }

    @FXML
    private void onCopy(ActionEvent e) { copySelected(); }

    @FXML
/**
 * onRetry.
 *
 * @param e TODO
 */
    private void onRetry(ActionEvent e) {
        // Phase 3.9.5: explicit retry modes.
        // Show a small menu anchored to the Retry button.
        ContextMenu cm = new ContextMenu();
        cm.getItems().add(buildRetryMenu(retryButton));
        cm.setAutoHide(true);
        if (retryButton != null) {
            cm.show(retryButton, Side.BOTTOM, 0, 0);
        } else {
            // Fallback: default behavior
            retrySelected(RetryMode.FAILED_AND_SKIPPED);
        }
    }


/**
 * revealSelected.
 *
 */
    private void revealSelected() {
        OperationHistoryEntry sel = selectedEntry();
        if (sel == null) return;

        Path target = bestEffortResolveTargetPath(sel);
        if (target == null) return;

        try {
            if (revealWithSelection(target)) return;

            Path folder = Files.isDirectory(target) ? target : target.getParent();
            if (folder == null) return;
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder.toFile());
            }
        } catch (Throwable ignored) {
        }
    }

/**
 * revealStorageFolder.
 *
 */
    private void revealStorageFolder() {
        if (context == null) return;

        try {
            Path dir = context.operationHistoryService().historyDirectory();
            if (dir == null) return;
            Files.createDirectories(dir);

            if (openFolderBestEffort(dir)) return;

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Throwable ignored) {
        }
    }


/**
 * showPersistenceSettingsDialog.
 *
 */
private void showPersistenceSettingsDialog() {
    if (context == null) return;

    Dialog<ButtonType> d = new Dialog<>();
    d.setTitle("History Persistence Settings");
    d.setHeaderText("Tune history persistence (snapshot + WAL)");

    ButtonType ok = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
    d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

    GridPane gp = new GridPane();
    gp.setHgap(10);
    gp.setVgap(10);
    gp.setPadding(new Insets(10));

    OperationHistoryServiceView svc = new OperationHistoryServiceView(context);

    Label storageDirLabel = new Label(svc.historyDirectory().toString());
    storageDirLabel.setWrapText(true);

    TextField checkpointBytes = new TextField(Long.toString(svc.walCheckpointBytesEffective()));
    checkpointBytes.setPromptText("e.g., 2000000 (0 disables)");

    Spinner<Integer> keepArchives = new Spinner<>();
    keepArchives.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, svc.walArchiveKeepEffective(), 1));
    keepArchives.setEditable(true);

    CheckBox checkpointOnStartup = new CheckBox("Checkpoint on startup when WAL is large");
    checkpointOnStartup.setSelected(svc.checkpointOnStartupEffective());

    Label note = new Label("");
    note.setWrapText(true);

    int row = 0;
    gp.add(new Label("Storage folder:"), 0, row);
    gp.add(storageDirLabel, 1, row++);

    gp.add(new Label("WAL checkpoint threshold (bytes):"), 0, row);
    gp.add(checkpointBytes, 1, row++);

    gp.add(new Label("WAL archives to keep:"), 0, row);
    gp.add(keepArchives, 1, row++);

    gp.add(new Label("Startup behavior:"), 0, row);
    gp.add(checkpointOnStartup, 1, row++);

    boolean overridden = svc.anyPersistencePropOverride();
    if (overridden) {
        note.setText("Note: One or more values are overridden by system properties (-D...). " +
                "Those fields are read-only and changing them here will have no effect until the property is removed.");
        checkpointBytes.setDisable(svc.isPropOverrideWalCheckpointBytes());
        keepArchives.setDisable(svc.isPropOverrideWalArchiveKeep());
        checkpointOnStartup.setDisable(svc.isPropOverrideCheckpointOnStartup());
    }
    gp.add(note, 0, row, 2, 1);

    d.getDialogPane().setContent(gp);

    // Validation: prevent closing on invalid bytes input
    final Button okBtn = (Button) d.getDialogPane().lookupButton(ok);
    okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
        if (checkpointBytes.isDisabled()) return;
        Long v = parseLongFlexible(checkpointBytes.getText());
        if (v == null || v < 0 || v > 1_000_000_000L) {
            alert("Invalid value", "WAL checkpoint threshold must be a number between 0 and 1,000,000,000 bytes.");
            ev.consume();
        }
    });

    Optional<ButtonType> res = d.showAndWait();
    if (res.isEmpty() || res.get() != ok) return;

    // Save (Preferences). If overridden by -D, setters won't matter; we keep it explicit and safe.
    if (!checkpointBytes.isDisabled()) {
        Long v = parseLongFlexible(checkpointBytes.getText());
        if (v != null) context.operationHistoryService().setWalCheckpointBytes(v);
    }
    if (!keepArchives.isDisabled()) {
        Integer v = keepArchives.getValue();
        if (v != null) context.operationHistoryService().setWalArchiveKeep(v);
    }
    if (!checkpointOnStartup.isDisabled()) {
        context.operationHistoryService().setCheckpointOnStartup(checkpointOnStartup.isSelected());
    }
}

/**
 * Minimal view adapter to avoid leaking persistence internals into the controller.
 */
private static final class OperationHistoryServiceView {
    private final ExplorerContext ctx;

    OperationHistoryServiceView(ExplorerContext ctx) { this.ctx = ctx; }

    Path historyDirectory() { return ctx.operationHistoryService().historyDirectory(); }

    long walCheckpointBytesEffective() { return ctx.operationHistoryService().walCheckpointBytes(); }

    int walArchiveKeepEffective() { return ctx.operationHistoryService().walArchiveKeep(); }

    boolean checkpointOnStartupEffective() { return ctx.operationHistoryService().checkpointOnStartup(); }

/**
 * isPropOverrideWalCheckpointBytes.
 *
 * @return TODO
 */
    boolean isPropOverrideWalCheckpointBytes() {
        String p = System.getProperty(com.fileexplorer.service.ops.history.persistence.HistoryStorageConfig.PROP_WAL_CHECKPOINT_BYTES);
        return p != null && !p.isBlank();
    }

/**
 * isPropOverrideWalArchiveKeep.
 *
 * @return TODO
 */
    boolean isPropOverrideWalArchiveKeep() {
        String p = System.getProperty(com.fileexplorer.service.ops.history.persistence.HistoryStorageConfig.PROP_WAL_ARCHIVE_KEEP);
        return p != null && !p.isBlank();
    }

/**
 * isPropOverrideCheckpointOnStartup.
 *
 * @return TODO
 */
    boolean isPropOverrideCheckpointOnStartup() {
        String p = System.getProperty(com.fileexplorer.service.ops.history.persistence.HistoryStorageConfig.PROP_CHECKPOINT_ON_STARTUP);
        return p != null && !p.isBlank();
    }

/**
 * anyPersistencePropOverride.
 *
 * @return TODO
 */
    boolean anyPersistencePropOverride() {
        return isPropOverrideWalCheckpointBytes() || isPropOverrideWalArchiveKeep() || isPropOverrideCheckpointOnStartup();
    }
}

/**
 * parseLongFlexible.
 *
 * @param s TODO
 * @return TODO
 */
private static Long parseLongFlexible(String s) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    // allow 2_000_000 or 2,000,000
    t = t.replace("_", "").replace(",", "");
    try { return Long.parseLong(t); } catch (NumberFormatException ex) { return null; }
}

/**
 * alert.
 *
 * @param title TODO
 * @param msg TODO
 */
private void alert(String title, String msg) {
    Alert a = new Alert(Alert.AlertType.INFORMATION);
    a.setTitle(title);
    a.setHeaderText(title);
    a.setContentText(msg);
    javafx.stage.Window owner = null;
    try { owner = (table != null && table.getScene() != null) ? table.getScene().getWindow() : null; } catch (Exception ignored) {}
    if (owner != null) { a.initOwner(owner); }
    com.fileexplorer.util.DialogTheme.apply(a, owner);
    a.showAndWait();
}

    // --- Phase 4.0.2: command-backed undo/redo helpers ---
/**
 * infoAlert.
 *
 * @param title TODO
 * @param msg TODO
 */
    private void infoAlert(String title, String msg) {
        alert(title, msg);
    }

    private void warnAlert(String title, Exception ex) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(title);
        String message = (ex == null ? "" : String.valueOf(ex.getMessage()));
        a.setContentText(message);

        if (ex != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            pw.flush();

            TextArea ta = new TextArea(sw.toString());
            ta.setEditable(false);
            ta.setWrapText(false);
            ta.setMaxWidth(Double.MAX_VALUE);
            ta.setMaxHeight(Double.MAX_VALUE);

            GridPane gp = new GridPane();
            gp.setMaxWidth(Double.MAX_VALUE);
            gp.add(new Label("Details:"), 0, 0);
            gp.add(ta, 0, 1);

            a.getDialogPane().setExpandableContent(gp);
        }

        a.showAndWait();
    }

/**
 * refreshNow.
 *
 */
    private void refreshNow() {
        if (table == null) return;
        Platform.runLater(() -> {
            try {
                rebuildTree();
                table.refresh();
                refreshHealthBanner();
            } catch (Exception ignored) {
                // do not break UI if refresh fails
            }
        });
    }


/**
 * bestEffortResolveTargetPath.
 *
 * @param e TODO
 * @return TODO
 */
    private Path bestEffortResolveTargetPath(OperationHistoryEntry e) {
        try {
            String dirStr = e.requestTargetDirectory();
            Path dir = (dirStr == null || dirStr.isBlank()) ? null : Path.of(dirStr);

            // RENAME: target = parent(sources[0]) / newName
            if (e.type() == FileOperationType.RENAME && e.requestSources() != null && !e.requestSources().isEmpty()) {
                Path src = Path.of(e.requestSources().get(0));
                String nn = e.requestNewName();
                if (nn != null && !nn.isBlank()) {
                    Path parent = src.getParent();
                    if (parent != null) return parent.resolve(nn);
                }
            }

            // COPY/MOVE: if single source and target dir is known, infer target path
            if ((e.type() == FileOperationType.COPY || e.type() == FileOperationType.MOVE) &&
                    dir != null && e.requestSources() != null && e.requestSources().size() == 1) {
                Path src = Path.of(e.requestSources().get(0));
                Path name = src.getFileName();
                if (name != null) return dir.resolve(name);
            }

            // Fallback: use targetSummary if it looks like a path
            String ts = e.targetSummary();
            if (ts != null && !ts.isBlank()) {
                Path p = Path.of(ts);
                return p;
            }

            return dir;
        } catch (Throwable ignored) {
            return null;
        }
    }

/**
 * revealWithSelection.
 *
 * @param target TODO
 * @return TODO
 */
    private boolean revealWithSelection(Path target) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String abs = target.toAbsolutePath().toString();

            if (os.contains("win")) {
                // explorer can select a file
                if (Files.exists(target) && !Files.isDirectory(target)) {
                    new ProcessBuilder("explorer.exe", "/select,", abs).start();
                    return true;
                }
                // otherwise open folder
                Path folder = Files.isDirectory(target) ? target : target.getParent();
                if (folder != null) {
                    new ProcessBuilder("explorer.exe", folder.toAbsolutePath().toString()).start();
                    return true;
                }
            } else if (os.contains("mac")) {
                if (Files.exists(target)) {
                    new ProcessBuilder("open", "-R", abs).start();
                    return true;
                }
            } else {
                // linux: best-effort open folder (selection is DE-specific)
                Path folder = Files.isDirectory(target) ? target : target.getParent();
                if (folder != null) {
                    new ProcessBuilder("xdg-open", folder.toAbsolutePath().toString()).start();
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

/**
 * openFolderBestEffort.
 *
 * @param folder TODO
 * @return TODO
 */
    private boolean openFolderBestEffort(Path folder) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String abs = folder.toAbsolutePath().toString();

            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", abs).start();
                return true;
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", abs).start();
                return true;
            } else {
                new ProcessBuilder("xdg-open", abs).start();
                return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }


/**
 * copySelected.
 *
 */
    private void copySelected() {
        List<OperationHistoryEntry> sels = selectedEntriesExpanded();
        if (sels.isEmpty()) return;

        String details;
        if (sels.size() == 1) {
            details = formatDetails(sels.get(0));
        } else {
            details = sels.stream()
                    .map(this::formatOneLine)
                    .collect(Collectors.joining(System.lineSeparator()));
        }

        ClipboardContent cc = new ClipboardContent();
        cc.putString(details);
        Clipboard.getSystemClipboard().setContent(cc);
    }

/**
 * copyCommandIdSelected.
 *
 */
    private void copyCommandIdSelected() {
        OperationHistoryEntry e = selectedEntry();
        if (e == null) return;
        String id = e.commandId();
        if (id == null || id.isBlank()) return;

        ClipboardContent cc = new ClipboardContent();
        cc.putString(id);
        Clipboard.getSystemClipboard().setContent(cc);
    }

/**
 * showCommandDetailsSelected.
 *
 */
    private void showCommandDetailsSelected() {
        OperationHistoryEntry e = selectedEntry();
        if (e == null) return;
        String id = e.commandId();
        if (id == null || id.isBlank()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Command ID: ").append(id).append(System.lineSeparator());
        if (context != null && context.commandManager() != null) {
            var cm = context.commandManager();
            sb.append("Undo depth: ").append(cm.undoDepth()).append(System.lineSeparator());
            sb.append("Redo depth: ").append(cm.redoDepth()).append(System.lineSeparator());
            try {
                sb.append("Stack store: ").append(cm.storeFile()).append(System.lineSeparator());
            } catch (Throwable ignored) {
                // best effort
            }
        }
        sb.append(System.lineSeparator());
        sb.append("Note: This view shows the command identifier associated with this history row. ")
          .append("(Direct command record inspection will be added in a future phase.)");

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Command Details");
        a.setHeaderText("Command details");
        a.setContentText(sb.toString());
        // Owner best-effort: prefer the table's window (there is no class field named "root").
        a.initOwner((table == null || table.getScene() == null) ? null : table.getScene().getWindow());
        a.showAndWait();
    }

/**
 * formatOneLine.
 *
 * @param e TODO
 * @return TODO
 */
    private String formatOneLine(OperationHistoryEntry e) {
        String started = e.startedAt() == null ? "" : TS.format(e.startedAt());
        String dur = formatDuration(e.durationMillis());
        String tgt = safe(e.targetSummary());
        return String.format("%s %s %s %s %s",
                started,
                e.type() == null ? "" : e.type().name(),
                e.status() == null ? "" : e.status().name(),
                dur,
                tgt);
    }

/**
 * formatDetails.
 *
 * @param e TODO
 * @return TODO
 */
    private String formatDetails(OperationHistoryEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(e.type()).append("\n");
        sb.append("Status: ").append(e.status()).append("\n");
        if (e.startedAt() != null) sb.append("Started: ").append(TS.format(e.startedAt())).append("\n");
        sb.append("Duration: ").append(formatDuration(e.durationMillis())).append("\n");
        sb.append("Bytes: ").append(formatBytes(e.processedBytes(), e.totalBytes())).append("\n");
        sb.append("Target: ").append(safe(e.targetSummary())).append("\n");
        sb.append("Message: ").append(safe(e.message())).append("\n");

        // Phase 5.5.1: Scheduled run origin/audit metadata (best-effort)
        if (e.originType() != null && !e.originType().isBlank()) {
            sb.append("Origin: ").append(safe(e.originType())).append("\n");
            if (e.originTriggerType() != null && !e.originTriggerType().isBlank()) {
                sb.append("Trigger: ").append(safe(e.originTriggerType())).append("\n");
            }
            if (e.originTemplateId() != null && !e.originTemplateId().isBlank()) {
                sb.append("TemplateId: ").append(safe(e.originTemplateId())).append("\n");
            }
            if (e.originScheduleId() != null && !e.originScheduleId().isBlank()) {
                sb.append("ScheduleId: ").append(safe(e.originScheduleId())).append("\n");
            }
            if (e.originRecurrenceMinutes() > 0L) {
                sb.append("Recurrence: ").append(e.originRecurrenceMinutes()).append(" min\n");
            }
            if (e.originRetryAttempt() > 0) {
                sb.append("RetryAttempt: ").append(e.originRetryAttempt()).append("\n");
            }
        }

        if (e.commandId() != null && !e.commandId().isBlank()) {
            sb.append("CommandId: ").append(safe(e.commandId())).append("\n");
        }
        if (e.requestSources() != null && !e.requestSources().isEmpty()) {
            sb.append("Sources:\n");
            for (String s : e.requestSources()) sb.append("  ").append(s).append("\n");
        }
        return sb.toString();
    }

    private enum RetryMode {
        FAILED_ONLY("failed"),
        SKIPPED_ONLY("skipped"),
        FAILED_AND_SKIPPED("failed+skipped"),
        ALL("all");

        final String label;
        RetryMode(String label) { this.label = label; }
    }

/**
 * buildRetryMenu.
 *
 * @param anchorForClose TODO
 * @return TODO
 */
    private Menu buildRetryMenu(Control anchorForClose) {
        Menu m = new Menu("Retry");
        MenuItem failed = new MenuItem("Retry failed only");
        failed.setOnAction(e -> retrySelected(RetryMode.FAILED_ONLY));

        MenuItem skipped = new MenuItem("Retry skipped only");
        skipped.setOnAction(e -> retrySelected(RetryMode.SKIPPED_ONLY));

        MenuItem failedSkipped = new MenuItem("Retry failed + skipped");
        failedSkipped.setOnAction(e -> retrySelected(RetryMode.FAILED_AND_SKIPPED));

        MenuItem all = new MenuItem("Retry all");
        all.setOnAction(e -> retrySelected(RetryMode.ALL));

        m.getItems().addAll(failed, skipped, failedSkipped, new SeparatorMenuItem(), all);
        return m;
    }


// --- Phase 3.9.6: atomic batch undo/redo (best-effort) ---
/**
 * buildUndoMenu.
 *
 * @param anchorForClose TODO
 * @return TODO
 */
private Menu buildUndoMenu(Control anchorForClose) {
    Menu m = new Menu("Undo/Redo");

    MenuItem undo = new MenuItem("Undo (atomic)");
    undo.setOnAction(e -> undoSelectedAtomic());

    MenuItem redo = new MenuItem("Redo (atomic)");
    redo.setOnAction(e -> redoSelectedAtomic());

    m.getItems().addAll(undo, redo);
    return m;
}

private 
/**
 * undoSelectedAtomic.
 *
 */
void undoSelectedAtomic() {
    if (context == null || context.commandManager() == null) return;
    try {
        if (!context.commandManager().canUndo()) {
            infoAlert("Undo", "Nothing to undo.");
            return;
        }
        context.commandManager().undo();
        refreshNow();
    } catch (Exception ex) {
        warnAlert("Undo failed", ex);
    }
}


private 
/**
 * redoSelectedAtomic.
 *
 */
void redoSelectedAtomic() {
    if (context == null || context.commandManager() == null) return;
    try {
        if (!context.commandManager().canRedo()) {
            infoAlert("Redo", "Nothing to redo.");
            return;
        }
        context.commandManager().redo();
        refreshNow();
    } catch (Exception ex) {
        warnAlert("Redo failed", ex);
    }
}


/**
 * canUndo.
 *
 * @param e TODO
 * @return TODO
 */
private boolean canUndo(OperationHistoryEntry e) {
    if (e == null || e.type() == null) return false;
    if (e.requestSources() == null || e.requestSources().isEmpty()) return false;

    // DELETE undo is supported only when the original delete was "send to trash" and the item is present in our app recycle bin.
    if (e.type() == FileOperationType.DELETE) {
        if (!e.requestSendToTrash()) return false;
        try {
            String s0 = e.requestSources().get(0);
            if (s0 == null || s0.isBlank()) return false;
            return context != null && context.operationQueueService() != null
                    && context.operationQueueService().canRestoreFromRecycleBin(Path.of(s0));
        } catch (Throwable ignored) {
            return false;
        }
    }

    return e.type() == FileOperationType.COPY || e.type() == FileOperationType.MOVE;
}

/**
 * buildInverseRequest.
 *
 * @param e TODO
 * @return TODO
 */
private FileOperationRequest buildInverseRequest(OperationHistoryEntry e) {
    try {
        if (e == null) return null;
        if (e.type() == FileOperationType.COPY) {
            // Undo COPY by deleting targets (send to trash for safety).
            Path targetDir = (e.requestTargetDirectory() == null || e.requestTargetDirectory().isBlank())
                    ? null : Path.of(e.requestTargetDirectory());
            if (targetDir == null) return null;

            String original = e.requestSources().get(0);
            if (original == null || original.isBlank()) return null;
            Path src = Path.of(original);

            String baseName = src.getFileName() == null ? "" : src.getFileName().toString();
            String currentName = (e.requestNewName() != null && !e.requestNewName().isBlank())
                    ? e.requestNewName() : baseName;
            if (currentName.isBlank()) return null;

            Path targetPath = targetDir.resolve(currentName);

            return new FileOperationRequest(
                    FileOperationType.DELETE,
                    List.of(targetPath),
                    null,
                    "",
                    false,
                    false,
                    true // send to trash by default
            );
        }

        if (e.type() == FileOperationType.MOVE) {
            // Undo MOVE by moving from target dir back to original source parent, renaming back to original name.
            Path targetDir = (e.requestTargetDirectory() == null || e.requestTargetDirectory().isBlank())
                    ? null : Path.of(e.requestTargetDirectory());
            if (targetDir == null) return null;

            String original = e.requestSources().get(0);
            if (original == null || original.isBlank()) return null;
            Path originalSrc = Path.of(original);

            Path originalParent = originalSrc.getParent();
            if (originalParent == null) return null;

            String originalName = originalSrc.getFileName() == null ? "" : originalSrc.getFileName().toString();
            String currentName = (e.requestNewName() != null && !e.requestNewName().isBlank())
                    ? e.requestNewName() : originalName;
            if (currentName.isBlank()) return null;

            Path currentPath = targetDir.resolve(currentName);

            return new FileOperationRequest(
                    FileOperationType.MOVE,
                    List.of(currentPath),
                    originalParent,
                    originalName, // rename back if needed
                    false,
                    false,
                    false
            );
        }


        if (e.type() == FileOperationType.DELETE) {
            // Phase 3.9.7: Undo DELETE (send-to-trash) by restoring from app recycle bin.
            if (!e.requestSendToTrash()) return null;

            String original = e.requestSources().get(0);
            if (original == null || original.isBlank()) return null;
            Path originalPath = Path.of(original);
            Path originalParent = originalPath.getParent();
            if (originalParent == null) return null;

            String originalName = originalPath.getFileName() == null ? "" : originalPath.getFileName().toString();
            if (originalName.isBlank()) return null;

            if (context == null || context.operationQueueService() == null) return null;
            var recycledOpt = context.operationQueueService().resolveLatestRecycled(originalPath);
            if (recycledOpt == null || recycledOpt.isEmpty()) return null;

            Path recycledPath = recycledOpt.get();

            return new FileOperationRequest(
                    FileOperationType.MOVE,
                    List.of(recycledPath),
                    originalParent,
                    originalName,
                    false,
                    false,
                    false
            );
        }
    } catch (Throwable ignored) {
    }
    return null;
}

/**
 * retrySelected.
 *
 */
    private void retrySelected() {
        retrySelected(RetryMode.FAILED_AND_SKIPPED);
    }

    private record RetryKey(FileOperationType type, String targetDir, String newName, boolean overwrite, boolean sendToTrash) {}

/**
 * retrySelected.
 *
 * @param mode TODO
 */
    private void retrySelected(RetryMode mode) {
        if (context == null) return;
        List<OperationHistoryEntry> sels = selectedEntriesExpanded();
        if (sels.isEmpty()) return;

        // Expand selection, then filter by mode.
        List<OperationHistoryEntry> filtered;
        switch (mode) {
            case FAILED_ONLY -> filtered = sels.stream().filter(e -> e != null && e.status() == OperationStatus.FAILED).toList();
            case SKIPPED_ONLY -> filtered = sels.stream().filter(e -> e != null && e.status() == OperationStatus.SKIPPED).toList();
            case FAILED_AND_SKIPPED -> filtered = sels.stream().filter(e -> e != null && (e.status() == OperationStatus.FAILED || e.status() == OperationStatus.SKIPPED)).toList();
            case ALL -> filtered = sels.stream().filter(Objects::nonNull).toList();
            default -> filtered = sels;
        }
        if (filtered.isEmpty()) return;

        // Preserve original batch order (oldest-first) when present.
        List<OperationHistoryEntry> toRetry = new ArrayList<>(filtered);
        toRetry.sort(Comparator.comparingInt(OperationHistoryEntry::batchIndex));

        boolean dangerousMove = toRetry.stream().anyMatch(s ->
                s != null && s.type() == FileOperationType.MOVE && s.status() == OperationStatus.COMPLETED);
        if (dangerousMove) {
            if (!confirm("Retry MOVE?",
                    "One or more selected operations were a MOVE and previously completed. Retrying MOVE may remove files again. Continue?")) {
                return;
            }
        }

        // Phase 3.9.5: smart requeue.
        // - When retrying items that share a compatible request signature, re-enqueue as a single multi-source request
        //   so the replay remains grouped in History.
        Map<RetryKey, List<OperationHistoryEntry>> groups = new LinkedHashMap<>();
        List<OperationHistoryEntry> singles = new ArrayList<>();

        for (OperationHistoryEntry e : toRetry) {
            if (e == null || !canRetry(e)) continue;
            FileOperationType t = e.type();
            if (t == FileOperationType.COPY || t == FileOperationType.MOVE || t == FileOperationType.DELETE) {
                String td = safe(e.requestTargetDirectory());
                String nn = safe(e.requestNewName());
                RetryKey k = new RetryKey(t, td, nn, e.requestOverwrite(), e.requestSendToTrash());
                groups.computeIfAbsent(k, kk -> new ArrayList<>()).add(e);
            } else {
                singles.add(e);
            }
        }

        // Enqueue grouped multi-source requests.
        for (var ent : groups.entrySet()) {
            RetryKey k = ent.getKey();
            List<OperationHistoryEntry> list = ent.getValue();
            if (list == null || list.isEmpty()) continue;

            List<Path> srcs = new ArrayList<>();
            for (OperationHistoryEntry e : list) {
                try {
                    if (e.requestSources() == null || e.requestSources().isEmpty()) continue;
                    String s = e.requestSources().get(0);
                    if (s == null || s.isBlank()) continue;
                    srcs.add(Path.of(s));
                } catch (Throwable ignored) {
                }
            }
            if (srcs.isEmpty()) continue;

            Path tgt = null;
            try {
                if (k.targetDir() != null && !k.targetDir().isBlank()) tgt = Path.of(k.targetDir());
            } catch (Throwable ignored) {
            }

            // Label: Retry (<mode>): <original label>
            String originalLabel = bestEffortOriginalLabel(list);
            String label = "Retry (" + mode.label + "): " + originalLabel;

            FileOperationRequest req = new FileOperationRequest(
                    k.type(),
                    srcs,
                    tgt,
                    k.newName(),
                    k.overwrite(),
                    false,
                    k.sendToTrash()
            );

            // Only bother overriding label when it will form a batch.
            if (srcs.size() > 1) {
                context.operationQueueService().enqueue(req, label);
            } else {
                context.operationQueueService().enqueue(req);
            }
        }

        // Enqueue remaining single requests.
        for (OperationHistoryEntry e : singles) {
            try {
                FileOperationType type = e.type();
                List<Path> sources = e.requestSources().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(Path::of)
                        .collect(Collectors.toList());
                String targetDir = e.requestTargetDirectory();
                Path tgt = (targetDir == null || targetDir.isBlank()) ? null : Path.of(targetDir);

                FileOperationRequest req = new FileOperationRequest(
                        type,
                        sources,
                        tgt,
                        e.requestNewName() == null ? "" : e.requestNewName(),
                        e.requestOverwrite(),
                        false,
                        e.requestSendToTrash()
                );
                context.operationQueueService().enqueue(req);
            } catch (Throwable ignored) {
                // best-effort
            }
        }
    }

/**
 * bestEffortOriginalLabel.
 *
 * @param list TODO
 * @return TODO
 */
    private String bestEffortOriginalLabel(List<OperationHistoryEntry> list) {
        if (list == null || list.isEmpty()) return "Operation";
        for (OperationHistoryEntry e : list) {
            if (e != null && e.batchLabel() != null && !e.batchLabel().isBlank()) return e.batchLabel().trim();
        }
        OperationHistoryEntry first = list.get(0);
        if (first == null || first.type() == null) return "Operation";
        String t = first.type().name();
        int n = list.size();
        String tgt = safe(first.requestTargetDirectory());
        if (first.type() == FileOperationType.DELETE) return "Delete " + n + " item" + (n == 1 ? "" : "s");
        return t + " " + n + " item" + (n == 1 ? "" : "s") + (tgt.isBlank() ? "" : (" → " + tgt));
    }

/**
 * confirm.
 *
 * @param title TODO
 * @param msg TODO
 * @return TODO
 */
private boolean confirm(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(msg);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    private static String safe(String s) { return s == null ? "" : s; }

/**
 * str.
 *
 * @param s TODO
 * @return TODO
 */
    private static javafx.beans.property.SimpleStringProperty str(String s) {
        return new javafx.beans.property.SimpleStringProperty(s == null ? "" : s);
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "";
        long s = ms / 1000;
        if (s < 60) return s + "s";
        long m = s / 60;
        long r = s % 60;
        return m + "m " + r + "s";
    }

/**
 * formatBytes.
 *
 * @param p TODO
 * @param t TODO
 * @return TODO
 */
    private static String formatBytes(long p, long t) {
        if (t <= 0 && p <= 0) return "";
        return human(p) + (t > 0 ? (" / " + human(t)) : "");
    }

    private static String human(long bytes) {
        if (bytes < 0) return "";
        double b = (double) bytes;
        String[] units = {"B","KB","MB","GB","TB"};
        int u = 0;
        while (b >= 1024 && u < units.length - 1) { b /= 1024; u++; }
        return String.format(Locale.US, "%.1f %s", b, units[u]);
    }
}
