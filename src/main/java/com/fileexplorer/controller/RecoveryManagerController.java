package com.fileexplorer.controller;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.service.ops.OperationQueueService;
import com.fileexplorer.service.ops.journal.OperationJournalService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Phase 4.5.1: Recovery Manager UI.
 *
 * Shows incomplete transaction journals and lets the user resume, mark failed, or delete journals.
 */
public final class RecoveryManagerController {

    @FXML private TableView<RecoveryRow> table;
    @FXML private TableColumn<RecoveryRow, String> colOperationId;
    @FXML private TableColumn<RecoveryRow, String> colType;
    @FXML private TableColumn<RecoveryRow, String> colPreviewHash;
    @FXML private TableColumn<RecoveryRow, Number> colCompleted;
    @FXML private TableColumn<RecoveryRow, Number> colTotal;

    @FXML private Button resumeButton;
    @FXML private Button markFailedButton;
    @FXML private Button deleteJournalButton;
    @FXML private Button openFolderButton;
    @FXML private Button refreshButton;
    @FXML private Button closeButton;

    private ExplorerContext context;
    private final ObservableList<RecoveryRow> rows = FXCollections.observableArrayList();

/**
 * show.
 *
 * @param owner TODO
 * @param context TODO
 */
    public static void show(Window owner, ExplorerContext context) {
        Objects.requireNonNull(context, "context");
        try {
            FXMLLoader loader = new FXMLLoader(RecoveryManagerController.class.getResource("/com/fileexplorer/ui/layout/RecoveryManagerDialog.fxml"));
            Parent root = loader.load();
            RecoveryManagerController c = loader.getController();
            c.attach(context);

            Stage s = new Stage();
            s.initModality(Modality.WINDOW_MODAL);
            if (owner != null) s.initOwner(owner);
            s.setTitle("Recovery Manager");
            s.setScene(new Scene(root));
            s.setMinWidth(780);
            s.setMinHeight(420);
            s.show();
        } catch (IOException ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Recovery Manager");
            a.setHeaderText("Unable to open Recovery Manager");
            a.setContentText(String.valueOf(ex.getMessage()));
            com.fileexplorer.util.DialogTheme.apply(a, null);
            com.fileexplorer.util.DialogTheme.apply(a, null);
            a.showAndWait();
        }
    }

/**
 * attach.
 *
 * @param context TODO
 */
    private void attach(ExplorerContext context) {
        this.context = context;
        wire();
        refresh();
    }

/**
 * wire.
 *
 */
    private void wire() {
        if (colOperationId != null) colOperationId.setCellValueFactory(v -> v.getValue().operationId);
        if (colType != null) colType.setCellValueFactory(v -> v.getValue().type);
        if (colPreviewHash != null) colPreviewHash.setCellValueFactory(v -> v.getValue().previewHash);
        if (colCompleted != null) colCompleted.setCellValueFactory(v -> v.getValue().completed);
        if (colTotal != null) colTotal.setCellValueFactory(v -> v.getValue().total);

        if (table != null) {
            table.setItems(rows);
            table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        }

        if (resumeButton != null) resumeButton.setOnAction(e -> resumeSelected());
        if (markFailedButton != null) markFailedButton.setOnAction(e -> markFailedSelected());
        if (deleteJournalButton != null) deleteJournalButton.setOnAction(e -> deleteSelected());
        if (openFolderButton != null) openFolderButton.setOnAction(e -> openFolder());
        if (refreshButton != null) refreshButton.setOnAction(e -> refresh());
        if (closeButton != null) closeButton.setOnAction(e -> {
            Stage s = (Stage) closeButton.getScene().getWindow();
            s.close();
        });

        // Enable/disable buttons based on selection
        if (table != null) {
            table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
                boolean has = n != null;
                if (resumeButton != null) resumeButton.setDisable(!has);
                if (markFailedButton != null) markFailedButton.setDisable(!has);
                if (deleteJournalButton != null) deleteJournalButton.setDisable(!has);
            });
        }

        if (resumeButton != null) resumeButton.setDisable(true);
        if (markFailedButton != null) markFailedButton.setDisable(true);
        if (deleteJournalButton != null) deleteJournalButton.setDisable(true);
    }

/**
 * refresh.
 *
 */
    private void refresh() {
        if (context == null) return;
        OperationQueueService q = context.operationQueueService();
        List<OperationJournalService.RecoveryCandidate> cands = q.findRecoveryCandidates();
        rows.clear();
        if (cands != null) {
            for (var c : cands) {
                rows.add(new RecoveryRow(c));
            }
        }
    }

/**
 * selected.
 *
 * @return TODO
 */
    private RecoveryRow selected() {
        return table == null ? null : table.getSelectionModel().getSelectedItem();
    }

/**
 * resumeSelected.
 *
 */
    private void resumeSelected() {
        RecoveryRow r = selected();
        if (r == null || context == null) return;
        int enq = context.operationQueueService().resumeFromJournal(r.operationId.get(), r.driftPolicy);
        if (enq <= 0) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Resume");
            a.setHeaderText("Nothing to resume");
            a.setContentText("The selected journal may already be complete or contains no remaining items.");
            com.fileexplorer.util.DialogTheme.apply(a, null);
            com.fileexplorer.util.DialogTheme.apply(a, null);
            a.showAndWait();
        }
        refresh();
    }

/**
 * markFailedSelected.
 *
 */
    private void markFailedSelected() {
        RecoveryRow r = selected();
        if (r == null || context == null) return;
        context.operationQueueService().markRecoveryFailed(r.operationId.get());
        refresh();
    }

/**
 * deleteSelected.
 *
 */
    private void deleteSelected() {
        RecoveryRow r = selected();
        if (r == null || context == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete journal");
        confirm.setHeaderText("Delete journal " + r.operationId.get() + "?");
        confirm.setContentText("This removes the crash recovery record for the operation.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        context.operationQueueService().deleteJournal(r.operationId.get());
        refresh();
    }

/**
 * openFolder.
 *
 */
    private void openFolder() {
        if (context == null) return;
        try {
            Path dir = context.operationQueueService().journalDirectory();
            if (dir == null) return;
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception ignored) {
        }
    }

    public static final class RecoveryRow {
        final SimpleStringProperty operationId;
        final SimpleStringProperty type;
        final SimpleStringProperty previewHash;
        final SimpleIntegerProperty completed;
        final SimpleIntegerProperty total;
        final com.fileexplorer.service.ops.ExecutionDriftPolicy driftPolicy;

        RecoveryRow(OperationJournalService.RecoveryCandidate c) {
            this.operationId = new SimpleStringProperty(c.operationId());
            this.type = new SimpleStringProperty(c.type() == null ? "" : c.type());
            this.previewHash = new SimpleStringProperty(c.previewHash() == null ? "" : c.previewHash());
            this.completed = new SimpleIntegerProperty(c.completedItems());
            this.total = new SimpleIntegerProperty(c.planItems());
            this.driftPolicy = c.driftPolicy();
        }
    }
}
