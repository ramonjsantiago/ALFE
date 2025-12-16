package com.example.explorerfx.controller;

import com.example.explorerfx.model.FileItem;
import com.example.explorerfx.service.FileOperationService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.*;

/**
 * ExplorerController
 * Controls the entire ExplorerFX UI layout (ribbon, navigation pane, table view, etc.).
 * Handles initialization, folder navigation, file operations, and theme switching.
 */
public class ExplorerController {

    // ================================
    // UI Bindings (linked from FXML)
    // ================================
    @FXML private TableView<FileItem> fileTable;
    @FXML private TableColumn<FileItem, String> colName;
    @FXML private TableColumn<FileItem, String> colType;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colDate;

    @FXML private TreeView<String> navigationTree;
    @FXML private TextField addressBar;
    @FXML private TextField searchBar;
    @FXML private ListView<String> quickAccessList;

    @FXML private CheckBox chkDarkMode;
    @FXML private Label lblItemCount;
    @FXML private Label lblSelectedCount;
    @FXML private Label lblTotalSize;
    @FXML private Label lblStatus;
    @FXML private TabPane tabPane;

    private ObservableList<FileItem> fileList = FXCollections.observableArrayList();
    private final Stack<Path> backStack = new Stack<>();
    private final Stack<Path> forwardStack = new Stack<>();
    private Path currentPath;

    private FileOperationService fileService;

    // ================================
    // Initialization
    // ================================
    @FXML
    public void initialize() {
        fileService = new FileOperationService();

        // Setup columns
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colSize.setCellValueFactory(new PropertyValueFactory<>("formattedSize"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("lastModified"));

        fileTable.setItems(fileList);
        fileTable.setRowFactory(tv -> {
            TableRow<FileItem> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openItem(row.getItem());
                }
            });
            return row;
        });

        // Initialize navigation tree
        populateNavigationTree();

        // Initialize Quick Access
        quickAccessList.getItems().addAll(
                System.getProperty("user.home"),
                System.getProperty("user.home") + "/Documents",
                System.getProperty("user.home") + "/Downloads",
                System.getProperty("user.home") + "/Pictures"
        );
        quickAccessList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) navigateTo(Paths.get(newVal));
        });

        // Address bar Enter key navigation
        addressBar.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                navigateTo(Paths.get(addressBar.getText()));
            }
        });

        // Initial directory
        navigateTo(Paths.get(System.getProperty("user.home")));

        updateStatus("Ready");
    }

    // ================================
    // Navigation & Table Management
    // ================================
    private void populateNavigationTree() {
        TreeItem<String> root = new TreeItem<>("This PC");
        File[] roots = File.listRoots();
        for (File r : roots) {
            TreeItem<String> drive = new TreeItem<>(r.getAbsolutePath());
            drive.getChildren().add(new TreeItem<>("Loading..."));
            drive.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
                if (isNowExpanded) loadTreeChildren(drive);
            });
            root.getChildren().add(drive);
        }
        navigationTree.setRoot(root);
    }

    private void loadTreeChildren(TreeItem<String> parent) {
        parent.getChildren().clear();
        File f = new File(parent.getValue());
        File[] files = f.listFiles(File::isDirectory);
        if (files != null) {
            for (File child : files) {
                TreeItem<String> item = new TreeItem<>(child.getName());
                item.getChildren().add(new TreeItem<>("Loading..."));
                item.expandedProperty().addListener((obs, o, n) -> {
                    if (n) loadTreeChildren(item);
                });
                parent.getChildren().add(item);
            }
        }
    }

    private void navigateTo(Path path) {
        if (path == null || !Files.exists(path)) {
            showError("Path not found: " + path);
            return;
        }

        if (currentPath != null) backStack.push(currentPath);
        forwardStack.clear();

        currentPath = path;
        addressBar.setText(path.toAbsolutePath().toString());

        fileList.clear();
        try {
            Files.list(path).forEach(p -> fileList.add(new FileItem(p)));
        } catch (Exception e) {
            showError("Cannot open folder: " + e.getMessage());
        }

        updateStatus("Opened: " + path.getFileName());
        updateStatusBar();
    }

    private void openItem(FileItem item) {
        if (item.isDirectory()) {
            navigateTo(item.getPath());
        } else {
            fileService.openFile(item);
        }
    }

    // ================================
    // Ribbon Actions
    // ================================
    @FXML
    private void actionCopy() { fileService.copySelected(fileTable.getSelectionModel().getSelectedItems()); }

    @FXML
    private void actionPaste() { fileService.paste(currentPath); }

    @FXML
    private void actionDelete() { fileService.deleteSelected(fileTable.getSelectionModel().getSelectedItems()); }

    @FXML
    private void actionRename() { fileService.renameSelected(fileTable.getSelectionModel().getSelectedItem()); }

    @FXML
    private void actionUndo() { fileService.undoLastOperation(); }

    @FXML
    private void actionRedo() { fileService.redoLastOperation(); }

    @FXML
    private void actionProperties() { fileService.showProperties(fileTable.getSelectionModel().getSelectedItem()); }

    @FXML
    private void actionNewFolder() {
        try {
            Path newDir = currentPath.resolve("New Folder");
            Files.createDirectory(newDir);
            navigateTo(currentPath);
        } catch (Exception e) {
            showError("Error creating folder: " + e.getMessage());
        }
    }

    @FXML
    private void actionSearch(KeyEvent e) {
        String query = searchBar.getText().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            navigateTo(currentPath);
            return;
        }
        fileList.setAll(fileList.filtered(item -> item.getName().toLowerCase().contains(query)));
    }

    @FXML
    private void actionBack() {
        if (!backStack.isEmpty()) {
            forwardStack.push(currentPath);
            navigateTo(backStack.pop());
        }
    }

    @FXML
    private void actionForward() {
        if (!forwardStack.isEmpty()) {
            backStack.push(currentPath);
            navigateTo(forwardStack.pop());
        }
    }

    @FXML
    private void actionNavigate() {
        navigateTo(Paths.get(addressBar.getText()));
    }

    // ================================
    // Dark Mode
    // ================================
    @FXML
    private void toggleDarkMode(ActionEvent e) {
        Scene scene = ((Node) e.getSource()).getScene();
        if (chkDarkMode.isSelected()) {
            scene.getRoot().getStyleClass().add("dark-mode");
        } else {
            scene.getRoot().getStyleClass().remove("dark-mode");
        }
    }

    // ================================
    // Utility
    // ================================
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Operation Failed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateStatusBar() {
        lblItemCount.setText("Items: " + fileList.size());
        long selectedSize = fileTable.getSelectionModel().getSelectedItems().stream()
                .mapToLong(FileItem::getSize).sum();
        lblSelectedCount.setText("Selected: " + fileTable.getSelectionModel().getSelectedItems().size());
        lblTotalSize.setText("Total Size: " + humanReadable(selectedSize));
    }

    private void updateStatus(String text) {
        Platform.runLater(() -> lblStatus.setText(text));
    }

    private String humanReadable(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @FXML
    private void handleTabClose(Event e) {
        // Add logic for saving state if needed
    }
}

=====

=====
.root {
    -fx-font-family: "Segoe UI", sans-serif;
    -fx-base: #f0f0f0;
    -fx-background: #f8f8f8;
}

.ribbon-bar {
    -fx-background-color: linear-gradient(to bottom, #e8e8e8, #dcdcdc);
}

.table-view {
    -fx-cell-size: 26px;
    -fx-font-size: 13px;
}

/* === Dark Mode === */
.root.dark-mode {
    -fx-base: #2b2b2b;
    -fx-background: #1e1e1e;
    -fx-text-fill: #f0f0f0;
}

.dark-mode .table-view {
    -fx-background-color: #2b2b2b;
    -fx-text-fill: #f0f0f0;
}

.dark-mode .ribbon-bar {
    -fx-background-color: linear-gradient(to bottom, #333333, #222222);
}

.label, .button, .text-field {
    -fx-text-fill: -fx-text-base-color;
}

=====

=====


=====

=====
// In initialize() or setupTableColumns()

TableColumn<FileItem, ImageView> iconColumn = new TableColumn<>("");
iconColumn.setCellValueFactory(param -> param.getValue().iconProperty());
iconColumn.setPrefWidth(30); // small icon column

TableColumn<FileItem, String> nameColumn = new TableColumn<>("Name");
nameColumn.setCellValueFactory(param -> param.getValue().nameProperty());

// ... other columns (Type, Size, Modified, etc.)