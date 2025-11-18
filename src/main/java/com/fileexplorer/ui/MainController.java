package com.fileexplorer.ui;

import com.fileexplorer.thumb.FileOperationManager;
import com.fileexplorer.thumb.FileOperationManager.CancellableOperation;
import com.fileexplorer.thumb.FileOperationManager.ProgressListener;
import com.fileexplorer.thumb.HistoryManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class MainController {

    @FXML private BorderPane historyPanel;
    @FXML private ToolBar ribbonBar;
    @FXML private HBox statusBar;
    @FXML private Label statusLabel;
    @FXML private ChoiceBox<String> themeSelector;

    @FXML private FlowPane contentGrid;
    @FXML private ProgressBar progressBar;
    @FXML private Button cancelOpButton;

    private HistoryPanelController historyPanelController;
    private final HistoryManager historyManager = new HistoryManager();

    private Path currentFolder;
    private final List<FlowTileCell> selectedCells = new ArrayList<>();

    private final FileOperationManager fileOpManager = new FileOperationManager();
    // hold current operation to allow cancel
    private final AtomicReference<CancellableOperation> currentOperation = new AtomicReference<>(null);

    @FXML
    public void initialize() {
        Object controller = historyPanel.getProperties().get("fx:controller");
        if (controller instanceof HistoryPanelController hp) {
            historyPanelController = hp;
            historyPanelController.setHistoryManager(historyManager);
            historyPanelController.refresh();
        }

        themeSelector.getSelectionModel().select("Light");
        cancelOpButton.setDisable(true);
        progressBar.setProgress(0);

        currentFolder = Path.of(System.getProperty("user.home"));
        loadFolder(currentFolder);
    }

    // File operations (copy/move/delete) that show progress and are cancellable

    /**
     * Copy selected files to target folder (targetFolder must be a directory).
     */
    public void copySelectedTo(Path targetFolder) {
        if (selectedCells.isEmpty()) {
            updateStatus("No files selected to copy");
            return;
        }
        for (FlowTileCell cell : selectedCells) {
            Path src = cell.path;
            Path dest = targetFolder.resolve(src.getFileName());
            startCopyOperation(src, dest);
        }
    }

    private void startCopyOperation(Path src, Path dest) {
        updateStatus("Starting copy: " + src.getFileName());
        progressBar.setProgress(0);
        cancelOpButton.setDisable(false);

        CancellableOperation op = fileOpManager.copy(src, dest, new ProgressListener() {
            @Override
            public void onProgress(long bytesTransferred, long totalBytes) {
                double p = (totalBytes > 0) ? (double) bytesTransferred / totalBytes : -1.0;
                Platform.runLater(() -> {
                    if (p >= 0.0) progressBar.setProgress(p);
                    else progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                });
            }

            @Override
            public void onComplete() {
                Platform.runLater(() -> {
                    updateStatus("Copy complete: " + src.getFileName());
                    progressBar.setProgress(1.0);
                    cancelOpButton.setDisable(true);
                    // record move? use historyManager.recordRename or recordMove if exists
                    historyManager.recordRename(src, dest);
                    if (historyPanelController != null) historyPanelController.refresh();
                    loadFolder(currentFolder);
                });
                currentOperation.set(null);
            }

            @Override
            public void onError(Throwable t) {
                Platform.runLater(() -> {
                    updateStatus("Copy failed: " + t.getMessage());
                    cancelOpButton.setDisable(true);
                    progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }

            @Override
            public void onCancelled() {
                Platform.runLater(() -> {
                    updateStatus("Copy cancelled");
                    cancelOpButton.setDisable(true);
                    progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }
        });

        currentOperation.set(op);
    }

    /**
     * Move selected files to target folder.
     */
    public void moveSelectedTo(Path targetFolder) {
        if (selectedCells.isEmpty()) {
            updateStatus("No files selected to move");
            return;
        }
        for (FlowTileCell cell : selectedCells) {
            Path src = cell.path;
            Path dest = targetFolder.resolve(src.getFileName());
            startMoveOperation(src, dest);
        }
    }

    private void startMoveOperation(Path src, Path dest) {
        updateStatus("Starting move: " + src.getFileName());
        progressBar.setProgress(0);
        cancelOpButton.setDisable(false);

        CancellableOperation op = fileOpManager.move(src, dest, new ProgressListener() {
            @Override
            public void onProgress(long bytesTransferred, long totalBytes) {
                double p = (totalBytes > 0) ? (double) bytesTransferred / totalBytes : -1.0;
                Platform.runLater(() -> {
                    if (p >= 0.0) progressBar.setProgress(p);
                    else progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                });
            }

            @Override
            public void onComplete() {
                Platform.runLater(() -> {
                    updateStatus("Move complete: " + src.getFileName());
                    progressBar.setProgress(1.0);
                    cancelOpButton.setDisable(true);
                    historyManager.recordMove(src, dest);
                    if (historyPanelController != null) historyPanelController.refresh();
                    loadFolder(currentFolder);
                });
                currentOperation.set(null);
            }

            @Override
            public void onError(Throwable t) {
                Platform.runLater(() -> {
                    updateStatus("Move failed: " + t.getMessage());
                    cancelOpButton.setDisable(true);
                    progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }

            @Override
            public void onCancelled() {
                Platform.runLater(() -> {
                    updateStatus("Move cancelled");
                    cancelOpButton.setDisable(true);
                    progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }
        });

        currentOperation.set(op);
    }

    /**
     * Delete selected files with progress.
     */
    public void deleteSelectedWithProgress() {
        if (selectedCells.isEmpty()) {
            updateStatus("No files selected to delete");
            return;
        }
        // For simplicity, delete items one by one
        Iterator<FlowTileCell> it = selectedCells.iterator();
        if (!it.hasNext()) return;
        FlowTileCell first = it.next();
        startDeleteOperation(first.path);
        // remaining items will be deleted after completion in onComplete handler or user can delete again
    }

    private void startDeleteOperation(Path path) {
        updateStatus("Deleting: " + path.getFileName());
        progressBar.setProgress(0);
        cancelOpButton.setDisable(false);

        CancellableOperation op = fileOpManager.delete(path, new ProgressListener() {
            @Override
            public void onProgress(long bytesTransferred, long totalBytes) {
                double p = (totalBytes > 0) ? (double) bytesTransferred / totalBytes : -1.0;
                Platform.runLater(() -> {
                    if (p >= 0.0) progressBar.setProgress(p);
                    else progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                });
            }

            @Override
            public void onComplete() {
                Platform.runLater(() -> {
                    updateStatus("Delete complete: " + path.getFileName());
                    progressBar.setProgress(1.0);
                    cancelOpButton.setDisable(true);
                    historyManager.recordDelete(path);
                    if (historyPanelController != null) historyPanelController.refresh();
                    loadFolder(currentFolder);
                });
                currentOperation.set(null);
            }

            @Override
            public void onError(Throwable t) {
                Platform.runLater(() -> {
                    updateStatus("Delete failed: " + t.getMessage());
                    cancelOpButton.setDisable(true);
                    progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }

            @Override
            public void onCancelled() {
                Platform.runLater(() -> {
                    updateStatus("Delete cancelled");
                    cancelOpButton.setDisable(true);
                    progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }
        });

        currentOperation.set(op);
    }

    /**
     * Cancel currently running operation if any.
     */
    @FXML
    private void onCancelOperation() {
        CancellableOperation op = currentOperation.getAndSet(null);
        if (op != null) {
            boolean cancelled = op.cancel();
            updateStatus(cancelled ? "Cancellation requested" : "Cancellation failed");
            cancelOpButton.setDisable(true);
        } else {
            updateStatus("No operation to cancel");
        }
    }

    // --- existing methods (loadFolder, updateStatus, etc.) should exist below ---
    // For brevity, we re-add the previous loadFolder and updateStatus implementations
    private void loadFolder(Path folder) {
        // minimal defensive implementation — should match your prior loadFolder logic
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

    public void updateStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    // cleanup on application exit
    public void shutdown() {
        fileOpManager.shutdown();
    }

    public HistoryManager getHistoryManager() {
    // helper to retrieve active tab controller (TabContentController)
    private TabContentController getActiveTabController() {
        javafx.scene.control.Tab tab = null;
        try { tab = tabPane.getSelectionModel().getSelectedItem(); } catch(Exception e) {}
        if (tab == null) return null;
        Object loaderObj = tab.getProperties().get("loader");
        if (loaderObj instanceof javafx.fxml.FXMLLoader loader) {
            Object ctrl = loader.getController();
            if (ctrl instanceof TabContentController tcc) return tcc;
        }
        // fallback: try controller from content properties
        Object contentCtrl = tab.getContent().getProperties().get("controller");
        if (contentCtrl instanceof TabContentController tcc2) return tcc2;
        return null;
    }
    // helper to retrieve active tab controller (TabContentController)
    private TabContentController getActiveTabController() {
        javafx.scene.control.Tab tab = null;
        try { tab = tabPane.getSelectionModel().getSelectedItem(); } catch(Exception e) {}
        if (tab == null) return null;
        Object loaderObj = tab.getProperties().get("loader");
        if (loaderObj instanceof javafx.fxml.FXMLLoader loader) {
            Object ctrl = loader.getController();
            if (ctrl instanceof TabContentController tcc) return tcc;
        }
        // fallback: try controller from content properties
        Object contentCtrl = tab.getContent().getProperties().get("controller");
        if (contentCtrl instanceof TabContentController tcc2) return tcc2;
        return null;
    }
        return historyManager;
    }
}

// --- Ribbon bar wrappers to call per-tab operations ---
public void copySelectedToActiveTab(java.nio.file.Path targetFolder) {
    TabContentController tcc = getActiveTabController();
    if (tcc != null) tcc.copySelectedTo(targetFolder);
    else updateStatus("No active tab to copy from");
}

public void moveSelectedToActiveTab(java.nio.file.Path targetFolder) {
    TabContentController tcc = getActiveTabController();
    if (tcc != null) tcc.moveSelectedTo(targetFolder);
    else updateStatus("No active tab to move from");
}

public void deleteSelectedWithProgressActiveTab() {
    TabContentController tcc = getActiveTabController();
    if (tcc != null) tcc.deleteSelectedWithProgress();
    else updateStatus("No active tab to delete from");
}

public void cancelActiveTabOperation() {
    TabContentController tcc = getActiveTabController();
    if (tcc != null) tcc.cancelCurrentOperation();
    else updateStatus("No active tab operation to cancel");
}

// --- Ribbon bar wrappers to call per-tab operations ---
public void copySelectedToActiveTab(java.nio.file.Path targetFolder) {
    TabContentController tcc = getActiveTabController();
    if (tcc != null) tcc.copySelectedTo(targetFolder);
    else updateStatus("No active tab to copy from");
}

public void moveSelectedToActiveTab(java.nio.file.Path targetFolder) {
    TabContentController tcc = getActiveTabController();
    if (tcc != null) tcc.moveSelectedTo(targetFolder);
    else updateStatus("No active tab to move from");
}

public void deleteSelectedWithProgressActiveTab() {
    TabContentController tcc = getActiveTabController();
    if (tcc != null) tcc.deleteSelectedWithProgress();
    else updateStatus("No active tab to delete from");
}

public void cancelActiveTabOperation() {
    TabContentController tcc = getActiveTabController();
    if (tcc != null) tcc.cancelCurrentOperation();
    else updateStatus("No active tab operation to cancel");
}
