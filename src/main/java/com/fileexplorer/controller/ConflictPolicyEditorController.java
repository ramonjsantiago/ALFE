package com.fileexplorer.controller;

import com.fileexplorer.service.ops.OperationQueueService;
import com.fileexplorer.service.ops.conflict.ConflictPolicyAction;
import com.fileexplorer.service.ops.conflict.ConflictPolicyConfig;
import com.fileexplorer.service.ops.conflict.ConflictPolicyJsonService;
import com.fileexplorer.service.ops.conflict.ConflictPolicyProfile;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Phase 4.2.1: UI editor for conflict policy profiles.
 *
 * <p>This is intentionally minimal and dependency-free. It edits the global policy stored by
 * {@link com.fileexplorer.service.ops.conflict.ConflictPolicyStore} via {@link OperationQueueService}.</p>
 */
public final class ConflictPolicyEditorController {

    @FXML private ComboBox<ConflictPolicyProfile> profileCombo;
    @FXML private ComboBox<ConflictPolicyAction> customActionCombo;
    @FXML private Label notesLabel;

    @FXML private TextArea rulesArea;

    @FXML private Button importButton;
    @FXML private Button exportButton;
    @FXML private Button resetButton;
    @FXML private Button closeButton;

    private OperationQueueService operationQueueService;
    private Stage stage;

/**
 * setOperationQueueService.
 *
 * @param operationQueueService TODO
 */
    public void setOperationQueueService(OperationQueueService operationQueueService) {
        this.operationQueueService = operationQueueService;
        refreshFromService();
    }

/**
 * setStage.
 *
 * @param stage TODO
 */
    void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
/**
 * initialize.
 *
 */
    private void initialize() {
        if (profileCombo != null) {
            profileCombo.getItems().setAll(ConflictPolicyProfile.values());
            profileCombo.valueProperty().addListener((obs, oldV, newV) -> {
                updateCustomEnabled();
                updateNotes();
                if (operationQueueService != null && newV != null) {
                    persistRulesFromUi();
                    operationQueueService.setConflictPolicyProfile(newV);
                }
            });
        }

        if (customActionCombo != null) {
            customActionCombo.getItems().setAll(
                    ConflictPolicyAction.PROMPT,
                    ConflictPolicyAction.SKIP,
                    ConflictPolicyAction.OVERWRITE,
                    ConflictPolicyAction.RENAME
            );
            customActionCombo.valueProperty().addListener((obs, oldV, newV) -> {
                if (operationQueueService != null && newV != null) {
                    persistRulesFromUi();
                    operationQueueService.setCustomConflictDefaultAction(newV);
                }
            });
        }

        if (importButton != null) importButton.setOnAction(e -> onImport());
        if (exportButton != null) exportButton.setOnAction(e -> onExport());
        if (resetButton != null) resetButton.setOnAction(e -> onReset());
        if (closeButton != null) closeButton.setOnAction(e -> onClose());

        if (rulesArea != null) {
            rulesArea.focusedProperty().addListener((obs, oldV, newV) -> {
                if (Boolean.FALSE.equals(newV)) {
                    persistRulesFromUi();
                }
            });
        }

        updateCustomEnabled();
        updateNotes();
    }

/**
 * refreshFromService.
 *
 */
    private void refreshFromService() {
        if (operationQueueService == null) return;
        ConflictPolicyProfile p = operationQueueService.getConflictPolicyProfile();
        ConflictPolicyAction a = operationQueueService.getCustomConflictDefaultAction();

        if (profileCombo != null) profileCombo.getSelectionModel().select(p);
        if (customActionCombo != null) customActionCombo.getSelectionModel().select(a);

        updateCustomEnabled();
        updateNotes();
    }

/**
 * updateCustomEnabled.
 *
 */
    private void updateCustomEnabled() {
        if (customActionCombo == null || profileCombo == null) return;
        boolean custom = profileCombo.getValue() == ConflictPolicyProfile.CUSTOM;
        customActionCombo.setDisable(!custom);
        customActionCombo.setOpacity(custom ? 1.0 : 0.75);
        if (rulesArea != null) {
            rulesArea.setDisable(!custom);
            rulesArea.setOpacity(custom ? 1.0 : 0.75);
        }
    }

/**
 * updateNotes.
 *
 */
    private void updateNotes() {
        if (notesLabel == null || profileCombo == null) return;
        ConflictPolicyProfile p = profileCombo.getValue();
        if (p == null) p = ConflictPolicyProfile.DEFAULT;
        String txt = switch (p) {
            case DEFAULT -> "Prompt on conflicts (use the Conflict Queue).";
            case CONSERVATIVE -> "Auto-skip when a destination exists.";
            case AGGRESSIVE -> "Auto-overwrite when a destination exists.";
            case MIRROR -> "For COPY/MOVE, overwrite to mirror the destination; otherwise prompt.";
            case CUSTOM -> "Use the CUSTOM default action for conflicts.";
        };
        notesLabel.setText(txt);
    }

/**
 * onImport.
 *
 */
    private void onImport() {
        if (operationQueueService == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Import Conflict Policy");
        fc.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON", "*.json"));
        Window owner = (stage != null) ? stage.getOwner() : null;
        var file = fc.showOpenDialog(owner);
        if (file == null) return;

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            ConflictPolicyConfig cfg = ConflictPolicyJsonService.importJson(json);
            if (cfg != null) {
                operationQueueService.setConflictPolicyProfile(cfg.profile());
                operationQueueService.setCustomConflictDefaultAction(cfg.customDefaultAction());
                refreshFromService();
            }
        } catch (Exception ex) {
            showError("Import failed", ex);
        }
    }

/**
 * onExport.
 *
 */
    private void onExport() {
        if (operationQueueService == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Conflict Policy");
        fc.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON", "*.json"));
        fc.setInitialFileName("conflict-policy.json");
        Window owner = (stage != null) ? stage.getOwner() : null;
        var file = fc.showSaveDialog(owner);
        if (file == null) return;

        try {
            ConflictPolicyConfig cfg = new ConflictPolicyConfig(
                    operationQueueService.getConflictPolicyProfile(),
                    operationQueueService.getCustomConflictDefaultAction()
            );
            String json = ConflictPolicyJsonService.exportJson(cfg);
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            showError("Export failed", ex);
        }
    }

/**
 * onReset.
 *
 */
    private void onReset() {
        if (operationQueueService == null) return;
        operationQueueService.setConflictPolicyProfile(ConflictPolicyProfile.DEFAULT);
        operationQueueService.setCustomConflictDefaultAction(ConflictPolicyAction.PROMPT);
        refreshFromService();
    }

/**
 * onClose.
 *
 */
    private void onClose() {
        persistRulesFromUi();
        if (stage != null) {
            stage.close();
        }
    }

/**
 * showError.
 *
 * @param title TODO
 * @param ex TODO
 */
    private void showError(String title, Exception ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(title);
        a.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
        com.fileexplorer.util.DialogTheme.apply(a, null);
        com.fileexplorer.util.DialogTheme.apply(a, null);
        a.showAndWait();
    }

    // ---------------------------------------------------------------------
    // Static helper to open the editor
    // ---------------------------------------------------------------------

/**
 * show.
 *
 * @param owner TODO
 * @param operationQueueService TODO
 */
    public static void show(Window owner, OperationQueueService operationQueueService) {
        try {
            FXMLLoader loader = new FXMLLoader(ConflictPolicyEditorController.class.getResource("/com/fileexplorer/ui/layout/ConflictPolicyEditor.fxml"));
            Parent root = loader.load();

            ConflictPolicyEditorController c = loader.getController();

            Stage s = new Stage();
            s.setTitle("Conflict Policy");
            s.initModality(Modality.WINDOW_MODAL);
            if (owner != null) s.initOwner(owner);
            s.setScene(new Scene(root));

            c.setStage(s);
            c.setOperationQueueService(operationQueueService);

            s.show();
        } catch (IOException ex) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Conflict Policy");
            a.setHeaderText("Failed to open Conflict Policy editor");
            a.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            com.fileexplorer.util.DialogTheme.apply(a, null);
            com.fileexplorer.util.DialogTheme.apply(a, null);
            a.showAndWait();
        }
    }


private void persistRulesFromUi() {
    if (operationQueueService == null) return;
    if (rulesArea == null) return;

    String raw = rulesArea.getText();
    java.util.List<com.fileexplorer.service.ops.conflict.ConflictRule> rules = new java.util.ArrayList<>();
    if (raw != null) {
        String[] lines = raw.split("\r?\n");
        int n = 0;
        for (String line : lines) {
            if (line == null) continue;
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("#")) continue;
            // split on literal pipe (|)
            String[] parts = t.split("\\|", -1);
            try {
                if (parts.length >= 3) {
                    String id = parts[0].trim();
                    String pattern = parts[1].trim();
                    String actionRaw = parts[2].trim();
                    if (id.isEmpty()) id = "R" + (++n);
                    com.fileexplorer.service.ops.conflict.ConflictPolicyAction a =
                            com.fileexplorer.service.ops.conflict.ConflictPolicyAction.valueOf(actionRaw.toUpperCase(java.util.Locale.ROOT));
                    rules.add(new com.fileexplorer.service.ops.conflict.ConflictRule(id, pattern, a));
                } else if (parts.length == 2) {
                    String id = "R" + (++n);
                    String pattern = parts[0].trim();
                    String actionRaw = parts[1].trim();
                    com.fileexplorer.service.ops.conflict.ConflictPolicyAction a =
                            com.fileexplorer.service.ops.conflict.ConflictPolicyAction.valueOf(actionRaw.toUpperCase(java.util.Locale.ROOT));
                    rules.add(new com.fileexplorer.service.ops.conflict.ConflictRule(id, pattern, a));
                }
            } catch (Exception ignored) {
                // ignore invalid
            }
        }
    }
    operationQueueService.setCustomConflictRules(java.util.List.copyOf(rules));
}
}
