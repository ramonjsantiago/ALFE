package com.fileexplorer.controller;

import com.fileexplorer.service.diag.DiagnosticsBundleService;
import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.service.template.OperationTemplate;
import com.fileexplorer.service.template.OperationTemplateService;
import com.fileexplorer.service.template.TemplateRunHistoryEntry;
import com.fileexplorer.service.template.TemplateRunHistoryService;
import com.fileexplorer.service.template.SchedulerSettings;
import com.fileexplorer.service.template.TemplateSchedulerService;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Phase 5.3.1: Scheduler dashboard polish + validation.
 *
 * <p>Deliberately implemented without FXML to minimize wiring risk.</p>
 */
public final class SchedulerDashboardController {

    /** Preferences node for Scheduler dashboard UI state (Phase 5.3.2). */
    private static final Preferences PREFS = Preferences.userRoot().node("com/fileexplorer/ui/scheduler");

    private static final String K_STAGE_X = "stage.x";
    private static final String K_STAGE_Y = "stage.y";
    private static final String K_STAGE_W = "stage.w";
    private static final String K_STAGE_H = "stage.h";
    private static final String K_SPLIT_DIV = "split.div";
    private static final String K_FILTER = "filter";
    private static final String K_HIST_STATUS = "history.status";
    private static final String K_HIST_FROM = "history.from";
    private static final String K_HIST_TO = "history.to";
    private static final String K_SELECTED_TEMPLATE_ID = "selected.templateId";
    private static final String K_SORT_COL = "sort.col";
    private static final String K_SORT_DIR = "sort.dir";

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private SchedulerDashboardController() {}

    /**
     * Shows the Scheduler dashboard.
     */
    public static void show(Window owner, ExplorerContext context) {
        if (context == null) return;

        OperationTemplateService templates = context.operationTemplateService();
        TemplateSchedulerService scheduler = context.templateSchedulerService();
        TemplateRunHistoryService history = context.templateRunHistoryService();

        // --- Top controls (filter + buttons)
        TextField filterField = new TextField();
        filterField.setPromptText("Filter templates (name or id)…");
        filterField.setPrefColumnCount(24);


        // --- History filter controls (Phase 5.5.2)
        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().setAll("ALL", "INFO", "SUCCESS", "WARN", "ERROR");
        statusBox.setValue(PREFS.get(K_HIST_STATUS, "ALL"));
        statusBox.setPrefWidth(110);

        DatePicker fromPicker = new DatePicker();
        DatePicker toPicker = new DatePicker();
        String fromPref = PREFS.get(K_HIST_FROM, "");
        String toPref = PREFS.get(K_HIST_TO, "");
        if (fromPref != null && !fromPref.isBlank()) {
            try { fromPicker.setValue(LocalDate.parse(fromPref)); } catch (Throwable ignored) {}
        }
        if (toPref != null && !toPref.isBlank()) {
            try { toPicker.setValue(LocalDate.parse(toPref)); } catch (Throwable ignored) {}
        }
        filterField.setText(PREFS.get(K_FILTER, ""));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setTooltip(new Tooltip("Refresh schedules and history (F5)"));

        Button settingsBtn = new Button("Settings…");
        settingsBtn.setTooltip(new Tooltip("Scheduler settings (tick, concurrency, retry policy, retention)"));

        Button maintenanceBtn = new Button("Maintenance…");
        maintenanceBtn.setTooltip(new Tooltip("Maintenance tools: validate schedules, recompute next run, trim history"));
        Button supportBtn = new Button("Support Bundle…");
        supportBtn.setTooltip(new Tooltip("Generate a diagnostics ZIP bundle for troubleshooting"));


        // --- Table
        TableView<ScheduleRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No templates or schedules match the current filter."));

        TableColumn<ScheduleRow, String> nameCol = new TableColumn<>("Template");
        nameCol.setId("template");
        nameCol.setCellValueFactory(cd -> cd.getValue().templateName);

        TableColumn<ScheduleRow, String> idCol = new TableColumn<>("Id");
        idCol.setId("id");
        idCol.setCellValueFactory(cd -> cd.getValue().templateId);

        TableColumn<ScheduleRow, Number> everyCol = new TableColumn<>("Every (min)");
        everyCol.setId("every");
        everyCol.setCellValueFactory(cd -> cd.getValue().periodMinutes);
        everyCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    return;
                }
                long v = item == null ? 0 : item.longValue();
                setText(v > 0 ? Long.toString(v) : "—");
            }
        });

        TableColumn<ScheduleRow, Boolean> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setId("enabled");
        enabledCol.setCellValueFactory(cd -> cd.getValue().enabled);
        enabledCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(Boolean.TRUE.equals(item) ? "Yes" : "No");
                }
            }
        });

        TableColumn<ScheduleRow, String> lastCol = new TableColumn<>("Last run");
        lastCol.setId("last");
        lastCol.setCellValueFactory(cd -> cd.getValue().lastRun);
        lastCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null || item.isBlank() ? "—" : item);
            }
        });

        TableColumn<ScheduleRow, String> nextCol = new TableColumn<>("Next run");
        nextCol.setId("next");
        nextCol.setCellValueFactory(cd -> cd.getValue().nextRun);
        nextCol.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null || item.isBlank() ? "—" : item);
            }
        });

        table.getColumns().addAll(nameCol, idCol, everyCol, enabledCol, lastCol, nextCol);

        // --- Recent activity (run history)
        ListView<TemplateRunHistoryEntry> recentList = new ListView<>();
        recentList.setPlaceholder(new Label("No scheduler activity yet."));
        recentList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TemplateRunHistoryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(TemplateRunHistoryService.formatForUi(item));
                }
            }
        });

        // --- Backing lists with filtering
        ObservableList<ScheduleRow> allRows = FXCollections.observableArrayList();
        FilteredList<ScheduleRow> filteredRows = new FilteredList<>(allRows, r -> true);
        SortedList<ScheduleRow> sortedRows = new SortedList<>(filteredRows);
        sortedRows.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedRows);

        ObservableList<TemplateRunHistoryEntry> allRecentEntries = FXCollections.observableArrayList();
        FilteredList<TemplateRunHistoryEntry> filteredRecent = new FilteredList<>(allRecentEntries, e -> true);
        recentList.setItems(filteredRecent);

        Runnable applyFilter = () -> {
            String q = safeLower(filterField.getText());

            // Schedules (table) filter uses only the free-text query.
            if (q.isBlank()) {
                filteredRows.setPredicate(r -> true);
            } else {
                filteredRows.setPredicate(r -> {
                    if (r == null) return false;
                    return r.lcName.contains(q) || r.lcId.contains(q);
                });
            }

            // History filters (Phase 5.5.2): status + date range + free-text query.
            String statusSel = statusBox.getValue();
            if (statusSel == null || statusSel.isBlank()) statusSel = "ALL";

            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            long fromMs = (from == null)
                    ? Long.MIN_VALUE
                    : from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long toMs = (to == null)
                    ? Long.MAX_VALUE
                    : to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;

            final String statusFinal = statusSel;
            filteredRecent.setPredicate(e -> {
                if (e == null) return false;
                if (!"ALL".equalsIgnoreCase(statusFinal) && (e.status() == null || !statusFinal.equalsIgnoreCase(e.status()))) {
                    return false;
                }
                long ts = e.timestampMillis();
                if (ts < fromMs || ts > toMs) return false;

                if (q.isBlank()) return true;

                return safeLower(e.templateName()).contains(q)
                        || safeLower(e.templateId()).contains(q)
                        || safeLower(e.status()).contains(q)
                        || safeLower(e.detail()).contains(q);
            });
        };

        // Phase 5.3.3: debounce filter to avoid predicate churn on every keystroke.
        PauseTransition filterDebounce = new PauseTransition(javafx.util.Duration.millis(180));
        filterDebounce.setOnFinished(ev -> applyFilter.run());

        filterField.textProperty().addListener((obs, o, n) -> filterDebounce.playFromStart());

        statusBox.valueProperty().addListener((obs, o, n) -> {
            PREFS.put(K_HIST_STATUS, n == null ? "ALL" : n);
            filterDebounce.playFromStart();
        });
        fromPicker.valueProperty().addListener((obs, o, n) -> {
            PREFS.put(K_HIST_FROM, n == null ? "" : n.toString());
            filterDebounce.playFromStart();
        });
        toPicker.valueProperty().addListener((obs, o, n) -> {
            PREFS.put(K_HIST_TO, n == null ? "" : n.toString());
            filterDebounce.playFromStart();
        });

        // --- Refresh
        Runnable refresh = () -> {
            Map<String, OperationTemplate> byId = new HashMap<>();
            for (OperationTemplate t : templates.list()) {
                byId.put(t.id(), t);
            }

            // Merge: show rows for templates with schedules + templates that exist (so they can be scheduled from here).
            Set<String> ids = new LinkedHashSet<>();
            ids.addAll(context.templateRecurringScheduleService().listRecurringMinutes().keySet());
            ids.addAll(byId.keySet());

            List<ScheduleRow> rows = new ArrayList<>(ids.size());
            long now = System.currentTimeMillis();
            for (String id : ids) {
                OperationTemplate t = byId.get(id);
                String name = t == null ? id : t.name();

                long period = scheduler.recurringMinutes(id).isPresent() ? scheduler.recurringMinutes(id).getAsLong() : 0L;
                boolean enabled = period > 0;

                long lastTs = lastRunTs(history, id);
                String last = lastTs <= 0 ? "" : TS_FMT.format(Instant.ofEpochMilli(lastTs));

                String next = "";
                if (enabled) {
                    long nextTs = scheduler.nextDueMillis(id).isPresent()
                            ? scheduler.nextDueMillis(id).getAsLong()
                            : ((lastTs > 0 ? lastTs : now) + (period * 60_000L));
                    next = formatTimestampWithRelative(nextTs, now);
                }

                rows.add(new ScheduleRow(name, id, period, enabled, last, next));
            }

            // Default sorting: enabled first, then by name.
            rows.sort(Comparator
                    .comparing((ScheduleRow r) -> !r.enabled.get())
                    .thenComparing(r -> r.templateName.get() == null ? "" : r.templateName.get(), String.CASE_INSENSITIVE_ORDER));

            allRows.setAll(rows);

            List<TemplateRunHistoryEntry> recent = history.listRecentAll(500);
            allRecentEntries.setAll(recent);

            applyFilter.run();
        };

        // Phase 5.3.3: coalesce refresh requests (e.g., after edits) to keep UI responsive.
        PauseTransition refreshDebounce = new PauseTransition(javafx.util.Duration.millis(150));
        final SimpleBooleanProperty refreshInFlight = new SimpleBooleanProperty(false);
        refreshDebounce.setOnFinished(ev -> {
            if (refreshInFlight.get()) return;
            refreshInFlight.set(true);
            try {
                refresh.run();
            } finally {
                refreshInFlight.set(false);
            }
        });
        Runnable requestRefresh = () -> refreshDebounce.playFromStart();

        refreshBtn.setOnAction(ev -> requestRefresh.run());

        settingsBtn.setOnAction(ev -> {
            SchedulerSettings current = scheduler.getSettings();
            var next = promptSchedulerSettings(owner, current);
            next.ifPresent(s -> {
                scheduler.applySettings(s);
                requestRefresh.run();
            });
        });

        maintenanceBtn.setOnAction(ev -> {
            var choice = promptMaintenance(owner);
            if (choice == null) return;
            switch (choice) {
                case VALIDATE_REPAIR -> {
                    var r = scheduler.maintenanceValidateAndRepairSchedules();
                    alert(owner, "Validate schedules", "Total: " + r.total() + "\nRemoved: " + r.removed() + "\nRepaired: " + r.repaired() + "\nErrors: " + r.errors());
                    requestRefresh.run();
                }
                case RECOMPUTE_NEXT_DUE -> {
                    scheduler.maintenanceRecomputeNextDueAll();
                    alert(owner, "Recompute next run", "Recomputed next run times for all enabled schedules.");
                    requestRefresh.run();
                }
                case TRIM_HISTORY -> {
                    boolean attempted = scheduler.maintenanceTrimHistoryNow();
                    alert(owner, "Trim history", attempted ? "Trim requested." : "No history file found to trim.");
                    requestRefresh.run();
                }
            }
        });

        supportBtn.setOnAction(ev -> {
            try {
                DiagnosticsBundleService svc = new DiagnosticsBundleService();
                FileChooser fc = new FileChooser();
                fc.setTitle("Save support bundle");
                fc.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
                fc.setInitialFileName(svc.defaultFileName());
                File out = fc.showSaveDialog(owner);
                if (out == null) return;
                svc.generate(context, out.toPath());
                ClipboardContent cc = new ClipboardContent();
                cc.putString(out.getAbsolutePath());
                Clipboard.getSystemClipboard().setContent(cc);
                alert(owner, "Support bundle", "Created: " + out.getAbsolutePath() + "\n(Path copied to clipboard)");
            } catch (IOException ex) {
                alert(owner, "Support bundle", "Failed: " + ex.getMessage());
            }
        });

        // --- Action buttons
        Button runNowBtn = new Button("Run now");
        runNowBtn.setTooltip(new Tooltip("Execute the selected template immediately (Enter)"));
        runNowBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button enableDisableBtn = new Button();
        enableDisableBtn.setTooltip(new Tooltip("Enable or disable the selected recurring schedule"));
        enableDisableBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        enableDisableBtn.textProperty().bind(Bindings.createStringBinding(() -> {
            ScheduleRow r = table.getSelectionModel().getSelectedItem();
            if (r == null) return "Enable";
            return r.enabled.get() ? "Disable" : "Enable";
        }, table.getSelectionModel().selectedItemProperty()));

        Button editBtn = new Button("Edit…");
        editBtn.setTooltip(new Tooltip("Edit recurrence for the selected template (Ctrl+E)"));
        editBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button deleteBtn = new Button("Delete schedule");
        deleteBtn.setTooltip(new Tooltip("Remove the recurring schedule (Delete)"));
        deleteBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        runNowBtn.setOnAction(e -> {
            ScheduleRow r = table.getSelectionModel().getSelectedItem();
            if (r == null) return;
            scheduler.runNow(r.templateId.get());
        });

        enableDisableBtn.setOnAction(e -> {
            ScheduleRow r = table.getSelectionModel().getSelectedItem();
            if (r == null) return;
            String id = r.templateId.get();

            if (r.enabled.get()) {
                if (confirm(owner, "Disable schedule", "Disable recurring schedule for '" + r.templateName.get() + "'?")) {
                    scheduler.cancelRecurring(id);
                    requestRefresh.run();
                }
                return;
            }

            Optional<Long> minutes = promptMinutes(owner, "Enable schedule", "Recurring every … minutes", 60, false);
            minutes.ifPresent(m -> {
                if (m > 0) scheduler.scheduleRecurringEveryMinutes(id, m);
                requestRefresh.run();
            });
        });

        editBtn.setOnAction(e -> {
            ScheduleRow r = table.getSelectionModel().getSelectedItem();
            if (r == null) return;
            String id = r.templateId.get();
            long current = r.periodMinutes.get();
            long def = current > 0 ? current : 60;

            Optional<Long> minutes = promptMinutes(owner, "Edit recurrence", "Every … minutes", def, true);
            minutes.ifPresent(m -> {
                if (m <= 0) {
                    if (!r.enabled.get() || confirm(owner, "Disable schedule", "Set recurrence to 0 and disable schedule for '" + r.templateName.get() + "'?")) {
                        scheduler.cancelRecurring(id);
                    }
                } else {
                    scheduler.scheduleRecurringEveryMinutes(id, m);
                }
                requestRefresh.run();
            });
        });

        deleteBtn.setOnAction(e -> {
            ScheduleRow r = table.getSelectionModel().getSelectedItem();
            if (r == null) return;
            if (confirm(owner, "Delete schedule", "Remove recurring schedule for '" + r.templateName.get() + "'?")) {
                scheduler.cancelRecurring(r.templateId.get());
                requestRefresh.run();
            }
        });

        HBox controls = new HBox(10,
                filterLabeled(filterField),
                labeled("Status", statusBox),
                labeled("From", fromPicker),
                labeled("To", toPicker),
                spacer(),
                refreshBtn,
                settingsBtn,
                maintenanceBtn,
                runNowBtn,
                enableDisableBtn,
                editBtn,
                deleteBtn);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(8));

        // --- Files info
        Label schedFile = new Label("Schedules: " + context.templateRecurringScheduleService().schedulesFile());
        Label histFile = new Label("History: " + history.historyFile());
        VBox files = new VBox(2, schedFile, histFile);
        files.setPadding(new Insets(6, 10, 10, 10));

        SplitPane split = new SplitPane();
        split.getItems().addAll(
                new VBox(8, new Label("Recurring schedules"), table),
                new VBox(8, new Label("Recent scheduler activity"), recentList)
        );
        split.setDividerPositions(clamp01(PREFS.getDouble(K_SPLIT_DIV, 0.60)));

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(controls);
        root.setCenter(split);
        root.setBottom(files);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle("Scheduler");
        double w = PREFS.getDouble(K_STAGE_W, 1180);
        double h = PREFS.getDouble(K_STAGE_H, 600);
        Scene scene = new Scene(root, Math.max(800, w), Math.max(500, h));
        stage.setScene(scene);

        // Restore window position (if previously persisted)
        double x = PREFS.getDouble(K_STAGE_X, Double.NaN);
        double y = PREFS.getDouble(K_STAGE_Y, Double.NaN);
        if (!Double.isNaN(x)) stage.setX(x);
        if (!Double.isNaN(y)) stage.setY(y);

        // Keyboard shortcuts
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), requestRefresh);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), filterField::requestFocus);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN), () -> {
            if (!editBtn.isDisabled()) editBtn.fire();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ENTER), () -> {
            if (!runNowBtn.isDisabled()) runNowBtn.fire();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DELETE), () -> {
            if (!deleteBtn.isDisabled()) deleteBtn.fire();
        });

        // First load
        refresh.run();

        // Restore sort and selection after first load.
        restoreSort(table, nameCol, idCol, everyCol, enabledCol, lastCol, nextCol);
        restoreSelection(table);

        // Persist UI state changes.
        installPersistence(stage, split, filterField, table, nameCol, idCol, everyCol, enabledCol, lastCol, nextCol);

        stage.show();
        filterField.requestFocus();
    }

    private static void installPersistence(
            Stage stage,
            SplitPane split,
            TextField filterField,
            TableView<ScheduleRow> table,
            TableColumn<?, ?> nameCol,
            TableColumn<?, ?> idCol,
            TableColumn<?, ?> everyCol,
            TableColumn<?, ?> enabledCol,
            TableColumn<?, ?> lastCol,
            TableColumn<?, ?> nextCol
    ) {
        // Persist filter as user types.
        filterField.textProperty().addListener((obs, o, n) -> {
            PREFS.put(K_FILTER, n == null ? "" : n);
            flushQuietly();
        });

        // Persist selection changes.
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == null || n.templateId == null) {
                PREFS.remove(K_SELECTED_TEMPLATE_ID);
            } else {
                PREFS.put(K_SELECTED_TEMPLATE_ID, String.valueOf(n.templateId.get()));
            }
            flushQuietly();
        });

        // Persist sort changes.
        table.sortPolicyProperty().addListener((obs, o, n) -> persistSort(table));
        table.getSortOrder().addListener((javafx.collections.ListChangeListener<TableColumn<ScheduleRow, ?>>) c -> persistSort(table));

        // Persist column widths.
        persistColWidthOnChange(nameCol);
        persistColWidthOnChange(idCol);
        persistColWidthOnChange(everyCol);
        persistColWidthOnChange(enabledCol);
        persistColWidthOnChange(lastCol);
        persistColWidthOnChange(nextCol);

        // Persist divider.
        if (!split.getDividers().isEmpty()) {
            split.getDividers().get(0).positionProperty().addListener((obs, o, n) -> {
                if (n != null) {
                    PREFS.putDouble(K_SPLIT_DIV, clamp01(n.doubleValue()));
                    flushQuietly();
                }
            });
        }

        // Persist stage geometry on hide (single write).
        stage.setOnHiding(e -> {
            PREFS.putDouble(K_STAGE_X, stage.getX());
            PREFS.putDouble(K_STAGE_Y, stage.getY());
            PREFS.putDouble(K_STAGE_W, stage.getWidth());
            PREFS.putDouble(K_STAGE_H, stage.getHeight());
            flushQuietly();
        });

        // Apply saved column widths immediately (if present).
        restoreColWidth(nameCol);
        restoreColWidth(idCol);
        restoreColWidth(everyCol);
        restoreColWidth(enabledCol);
        restoreColWidth(lastCol);
        restoreColWidth(nextCol);
    }

    private static void persistColWidthOnChange(TableColumn<?, ?> col) {
        if (col == null || col.getId() == null) return;
        col.widthProperty().addListener((obs, o, n) -> {
            if (n != null) {
                PREFS.putDouble("col." + col.getId() + ".w", Math.max(40, n.doubleValue()));
                flushQuietly();
            }
        });
    }

    private static void restoreColWidth(TableColumn<?, ?> col) {
        if (col == null || col.getId() == null) return;
        double w = PREFS.getDouble("col." + col.getId() + ".w", -1);
        if (w > 0) col.setPrefWidth(w);
    }

    private static void persistSort(TableView<ScheduleRow> table) {
        if (table == null) return;
        if (table.getSortOrder().isEmpty()) {
            PREFS.remove(K_SORT_COL);
            PREFS.remove(K_SORT_DIR);
            flushQuietly();
            return;
        }
        TableColumn<ScheduleRow, ?> c = table.getSortOrder().get(0);
        String id = c == null ? null : c.getId();
        if (id == null || id.isBlank()) {
            PREFS.remove(K_SORT_COL);
            PREFS.remove(K_SORT_DIR);
            flushQuietly();
            return;
        }
        PREFS.put(K_SORT_COL, id);
        PREFS.put(K_SORT_DIR, c.getSortType() == null ? "ASC" : c.getSortType().name());
        flushQuietly();
    }

    private static void restoreSort(
            TableView<ScheduleRow> table,
            TableColumn<ScheduleRow, ?> nameCol,
            TableColumn<ScheduleRow, ?> idCol,
            TableColumn<ScheduleRow, ?> everyCol,
            TableColumn<ScheduleRow, ?> enabledCol,
            TableColumn<ScheduleRow, ?> lastCol,
            TableColumn<ScheduleRow, ?> nextCol
    ) {
        String colId = PREFS.get(K_SORT_COL, "");
        if (colId == null || colId.isBlank()) return;
        TableColumn<ScheduleRow, ?> col = switch (colId) {
            case "template" -> nameCol;
            case "id" -> idCol;
            case "every" -> everyCol;
            case "enabled" -> enabledCol;
            case "last" -> lastCol;
            case "next" -> nextCol;
            default -> null;
        };
        if (col == null) return;

        String dir = PREFS.get(K_SORT_DIR, "ASC");
        try {
            col.setSortType(TableColumn.SortType.valueOf(dir));
        } catch (Exception ignored) {
            col.setSortType(TableColumn.SortType.ASCENDING);
        }
        table.getSortOrder().setAll(col);
        table.sort();
    }

    private static void restoreSelection(TableView<ScheduleRow> table) {
        String id = PREFS.get(K_SELECTED_TEMPLATE_ID, "");
        if (id == null || id.isBlank()) return;
        for (int i = 0; i < table.getItems().size(); i++) {
            ScheduleRow r = table.getItems().get(i);
            if (r != null && r.templateId != null && id.equals(r.templateId.get())) {
                table.getSelectionModel().select(i);
                table.scrollTo(i);
                return;
            }
        }
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.60;
        if (v < 0.05) return 0.05;
        if (v > 0.95) return 0.95;
        return v;
    }

    private static void flushQuietly() {
        try {
            PREFS.flush();
        } catch (Exception ignored) {
            // Best-effort only.
        }
    }

    private static Node spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static Node filterLabeled(TextField field) {
        Label l = new Label("Filter:");
        l.setMinWidth(45);
        HBox box = new HBox(6, l, field);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }


    private static Node labeled(String label, Node control) {
        Label l = new Label(label + ":");
        HBox box = new HBox(6, l, control);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static boolean confirm(Window owner, String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(title);
        Optional<ButtonType> res = a.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
    }

    private static long lastRunTs(TemplateRunHistoryService history, String templateId) {
        try {
            List<TemplateRunHistoryEntry> recent = history.listRecent(templateId, 50);
            if (recent.isEmpty()) return -1;
            return recent.get(recent.size() - 1).timestampMillis();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String formatTimestampWithRelative(long tsMillis, long nowMillis) {
        String abs = TS_FMT.format(Instant.ofEpochMilli(tsMillis));
        long delta = tsMillis - nowMillis;
        if (Math.abs(delta) < 60_000L) return abs + " (soon)";
        Duration d = Duration.ofMillis(Math.abs(delta));
        long days = d.toDays();
        long hours = d.toHoursPart();
        long mins = d.toMinutesPart();
        String rel;
        if (days > 0) rel = days + "d " + hours + "h";
        else if (hours > 0) rel = hours + "h " + mins + "m";
        else rel = d.toMinutes() + "m";
        return abs + (delta >= 0 ? " (in " + rel + ")" : " (" + rel + " ago)" );
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Recurrence dialog with built-in input validation.
     */
    private static Optional<Long> promptMinutes(Window owner, String title, String header, long defaultMinutes, boolean allowZero) {
        Dialog<Long> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle(title);
        d.setHeaderText(header);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        long def = Math.max(allowZero ? 0 : 1, defaultMinutes);
        long min = allowZero ? 0 : 1;
        long max = 525_600; // 365 days in minutes

        // JavaFX provides Integer/Double value factories but not Long.
        // Our bounds fit safely in an int (<= 525,600), so we use Integer and parse as long.
        Spinner<Integer> minutes = new Spinner<>();
        SpinnerValueFactory.IntegerSpinnerValueFactory vf = new SpinnerValueFactory.IntegerSpinnerValueFactory(
                (int) min, (int) max, (int) def, 1
        );
        minutes.setValueFactory(vf);
        minutes.setEditable(true);
        minutes.setPrefWidth(160);

        Label hint = new Label(allowZero
                ? "Minutes: 0 disables the schedule. Range: 0–" + max + "."
                : "Minutes must be ≥ 1. Range: 1–" + max + ".");

        Button quick15 = new Button("15");
        Button quick60 = new Button("60");
        Button quick1440 = new Button("1440");
        quick15.setOnAction(e -> vf.setValue(15));
        quick60.setOnAction(e -> vf.setValue(60));
        quick1440.setOnAction(e -> vf.setValue(1440));

        HBox quick = new HBox(6, new Label("Quick:"), quick15, quick60, quick1440);
        quick.setAlignment(Pos.CENTER_LEFT);

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(10));
        g.addRow(0, new Label("Every"), minutes, new Label("minutes"));
        g.addRow(1, hint);
        g.addRow(2, quick);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(90);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        ColumnConstraints c3 = new ColumnConstraints();
        c3.setMinWidth(80);
        g.getColumnConstraints().addAll(c1, c2, c3);

        d.getDialogPane().setContent(g);

        // Validate editor value; disable Save when invalid.
        Node saveNode = d.getDialogPane().lookupButton(save);
        saveNode.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            try {
                String txt = minutes.getEditor().getText();
                long v = Long.parseLong(txt.trim());
                return v < min || v > max;
            } catch (Exception ex) {
                return true;
            }
        }, minutes.getEditor().textProperty()));

        d.setResultConverter(bt -> {
            if (bt != save) return null;
            try {
                return Long.parseLong(minutes.getEditor().getText().trim());
            } catch (Exception ex) {
                return null;
            }
        });

        Optional<Long> r = d.showAndWait();
        if (r.isEmpty()) return Optional.empty();
        Long v = r.get();
        if (v == null) return Optional.empty();
        if (v < min || v > max) return Optional.empty();
        return Optional.of(v);
    }

    // ============================
    // Phase 5.5.0: Reporting/export
    // ============================

    private static void exportSchedulesCsv(Window owner, ExplorerContext context) {
        Objects.requireNonNull(context, "context");
        File out = chooseSave(owner, "Export Schedules (CSV)", "schedules.csv", "CSV files", "*.csv");
        if (out == null) return;

        Map<String, OperationTemplate> byId = new HashMap<>();
        for (OperationTemplate t : context.operationTemplateService().list()) {
            byId.put(t.id(), t);
        }

        Map<String, com.fileexplorer.service.template.TemplateRecurringScheduleService.RecurringSchedule> schedules =
                context.templateRecurringScheduleService().listSchedules();

        try (BufferedWriter bw = Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8)) {
            bw.write("templateId,templateName,minutes,enabled,lastRunEpochMillis,nextDueEpochMillis,retryCount,backoffUntilEpochMillis,lastFailureCategory");
            bw.newLine();
            List<String> ids = new ArrayList<>(schedules.keySet());
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            for (String id : ids) {
                var s = schedules.get(id);
                OperationTemplate t = byId.get(id);
                String name = t == null ? "" : safe(t.name());
                bw.write(csv(id)); bw.write(",");
                bw.write(csv(name)); bw.write(",");
                bw.write(Long.toString(s.minutes())); bw.write(",");
                bw.write(s.minutes() > 0 ? "true" : "false"); bw.write(",");
                bw.write(Long.toString(s.lastRunEpochMillis())); bw.write(",");
                bw.write(Long.toString(s.nextDueEpochMillis())); bw.write(",");
                bw.write(Integer.toString(s.retryCount())); bw.write(",");
                bw.write(Long.toString(s.backoffUntilEpochMillis())); bw.write(",");
                bw.write(csv(s.lastFailureCategory()));
                bw.newLine();
            }
        } catch (IOException ex) {
            alert(owner, "Export Schedules", "Failed to export: " + ex.getMessage());
        }
    }

    private static void exportSchedulesJson(Window owner, ExplorerContext context) {
        Objects.requireNonNull(context, "context");
        File out = chooseSave(owner, "Export Schedules (JSON)", "schedules.json", "JSON files", "*.json");
        if (out == null) return;

        Map<String, OperationTemplate> byId = new HashMap<>();
        for (OperationTemplate t : context.operationTemplateService().list()) {
            byId.put(t.id(), t);
        }

        Map<String, com.fileexplorer.service.template.TemplateRecurringScheduleService.RecurringSchedule> schedules =
                context.templateRecurringScheduleService().listSchedules();

        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("{\n  \"exportedAtEpochMillis\": ").append(System.currentTimeMillis()).append(",\n");
        sb.append("  \"schedules\": [\n");

        List<String> ids = new ArrayList<>(schedules.keySet());
        ids.sort(String.CASE_INSENSITIVE_ORDER);

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            var s = schedules.get(id);
            OperationTemplate t = byId.get(id);
            String name = t == null ? null : t.name();
            sb.append("    {");
            sb.append("\"templateId\": ").append(jsonStr(id)).append(", ");
            sb.append("\"templateName\": ").append(jsonStr(name)).append(", ");
            sb.append("\"minutes\": ").append(s.minutes()).append(", ");
            sb.append("\"enabled\": ").append(s.minutes() > 0).append(", ");
            sb.append("\"lastRunEpochMillis\": ").append(s.lastRunEpochMillis()).append(", ");
            sb.append("\"nextDueEpochMillis\": ").append(s.nextDueEpochMillis()).append(", ");
            sb.append("\"retryCount\": ").append(s.retryCount()).append(", ");
            sb.append("\"backoffUntilEpochMillis\": ").append(s.backoffUntilEpochMillis()).append(", ");
            sb.append("\"lastFailureCategory\": ").append(jsonStr(s.lastFailureCategory()));
            sb.append("}");
            if (i < ids.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}");
        try {
            Files.writeString(out.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            alert(owner, "Export Schedules", "Failed to export: " + ex.getMessage());
        }
    }

    private static void exportHistoryCsv(Window owner, ExplorerContext context, String templateIdOrNull) {
        Objects.requireNonNull(context, "context");
        String suffix = (templateIdOrNull == null || templateIdOrNull.isBlank()) ? "all" : "selected";
        File out = chooseSave(owner, "Export History (CSV)", "scheduler-history-" + suffix + ".csv", "CSV files", "*.csv");
        if (out == null) return;

        List<TemplateRunHistoryEntry> entries = templateIdOrNull == null || templateIdOrNull.isBlank()
                ? context.templateRunHistoryService().listAll(20000)
                : context.templateRunHistoryService().listAll(templateIdOrNull, 20000);

        try (BufferedWriter bw = Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8)) {
            bw.write("timestampEpochMillis,templateId,templateName,status,detail,operationId");
            bw.newLine();
            for (TemplateRunHistoryEntry e : entries) {
                bw.write(Long.toString(e.timestampMillis())); bw.write(",");
                bw.write(csv(e.templateId())); bw.write(",");
                bw.write(csv(e.templateName())); bw.write(",");
                bw.write(csv(e.status())); bw.write(",");
                bw.write(csv(e.detail())); bw.write(",");
                bw.write(csv(e.operationId()));
                bw.newLine();
            }
        } catch (IOException ex) {
            alert(owner, "Export History", "Failed to export: " + ex.getMessage());
        }
    }

    private static void exportHistoryJson(Window owner, ExplorerContext context, String templateIdOrNull) {
        Objects.requireNonNull(context, "context");
        String suffix = (templateIdOrNull == null || templateIdOrNull.isBlank()) ? "all" : "selected";
        File out = chooseSave(owner, "Export History (JSON)", "scheduler-history-" + suffix + ".json", "JSON files", "*.json");
        if (out == null) return;

        List<TemplateRunHistoryEntry> entries = templateIdOrNull == null || templateIdOrNull.isBlank()
                ? context.templateRunHistoryService().listAll(20000)
                : context.templateRunHistoryService().listAll(templateIdOrNull, 20000);

        StringBuilder sb = new StringBuilder(Math.max(64 * 1024, entries.size() * 200));
        sb.append("{\n  \"exportedAtEpochMillis\": ").append(System.currentTimeMillis()).append(",\n");
        if (templateIdOrNull != null && !templateIdOrNull.isBlank()) {
            sb.append("  \"templateId\": ").append(jsonStr(templateIdOrNull)).append(",\n");
        }
        sb.append("  \"history\": [\n");
        for (int i = 0; i < entries.size(); i++) {
            TemplateRunHistoryEntry e = entries.get(i);
            sb.append("    {");
            sb.append("\"timestampEpochMillis\": ").append(e.timestampMillis()).append(", ");
            sb.append("\"templateId\": ").append(jsonStr(e.templateId())).append(", ");
            sb.append("\"templateName\": ").append(jsonStr(e.templateName())).append(", ");
            sb.append("\"status\": ").append(jsonStr(e.status())).append(", ");
            sb.append("\"detail\": ").append(jsonStr(e.detail())).append(", ");
            sb.append("\"operationId\": ").append(jsonStr(e.operationId()));
            sb.append("}");
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}");
        try {
            Files.writeString(out.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            alert(owner, "Export History", "Failed to export: " + ex.getMessage());
        }
    }

    private static void copyDiagnostics(ExplorerContext context) {
        Objects.requireNonNull(context, "context");

        StringBuilder sb = new StringBuilder(128 * 1024);
        sb.append("FileExplorer Diagnostics Bundle\n");
        sb.append("generatedAtEpochMillis=").append(System.currentTimeMillis()).append("\n\n");

        sb.append("[Environment]\n");
        sb.append("java.version=").append(System.getProperty("java.version")).append("\n");
        sb.append("os.name=").append(System.getProperty("os.name")).append("\n");
        sb.append("user.home=").append(System.getProperty("user.home")).append("\n");
        sb.append("timezone=").append(ZoneId.systemDefault()).append("\n\n");

        sb.append("[Scheduler files]\n");
        sb.append("schedulesFile=").append(context.templateRecurringScheduleService().schedulesFile()).append("\n");
        sb.append("historyFile=").append(context.templateRunHistoryService().historyFile()).append("\n\n");

        sb.append("[Schedules.json]\n");
        sb.append(buildSchedulesJson(context)).append("\n\n");

        sb.append("[History.json - recent]\n");
        sb.append(buildHistoryJson(context, null, 200)).append("\n\n");

        ClipboardContent cc = new ClipboardContent();
        cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
    }

    private static String buildSchedulesJson(ExplorerContext context) {
        Map<String, OperationTemplate> byId = new HashMap<>();
        for (OperationTemplate t : context.operationTemplateService().list()) byId.put(t.id(), t);

        Map<String, com.fileexplorer.service.template.TemplateRecurringScheduleService.RecurringSchedule> schedules =
                context.templateRecurringScheduleService().listSchedules();

        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("{\"schedules\":[");
        List<String> ids = new ArrayList<>(schedules.keySet());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            var s = schedules.get(id);
            OperationTemplate t = byId.get(id);
            String name = t == null ? null : t.name();
            if (i > 0) sb.append(",");
            sb.append("{\"templateId\":").append(jsonStr(id))
                    .append(",\"templateName\":").append(jsonStr(name))
                    .append(",\"minutes\":").append(s.minutes())
                    .append(",\"lastRunEpochMillis\":").append(s.lastRunEpochMillis())
                    .append(",\"nextDueEpochMillis\":").append(s.nextDueEpochMillis())
                    .append(",\"retryCount\":").append(s.retryCount())
                    .append(",\"backoffUntilEpochMillis\":").append(s.backoffUntilEpochMillis())
                    .append(",\"lastFailureCategory\":").append(jsonStr(s.lastFailureCategory()))
                    .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String buildHistoryJson(ExplorerContext context, String templateIdOrNull, int limit) {
        int lim = Math.max(1, Math.min(20000, limit));
        List<TemplateRunHistoryEntry> entries = templateIdOrNull == null || templateIdOrNull.isBlank()
                ? context.templateRunHistoryService().listAll(lim)
                : context.templateRunHistoryService().listAll(templateIdOrNull, lim);

        StringBuilder sb = new StringBuilder(Math.max(16 * 1024, entries.size() * 200));
        sb.append("{\"history\":[");
        for (int i = 0; i < entries.size(); i++) {
            TemplateRunHistoryEntry e = entries.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"timestampEpochMillis\":").append(e.timestampMillis())
                    .append(",\"templateId\":").append(jsonStr(e.templateId()))
                    .append(",\"templateName\":").append(jsonStr(e.templateName()))
                    .append(",\"status\":").append(jsonStr(e.status()))
                    .append(",\"detail\":").append(jsonStr(e.detail()))
                    .append(",\"operationId\":").append(jsonStr(e.operationId()))
                    .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static File chooseSave(Window owner, String title, String fileName, String filterLabel, String filterPattern) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(filterLabel, filterPattern));
        fc.setInitialFileName(fileName);
        return fc.showSaveDialog(owner);
    }

    private static void alert(Window owner, String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(msg);
        if (owner != null) a.initOwner(owner);
        com.fileexplorer.util.DialogTheme.apply(a, owner);
        a.showAndWait();
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder(s.length() + 16);
        out.append('\"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('\"');
        return out.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s;
        boolean need = v.contains(",") || v.contains("\n") || v.contains("\r") || v.contains("\"");
        v = v.replace("\"", "\"\"");
        return need ? ("\"" + v + "\"") : v;
    }



/**
 * Prompt the user to edit global scheduler settings.
 */
private static Optional<SchedulerSettings> promptSchedulerSettings(Window owner, SchedulerSettings current) {
    SchedulerSettings cur = (current == null) ? SchedulerSettings.defaults() : current;

    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.initOwner(owner);
    dialog.setTitle("Scheduler settings");
    dialog.setHeaderText("Configure global scheduler behavior");
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    // Controls
    Spinner<Integer> tickSeconds = new Spinner<>(1, 60, cur.tickSeconds());
    tickSeconds.setEditable(true);

    Spinner<Integer> maxParallel = new Spinner<>(1, 16, cur.maxParallel());
    maxParallel.setEditable(true);

    Spinner<Integer> maxRetry = new Spinner<>(0, 20, cur.maxRetryAttempts());
    maxRetry.setEditable(true);

    int baseSec = (int) Math.max(1, Math.min(86_400, cur.retryBaseMillis() / 1000L));
    int maxSec = (int) Math.max(baseSec, Math.min(86_400, cur.retryMaxMillis() / 1000L));

    Spinner<Integer> retryBaseSeconds = new Spinner<>(1, 86_400, baseSec);
    retryBaseSeconds.setEditable(true);

    Spinner<Integer> retryMaxSeconds = new Spinner<>(1, 86_400, maxSec);
    retryMaxSeconds.setEditable(true);

    Spinner<Integer> historyRetention = new Spinner<>(100, 100_000, cur.historyRetentionEntries());
    historyRetention.setEditable(true);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(10));

    int r = 0;
    grid.add(new Label("Tick cadence (seconds):"), 0, r);
    grid.add(tickSeconds, 1, r++);

    grid.add(new Label("Max concurrent scheduled runs:"), 0, r);
    grid.add(maxParallel, 1, r++);

    grid.add(new Label("Max retry attempts (recurring runs):"), 0, r);
    grid.add(maxRetry, 1, r++);

    grid.add(new Label("Retry base backoff (seconds):"), 0, r);
    grid.add(retryBaseSeconds, 1, r++);

    grid.add(new Label("Retry max backoff (seconds):"), 0, r);
    grid.add(retryMaxSeconds, 1, r++);

    grid.add(new Label("History retention (entries):"), 0, r);
    grid.add(historyRetention, 1, r++);

    Label validation = new Label();
    validation.getStyleClass().add("error-label");

    VBox root = new VBox(10, grid, validation);
    root.setPadding(new Insets(5));
    dialog.getDialogPane().setContent(root);

    Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);

    Runnable validate = () -> {
        String msg = null;
        int baseS = retryBaseSeconds.getValue();
        int maxS = retryMaxSeconds.getValue();
        if (maxS < baseS) msg = "Retry max backoff must be >= base backoff.";
        validation.setText(msg == null ? "" : msg);
        okBtn.setDisable(msg != null);
    };

    retryBaseSeconds.valueProperty().addListener((obs, o, n) -> validate.run());
    retryMaxSeconds.valueProperty().addListener((obs, o, n) -> validate.run());
    validate.run();

    Optional<ButtonType> res = dialog.showAndWait();
    if (res.isEmpty() || res.get() != ButtonType.OK) return Optional.empty();

    int tick = tickSeconds.getValue();
    int par = maxParallel.getValue();
    int mr = maxRetry.getValue();
    long baseMs = retryBaseSeconds.getValue() * 1000L;
    long maxMs = retryMaxSeconds.getValue() * 1000L;
    int hist = historyRetention.getValue();

    SchedulerSettings next = new SchedulerSettings(tick, par, mr, baseMs, maxMs, hist);
    return Optional.of(next);
}

    private static final class ScheduleRow {
        final SimpleStringProperty templateName;
        final SimpleStringProperty templateId;
        final String lcName;
        final String lcId;
        final SimpleLongProperty periodMinutes;
        final SimpleBooleanProperty enabled;
        final SimpleStringProperty lastRun;
        final SimpleStringProperty nextRun;

        ScheduleRow(String name, String id, long period, boolean enabled, String last, String next) {
            this.templateName = new SimpleStringProperty(name);
            this.templateId = new SimpleStringProperty(id);
            this.lcName = safeLower(name);
            this.lcId = safeLower(id);
            this.periodMinutes = new SimpleLongProperty(period);
            this.enabled = new SimpleBooleanProperty(enabled);
            this.lastRun = new SimpleStringProperty(last);
            this.nextRun = new SimpleStringProperty(next);
        }
    }
    // --- Phase 5.6.1: Maintenance tools

    private enum MaintenanceAction {
        VALIDATE_REPAIR,
        RECOMPUTE_NEXT_DUE,
        TRIM_HISTORY
    }

    private static MaintenanceAction promptMaintenance(Window owner) {
        Dialog<MaintenanceAction> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Scheduler maintenance");
        d.setHeaderText("Maintenance tools");

        ButtonType validate = new ButtonType("Validate/Repair schedules", ButtonBar.ButtonData.LEFT);
        ButtonType recompute = new ButtonType("Recompute next run", ButtonBar.ButtonData.LEFT);
        ButtonType trim = new ButtonType("Trim history now", ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().addAll(validate, recompute, trim, ButtonType.CANCEL);

        Label info = new Label("Choose an action. These are best-effort operations intended for recovery/cleanup.");
        info.setWrapText(true);
        d.getDialogPane().setContent(new VBox(10, info));

        d.setResultConverter(bt -> {
            if (bt == validate) return MaintenanceAction.VALIDATE_REPAIR;
            if (bt == recompute) return MaintenanceAction.RECOMPUTE_NEXT_DUE;
            if (bt == trim) return MaintenanceAction.TRIM_HISTORY;
            return null;
        });

        return d.showAndWait().orElse(null);
    }

}
