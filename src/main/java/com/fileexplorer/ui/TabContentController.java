package com.fileexplorer.ui;

import com.fileexplorer.thumb.FileOperationManager;
import com.fileexplorer.thumb.FileOperationManager.CancellableOperation;
import com.fileexplorer.thumb.FileOperationManager.ProgressListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controller for per-tab content. Each tab contains its own FileOperationManager,
 * progress bar, cancel button, FlowPane thumbnails, details table, and preview.
 */
public class TabContentController {

    @FXML private FlowPane rightFlowPane;
    @FXML private ImageView previewImage;
    @FXML private ProgressBar progressBar;
    @FXML private Button cancelOpButton;
    @FXML private TableView<?> detailsTable; // keep generic; cast in usages if needed
    @FXML private StackPane previewContainer;
    @FXML private TextField tabSearchField;

    private Path currentFolder;
    private final List<FlowTileCell> selectedCells = new ArrayList<>();

    // Per-tab file operation manager & current operation
    private final FileOperationManager fileOpManager = new FileOperationManager();
    private final AtomicReference<CancellableOperation> currentOperation = new AtomicReference<>(null);

    public void initializeFolder(Path folder) {
        this.currentFolder = folder;
        // populate UI: thumbnails, details, preview hooks...
        loadFolder(folder);
        setupCancelButton();
        if (tabSearchField != null) {
            tabSearchField.textProperty().addListener((obs, oldV, newV) -> {
                // ideally filter the details table — left as a simple placeholder
            });
        }
    }

    public Path getCurrentFolder() {
        return currentFolder;
    }

    private void setupCancelButton() {
        if (cancelOpButton != null) {
            cancelOpButton.setOnAction(e -> cancelCurrentOperation());
            cancelOpButton.setDisable(true);
        }
        if (progressBar != null) progressBar.setProgress(0);
    }

    private void loadFolder(Path folder) {
        // Load thumbnails into rightFlowPane (similar to MainController loadFolder)
        rightFlowPane.getChildren().clear();
        selectedCells.clear();
        if (folder == null) return;
        try {
            Files.list(folder).forEach(p -> {
                FlowTileCell cell = new FlowTileCell(p, (path, cb) -> {
                    try {
                        cb.accept(IconLoader.loadIcon(path.toFile()));
                    } catch (Exception ex) {
                        cb.accept(IconLoader.getPlaceholder());
                    }
                    return null;
                });

                cell.setOnMouseClicked(e -> {
                    if (!selectedCells.contains(cell)) selectedCells.add(cell);
                    else selectedCells.remove(cell);
                    // update per-tab UI if needed
                    updateStatus(selectedCells.size() + " items selected");
                });

                ContextMenuHandler.attach(cell, p.toFile());
                rightFlowPane.getChildren().add(cell);
            });
            updateStatus(Files.list(folder).count() + " items loaded");
        } catch (IOException ex) {
            updateStatus("Failed to load folder: " + ex.getMessage());
        }
    }

    // ----------------------------
    // Public per-tab file operations
    // ----------------------------

    /**
     * Copy selected items to target folder using per-tab FileOperationManager.
     */
    public void copySelectedTo(Path targetFolder) {
        if (selectedCells.isEmpty()) {
            updateStatus("No files selected to copy");
            return;
        }
        // For simplicity start one operation per selected item (could be batched)
        for (FlowTileCell c : new ArrayList<>(selectedCells)) {
            Path src = c.path;
            Path dest = targetFolder.resolve(src.getFileName());
            startCopyOperation(src, dest);
        }
    }

    /**
     * Move selected items to target folder using per-tab FileOperationManager.
     */
    public void moveSelectedTo(Path targetFolder) {
        if (selectedCells.isEmpty()) {
            updateStatus("No files selected to move");
            return;
        }
        for (FlowTileCell c : new ArrayList<>(selectedCells)) {
            Path src = c.path;
            Path dest = targetFolder.resolve(src.getFileName());
            startMoveOperation(src, dest);
        }
    }

    /**
     * Delete selected items with per-tab progress.
     */
    public void deleteSelectedWithProgress() {
        if (selectedCells.isEmpty()) {
            updateStatus("No files selected to delete");
            return;
        }
        // Delete first selected item (others can be queued or repeated)
        Iterator<FlowTileCell> it = selectedCells.iterator();
        if (it.hasNext()) {
            startDeleteOperation(it.next().path);
        }
    }

    public void cancelCurrentOperation() {
        CancellableOperation op = currentOperation.getAndSet(null);
        if (op != null) {
            op.cancel();
            Platform.runLater(() -> {
                if (cancelOpButton != null) cancelOpButton.setDisable(true);
                if (progressBar != null) progressBar.setProgress(0);
            });
            updateStatus("Operation cancellation requested");
        } else {
            updateStatus("No running operation to cancel");
        }
    }

    // ----------------------------
    // Internal operation helpers
    // ----------------------------

    private void startCopyOperation(Path src, Path dest) {
        updateStatus("Starting copy: " + src.getFileName());
        if (progressBar != null) progressBar.setProgress(0);
        if (cancelOpButton != null) cancelOpButton.setDisable(false);

        CancellableOperation op = fileOpManager.copy(src, dest, new ProgressListener() {
            @Override
            public void onProgress(long bytesTransferred, long totalBytes) {
                Platform.runLater(() -> {
                    if (progressBar != null) {
                        if (totalBytes > 0) progressBar.setProgress((double) bytesTransferred / totalBytes);
                        else progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                    }
                });
            }

            @Override
            public void onComplete() {
                Platform.runLater(() -> {
                    updateStatus("Copy complete: " + src.getFileName());
                    if (progressBar != null) progressBar.setProgress(1.0);
                    if (cancelOpButton != null) cancelOpButton.setDisable(true);
                    // refresh view
                    loadFolder(currentFolder);
                });
                currentOperation.set(null);
            }

            @Override
            public void onError(Throwable t) {
                Platform.runLater(() -> {
                    updateStatus("Copy failed: " + t.getMessage());
                    if (cancelOpButton != null) cancelOpButton.setDisable(true);
                    if (progressBar != null) progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }

            @Override
            public void onCancelled() {
                Platform.runLater(() -> {
                    updateStatus("Copy cancelled");
                    if (cancelOpButton != null) cancelOpButton.setDisable(true);
                    if (progressBar != null) progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }
        });

        currentOperation.set(op);
    }

    private void startMoveOperation(Path src, Path dest) {
        updateStatus("Starting move: " + src.getFileName());
        if (progressBar != null) progressBar.setProgress(0);
        if (cancelOpButton != null) cancelOpButton.setDisable(false);

        CancellableOperation op = fileOpManager.move(src, dest, new ProgressListener() {
            @Override
            public void onProgress(long bytesTransferred, long totalBytes) {
                Platform.runLater(() -> {
                    if (progressBar != null) {
                        if (totalBytes > 0) progressBar.setProgress((double) bytesTransferred / totalBytes);
                        else progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                    }
                });
            }

            @Override
            public void onComplete() {
                Platform.runLater(() -> {
                    updateStatus("Move complete: " + src.getFileName());
                    if (progressBar != null) progressBar.setProgress(1.0);
                    if (cancelOpButton != null) cancelOpButton.setDisable(true);
                    loadFolder(currentFolder);
                });
                currentOperation.set(null);
            }

            @Override
            public void onError(Throwable t) {
                Platform.runLater(() -> {
                    updateStatus("Move failed: " + t.getMessage());
                    if (cancelOpButton != null) cancelOpButton.setDisable(true);
                    if (progressBar != null) progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }

            @Override
            public void onCancelled() {
                Platform.runLater(() -> {
                    updateStatus("Move cancelled");
                    if (cancelOpButton != null) cancelOpButton.setDisable(true);
                    if (progressBar != null) progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }
        });

        currentOperation.set(op);
    }

    private void startDeleteOperation(Path path) {
        updateStatus("Deleting: " + path.getFileName());
        if (progressBar != null) progressBar.setProgress(0);
        if (cancelOpButton != null) cancelOpButton.setDisable(false);

        CancellableOperation op = fileOpManager.delete(path, new ProgressListener() {
            @Override
            public void onProgress(long bytesTransferred, long totalBytes) {
                Platform.runLater(() -> {
                    if (progressBar != null) {
                        if (totalBytes > 0) progressBar.setProgress((double) bytesTransferred / totalBytes);
                        else progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                    }
                });
            }

            @Override
            public void onComplete() {
                Platform.runLater(() -> {
                    updateStatus("Delete complete: " + path.getFileName());
                    if (progressBar != null) progressBar.setProgress(1.0);
                    if (cancelOpButton != null) cancelOpButton.setDisable(true);
                    loadFolder(currentFolder);
                });
                currentOperation.set(null);
            }

            @Override
            public void onError(Throwable t) {
                Platform.runLater(() -> {
                    updateStatus("Delete failed: " + t.getMessage());
                    if (cancelOpButton != null) cancelOpButton.setDisable(true);
                    if (progressBar != null) progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }

            @Override
            public void onCancelled() {
                Platform.runLater(() -> {
                    updateStatus("Delete cancelled");
                    if (cancelOpButton != null) cancelOpButton.setDisable(true);
                    if (progressBar != null) progressBar.setProgress(0);
                });
                currentOperation.set(null);
            }
        });

        currentOperation.set(op);
    }

    // ----------------------------
    // Utilities
    // ----------------------------

    private void updateStatus(String msg) {
        // Tab-level status: show in the preview container or use a global status update via MainController
        // We'll try to send it to MainController if present
        Platform.runLater(() -> {
            // find parent MainController via scene lookup (if needed) or just print to console for now
            System.out.println("[TabStatus] " + msg);
        });
    }

    /**
     * Should be called when the tab is closed to stop any running operations and free resources.
     */
    public void cleanup() {
        cancelCurrentOperation();
        try {
            fileOpManager.shutdown();
        } catch (Exception ignore) {}
    }
}
