package com.fileexplorer.ui;

import com.fileexplorer.thumb.HistoryManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.awt.Desktop;
import java.io.File;
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
        Object controller = historyPanel.getProperties().get("fx:controller");
        if (controller instanceof HistoryPanelController hp) {
            historyPanelController = hp;
            historyPanelController.setHistoryManager(historyManager);
            historyPanelController.refresh();
        }

        themeSelector.getSelectionModel().select("Light");

        currentFolder = Path.of(System.getProperty("user.home"));
        loadFolder(currentFolder);

        setupDragAndDrop();
    }

    // --- Folder / File Actions ---

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
            File file = cell.path.toFile();
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                    Desktop.getDesktop().moveToTrash(file);
                } else {
                    if (Files.isDirectory(file.toPath())) Files.delete(file.toPath());
                    else Files.delete(file.toPath());
                }
                historyManager.recordDelete(file.toPath());
            } catch (IOException e) {
                updateStatus("Failed to delete: " + file.getName());
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

    // --- Folder Grid Loading ---

    private void loadFolder(Path folder) {
        contentGrid.getChildren().forEach(node -> {
            if (node instanceof FlowTileCell ftc) ftc.dispose();
        });
        contentGrid.getChildren().clear();
        selectedCells.clear();
        if (!Files.exists(folder) || !Files.isDirectory(folder)) return;

        currentFolder = folder;

        try {
            Files.list(folder).forEach(path -> {
                FlowTileCell cell = new FlowTileCell(path, (p, cb) -> {
                    try {
                        cb.accept(IconLoader.loadIcon(p.toFile()));
                    } catch (Exception ex) {
                        cb.accept(IconLoader.getPlaceholder());
                    }
                    return null;
                });

                cell.setOnMouseClicked(e -> {
                    if (!selectedCells.contains(cell)) selectedCells.add(cell);
                    else selectedCells.remove(cell);
                    updateStatus(selectedCells.size() + " items selected");
                });

                ContextMenuHandler.attach(cell, path.toFile());

                contentGrid.getChildren().add(cell);
            });
            updateStatus(Files.list(folder).count() + " items loaded");
        } catch (IOException e) {
            updateStatus("Failed to load folder: " + e.getMessage());
        }
    }

    // --- Advanced Drag-and-Drop ---

    private void setupDragAndDrop() {
        contentGrid.setOnDragOver(event -> {
            if (event.getGestureSource() != contentGrid && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        contentGrid.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    Path target = currentFolder.resolve(file.getName());
                    try {
                        if (file.isDirectory()) {
                            Files.move(file.toPath(), target);
                        } else {
                            Files.move(file.toPath(), target);
                        }
                        historyManager.recordMove(file.toPath(), target);
                    } catch (IOException ex) {
                        updateStatus("Failed to move: " + file.getName());
                    }
                }
                loadFolder(currentFolder);
                updateStatus(db.getFiles().size() + " items moved via drag-and-drop");
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    // --- StatusBar Update ---

    public void updateStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }
}
