// package com.fileexplorer.ui;

// import java.nio.file.Paths;

// import javafx.scene.control.TabPane;

// import javafx.scene.Parent;

// import javafx.stage.Stage;

// import javafx.stage.DirectoryChooser;

// import com.fileexplorer.thumb.FileOperationManager;
// import com.fileexplorer.thumb.FileOperationManager.CancellableOperation;
// import com.fileexplorer.thumb.FileOperationManager.ProgressListener;
// import com.fileexplorer.thumb.HistoryManager;
// import javafx.application.Platform;
// import javafx.fxml.FXML;
// import javafx.scene.control.*;
// import javafx.scene.layout.FlowPane;
// import javafx.scene.layout.HBox;
// import javafx.scene.layout.BorderPane;

// import java.io.File;
// import java.io.IOException;
// import java.nio.file.Path;
// import java.nio.file.Files;
// import java.util.*;
// import java.util.concurrent.atomic.AtomicReference;

// public class MainController {

    // @FXML private BorderPane historyPanel;
    // @FXML private ToolBar ribbonBar;
    // @FXML private HBox statusBar;
    // @FXML private Label statusLabel;
    // @FXML private ChoiceBox<String> themeSelector;

    // @FXML private FlowPane contentGrid;
    // @FXML private ProgressBar progressBar;
    // @FXML private Button cancelOpButton;

    // private HistoryPanelController historyPanelController;
    // private final HistoryManager historyManager = new HistoryManager();

    // private Path currentFolder;
    // private final List<FlowTileCell> selectedCells = new ArrayList<>();

    // private final FileOperationManager fileOpManager = new FileOperationManager();
    // // hold current operation to allow cancel
    // private final AtomicReference<CancellableOperation> currentOperation = new AtomicReference<>(null);

    // @FXML
    // public void initialize() {
        // Object controller = historyPanel.getProperties().get("fx:controller");
        // if (controller instanceof HistoryPanelController hp) {
            // historyPanelController = hp;
            // historyPanelController.setHistoryManager(historyManager);
            // historyPanelController.refresh();
        // }

        // themeSelector.getSelectionModel().select("Light");
        // cancelOpButton.setDisable(true);
        // progressBar.setProgress(0);

        // currentFolder = Path.of(System.getProperty("user.home"));
        // loadFolder(currentFolder);
    // }

    // // File operations (copy/move/delete) that show progress and are cancellable

    // /**
     // * Copy selected files to target folder (targetFolder must be a directory).
     // */
    // public void copySelectedTo(Path targetFolder) {
        // if (selectedCells.isEmpty()) {
            // updateStatus("No files selected to copy");
            // return;
        // }
        // for (FlowTileCell cell : selectedCells) {
            // Path src = cell.path;
            // Path dest = targetFolder.resolve(src.getFileName());
            // startCopyOperation(src, dest);
        // }
    // }

    // private void startCopyOperation(Path src, Path dest) {
        // updateStatus("Starting copy: " + src.getFileName());
        // progressBar.setProgress(0);
        // cancelOpButton.setDisable(false);

        // CancellableOperation op = fileOpManager.copy(src, dest, new ProgressListener() {
            // @Override
            // public void onProgress(long bytesTransferred, long totalBytes) {
                // double p = (totalBytes > 0) ? (double) bytesTransferred / totalBytes : -1.0;
                // Platform.runLater(() -> {
                    // if (p >= 0.0) progressBar.setProgress(p);
                    // else progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                // });
            // }

            // @Override
            // public void onComplete() {
                // Platform.runLater(() -> {
                    // updateStatus("Copy complete: " + src.getFileName());
                    // progressBar.setProgress(1.0);
                    // cancelOpButton.setDisable(true);
                    // // record move? use historyManager.recordRename or recordMove if exists
                    // historyManager.recordRename(src, dest);
                    // if (historyPanelController != null) historyPanelController.refresh();
                    // loadFolder(currentFolder);
                // });
                // currentOperation.set(null);
            // }

            // @Override
            // public void onError(Throwable t) {
                // Platform.runLater(() -> {
                    // updateStatus("Copy failed: " + t.getMessage());
                    // cancelOpButton.setDisable(true);
                    // progressBar.setProgress(0);
                // });
                // currentOperation.set(null);
            // }

            // @Override
            // public void onCancelled() {
                // Platform.runLater(() -> {
                    // updateStatus("Copy cancelled");
                    // cancelOpButton.setDisable(true);
                    // progressBar.setProgress(0);
                // });
                // currentOperation.set(null);
            // }
        // });

        // currentOperation.set(op);
    // }

    // /**
     // * Move selected files to target folder.
     // */
    // public void moveSelectedTo(Path targetFolder) {
        // if (selectedCells.isEmpty()) {
            // updateStatus("No files selected to move");
            // return;
        // }
        // for (FlowTileCell cell : selectedCells) {
            // Path src = cell.path;
            // Path dest = targetFolder.resolve(src.getFileName());
            // startMoveOperation(src, dest);
        // }
    // }

    // private void startMoveOperation(Path src, Path dest) {
        // updateStatus("Starting move: " + src.getFileName());
        // progressBar.setProgress(0);
        // cancelOpButton.setDisable(false);

        // CancellableOperation op = fileOpManager.move(src, dest, new ProgressListener() {
            // @Override
            // public void onProgress(long bytesTransferred, long totalBytes) {
                // double p = (totalBytes > 0) ? (double) bytesTransferred / totalBytes : -1.0;
                // Platform.runLater(() -> {
                    // if (p >= 0.0) progressBar.setProgress(p);
                    // else progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                // });
            // }

            // @Override
            // public void onComplete() {
                // Platform.runLater(() -> {
                    // updateStatus("Move complete: " + src.getFileName());
                    // progressBar.setProgress(1.0);
                    // cancelOpButton.setDisable(true);
                    // historyManager.recordMove(src, dest);
                    // if (historyPanelController != null) historyPanelController.refresh();
                    // loadFolder(currentFolder);
                // });
                // currentOperation.set(null);
            // }

            // @Override
            // public void onError(Throwable t) {
                // Platform.runLater(() -> {
                    // updateStatus("Move failed: " + t.getMessage());
                    // cancelOpButton.setDisable(true);
                    // progressBar.setProgress(0);
                // });
                // currentOperation.set(null);
            // }

            // @Override
            // public void onCancelled() {
                // Platform.runLater(() -> {
                    // updateStatus("Move cancelled");
                    // cancelOpButton.setDisable(true);
                    // progressBar.setProgress(0);
                // });
                // currentOperation.set(null);
            // }
        // });

        // currentOperation.set(op);
    // }

    // /**
     // * Delete selected files with progress.
     // */
    // public void deleteSelectedWithProgress() {
        // if (selectedCells.isEmpty()) {
            // updateStatus("No files selected to delete");
            // return;
        // }
        // // For simplicity, delete items one by one
        // Iterator<FlowTileCell> it = selectedCells.iterator();
        // if (!it.hasNext()) return;
        // FlowTileCell first = it.next();
        // startDeleteOperation(first.path);
        // // remaining items will be deleted after completion in onComplete handler or user can delete again
    // }

    // private void startDeleteOperation(Path path) {
        // updateStatus("Deleting: " + path.getFileName());
        // progressBar.setProgress(0);
        // cancelOpButton.setDisable(false);

        // CancellableOperation op = fileOpManager.delete(path, new ProgressListener() {
            // @Override
            // public void onProgress(long bytesTransferred, long totalBytes) {
                // double p = (totalBytes > 0) ? (double) bytesTransferred / totalBytes : -1.0;
                // Platform.runLater(() -> {
                    // if (p >= 0.0) progressBar.setProgress(p);
                    // else progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                // });
            // }

            // @Override
            // public void onComplete() {
                // Platform.runLater(() -> {
                    // updateStatus("Delete complete: " + path.getFileName());
                    // progressBar.setProgress(1.0);
                    // cancelOpButton.setDisable(true);
                    // historyManager.recordDelete(path);
                    // if (historyPanelController != null) historyPanelController.refresh();
                    // loadFolder(currentFolder);
                // });
                // currentOperation.set(null);
            // }

            // @Override
            // public void onError(Throwable t) {
                // Platform.runLater(() -> {
                    // updateStatus("Delete failed: " + t.getMessage());
                    // cancelOpButton.setDisable(true);
                    // progressBar.setProgress(0);
                // });
                // currentOperation.set(null);
            // }

            // @Override
            // public void onCancelled() {
                // Platform.runLater(() -> {
                    // updateStatus("Delete cancelled");
                    // cancelOpButton.setDisable(true);
                    // progressBar.setProgress(0);
                // });
                // currentOperation.set(null);
            // }
        // });

        // currentOperation.set(op);
    // }

    // /**
     // * Cancel currently running operation if any.
     // */
    // @FXML
    // private void onCancelOperation() {
        // CancellableOperation op = currentOperation.getAndSet(null);
        // if (op != null) {
            // boolean cancelled = op.cancel();
            // updateStatus(cancelled ? "Cancellation requested" : "Cancellation failed");
            // cancelOpButton.setDisable(true);
        // } else {
            // updateStatus("No operation to cancel");
        // }
    // }

    // // --- existing methods (loadFolder, updateStatus, etc.) should exist below ---
    // // For brevity, we re-add the previous loadFolder and updateStatus implementations
    // private void loadFolder(Path folder) {
        // // minimal defensive implementation — should match your prior loadFolder logic
        // contentGrid.getChildren().clear();
        // selectedCells.clear();
        // if (!Files.exists(folder) || !Files.isDirectory(folder)) return;
        // currentFolder = folder;
        // try {
            // Files.list(folder).forEach(path -> {
                // FlowTileCell cell = new FlowTileCell(path, (p, cb) -> {
                    // try {
                        // cb.accept(IconLoader.loadIcon(p.toFile()));
                    // } catch (Exception ex) {
                        // cb.accept(IconLoader.getPlaceholder());
                    // }
                    // return null;
                // });
                // cell.setOnMouseClicked(e -> {
                    // if (!selectedCells.contains(cell)) selectedCells.add(cell);
                    // else selectedCells.remove(cell);
                    // updateStatus(selectedCells.size() + " items selected");
                // });
                // ContextMenuHandler.attach(cell, path.toFile());
                // contentGrid.getChildren().add(cell);
            // });
            // updateStatus(Files.list(folder).count() + " items loaded");
        // } catch (IOException e) {
            // updateStatus("Failed to load folder: " + e.getMessage());
        // }
    // }

    // public void updateStatus(String msg) {
        // Platform.runLater(() -> statusLabel.setText(msg));
    // }

    // // cleanup on application exit
    // public void shutdown() {
        // fileOpManager.shutdown();
    // }

    // public HistoryManager getHistoryManager() {
    // // helper to retrieve active tab controller (TabContentController)
    // private TabContentController getActiveTabController() {
        // javafx.scene.control.Tab tab = null;
        // try { tab = tabPane.getSelectionModel().getSelectedItem(); } catch(Exception e) {}
        // if (tab == null) return null;
        // Object loaderObj = tab.getProperties().get("loader");
        // if (loaderObj instanceof javafx.fxml.FXMLLoader loader) {
            // Object ctrl = loader.getController();
            // if (ctrl instanceof TabContentController tcc) return tcc;
        // }
        // // fallback: try controller from content properties
        // Object contentCtrl = tab.getContent().getProperties().get("controller");
        // if (contentCtrl instanceof TabContentController tcc2) return tcc2;
        // return null;
    // }
    // // helper to retrieve active tab controller (TabContentController)
    // private TabContentController getActiveTabController() {
        // javafx.scene.control.Tab tab = null;
        // try { tab = tabPane.getSelectionModel().getSelectedItem(); } catch(Exception e) {}
        // if (tab == null) return null;
        // Object loaderObj = tab.getProperties().get("loader");
        // if (loaderObj instanceof javafx.fxml.FXMLLoader loader) {
            // Object ctrl = loader.getController();
            // if (ctrl instanceof TabContentController tcc) return tcc;
        // }
        // // fallback: try controller from content properties
        // Object contentCtrl = tab.getContent().getProperties().get("controller");
        // if (contentCtrl instanceof TabContentController tcc2) return tcc2;
        // return null;
    // }
        // return historyManager;
    // }
// }

// // --- Ribbon bar wrappers to call per-tab operations ---
// public void copySelectedToActiveTab(java.nio.file.Path targetFolder) {
    // TabContentController tcc = getActiveTabController();
    // if (tcc != null) tcc.copySelectedTo(targetFolder);
    // else updateStatus("No active tab to copy from");
// }

// public void moveSelectedToActiveTab(java.nio.file.Path targetFolder) {
    // TabContentController tcc = getActiveTabController();
    // if (tcc != null) tcc.moveSelectedTo(targetFolder);
    // else updateStatus("No active tab to move from");
// }

// public void deleteSelectedWithProgressActiveTab() {
    // TabContentController tcc = getActiveTabController();
    // if (tcc != null) tcc.deleteSelectedWithProgress();
    // else updateStatus("No active tab to delete from");
// }

// public void cancelActiveTabOperation() {
    // TabContentController tcc = getActiveTabController();
    // if (tcc != null) tcc.cancelCurrentOperation();
    // else updateStatus("No active tab operation to cancel");
// }

// // --- Ribbon bar wrappers to call per-tab operations ---
// public void copySelectedToActiveTab(java.nio.file.Path targetFolder) {
    // TabContentController tcc = getActiveTabController();
    // if (tcc != null) tcc.copySelectedTo(targetFolder);
    // else updateStatus("No active tab to copy from");
// }

// public void moveSelectedToActiveTab(java.nio.file.Path targetFolder) {
    // TabContentController tcc = getActiveTabController();
    // if (tcc != null) tcc.moveSelectedTo(targetFolder);
    // else updateStatus("No active tab to move from");
// }

// public void deleteSelectedWithProgressActiveTab() {
    // TabContentController tcc = getActiveTabController();
    // if (tcc != null) tcc.deleteSelectedWithProgress();
    // else updateStatus("No active tab to delete from");
// }

// public void cancelActiveTabOperation() {
    // TabContentController tcc = getActiveTabController();
    // if (tcc != null) tcc.cancelCurrentOperation();
    // else updateStatus("No active tab operation to cancel");
// }

// /*
 // * Helper: create a tab that stores its FXMLLoader in tab.properties ("loader"),
 // * so other code can retrieve the controller via loader.getController().
 // */
// private void createTabWithLoader(java.nio.file.Path folder) {
    // try {
        // FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fileexplorer/ui/TabContent.fxml"));
        // Parent content = loader.load();
        // Tab tab = new Tab(folder.getFileName() != null ? folder.getFileName().toString() : folder.toString(), content);
        // // store loader so MainController can retrieve per-tab controller later
        // tab.getProperties().put("loader", loader);

        // // when closing, call cleanup on the TabContentController if present
        // tab.setOnClosed(evt -> {
            // try {
                // Object ctrl = loader.getController();
                // if (ctrl instanceof com.fileexplorer.ui.TabContentController tcc) {
                    // tcc.cleanup();
                // }
            // } catch (Exception e) {
                // // ignore cleanup exceptions
            // }
        // });

        // // add tab and select it
        // try {
            // if (this.tabPane != null) {
                // this.tabPane.getTabs().add(tab);
                // this.tabPane.getSelectionModel().select(tab);
            // } else {
                // // fallback: try to find a TabPane in the scene
                // TabPane tp = (TabPane) statusBar.getScene().lookup("#tabPane");
                // if (tp != null) {
                    // tp.getTabs().add(tab);
                    // tp.getSelectionModel().select(tab);
                // }
            // }
        // } catch (Exception e) {
            // // if adding fails, add to default location
            // System.out.println("Warning: could not add tab to tabPane: " + e.getMessage());
        // }
    // } catch (Exception ex) {
        // ex.printStackTrace();
        // updateStatus("Failed to open folder in tab: " + folder);
    // }
// }

// /*
 // * Ribbon button helpers that prompt for a destination folder (DirectoryChooser) and call per-tab operations.
 // * These methods assume the per-tab wrappers were added earlier:
 // *   copySelectedToActiveTab(Path), moveSelectedToActiveTab(Path), deleteSelectedWithProgressActiveTab(), cancelActiveTabOperation()
 // */

// @FXML
// private void onRibbonCopyWithChooser() {
    // try {
        // Stage stage = (Stage) statusBar.getScene().getWindow();
        // DirectoryChooser chooser = new DirectoryChooser();
        // chooser.setTitle("Select target folder for copy");
        // java.io.File selected = chooser.showDialog(stage);
        // if (selected != null) {
            // java.nio.file.Path target = selected.toPath();
            // copySelectedToActiveTab(target);
        // } else {
            // updateStatus("Copy cancelled: no target selected");
        // }
    // } catch (Exception e) {
        // updateStatus("Copy failed: " + e.getMessage());
    // }
// }

// @FXML
// private void onRibbonMoveWithChooser() {
    // try {
        // Stage stage = (Stage) statusBar.getScene().getWindow();
        // DirectoryChooser chooser = new DirectoryChooser();
        // chooser.setTitle("Select target folder for move");
        // java.io.File selected = chooser.showDialog(stage);
        // if (selected != null) {
            // java.nio.file.Path target = selected.toPath();
            // moveSelectedToActiveTab(target);
        // } else {
            // updateStatus("Move cancelled: no target selected");
        // }
    // } catch (Exception e) {
        // updateStatus("Move failed: " + e.getMessage());
    // }
// }

// @FXML
// private void onRibbonDeleteWithProgress() {
    // // Confirm deletion with user
    // Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    // confirm.setTitle("Confirm Delete");
    // confirm.setHeaderText("Delete selected items");
    // confirm.setContentText("Are you sure you want to delete the selected items? This will attempt to use the OS recycle/trash where available.");
    // java.util.Optional<javafx.scene.control.ButtonType> res = confirm.showAndWait();
    // if (res.isPresent() && res.get() == javafx.scene.control.ButtonType.OK) {
        // deleteSelectedWithProgressActiveTab();
    // } else {
        // updateStatus("Delete cancelled");
    // }
// }

// @FXML
// private void onRibbonCancelOperation() {
    // cancelActiveTabOperation();
// }

package com.fileexplorer.controller;

import com.fileexplorer.events.*;
import com.fileexplorer.history.HistoryManager;
import com.fileexplorer.navigation.NavigationBus;
import com.fileexplorer.tabs.TabState;
import com.fileexplorer.tabs.TabPersistence;
import com.fileexplorer.views.DetailsViewController;
import com.fileexplorer.views.FlowViewController;
import com.fileexplorer.views.PreviewPaneController;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Main controller — now supports rewritten tab creation.
 * createTab(Path) is preserved but deprecated; all real logic now
 * resides in createTabWithLoader(Path).
 */
public class MainController {

    @FXML private TabPane mainTabPane;
    @FXML private StackPane previewPaneContainer;

    private HistoryManager historyManager;
    private PreviewPaneController previewPane;

    // Virtual thread executor (Java 25 virtual threads)
    private final ExecutorService vtExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().factory()
    );

    // Persistence + navigation event broadcasting
    private final TabPersistence tabPersistence = new TabPersistence();
    private final NavigationBus navBus = NavigationBus.getInstance();

    public void setHistoryManager(HistoryManager manager) {
        this.historyManager = manager;
    }

    @FXML
    public void initialize() {
        loadPreviewPane();
        restoreTabsOnStartup();
        hookTabSelectionEvents();
    }

    private void loadPreviewPane() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PreviewPane.fxml"));
            Node pane = loader.load();
            previewPane = loader.getController();
            previewPaneContainer.getChildren().setAll(pane);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Preview Pane", e);
        }
    }

    private void restoreTabsOnStartup() {
        vtExecutor.submit(() -> {
            var saved = tabPersistence.loadSavedTabs();
            Platform.runLater(() -> {
                if (saved.isEmpty()) {
                    createTabWithLoader(Path.of(System.getProperty("user.home")));
                } else {
                    saved.forEach(state -> createTabWithLoader(state.initialPath()));
                }
            });
        });
    }

    private void hookTabSelectionEvents() {
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getUserData() instanceof Path path) {
                navBus.publish(new PathSelectionEvent(path));
                if (previewPane != null)
                    previewPane.loadPreviewFor(path);
            }
        });
    }

    // ============================================================
    //               NEW METHOD (A) — full implementation
    // ============================================================

    /**
     * New authoritative method for creating a tab.
     * All prior logic has been relocated here.
     */
    public Tab createTabWithLoader(Path initialPath) {
        if (initialPath == null) throw new IllegalArgumentException("initialPath cannot be null");

        Tab tab = new Tab(initialPath.getFileName() != null ? initialPath.getFileName().toString() : initialPath.toString());
        tab.setClosable(true);
        tab.setUserData(initialPath);

        // — Load the Flow View UI —
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/FlowView.fxml"));
        Node content;
        FlowViewController flowController;
        try {
            content = loader.load();
            flowController = loader.getController();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FlowView.fxml", e);
        }

        // Set controller dependencies
        flowController.setHistoryManager(historyManager);
        flowController.setPreviewPane(previewPane);

        // Start async directory load via virtual threads
        vtExecutor.submit(() -> {
            try {
                var files = Files.list(initialPath).toList();
                Platform.runLater(() -> flowController.loadDirectory(files));
            } catch (IOException ex) {
                Platform.runLater(() -> flowController.showError("Failed to load directory: " + ex.getMessage()));
            }
        });

        tab.setContent(content);

        // When tab closes → persist state
        tab.setOnClosed(evt -> {
            tabPersistence.removeTabState(initialPath);
        });

        mainTabPane.getTabs().add(tab);
        mainTabPane.getSelectionModel().select(tab);

        // Persist immediately
        tabPersistence.addTabState(new TabState(initialPath));

        // Broadcast navigation event
        navBus.publish(new PathSelectionEvent(initialPath));

        return tab;
    }

    // ============================================================
    //          OLD METHOD (B + C) — DEPRECATED WRAPPER
    // ============================================================

    /**
     * Deprecated. Use createTabWithLoader(Path) instead.
     * Preserved only for backward compatibility.
     */
    @Deprecated(since="25.0", forRemoval=false)
    public Tab createTab(Path path) {
        return createTabWithLoader(path);
    }

    // ============================================================
    //           Public helper for UI commands
    // ============================================================

    public void openNewTabAt(Path path) {
        createTabWithLoader(path);
    }
}