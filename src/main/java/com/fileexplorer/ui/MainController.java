package com.fileexplorer.ui;

import com.fileexplorer.thumb.HistoryManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    @FXML private BorderPane historyPanel;
    @FXML private ToolBar ribbonBar;
    @FXML private HBox statusBar;
    @FXML private Label statusLabel;
    @FXML private ChoiceBox<String> themeSelector;

    @FXML private FlowPane contentGrid;

    private HistoryPanelController historyPanelController;
    private final HistoryManager historyManager = new HistoryManager();

    private Path currentFolder;
    private final List<FlowTileCell> selectedCells = new ArrayList<>();

    @FXML
    public void initialize() {
        // Retrieve included HistoryPanel controller
        Object controller = historyPanel.getProperties().get("fx:controller");
        if (controller instanceof HistoryPanelController hp) {
            historyPanelController = hp;
            historyPanelController.setHistoryManager(historyManager);
            historyPanelController.refresh();
        }

        themeSelector.getSelectionModel().select("Light");

        // Initialize folder
        currentFolder = Path.of(System.getProperty("user.home"));
        loadFolder(currentFolder);
    }

    // --- RibbonBar Actions ---

    @FXML
    private void onNewFolder() {
        try {
            String folderName = "New Folder";
            Path newFolder = currentFolder.resolve(folderName);
            int i = 1;
            while (Files.exists(newFolder)) {
                newFolder = currentFolder.resolve(folderName + " (" + i++ + ")");
            }
            Files.createDirectory(newFolder);
            updateStatus("Folder created: " + newFolder.getFileName());
            loadFolder(currentFolder);
        } catch (IOException e) {
            updateStatus("Failed to create folder: " + e.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        loadFolder(currentFolder);
        updateStatus("Folder refreshed");
    }

    @FXML
    private void onDelete() {
        if (selectedCells.isEmpty()) {
            updateStatus("No files selected to delete");
            return;
        }

        for (FlowTileCell cell : selectedCells) {
            Path p = cell.path;
            try {
                if (Files.isDirectory(p)) Files.delete(p);
                else Files.delete(p);
                historyManager.recordDelete(p);
            } catch (IOException e) {
                updateStatus("Failed to delete: " + p.getFileName());
            }
        }

        updateStatus(selectedCells.size() + " items deleted");
        selectedCells.clear();
        loadFolder(currentFolder);
        if (historyPanelController != null) historyPanelController.refresh();
    }

    @FXML
    private void onUndo() {
        try {
            if (historyManager.canUndo()) {
                historyManager.undo();
                updateStatus("Undo performed");
                loadFolder(currentFolder);
                if (historyPanelController != null) historyPanelController.refresh();
            } else {
                updateStatus("Nothing to undo");
            }
        } catch (Exception e) {
            updateStatus("Undo failed: " + e.getMessage());
        }
    }

    @FXML
    private void onThemeChange() {
        String theme = themeSelector.getValue();
        updateStatus("Theme switched to " + theme);
        MainApp app = (MainApp) statusBar.getScene().getWindow().getScene().getWindow().getUserData();
        if (app != null) app.setTheme(theme);
    }

    // --- Folder / FlowTileCell Handling ---

    private void loadFolder(Path folder) {
        contentGrid.getChildren().clear();
        selectedCells.clear();
        if (!Files.exists(folder) || !Files.isDirectory(folder)) return;

        currentFolder = folder;

        try {
            Files.list(folder).forEach(path -> {
                FlowTileCell cell = new FlowTileCell(path, (p, cb) -> {
                    cb.accept(IconLoader.loadIcon(p.toFile()));
                    return null;
                });

                // Selection hook
                cell.setOnMouseClicked(e -> {
                    if (!selectedCells.contains(cell)) selectedCells.add(cell);
                    else selectedCells.remove(cell);
                    updateStatus(selectedCells.size() + " items selected");
                });

                // Right-click context menu
                ContextMenuHandler.attach(cell, path.toFile());

                contentGrid.getChildren().add(cell);
            });
            updateStatus(Files.list(folder).count() + " items loaded");
        } catch (IOException e) {
            updateStatus("Failed to load folder: " + e.getMessage());
        }
    }

    // --- StatusBar Update ---

    public void updateStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }
}
