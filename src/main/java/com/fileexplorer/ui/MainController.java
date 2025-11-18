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
    // Theme controls (injected by chunk136)
    @FXML private javafx.scene.control.ChoiceBox<String> baseThemeChoice;
    @FXML private javafx.scene.control.ChoiceBox<String> overlayChoice;

    @FXML public void initializeThemeControls() {
        try {
            baseThemeChoice.getItems().setAll("Light","Dark");
            baseThemeChoice.setValue(ThemeUtils.BaseTheme.LIGHT.name());
            overlayChoice.getItems().setAll("None","Glassy","Mica","Acrylic");
            overlayChoice.setValue("None");

            baseThemeChoice.getSelectionModel().selectedItemProperty().addListener((obs,oldV,newV) -> {
                ThemeUtils.BaseTheme t = "Dark".equalsIgnoreCase(newV) ? ThemeUtils.BaseTheme.DARK : ThemeUtils.BaseTheme.LIGHT;
                ThemeManager.get().setBaseTheme(t);
            });

            overlayChoice.getSelectionModel().selectedItemProperty().addListener((obs,oldV,newV) -> {
                // Apply overlay to current tab only (mixed model)
                javafx.scene.control.Tab selected = tabPane.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    ThemeUtils.Overlay ov = switch(newV) { case "Glassy" -> ThemeUtils.Overlay.GLASSY; case "Mica" -> ThemeUtils.Overlay.MICA; case "Acrylic" -> ThemeUtils.Overlay.ACRYLIC; default -> ThemeUtils.Overlay.NONE; };
                    ThemeManager.get().setTabOverlay(selected, ov);
                }
            });

            // When tab selection changes, update overlayChoice to reflect current tab
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs,oldTab,newTab) -> {
                if (newTab == null) return;
                ThemeUtils.Overlay ov = ThemeManager.get().getTabOverlay(newTab);
                overlayChoice.setValue(ov == ThemeUtils.Overlay.NONE ? "None" : ov.name().substring(0,1)+ov.name().substring(1).toLowerCase());
            });

            // Register the scene with ThemeManager if available
            if (tabPane != null && tabPane.getScene() != null) ThemeManager.get().registerScene(tabPane.getScene());
        } catch(Exception e) { e.printStackTrace(); }
    }
    private void initializeKeyboardShortcuts() {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case DELETE -> deleteSelectedRibbon();
                case F2 -> renameSelectedRibbon();
                case F4 -> showPropertiesRibbon();
                case Z -> { if (event.isControlDown()) historyManager.undo(); }
                case Y -> { if (event.isControlDown()) historyManager.redo(); }
                default -> {}
            }
        });
    }
    private PropertiesDialogController propertiesDialogController;

    public void showPropertiesDialog(java.util.List<java.io.File> files) {
        if (propertiesDialogController != null) propertiesDialogController.showProperties(files);
    }

    // RibbonBar multi-selection actions
    public void deleteSelectedRibbon() {
        for (java.io.File f : getSelectedFiles()) deleteFile(f);
    }

    public void renameSelectedRibbon() {
        java.util.List<java.io.File> selected = getSelectedFiles();
        if (selected.size() == 1) renameSelected();
        else System.out.println("Batch rename not implemented yet");
    }

    public void showPropertiesRibbon() {
        showPropertiesDialog(getSelectedFiles());
    }
    private DragAndDropHandler dragAndDropHandler;

    private void initializeDragAndDrop() {
        dragAndDropHandler = new DragAndDropHandler();
        dragAndDropHandler.setController(this);
        dragAndDropHandler.attachDragAndDrop(leftPane());
        dragAndDropHandler.attachDragAndDrop(rightPane());
    }
    public void deleteFile(java.io.File file) {
        if (file != null && historyManager != null) {
            historyManager.recordDelete(file);
            file.delete();
            refreshPanes();
        }
    }

    public java.util.List<java.io.File> getSelectedFiles() {
        java.util.List<java.io.File> selected = new java.util.ArrayList<>();
        selected.addAll(leftPane().getSelectionModel().getSelectedItems());
        selected.addAll(rightPane().getSelectionModel().getSelectedItems());
        return selected;
    }

    private void attachMultiSelection() {
        leftPane().getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        rightPane().getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
    }
    @FXML private void undoAction() {
        if (historyManager != null) historyManager.undo();
        refreshPanes();
    }

    @FXML private void redoAction() {
        if (historyManager != null) historyManager.redo();
        refreshPanes();
    }

    private void refreshPanes() {
        leftPane().refresh();
        rightPane().refresh();
        updateStatusBar();
        updatePreview(getSelectedFile());
    }
    @FXML private TabPane tabPane;
    private TabManager tabManager;

    private void initializeTabs() {
        tabManager = new TabManager(tabPane);
        tabManager.restoreSession();
    }

    @FXML private void handleClose() {
        if (tabManager != null) tabManager.saveSession();
    }
    @FXML private StatusBarController statusBarController;

    private void attachStatusBarUpdates() {
        leftPane().getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> updateStatusBar());
        rightPane().getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> updateStatusBar());
    }

    private void updateStatusBar() {
        if (statusBarController != null) {
            statusBarController.updateSelection(getSelectedFiles());
            statusBarController.updateTabInfo();
        }
    }
    @FXML private RibbonBarController ribbonBarController;

    public void initializeRibbonBar() {
        if (ribbonBarController != null) ribbonBarController.initializeRibbon(this);
    }

    // Stub methods for menu actions
    public void copySelected() { /* implement copy logic with HistoryManager */ }
    public void pasteClipboard() { /* implement paste logic with HistoryManager */ }
    public void deleteSelected() { /* implement delete logic with HistoryManager */ }
    public void renameSelected() { /* implement rename logic with HistoryManager */ }
    public void showProperties() { /* show PropertiesDialog for selected file */ }
    public java.util.List<java.io.File> getSelectedFiles() { return new java.util.ArrayList<>(); }
    public boolean clipboardHasFiles() { return false; /* implement clipboard check */ }
    @FXML private PreviewPaneController previewPaneController;

    public void updatePreview(File file) {
        if (previewPaneController != null) previewPaneController.showPreview(file);
    }

    private void attachPreviewOnSelection() {
        leftPane().getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> updatePreview(newFile));
        rightPane().getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> updatePreview(newFile));
    }
    @FXML public void initializeDragAndDrop() {
        dragAndDropHandler.setController(this);
        dragAndDropHandler.setHistoryManager(historyManager);
        dragAndDropHandler.enableDragAndDrop(leftPane());
        dragAndDropHandler.enableDragAndDrop(rightPane());
    }
    private java.util.Map<javafx.scene.control.Tab, HistoryManager> tabHistoryMap = new java.util.HashMap<>();

    public void initializeTabHistory() {
        tabPane.getTabs().forEach(tab -> {
            HistoryManager hm = new HistoryManager();
            tabHistoryMap.put(tab, hm);
            ribbonBarController.initializeUndoRedoButtons(hm);
        });
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                HistoryManager hm = tabHistoryMap.get(newTab);
                ribbonBarController.initializeUndoRedoButtons(hm);
            }
        });
    }
    @FXML public void initializeUndoRedo() {
        ribbonBarController.initializeUndoRedoButtons(historyManager);
    }
    @FXML public void initializeStatusBar() {
        // Bind left pane selection
        statusBarController.bindSelection(leftPane().getSelectionModel().getSelectedItems(), getCurrentFolder());
        // Bind right pane selection
        statusBarController.bindSelection(rightPane().getSelectionModel().getSelectedItems(), getCurrentFolder());
    }

    @FXML public void updateCurrentFolderInStatusBar(File folder) {
        statusBarController.setCurrentFolder(folder);
    }
    @FXML public void initializeRibbonBar() {
        ribbonBarController.initializeMenus(this);
    }
    @FXML public void initializePreviewPaneIntegration() {
        leftPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends java.io.File> c) -> {
            previewPaneController.showFiles(leftPane().getSelectionModel().getSelectedItems());
        });
        rightPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends java.io.File> c) -> {
            previewPaneController.showFiles(rightPane().getSelectionModel().getSelectedItems());
        });
    }
    @FXML public void initializeNavigationTreePins() {
        navigationTreeController.initializePins();
    }
    @FXML public void initializeTabManagement() {
        tabPane.setTabDragPolicy(javafx.scene.control.TabPane.TabDragPolicy.REORDER);
        tabPane.getTabs().forEach(tab -> {
            tab.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(tab.getText());
                    dialog.setTitle("Rename Tab");
                    dialog.setHeaderText("Rename Tab");
                    dialog.setContentText("New name:");
                    java.util.Optional<String> result = dialog.showAndWait();
                    result.ifPresent(newName -> tab.setText(newName));
                }
            });
        });
    }

    @FXML public void saveTabsOnExit() {
        SessionManager.saveTabStates(tabPane.getTabs());
    }

    @FXML public void loadTabsOnStartup() {
        java.util.List<String> tabNames = SessionManager.loadTabStates();
        if (!tabNames.isEmpty()) {
            tabPane.getTabs().clear();
            for (String name : tabNames) {
                javafx.scene.control.Tab tab = new javafx.scene.control.Tab(name);
                tab.setContent(createEmptyPane());
                tabPane.getTabs().add(tab);
            }
        }
    }

    private javafx.scene.layout.Pane createEmptyPane() {
        return new javafx.scene.layout.StackPane();
    }
    @FXML public void handleTabDragDrop(javafx.scene.control.ListView<java.io.File> targetPane, java.util.List<java.io.File> files) {
        DragAndDropHandler.handleMultiFileDrop(targetPane, files, this);
    }
    @FXML public void copySelectedFilesWithProgress(java.io.File targetFolder) {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty() || targetFolder == null) return;

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {
            @Override protected Void call() throws Exception {
                int total = selected.size();
                int count = 0;
                for (java.io.File f : selected) {
                    if (isCancelled()) break;
                    java.nio.file.Files.copy(f.toPath(), java.nio.file.Paths.get(targetFolder.getAbsolutePath(), f.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    updateProgress(++count, total);
                    historyManager.recordAction("Copied: " + f.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
                }
                return null;
            }
        };

        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        javafx.scene.control.Button cancelButton = new javafx.scene.control.Button("Cancel");
        cancelButton.setOnAction(e -> task.cancel());

        javafx.scene.layout.VBox dialogBox = new javafx.scene.layout.VBox(progressBar, cancelButton);
        dialogBox.setSpacing(10);
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Copying Files");
        stage.setScene(new javafx.scene.Scene(dialogBox, 300, 100));
        stage.initOwner(navigationTree.getScene().getWindow());
        stage.show();

        new Thread(task).start();
        task.setOnSucceeded(e -> stage.close());
        task.setOnCancelled(e -> stage.close());
        task.setOnFailed(e -> stage.close());
    }

    @FXML public void moveSelectedFilesWithProgress(java.io.File targetFolder) {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty() || targetFolder == null) return;

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {
            @Override protected Void call() throws Exception {
                int total = selected.size();
                int count = 0;
                for (java.io.File f : selected) {
                    if (isCancelled()) break;
                    java.nio.file.Files.move(f.toPath(), java.nio.file.Paths.get(targetFolder.getAbsolutePath(), f.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    updateProgress(++count, total);
                    historyManager.recordAction("Moved: " + f.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
                }
                getCurrentPane().getItems().removeAll(selected);
                return null;
            }
        };

        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        javafx.scene.control.Button cancelButton = new javafx.scene.control.Button("Cancel");
        cancelButton.setOnAction(e -> task.cancel());

        javafx.scene.layout.VBox dialogBox = new javafx.scene.layout.VBox(progressBar, cancelButton);
        dialogBox.setSpacing(10);
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Moving Files");
        stage.setScene(new javafx.scene.Scene(dialogBox, 300, 100));
        stage.initOwner(navigationTree.getScene().getWindow());
        stage.show();

        new Thread(task).start();
        task.setOnSucceeded(e -> stage.close());
        task.setOnCancelled(e -> stage.close());
        task.setOnFailed(e -> stage.close());
    }
    @FXML public void initializeKeyboardShortcuts() {
        javafx.scene.Scene scene = navigationTree.getScene();
        if (scene == null) return;

        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.DELETE), () -> deleteSelectedFiles());
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F2), () -> renameSelectedFile());
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.C, javafx.scene.input.KeyCombination.CONTROL_DOWN), () -> copySelectedFiles(selectTargetFolder()));
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.V, javafx.scene.input.KeyCombination.CONTROL_DOWN), () -> moveSelectedFiles(selectTargetFolder()));
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ENTER), () -> showPropertiesDialog());
    }

    private java.io.File selectTargetFolder() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Target Folder");
        return chooser.showDialog(navigationTree.getScene().getWindow());
    }

    private void showPropertiesDialog() {
        java.io.File selected = getCurrentPane().getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("PropertiesDialog.fxml"));
            javafx.scene.Parent root = loader.load();
            PropertiesDialogController controller = loader.getController();
            controller.setFile(selected);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Properties: " + selected.getName());
            stage.setScene(new javafx.scene.Scene(root));
            stage.initOwner(navigationTree.getScene().getWindow());
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private javafx.scene.control.Button deleteButton;
    @FXML private javafx.scene.control.Button renameButton;
    @FXML private javafx.scene.control.Button copyButton;
    @FXML private javafx.scene.control.Button moveButton;
    @FXML private javafx.scene.control.Button propertiesButton;

    @FXML public void initializeRibbonContext() {
        leftPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends java.io.File> c) -> updateRibbonButtons());
        rightPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends java.io.File> c) -> updateRibbonButtons());
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateRibbonButtons());
        updateRibbonButtons();
    }

    private void updateRibbonButtons() {
        boolean leftHasSelection = !leftPane().getSelectionModel().getSelectedItems().isEmpty();
        boolean rightHasSelection = !rightPane().getSelectionModel().getSelectedItems().isEmpty();
        boolean anySelection = leftHasSelection || rightHasSelection;

        deleteButton.setDisable(!anySelection);
        renameButton.setDisable(!anySelection || leftPane().getSelectionModel().getSelectedItems().size() + rightPane().getSelectionModel().getSelectedItems().size() != 1);
        copyButton.setDisable(!anySelection);
        moveButton.setDisable(!anySelection);
        propertiesButton.setDisable(!anySelection || leftPane().getSelectionModel().getSelectedItems().size() + rightPane().getSelectionModel().getSelectedItems().size() != 1);

        // Optionally: update icon size / layout combos to reflect current tab state
        javafx.scene.control.Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
        if (currentTab != null && tabStates.containsKey(currentTab)) {
            PaneState state = tabStates.get(currentTab);
            iconSizeCombo.getSelectionModel().select(suggestIconSize(state.leftLayout));
            layoutCombo.getSelectionModel().select(state.leftLayout);
        }
    }

    private String suggestIconSize(String layout) {
        switch(layout) {
            case "Tiles": return "Medium";
            case "Thumbnails": return "Large";
            default: return "Medium";
        }
    }
    @FXML private javafx.scene.control.ListView<String> undoListView;
    @FXML private javafx.scene.control.Button undoButton;
    @FXML private javafx.scene.control.Button redoButton;

    @FXML public void initializeUndoRedoPanel() {
        undoListView.setItems(historyManager.getUndoObservableList());
        undoButton.setOnAction(e -> performUndo());
        redoButton.setOnAction(e -> performRedo());
        historyManager.getUndoObservableList().addListener((javafx.collections.ListChangeListener.Change<? extends String> c) -> {
            undoButton.setDisable(historyManager.getUndoObservableList().isEmpty());
            redoButton.setDisable(historyManager.getRedoObservableList().isEmpty());
        });
    }

    private void performUndo() {
        historyManager.undo();
        refreshAllPanes();
    }

    private void performRedo() {
        historyManager.redo();
        refreshAllPanes();
    }

    private void refreshAllPanes() {
        leftPane().refresh();
        rightPane().refresh();
        // For each tab, restore tab state if necessary
        for (javafx.scene.control.Tab tab : tabPane.getTabs()) {
            restoreTabState(tab);
        }
    }
    @FXML public void deleteSelectedFiles() {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;
        for (java.io.File f : selected) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().moveToTrash(f);
                else f.delete();
                historyManager.recordAction("Deleted: " + f.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
        getCurrentPane().getItems().removeAll(selected);
    }

    @FXML public void renameSelectedFile() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        java.io.File selected = pane.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(selected.getName());
        dialog.setTitle("Rename File");
        dialog.setHeaderText("Rename File");
        dialog.setContentText("New name:");
        java.util.Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            java.io.File target = new java.io.File(selected.getParent(), newName);
            if (selected.renameTo(target)) {
                pane.getItems().set(pane.getItems().indexOf(selected), target);
                historyManager.recordAction("Renamed: " + selected.getAbsolutePath() + " -> " + target.getAbsolutePath());
            }
        });
    }

    @FXML public void moveSelectedFiles(java.io.File targetFolder) {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty() || targetFolder == null) return;
        for (java.io.File f : selected) {
            try {
                java.nio.file.Files.move(f.toPath(), java.nio.file.Paths.get(targetFolder.getAbsolutePath(), f.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                historyManager.recordAction("Moved: " + f.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
        getCurrentPane().getItems().removeAll(selected);
    }

    @FXML public void copySelectedFiles(java.io.File targetFolder) {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty() || targetFolder == null) return;
        for (java.io.File f : selected) {
            try {
                java.nio.file.Files.copy(f.toPath(), java.nio.file.Paths.get(targetFolder.getAbsolutePath(), f.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                historyManager.recordAction("Copied: " + f.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
    private java.util.Map<javafx.scene.control.Tab, PaneState> tabStates = new java.util.HashMap<>();

    private static class PaneState {
        java.io.File leftFolder;
        java.io.File rightFolder;
        String leftLayout; String rightLayout;
        String leftSort; String rightSort;
        String leftFilter; String rightFilter;
    }

    private void saveCurrentTabState() {
        javafx.scene.control.Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) return;
        PaneState state = new PaneState();
        state.leftFolder = leftPaneCurrentFolder();
        state.rightFolder = rightPaneCurrentFolder();
        state.leftLayout = getPaneLayout(leftPane());
        state.rightLayout = getPaneLayout(rightPane());
        state.leftSort = getPaneSort(leftPane());
        state.rightSort = getPaneSort(rightPane());
        state.leftFilter = getPaneFilter(leftPane());
        state.rightFilter = getPaneFilter(rightPane());
        tabStates.put(tab, state);
    }

    private void restoreTabState(javafx.scene.control.Tab tab) {
        PaneState state = tabStates.get(tab);
        if (state == null) return;
        setPaneFolder(leftPane(), state.leftFolder);
        setPaneFolder(rightPane(), state.rightFolder);
        setPaneLayout(leftPane(), state.leftLayout);
        setPaneLayout(rightPane(), state.rightLayout);
        setPaneSort(leftPane(), state.leftSort);
        setPaneSort(rightPane(), state.rightSort);
        setPaneFilter(leftPane(), state.leftFilter);
        setPaneFilter(rightPane(), state.rightFilter);
    }

    @FXML public void initializeDragAndDrop() {
        installDragAndDrop(leftPane());
        installDragAndDrop(rightPane());
    }

    private void installDragAndDrop(javafx.scene.control.ListView<java.io.File> pane) {
        pane.setOnDragDetected(event -> DragAndDropHandler.startDrag(pane, event));
        pane.setOnDragOver(event -> DragAndDropHandler.handleDragOver(pane, event));
        pane.setOnDragDropped(event -> {
            DragAndDropHandler.handleDrop(pane, event);
            saveCurrentTabState();
        });
    }
    @FXML private javafx.scene.control.TabPane tabPane;

    @FXML public void addNewTab() {
        javafx.scene.control.Tab tab = new javafx.scene.control.Tab("New Tab");
        javafx.scene.layout.BorderPane pane = new javafx.scene.layout.BorderPane();
        tab.setContent(pane);
        tab.setClosable(true);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        historyManager.recordAction("Added new tab");
    }

    @FXML public void closeCurrentTab() {
        javafx.scene.control.Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null) {
            tabPane.getTabs().remove(selected);
            historyManager.recordAction("Closed tab: " + selected.getText());
        }
    }

    @FXML public void moveTabLeft() {
        javafx.scene.control.Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int idx = tabPane.getTabs().indexOf(selected);
            if (idx > 0) {
                tabPane.getTabs().remove(selected);
                tabPane.getTabs().add(idx-1, selected);
                tabPane.getSelectionModel().select(selected);
                historyManager.recordAction("Moved tab left: " + selected.getText());
            }
        }
    }

    @FXML public void moveTabRight() {
        javafx.scene.control.Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int idx = tabPane.getTabs().indexOf(selected);
            if (idx < tabPane.getTabs().size() - 1) {
                tabPane.getTabs().remove(selected);
                tabPane.getTabs().add(idx+1, selected);
                tabPane.getSelectionModel().select(selected);
                historyManager.recordAction("Moved tab right: " + selected.getText());
            }
        }
    }

    private void saveTabState(javafx.scene.control.Tab tab) {
        // Save per-tab state such as current folder, layout, sort, filters
        // Implementation placeholder for persistent storage
    }
    @FXML private javafx.scene.control.ComboBox<String> iconSizeCombo;
    @FXML private javafx.scene.control.ComboBox<String> layoutCombo;
    @FXML private javafx.scene.control.ComboBox<String> themeCombo;

    @FXML public void initializeViewOptions() {
        iconSizeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> setIconSize(newVal));
        layoutCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> setLayout(newVal));
        themeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> setTheme(newVal));
    }

    private void setIconSize(String size) {
        int pixels = switch(size) { case "Small" -> 64; case "Medium" -> 128; case "Large" -> 256; default -> 128; };
        getCurrentPane().setCellFactory(lv -> new FlowTileCell(pixels, pixels));
        getCurrentPane().refresh();
        historyManager.recordAction("Set icon size: " + size);
    }

    private void setLayout(String layout) {
        switch(layout) {
            case "Details" -> switchToTableView();
            case "Tiles" -> switchToTileView();
            case "List" -> switchToListView();
            case "Thumbnails" -> switchToThumbnailView();
        }
        historyManager.recordAction("Set layout: " + layout);
    }

    private void setTheme(String theme) {
        javafx.scene.Scene scene = navigationTree.getScene();
        scene.getStylesheets().clear();
        switch(theme) {
            case "Light" -> scene.getStylesheets().add(getClass().getResource("light.css").toExternalForm());
            case "Dark" -> scene.getStylesheets().add(getClass().getResource("dark.css").toExternalForm());
            case "Glassy" -> scene.getStylesheets().add(getClass().getResource("glassy.css").toExternalForm());
        }
        historyManager.recordAction("Set theme: " + theme);
    }

    private void switchToTableView() { /* code to replace current pane with TableView */ }
    private void switchToTileView() { /* code to replace current pane with Tile-based FlowPane */ }
    private void switchToListView() { /* code to replace current pane with ListView */ }
    private void switchToThumbnailView() { /* code to replace current pane with Thumbnail view using FlowTileCell */ }
    @FXML public void showPropertiesDialog() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("PropertiesDialog.fxml"));
            javafx.scene.Parent root = loader.load();
            PropertiesDialogController controller = loader.getController();
            controller.setFile(selected);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Properties: " + selected.getName());
            stage.setScene(new javafx.scene.Scene(root));
            stage.initOwner(navigationTree.getScene().getWindow());
            stage.show();
            historyManager.recordAction("Opened properties for: " + selected.getAbsolutePath());
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private javafx.scene.layout.Pane previewPane;
    @FXML private javafx.scene.control.Label previewLabel;
    @FXML private javafx.scene.image.ImageView previewImageView;

    @FXML public void initializePreviewPane() {
        getCurrentPane().getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> showPreview(newVal));
    }

    private void showPreview(java.io.File file) {
        if (file == null) { previewImageView.setImage(null); previewLabel.setText(""); return; }
        if (file.isDirectory()) { previewLabel.setText("Folder: " + file.getName()); previewImageView.setImage(null); return; }
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif")) {
                javafx.scene.image.Image img = ThumbnailGenerator.generatePreview(file, 256, 256);
                previewImageView.setImage(img); previewLabel.setText(file.getName());
            } else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".csv")) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
                previewLabel.setText(String.join("\n", lines.subList(0, Math.min(20, lines.size()))));
                previewImageView.setImage(null);
            } else {
                previewLabel.setText("No preview available for: " + file.getName());
                previewImageView.setImage(null);
            }
        } catch(Exception e){ previewLabel.setText("Error loading preview"); previewImageView.setImage(null); e.printStackTrace(); }
        historyManager.recordAction("Previewed file: " + file.getAbsolutePath());
    }
    @FXML private javafx.scene.control.ComboBox<String> sortCombo;
    @FXML private javafx.scene.control.ComboBox<String> groupCombo;
    @FXML private javafx.scene.control.ContextMenu columnMenu;

    @FXML public void initializeSortGroupColumns() {
        sortCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> applySort(newVal));
        groupCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> applyGroup(newVal));
        // columnMenu items should be CheckMenuItem for visibility toggle
    }

    private void applySort(String sortKey) {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        java.util.Comparator<java.io.File> comparator = switch(sortKey) {
            case "Name" -> java.util.Comparator.comparing(java.io.File::getName, String.CASE_INSENSITIVE_ORDER);
            case "Date Modified" -> java.util.Comparator.comparingLong(java.io.File::lastModified);
            case "Size" -> java.util.Comparator.comparingLong(java.io.File::length);
            case "Type" -> java.util.Comparator.comparing(f -> f.getName().substring(f.getName().lastIndexOf(.)+1));
            default -> java.util.Comparator.comparing(java.io.File::getName, String.CASE_INSENSITIVE_ORDER);
        };
        javafx.collections.ObservableList<java.io.File> items = pane.getItems();
        javafx.collections.FXCollections.sort(items, comparator);
        pane.refresh();
        saveFolderPreference(getCurrentFolder(), "sort", sortKey);
        historyManager.recordAction("Applied sort: " + sortKey);
    }

    private void applyGroup(String groupKey) {
        // Simplified example: grouping may require custom cell factory or Section headers
        saveFolderPreference(getCurrentFolder(), "group", groupKey);
        historyManager.recordAction("Applied group: " + groupKey);
        // Actual grouping implementation omitted for brevity; can wrap items in group headers
    }

    private void toggleColumnVisibility(String columnName, boolean visible) {
        // For TableView pane, show/hide columns
        javafx.scene.control.TableView<java.io.File> table = getCurrentTablePane();
        table.getColumns().stream().filter(c -> c.getText().equals(columnName)).forEach(c -> c.setVisible(visible));
        historyManager.recordAction("Toggled column: " + columnName + " visible=" + visible);
    }
    @FXML public void renameSelectedItem() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(selected.getName());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename selected item");
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> {
            java.io.File newFile = new java.io.File(selected.getParent(), newName);
            java.util.concurrent.Executor executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(java.lang.Thread.ofVirtual().factory());
            executor.execute(() -> {
                try {
                    java.nio.file.Files.move(selected.toPath(), newFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    javafx.application.Platform.runLater(() -> refreshCurrentPane());
                    historyManager.recordAction("Renamed " + selected + " to " + newFile,
                        () -> { try { java.nio.file.Files.move(newFile.toPath(), selected.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); refreshCurrentPane(); } catch(Exception e){e.printStackTrace(); } });
                } catch(Exception e){ e.printStackTrace(); }
            });
        });
    }

    @FXML public void createNewFolder() {
        java.io.File folder = getCurrentFolder();
        if (folder == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("New Folder");
        dialog.setTitle("New Folder");
        dialog.setHeaderText("Create new folder");
        dialog.setContentText("Folder name:");
        dialog.showAndWait().ifPresent(name -> {
            java.io.File newDir = new java.io.File(folder, name);
            java.util.concurrent.Executor executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(java.lang.Thread.ofVirtual().factory());
            executor.execute(() -> {
                try { java.nio.file.Files.createDirectory(newDir.toPath());
                    javafx.application.Platform.runLater(() -> refreshCurrentPane());
                    historyManager.recordAction("Created folder: " + newDir,
                        () -> { try { java.nio.file.Files.deleteIfExists(newDir.toPath()); refreshCurrentPane(); } catch(Exception e){e.printStackTrace();} });
                } catch(Exception e){ e.printStackTrace(); }
            });
        });
    }

    @FXML public void deleteSelectedItem() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        java.util.concurrent.Executor executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(java.lang.Thread.ofVirtual().factory());
        executor.execute(() -> {
            try {
                boolean movedToTrash = false;
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().moveToTrash(selected)) movedToTrash = true;
                else java.nio.file.Files.deleteIfExists(selected.toPath());
                javafx.application.Platform.runLater(() -> refreshCurrentPane());
                historyManager.recordAction((movedToTrash ? "Moved to Trash: " : "Deleted: ") + selected,
                    () -> { /* Undo for delete not implemented yet */ });
            } catch(Exception e){ e.printStackTrace(); }
        });
    }
    private void performPasteWithUndo(java.io.File src, java.io.File dest, boolean move) {
        try {
            java.nio.file.Path srcPath = src.toPath();
            java.nio.file.Path destPath = dest.toPath();
            if (move) java.nio.file.Files.move(srcPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            else {
                if (src.isDirectory()) copyDirectory(srcPath, destPath);
                else java.nio.file.Files.copy(srcPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            historyManager.recordAction((move ? "Moved" : "Copied") + " " + src + " to " + dest,
                () -> {
                    try {
                        java.nio.file.Files.deleteIfExists(destPath);
                        refreshCurrentPane();
                    } catch (Exception e) { e.printStackTrace(); }
                });
        } catch (Exception e) { e.printStackTrace(); }
    }
    private java.util.List<java.io.File> clipboard = new java.util.ArrayList<>();
    private boolean cutMode = false;

    @FXML public void copySelected() {
        clipboard.clear();
        clipboard.addAll(getCurrentPane().getSelectionModel().getSelectedItems());
        cutMode = false;
        historyManager.recordAction("Copied items: " + clipboard);
    }

    @FXML public void cutSelected() {
        clipboard.clear();
        clipboard.addAll(getCurrentPane().getSelectionModel().getSelectedItems());
        cutMode = true;
        historyManager.recordAction("Cut items: " + clipboard);
    }

    @FXML public void pasteIntoCurrentFolder() {
        java.io.File targetFolder = getCurrentFolder();
        if (targetFolder == null || clipboard.isEmpty()) return;
        java.util.concurrent.Executor executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(java.lang.Thread.ofVirtual().factory());
        for (java.io.File src : clipboard) {
            executor.execute(() -> {
                try {
                    java.nio.file.Path srcPath = src.toPath();
                    java.nio.file.Path destPath = targetFolder.toPath().resolve(src.getName());
                    if (cutMode) {
                        java.nio.file.Files.move(srcPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        if (src.isDirectory()) {
                            copyDirectory(srcPath, destPath);
                        } else {
                            java.nio.file.Files.copy(srcPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    javafx.application.Platform.runLater(() -> refreshCurrentPane());
                    historyManager.recordAction((cutMode ? "Moved" : "Copied") + " " + src + " to " + targetFolder);
                } catch (Exception e) { e.printStackTrace(); }
            });
        }
        if (cutMode) clipboard.clear();
        cutMode = false;
    }

    private void copyDirectory(java.nio.file.Path src, java.nio.file.Path dest) throws java.io.IOException {
        java.nio.file.Files.walk(src).forEach(p -> {
            try {
                java.nio.file.Path relative = src.relativize(p);
                java.nio.file.Path target = dest.resolve(relative);
                if (java.nio.file.Files.isDirectory(p)) java.nio.file.Files.createDirectories(target);
                else java.nio.file.Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
    @FXML private javafx.scene.control.TabPane tabPane;

    @FXML public void addNewTab(java.io.File folder) {
        javafx.scene.control.Tab tab = new javafx.scene.control.Tab(folder.getName());
        javafx.scene.layout.BorderPane content = new javafx.scene.layout.BorderPane();
        javafx.scene.control.ListView<java.io.File> listView = new javafx.scene.control.ListView<>();
        populatePane(listView, folder);
        content.setCenter(listView);
        tab.setContent(content);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        historyManager.recordAction("Added new tab for folder: " + folder.getAbsolutePath());
    }

    @FXML public void closeCurrentTab() {
        javafx.scene.control.Tab current = tabPane.getSelectionModel().getSelectedItem();
        if (current != null) {
            tabPane.getTabs().remove(current);
            historyManager.recordAction("Closed tab: " + current.getText());
        }
    }

    @FXML public void reorderTabs(int fromIndex, int toIndex) {
        javafx.scene.control.Tab t = tabPane.getTabs().remove(fromIndex);
        tabPane.getTabs().add(toIndex, t);
        historyManager.recordAction("Reordered tab from " + fromIndex + " to " + toIndex);
    }

    @FXML public void initializeTabs() {
        tabPane.getTabs().clear();
        // optionally add initial default folder tab
        // tabPane.getTabs().add(createDefaultTab());
    }
    private java.util.List<java.io.File> pinnedItems = new java.util.ArrayList<>();

    @FXML private javafx.scene.control.TreeView<java.io.File> navigationTree;

    @FXML public void pinSelectedItem() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        if (!pinnedItems.contains(selected)) {
            pinnedItems.add(selected);
            addPinnedItemToTree(selected);
            historyManager.recordAction("Pinned item: " + selected.getAbsolutePath());
        }
    }

    @FXML public void unpinSelectedItem() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        if (pinnedItems.remove(selected)) {
            removePinnedItemFromTree(selected);
            historyManager.recordAction("Unpinned item: " + selected.getAbsolutePath());
        }
    }

    private void addPinnedItemToTree(java.io.File item) {
        javafx.scene.control.TreeItem<java.io.File> root = navigationTree.getRoot();
        javafx.scene.control.TreeItem<java.io.File> node = new javafx.scene.control.TreeItem<>(item);
        root.getChildren().add(node);
    }

    private void removePinnedItemFromTree(java.io.File item) {
        javafx.scene.control.TreeItem<java.io.File> root = navigationTree.getRoot();
        root.getChildren().removeIf(ti -> ti.getValue().equals(item));
    }

    private java.io.File getSelectedFolderOrFile() {
        javafx.scene.control.TreeItem<java.io.File> sel = navigationTree.getSelectionModel().getSelectedItem();
        return sel != null ? sel.getValue() : null;
    }
    private java.util.Map<java.io.File, java.util.Map<String, String>> folderPreferences = new java.util.HashMap<>();

    private void saveFolderPreference(java.io.File folder, String key, String value) {
        folderPreferences.computeIfAbsent(folder, f -> new java.util.HashMap<>()).put(key, value);
        historyManager.recordAction("Saved preference for " + folder.getAbsolutePath() + ": " + key + "=" + value);
    }

    private String getFolderPreference(java.io.File folder, String key, String defaultValue) {
        return folderPreferences.getOrDefault(folder, java.util.Collections.emptyMap()).getOrDefault(key, defaultValue);
    }

    private void applyFolderPreferences(java.io.File folder) {
        String sort = getFolderPreference(folder, "sort", "Name");
        String group = getFolderPreference(folder, "group", "None");
        String layout = getFolderPreference(folder, "layout", "THUMBNAILS");
        sortCurrentPane(sort);
        groupCurrentPane(group);
        switchView(layout);
    }

    private void onFolderChanged(java.io.File folder) {
        applyFolderPreferences(folder);
    }
    @FXML private javafx.scene.control.ToggleGroup viewToggleGroup;

    @FXML public void initializeViewToggle() {
        viewToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                String view = (String)newToggle.getUserData();
                switchView(view);
            }
        });
    }

    private void switchView(String view) {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        switch(view) {
            case "THUMBNAILS":
                pane.setCellFactory(f -> new FlowTileCell(128, 128));
                break;
            case "LARGE_ICONS":
                pane.setCellFactory(f -> new FlowTileCell(64, 64));
                break;
            case "SMALL_ICONS":
                pane.setCellFactory(f -> new FlowTileCell(32, 32));
                break;
        }
        pane.refresh();
        historyManager.recordAction("Switched view to: " + view);
    }
    @FXML public void showFileProperties() {
        java.io.File selected = getSelectedFile();
        if (selected == null) return;
        try {
            PropertiesDialogController dialog = new PropertiesDialogController(selected);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("PropertiesDialog.fxml"));
            loader.setController(dialog);
            stage.setScene(new javafx.scene.Scene(loader.load()));
            stage.setTitle("Properties - " + selected.getName());
            stage.initOwner(tabPane.getScene().getWindow());
            stage.showAndWait();
            historyManager.recordAction("Viewed properties for: " + selected.getAbsolutePath());
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private javafx.scene.layout.Pane previewPane;
    @FXML private javafx.scene.image.ImageView previewImageView;
    @FXML private javafx.scene.web.WebView previewWebView;

    @FXML public void togglePreviewPane() {
        boolean visible = previewPane.isVisible();
        previewPane.setVisible(!visible);
        historyManager.recordAction("Toggled Preview Pane: " + (!visible));
    }

    @FXML public void updatePreviewPane() {
        java.io.File selected = getSelectedFile();
        if (selected == null) { previewImageView.setImage(null); previewWebView.getEngine().loadContent(""); return; }
        String name = selected.getName().toLowerCase();
        try {
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".bmp")) {
                javafx.scene.image.Image img = new javafx.scene.image.Image(selected.toURI().toString(), true);
                previewImageView.setImage(img);
                previewWebView.setVisible(false); previewImageView.setVisible(true);
            } else if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".txt") || name.endsWith(".md")) {
                previewWebView.getEngine().load(selected.toURI().toString());
                previewWebView.setVisible(true); previewImageView.setVisible(false);
            } else {
                previewImageView.setImage(null); previewWebView.getEngine().loadContent("<html><body><p>No preview available</p></body></html>");
                previewWebView.setVisible(true); previewImageView.setVisible(false);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML public void initializePreviewPane() {
        getCurrentPane().getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> updatePreviewPane());
    }
    @FXML private javafx.scene.layout.Pane detailsPane;
    @FXML private javafx.scene.control.ListView<java.io.File> previewListView;

    @FXML public void toggleDetailsPane() {
        boolean visible = detailsPane.isVisible();
        detailsPane.setVisible(!visible);
        historyManager.recordAction("Toggled Details Pane: " + (!visible));
    }

    @FXML public void updateDetailsPane() {
        java.util.List<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        previewListView.getItems().clear();
        previewListView.getItems().addAll(selected);
    }

    @FXML public void initializeDetailsPane() {
        getCurrentPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<java.io.File>) c -> updateDetailsPane());
    }
    @FXML private javafx.scene.control.ComboBox<String> sortComboBox;
    @FXML private javafx.scene.control.ComboBox<String> groupComboBox;

    @FXML public void initializeSorting() {
        sortComboBox.getItems().addAll("Name", "Size", "Date Modified", "Type");
        groupComboBox.getItems().addAll("None", "Type", "Date Modified");

        sortComboBox.valueProperty().addListener((obs, oldVal, newVal) -> sortCurrentPane(newVal));
        groupComboBox.valueProperty().addListener((obs, oldVal, newVal) -> groupCurrentPane(newVal));
    }

    private void sortCurrentPane(String criterion) {
        java.util.List<java.io.File> items = getCurrentPane().getItems();
        if (criterion == null) return;
        switch(criterion) {
            case "Name": items.sort((a,b) -> a.getName().compareToIgnoreCase(b.getName())); break;
            case "Size": items.sort((a,b) -> Long.compare(a.length(), b.length())); break;
            case "Date Modified": items.sort((a,b) -> Long.compare(a.lastModified(), b.lastModified())); break;
            case "Type": items.sort((a,b) -> getFileExtension(a).compareToIgnoreCase(getFileExtension(b))); break;
        }
        refreshCurrentPane();
        historyManager.recordAction("Sorted current pane by: " + criterion);
    }

    private void groupCurrentPane(String criterion) {
        // Simple grouping: prepend group headers (pseudo) in Details view
        historyManager.recordAction("Grouped current pane by: " + criterion);
        // TODO: actual UI grouping implementation for Tiles view (future enhancement)
    }

    private javafx.scene.control.ListView<java.io.File> getCurrentPane() {
        return leftPaneFlow.isFocused() ? leftPaneFlow : rightPaneFlow;
    }
    @FXML private javafx.scene.control.TextField searchField;

    @FXML public void initializeSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterCurrentPane(newVal);
        });
    }

    private void filterCurrentPane(String query) {
        javafx.scene.control.ListView<java.io.File> pane = leftPaneFlow.isFocused() ? leftPaneFlow : rightPaneFlow;
        java.util.List<java.io.File> allItems = pane.getItems();
        pane.getItems().clear();
        for (java.io.File f : allItems) {
            if (query == null || query.isEmpty() || f.getName().toLowerCase().contains(query.toLowerCase())) {
                pane.getItems().add(f);
            }
        }
        historyManager.recordAction("Filtered current pane with query: " + query);
    }
    @FXML public void refreshCurrentPane() {
        if (leftPaneFlow.isFocused()) populatePane(leftPaneFlow, leftFolder);
        else populatePane(rightPaneFlow, rightFolder);
        historyManager.recordAction("Refreshed current pane");
    }

    @FXML public void selectColumns() {
        javafx.scene.control.CheckBoxListCell<javafx.scene.control.TableColumn<java.io.File,?>> cell;
        // For simplicity, show dialog listing columns to toggle
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Select Columns");
        javafx.scene.control.VBox vbox = new javafx.scene.control.VBox();
        for (javafx.scene.control.TableColumn<java.io.File,?> col : detailsTable.getColumns()) {
            javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(col.getText());
            cb.setSelected(col.isVisible());
            cb.selectedProperty().addListener((obs, oldV, newV) -> col.setVisible(newV));
            vbox.getChildren().add(cb);
        }
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.OK);
        dialog.showAndWait();
        historyManager.recordAction("Adjusted column visibility");
    }

    private void startAutoRefreshWatcher(java.io.File folder, javafx.scene.control.ListView<java.io.File> pane) {
        java.nio.file.Path path = folder.toPath();
        java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
            try (java.nio.file.WatchService watcher = path.getFileSystem().newWatchService()) {
                path.register(watcher, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                                         java.nio.file.StandardWatchEventKinds.ENTRY_DELETE,
                                         java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
                while (true) {
                    java.nio.file.WatchKey key = watcher.take();
                    javafx.application.Platform.runLater(() -> populatePane(pane, folder));
                    key.reset();
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
    @FXML public void createNewFolder() {
        java.io.File parent = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        String baseName = "New Folder";
        java.io.File newFolder = new java.io.File(parent, baseName);
        int counter = 1;
        while(newFolder.exists()) { newFolder = new java.io.File(parent, baseName + " (" + counter + ")"); counter++; }
        if (newFolder.mkdir()) historyManager.recordAction("Created folder: " + newFolder.getAbsolutePath());
        refreshCurrentPane();
    }

    @FXML public void createNewFile() {
        java.io.File parent = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        String baseName = "New File.txt";
        java.io.File newFile = new java.io.File(parent, baseName);
        int counter = 1;
        while(newFile.exists()) { newFile = new java.io.File(parent, "New File (" + counter + ").txt"); counter++; }
        try { if (newFile.createNewFile()) historyManager.recordAction("Created file: " + newFile.getAbsolutePath()); } catch(Exception e){ e.printStackTrace(); }
        refreshCurrentPane();
    }

    @FXML public void renameSelectedFile() {
        java.io.File selected = getSelectedFile();
        if (selected == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(selected.getName());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename File / Folder");
        dialog.setContentText("New name:");
        dialog.initOwner(tabPane.getScene().getWindow());
        java.util.Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            java.io.File renamed = new java.io.File(selected.getParentFile(), name);
            if(selected.renameTo(renamed)) historyManager.recordAction("Renamed: " + selected.getAbsolutePath() + " -> " + renamed.getAbsolutePath());
            refreshCurrentPane();
        });
    }
    @FXML public void deleteFiles(boolean permanent) {
        java.util.List<java.io.File> files = getCurrentPane().getSelectionModel().getSelectedItems();
        if (files.isEmpty()) return;
        for (java.io.File f : files) {
            try {
                if (permanent) {
                    java.nio.file.Files.deleteIfExists(f.toPath());
                    historyManager.recordAction("Permanently deleted: " + f.getAbsolutePath());
                } else {
                    boolean moved = java.awt.Desktop.getDesktop().moveToTrash(f);
                    historyManager.recordAction((moved ? "Recycled: " : "Failed recycle: ") + f.getAbsolutePath());
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        refreshCurrentPane();
    }

    @FXML public void deleteSelectedFiles() {
        deleteFiles(false);
    }

    @FXML public void permanentlyDeleteSelectedFiles() {
        deleteFiles(true);
    }
    private java.util.List<java.io.File> clipboardFiles = new java.util.ArrayList<>();
    private boolean isCutOperation = false;

    @FXML public void copyFiles() {
        clipboardFiles.clear();
        clipboardFiles.addAll(getCurrentPane().getSelectionModel().getSelectedItems());
        isCutOperation = false;
        historyManager.recordAction("Copied " + clipboardFiles.size() + " files to clipboard");
    }

    @FXML public void cutFiles() {
        clipboardFiles.clear();
        clipboardFiles.addAll(getCurrentPane().getSelectionModel().getSelectedItems());
        isCutOperation = true;
        historyManager.recordAction("Cut " + clipboardFiles.size() + " files to clipboard");
    }

    @FXML public void pasteFiles() {
        java.io.File targetFolder = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        for (java.io.File f : clipboardFiles) {
            try {
                java.io.File dest = new java.io.File(targetFolder, f.getName());
                if (isCutOperation) {
                    java.nio.file.Files.move(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    historyManager.recordAction("Moved file: " + f.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                } else {
                    java.nio.file.Files.copy(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    historyManager.recordAction("Copied file: " + f.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        refreshCurrentPane();
        if (isCutOperation) clipboardFiles.clear();
    }
    @FXML public void showAggregatedProperties() {
        java.util.List<java.io.File> files = getCurrentPane().getSelectionModel().getSelectedItems();
        if (files.isEmpty()) return;
        long totalSize = 0;
        java.util.Map<String, Integer> typeCounts = new java.util.HashMap<>();
        for (java.io.File f : files) {
            totalSize += f.length();
            String ext = getFileExtension(f);
            typeCounts.put(ext, typeCounts.getOrDefault(ext,0)+1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Selected Files: ").append(files.size()).append("\n");
        sb.append("Total Size: ").append(totalSize / 1024).append(" KB\n\n");
        sb.append("File Types:\n");
        for (java.util.Map.Entry<String,Integer> e : typeCounts.entrySet()) sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Aggregated Properties");
        alert.setHeaderText("Multiple Files Properties");
        alert.setContentText(sb.toString());
        alert.initOwner(tabPane.getScene().getWindow());
        alert.showAndWait();
        historyManager.recordAction("Viewed aggregated properties for " + files.size() + " files");
    }
    private QuickAccessManager quickAccess = new QuickAccessManager();

    @FXML public void sendToFolder(java.io.File targetFolder) {
        java.util.List<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        for (java.io.File f : selected) {
            try {
                java.nio.file.Files.copy(f.toPath(), new java.io.File(targetFolder, f.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                historyManager.recordAction("Sent file to: " + targetFolder.getAbsolutePath() + " -> " + f.getName());
            } catch (Exception e) { e.printStackTrace(); }
        }
        refreshCurrentPane();
    }

    @FXML public void pinSelectedFolder() {
        java.io.File folder = getSelectedFile();
        if (folder != null && folder.isDirectory()) {
            quickAccess.pinFolder(folder);
            historyManager.recordAction("Pinned folder: " + folder.getAbsolutePath());
        }
    }

    @FXML public void unpinSelectedFolder() {
        java.io.File folder = getSelectedFile();
        if (folder != null && folder.isDirectory()) {
            quickAccess.unpinFolder(folder);
            historyManager.recordAction("Unpinned folder: " + folder.getAbsolutePath());
        }
    }
    @FXML public void openWithDefaultApp() {
        java.util.List<java.io.File> files = getCurrentPane().getSelectionModel().getSelectedItems();
        if (files.isEmpty()) return;
        for (java.io.File f : files) {
            try { java.awt.Desktop.getDesktop().open(f);
            historyManager.recordAction("Opened with default app: " + f.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @FXML public void openWithCustomApp() {
        java.util.List<java.io.File> files = getCurrentPane().getSelectionModel().getSelectedItems();
        if (files.isEmpty()) return;
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select Application");
        java.io.File app = chooser.showOpenDialog(tabPane.getScene().getWindow());
        if (app != null) {
            for (java.io.File f : files) {
                try { new ProcessBuilder(app.getAbsolutePath(), f.getAbsolutePath()).start();
                historyManager.recordAction("Opened with custom app: " + f.getAbsolutePath());
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }
    @FXML public void copyPathToClipboard() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            final javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            final javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(selected.getAbsolutePath());
            clipboard.setContent(content);
            historyManager.recordAction("Copied path to clipboard: " + selected.getAbsolutePath());
        }
    }

    @FXML public void openTerminalHere() {
        java.io.File folder = getSelectedFile();
        if (folder == null || !folder.isDirectory()) folder = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "cmd", "/K", "cd /d " + folder.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-a", "Terminal", folder.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("x-terminal-emulator", "--working-directory=" + folder.getAbsolutePath()).start();
            }
            historyManager.recordAction("Opened terminal at: " + folder.getAbsolutePath());
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML public void setIconSizeSmall() {
        FlowTileCell.setGlobalIconSize(32);
        refreshCurrentPane();
        historyManager.recordAction("Set icon size to Small");
    }

    @FXML public void setIconSizeMedium() {
        FlowTileCell.setGlobalIconSize(64);
        refreshCurrentPane();
        historyManager.recordAction("Set icon size to Medium");
    }

    @FXML public void setIconSizeLarge() {
        FlowTileCell.setGlobalIconSize(128);
        refreshCurrentPane();
        historyManager.recordAction("Set icon size to Large");
    }

    @FXML public void toggleColumnVisibility(javafx.scene.control.TableColumn<java.io.File, ?> column) {
        column.setVisible(!column.isVisible());
        historyManager.recordAction("Toggled column visibility: " + column.getText());
    }

    @FXML public void togglePreviewPane() {
        previewPaneContainer.setVisible(!previewPaneContainer.isVisible());
        historyManager.recordAction("Toggled Preview Pane visibility");
    }
    @FXML public void sortByName() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getItems().sort((a,b) -> a.getName().compareToIgnoreCase(b.getName()));
        historyManager.recordAction("Sorted by Name in current pane");
    }

    @FXML public void sortBySize() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getItems().sort((a,b) -> Long.compare(a.length(), b.length()));
        historyManager.recordAction("Sorted by Size in current pane");
    }

    @FXML public void sortByDate() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getItems().sort((a,b) -> Long.compare(a.lastModified(), b.lastModified()));
        historyManager.recordAction("Sorted by Date in current pane");
    }

    @FXML public void groupByType() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) {
            pane.getItems().sort((a,b) -> getFileExtension(a).compareToIgnoreCase(getFileExtension(b)));
            historyManager.recordAction("Grouped by Type in current pane");
        }
    }

    private String getFileExtension(java.io.File f) {
        String name = f.getName();
        int idx = name.lastIndexOf(.);
        return idx > 0 ? name.substring(idx+1) : "";
    }
    @FXML public void showPropertiesMulti() {
        java.util.List<java.io.File> selectedFiles = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selectedFiles.isEmpty()) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (java.io.File f : selectedFiles) sb.append(f.getName()).append("\n");
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Properties");
            alert.setHeaderText("Selected Files");
            alert.setContentText(sb.toString());
            alert.initOwner(tabPane.getScene().getWindow());
            alert.showAndWait();
            historyManager.recordAction("Viewed Properties for multiple files: " + selectedFiles.size());
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML public void selectAllFiles() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getSelectionModel().selectAll();
        historyManager.recordAction("Selected All files in current pane");
    }

    @FXML public void deselectAllFiles() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getSelectionModel().clearSelection();
        historyManager.recordAction("Deselected All files in current pane");
    }

    @FXML public void invertSelection() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) {
            for (int i = 0; i < pane.getItems().size(); i++) {
                if (pane.getSelectionModel().isSelected(i)) pane.getSelectionModel().clearSelection(i);
                else pane.getSelectionModel().select(i);
            }
            historyManager.recordAction("Inverted selection in current pane");
        }
    }

    private javafx.scene.control.ListView<java.io.File> getCurrentPane() {
        return leftPaneFlow.isFocused() ? leftPaneFlow : rightPaneFlow;
    }
    @FXML public void createNewFolder() {
        java.io.File parent = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("New Folder");
        dialog.setTitle("Create New Folder");
        dialog.setHeaderText("Enter folder name:");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            java.io.File newFolder = new java.io.File(parent, name);
            if (!newFolder.exists() && newFolder.mkdir()) {
                refreshCurrentPane();
                historyManager.recordAction("Created Folder: " + newFolder.getAbsolutePath());
            }
        });
    }

    @FXML public void renameFile() {
        java.io.File selected = getSelectedFile();
        if (selected == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(selected.getName());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Enter new name:");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(newName -> {
            java.io.File renamed = new java.io.File(selected.getParentFile(), newName);
            if (selected.renameTo(renamed)) {
                refreshCurrentPane();
                historyManager.recordAction("Renamed: " + selected.getAbsolutePath() + " -> " + renamed.getAbsolutePath());
            }
        });
    }
    private FolderWatcher leftWatcher;
    private FolderWatcher rightWatcher;

    @FXML public void refreshCurrentPane() {
        if (leftPaneFlow.isFocused()) reloadFolder(leftFolder);
        else reloadFolder(rightFolder);
        historyManager.recordAction("Refreshed current pane");
    }

    private void reloadFolder(java.io.File folder) {
        if (!folder.exists()) return;
        java.io.File[] files = folder.listFiles();
        if (leftPaneFlow.isFocused()) leftPaneFlow.getItems().setAll(files);
        else rightPaneFlow.getItems().setAll(files);
    }

    private void initFolderWatchers() {
        try {
            leftWatcher = new FolderWatcher(leftFolder, this::refreshCurrentPane);
            rightWatcher = new FolderWatcher(rightFolder, this::refreshCurrentPane);
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML public void showProperties() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/fileexplorer/ui/PropertiesDialog.fxml"));
                javafx.scene.Parent root = loader.load();
                PropertiesDialogController ctrl = loader.getController();
                ctrl.setFile(selected);
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(root));
                stage.setTitle("Properties - " + selected.getName());
                stage.initOwner(tabPane.getScene().getWindow());
                stage.showAndWait();
                historyManager.recordAction("Viewed Properties: " + selected.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @FXML public void handleF12Preview() { switchToPreview(); }
    @FXML private javafx.scene.layout.VBox previewPaneContainer;

    private void updatePreviewPane(java.util.List<java.io.File> files) {
        previewPaneContainer.getChildren().clear();
        for (java.io.File f : files) {
            PreviewCell cell = new PreviewCell();
            cell.updateItem(f, false);
            previewPaneContainer.getChildren().add(cell);
        }
    }
    @FXML public void copyFile() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            java.io.File dest = chooseDestination(selected);
            if (dest != null) {
                try { java.nio.file.Files.copy(selected.toPath(), dest.toPath());
                historyManager.recordAction("Copied: " + selected.getAbsolutePath() + " -> " + dest.getAbsolutePath()); }
                catch (Exception e) { System.err.println("Copy failed: " + e); }
            }
        }
    }

    @FXML public void moveFile() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            java.io.File dest = chooseDestination(selected);
            if (dest != null) {
                try { java.nio.file.Files.move(selected.toPath(), dest.toPath());
                historyManager.recordAction("Moved: " + selected.getAbsolutePath() + " -> " + dest.getAbsolutePath()); }
                catch (Exception e) { System.err.println("Move failed: " + e); }
            }
        }
    }

    @FXML public void deleteFile() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().moveToTrash(selected);
                historyManager.recordAction("Deleted: " + selected.getAbsolutePath());
            } catch (Exception e) { System.err.println("Delete failed: " + e); }
        }
    }

    private java.io.File chooseDestination(java.io.File source) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Destination for " + source.getName());
        return chooser.showDialog(tabPane.getScene().getWindow());
    }
    @FXML public void ribbonOpenWith() {
        java.io.File selectedFile = getSelectedFile();
        if (selectedFile != null) {
            javafx.scene.control.ContextMenu tempMenu = new javafx.scene.control.ContextMenu();
            ContextMenuHandler.addOpenWithOption(tempMenu, selectedFile);
            tempMenu.show(tabPane, javafx.geometry.Side.BOTTOM, 0, 0);
        }
    }

    private java.io.File getSelectedFile() {
        if (leftPaneFlow.isFocused() && !leftPaneFlow.getSelectionModel().isEmpty()) return leftPaneFlow.getSelectionModel().getSelectedItem();
        if (rightPaneFlow.isFocused() && !rightPaneFlow.getSelectionModel().isEmpty()) return rightPaneFlow.getSelectionModel().getSelectedItem();
        return null;
    }
    @FXML private javafx.scene.control.ToggleGroup viewToggleGroup;
    @FXML private javafx.scene.control.RadioButton tilesViewButton;
    @FXML private javafx.scene.control.RadioButton detailsViewButton;
    @FXML private javafx.scene.control.RadioButton previewViewButton;

    @FXML public void switchToTiles() {
        updateCurrentPaneView(ViewMode.TILES);
    }

    @FXML public void switchToDetails() {
        updateCurrentPaneView(ViewMode.DETAILS);
    }

    @FXML public void switchToPreview() {
        updateCurrentPaneView(ViewMode.PREVIEW);
    }

    private enum ViewMode { TILES, DETAILS, PREVIEW }

    private void updateCurrentPaneView(ViewMode mode) {
        // If PREVIEW mode, update preview pane content
        if (mode == ViewMode.PREVIEW) updatePreviewPane(getCurrentPaneItems());
        if (leftPaneFlow.isFocused()) setPaneView(leftPaneFlow, mode);
        else setPaneView(rightPaneFlow, mode);
    }

    private void setPaneView(javafx.scene.control.ListView<java.io.File> pane, ViewMode mode) {
        switch(mode) {
            case TILES: pane.setCellFactory(f -> new FlowTileCell()); break;
            case DETAILS: pane.setCellFactory(f -> new DetailsCell()); break;
            case PREVIEW: pane.setCellFactory(f -> new PreviewCell()); break;
        }
    }
    @FXML public void clearSearch() {
        searchField.clear();
        filterFiles();
    }
    @FXML private javafx.scene.control.TextField searchField;

    @FXML public void filterFiles() {
        String query = searchField.getText().toLowerCase();
        if (query.isEmpty()) {
            refreshCurrentPane();
            return;
        }
        java.util.List<java.io.File> allFiles = getCurrentPaneItems();
        java.util.List<java.io.File> filtered = new java.util.ArrayList<>();
        for (java.io.File f : allFiles) {
            if (f.getName().toLowerCase().contains(query)) filtered.add(f);
        }
        updateCurrentPane(filtered);
        historyManager.recordAction("Filtered: " + query);
    }

    private java.util.List<java.io.File> getCurrentPaneItems() {
        if (leftPaneFlow.isFocused()) return new java.util.ArrayList<>(leftPaneFlow.getItems());
        else return new java.util.ArrayList<>(rightPaneFlow.getItems());
    }

    private void updateCurrentPane(java.util.List<java.io.File> items) {
        if (leftPaneFlow.isFocused()) { leftPaneFlow.getItems().setAll(items); }
        else { rightPaneFlow.getItems().setAll(items); }
    }
    @FXML public void sortByName() {
        if (detailsTableView != null) detailsTableView.getItems().sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    }

    @FXML public void sortByDate() {
        if (detailsTableView != null) detailsTableView.getItems().sort((a, b) -> java.util.Comparator.nullsLast(java.util.Comparator.comparingLong(f -> f.lastModified())).compare(a, b));
    }

    @FXML public void sortBySize() {
        if (detailsTableView != null) detailsTableView.getItems().sort((a, b) -> Long.compare(a.length(), b.length()));
    }

    @FXML public void groupByType() {
        if (detailsTableView != null) {
            java.util.Map<String, java.util.List<java.io.File>> groups = new java.util.TreeMap<>();
            for (java.io.File f : detailsTableView.getItems()) {
                String ext = f.isDirectory() ? "[Folder]" : getExtension(f.getName());
                groups.computeIfAbsent(ext, k -> new java.util.ArrayList<>()).add(f);
            }
            detailsTableView.getItems().clear();
            for (java.util.List<java.io.File> list : groups.values()) { detailsTableView.getItems().addAll(list); }
        }
    }

    private String getExtension(String name) {
        int idx = name.lastIndexOf(.); return (idx >= 0) ? name.substring(idx + 1).toLowerCase() : "[No Ext]";
    }
    private void enableTabDragAndDrop() {
        tabPane.setOnDragDetected(event -> {
            javafx.scene.control.Tab tab = tabPane.getSelectionModel().getSelectedItem();
            if (tab == null) return;
            javafx.scene.input.Dragboard db = tabPane.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(tab.getText());
            db.setContent(content);
            event.consume();
        });

        tabPane.setOnDragOver(event -> {
            if (event.getGestureSource() != tabPane) event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            event.consume();
        });

        tabPane.setOnDragDropped(event -> {
            javafx.scene.input.Dragboard db = event.getDragboard();
            if (db.hasString()) {
                String tabText = db.getString();
                javafx.scene.control.Tab draggedTab = null;
                for (javafx.scene.control.Tab t : tabPane.getTabs()) if (t.getText().equals(tabText)) draggedTab = t;
                if (draggedTab != null) {
                    int dropIndex = 0;
                    for (int i = 0; i < tabPane.getTabs().size(); i++) {
                        if (tabPane.getTabs().get(i).getLayoutX() > event.getX()) { dropIndex = i; break; }
                        dropIndex = i + 1;
                    }
                    tabPane.getTabs().remove(draggedTab);
                    tabPane.getTabs().add(dropIndex, draggedTab);
                    event.setDropCompleted(true);
                }
            }
            event.consume();
        });

        tabPane.setOnDragDone(event -> event.consume());
    }
    @FXML private javafx.scene.control.TabPane tabPane;

    private void setupTabs() {
        tabPane.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.SELECTED_TAB);
        tabPane.getTabs().add(createNewTab(new java.io.File(System.getProperty("user.home"))));
    }

    private javafx.scene.control.Tab createNewTab(java.io.File dir) {
        javafx.scene.control.Tab tab = new javafx.scene.control.Tab(dir.getName());
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox();
        // Add left/right panes to content or a nested layout
        tab.setContent(content);
        tab.setContextMenu(createTabContextMenu(tab, dir));
        return tab;
    }

    private javafx.scene.control.ContextMenu createTabContextMenu(javafx.scene.control.Tab tab, java.io.File dir) {
        javafx.scene.control.MenuItem closeItem = new javafx.scene.control.MenuItem("Close");
        closeItem.setOnAction(e -> tabPane.getTabs().remove(tab));
        javafx.scene.control.MenuItem newTabItem = new javafx.scene.control.MenuItem("New Tab");
        newTabItem.setOnAction(e -> tabPane.getTabs().add(createNewTab(new java.io.File(System.getProperty("user.home")))));
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu(closeItem, newTabItem);
        return menu;
    }
    private void setupDragAndDrop() {
        DragAndDropHandler.enableDragAndDrop(leftPaneFlow, rightPaneFlow, this);
        DragAndDropHandler.enableDragAndDrop(rightPaneFlow, leftPaneFlow, this);
    }

    @FXML private void initialize() {
        enableTabDragAndDrop();
        setupDragAndDrop();
        setupStatusBarListeners();
    }
    @FXML public void copySelectedFiles() {
        java.util.List<File> selectedFiles = getSelectedFiles();
        if (selectedFiles.isEmpty()) return;
        File targetDir = getCurrentDirectory() == leftPaneFlow.getFocusOwner() ? getOtherPaneDirectory(rightPaneFlow) : getOtherPaneDirectory(leftPaneFlow);
        showProgressDialog(selectedFiles, targetDir, false);
    }

    @FXML public void moveSelectedFiles() {
        java.util.List<File> selectedFiles = getSelectedFiles();
        if (selectedFiles.isEmpty()) return;
        File targetDir = getCurrentDirectory() == leftPaneFlow.getFocusOwner() ? getOtherPaneDirectory(rightPaneFlow) : getOtherPaneDirectory(leftPaneFlow);
        showProgressDialog(selectedFiles, targetDir, true);
    }

    private File getOtherPaneDirectory(javafx.scene.control.ListView<File> pane) {
        if (!pane.getItems().isEmpty()) {
            File f = pane.getItems().get(0);
            return f.isDirectory() ? f : f.getParentFile();
        }
        return new File(System.getProperty("user.home"));
    }

    private void showProgressDialog(java.util.List<File> files, File targetDir, boolean move) {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = files.size();
                for (int i = 0; i < total; i++) {
                    File src = files.get(i);
                    File dest = new File(targetDir, src.getName());
                    if (move) {
                        src.renameTo(dest);
                        historyManager.recordAction("Moved: " + src.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                    } else {
                        java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        historyManager.recordAction("Copied: " + src.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                    }
                    updateProgress(i+1, total);
                }
                return null;
            }
        };
        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        javafx.scene.control.Alert dlg = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.NONE);
        dlg.setHeaderText((move ? "Moving" : "Copying") + " files...");
        dlg.getDialogPane().setContent(progressBar);
        dlg.show();
        new Thread(task).start();
        task.setOnSucceeded(e -> { dlg.close(); refreshCurrentPane(); });
        task.setOnFailed(e -> dlg.close());
    }
    private FavoritesManager favoritesManager = new FavoritesManager();
    @FXML private javafx.scene.layout.VBox favoritesPane;

    @FXML public void pinCurrentFolder() {
        File current = getCurrentDirectory();
        if (current != null) {
            favoritesManager.addFavorite(current);
            refreshFavoritesPane();
        }
    }

    @FXML public void unpinFolder(File folder) {
        favoritesManager.removeFavorite(folder);
        refreshFavoritesPane();
    }

    private void refreshFavoritesPane() {
        favoritesPane.getChildren().clear();
        for (File f : favoritesManager.getFavorites()) {
            javafx.scene.control.HBox row = new javafx.scene.control.HBox(5);
            javafx.scene.control.Label lbl = new javafx.scene.control.Label(f.getName());
            javafx.scene.control.Button btnUnpin = new javafx.scene.control.Button("X");
            btnUnpin.setOnAction(e -> unpinFolder(f));
            row.getChildren().addAll(lbl, btnUnpin);
            row.setOnMouseClicked(e -> navigateTo(f));
            favoritesPane.getChildren().add(row);
        }
    }
    @FXML private javafx.scene.control.Label lblPath;
    @FXML private javafx.scene.control.Label lblSelectionCount;
    @FXML private javafx.scene.control.Label lblTotalSize;
    @FXML private javafx.scene.control.Label lblDiskInfo;

    private void updateStatusBar() {
        File selectedDir = getCurrentDirectory();
        lblPath.setText(selectedDir != null ? selectedDir.getAbsolutePath() : "");
        java.util.List<File> selectedFiles = getSelectedFiles();
        lblSelectionCount.setText(selectedFiles.size() + " item(s) selected");
        long totalBytes = selectedFiles.stream().mapToLong(f -> f.length()).sum();
        lblTotalSize.setText(totalBytes / 1024 + " KB");
        if (selectedDir != null) {
            javax.swing.filechooser.FileSystemView fsv = javax.swing.filechooser.FileSystemView.getFileSystemView();
            long usable = selectedDir.getUsableSpace() / 1024 / 1024;
            long total = selectedDir.getTotalSpace() / 1024 / 1024;
            lblDiskInfo.setText("Disk: " + usable + "MB free / " + total + "MB total");
        }
    }

    private File getCurrentDirectory() {
        // Return directory of first pane with focus
        if (!leftPaneFlow.getSelectionModel().isEmpty()) {
            File sel = leftPaneFlow.getSelectionModel().getSelectedItem();
            return sel.isDirectory() ? sel : sel.getParentFile();
        } else if (!rightPaneFlow.getSelectionModel().isEmpty()) {
            File sel = rightPaneFlow.getSelectionModel().getSelectedItem();
            return sel.isDirectory() ? sel : sel.getParentFile();
        }
        return new File(System.getProperty("user.home"));
    }

    private void setupStatusBarListeners() {
        leftPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
        rightPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
    }
    @FXML public void viewLargeIcons() { FlowTileCell.setIconSize(FlowTileCell.IconSize.LARGE); leftPaneFlow.refresh(); rightPaneFlow.refresh(); }
    @FXML public void viewMediumIcons() { FlowTileCell.setIconSize(FlowTileCell.IconSize.MEDIUM); leftPaneFlow.refresh(); rightPaneFlow.refresh(); }
    @FXML public void viewSmallIcons() { FlowTileCell.setIconSize(FlowTileCell.IconSize.SMALL); leftPaneFlow.refresh(); rightPaneFlow.refresh(); }
    @FXML public void viewList() {
        // List view: smaller tiles, single column text
        FlowTileCell.setIconSize(FlowTileCell.IconSize.SMALL);
        leftPaneFlow.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rightPaneFlow.setOrientation(javafx.geometry.Orientation.VERTICAL);
        leftPaneFlow.refresh();
        rightPaneFlow.refresh();
    }
    @FXML public void viewDetails() {
        showDetailsView();
        updateRibbonForView("Details");
    }
    @FXML public void newFolder() {
        File parent = getSelectedFile();
        if (parent == null || !parent.isDirectory()) {
            parent = new File(System.getProperty("user.home"));
        }
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog("New Folder");
        dlg.setHeaderText("Create New Folder");
        dlg.showAndWait().ifPresent(name -> {
            File newDir = new File(parent, name);
            if (!newDir.exists() && newDir.mkdir()) {
                historyManager.recordAction("Created Folder: " + newDir.getName());
                refreshCurrentPane();
            }
        });
    }

    @FXML public void newFile() {
        File parent = getSelectedFile();
        if (parent == null || !parent.isDirectory()) {
            parent = new File(System.getProperty("user.home"));
        }
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog("NewFile.txt");
        dlg.setHeaderText("Create New File");
        dlg.showAndWait().ifPresent(name -> {
            try {
                File newFile = new File(parent, name);
                if (!newFile.exists() && newFile.createNewFile()) {
                    historyManager.recordAction("Created File: " + newFile.getName());
                    refreshCurrentPane();
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @FXML public void newFromTemplate() {
        // Simple template example: copy from predefined template directory
        File templateDir = new File(System.getProperty("user.home"), ".fileexplorer_templates");
        if (!templateDir.exists()) templateDir.mkdirs();
        java.io.File[] templates = templateDir.listFiles(f -> f.isFile());
        if (templates == null || templates.length == 0) return;
        javafx.scene.control.ChoiceDialog<File> dlg = new javafx.scene.control.ChoiceDialog<>(templates[0], templates);
        dlg.setHeaderText("Select a Template");
        dlg.showAndWait().ifPresent(template -> {
            File parent = getSelectedFile();
            if (parent == null || !parent.isDirectory()) {
                parent = new File(System.getProperty("user.home"));
            }
            try {
                File newFile = new File(parent, template.getName());
                java.nio.file.Files.copy(template.toPath(), newFile.toPath());
                historyManager.recordAction("Created from template: " + newFile.getName());
                refreshCurrentPane();
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
    @FXML private javafx.scene.control.ComboBox<String> cbSortBy;
    @FXML private javafx.scene.control.ComboBox<String> cbGroupBy;

    private void setupSortGroupControls() {
        cbSortBy.getItems().setAll("Name", "Size", "Type", "Date");
        cbGroupBy.getItems().setAll("None", "Date", "Type");

        cbSortBy.setOnAction(e -> {
            String col = cbSortBy.getValue();
            if (currentView.equals("Details")) detailsViewController.sortByColumn(col);
            else if (currentView.equals("Tiles")) tilesFlowController.sortByColumn(col);
        });

        cbGroupBy.setOnAction(e -> {
            String col = cbGroupBy.getValue();
            if (currentView.equals("Details")) detailsViewController.groupByColumn(col);
        });
    }

    private void updateRibbonForView(String view) {
        currentView = view;
        switch(view) {
            case "Details": cbSortBy.setDisable(false); cbGroupBy.setDisable(false); break;
            case "Tiles": cbSortBy.setDisable(false); cbGroupBy.setDisable(true); break;
            case "Preview": cbSortBy.setDisable(true); cbGroupBy.setDisable(true); break;
        }
    }

    // Example: call updateRibbonForView("Details") after view switch
    @FXML private javafx.scene.layout.StackPane contentPane;
    @FXML private javafx.fxml.FXMLLoader detailsLoader;
    @FXML private javafx.fxml.FXMLLoader tilesLoader;
    @FXML private javafx.fxml.FXMLLoader previewLoader;

    private void showDetailsView() {
        try {
            if (detailsLoader == null) detailsLoader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/fileexplorer/ui/DetailsView.fxml"));
            javafx.scene.Parent detailsRoot = detailsLoader.load();
            contentPane.getChildren().setAll(detailsRoot);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showTilesView() {
        try {
            if (tilesLoader == null) tilesLoader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/fileexplorer/ui/TilesView.fxml"));
            javafx.scene.Parent tilesRoot = tilesLoader.load();
            contentPane.getChildren().setAll(tilesRoot);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showPreviewView() {
        try {
            if (previewLoader == null) previewLoader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/fileexplorer/ui/PreviewPane.fxml"));
            javafx.scene.Parent previewRoot = previewLoader.load();
            contentPane.getChildren().setAll(previewRoot);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML public void viewTiles() { showTilesView(); }
    @FXML public void viewDetails() { showDetailsView(); }
    @FXML public void viewPreview() { showPreviewView(); }
    private java.util.List<File> getSelectedFiles() {
        java.util.List<File> files = new java.util.ArrayList<>();
        files.addAll(leftPaneFlow.getSelectionModel().getSelectedItems());
        files.addAll(rightPaneFlow.getSelectionModel().getSelectedItems());
        return files;
    }

    @FXML public void deleteSelectedBatch() {
        for (File f : getSelectedFiles()) {
            deleteFile(f);
        }
    }

    @FXML public void copySelectedBatch() {
        for (File f : getSelectedFiles()) {
            copyFile(f);
        }
    }

    @FXML public void renameSelectedBatch() {
        for (File f : getSelectedFiles()) {
            renameFile(f);
        }
    }

    @FXML public void propertiesSelectedBatch() {
        for (File f : getSelectedFiles()) {
            showProperties(f);
        }
    }
    @FXML private javafx.scene.control.Button btnDelete;
    @FXML private javafx.scene.control.Button btnRename;
    @FXML private javafx.scene.control.Button btnCopy;
    @FXML private javafx.scene.control.Button btnProperties;

    private void updateRibbonButtons() {
        File selected = getSelectedFile();
        boolean hasSelection = selected != null;
        btnDelete.setDisable(!hasSelection);
        btnRename.setDisable(!hasSelection);
        btnCopy.setDisable(!hasSelection);
        btnProperties.setDisable(!hasSelection);

        if (hasSelection && selected.isDirectory()) {
            // Example: disable certain actions for directories if needed
        }
    }

    private void setupSelectionListeners() {
        leftPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateRibbonButtons());
        rightPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateRibbonButtons());
    }
    @FXML public void deleteSelected() {
        File selected = getSelectedFile();
        if (selected != null) deleteFile(selected);
    }

    @FXML public void renameSelected() {
        File selected = getSelectedFile();
        if (selected != null) renameFile(selected);
    }

    @FXML public void copySelected() {
        File selected = getSelectedFile();
        if (selected != null) copyFile(selected);
    }

    @FXML public void propertiesSelected() {
        File selected = getSelectedFile();
        if (selected != null) showProperties(selected);
    }

    private File getSelectedFile() {
        File f = null;
        if (!leftPaneFlow.getSelectionModel().isEmpty()) f = leftPaneFlow.getSelectionModel().getSelectedItem();
        else if (!rightPaneFlow.getSelectionModel().isEmpty()) f = rightPaneFlow.getSelectionModel().getSelectedItem();
        return f;
    }

    public void deleteFile(File file) {
        try {
            java.awt.Desktop.getDesktop().moveToTrash(file);
            historyManager.recordAction("Deleted: " + file.getName());
            refreshCurrentPane();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void renameFile(File file) {
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog(file.getName());
        dlg.setHeaderText("Rename File");
        dlg.showAndWait().ifPresent(newName -> {
            File renamed = new File(file.getParentFile(), newName);
            if (file.renameTo(renamed)) {
                historyManager.recordAction("Renamed: " + file.getName() + " → " + newName);
                refreshCurrentPane();
            }
        });
    }

    public void copyFile(File file) {
        try {
            java.nio.file.Path dest = java.nio.file.Paths.get(file.getParent(), "Copy_of_" + file.getName());
            java.nio.file.Files.copy(file.toPath(), dest);
            historyManager.recordAction("Copied: " + file.getName());
            refreshCurrentPane();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void showProperties(File file) {
        PropertiesDialogController dlgCtrl = new PropertiesDialogController();
        dlgCtrl.showProperties(file);
    }

    private void refreshCurrentPane() {
        // Simple refresh: reload FlowTileCells
        leftPaneFlow.refresh();
        rightPaneFlow.refresh();
    }
    // Tab persistence storage (simple example using user home)
    private static final String TAB_STATE_FILE = System.getProperty("user.home") + "/.fileexplorer_tabs.dat";

    public void saveTabsState() {
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(new java.io.FileOutputStream(TAB_STATE_FILE))) {
            java.util.List<String> paths = new java.util.ArrayList<>();
            for (javafx.scene.control.Tab tab : leftTabPane.getTabs()) {
                paths.add(tab.getText());
            }
            out.writeObject(paths);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void restoreTabsState() {
        java.io.File f = new java.io.File(TAB_STATE_FILE);
        if (!f.exists()) return;
        try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(new java.io.FileInputStream(f))) {
            java.util.List<String> paths = (java.util.List<String>) in.readObject();
            for (String path : paths) {
                addTabToLeft(path);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private PreviewPaneController previewPaneController;
    @FXML private FlowTileCell leftPaneFlow;
    @FXML private FlowTileCell rightPaneFlow;

    private void setupDualPaneSelection() {
        leftPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> {
            previewPaneController.showFile(newFile);
            rightPaneFlow.getSelectionModel().clearSelection();
        });

        rightPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> {
            previewPaneController.showFile(newFile);
            leftPaneFlow.getSelectionModel().clearSelection();
        });
    }
    @FXML public void groupByNone() {
        if (detailsViewController != null) detailsViewController.groupByColumn("None");
    }
    @FXML public void groupByDate() {
        if (detailsViewController != null) detailsViewController.groupByColumn("Date");
    }
    @FXML public void groupByType() {
        if (detailsViewController != null) detailsViewController.groupByColumn("Type");
    }
    @FXML private DetailsViewController detailsViewController;

    @FXML public void sortByName() {
        if (detailsViewController != null) detailsViewController.sortByColumn("Name");
    }
    @FXML public void sortBySize() {
        if (detailsViewController != null) detailsViewController.sortByColumn("Size");
    }
    @FXML public void sortByType() {
        if (detailsViewController != null) detailsViewController.sortByColumn("Type");
    }
    @FXML public void sortByDate() {
        if (detailsViewController != null) detailsViewController.sortByColumn("Date");
    }
    private String currentTheme = "Light";

    public void setTheme(String themeName) {
        javafx.scene.Scene scene = lblCurrentPath.getScene();
        if (scene == null) return;
        scene.getStylesheets().clear();
        switch(themeName) {
            case "Light": scene.getStylesheets().add(getClass().getResource("Light.css").toExternalForm()); break;
            case "Dark": scene.getStylesheets().add(getClass().getResource("Dark.css").toExternalForm()); break;
            case "Glassy": scene.getStylesheets().add(getClass().getResource("Glassy.css").toExternalForm()); break;
        }
        currentTheme = themeName;
        System.out.println("[Theme] Switched to: " + currentTheme);
    }

    public String getCurrentTheme() { return currentTheme; }
    @FXML private javafx.scene.layout.HBox statusBar;
    @FXML private javafx.scene.control.Label lblCurrentPath;
    @FXML private javafx.scene.control.Label lblSelectionCount;
    @FXML private javafx.scene.control.Label lblTotalSize;
    @FXML private javafx.scene.control.ProgressBar operationProgress;

    public void updateStatusBar(String path, int selectedCount, long totalSizeBytes, double progress) {
        lblCurrentPath.setText("Path: " + path);
        lblSelectionCount.setText("Selected: " + selectedCount);
        lblTotalSize.setText("Size: " + (totalSizeBytes/1024) + " KB");
        operationProgress.setProgress(progress);
    }
    @FXML private javafx.scene.layout.VBox navigationPane;
    @FXML private NavigationTreeController navTree;

    @FXML public void initialize() {
        navTree.setMainController(this);
        // Existing initialization (TabManager, DragDrop) continues
    }
    @FXML public void onRibbonUndo() {
        if (historyManager != null) historyManager.undo();
    }
    @FXML public void onRibbonRedo() {
        if (historyManager != null) historyManager.redo();
    }
    @FXML public void onRibbonUndo() {
        if (historyManager != null) historyManager.undo();
    }
    @FXML public void onRibbonRedo() {
        if (historyManager != null) historyManager.redo();
    }
    // --- Drag-and-Drop + FlowTileCell integration ---
    private DragAndDropHandler dragHandler;
    @FXML public void initialize() {
        // Register with Ribbon
        MainControllerAccessor.set(this);
        // Initialize TabManager already done in previous chunk
        // Initialize DragAndDropHandler for both panes
        dragHandler = new DragAndDropHandler(leftTabPane, rightTabPane, previewPane, historyManager);
        dragHandler.enableDragAndDrop();
        // Hook FlowTileCell selection to update preview
        leftTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updatePreviewPane());
        rightTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updatePreviewPane());
    }
    // --- Dual-Pane Mode / Tabs / Preview Pane ---
    @FXML private javafx.scene.control.TabPane leftTabPane;
    @FXML private javafx.scene.control.TabPane rightTabPane;
    @FXML private javafx.scene.layout.VBox previewPane;
    private TabManager tabManager;

    @FXML public void initialize() {
        // Register with Ribbon accessor
        MainControllerAccessor.set(this);
        tabManager = new TabManager(leftTabPane, rightTabPane);
        // Add initial tabs
        tabManager.addTab(true, "Home");
        tabManager.addTab(false, "Home");
        // Optional: hook selection listeners to update preview pane
        leftTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updatePreviewPane());
        rightTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updatePreviewPane());
    }

    public void addTabToLeft(String title) { tabManager.addTab(true, title); }
    public void addTabToRight(String title) { tabManager.addTab(false, title); }

    private void updatePreviewPane() {
        previewPane.getChildren().clear();
        Tab leftTab = leftTabPane.getSelectionModel().getSelectedItem();
        Tab rightTab = rightTabPane.getSelectionModel().getSelectedItem();
        // For demo: show selected tab names in preview
        javafx.scene.control.Label lbl = new javafx.scene.control.Label(
            "Left: " + (leftTab != null ? leftTab.getText() : "None") +
            " | Right: " + (rightTab != null ? rightTab.getText() : "None")
        );
        previewPane.getChildren().add(lbl);
    }
    // --- Ribbon demo wiring ---
    public void onNewFolder() {
        System.out.println("[Ribbon Demo] Creating new folder...");
        // call existing folder creation service here
        if (historyManager != null) historyManager.recordAction("New Folder");
    }
    public void onRibbonDeleteWithProgress() {
        System.out.println("[Ribbon Demo] Deleting selected items...");
        if (historyManager != null) historyManager.recordAction("Delete");
        // existing delete service can be called here
    }
    public void onRibbonCopyWithChooser() {
        System.out.println("[Ribbon Demo] Copying selected items...");
        if (historyManager != null) historyManager.recordAction("Copy");
        // call copy service or dialog
    }
    public void onRibbonMoveWithChooser() {
        System.out.println("[Ribbon Demo] Moving selected items...");
        if (historyManager != null) historyManager.recordAction("Move");
        // call move service or dialog
    }
    public void onRibbonCancelOperation() {
        System.out.println("[Ribbon Demo] Cancel operation triggered");
        // cancel pending tasks if needed
    }
    public void onHomeProperties() {
        System.out.println("[Ribbon Demo] Showing properties...");
        // show PropertiesDialog
    }
    public void toggleNavigationPane(boolean show) {
        System.out.println("[Ribbon Demo] toggleNavigationPane="+show);
        // show/hide Navigation Pane
    }
    public void togglePreviewPane(boolean show) {
        System.out.println("[Ribbon Demo] togglePreviewPane="+show);
        // show/hide Preview Pane
    }
    public void toggleDetailsPane(boolean show) {
        System.out.println("[Ribbon Demo] toggleDetailsPane="+show);
        // show/hide Details Pane
    }
    public void setLayout(String layout) {
        System.out.println("[Ribbon Demo] setLayout="+layout);
        // update ListView/FlowTileCell layout accordingly
    }
    public void sortBy(String criteria) {
        System.out.println("[Ribbon Demo] sortBy="+criteria);
        // sort thumbnails/files accordingly
    }
    public void groupBy(String criteria) {
        System.out.println("[Ribbon Demo] groupBy="+criteria);
        // implement groupBy logic if exists
    }
    public void toggleShowExtensions(boolean show) {
        System.out.println("[Ribbon Demo] toggleShowExtensions="+show);
        // update display of file extensions
    }
    public void toggleShowHidden(boolean show) {
        System.out.println("[Ribbon Demo] toggleShowHidden="+show);
        // update display of hidden files
    }
    // RibbonBar / Toolbar support methods
    public void toggleNavigationPane(boolean show) { System.out.println("toggleNavigationPane="+show); }
    public void togglePreviewPane(boolean show) { System.out.println("togglePreviewPane="+show); }
    public void toggleDetailsPane(boolean show) { System.out.println("toggleDetailsPane="+show); }
    public void setLayout(String layout) { System.out.println("Layout set to: "+layout); }
    public void sortBy(String criteria) { System.out.println("Sort by: "+criteria); }
    public void groupBy(String criteria) { System.out.println("Group by: "+criteria); }
    public void toggleShowExtensions(boolean show) { System.out.println("Show extensions: "+show); }
    public void toggleShowHidden(boolean show) { System.out.println("Show hidden: "+show); }
    public void onNewFolder() { System.out.println("New folder created"); }
    public void renameSelected() { System.out.println("Rename selected files"); }
    public void showPropertiesForSelected() { System.out.println("Show properties"); }
    public void compressSelected() { System.out.println("Compress selected files to ZIP"); }
    public void burnSelected() { System.out.println("Burn selected files to disc"); }
    public void createShortcut() { System.out.println("Create shortcut"); }
    public void shareSelected() { System.out.println("Share selected files"); }
    public void emailSelected() { System.out.println("Email selected files"); }
    public void zipSelected() { System.out.println("ZIP selected files"); }
    public void mapNetworkDrive() { System.out.println("Map network drive"); }
    public void openWithDefault() { System.out.println("Open with default"); }
    public void openWithChooser() { System.out.println("Open with chooser"); }
    public void onRibbonCopyWithChooser() { System.out.println("Copy with chooser"); }
    public void onRibbonMoveWithChooser() { System.out.println("Move with chooser"); }
    public void onRibbonDeleteWithProgress() { System.out.println("Delete with progress"); }
    public void onRibbonCancelOperation() { System.out.println("Cancel operation"); }

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

// package com.fileexplorer.controller;

// import com.fileexplorer.events.*;
// import com.fileexplorer.history.HistoryManager;
// import com.fileexplorer.navigation.NavigationBus;
// import com.fileexplorer.tabs.TabState;
// import com.fileexplorer.tabs.TabPersistence;
// import com.fileexplorer.views.DetailsViewController;
// import com.fileexplorer.views.FlowViewController;
// import com.fileexplorer.views.PreviewPaneController;

// import javafx.application.Platform;
// import javafx.concurrent.Task;
// import javafx.fxml.FXML;
// import javafx.fxml.FXMLLoader;
// import javafx.scene.Node;
// import javafx.scene.control.Tab;
// import javafx.scene.control.TabPane;
// import javafx.scene.layout.StackPane;

// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
// import java.util.concurrent.ThreadFactory;

// /**
 // * Main controller — now supports rewritten tab creation.
 // * createTab(Path) is preserved but deprecated; all real logic now
 // * resides in createTabWithLoader(Path).
 // */
// public class MainController {
    // Theme controls (injected by chunk136)
    @FXML private javafx.scene.control.ChoiceBox<String> baseThemeChoice;
    @FXML private javafx.scene.control.ChoiceBox<String> overlayChoice;

    @FXML public void initializeThemeControls() {
        try {
            baseThemeChoice.getItems().setAll("Light","Dark");
            baseThemeChoice.setValue(ThemeUtils.BaseTheme.LIGHT.name());
            overlayChoice.getItems().setAll("None","Glassy","Mica","Acrylic");
            overlayChoice.setValue("None");

            baseThemeChoice.getSelectionModel().selectedItemProperty().addListener((obs,oldV,newV) -> {
                ThemeUtils.BaseTheme t = "Dark".equalsIgnoreCase(newV) ? ThemeUtils.BaseTheme.DARK : ThemeUtils.BaseTheme.LIGHT;
                ThemeManager.get().setBaseTheme(t);
            });

            overlayChoice.getSelectionModel().selectedItemProperty().addListener((obs,oldV,newV) -> {
                // Apply overlay to current tab only (mixed model)
                javafx.scene.control.Tab selected = tabPane.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    ThemeUtils.Overlay ov = switch(newV) { case "Glassy" -> ThemeUtils.Overlay.GLASSY; case "Mica" -> ThemeUtils.Overlay.MICA; case "Acrylic" -> ThemeUtils.Overlay.ACRYLIC; default -> ThemeUtils.Overlay.NONE; };
                    ThemeManager.get().setTabOverlay(selected, ov);
                }
            });

            // When tab selection changes, update overlayChoice to reflect current tab
            tabPane.getSelectionModel().selectedItemProperty().addListener((obs,oldTab,newTab) -> {
                if (newTab == null) return;
                ThemeUtils.Overlay ov = ThemeManager.get().getTabOverlay(newTab);
                overlayChoice.setValue(ov == ThemeUtils.Overlay.NONE ? "None" : ov.name().substring(0,1)+ov.name().substring(1).toLowerCase());
            });

            // Register the scene with ThemeManager if available
            if (tabPane != null && tabPane.getScene() != null) ThemeManager.get().registerScene(tabPane.getScene());
        } catch(Exception e) { e.printStackTrace(); }
    }
    private void initializeKeyboardShortcuts() {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case DELETE -> deleteSelectedRibbon();
                case F2 -> renameSelectedRibbon();
                case F4 -> showPropertiesRibbon();
                case Z -> { if (event.isControlDown()) historyManager.undo(); }
                case Y -> { if (event.isControlDown()) historyManager.redo(); }
                default -> {}
            }
        });
    }
    private PropertiesDialogController propertiesDialogController;

    public void showPropertiesDialog(java.util.List<java.io.File> files) {
        if (propertiesDialogController != null) propertiesDialogController.showProperties(files);
    }

    // RibbonBar multi-selection actions
    public void deleteSelectedRibbon() {
        for (java.io.File f : getSelectedFiles()) deleteFile(f);
    }

    public void renameSelectedRibbon() {
        java.util.List<java.io.File> selected = getSelectedFiles();
        if (selected.size() == 1) renameSelected();
        else System.out.println("Batch rename not implemented yet");
    }

    public void showPropertiesRibbon() {
        showPropertiesDialog(getSelectedFiles());
    }
    private DragAndDropHandler dragAndDropHandler;

    private void initializeDragAndDrop() {
        dragAndDropHandler = new DragAndDropHandler();
        dragAndDropHandler.setController(this);
        dragAndDropHandler.attachDragAndDrop(leftPane());
        dragAndDropHandler.attachDragAndDrop(rightPane());
    }
    public void deleteFile(java.io.File file) {
        if (file != null && historyManager != null) {
            historyManager.recordDelete(file);
            file.delete();
            refreshPanes();
        }
    }

    public java.util.List<java.io.File> getSelectedFiles() {
        java.util.List<java.io.File> selected = new java.util.ArrayList<>();
        selected.addAll(leftPane().getSelectionModel().getSelectedItems());
        selected.addAll(rightPane().getSelectionModel().getSelectedItems());
        return selected;
    }

    private void attachMultiSelection() {
        leftPane().getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        rightPane().getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
    }
    @FXML private void undoAction() {
        if (historyManager != null) historyManager.undo();
        refreshPanes();
    }

    @FXML private void redoAction() {
        if (historyManager != null) historyManager.redo();
        refreshPanes();
    }

    private void refreshPanes() {
        leftPane().refresh();
        rightPane().refresh();
        updateStatusBar();
        updatePreview(getSelectedFile());
    }
    @FXML private TabPane tabPane;
    private TabManager tabManager;

    private void initializeTabs() {
        tabManager = new TabManager(tabPane);
        tabManager.restoreSession();
    }

    @FXML private void handleClose() {
        if (tabManager != null) tabManager.saveSession();
    }
    @FXML private StatusBarController statusBarController;

    private void attachStatusBarUpdates() {
        leftPane().getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> updateStatusBar());
        rightPane().getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> updateStatusBar());
    }

    private void updateStatusBar() {
        if (statusBarController != null) {
            statusBarController.updateSelection(getSelectedFiles());
            statusBarController.updateTabInfo();
        }
    }
    @FXML private RibbonBarController ribbonBarController;

    public void initializeRibbonBar() {
        if (ribbonBarController != null) ribbonBarController.initializeRibbon(this);
    }

    // Stub methods for menu actions
    public void copySelected() { /* implement copy logic with HistoryManager */ }
    public void pasteClipboard() { /* implement paste logic with HistoryManager */ }
    public void deleteSelected() { /* implement delete logic with HistoryManager */ }
    public void renameSelected() { /* implement rename logic with HistoryManager */ }
    public void showProperties() { /* show PropertiesDialog for selected file */ }
    public java.util.List<java.io.File> getSelectedFiles() { return new java.util.ArrayList<>(); }
    public boolean clipboardHasFiles() { return false; /* implement clipboard check */ }
    @FXML private PreviewPaneController previewPaneController;

    public void updatePreview(File file) {
        if (previewPaneController != null) previewPaneController.showPreview(file);
    }

    private void attachPreviewOnSelection() {
        leftPane().getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> updatePreview(newFile));
        rightPane().getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> updatePreview(newFile));
    }
    @FXML public void initializeDragAndDrop() {
        dragAndDropHandler.setController(this);
        dragAndDropHandler.setHistoryManager(historyManager);
        dragAndDropHandler.enableDragAndDrop(leftPane());
        dragAndDropHandler.enableDragAndDrop(rightPane());
    }
    private java.util.Map<javafx.scene.control.Tab, HistoryManager> tabHistoryMap = new java.util.HashMap<>();

    public void initializeTabHistory() {
        tabPane.getTabs().forEach(tab -> {
            HistoryManager hm = new HistoryManager();
            tabHistoryMap.put(tab, hm);
            ribbonBarController.initializeUndoRedoButtons(hm);
        });
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                HistoryManager hm = tabHistoryMap.get(newTab);
                ribbonBarController.initializeUndoRedoButtons(hm);
            }
        });
    }
    @FXML public void initializeUndoRedo() {
        ribbonBarController.initializeUndoRedoButtons(historyManager);
    }
    @FXML public void initializeStatusBar() {
        // Bind left pane selection
        statusBarController.bindSelection(leftPane().getSelectionModel().getSelectedItems(), getCurrentFolder());
        // Bind right pane selection
        statusBarController.bindSelection(rightPane().getSelectionModel().getSelectedItems(), getCurrentFolder());
    }

    @FXML public void updateCurrentFolderInStatusBar(File folder) {
        statusBarController.setCurrentFolder(folder);
    }
    @FXML public void initializeRibbonBar() {
        ribbonBarController.initializeMenus(this);
    }
    @FXML public void initializePreviewPaneIntegration() {
        leftPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends java.io.File> c) -> {
            previewPaneController.showFiles(leftPane().getSelectionModel().getSelectedItems());
        });
        rightPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends java.io.File> c) -> {
            previewPaneController.showFiles(rightPane().getSelectionModel().getSelectedItems());
        });
    }
    @FXML public void initializeNavigationTreePins() {
        navigationTreeController.initializePins();
    }
    @FXML public void initializeTabManagement() {
        tabPane.setTabDragPolicy(javafx.scene.control.TabPane.TabDragPolicy.REORDER);
        tabPane.getTabs().forEach(tab -> {
            tab.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(tab.getText());
                    dialog.setTitle("Rename Tab");
                    dialog.setHeaderText("Rename Tab");
                    dialog.setContentText("New name:");
                    java.util.Optional<String> result = dialog.showAndWait();
                    result.ifPresent(newName -> tab.setText(newName));
                }
            });
        });
    }

    @FXML public void saveTabsOnExit() {
        SessionManager.saveTabStates(tabPane.getTabs());
    }

    @FXML public void loadTabsOnStartup() {
        java.util.List<String> tabNames = SessionManager.loadTabStates();
        if (!tabNames.isEmpty()) {
            tabPane.getTabs().clear();
            for (String name : tabNames) {
                javafx.scene.control.Tab tab = new javafx.scene.control.Tab(name);
                tab.setContent(createEmptyPane());
                tabPane.getTabs().add(tab);
            }
        }
    }

    private javafx.scene.layout.Pane createEmptyPane() {
        return new javafx.scene.layout.StackPane();
    }
    @FXML public void handleTabDragDrop(javafx.scene.control.ListView<java.io.File> targetPane, java.util.List<java.io.File> files) {
        DragAndDropHandler.handleMultiFileDrop(targetPane, files, this);
    }
    @FXML public void copySelectedFilesWithProgress(java.io.File targetFolder) {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty() || targetFolder == null) return;

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {
            @Override protected Void call() throws Exception {
                int total = selected.size();
                int count = 0;
                for (java.io.File f : selected) {
                    if (isCancelled()) break;
                    java.nio.file.Files.copy(f.toPath(), java.nio.file.Paths.get(targetFolder.getAbsolutePath(), f.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    updateProgress(++count, total);
                    historyManager.recordAction("Copied: " + f.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
                }
                return null;
            }
        };

        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        javafx.scene.control.Button cancelButton = new javafx.scene.control.Button("Cancel");
        cancelButton.setOnAction(e -> task.cancel());

        javafx.scene.layout.VBox dialogBox = new javafx.scene.layout.VBox(progressBar, cancelButton);
        dialogBox.setSpacing(10);
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Copying Files");
        stage.setScene(new javafx.scene.Scene(dialogBox, 300, 100));
        stage.initOwner(navigationTree.getScene().getWindow());
        stage.show();

        new Thread(task).start();
        task.setOnSucceeded(e -> stage.close());
        task.setOnCancelled(e -> stage.close());
        task.setOnFailed(e -> stage.close());
    }

    @FXML public void moveSelectedFilesWithProgress(java.io.File targetFolder) {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty() || targetFolder == null) return;

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {
            @Override protected Void call() throws Exception {
                int total = selected.size();
                int count = 0;
                for (java.io.File f : selected) {
                    if (isCancelled()) break;
                    java.nio.file.Files.move(f.toPath(), java.nio.file.Paths.get(targetFolder.getAbsolutePath(), f.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    updateProgress(++count, total);
                    historyManager.recordAction("Moved: " + f.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
                }
                getCurrentPane().getItems().removeAll(selected);
                return null;
            }
        };

        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        javafx.scene.control.Button cancelButton = new javafx.scene.control.Button("Cancel");
        cancelButton.setOnAction(e -> task.cancel());

        javafx.scene.layout.VBox dialogBox = new javafx.scene.layout.VBox(progressBar, cancelButton);
        dialogBox.setSpacing(10);
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Moving Files");
        stage.setScene(new javafx.scene.Scene(dialogBox, 300, 100));
        stage.initOwner(navigationTree.getScene().getWindow());
        stage.show();

        new Thread(task).start();
        task.setOnSucceeded(e -> stage.close());
        task.setOnCancelled(e -> stage.close());
        task.setOnFailed(e -> stage.close());
    }
    @FXML public void initializeKeyboardShortcuts() {
        javafx.scene.Scene scene = navigationTree.getScene();
        if (scene == null) return;

        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.DELETE), () -> deleteSelectedFiles());
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F2), () -> renameSelectedFile());
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.C, javafx.scene.input.KeyCombination.CONTROL_DOWN), () -> copySelectedFiles(selectTargetFolder()));
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.V, javafx.scene.input.KeyCombination.CONTROL_DOWN), () -> moveSelectedFiles(selectTargetFolder()));
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ENTER), () -> showPropertiesDialog());
    }

    private java.io.File selectTargetFolder() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Target Folder");
        return chooser.showDialog(navigationTree.getScene().getWindow());
    }

    private void showPropertiesDialog() {
        java.io.File selected = getCurrentPane().getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("PropertiesDialog.fxml"));
            javafx.scene.Parent root = loader.load();
            PropertiesDialogController controller = loader.getController();
            controller.setFile(selected);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Properties: " + selected.getName());
            stage.setScene(new javafx.scene.Scene(root));
            stage.initOwner(navigationTree.getScene().getWindow());
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private javafx.scene.control.Button deleteButton;
    @FXML private javafx.scene.control.Button renameButton;
    @FXML private javafx.scene.control.Button copyButton;
    @FXML private javafx.scene.control.Button moveButton;
    @FXML private javafx.scene.control.Button propertiesButton;

    @FXML public void initializeRibbonContext() {
        leftPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends java.io.File> c) -> updateRibbonButtons());
        rightPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends java.io.File> c) -> updateRibbonButtons());
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateRibbonButtons());
        updateRibbonButtons();
    }

    private void updateRibbonButtons() {
        boolean leftHasSelection = !leftPane().getSelectionModel().getSelectedItems().isEmpty();
        boolean rightHasSelection = !rightPane().getSelectionModel().getSelectedItems().isEmpty();
        boolean anySelection = leftHasSelection || rightHasSelection;

        deleteButton.setDisable(!anySelection);
        renameButton.setDisable(!anySelection || leftPane().getSelectionModel().getSelectedItems().size() + rightPane().getSelectionModel().getSelectedItems().size() != 1);
        copyButton.setDisable(!anySelection);
        moveButton.setDisable(!anySelection);
        propertiesButton.setDisable(!anySelection || leftPane().getSelectionModel().getSelectedItems().size() + rightPane().getSelectionModel().getSelectedItems().size() != 1);

        // Optionally: update icon size / layout combos to reflect current tab state
        javafx.scene.control.Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
        if (currentTab != null && tabStates.containsKey(currentTab)) {
            PaneState state = tabStates.get(currentTab);
            iconSizeCombo.getSelectionModel().select(suggestIconSize(state.leftLayout));
            layoutCombo.getSelectionModel().select(state.leftLayout);
        }
    }

    private String suggestIconSize(String layout) {
        switch(layout) {
            case "Tiles": return "Medium";
            case "Thumbnails": return "Large";
            default: return "Medium";
        }
    }
    @FXML private javafx.scene.control.ListView<String> undoListView;
    @FXML private javafx.scene.control.Button undoButton;
    @FXML private javafx.scene.control.Button redoButton;

    @FXML public void initializeUndoRedoPanel() {
        undoListView.setItems(historyManager.getUndoObservableList());
        undoButton.setOnAction(e -> performUndo());
        redoButton.setOnAction(e -> performRedo());
        historyManager.getUndoObservableList().addListener((javafx.collections.ListChangeListener.Change<? extends String> c) -> {
            undoButton.setDisable(historyManager.getUndoObservableList().isEmpty());
            redoButton.setDisable(historyManager.getRedoObservableList().isEmpty());
        });
    }

    private void performUndo() {
        historyManager.undo();
        refreshAllPanes();
    }

    private void performRedo() {
        historyManager.redo();
        refreshAllPanes();
    }

    private void refreshAllPanes() {
        leftPane().refresh();
        rightPane().refresh();
        // For each tab, restore tab state if necessary
        for (javafx.scene.control.Tab tab : tabPane.getTabs()) {
            restoreTabState(tab);
        }
    }
    @FXML public void deleteSelectedFiles() {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;
        for (java.io.File f : selected) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().moveToTrash(f);
                else f.delete();
                historyManager.recordAction("Deleted: " + f.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
        getCurrentPane().getItems().removeAll(selected);
    }

    @FXML public void renameSelectedFile() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        java.io.File selected = pane.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(selected.getName());
        dialog.setTitle("Rename File");
        dialog.setHeaderText("Rename File");
        dialog.setContentText("New name:");
        java.util.Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            java.io.File target = new java.io.File(selected.getParent(), newName);
            if (selected.renameTo(target)) {
                pane.getItems().set(pane.getItems().indexOf(selected), target);
                historyManager.recordAction("Renamed: " + selected.getAbsolutePath() + " -> " + target.getAbsolutePath());
            }
        });
    }

    @FXML public void moveSelectedFiles(java.io.File targetFolder) {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty() || targetFolder == null) return;
        for (java.io.File f : selected) {
            try {
                java.nio.file.Files.move(f.toPath(), java.nio.file.Paths.get(targetFolder.getAbsolutePath(), f.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                historyManager.recordAction("Moved: " + f.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
        getCurrentPane().getItems().removeAll(selected);
    }

    @FXML public void copySelectedFiles(java.io.File targetFolder) {
        javafx.collections.ObservableList<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selected.isEmpty() || targetFolder == null) return;
        for (java.io.File f : selected) {
            try {
                java.nio.file.Files.copy(f.toPath(), java.nio.file.Paths.get(targetFolder.getAbsolutePath(), f.getName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                historyManager.recordAction("Copied: " + f.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
    private java.util.Map<javafx.scene.control.Tab, PaneState> tabStates = new java.util.HashMap<>();

    private static class PaneState {
        java.io.File leftFolder;
        java.io.File rightFolder;
        String leftLayout; String rightLayout;
        String leftSort; String rightSort;
        String leftFilter; String rightFilter;
    }

    private void saveCurrentTabState() {
        javafx.scene.control.Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) return;
        PaneState state = new PaneState();
        state.leftFolder = leftPaneCurrentFolder();
        state.rightFolder = rightPaneCurrentFolder();
        state.leftLayout = getPaneLayout(leftPane());
        state.rightLayout = getPaneLayout(rightPane());
        state.leftSort = getPaneSort(leftPane());
        state.rightSort = getPaneSort(rightPane());
        state.leftFilter = getPaneFilter(leftPane());
        state.rightFilter = getPaneFilter(rightPane());
        tabStates.put(tab, state);
    }

    private void restoreTabState(javafx.scene.control.Tab tab) {
        PaneState state = tabStates.get(tab);
        if (state == null) return;
        setPaneFolder(leftPane(), state.leftFolder);
        setPaneFolder(rightPane(), state.rightFolder);
        setPaneLayout(leftPane(), state.leftLayout);
        setPaneLayout(rightPane(), state.rightLayout);
        setPaneSort(leftPane(), state.leftSort);
        setPaneSort(rightPane(), state.rightSort);
        setPaneFilter(leftPane(), state.leftFilter);
        setPaneFilter(rightPane(), state.rightFilter);
    }

    @FXML public void initializeDragAndDrop() {
        installDragAndDrop(leftPane());
        installDragAndDrop(rightPane());
    }

    private void installDragAndDrop(javafx.scene.control.ListView<java.io.File> pane) {
        pane.setOnDragDetected(event -> DragAndDropHandler.startDrag(pane, event));
        pane.setOnDragOver(event -> DragAndDropHandler.handleDragOver(pane, event));
        pane.setOnDragDropped(event -> {
            DragAndDropHandler.handleDrop(pane, event);
            saveCurrentTabState();
        });
    }
    @FXML private javafx.scene.control.TabPane tabPane;

    @FXML public void addNewTab() {
        javafx.scene.control.Tab tab = new javafx.scene.control.Tab("New Tab");
        javafx.scene.layout.BorderPane pane = new javafx.scene.layout.BorderPane();
        tab.setContent(pane);
        tab.setClosable(true);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        historyManager.recordAction("Added new tab");
    }

    @FXML public void closeCurrentTab() {
        javafx.scene.control.Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null) {
            tabPane.getTabs().remove(selected);
            historyManager.recordAction("Closed tab: " + selected.getText());
        }
    }

    @FXML public void moveTabLeft() {
        javafx.scene.control.Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int idx = tabPane.getTabs().indexOf(selected);
            if (idx > 0) {
                tabPane.getTabs().remove(selected);
                tabPane.getTabs().add(idx-1, selected);
                tabPane.getSelectionModel().select(selected);
                historyManager.recordAction("Moved tab left: " + selected.getText());
            }
        }
    }

    @FXML public void moveTabRight() {
        javafx.scene.control.Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int idx = tabPane.getTabs().indexOf(selected);
            if (idx < tabPane.getTabs().size() - 1) {
                tabPane.getTabs().remove(selected);
                tabPane.getTabs().add(idx+1, selected);
                tabPane.getSelectionModel().select(selected);
                historyManager.recordAction("Moved tab right: " + selected.getText());
            }
        }
    }

    private void saveTabState(javafx.scene.control.Tab tab) {
        // Save per-tab state such as current folder, layout, sort, filters
        // Implementation placeholder for persistent storage
    }
    @FXML private javafx.scene.control.ComboBox<String> iconSizeCombo;
    @FXML private javafx.scene.control.ComboBox<String> layoutCombo;
    @FXML private javafx.scene.control.ComboBox<String> themeCombo;

    @FXML public void initializeViewOptions() {
        iconSizeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> setIconSize(newVal));
        layoutCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> setLayout(newVal));
        themeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> setTheme(newVal));
    }

    private void setIconSize(String size) {
        int pixels = switch(size) { case "Small" -> 64; case "Medium" -> 128; case "Large" -> 256; default -> 128; };
        getCurrentPane().setCellFactory(lv -> new FlowTileCell(pixels, pixels));
        getCurrentPane().refresh();
        historyManager.recordAction("Set icon size: " + size);
    }

    private void setLayout(String layout) {
        switch(layout) {
            case "Details" -> switchToTableView();
            case "Tiles" -> switchToTileView();
            case "List" -> switchToListView();
            case "Thumbnails" -> switchToThumbnailView();
        }
        historyManager.recordAction("Set layout: " + layout);
    }

    private void setTheme(String theme) {
        javafx.scene.Scene scene = navigationTree.getScene();
        scene.getStylesheets().clear();
        switch(theme) {
            case "Light" -> scene.getStylesheets().add(getClass().getResource("light.css").toExternalForm());
            case "Dark" -> scene.getStylesheets().add(getClass().getResource("dark.css").toExternalForm());
            case "Glassy" -> scene.getStylesheets().add(getClass().getResource("glassy.css").toExternalForm());
        }
        historyManager.recordAction("Set theme: " + theme);
    }

    private void switchToTableView() { /* code to replace current pane with TableView */ }
    private void switchToTileView() { /* code to replace current pane with Tile-based FlowPane */ }
    private void switchToListView() { /* code to replace current pane with ListView */ }
    private void switchToThumbnailView() { /* code to replace current pane with Thumbnail view using FlowTileCell */ }
    @FXML public void showPropertiesDialog() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("PropertiesDialog.fxml"));
            javafx.scene.Parent root = loader.load();
            PropertiesDialogController controller = loader.getController();
            controller.setFile(selected);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Properties: " + selected.getName());
            stage.setScene(new javafx.scene.Scene(root));
            stage.initOwner(navigationTree.getScene().getWindow());
            stage.show();
            historyManager.recordAction("Opened properties for: " + selected.getAbsolutePath());
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private javafx.scene.layout.Pane previewPane;
    @FXML private javafx.scene.control.Label previewLabel;
    @FXML private javafx.scene.image.ImageView previewImageView;

    @FXML public void initializePreviewPane() {
        getCurrentPane().getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> showPreview(newVal));
    }

    private void showPreview(java.io.File file) {
        if (file == null) { previewImageView.setImage(null); previewLabel.setText(""); return; }
        if (file.isDirectory()) { previewLabel.setText("Folder: " + file.getName()); previewImageView.setImage(null); return; }
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif")) {
                javafx.scene.image.Image img = ThumbnailGenerator.generatePreview(file, 256, 256);
                previewImageView.setImage(img); previewLabel.setText(file.getName());
            } else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".csv")) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
                previewLabel.setText(String.join("\n", lines.subList(0, Math.min(20, lines.size()))));
                previewImageView.setImage(null);
            } else {
                previewLabel.setText("No preview available for: " + file.getName());
                previewImageView.setImage(null);
            }
        } catch(Exception e){ previewLabel.setText("Error loading preview"); previewImageView.setImage(null); e.printStackTrace(); }
        historyManager.recordAction("Previewed file: " + file.getAbsolutePath());
    }
    @FXML private javafx.scene.control.ComboBox<String> sortCombo;
    @FXML private javafx.scene.control.ComboBox<String> groupCombo;
    @FXML private javafx.scene.control.ContextMenu columnMenu;

    @FXML public void initializeSortGroupColumns() {
        sortCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> applySort(newVal));
        groupCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> applyGroup(newVal));
        // columnMenu items should be CheckMenuItem for visibility toggle
    }

    private void applySort(String sortKey) {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        java.util.Comparator<java.io.File> comparator = switch(sortKey) {
            case "Name" -> java.util.Comparator.comparing(java.io.File::getName, String.CASE_INSENSITIVE_ORDER);
            case "Date Modified" -> java.util.Comparator.comparingLong(java.io.File::lastModified);
            case "Size" -> java.util.Comparator.comparingLong(java.io.File::length);
            case "Type" -> java.util.Comparator.comparing(f -> f.getName().substring(f.getName().lastIndexOf(.)+1));
            default -> java.util.Comparator.comparing(java.io.File::getName, String.CASE_INSENSITIVE_ORDER);
        };
        javafx.collections.ObservableList<java.io.File> items = pane.getItems();
        javafx.collections.FXCollections.sort(items, comparator);
        pane.refresh();
        saveFolderPreference(getCurrentFolder(), "sort", sortKey);
        historyManager.recordAction("Applied sort: " + sortKey);
    }

    private void applyGroup(String groupKey) {
        // Simplified example: grouping may require custom cell factory or Section headers
        saveFolderPreference(getCurrentFolder(), "group", groupKey);
        historyManager.recordAction("Applied group: " + groupKey);
        // Actual grouping implementation omitted for brevity; can wrap items in group headers
    }

    private void toggleColumnVisibility(String columnName, boolean visible) {
        // For TableView pane, show/hide columns
        javafx.scene.control.TableView<java.io.File> table = getCurrentTablePane();
        table.getColumns().stream().filter(c -> c.getText().equals(columnName)).forEach(c -> c.setVisible(visible));
        historyManager.recordAction("Toggled column: " + columnName + " visible=" + visible);
    }
    @FXML public void renameSelectedItem() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(selected.getName());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename selected item");
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> {
            java.io.File newFile = new java.io.File(selected.getParent(), newName);
            java.util.concurrent.Executor executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(java.lang.Thread.ofVirtual().factory());
            executor.execute(() -> {
                try {
                    java.nio.file.Files.move(selected.toPath(), newFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    javafx.application.Platform.runLater(() -> refreshCurrentPane());
                    historyManager.recordAction("Renamed " + selected + " to " + newFile,
                        () -> { try { java.nio.file.Files.move(newFile.toPath(), selected.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); refreshCurrentPane(); } catch(Exception e){e.printStackTrace(); } });
                } catch(Exception e){ e.printStackTrace(); }
            });
        });
    }

    @FXML public void createNewFolder() {
        java.io.File folder = getCurrentFolder();
        if (folder == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("New Folder");
        dialog.setTitle("New Folder");
        dialog.setHeaderText("Create new folder");
        dialog.setContentText("Folder name:");
        dialog.showAndWait().ifPresent(name -> {
            java.io.File newDir = new java.io.File(folder, name);
            java.util.concurrent.Executor executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(java.lang.Thread.ofVirtual().factory());
            executor.execute(() -> {
                try { java.nio.file.Files.createDirectory(newDir.toPath());
                    javafx.application.Platform.runLater(() -> refreshCurrentPane());
                    historyManager.recordAction("Created folder: " + newDir,
                        () -> { try { java.nio.file.Files.deleteIfExists(newDir.toPath()); refreshCurrentPane(); } catch(Exception e){e.printStackTrace();} });
                } catch(Exception e){ e.printStackTrace(); }
            });
        });
    }

    @FXML public void deleteSelectedItem() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        java.util.concurrent.Executor executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(java.lang.Thread.ofVirtual().factory());
        executor.execute(() -> {
            try {
                boolean movedToTrash = false;
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().moveToTrash(selected)) movedToTrash = true;
                else java.nio.file.Files.deleteIfExists(selected.toPath());
                javafx.application.Platform.runLater(() -> refreshCurrentPane());
                historyManager.recordAction((movedToTrash ? "Moved to Trash: " : "Deleted: ") + selected,
                    () -> { /* Undo for delete not implemented yet */ });
            } catch(Exception e){ e.printStackTrace(); }
        });
    }
    private void performPasteWithUndo(java.io.File src, java.io.File dest, boolean move) {
        try {
            java.nio.file.Path srcPath = src.toPath();
            java.nio.file.Path destPath = dest.toPath();
            if (move) java.nio.file.Files.move(srcPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            else {
                if (src.isDirectory()) copyDirectory(srcPath, destPath);
                else java.nio.file.Files.copy(srcPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            historyManager.recordAction((move ? "Moved" : "Copied") + " " + src + " to " + dest,
                () -> {
                    try {
                        java.nio.file.Files.deleteIfExists(destPath);
                        refreshCurrentPane();
                    } catch (Exception e) { e.printStackTrace(); }
                });
        } catch (Exception e) { e.printStackTrace(); }
    }
    private java.util.List<java.io.File> clipboard = new java.util.ArrayList<>();
    private boolean cutMode = false;

    @FXML public void copySelected() {
        clipboard.clear();
        clipboard.addAll(getCurrentPane().getSelectionModel().getSelectedItems());
        cutMode = false;
        historyManager.recordAction("Copied items: " + clipboard);
    }

    @FXML public void cutSelected() {
        clipboard.clear();
        clipboard.addAll(getCurrentPane().getSelectionModel().getSelectedItems());
        cutMode = true;
        historyManager.recordAction("Cut items: " + clipboard);
    }

    @FXML public void pasteIntoCurrentFolder() {
        java.io.File targetFolder = getCurrentFolder();
        if (targetFolder == null || clipboard.isEmpty()) return;
        java.util.concurrent.Executor executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(java.lang.Thread.ofVirtual().factory());
        for (java.io.File src : clipboard) {
            executor.execute(() -> {
                try {
                    java.nio.file.Path srcPath = src.toPath();
                    java.nio.file.Path destPath = targetFolder.toPath().resolve(src.getName());
                    if (cutMode) {
                        java.nio.file.Files.move(srcPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        if (src.isDirectory()) {
                            copyDirectory(srcPath, destPath);
                        } else {
                            java.nio.file.Files.copy(srcPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    javafx.application.Platform.runLater(() -> refreshCurrentPane());
                    historyManager.recordAction((cutMode ? "Moved" : "Copied") + " " + src + " to " + targetFolder);
                } catch (Exception e) { e.printStackTrace(); }
            });
        }
        if (cutMode) clipboard.clear();
        cutMode = false;
    }

    private void copyDirectory(java.nio.file.Path src, java.nio.file.Path dest) throws java.io.IOException {
        java.nio.file.Files.walk(src).forEach(p -> {
            try {
                java.nio.file.Path relative = src.relativize(p);
                java.nio.file.Path target = dest.resolve(relative);
                if (java.nio.file.Files.isDirectory(p)) java.nio.file.Files.createDirectories(target);
                else java.nio.file.Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
    @FXML private javafx.scene.control.TabPane tabPane;

    @FXML public void addNewTab(java.io.File folder) {
        javafx.scene.control.Tab tab = new javafx.scene.control.Tab(folder.getName());
        javafx.scene.layout.BorderPane content = new javafx.scene.layout.BorderPane();
        javafx.scene.control.ListView<java.io.File> listView = new javafx.scene.control.ListView<>();
        populatePane(listView, folder);
        content.setCenter(listView);
        tab.setContent(content);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        historyManager.recordAction("Added new tab for folder: " + folder.getAbsolutePath());
    }

    @FXML public void closeCurrentTab() {
        javafx.scene.control.Tab current = tabPane.getSelectionModel().getSelectedItem();
        if (current != null) {
            tabPane.getTabs().remove(current);
            historyManager.recordAction("Closed tab: " + current.getText());
        }
    }

    @FXML public void reorderTabs(int fromIndex, int toIndex) {
        javafx.scene.control.Tab t = tabPane.getTabs().remove(fromIndex);
        tabPane.getTabs().add(toIndex, t);
        historyManager.recordAction("Reordered tab from " + fromIndex + " to " + toIndex);
    }

    @FXML public void initializeTabs() {
        tabPane.getTabs().clear();
        // optionally add initial default folder tab
        // tabPane.getTabs().add(createDefaultTab());
    }
    private java.util.List<java.io.File> pinnedItems = new java.util.ArrayList<>();

    @FXML private javafx.scene.control.TreeView<java.io.File> navigationTree;

    @FXML public void pinSelectedItem() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        if (!pinnedItems.contains(selected)) {
            pinnedItems.add(selected);
            addPinnedItemToTree(selected);
            historyManager.recordAction("Pinned item: " + selected.getAbsolutePath());
        }
    }

    @FXML public void unpinSelectedItem() {
        java.io.File selected = getSelectedFolderOrFile();
        if (selected == null) return;
        if (pinnedItems.remove(selected)) {
            removePinnedItemFromTree(selected);
            historyManager.recordAction("Unpinned item: " + selected.getAbsolutePath());
        }
    }

    private void addPinnedItemToTree(java.io.File item) {
        javafx.scene.control.TreeItem<java.io.File> root = navigationTree.getRoot();
        javafx.scene.control.TreeItem<java.io.File> node = new javafx.scene.control.TreeItem<>(item);
        root.getChildren().add(node);
    }

    private void removePinnedItemFromTree(java.io.File item) {
        javafx.scene.control.TreeItem<java.io.File> root = navigationTree.getRoot();
        root.getChildren().removeIf(ti -> ti.getValue().equals(item));
    }

    private java.io.File getSelectedFolderOrFile() {
        javafx.scene.control.TreeItem<java.io.File> sel = navigationTree.getSelectionModel().getSelectedItem();
        return sel != null ? sel.getValue() : null;
    }
    private java.util.Map<java.io.File, java.util.Map<String, String>> folderPreferences = new java.util.HashMap<>();

    private void saveFolderPreference(java.io.File folder, String key, String value) {
        folderPreferences.computeIfAbsent(folder, f -> new java.util.HashMap<>()).put(key, value);
        historyManager.recordAction("Saved preference for " + folder.getAbsolutePath() + ": " + key + "=" + value);
    }

    private String getFolderPreference(java.io.File folder, String key, String defaultValue) {
        return folderPreferences.getOrDefault(folder, java.util.Collections.emptyMap()).getOrDefault(key, defaultValue);
    }

    private void applyFolderPreferences(java.io.File folder) {
        String sort = getFolderPreference(folder, "sort", "Name");
        String group = getFolderPreference(folder, "group", "None");
        String layout = getFolderPreference(folder, "layout", "THUMBNAILS");
        sortCurrentPane(sort);
        groupCurrentPane(group);
        switchView(layout);
    }

    private void onFolderChanged(java.io.File folder) {
        applyFolderPreferences(folder);
    }
    @FXML private javafx.scene.control.ToggleGroup viewToggleGroup;

    @FXML public void initializeViewToggle() {
        viewToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                String view = (String)newToggle.getUserData();
                switchView(view);
            }
        });
    }

    private void switchView(String view) {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        switch(view) {
            case "THUMBNAILS":
                pane.setCellFactory(f -> new FlowTileCell(128, 128));
                break;
            case "LARGE_ICONS":
                pane.setCellFactory(f -> new FlowTileCell(64, 64));
                break;
            case "SMALL_ICONS":
                pane.setCellFactory(f -> new FlowTileCell(32, 32));
                break;
        }
        pane.refresh();
        historyManager.recordAction("Switched view to: " + view);
    }
    @FXML public void showFileProperties() {
        java.io.File selected = getSelectedFile();
        if (selected == null) return;
        try {
            PropertiesDialogController dialog = new PropertiesDialogController(selected);
            javafx.stage.Stage stage = new javafx.stage.Stage();
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("PropertiesDialog.fxml"));
            loader.setController(dialog);
            stage.setScene(new javafx.scene.Scene(loader.load()));
            stage.setTitle("Properties - " + selected.getName());
            stage.initOwner(tabPane.getScene().getWindow());
            stage.showAndWait();
            historyManager.recordAction("Viewed properties for: " + selected.getAbsolutePath());
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private javafx.scene.layout.Pane previewPane;
    @FXML private javafx.scene.image.ImageView previewImageView;
    @FXML private javafx.scene.web.WebView previewWebView;

    @FXML public void togglePreviewPane() {
        boolean visible = previewPane.isVisible();
        previewPane.setVisible(!visible);
        historyManager.recordAction("Toggled Preview Pane: " + (!visible));
    }

    @FXML public void updatePreviewPane() {
        java.io.File selected = getSelectedFile();
        if (selected == null) { previewImageView.setImage(null); previewWebView.getEngine().loadContent(""); return; }
        String name = selected.getName().toLowerCase();
        try {
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".bmp")) {
                javafx.scene.image.Image img = new javafx.scene.image.Image(selected.toURI().toString(), true);
                previewImageView.setImage(img);
                previewWebView.setVisible(false); previewImageView.setVisible(true);
            } else if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".txt") || name.endsWith(".md")) {
                previewWebView.getEngine().load(selected.toURI().toString());
                previewWebView.setVisible(true); previewImageView.setVisible(false);
            } else {
                previewImageView.setImage(null); previewWebView.getEngine().loadContent("<html><body><p>No preview available</p></body></html>");
                previewWebView.setVisible(true); previewImageView.setVisible(false);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML public void initializePreviewPane() {
        getCurrentPane().getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> updatePreviewPane());
    }
    @FXML private javafx.scene.layout.Pane detailsPane;
    @FXML private javafx.scene.control.ListView<java.io.File> previewListView;

    @FXML public void toggleDetailsPane() {
        boolean visible = detailsPane.isVisible();
        detailsPane.setVisible(!visible);
        historyManager.recordAction("Toggled Details Pane: " + (!visible));
    }

    @FXML public void updateDetailsPane() {
        java.util.List<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        previewListView.getItems().clear();
        previewListView.getItems().addAll(selected);
    }

    @FXML public void initializeDetailsPane() {
        getCurrentPane().getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<java.io.File>) c -> updateDetailsPane());
    }
    @FXML private javafx.scene.control.ComboBox<String> sortComboBox;
    @FXML private javafx.scene.control.ComboBox<String> groupComboBox;

    @FXML public void initializeSorting() {
        sortComboBox.getItems().addAll("Name", "Size", "Date Modified", "Type");
        groupComboBox.getItems().addAll("None", "Type", "Date Modified");

        sortComboBox.valueProperty().addListener((obs, oldVal, newVal) -> sortCurrentPane(newVal));
        groupComboBox.valueProperty().addListener((obs, oldVal, newVal) -> groupCurrentPane(newVal));
    }

    private void sortCurrentPane(String criterion) {
        java.util.List<java.io.File> items = getCurrentPane().getItems();
        if (criterion == null) return;
        switch(criterion) {
            case "Name": items.sort((a,b) -> a.getName().compareToIgnoreCase(b.getName())); break;
            case "Size": items.sort((a,b) -> Long.compare(a.length(), b.length())); break;
            case "Date Modified": items.sort((a,b) -> Long.compare(a.lastModified(), b.lastModified())); break;
            case "Type": items.sort((a,b) -> getFileExtension(a).compareToIgnoreCase(getFileExtension(b))); break;
        }
        refreshCurrentPane();
        historyManager.recordAction("Sorted current pane by: " + criterion);
    }

    private void groupCurrentPane(String criterion) {
        // Simple grouping: prepend group headers (pseudo) in Details view
        historyManager.recordAction("Grouped current pane by: " + criterion);
        // TODO: actual UI grouping implementation for Tiles view (future enhancement)
    }

    private javafx.scene.control.ListView<java.io.File> getCurrentPane() {
        return leftPaneFlow.isFocused() ? leftPaneFlow : rightPaneFlow;
    }
    @FXML private javafx.scene.control.TextField searchField;

    @FXML public void initializeSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterCurrentPane(newVal);
        });
    }

    private void filterCurrentPane(String query) {
        javafx.scene.control.ListView<java.io.File> pane = leftPaneFlow.isFocused() ? leftPaneFlow : rightPaneFlow;
        java.util.List<java.io.File> allItems = pane.getItems();
        pane.getItems().clear();
        for (java.io.File f : allItems) {
            if (query == null || query.isEmpty() || f.getName().toLowerCase().contains(query.toLowerCase())) {
                pane.getItems().add(f);
            }
        }
        historyManager.recordAction("Filtered current pane with query: " + query);
    }
    @FXML public void refreshCurrentPane() {
        if (leftPaneFlow.isFocused()) populatePane(leftPaneFlow, leftFolder);
        else populatePane(rightPaneFlow, rightFolder);
        historyManager.recordAction("Refreshed current pane");
    }

    @FXML public void selectColumns() {
        javafx.scene.control.CheckBoxListCell<javafx.scene.control.TableColumn<java.io.File,?>> cell;
        // For simplicity, show dialog listing columns to toggle
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Select Columns");
        javafx.scene.control.VBox vbox = new javafx.scene.control.VBox();
        for (javafx.scene.control.TableColumn<java.io.File,?> col : detailsTable.getColumns()) {
            javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(col.getText());
            cb.setSelected(col.isVisible());
            cb.selectedProperty().addListener((obs, oldV, newV) -> col.setVisible(newV));
            vbox.getChildren().add(cb);
        }
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.OK);
        dialog.showAndWait();
        historyManager.recordAction("Adjusted column visibility");
    }

    private void startAutoRefreshWatcher(java.io.File folder, javafx.scene.control.ListView<java.io.File> pane) {
        java.nio.file.Path path = folder.toPath();
        java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
            try (java.nio.file.WatchService watcher = path.getFileSystem().newWatchService()) {
                path.register(watcher, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                                         java.nio.file.StandardWatchEventKinds.ENTRY_DELETE,
                                         java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
                while (true) {
                    java.nio.file.WatchKey key = watcher.take();
                    javafx.application.Platform.runLater(() -> populatePane(pane, folder));
                    key.reset();
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
    @FXML public void createNewFolder() {
        java.io.File parent = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        String baseName = "New Folder";
        java.io.File newFolder = new java.io.File(parent, baseName);
        int counter = 1;
        while(newFolder.exists()) { newFolder = new java.io.File(parent, baseName + " (" + counter + ")"); counter++; }
        if (newFolder.mkdir()) historyManager.recordAction("Created folder: " + newFolder.getAbsolutePath());
        refreshCurrentPane();
    }

    @FXML public void createNewFile() {
        java.io.File parent = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        String baseName = "New File.txt";
        java.io.File newFile = new java.io.File(parent, baseName);
        int counter = 1;
        while(newFile.exists()) { newFile = new java.io.File(parent, "New File (" + counter + ").txt"); counter++; }
        try { if (newFile.createNewFile()) historyManager.recordAction("Created file: " + newFile.getAbsolutePath()); } catch(Exception e){ e.printStackTrace(); }
        refreshCurrentPane();
    }

    @FXML public void renameSelectedFile() {
        java.io.File selected = getSelectedFile();
        if (selected == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(selected.getName());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename File / Folder");
        dialog.setContentText("New name:");
        dialog.initOwner(tabPane.getScene().getWindow());
        java.util.Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            java.io.File renamed = new java.io.File(selected.getParentFile(), name);
            if(selected.renameTo(renamed)) historyManager.recordAction("Renamed: " + selected.getAbsolutePath() + " -> " + renamed.getAbsolutePath());
            refreshCurrentPane();
        });
    }
    @FXML public void deleteFiles(boolean permanent) {
        java.util.List<java.io.File> files = getCurrentPane().getSelectionModel().getSelectedItems();
        if (files.isEmpty()) return;
        for (java.io.File f : files) {
            try {
                if (permanent) {
                    java.nio.file.Files.deleteIfExists(f.toPath());
                    historyManager.recordAction("Permanently deleted: " + f.getAbsolutePath());
                } else {
                    boolean moved = java.awt.Desktop.getDesktop().moveToTrash(f);
                    historyManager.recordAction((moved ? "Recycled: " : "Failed recycle: ") + f.getAbsolutePath());
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        refreshCurrentPane();
    }

    @FXML public void deleteSelectedFiles() {
        deleteFiles(false);
    }

    @FXML public void permanentlyDeleteSelectedFiles() {
        deleteFiles(true);
    }
    private java.util.List<java.io.File> clipboardFiles = new java.util.ArrayList<>();
    private boolean isCutOperation = false;

    @FXML public void copyFiles() {
        clipboardFiles.clear();
        clipboardFiles.addAll(getCurrentPane().getSelectionModel().getSelectedItems());
        isCutOperation = false;
        historyManager.recordAction("Copied " + clipboardFiles.size() + " files to clipboard");
    }

    @FXML public void cutFiles() {
        clipboardFiles.clear();
        clipboardFiles.addAll(getCurrentPane().getSelectionModel().getSelectedItems());
        isCutOperation = true;
        historyManager.recordAction("Cut " + clipboardFiles.size() + " files to clipboard");
    }

    @FXML public void pasteFiles() {
        java.io.File targetFolder = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        for (java.io.File f : clipboardFiles) {
            try {
                java.io.File dest = new java.io.File(targetFolder, f.getName());
                if (isCutOperation) {
                    java.nio.file.Files.move(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    historyManager.recordAction("Moved file: " + f.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                } else {
                    java.nio.file.Files.copy(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    historyManager.recordAction("Copied file: " + f.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        refreshCurrentPane();
        if (isCutOperation) clipboardFiles.clear();
    }
    @FXML public void showAggregatedProperties() {
        java.util.List<java.io.File> files = getCurrentPane().getSelectionModel().getSelectedItems();
        if (files.isEmpty()) return;
        long totalSize = 0;
        java.util.Map<String, Integer> typeCounts = new java.util.HashMap<>();
        for (java.io.File f : files) {
            totalSize += f.length();
            String ext = getFileExtension(f);
            typeCounts.put(ext, typeCounts.getOrDefault(ext,0)+1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Selected Files: ").append(files.size()).append("\n");
        sb.append("Total Size: ").append(totalSize / 1024).append(" KB\n\n");
        sb.append("File Types:\n");
        for (java.util.Map.Entry<String,Integer> e : typeCounts.entrySet()) sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Aggregated Properties");
        alert.setHeaderText("Multiple Files Properties");
        alert.setContentText(sb.toString());
        alert.initOwner(tabPane.getScene().getWindow());
        alert.showAndWait();
        historyManager.recordAction("Viewed aggregated properties for " + files.size() + " files");
    }
    private QuickAccessManager quickAccess = new QuickAccessManager();

    @FXML public void sendToFolder(java.io.File targetFolder) {
        java.util.List<java.io.File> selected = getCurrentPane().getSelectionModel().getSelectedItems();
        for (java.io.File f : selected) {
            try {
                java.nio.file.Files.copy(f.toPath(), new java.io.File(targetFolder, f.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                historyManager.recordAction("Sent file to: " + targetFolder.getAbsolutePath() + " -> " + f.getName());
            } catch (Exception e) { e.printStackTrace(); }
        }
        refreshCurrentPane();
    }

    @FXML public void pinSelectedFolder() {
        java.io.File folder = getSelectedFile();
        if (folder != null && folder.isDirectory()) {
            quickAccess.pinFolder(folder);
            historyManager.recordAction("Pinned folder: " + folder.getAbsolutePath());
        }
    }

    @FXML public void unpinSelectedFolder() {
        java.io.File folder = getSelectedFile();
        if (folder != null && folder.isDirectory()) {
            quickAccess.unpinFolder(folder);
            historyManager.recordAction("Unpinned folder: " + folder.getAbsolutePath());
        }
    }
    @FXML public void openWithDefaultApp() {
        java.util.List<java.io.File> files = getCurrentPane().getSelectionModel().getSelectedItems();
        if (files.isEmpty()) return;
        for (java.io.File f : files) {
            try { java.awt.Desktop.getDesktop().open(f);
            historyManager.recordAction("Opened with default app: " + f.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @FXML public void openWithCustomApp() {
        java.util.List<java.io.File> files = getCurrentPane().getSelectionModel().getSelectedItems();
        if (files.isEmpty()) return;
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select Application");
        java.io.File app = chooser.showOpenDialog(tabPane.getScene().getWindow());
        if (app != null) {
            for (java.io.File f : files) {
                try { new ProcessBuilder(app.getAbsolutePath(), f.getAbsolutePath()).start();
                historyManager.recordAction("Opened with custom app: " + f.getAbsolutePath());
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }
    @FXML public void copyPathToClipboard() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            final javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            final javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(selected.getAbsolutePath());
            clipboard.setContent(content);
            historyManager.recordAction("Copied path to clipboard: " + selected.getAbsolutePath());
        }
    }

    @FXML public void openTerminalHere() {
        java.io.File folder = getSelectedFile();
        if (folder == null || !folder.isDirectory()) folder = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "cmd", "/K", "cd /d " + folder.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-a", "Terminal", folder.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("x-terminal-emulator", "--working-directory=" + folder.getAbsolutePath()).start();
            }
            historyManager.recordAction("Opened terminal at: " + folder.getAbsolutePath());
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML public void setIconSizeSmall() {
        FlowTileCell.setGlobalIconSize(32);
        refreshCurrentPane();
        historyManager.recordAction("Set icon size to Small");
    }

    @FXML public void setIconSizeMedium() {
        FlowTileCell.setGlobalIconSize(64);
        refreshCurrentPane();
        historyManager.recordAction("Set icon size to Medium");
    }

    @FXML public void setIconSizeLarge() {
        FlowTileCell.setGlobalIconSize(128);
        refreshCurrentPane();
        historyManager.recordAction("Set icon size to Large");
    }

    @FXML public void toggleColumnVisibility(javafx.scene.control.TableColumn<java.io.File, ?> column) {
        column.setVisible(!column.isVisible());
        historyManager.recordAction("Toggled column visibility: " + column.getText());
    }

    @FXML public void togglePreviewPane() {
        previewPaneContainer.setVisible(!previewPaneContainer.isVisible());
        historyManager.recordAction("Toggled Preview Pane visibility");
    }
    @FXML public void sortByName() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getItems().sort((a,b) -> a.getName().compareToIgnoreCase(b.getName()));
        historyManager.recordAction("Sorted by Name in current pane");
    }

    @FXML public void sortBySize() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getItems().sort((a,b) -> Long.compare(a.length(), b.length()));
        historyManager.recordAction("Sorted by Size in current pane");
    }

    @FXML public void sortByDate() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getItems().sort((a,b) -> Long.compare(a.lastModified(), b.lastModified()));
        historyManager.recordAction("Sorted by Date in current pane");
    }

    @FXML public void groupByType() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) {
            pane.getItems().sort((a,b) -> getFileExtension(a).compareToIgnoreCase(getFileExtension(b)));
            historyManager.recordAction("Grouped by Type in current pane");
        }
    }

    private String getFileExtension(java.io.File f) {
        String name = f.getName();
        int idx = name.lastIndexOf(.);
        return idx > 0 ? name.substring(idx+1) : "";
    }
    @FXML public void showPropertiesMulti() {
        java.util.List<java.io.File> selectedFiles = getCurrentPane().getSelectionModel().getSelectedItems();
        if (selectedFiles.isEmpty()) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (java.io.File f : selectedFiles) sb.append(f.getName()).append("\n");
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Properties");
            alert.setHeaderText("Selected Files");
            alert.setContentText(sb.toString());
            alert.initOwner(tabPane.getScene().getWindow());
            alert.showAndWait();
            historyManager.recordAction("Viewed Properties for multiple files: " + selectedFiles.size());
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML public void selectAllFiles() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getSelectionModel().selectAll();
        historyManager.recordAction("Selected All files in current pane");
    }

    @FXML public void deselectAllFiles() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) pane.getSelectionModel().clearSelection();
        historyManager.recordAction("Deselected All files in current pane");
    }

    @FXML public void invertSelection() {
        javafx.scene.control.ListView<java.io.File> pane = getCurrentPane();
        if (pane != null) {
            for (int i = 0; i < pane.getItems().size(); i++) {
                if (pane.getSelectionModel().isSelected(i)) pane.getSelectionModel().clearSelection(i);
                else pane.getSelectionModel().select(i);
            }
            historyManager.recordAction("Inverted selection in current pane");
        }
    }

    private javafx.scene.control.ListView<java.io.File> getCurrentPane() {
        return leftPaneFlow.isFocused() ? leftPaneFlow : rightPaneFlow;
    }
    @FXML public void createNewFolder() {
        java.io.File parent = leftPaneFlow.isFocused() ? leftFolder : rightFolder;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("New Folder");
        dialog.setTitle("Create New Folder");
        dialog.setHeaderText("Enter folder name:");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            java.io.File newFolder = new java.io.File(parent, name);
            if (!newFolder.exists() && newFolder.mkdir()) {
                refreshCurrentPane();
                historyManager.recordAction("Created Folder: " + newFolder.getAbsolutePath());
            }
        });
    }

    @FXML public void renameFile() {
        java.io.File selected = getSelectedFile();
        if (selected == null) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(selected.getName());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Enter new name:");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(newName -> {
            java.io.File renamed = new java.io.File(selected.getParentFile(), newName);
            if (selected.renameTo(renamed)) {
                refreshCurrentPane();
                historyManager.recordAction("Renamed: " + selected.getAbsolutePath() + " -> " + renamed.getAbsolutePath());
            }
        });
    }
    private FolderWatcher leftWatcher;
    private FolderWatcher rightWatcher;

    @FXML public void refreshCurrentPane() {
        if (leftPaneFlow.isFocused()) reloadFolder(leftFolder);
        else reloadFolder(rightFolder);
        historyManager.recordAction("Refreshed current pane");
    }

    private void reloadFolder(java.io.File folder) {
        if (!folder.exists()) return;
        java.io.File[] files = folder.listFiles();
        if (leftPaneFlow.isFocused()) leftPaneFlow.getItems().setAll(files);
        else rightPaneFlow.getItems().setAll(files);
    }

    private void initFolderWatchers() {
        try {
            leftWatcher = new FolderWatcher(leftFolder, this::refreshCurrentPane);
            rightWatcher = new FolderWatcher(rightFolder, this::refreshCurrentPane);
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML public void showProperties() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/fileexplorer/ui/PropertiesDialog.fxml"));
                javafx.scene.Parent root = loader.load();
                PropertiesDialogController ctrl = loader.getController();
                ctrl.setFile(selected);
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(root));
                stage.setTitle("Properties - " + selected.getName());
                stage.initOwner(tabPane.getScene().getWindow());
                stage.showAndWait();
                historyManager.recordAction("Viewed Properties: " + selected.getAbsolutePath());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @FXML public void handleF12Preview() { switchToPreview(); }
    @FXML private javafx.scene.layout.VBox previewPaneContainer;

    private void updatePreviewPane(java.util.List<java.io.File> files) {
        previewPaneContainer.getChildren().clear();
        for (java.io.File f : files) {
            PreviewCell cell = new PreviewCell();
            cell.updateItem(f, false);
            previewPaneContainer.getChildren().add(cell);
        }
    }
    @FXML public void copyFile() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            java.io.File dest = chooseDestination(selected);
            if (dest != null) {
                try { java.nio.file.Files.copy(selected.toPath(), dest.toPath());
                historyManager.recordAction("Copied: " + selected.getAbsolutePath() + " -> " + dest.getAbsolutePath()); }
                catch (Exception e) { System.err.println("Copy failed: " + e); }
            }
        }
    }

    @FXML public void moveFile() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            java.io.File dest = chooseDestination(selected);
            if (dest != null) {
                try { java.nio.file.Files.move(selected.toPath(), dest.toPath());
                historyManager.recordAction("Moved: " + selected.getAbsolutePath() + " -> " + dest.getAbsolutePath()); }
                catch (Exception e) { System.err.println("Move failed: " + e); }
            }
        }
    }

    @FXML public void deleteFile() {
        java.io.File selected = getSelectedFile();
        if (selected != null) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().moveToTrash(selected);
                historyManager.recordAction("Deleted: " + selected.getAbsolutePath());
            } catch (Exception e) { System.err.println("Delete failed: " + e); }
        }
    }

    private java.io.File chooseDestination(java.io.File source) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Destination for " + source.getName());
        return chooser.showDialog(tabPane.getScene().getWindow());
    }
    @FXML public void ribbonOpenWith() {
        java.io.File selectedFile = getSelectedFile();
        if (selectedFile != null) {
            javafx.scene.control.ContextMenu tempMenu = new javafx.scene.control.ContextMenu();
            ContextMenuHandler.addOpenWithOption(tempMenu, selectedFile);
            tempMenu.show(tabPane, javafx.geometry.Side.BOTTOM, 0, 0);
        }
    }

    private java.io.File getSelectedFile() {
        if (leftPaneFlow.isFocused() && !leftPaneFlow.getSelectionModel().isEmpty()) return leftPaneFlow.getSelectionModel().getSelectedItem();
        if (rightPaneFlow.isFocused() && !rightPaneFlow.getSelectionModel().isEmpty()) return rightPaneFlow.getSelectionModel().getSelectedItem();
        return null;
    }
    @FXML private javafx.scene.control.ToggleGroup viewToggleGroup;
    @FXML private javafx.scene.control.RadioButton tilesViewButton;
    @FXML private javafx.scene.control.RadioButton detailsViewButton;
    @FXML private javafx.scene.control.RadioButton previewViewButton;

    @FXML public void switchToTiles() {
        updateCurrentPaneView(ViewMode.TILES);
    }

    @FXML public void switchToDetails() {
        updateCurrentPaneView(ViewMode.DETAILS);
    }

    @FXML public void switchToPreview() {
        updateCurrentPaneView(ViewMode.PREVIEW);
    }

    private enum ViewMode { TILES, DETAILS, PREVIEW }

    private void updateCurrentPaneView(ViewMode mode) {
        // If PREVIEW mode, update preview pane content
        if (mode == ViewMode.PREVIEW) updatePreviewPane(getCurrentPaneItems());
        if (leftPaneFlow.isFocused()) setPaneView(leftPaneFlow, mode);
        else setPaneView(rightPaneFlow, mode);
    }

    private void setPaneView(javafx.scene.control.ListView<java.io.File> pane, ViewMode mode) {
        switch(mode) {
            case TILES: pane.setCellFactory(f -> new FlowTileCell()); break;
            case DETAILS: pane.setCellFactory(f -> new DetailsCell()); break;
            case PREVIEW: pane.setCellFactory(f -> new PreviewCell()); break;
        }
    }
    @FXML public void clearSearch() {
        searchField.clear();
        filterFiles();
    }
    @FXML private javafx.scene.control.TextField searchField;

    @FXML public void filterFiles() {
        String query = searchField.getText().toLowerCase();
        if (query.isEmpty()) {
            refreshCurrentPane();
            return;
        }
        java.util.List<java.io.File> allFiles = getCurrentPaneItems();
        java.util.List<java.io.File> filtered = new java.util.ArrayList<>();
        for (java.io.File f : allFiles) {
            if (f.getName().toLowerCase().contains(query)) filtered.add(f);
        }
        updateCurrentPane(filtered);
        historyManager.recordAction("Filtered: " + query);
    }

    private java.util.List<java.io.File> getCurrentPaneItems() {
        if (leftPaneFlow.isFocused()) return new java.util.ArrayList<>(leftPaneFlow.getItems());
        else return new java.util.ArrayList<>(rightPaneFlow.getItems());
    }

    private void updateCurrentPane(java.util.List<java.io.File> items) {
        if (leftPaneFlow.isFocused()) { leftPaneFlow.getItems().setAll(items); }
        else { rightPaneFlow.getItems().setAll(items); }
    }
    @FXML public void sortByName() {
        if (detailsTableView != null) detailsTableView.getItems().sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    }

    @FXML public void sortByDate() {
        if (detailsTableView != null) detailsTableView.getItems().sort((a, b) -> java.util.Comparator.nullsLast(java.util.Comparator.comparingLong(f -> f.lastModified())).compare(a, b));
    }

    @FXML public void sortBySize() {
        if (detailsTableView != null) detailsTableView.getItems().sort((a, b) -> Long.compare(a.length(), b.length()));
    }

    @FXML public void groupByType() {
        if (detailsTableView != null) {
            java.util.Map<String, java.util.List<java.io.File>> groups = new java.util.TreeMap<>();
            for (java.io.File f : detailsTableView.getItems()) {
                String ext = f.isDirectory() ? "[Folder]" : getExtension(f.getName());
                groups.computeIfAbsent(ext, k -> new java.util.ArrayList<>()).add(f);
            }
            detailsTableView.getItems().clear();
            for (java.util.List<java.io.File> list : groups.values()) { detailsTableView.getItems().addAll(list); }
        }
    }

    private String getExtension(String name) {
        int idx = name.lastIndexOf(.); return (idx >= 0) ? name.substring(idx + 1).toLowerCase() : "[No Ext]";
    }
    private void enableTabDragAndDrop() {
        tabPane.setOnDragDetected(event -> {
            javafx.scene.control.Tab tab = tabPane.getSelectionModel().getSelectedItem();
            if (tab == null) return;
            javafx.scene.input.Dragboard db = tabPane.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(tab.getText());
            db.setContent(content);
            event.consume();
        });

        tabPane.setOnDragOver(event -> {
            if (event.getGestureSource() != tabPane) event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            event.consume();
        });

        tabPane.setOnDragDropped(event -> {
            javafx.scene.input.Dragboard db = event.getDragboard();
            if (db.hasString()) {
                String tabText = db.getString();
                javafx.scene.control.Tab draggedTab = null;
                for (javafx.scene.control.Tab t : tabPane.getTabs()) if (t.getText().equals(tabText)) draggedTab = t;
                if (draggedTab != null) {
                    int dropIndex = 0;
                    for (int i = 0; i < tabPane.getTabs().size(); i++) {
                        if (tabPane.getTabs().get(i).getLayoutX() > event.getX()) { dropIndex = i; break; }
                        dropIndex = i + 1;
                    }
                    tabPane.getTabs().remove(draggedTab);
                    tabPane.getTabs().add(dropIndex, draggedTab);
                    event.setDropCompleted(true);
                }
            }
            event.consume();
        });

        tabPane.setOnDragDone(event -> event.consume());
    }
    @FXML private javafx.scene.control.TabPane tabPane;

    private void setupTabs() {
        tabPane.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.SELECTED_TAB);
        tabPane.getTabs().add(createNewTab(new java.io.File(System.getProperty("user.home"))));
    }

    private javafx.scene.control.Tab createNewTab(java.io.File dir) {
        javafx.scene.control.Tab tab = new javafx.scene.control.Tab(dir.getName());
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox();
        // Add left/right panes to content or a nested layout
        tab.setContent(content);
        tab.setContextMenu(createTabContextMenu(tab, dir));
        return tab;
    }

    private javafx.scene.control.ContextMenu createTabContextMenu(javafx.scene.control.Tab tab, java.io.File dir) {
        javafx.scene.control.MenuItem closeItem = new javafx.scene.control.MenuItem("Close");
        closeItem.setOnAction(e -> tabPane.getTabs().remove(tab));
        javafx.scene.control.MenuItem newTabItem = new javafx.scene.control.MenuItem("New Tab");
        newTabItem.setOnAction(e -> tabPane.getTabs().add(createNewTab(new java.io.File(System.getProperty("user.home")))));
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu(closeItem, newTabItem);
        return menu;
    }
    private void setupDragAndDrop() {
        DragAndDropHandler.enableDragAndDrop(leftPaneFlow, rightPaneFlow, this);
        DragAndDropHandler.enableDragAndDrop(rightPaneFlow, leftPaneFlow, this);
    }

    @FXML private void initialize() {
        enableTabDragAndDrop();
        setupDragAndDrop();
        setupStatusBarListeners();
    }
    @FXML public void copySelectedFiles() {
        java.util.List<File> selectedFiles = getSelectedFiles();
        if (selectedFiles.isEmpty()) return;
        File targetDir = getCurrentDirectory() == leftPaneFlow.getFocusOwner() ? getOtherPaneDirectory(rightPaneFlow) : getOtherPaneDirectory(leftPaneFlow);
        showProgressDialog(selectedFiles, targetDir, false);
    }

    @FXML public void moveSelectedFiles() {
        java.util.List<File> selectedFiles = getSelectedFiles();
        if (selectedFiles.isEmpty()) return;
        File targetDir = getCurrentDirectory() == leftPaneFlow.getFocusOwner() ? getOtherPaneDirectory(rightPaneFlow) : getOtherPaneDirectory(leftPaneFlow);
        showProgressDialog(selectedFiles, targetDir, true);
    }

    private File getOtherPaneDirectory(javafx.scene.control.ListView<File> pane) {
        if (!pane.getItems().isEmpty()) {
            File f = pane.getItems().get(0);
            return f.isDirectory() ? f : f.getParentFile();
        }
        return new File(System.getProperty("user.home"));
    }

    private void showProgressDialog(java.util.List<File> files, File targetDir, boolean move) {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = files.size();
                for (int i = 0; i < total; i++) {
                    File src = files.get(i);
                    File dest = new File(targetDir, src.getName());
                    if (move) {
                        src.renameTo(dest);
                        historyManager.recordAction("Moved: " + src.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                    } else {
                        java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        historyManager.recordAction("Copied: " + src.getAbsolutePath() + " -> " + dest.getAbsolutePath());
                    }
                    updateProgress(i+1, total);
                }
                return null;
            }
        };
        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        javafx.scene.control.Alert dlg = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.NONE);
        dlg.setHeaderText((move ? "Moving" : "Copying") + " files...");
        dlg.getDialogPane().setContent(progressBar);
        dlg.show();
        new Thread(task).start();
        task.setOnSucceeded(e -> { dlg.close(); refreshCurrentPane(); });
        task.setOnFailed(e -> dlg.close());
    }
    private FavoritesManager favoritesManager = new FavoritesManager();
    @FXML private javafx.scene.layout.VBox favoritesPane;

    @FXML public void pinCurrentFolder() {
        File current = getCurrentDirectory();
        if (current != null) {
            favoritesManager.addFavorite(current);
            refreshFavoritesPane();
        }
    }

    @FXML public void unpinFolder(File folder) {
        favoritesManager.removeFavorite(folder);
        refreshFavoritesPane();
    }

    private void refreshFavoritesPane() {
        favoritesPane.getChildren().clear();
        for (File f : favoritesManager.getFavorites()) {
            javafx.scene.control.HBox row = new javafx.scene.control.HBox(5);
            javafx.scene.control.Label lbl = new javafx.scene.control.Label(f.getName());
            javafx.scene.control.Button btnUnpin = new javafx.scene.control.Button("X");
            btnUnpin.setOnAction(e -> unpinFolder(f));
            row.getChildren().addAll(lbl, btnUnpin);
            row.setOnMouseClicked(e -> navigateTo(f));
            favoritesPane.getChildren().add(row);
        }
    }
    @FXML private javafx.scene.control.Label lblPath;
    @FXML private javafx.scene.control.Label lblSelectionCount;
    @FXML private javafx.scene.control.Label lblTotalSize;
    @FXML private javafx.scene.control.Label lblDiskInfo;

    private void updateStatusBar() {
        File selectedDir = getCurrentDirectory();
        lblPath.setText(selectedDir != null ? selectedDir.getAbsolutePath() : "");
        java.util.List<File> selectedFiles = getSelectedFiles();
        lblSelectionCount.setText(selectedFiles.size() + " item(s) selected");
        long totalBytes = selectedFiles.stream().mapToLong(f -> f.length()).sum();
        lblTotalSize.setText(totalBytes / 1024 + " KB");
        if (selectedDir != null) {
            javax.swing.filechooser.FileSystemView fsv = javax.swing.filechooser.FileSystemView.getFileSystemView();
            long usable = selectedDir.getUsableSpace() / 1024 / 1024;
            long total = selectedDir.getTotalSpace() / 1024 / 1024;
            lblDiskInfo.setText("Disk: " + usable + "MB free / " + total + "MB total");
        }
    }

    private File getCurrentDirectory() {
        // Return directory of first pane with focus
        if (!leftPaneFlow.getSelectionModel().isEmpty()) {
            File sel = leftPaneFlow.getSelectionModel().getSelectedItem();
            return sel.isDirectory() ? sel : sel.getParentFile();
        } else if (!rightPaneFlow.getSelectionModel().isEmpty()) {
            File sel = rightPaneFlow.getSelectionModel().getSelectedItem();
            return sel.isDirectory() ? sel : sel.getParentFile();
        }
        return new File(System.getProperty("user.home"));
    }

    private void setupStatusBarListeners() {
        leftPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
        rightPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateStatusBar());
    }
    @FXML public void viewLargeIcons() { FlowTileCell.setIconSize(FlowTileCell.IconSize.LARGE); leftPaneFlow.refresh(); rightPaneFlow.refresh(); }
    @FXML public void viewMediumIcons() { FlowTileCell.setIconSize(FlowTileCell.IconSize.MEDIUM); leftPaneFlow.refresh(); rightPaneFlow.refresh(); }
    @FXML public void viewSmallIcons() { FlowTileCell.setIconSize(FlowTileCell.IconSize.SMALL); leftPaneFlow.refresh(); rightPaneFlow.refresh(); }
    @FXML public void viewList() {
        // List view: smaller tiles, single column text
        FlowTileCell.setIconSize(FlowTileCell.IconSize.SMALL);
        leftPaneFlow.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rightPaneFlow.setOrientation(javafx.geometry.Orientation.VERTICAL);
        leftPaneFlow.refresh();
        rightPaneFlow.refresh();
    }
    @FXML public void viewDetails() {
        showDetailsView();
        updateRibbonForView("Details");
    }
    @FXML public void newFolder() {
        File parent = getSelectedFile();
        if (parent == null || !parent.isDirectory()) {
            parent = new File(System.getProperty("user.home"));
        }
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog("New Folder");
        dlg.setHeaderText("Create New Folder");
        dlg.showAndWait().ifPresent(name -> {
            File newDir = new File(parent, name);
            if (!newDir.exists() && newDir.mkdir()) {
                historyManager.recordAction("Created Folder: " + newDir.getName());
                refreshCurrentPane();
            }
        });
    }

    @FXML public void newFile() {
        File parent = getSelectedFile();
        if (parent == null || !parent.isDirectory()) {
            parent = new File(System.getProperty("user.home"));
        }
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog("NewFile.txt");
        dlg.setHeaderText("Create New File");
        dlg.showAndWait().ifPresent(name -> {
            try {
                File newFile = new File(parent, name);
                if (!newFile.exists() && newFile.createNewFile()) {
                    historyManager.recordAction("Created File: " + newFile.getName());
                    refreshCurrentPane();
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @FXML public void newFromTemplate() {
        // Simple template example: copy from predefined template directory
        File templateDir = new File(System.getProperty("user.home"), ".fileexplorer_templates");
        if (!templateDir.exists()) templateDir.mkdirs();
        java.io.File[] templates = templateDir.listFiles(f -> f.isFile());
        if (templates == null || templates.length == 0) return;
        javafx.scene.control.ChoiceDialog<File> dlg = new javafx.scene.control.ChoiceDialog<>(templates[0], templates);
        dlg.setHeaderText("Select a Template");
        dlg.showAndWait().ifPresent(template -> {
            File parent = getSelectedFile();
            if (parent == null || !parent.isDirectory()) {
                parent = new File(System.getProperty("user.home"));
            }
            try {
                File newFile = new File(parent, template.getName());
                java.nio.file.Files.copy(template.toPath(), newFile.toPath());
                historyManager.recordAction("Created from template: " + newFile.getName());
                refreshCurrentPane();
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
    @FXML private javafx.scene.control.ComboBox<String> cbSortBy;
    @FXML private javafx.scene.control.ComboBox<String> cbGroupBy;

    private void setupSortGroupControls() {
        cbSortBy.getItems().setAll("Name", "Size", "Type", "Date");
        cbGroupBy.getItems().setAll("None", "Date", "Type");

        cbSortBy.setOnAction(e -> {
            String col = cbSortBy.getValue();
            if (currentView.equals("Details")) detailsViewController.sortByColumn(col);
            else if (currentView.equals("Tiles")) tilesFlowController.sortByColumn(col);
        });

        cbGroupBy.setOnAction(e -> {
            String col = cbGroupBy.getValue();
            if (currentView.equals("Details")) detailsViewController.groupByColumn(col);
        });
    }

    private void updateRibbonForView(String view) {
        currentView = view;
        switch(view) {
            case "Details": cbSortBy.setDisable(false); cbGroupBy.setDisable(false); break;
            case "Tiles": cbSortBy.setDisable(false); cbGroupBy.setDisable(true); break;
            case "Preview": cbSortBy.setDisable(true); cbGroupBy.setDisable(true); break;
        }
    }

    // Example: call updateRibbonForView("Details") after view switch
    @FXML private javafx.scene.layout.StackPane contentPane;
    @FXML private javafx.fxml.FXMLLoader detailsLoader;
    @FXML private javafx.fxml.FXMLLoader tilesLoader;
    @FXML private javafx.fxml.FXMLLoader previewLoader;

    private void showDetailsView() {
        try {
            if (detailsLoader == null) detailsLoader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/fileexplorer/ui/DetailsView.fxml"));
            javafx.scene.Parent detailsRoot = detailsLoader.load();
            contentPane.getChildren().setAll(detailsRoot);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showTilesView() {
        try {
            if (tilesLoader == null) tilesLoader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/fileexplorer/ui/TilesView.fxml"));
            javafx.scene.Parent tilesRoot = tilesLoader.load();
            contentPane.getChildren().setAll(tilesRoot);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showPreviewView() {
        try {
            if (previewLoader == null) previewLoader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/fileexplorer/ui/PreviewPane.fxml"));
            javafx.scene.Parent previewRoot = previewLoader.load();
            contentPane.getChildren().setAll(previewRoot);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML public void viewTiles() { showTilesView(); }
    @FXML public void viewDetails() { showDetailsView(); }
    @FXML public void viewPreview() { showPreviewView(); }
    private java.util.List<File> getSelectedFiles() {
        java.util.List<File> files = new java.util.ArrayList<>();
        files.addAll(leftPaneFlow.getSelectionModel().getSelectedItems());
        files.addAll(rightPaneFlow.getSelectionModel().getSelectedItems());
        return files;
    }

    @FXML public void deleteSelectedBatch() {
        for (File f : getSelectedFiles()) {
            deleteFile(f);
        }
    }

    @FXML public void copySelectedBatch() {
        for (File f : getSelectedFiles()) {
            copyFile(f);
        }
    }

    @FXML public void renameSelectedBatch() {
        for (File f : getSelectedFiles()) {
            renameFile(f);
        }
    }

    @FXML public void propertiesSelectedBatch() {
        for (File f : getSelectedFiles()) {
            showProperties(f);
        }
    }
    @FXML private javafx.scene.control.Button btnDelete;
    @FXML private javafx.scene.control.Button btnRename;
    @FXML private javafx.scene.control.Button btnCopy;
    @FXML private javafx.scene.control.Button btnProperties;

    private void updateRibbonButtons() {
        File selected = getSelectedFile();
        boolean hasSelection = selected != null;
        btnDelete.setDisable(!hasSelection);
        btnRename.setDisable(!hasSelection);
        btnCopy.setDisable(!hasSelection);
        btnProperties.setDisable(!hasSelection);

        if (hasSelection && selected.isDirectory()) {
            // Example: disable certain actions for directories if needed
        }
    }

    private void setupSelectionListeners() {
        leftPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateRibbonButtons());
        rightPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updateRibbonButtons());
    }
    @FXML public void deleteSelected() {
        File selected = getSelectedFile();
        if (selected != null) deleteFile(selected);
    }

    @FXML public void renameSelected() {
        File selected = getSelectedFile();
        if (selected != null) renameFile(selected);
    }

    @FXML public void copySelected() {
        File selected = getSelectedFile();
        if (selected != null) copyFile(selected);
    }

    @FXML public void propertiesSelected() {
        File selected = getSelectedFile();
        if (selected != null) showProperties(selected);
    }

    private File getSelectedFile() {
        File f = null;
        if (!leftPaneFlow.getSelectionModel().isEmpty()) f = leftPaneFlow.getSelectionModel().getSelectedItem();
        else if (!rightPaneFlow.getSelectionModel().isEmpty()) f = rightPaneFlow.getSelectionModel().getSelectedItem();
        return f;
    }

    public void deleteFile(File file) {
        try {
            java.awt.Desktop.getDesktop().moveToTrash(file);
            historyManager.recordAction("Deleted: " + file.getName());
            refreshCurrentPane();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void renameFile(File file) {
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog(file.getName());
        dlg.setHeaderText("Rename File");
        dlg.showAndWait().ifPresent(newName -> {
            File renamed = new File(file.getParentFile(), newName);
            if (file.renameTo(renamed)) {
                historyManager.recordAction("Renamed: " + file.getName() + " → " + newName);
                refreshCurrentPane();
            }
        });
    }

    public void copyFile(File file) {
        try {
            java.nio.file.Path dest = java.nio.file.Paths.get(file.getParent(), "Copy_of_" + file.getName());
            java.nio.file.Files.copy(file.toPath(), dest);
            historyManager.recordAction("Copied: " + file.getName());
            refreshCurrentPane();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void showProperties(File file) {
        PropertiesDialogController dlgCtrl = new PropertiesDialogController();
        dlgCtrl.showProperties(file);
    }

    private void refreshCurrentPane() {
        // Simple refresh: reload FlowTileCells
        leftPaneFlow.refresh();
        rightPaneFlow.refresh();
    }
    // Tab persistence storage (simple example using user home)
    private static final String TAB_STATE_FILE = System.getProperty("user.home") + "/.fileexplorer_tabs.dat";

    public void saveTabsState() {
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(new java.io.FileOutputStream(TAB_STATE_FILE))) {
            java.util.List<String> paths = new java.util.ArrayList<>();
            for (javafx.scene.control.Tab tab : leftTabPane.getTabs()) {
                paths.add(tab.getText());
            }
            out.writeObject(paths);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void restoreTabsState() {
        java.io.File f = new java.io.File(TAB_STATE_FILE);
        if (!f.exists()) return;
        try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(new java.io.FileInputStream(f))) {
            java.util.List<String> paths = (java.util.List<String>) in.readObject();
            for (String path : paths) {
                addTabToLeft(path);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private PreviewPaneController previewPaneController;
    @FXML private FlowTileCell leftPaneFlow;
    @FXML private FlowTileCell rightPaneFlow;

    private void setupDualPaneSelection() {
        leftPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> {
            previewPaneController.showFile(newFile);
            rightPaneFlow.getSelectionModel().clearSelection();
        });

        rightPaneFlow.getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> {
            previewPaneController.showFile(newFile);
            leftPaneFlow.getSelectionModel().clearSelection();
        });
    }
    @FXML public void groupByNone() {
        if (detailsViewController != null) detailsViewController.groupByColumn("None");
    }
    @FXML public void groupByDate() {
        if (detailsViewController != null) detailsViewController.groupByColumn("Date");
    }
    @FXML public void groupByType() {
        if (detailsViewController != null) detailsViewController.groupByColumn("Type");
    }
    @FXML private DetailsViewController detailsViewController;

    @FXML public void sortByName() {
        if (detailsViewController != null) detailsViewController.sortByColumn("Name");
    }
    @FXML public void sortBySize() {
        if (detailsViewController != null) detailsViewController.sortByColumn("Size");
    }
    @FXML public void sortByType() {
        if (detailsViewController != null) detailsViewController.sortByColumn("Type");
    }
    @FXML public void sortByDate() {
        if (detailsViewController != null) detailsViewController.sortByColumn("Date");
    }
    private String currentTheme = "Light";

    public void setTheme(String themeName) {
        javafx.scene.Scene scene = lblCurrentPath.getScene();
        if (scene == null) return;
        scene.getStylesheets().clear();
        switch(themeName) {
            case "Light": scene.getStylesheets().add(getClass().getResource("Light.css").toExternalForm()); break;
            case "Dark": scene.getStylesheets().add(getClass().getResource("Dark.css").toExternalForm()); break;
            case "Glassy": scene.getStylesheets().add(getClass().getResource("Glassy.css").toExternalForm()); break;
        }
        currentTheme = themeName;
        System.out.println("[Theme] Switched to: " + currentTheme);
    }

    public String getCurrentTheme() { return currentTheme; }
    @FXML private javafx.scene.layout.HBox statusBar;
    @FXML private javafx.scene.control.Label lblCurrentPath;
    @FXML private javafx.scene.control.Label lblSelectionCount;
    @FXML private javafx.scene.control.Label lblTotalSize;
    @FXML private javafx.scene.control.ProgressBar operationProgress;

    public void updateStatusBar(String path, int selectedCount, long totalSizeBytes, double progress) {
        lblCurrentPath.setText("Path: " + path);
        lblSelectionCount.setText("Selected: " + selectedCount);
        lblTotalSize.setText("Size: " + (totalSizeBytes/1024) + " KB");
        operationProgress.setProgress(progress);
    }
    @FXML private javafx.scene.layout.VBox navigationPane;
    @FXML private NavigationTreeController navTree;

    @FXML public void initialize() {
        navTree.setMainController(this);
        // Existing initialization (TabManager, DragDrop) continues
    }
    @FXML public void onRibbonUndo() {
        if (historyManager != null) historyManager.undo();
    }
    @FXML public void onRibbonRedo() {
        if (historyManager != null) historyManager.redo();
    }
    @FXML public void onRibbonUndo() {
        if (historyManager != null) historyManager.undo();
    }
    @FXML public void onRibbonRedo() {
        if (historyManager != null) historyManager.redo();
    }
    // --- Drag-and-Drop + FlowTileCell integration ---
    private DragAndDropHandler dragHandler;
    @FXML public void initialize() {
        // Register with Ribbon
        MainControllerAccessor.set(this);
        // Initialize TabManager already done in previous chunk
        // Initialize DragAndDropHandler for both panes
        dragHandler = new DragAndDropHandler(leftTabPane, rightTabPane, previewPane, historyManager);
        dragHandler.enableDragAndDrop();
        // Hook FlowTileCell selection to update preview
        leftTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updatePreviewPane());
        rightTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updatePreviewPane());
    }
    // --- Dual-Pane Mode / Tabs / Preview Pane ---
    @FXML private javafx.scene.control.TabPane leftTabPane;
    @FXML private javafx.scene.control.TabPane rightTabPane;
    @FXML private javafx.scene.layout.VBox previewPane;
    private TabManager tabManager;

    @FXML public void initialize() {
        // Register with Ribbon accessor
        MainControllerAccessor.set(this);
        tabManager = new TabManager(leftTabPane, rightTabPane);
        // Add initial tabs
        tabManager.addTab(true, "Home");
        tabManager.addTab(false, "Home");
        // Optional: hook selection listeners to update preview pane
        leftTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updatePreviewPane());
        rightTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updatePreviewPane());
    }

    public void addTabToLeft(String title) { tabManager.addTab(true, title); }
    public void addTabToRight(String title) { tabManager.addTab(false, title); }

    private void updatePreviewPane() {
        previewPane.getChildren().clear();
        Tab leftTab = leftTabPane.getSelectionModel().getSelectedItem();
        Tab rightTab = rightTabPane.getSelectionModel().getSelectedItem();
        // For demo: show selected tab names in preview
        javafx.scene.control.Label lbl = new javafx.scene.control.Label(
            "Left: " + (leftTab != null ? leftTab.getText() : "None") +
            " | Right: " + (rightTab != null ? rightTab.getText() : "None")
        );
        previewPane.getChildren().add(lbl);
    }
    // --- Ribbon demo wiring ---
    public void onNewFolder() {
        System.out.println("[Ribbon Demo] Creating new folder...");
        // call existing folder creation service here
        if (historyManager != null) historyManager.recordAction("New Folder");
    }
    public void onRibbonDeleteWithProgress() {
        System.out.println("[Ribbon Demo] Deleting selected items...");
        if (historyManager != null) historyManager.recordAction("Delete");
        // existing delete service can be called here
    }
    public void onRibbonCopyWithChooser() {
        System.out.println("[Ribbon Demo] Copying selected items...");
        if (historyManager != null) historyManager.recordAction("Copy");
        // call copy service or dialog
    }
    public void onRibbonMoveWithChooser() {
        System.out.println("[Ribbon Demo] Moving selected items...");
        if (historyManager != null) historyManager.recordAction("Move");
        // call move service or dialog
    }
    public void onRibbonCancelOperation() {
        System.out.println("[Ribbon Demo] Cancel operation triggered");
        // cancel pending tasks if needed
    }
    public void onHomeProperties() {
        System.out.println("[Ribbon Demo] Showing properties...");
        // show PropertiesDialog
    }
    public void toggleNavigationPane(boolean show) {
        System.out.println("[Ribbon Demo] toggleNavigationPane="+show);
        // show/hide Navigation Pane
    }
    public void togglePreviewPane(boolean show) {
        System.out.println("[Ribbon Demo] togglePreviewPane="+show);
        // show/hide Preview Pane
    }
    public void toggleDetailsPane(boolean show) {
        System.out.println("[Ribbon Demo] toggleDetailsPane="+show);
        // show/hide Details Pane
    }
    public void setLayout(String layout) {
        System.out.println("[Ribbon Demo] setLayout="+layout);
        // update ListView/FlowTileCell layout accordingly
    }
    public void sortBy(String criteria) {
        System.out.println("[Ribbon Demo] sortBy="+criteria);
        // sort thumbnails/files accordingly
    }
    public void groupBy(String criteria) {
        System.out.println("[Ribbon Demo] groupBy="+criteria);
        // implement groupBy logic if exists
    }
    public void toggleShowExtensions(boolean show) {
        System.out.println("[Ribbon Demo] toggleShowExtensions="+show);
        // update display of file extensions
    }
    public void toggleShowHidden(boolean show) {
        System.out.println("[Ribbon Demo] toggleShowHidden="+show);
        // update display of hidden files
    }
    // RibbonBar / Toolbar support methods
    public void toggleNavigationPane(boolean show) { System.out.println("toggleNavigationPane="+show); }
    public void togglePreviewPane(boolean show) { System.out.println("togglePreviewPane="+show); }
    public void toggleDetailsPane(boolean show) { System.out.println("toggleDetailsPane="+show); }
    public void setLayout(String layout) { System.out.println("Layout set to: "+layout); }
    public void sortBy(String criteria) { System.out.println("Sort by: "+criteria); }
    public void groupBy(String criteria) { System.out.println("Group by: "+criteria); }
    public void toggleShowExtensions(boolean show) { System.out.println("Show extensions: "+show); }
    public void toggleShowHidden(boolean show) { System.out.println("Show hidden: "+show); }
    public void onNewFolder() { System.out.println("New folder created"); }
    public void renameSelected() { System.out.println("Rename selected files"); }
    public void showPropertiesForSelected() { System.out.println("Show properties"); }
    public void compressSelected() { System.out.println("Compress selected files to ZIP"); }
    public void burnSelected() { System.out.println("Burn selected files to disc"); }
    public void createShortcut() { System.out.println("Create shortcut"); }
    public void shareSelected() { System.out.println("Share selected files"); }
    public void emailSelected() { System.out.println("Email selected files"); }
    public void zipSelected() { System.out.println("ZIP selected files"); }
    public void mapNetworkDrive() { System.out.println("Map network drive"); }
    public void openWithDefault() { System.out.println("Open with default"); }
    public void openWithChooser() { System.out.println("Open with chooser"); }
    public void onRibbonCopyWithChooser() { System.out.println("Copy with chooser"); }
    public void onRibbonMoveWithChooser() { System.out.println("Move with chooser"); }
    public void onRibbonDeleteWithProgress() { System.out.println("Delete with progress"); }
    public void onRibbonCancelOperation() { System.out.println("Cancel operation"); }

    // @FXML private TabPane mainTabPane;
    // @FXML private StackPane previewPaneContainer;

    // private HistoryManager historyManager;
    // private PreviewPaneController previewPane;

    // // Virtual thread executor (Java 25 virtual threads)
    // private final ExecutorService vtExecutor = Executors.newThreadPerTaskExecutor(
            // Thread.ofVirtual().factory()
    // );

    // // Persistence + navigation event broadcasting
    // private final TabPersistence tabPersistence = new TabPersistence();
    // private final NavigationBus navBus = NavigationBus.getInstance();

    // public void setHistoryManager(HistoryManager manager) {
        // this.historyManager = manager;
    // }

    // @FXML
    // public void initialize() {
        // loadPreviewPane();
        // restoreTabsOnStartup();
        // hookTabSelectionEvents();
    // }

    // private void loadPreviewPane() {
        // try {
            // FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PreviewPane.fxml"));
            // Node pane = loader.load();
            // previewPane = loader.getController();
            // previewPaneContainer.getChildren().setAll(pane);
        // } catch (IOException e) {
            // throw new RuntimeException("Failed to load Preview Pane", e);
        // }
    // }

    // private void restoreTabsOnStartup() {
        // vtExecutor.submit(() -> {
            // var saved = tabPersistence.loadSavedTabs();
            // Platform.runLater(() -> {
                // if (saved.isEmpty()) {
                    // createTabWithLoader(Path.of(System.getProperty("user.home")));
                // } else {
                    // saved.forEach(state -> createTabWithLoader(state.initialPath()));
                // }
            // });
        // });
    // }

    // private void hookTabSelectionEvents() {
        // mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            // if (newTab != null && newTab.getUserData() instanceof Path path) {
                // navBus.publish(new PathSelectionEvent(path));
                // if (previewPane != null)
                    // previewPane.loadPreviewFor(path);
            // }
        // });
    // }

    // // ============================================================
    // //               NEW METHOD (A) — full implementation
    // // ============================================================

    // /**
     // * New authoritative method for creating a tab.
     // * All prior logic has been relocated here.
     // */
    // public Tab createTabWithLoader(Path initialPath) {
        // if (initialPath == null) throw new IllegalArgumentException("initialPath cannot be null");

        // Tab tab = new Tab(initialPath.getFileName() != null ? initialPath.getFileName().toString() : initialPath.toString());
        // tab.setClosable(true);
        // tab.setUserData(initialPath);

        // // — Load the Flow View UI —
        // FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/FlowView.fxml"));
        // Node content;
        // FlowViewController flowController;
        // try {
            // content = loader.load();
            // flowController = loader.getController();
        // } catch (IOException e) {
            // throw new RuntimeException("Failed to load FlowView.fxml", e);
        // }

        // // Set controller dependencies
        // flowController.setHistoryManager(historyManager);
        // flowController.setPreviewPane(previewPane);

        // // Start async directory load via virtual threads
        // vtExecutor.submit(() -> {
            // try {
                // var files = Files.list(initialPath).toList();
                // Platform.runLater(() -> flowController.loadDirectory(files));
            // } catch (IOException ex) {
                // Platform.runLater(() -> flowController.showError("Failed to load directory: " + ex.getMessage()));
            // }
        // });

        // tab.setContent(content);

        // // When tab closes → persist state
        // tab.setOnClosed(evt -> {
            // tabPersistence.removeTabState(initialPath);
        // });

        // mainTabPane.getTabs().add(tab);
        // mainTabPane.getSelectionModel().select(tab);

        // // Persist immediately
        // tabPersistence.addTabState(new TabState(initialPath));

        // // Broadcast navigation event
        // navBus.publish(new PathSelectionEvent(initialPath));

        // return tab;
    // }

    // // ============================================================
    // //          OLD METHOD (B + C) — DEPRECATED WRAPPER
    // // ============================================================

    // /**
     // * Deprecated. Use createTabWithLoader(Path) instead.
     // * Preserved only for backward compatibility.
     // */
    // @Deprecated(since="25.0", forRemoval=false)
    // public Tab createTab(Path path) {
        // return createTabWithLoader(path);
    // }

    // // ============================================================
    // //           Public helper for UI commands
    // // ============================================================

    // public void openNewTabAt(Path path) {
        // createTabWithLoader(path);
    // }
// }

package com.fileexplorer.ui;

/**
 * Small static accessor so RibbonBarController can call into MainController
 * without fragile lookup logic. MainController.initialize() sets the reference.
 */
public final class MainControllerAccessor {
    private static volatile MainController instance;
    private MainControllerAccessor() {}
    public static void set(MainController c) { instance = c; }
    public static MainController get() { return instance; }
}
