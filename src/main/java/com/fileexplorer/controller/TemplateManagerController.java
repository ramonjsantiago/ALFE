package com.fileexplorer.controller;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.service.ops.ExecutionDriftPolicy;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.rollback.RollbackMode;
import com.fileexplorer.service.template.OperationTemplate;
import com.fileexplorer.service.template.OperationTemplateService;
import com.fileexplorer.service.template.TemplateRunHistoryEntry;
import com.fileexplorer.service.template.TemplateRunHistoryService;
import com.fileexplorer.service.template.TemplateSchedulerService;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.fileexplorer.service.template.TemplatePackService;
import com.fileexplorer.service.template.TemplateRecurringScheduleService;
import javafx.stage.FileChooser;
import java.io.File;

/**
 * Phase 6.2.0: Template Builder UX + Validation.
 *
 * <p>Deliberately implemented without FXML to reduce wiring risk.</p>
 */
public final class TemplateManagerController {

    private TemplateManagerController() {}

    /**
     * Show the Templates window.
     *
     * @param owner owner window
     * @param context explorer context
     */
    public static void show(Window owner, ExplorerContext context) {
        if (context == null) return;

        OperationTemplateService templates = context.operationTemplateService();
        TemplateSchedulerService scheduler = context.templateSchedulerService();
        TemplateRunHistoryService history = context.templateRunHistoryService();

        TemplateRecurringScheduleService recurringStore = context.templateRecurringScheduleService();
        ListView<OperationTemplate> list = new ListView<>();
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(OperationTemplate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String rec = scheduler.recurringMinutes(item.id()).isPresent()
                            ? ("  ⟳ every " + scheduler.recurringMinutes(item.id()).getAsLong() + " min")
                            : "";
                    setText(item.name() + "  [" + item.type() + "]  (" + item.sources().size() + " src) → " + item.target() + rec);
                }
            }
        });

        Runnable refresh = () -> list.setItems(FXCollections.observableArrayList(templates.list()));
        refresh.run();

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refresh.run());

        Button createBtn = new Button("Create…");
        createBtn.setOnAction(e -> {
            Optional<OperationTemplate> t = promptCreate(owner);
            if (t.isEmpty()) return;
            if (!ensureValid(owner, templates, t.get())) return;
            templates.save(t.get());
            refresh.run();
        });

        Button editBtn = new Button("Edit…");
        editBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        editBtn.setOnAction(e -> {
            OperationTemplate current = list.getSelectionModel().getSelectedItem();
            if (current == null) return;
            Optional<OperationTemplate> updated = promptEdit(owner, current);
            if (updated.isEmpty()) return;
            if (!ensureValid(owner, templates, updated.get())) return;
            templates.save(updated.get());
            refresh.run();
        });

        Button validateBtn = new Button("Validate");
        validateBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        validateBtn.setOnAction(e -> {
            OperationTemplate t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            List<String> errors = templates.validate(t);
            Alert a = new Alert(errors.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                    OperationTemplateService.formatValidationErrors(errors), ButtonType.OK);
            a.initOwner(owner);
            a.setHeaderText(errors.isEmpty() ? "Template is valid" : "Template has issues");
            com.fileexplorer.util.DialogTheme.apply(a, null);
            com.fileexplorer.util.DialogTheme.apply(a, null);
            a.showAndWait();
        });

        Button deleteBtn = new Button("Delete");
        deleteBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        deleteBtn.setOnAction(e -> {
            OperationTemplate t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete template '" + t.name() + "'?", ButtonType.OK, ButtonType.CANCEL);
            a.initOwner(owner);
            a.setHeaderText("Confirm delete");
            Optional<ButtonType> r = a.showAndWait();
            if (r.isPresent() && r.get() == ButtonType.OK) {
                scheduler.cancelRecurring(t.id());
                templates.delete(t.id());
                refresh.run();
            }
        });

        Button runBtn = new Button("Run now");
        runBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        runBtn.setOnAction(e -> {
            OperationTemplate t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            if (!ensureValid(owner, templates, t)) return;
            scheduler.runNow(t.id());
        });

        Button scheduleBtn = new Button("Schedule +1 min");
        scheduleBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        scheduleBtn.setOnAction(e -> {
            OperationTemplate t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            if (!ensureValid(owner, templates, t)) return;
            scheduler.scheduleOnceInMinutes(t.id(), 1);
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Scheduled '" + t.name() + "' to run in 1 minute.", ButtonType.OK);
            a.initOwner(owner);
            a.setHeaderText("Scheduled");
            com.fileexplorer.util.DialogTheme.apply(a, null);
            com.fileexplorer.util.DialogTheme.apply(a, null);
            a.showAndWait();
        });

        Button recurringBtn = new Button("Recurring…");
        recurringBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        recurringBtn.setOnAction(e -> {
            OperationTemplate t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            if (!ensureValid(owner, templates, t)) return;
            Optional<Long> minutes = promptRecurring(owner, t, scheduler);
            if (minutes.isEmpty()) return;

            long m = minutes.get();
            if (m <= 0) {
                scheduler.cancelRecurring(t.id());
            } else {
                scheduler.scheduleRecurringEveryMinutes(t.id(), m);
            }
            refresh.run();
        });

        Button stopRecurringBtn = new Button("Stop recurring");
        stopRecurringBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        stopRecurringBtn.setOnAction(e -> {
            OperationTemplate t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            scheduler.cancelRecurring(t.id());
            refresh.run();
        });

        Button historyBtn = new Button("History…");
        historyBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        historyBtn.setOnAction(e -> {
            OperationTemplate t = list.getSelectionModel().getSelectedItem();
            if (t == null) return;
            showHistory(owner, t, history);
        });

        Button openDirBtn = new Button("Open folder");
        Button exportPackBtn = new Button("Export pack…");
        Button importPackBtn = new Button("Import pack…");
        openDirBtn.setOnAction(e -> {
            Path dir = templates.templatesDir();
            Alert a = new Alert(Alert.AlertType.INFORMATION, dir.toString(), ButtonType.OK);
            a.initOwner(owner);
            a.setHeaderText("Templates folder");
            com.fileexplorer.util.DialogTheme.apply(a, null);
            com.fileexplorer.util.DialogTheme.apply(a, null);
            a.showAndWait();
        });
exportPackBtn.setOnAction(ev -> {
    FileChooser fc = new FileChooser();
    fc.setTitle("Export Template Pack");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Template Pack (*.zip)", "*.zip"));
    fc.setInitialFileName("FileExplorer-templates-pack.zip");
    File out = fc.showSaveDialog(owner instanceof Stage s ? s : null);
    if (out == null) return;
    boolean includeSchedules = true;
    Alert ask = new Alert(Alert.AlertType.CONFIRMATION,
            "Include recurring schedules in the pack?\n\n(Templates are always included.)",
            ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
    ask.initOwner(owner);
    ask.setHeaderText("Export options");
    Optional<ButtonType> res = ask.showAndWait();
    if (res.isEmpty() || res.get() == ButtonType.CANCEL) return;
    includeSchedules = (res.get() == ButtonType.YES);

    try {
        TemplatePackService.exportPack(out.toPath(), templates, List.of(), recurringStore, includeSchedules);
        Alert ok = new Alert(Alert.AlertType.INFORMATION, out.getAbsolutePath(), ButtonType.OK);
        ok.initOwner(owner);
        ok.setHeaderText("Template pack exported");
        com.fileexplorer.util.DialogTheme.apply(ok, null);
        ok.showAndWait();
    } catch (Exception ex) {
        Alert err = new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()), ButtonType.OK);
        err.initOwner(owner);
        err.setHeaderText("Export failed");
        com.fileexplorer.util.DialogTheme.apply(err, null);
        err.showAndWait();
    }
});

importPackBtn.setOnAction(ev -> {
    FileChooser fc = new FileChooser();
    fc.setTitle("Import Template Pack");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Template Pack (*.zip)", "*.zip"));
    File in = fc.showOpenDialog(owner instanceof Stage s ? s : null);
    if (in == null) return;

    Alert ask = new Alert(Alert.AlertType.CONFIRMATION,
            "Import into your templates store?\n\nChoose YES to overwrite existing templates and schedules with the same ids.",
            ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
    ask.initOwner(owner);
    ask.setHeaderText("Import options");
    Optional<ButtonType> res = ask.showAndWait();
    if (res.isEmpty() || res.get() == ButtonType.CANCEL) return;
    boolean overwrite = (res.get() == ButtonType.YES);

    try {
        TemplatePackService.ImportReport r = TemplatePackService.importPack(in.toPath(), templates, recurringStore, overwrite);
        Alert ok = new Alert(Alert.AlertType.INFORMATION,
                "Templates imported: " + r.templatesImported() + "\nSchedules imported: " + r.schedulesImported(),
                ButtonType.OK);
        ok.initOwner(owner);
        ok.setHeaderText("Template pack imported");
        com.fileexplorer.util.DialogTheme.apply(ok, null);
        ok.showAndWait();
        // Refresh list
        list.setItems(FXCollections.observableArrayList(templates.list()));
    } catch (Exception ex) {
        Alert err = new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()), ButtonType.OK);
        err.initOwner(owner);
        err.setHeaderText("Import failed");
        com.fileexplorer.util.DialogTheme.apply(err, null);
        err.showAndWait();
    }
});


        HBox buttons = new HBox(8,
                refreshBtn,
                createBtn,
                editBtn,
                validateBtn,
                deleteBtn,
                runBtn,
                scheduleBtn,
                recurringBtn,
                stopRecurringBtn,
                historyBtn,
                openDirBtn,
                exportPackBtn,
                importPackBtn);
        buttons.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setCenter(list);
        root.setBottom(buttons);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle("Templates");
        stage.setScene(new Scene(root, 1040, 480));
        stage.show();
    }

    private static boolean ensureValid(Window owner, OperationTemplateService templates, OperationTemplate t) {
        List<String> errors = templates.validate(t);
        if (errors.isEmpty()) return true;
        Alert a = new Alert(Alert.AlertType.ERROR, OperationTemplateService.formatValidationErrors(errors), ButtonType.OK);
        a.initOwner(owner);
        a.setHeaderText("Template is not valid");
        com.fileexplorer.util.DialogTheme.apply(a, null);
        com.fileexplorer.util.DialogTheme.apply(a, null);
        a.showAndWait();
        return false;
    }

    private static Optional<OperationTemplate> promptCreate(Window owner) {
        Dialog<OperationTemplate> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Create Template");
        d.setHeaderText("Define a new operation template");

        ButtonType create = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);

        TextField name = new TextField("New template");
        ComboBox<FileOperationType> type = new ComboBox<>(FXCollections.observableArrayList(FileOperationType.COPY, FileOperationType.MOVE, FileOperationType.DELETE));
        type.getSelectionModel().select(FileOperationType.COPY);

        TextArea sources = new TextArea();
        sources.setPromptText("Sources (one per line or comma-separated)");
        sources.setPrefRowCount(4);

        TextField target = new TextField();
        target.setPromptText("Target directory (path)");

        ComboBox<ExecutionDriftPolicy> drift = new ComboBox<>(FXCollections.observableArrayList(ExecutionDriftPolicy.values()));
        drift.getSelectionModel().select(ExecutionDriftPolicy.FAIL_FAST);

        ComboBox<RollbackMode> rollback = new ComboBox<>(FXCollections.observableArrayList(RollbackMode.values()));
        rollback.getSelectionModel().select(RollbackMode.ASK);

        CheckBox batch = new CheckBox("Batch transaction");

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(10));
        g.addRow(0, new Label("Name"), name);
        g.addRow(1, new Label("Type"), type);
        g.addRow(2, new Label("Sources"), sources);
        g.addRow(3, new Label("Target"), target);
        g.addRow(4, new Label("Drift policy"), drift);
        g.addRow(5, new Label("Rollback"), rollback);
        g.addRow(6, new Label(""), batch);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(110);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        d.getDialogPane().setContent(g);

        d.setResultConverter(bt -> {
            if (bt != create) return null;
            String id = java.util.UUID.randomUUID().toString().replace("-", "");
            return new OperationTemplate(
                    id,
                    name.getText() == null ? "" : name.getText().trim(),
                    type.getSelectionModel().getSelectedItem(),
                    parseSources(sources.getText()),
                    target.getText() == null ? "" : target.getText().trim(),
                    null,
                    drift.getSelectionModel().getSelectedItem(),
                    rollback.getSelectionModel().getSelectedItem(),
                    batch.isSelected()
            );
        });

        return d.showAndWait();
    }

    private static Optional<OperationTemplate> promptEdit(Window owner, OperationTemplate current) {
        Dialog<OperationTemplate> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Edit Template");
        d.setHeaderText("Update template '" + current.name() + "'");

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField name = new TextField(current.name());
        ComboBox<FileOperationType> type = new ComboBox<>(FXCollections.observableArrayList(FileOperationType.values()));
        type.getSelectionModel().select(current.type());

        TextArea sources = new TextArea(String.join("\n", current.sources()));
        sources.setPromptText("Sources (one per line or comma-separated)");
        sources.setPrefRowCount(4);

        TextField target = new TextField(current.target());

        TextField conflictProfile = new TextField(current.conflictProfileId() == null ? "" : current.conflictProfileId());
        conflictProfile.setPromptText("Optional conflict profile id");

        ComboBox<ExecutionDriftPolicy> drift = new ComboBox<>(FXCollections.observableArrayList(ExecutionDriftPolicy.values()));
        drift.getSelectionModel().select(current.driftPolicy());

        ComboBox<RollbackMode> rollback = new ComboBox<>(FXCollections.observableArrayList(RollbackMode.values()));
        rollback.getSelectionModel().select(current.rollbackMode());

        CheckBox batch = new CheckBox("Batch transaction");
        batch.setSelected(current.batchTransaction());

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(10));
        g.addRow(0, new Label("Name"), name);
        g.addRow(1, new Label("Type"), type);
        g.addRow(2, new Label("Sources"), sources);
        g.addRow(3, new Label("Target"), target);
        g.addRow(4, new Label("Conflict profile"), conflictProfile);
        g.addRow(5, new Label("Drift policy"), drift);
        g.addRow(6, new Label("Rollback"), rollback);
        g.addRow(7, new Label(""), batch);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(120);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        d.getDialogPane().setContent(g);

        d.setResultConverter(bt -> {
            if (bt != save) return null;
            String cp = conflictProfile.getText() == null ? "" : conflictProfile.getText().trim();
            return new OperationTemplate(
                    current.id(),
                    name.getText() == null ? "" : name.getText().trim(),
                    type.getSelectionModel().getSelectedItem(),
                    parseSources(sources.getText()),
                    target.getText() == null ? "" : target.getText().trim(),
                    cp.isEmpty() ? null : cp,
                    drift.getSelectionModel().getSelectedItem(),
                    rollback.getSelectionModel().getSelectedItem(),
                    batch.isSelected()
            );
        });

        return d.showAndWait();
    }

    private static List<String> parseSources(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        // split on newlines and commas
        for (String line : s.split("\\r?\\n")) {
            for (String part : line.split(",")) {
                String v = part.trim();
                if (!v.isEmpty()) out.add(v);
            }
        }
        return out;
    }

    private static Optional<Long> promptRecurring(Window owner, OperationTemplate t, TemplateSchedulerService scheduler) {
        Dialog<Long> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Recurring schedule");
        d.setHeaderText("Schedule template '" + t.name() + "' to run repeatedly");

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField minutes = new TextField();
        minutes.setPromptText("Minutes");

        long current = scheduler.recurringMinutes(t.id()).isPresent() ? scheduler.recurringMinutes(t.id()).getAsLong() : 60;
        minutes.setText(Long.toString(current));

        Label hint = new Label("Enter minutes (>= 1). Use 0 to disable recurring.");

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(10));
        g.addRow(0, new Label("Every"), minutes, new Label("minutes"));
        g.addRow(1, hint);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(90);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        ColumnConstraints c3 = new ColumnConstraints();
        c3.setMinWidth(80);
        g.getColumnConstraints().addAll(c1, c2, c3);

        d.getDialogPane().setContent(g);

        d.setResultConverter(bt -> {
            if (bt != save) return null;
            try {
                return Long.parseLong(minutes.getText().trim());
            } catch (Exception ex) {
                return -1L;
            }
        });

        Optional<Long> r = d.showAndWait();
        if (r.isEmpty()) return Optional.empty();
        long v = r.get();
        if (v < 0) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Please enter a valid number of minutes (>= 0).", ButtonType.OK);
            a.initOwner(owner);
            a.setHeaderText("Invalid value");
            com.fileexplorer.util.DialogTheme.apply(a, null);
            com.fileexplorer.util.DialogTheme.apply(a, null);
            a.showAndWait();
            return Optional.empty();
        }
        return Optional.of(v);
    }

    private static void showHistory(Window owner, OperationTemplate t, TemplateRunHistoryService history) {
        List<TemplateRunHistoryEntry> entries = history.listRecent(t.id(), 200);

        ListView<String> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(entries.stream().map(TemplateRunHistoryService::formatForUi).collect(Collectors.toList())));

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setCenter(list);

        Label foot = new Label("History file: " + history.historyFile());
        foot.setPadding(new Insets(8, 0, 0, 0));
        root.setBottom(foot);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle("Template History — " + t.name());
        stage.setScene(new Scene(root, 980, 420));
        stage.show();
    }
}
