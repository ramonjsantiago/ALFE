package com.fileexplorer.controller;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.service.ops.command.BatchCommand;
import com.fileexplorer.service.ops.command.Command;
import com.fileexplorer.service.ops.command.CommandManager;
import com.fileexplorer.service.ops.command.CopyCommand;
import com.fileexplorer.service.ops.command.DeleteCommand;
import com.fileexplorer.service.ops.command.MoveCommand;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import javafx.stage.FileChooser;

import java.time.Instant;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CommandLogController.
 * <p>
 * Auto-generated API documentation for this type.
 */
public final class CommandLogController {

    private static final Logger LOG = Logger.getLogger(CommandLogController.class.getName());

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    @FXML private Label statusLabel;
    @FXML private Label analyticsLabel;
    @FXML private Label storePathLabel;

    @FXML private TextField searchField;
    @FXML private CheckBox undoableOnlyCheck;
    @FXML private CheckBox batchesOnlyCheck;

    @FXML private TableView<Row> undoTable;
    @FXML private TableColumn<Row, String> undoWhenCol;
    @FXML private TableColumn<Row, String> undoKindCol;
    @FXML private TableColumn<Row, String> undoLabelCol;
    @FXML private TableColumn<Row, Boolean> undoUndoableCol;
    @FXML private TableColumn<Row, String> undoIdCol;

    @FXML private TabPane tabs;

    @FXML private TableView<Row> redoTable;
    @FXML private TableColumn<Row, String> redoWhenCol;
    @FXML private TableColumn<Row, String> redoKindCol;
    @FXML private TableColumn<Row, String> redoLabelCol;
    @FXML private TableColumn<Row, Boolean> redoUndoableCol;
    @FXML private TableColumn<Row, String> redoIdCol;

    private ExplorerContext context;

    private List<Row> undoAll = List.of();
    private List<Row> redoAll = List.of();

    private Stage historyStage;
    private OperationHistoryController historyController;

    @FXML
/**
 * initialize.
 *
 */
    private void initialize() {
        configureColumns();
        // Note: columnResizePolicy set in code for JavaFX FXML compatibility
        if (undoTable != null) undoTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (redoTable != null) redoTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        if (searchField != null) {
            searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        }
        if (undoableOnlyCheck != null) {
            undoableOnlyCheck.selectedProperty().addListener((obs, o, n) -> applyFilters());
        }
        if (batchesOnlyCheck != null) {
            batchesOnlyCheck.selectedProperty().addListener((obs, o, n) -> applyFilters());
        }
    }

/**
 * attach.
 *
 * @param ctx TODO
 */
    void attach(ExplorerContext ctx) {
        this.context = ctx;
        refresh();
    }

    @FXML
/**
 * onRefresh.
 *
 * @param e TODO
 */
    private void onRefresh(ActionEvent e) {
        refresh();
    }

    @FXML
/**
 * onCopyCommandId.
 *
 * @param e TODO
 */
    private void onCopyCommandId(ActionEvent e) {
        Row r = selectedRow().orElse(null);
        if (r == null || r.commandId() == null || r.commandId().isBlank()) {
            info("Copy Command ID", "No command selected.");
            return;
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(r.commandId());
        Clipboard.getSystemClipboard().setContent(cc);
        statusLabel.setText("Copied " + r.commandId());
    }

    @FXML
/**
 * onShowDetails.
 *
 * @param e TODO
 */
    private void onShowDetails(ActionEvent e) {
        Row r = selectedRow().orElse(null);
        if (r == null) {
            info("Command Details", "No command selected.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Stack: ").append(r.stack()).append("\n");
        sb.append("When: ").append(r.when()).append("\n");
        sb.append("Kind: ").append(r.kind()).append("\n");
        sb.append("Undoable: ").append(r.undoable() ? "Yes" : "No").append("\n");
        sb.append("Command ID: ").append(r.commandId()).append("\n");
        sb.append("\nLabel:\n").append(r.label());

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Command Details");
        a.setHeaderText("Command");
        a.setContentText(sb.toString());
        a.initOwner(ownerWindow());
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        com.fileexplorer.util.DialogTheme.apply(a, ownerWindow());
        a.showAndWait();

    }

    @FXML
/**
 * onShowInHistory.
 *
 * @param e TODO
 */
    private void onShowInHistory(ActionEvent e) {
        Row r = selectedRow().orElse(null);
        if (r == null || r.commandId() == null || r.commandId().isBlank()) {
            info("Show in Operation History", "No command selected.");
            return;
        }
        showInOperationHistory(r.commandId());
    }

    @FXML
/**
 * onExportCsv.
 *
 * @param e TODO
 */
    private void onExportCsv(ActionEvent e) {
        TableView<Row> t = activeTable();
        if (t == null) {
            info("Export CSV", "No table available.");
            return;
        }
        List<Row> rows = t.getItems();
        if (rows == null || rows.isEmpty()) {
            info("Export CSV", "No rows to export.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Command Log (CSV)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        fc.setInitialFileName("command-log-" + (t == undoTable ? "undo" : "redo") + ".csv");
        File out = fc.showSaveDialog(ownerWindow());
        if (out == null) return;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(out))) {
            bw.write("when,kind,label,undoable,commandId,stack");
            bw.newLine();
            for (Row r : rows) {
                bw.write(csv(r.when())); bw.write(",");
                bw.write(csv(r.kind())); bw.write(",");
                bw.write(csv(r.label())); bw.write(",");
                bw.write(r.undoable() ? "true" : "false"); bw.write(",");
                bw.write(csv(r.commandId())); bw.write(",");
                bw.write(csv(r.stack()));
                bw.newLine();
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "CSV export failed", ex);
            info("Export CSV", "Failed to export: " + ex.getMessage());
            return;
        }

        statusLabel.setText("Exported " + rows.size() + " row(s) to " + out.getName());
    }

    @FXML
/**
 * onCopyVisible.
 *
 * @param e TODO
 */
    private void onCopyVisible(ActionEvent e) {
        TableView<Row> t = activeTable();
        if (t == null) {
            info("Copy Visible", "No table available.");
            return;
        }
        List<Row> rows = t.getItems();
        if (rows == null || rows.isEmpty()) {
            info("Copy Visible", "No rows to copy.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("when\tkind\tlabel\tundoable\tcommandId\tstack\n");
        for (Row r : rows) {
            sb.append(safe(r.when())).append("\t")
              .append(safe(r.kind())).append("\t")
              .append(safe(r.label()).replace("\t", " ")).append("\t")
              .append(r.undoable() ? "Yes" : "No").append("\t")
              .append(safe(r.commandId())).append("\t")
              .append(safe(r.stack()))
              .append("\n");
        }

        ClipboardContent cc = new ClipboardContent();
        cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
        statusLabel.setText("Copied " + rows.size() + " row(s)");
    }

    @FXML
/**
 * onClearRedoStack.
 *
 * @param e TODO
 */
    private void onClearRedoStack(ActionEvent e) {
        if (context == null) return;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Clear Redo Stack");
        a.setHeaderText("Clear redo stack?");
        a.setContentText("This will clear the redo stack and persist the updated stacks.");
        a.initOwner(ownerWindow());
        Optional<ButtonType> res = a.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        try {
            context.commandManager().clearRedoStack();
            refresh();
            statusLabel.setText("Redo stack cleared");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to clear redo stack", ex);
            info("Clear Redo Stack", "Failed: " + ex.getMessage());
        }
    }


/**
 * showInOperationHistory.
 *
 * @param commandId TODO
 */
    private void showInOperationHistory(String commandId) {
        if (context == null) return;
        Platform.runLater(() -> {
            try {
                if (historyStage == null || historyController == null || !historyStage.isShowing()) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fileexplorer/ui/layout/OperationHistoryWindow.fxml"));
                    Parent root = loader.load();
                    OperationHistoryController c = loader.getController();
                    if (c != null) {
                        c.attach(context);
                    }
                    Stage s = new Stage();
                    s.setTitle("Operation History");
                    s.initModality(Modality.NONE);
                    Scene scene = new Scene(root, 1050, 600);
                    if (context != null && context.themeService() != null) {
                        context.themeService().apply(scene);
                    }
                    s.setScene(scene);
                    s.show();

                    historyStage = s;
                    historyController = c;
                } else {
                    historyStage.toFront();
                }

                if (historyController != null) {
                    historyController.selectByCommandId(commandId);
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Failed to show Operation History", ex);
                statusLabel.setText("Failed to open history");
            }
        });
    }

    @FXML
/**
 * onRepairStacks.
 *
 * @param e TODO
 */
    private void onRepairStacks(ActionEvent e) {
        if (context == null) return;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Repair Command Stacks");
        a.setHeaderText("Repair / reset command stacks?");
        a.setContentText("This will back up the current stack file and reset undo/redo stacks to empty.");
        a.initOwner(ownerWindow());
        Optional<ButtonType> res = a.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) {
            return;
        }
        try {
            context.commandManager().repairStacks();
            refresh();
            statusLabel.setText("Repaired stacks");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Repair stacks failed", ex);
            statusLabel.setText("Repair failed");
        }
    }


/**
 * refresh.
 *
 */
    private void refresh() {
        if (context == null) return;
        try {
            CommandManager cm = context.commandManager();
            storePathLabel.setText(cm.storeFile().toString());

            List<CommandManager.ExecutedCommand> undo = cm.undoStackSnapshot();
            List<CommandManager.ExecutedCommand> redo = cm.redoStackSnapshot();

            undoAll = toRows("UNDO", undo);
            redoAll = toRows("REDO", redo);
            applyFilters();

            statusLabel.setText(cm.lastLoadStatus() + " | Undo: " + undo.size() + "  Redo: " + redo.size() + (cm.lastLoadDropped() > 0 ? "  Dropped: " + cm.lastLoadDropped() : ""));
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Command log refresh failed", ex);
            statusLabel.setText("Failed to load");
        }
    }

    
/**
 * applyFilters.
 *
 */
    private void applyFilters() {
        if (context == null) return;

        String q = searchField == null ? "" : safe(searchField.getText()).toLowerCase().trim();
        boolean undoableOnly = undoableOnlyCheck != null && undoableOnlyCheck.isSelected();
        boolean batchesOnly = batchesOnlyCheck != null && batchesOnlyCheck.isSelected();

        List<Row> u = undoAll;
        List<Row> r = redoAll;

        if (!q.isEmpty() || undoableOnly || batchesOnly) {
            u = u.stream().filter(row -> matches(row, q, undoableOnly, batchesOnly)).toList();
            r = r.stream().filter(row -> matches(row, q, undoableOnly, batchesOnly)).toList();
        }

        if (undoTable != null) undoTable.setItems(FXCollections.observableArrayList(u));
        if (redoTable != null) redoTable.setItems(FXCollections.observableArrayList(r));

        if (statusLabel != null) {
            String extra = (q.isEmpty() && !undoableOnly && !batchesOnly) ? "" :
                    "  (filtered: " + u.size() + "/" + undoAll.size() + ", " + r.size() + "/" + redoAll.size() + ")";
            statusLabel.setText("Undo: " + undoAll.size() + "  Redo: " + redoAll.size() + extra);
        }
    }

/**
 * matches.
 *
 * @param row TODO
 * @param qLower TODO
 * @param undoableOnly TODO
 * @param batchesOnly TODO
 * @return TODO
 */
    private boolean matches(Row row, String qLower, boolean undoableOnly, boolean batchesOnly) {
        if (undoableOnly && !row.undoable()) return false;
        if (batchesOnly && !"BATCH".equalsIgnoreCase(row.kind())) return false;

        if (qLower == null || qLower.isEmpty()) return true;

        return safe(row.label()).toLowerCase().contains(qLower)
                || safe(row.commandId()).toLowerCase().contains(qLower)
                || safe(row.kind()).toLowerCase().contains(qLower);
    }

/**
 * toRows.
 *
 * @param stack TODO
 * @param list TODO
 * @return TODO
 */
private List<Row> toRows(String stack, List<CommandManager.ExecutedCommand> list) {
        return list.stream().map(ec -> {
            Command c = ec.command();
            String kind = kindOf(c);
            String when = TS_FMT.format(ec.executedAt());
            return new Row(stack, when, kind, safe(c.label()), c.isUndoable(), safe(c.id()));
        }).toList();
    }

/**
 * kindOf.
 *
 * @param c TODO
 * @return TODO
 */
    private static String kindOf(Command c) {
        if (c instanceof BatchCommand) return "BATCH";
        if (c instanceof CopyCommand) return "COPY";
        if (c instanceof MoveCommand) return "MOVE";
        if (c instanceof DeleteCommand) return "DELETE";
        return c.getClass().getSimpleName();
    }

/**
 * selectedRow.
 *
 * @return TODO
 */
    private Optional<Row> selectedRow() {
        // Prefer active tab selection
        Row r1 = undoTable == null ? null : undoTable.getSelectionModel().getSelectedItem();
        Row r2 = redoTable == null ? null : redoTable.getSelectionModel().getSelectedItem();
        if (r1 != null) return Optional.of(r1);
        if (r2 != null) return Optional.of(r2);
        return Optional.empty();
    }


/**
 * activeTable.
 *
 * @return TODO
 */
    private TableView<Row> activeTable() {
        if (undoTable == null && redoTable == null) return null;
        // If TabPane exists, use selection; otherwise default to undo.
        if (tabs != null && tabs.getSelectionModel() != null) {
            int i = tabs.getSelectionModel().getSelectedIndex();
            return (i == 1) ? redoTable : undoTable;
        }
        return undoTable != null ? undoTable : redoTable;
    }

/**
 * csv.
 *
 * @param s TODO
 * @return TODO
 */
    private static String csv(String s) {
        String v = safe(s);
        boolean need = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (!need) return v;
        v = v.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

/**
 * updateAnalytics.
 *
 * @param uVisible TODO
 * @param rVisible TODO
 */
    private void updateAnalytics(List<Row> uVisible, List<Row> rVisible) {
        if (analyticsLabel == null) return;

        String u = formatAnalytics("Undo", uVisible);
        String r = formatAnalytics("Redo", rVisible);
        analyticsLabel.setText(u + "    |    " + r);
    }

/**
 * formatAnalytics.
 *
 * @param name TODO
 * @param rows TODO
 * @return TODO
 */
    private String formatAnalytics(String name, List<Row> rows) {
        int total = rows == null ? 0 : rows.size();
        int undoable = 0;
        Map<String, Integer> byKind = new LinkedHashMap<>();
        if (rows != null) {
            for (Row row : rows) {
                if (row.undoable()) undoable++;
                String k = safe(row.kind()).toUpperCase();
                byKind.put(k, byKind.getOrDefault(k, 0) + 1);
            }
        }
        int nonUndoable = total - undoable;
        int cCopy = byKind.getOrDefault("COPY", 0);
        int cMove = byKind.getOrDefault("MOVE", 0);
        int cDel = byKind.getOrDefault("DELETE", 0);
        int cBatch = byKind.getOrDefault("BATCH", 0);

        return name + ": " + total + "  (Copy " + cCopy + ", Move " + cMove + ", Delete " + cDel + ", Batch " + cBatch + ")"
                + "  Undoable " + undoable + " / Non " + nonUndoable;
    }


/**
 * configureColumns.
 *
 */
    private void configureColumns() {
        if (undoWhenCol != null) undoWhenCol.setCellValueFactory(v -> v.getValue().whenProp());
        if (undoKindCol != null) undoKindCol.setCellValueFactory(v -> v.getValue().kindProp());
        if (undoLabelCol != null) undoLabelCol.setCellValueFactory(v -> v.getValue().labelProp());
        if (undoUndoableCol != null) undoUndoableCol.setCellValueFactory(v -> v.getValue().undoableProp());
        if (undoIdCol != null) undoIdCol.setCellValueFactory(v -> v.getValue().commandIdProp());

        if (redoWhenCol != null) redoWhenCol.setCellValueFactory(v -> v.getValue().whenProp());
        if (redoKindCol != null) redoKindCol.setCellValueFactory(v -> v.getValue().kindProp());
        if (redoLabelCol != null) redoLabelCol.setCellValueFactory(v -> v.getValue().labelProp());
        if (redoUndoableCol != null) redoUndoableCol.setCellValueFactory(v -> v.getValue().undoableProp());
        if (redoIdCol != null) redoIdCol.setCellValueFactory(v -> v.getValue().commandIdProp());
    }

/**
 * info.
 *
 * @param title TODO
 * @param msg TODO
 */
    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.initOwner(ownerWindow());
        com.fileexplorer.util.DialogTheme.apply(a, ownerWindow());
        a.showAndWait();
    }

/**
 * ownerWindow.
 *
 * @return TODO
 */
    private Window ownerWindow() {
        if (undoTable != null && undoTable.getScene() != null) return undoTable.getScene().getWindow();
        if (redoTable != null && redoTable.getScene() != null) return redoTable.getScene().getWindow();
        return null;
    }

/**
 * safe.
 *
 * @param s TODO
 * @return TODO
 */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static final class Row {
        private final SimpleStringProperty stack = new SimpleStringProperty();
        private final SimpleStringProperty when = new SimpleStringProperty();
        private final SimpleStringProperty kind = new SimpleStringProperty();
        private final SimpleStringProperty label = new SimpleStringProperty();
        private final SimpleBooleanProperty undoable = new SimpleBooleanProperty();
        private final SimpleStringProperty commandId = new SimpleStringProperty();

        Row(String stack, String when, String kind, String label, boolean undoable, String commandId) {
            this.stack.set(stack);
            this.when.set(when);
            this.kind.set(kind);
            this.label.set(label);
            this.undoable.set(undoable);
            this.commandId.set(commandId);
        }

        public String stack() { return stack.get(); }
        public String when() { return when.get(); }
        public String kind() { return kind.get(); }
        public String label() { return label.get(); }
        public boolean undoable() { return undoable.get(); }
        public String commandId() { return commandId.get(); }

        SimpleStringProperty whenProp() { return when; }
        SimpleStringProperty kindProp() { return kind; }
        SimpleStringProperty labelProp() { return label; }
        SimpleBooleanProperty undoableProp() { return undoable; }
        SimpleStringProperty commandIdProp() { return commandId; }
    }
}
