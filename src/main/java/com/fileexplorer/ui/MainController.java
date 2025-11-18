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
// --- Explorer-like additional UI components ---
@FXML private TreeView<Path> folderTree;
@FXML private TableView<FileMetadata> leftDetailsTable;
@FXML private TableColumn<FileMetadata, String> colName;
@FXML private TableColumn<FileMetadata, String> colType;
@FXML private TableColumn<FileMetadata, Long> colSize;
@FXML private TableColumn<FileMetadata, String> colModified;
@FXML private ImageView previewImage;
@FXML private FlowPane rightFlowPane;

// --- Folder tree population ---
private void populateFolderTree(Path rootPath) {
    TreeItem<Path> rootItem = new TreeItem<>(rootPath);
    rootItem.setExpanded(true);
    folderTree.setRoot(rootItem);
    addSubFolders(rootItem);
}

private void addSubFolders(TreeItem<Path> parentItem) {
    File folder = parentItem.getValue().toFile();
    File[] subfolders = folder.listFiles(File::isDirectory);
    if (subfolders != null) {
        for (File f : subfolders) {
            TreeItem<Path> childItem = new TreeItem<>(f.toPath());
            parentItem.getChildren().add(childItem);
        }
    }
}

@FXML
private void onTreeItemClicked() {
    TreeItem<Path> selected = folderTree.getSelectionModel().getSelectedItem();
    if (selected != null) {
        Path path = selected.getValue();
        loadDetailsTable(path);
        loadRightFlowPane(path);
    }
}

// --- Details table population ---
private void loadDetailsTable(Path folder) {
    leftDetailsTable.getItems().clear();
    try {
        Files.list(folder).forEach(p -> {
            FileMetadata fm = new FileMetadata(p.toFile());
            leftDetailsTable.getItems().add(fm);
        });
    } catch (IOException e) {
        updateStatus("Failed to load details table: " + e.getMessage());
    }
}

// --- Right pane FlowPane / preview ---
private void loadRightFlowPane(Path folder) {
    rightFlowPane.getChildren().clear();
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
            cell.setOnMouseClicked(e -> showPreview(p));
            rightFlowPane.getChildren().add(cell);
        });
    } catch (IOException e) {
        updateStatus("Failed to load right pane: " + e.getMessage());
    }
}

// --- Preview Pane ---
private void showPreview(Path file) {
    try {
        previewImage.setImage(IconLoader.loadIcon(file.toFile()));
    } catch (Exception e) {
        previewImage.setImage(IconLoader.getPlaceholder());
    }
}

// --- Metadata class for Details TableView ---
public static class FileMetadata {
    private final File file;
    public FileMetadata(File f) { this.file = f; }
    public String getName() { return file.getName(); }
    public String getType() { return file.isDirectory() ? "Folder" : "File"; }
    public Long getSize() { return file.isFile() ? file.length() : 0L; }
    public String getModified() { return java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.format(
            java.time.Instant.ofEpochMilli(file.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()); }
    public File getFile() { return file; }
}
// --- Explorer-style Left Navigation Tree Enhancements ---
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.util.Callback;

import java.util.HashSet;
import java.util.Set;

@FXML
private void initializeLeftTree() {
    folderTree.setCellFactory(TextFieldTreeCell.forTreeView());
    folderTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
        if (newItem != null) {
            Path path = newItem.getValue();
            loadDetailsTable(path);
            loadRightFlowPane(path);
        }
    });

    // Populate Quick Access pinned items
    pinnedItems.forEach(p -> {
        TreeItem<Path> item = new TreeItem<>(p);
        item.setExpanded(true);
        quickAccessRoot.getChildren().add(item);
    });

    // Populate main file system tree
    populateFolderTree(File.listRoots()[0].toPath());
}

// Set to store pinned items
private final Set<Path> pinnedItems = new HashSet<>();
private final TreeItem<Path> quickAccessRoot = new TreeItem<>(Path.of("Quick Access"));

@FXML
private void onPinItem(Path path) {
    if (pinnedItems.contains(path)) {
        pinnedItems.remove(path);
        quickAccessRoot.getChildren().removeIf(t -> t.getValue().equals(path));
        updateStatus("Unpinned " + path.getFileName());
    } else {
        pinnedItems.add(path);
        quickAccessRoot.getChildren().add(new TreeItem<>(path));
        updateStatus("Pinned " + path.getFileName());
    }
}

// Context menu for tree items
private void setupTreeContextMenu() {
    folderTree.setCellFactory(tv -> {
        TextFieldTreeCell<Path> cell = new TextFieldTreeCell<>() {};
        ContextMenu menu = new ContextMenu();

        MenuItem pinItem = new MenuItem("Pin/Unpin");
        pinItem.setOnAction(e -> {
            TreeItem<Path> selected = cell.getTreeItem();
            if (selected != null) onPinItem(selected.getValue());
        });

        MenuItem newFolderItem = new MenuItem("New Folder");
        newFolderItem.setOnAction(e -> {
            TreeItem<Path> selected = cell.getTreeItem();
            if (selected != null) createNewFolder(selected.getValue());
        });

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            TreeItem<Path> selected = cell.getTreeItem();
            if (selected != null) deleteFolder(selected.getValue());
        });

        MenuItem propertiesItem = new MenuItem("Properties");
        propertiesItem.setOnAction(e -> {
            TreeItem<Path> selected = cell.getTreeItem();
            if (selected != null) showProperties(selected.getValue());
        });

        menu.getItems().addAll(pinItem, newFolderItem, deleteItem, propertiesItem);

        cell.setContextMenu(menu);
        return cell;
    });
}

// --- Folder operations for tree context ---
private void createNewFolder(Path parent) {
    try {
        String folderName = "New Folder";
        Path newFolder = parent.resolve(folderName);
        int i = 1;
        while (Files.exists(newFolder)) {
            newFolder = parent.resolve(folderName + " (" + i++ + ")");
        }
        Files.createDirectory(newFolder);
        updateStatus("Folder created: " + newFolder.getFileName());
        loadDetailsTable(parent);
    } catch (IOException e) {
        updateStatus("Failed to create folder: " + e.getMessage());
    }
}

private void deleteFolder(Path folder) {
    try {
        Files.delete(folder);
        historyManager.recordDelete(folder);
        updateStatus("Deleted folder: " + folder.getFileName());
        TreeItem<Path> selected = folderTree.getSelectionModel().getSelectedItem();
        if (selected != null) selected.getParent().getChildren().remove(selected);
        loadDetailsTable(folder.getParent());
    } catch (IOException e) {
        updateStatus("Failed to delete folder: " + folder.getFileName());
    }
}

private void showProperties(Path folderOrFile) {
    PropertiesDialogController props = new PropertiesDialogController();
    props.show(folderOrFile);
}
