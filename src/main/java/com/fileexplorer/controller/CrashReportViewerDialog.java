package com.fileexplorer.controller;

import com.fileexplorer.service.diag.CrashReportService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Window;

/**
 * Simple viewer for the last crash snapshot (if present).
 *
 * <p>This is a lightweight utility dialog; it is safe to call even if no crash file exists.</p>
 */
public final class CrashReportViewerDialog {

    private CrashReportViewerDialog() {
    }

    /**
     * Shows a modal dialog with the contents of the last crash report.
     *
     * @param owner owner window
     */
    public static void show(Window owner) {
        Path p = CrashReportService.lastCrashFile();
        String content = "(No crash report found)";
        if (p != null && Files.exists(p)) {
            try {
                content = Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                content = "(Failed to read crash report: " + ex.getMessage() + ")";
            }
        }

        TextArea area = new TextArea(content);
        area.setEditable(false);
        area.setWrapText(false);

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Last Crash Report");
        a.setHeaderText("Crash snapshot");
        a.getDialogPane().setContent(area);
        a.getButtonTypes().setAll(ButtonType.CLOSE, new ButtonType("Copy"));

        if (owner != null) a.initOwner(owner);

        a.showAndWait().ifPresent(bt -> {
            if ("Copy".equals(bt.getText())) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(area.getText());
                Clipboard.getSystemClipboard().setContent(cc);
            }
        });
    }
}
