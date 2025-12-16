package com.fileexplorer.ui;

import com.fileexplorer.service.FileMetadataService;
import com.fileexplorer.service.ThemeService;
import com.fileexplorer.service.TreeBuildService;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;

public final class MainController implements Initializable {

    private static final double UI_FONT_DEFAULT_PX = 16.0;
    private static final double UI_FONT_MIN_PX = 10.0;
    private static final double UI_FONT_MAX_PX = 32.0;
    private static final double UI_FONT_STEP_PX = 2.0;

    @FXML private TreeView<Path> folderTree;
    @FXML private TableView<Path> fileTable;

    @FXML private TableColumn<Path, String> colName;
    @FXML private TableColumn<Path, String> colType;
    @FXML private TableColumn<Path, String> colSize;
    @FXML private TableColumn<Path, String> colModified;

    @FXML private ToggleButton themeToggle;
    @FXML private Label statusLabel;

    private final FileMetadataService fileMetadataService;
    private final ThemeService themeService;
    private final TreeBuildService treeBuildService;

    private final ObservableList<Path> tableItems;

    private final AtomicLong directoryLoadSeq;

    private double uiFontSizePx;
    private boolean zoomShortcutsInstalled;

    public MainController() {
        this.fileMetadataService = new FileMetadataService();
        this.themeService = new ThemeService();
        this.treeBuildService = new TreeBuildService();
        this.tableItems = FXCollections.observableArrayList();
        this.directoryLoadSeq = new AtomicLong(0L);

        this.uiFontSizePx = UI_FONT_DEFAULT_PX;
        this.zoomShortcutsInstalled = false;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureTree();
        configureTable();
        configureThemeToggle();
        setStatus("Ready.");
    }

    public void setScene(Scene scene) {
        if (scene == null) {
            return;
        }

        if (!zoomShortcutsInstalled) {
            installZoomShortcuts(scene);
            zoomShortcutsInstalled = true;
        }

        applyUiFontSize(scene);

        Platform.runLater(this::applyThemeToCurrentScene);
    }

    public void openInitialFolder(Path initialFolder) {
        if (initialFolder == null) {
            return;
        }
        Path target = initialFolder.normalize();
        Platform.runLater(() -> expandAndSelectFolder(target));
    }

    private void installZoomShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!e.isControlDown()) {
                return;
            }

            KeyCode code = e.getCode();

            // Ctrl++ : PLUS is not always emitted; on many keyboards it arrives as EQUALS with Shift.
            if (code == KeyCode.PLUS || code == KeyCode.EQUALS || code == KeyCode.ADD) {
                adjustUiFontSize(+UI_FONT_STEP_PX);
                applyUiFontSize(scene);
                e.consume();
                return;
            }

            // Ctrl--
            if (code == KeyCode.MINUS || code == KeyCode.SUBTRACT) {
                adjustUiFontSize(-UI_FONT_STEP_PX);
                applyUiFontSize(scene);
                e.consume();
                return;
            }

            // Optional but standard: Ctrl+0 resets zoom
            if (code == KeyCode.DIGIT0 || code == KeyCode.NUMPAD0) {
                uiFontSizePx = UI_FONT_DEFAULT_PX;
                applyUiFontSize(scene);
                e.consume();
            }
        });
    }

    private void adjustUiFontSize(double deltaPx) {
        uiFontSizePx = clamp(uiFontSizePx + deltaPx, UI_FONT_MIN_PX, UI_FONT_MAX_PX);
        setStatus("UI size: " + (int) uiFontSizePx + "px");
    }

    private void applyUiFontSize(Scene scene) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        // Apply as inline style so it overrides older CSS defaults globally.
        scene.getRoot().setStyle("-fx-font-size: " + uiFontSizePx + "px;");
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    private void configureTree() {
        folderTree.setShowRoot(true);

        TreeItem<Path> root = treeBuildService.buildComputerRoot();
        folderTree.setRoot(root);

        folderTree.setCellFactory(new Callback<TreeView<Path>, TreeCell<Path>>() {
            @Override
            public TreeCell<Path> call(TreeView<Path> view) {
                return new TreeCell<Path>() {
                    @Override
                    protected void updateItem(Path item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setText(null);
                            setGraphic(null);
                            return;
                        }
                        if (item == null) {
                            setText("");
                            setGraphic(null);
                            return;
                        }
                        String label = treeBuildService.toDisplayName(item, getTreeItem());
                        setText(label);
                        setGraphic(null);
                    }
                };
            }
        });

        folderTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                tableItems.clear();
                setStatus("No folder selected.");
                return;
            }
            Path dir = newItem.getValue();
            if (dir == null) {
                tableItems.clear();
                setStatus("No folder selected.");
                return;
            }
            loadDirectoryIntoTableAsync(dir);
        });
    }

    private void configureTable() {
        fileTable.setItems(tableItems);

        colName.setCellValueFactory(param -> {
            Path p = param.getValue();
            String name = fileMetadataService.displayName(p);
            return new ReadOnlyObjectWrapper<>(name);
        });

        colType.setCellValueFactory(param -> {
            Path p = param.getValue();
            String type = fileMetadataService.detectFileType(p);
            return new ReadOnlyObjectWrapper<>(type);
        });

        colSize.setCellValueFactory(param -> {
            Path p = param.getValue();
            String size = fileMetadataService.humanReadableSize(p);
            return new ReadOnlyObjectWrapper<>(size);
        });

        colModified.setCellValueFactory(param -> {
            Path p = param.getValue();
            String ts = fileMetadataService.lastModifiedLocalString(p);
            return new ReadOnlyObjectWrapper<>(ts);
        });

        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                setStatus("Ready.");
                return;
            }
            setStatus(fileMetadataService.describeForStatusBar(newItem));
        });
    }

    private void configureThemeToggle() {
        themeToggle.setSelected(themeService.isDarkPreferred());
        syncThemeToggleText();

        themeToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            themeService.setDarkPreferred(Boolean.TRUE.equals(newVal));
            applyThemeToCurrentScene();
            syncThemeToggleText();
        });

        Platform.runLater(this::applyThemeToCurrentScene);
    }

    private void applyThemeToCurrentScene() {
        Scene scene = folderTree == null ? null : folderTree.getScene();
        if (scene == null) {
            return;
        }
        themeService.apply(scene);

        // Re-apply font size after theme swaps stylesheets.
        applyUiFontSize(scene);
    }

    private void loadDirectoryIntoTableAsync(Path dir) {
        Objects.requireNonNull(dir, "dir");

        final long seq = directoryLoadSeq.incrementAndGet();
        setStatus("Loading: " + fileMetadataService.displayPathForStatus(dir) + " ...");
        tableItems.clear();

        Thread t = new Thread(() -> {
            List<Path> list = new ArrayList<>();
            String statusText;

            try {
                List<Path> children = fileMetadataService.listDirectory(dir);
                list.addAll(children);
                statusText = "Showing: " + fileMetadataService.displayPathForStatus(dir) + " (" + list.size() + " items)";
            } catch (Exception ex) {
                statusText = "Failed to open: " + fileMetadataService.displayPathForStatus(dir);
            }

            final List<Path> finalList = new ArrayList<>(list);
            final String finalStatusText = statusText;

            Platform.runLater(() -> {
                if (directoryLoadSeq.get() != seq) {
                    return;
                }
                tableItems.setAll(finalList);
                setStatus(finalStatusText);
            });
        }, "dir-list");

        t.setDaemon(true);
        t.start();
    }

    private void expandAndSelectFolder(Path target) {
        TreeItem<Path> root = folderTree.getRoot();
        if (root == null || target == null) {
            return;
        }

        TreeItem<Path> drive = treeBuildService.findContainingRootItem(root, target);
        if (drive == null) {
            folderTree.getSelectionModel().select(root);
            return;
        }

        drive.setExpanded(true);

        Path drivePath = drive.getValue();
        if (drivePath == null) {
            folderTree.getSelectionModel().select(drive);
            return;
        }

        Path normalizedTarget = target.normalize();
        Path normalizedDrive = drivePath.normalize();

        TreeItem<Path> current = drive;

        if (normalizedTarget.equals(normalizedDrive)) {
            folderTree.getSelectionModel().select(current);
            folderTree.scrollTo(folderTree.getRow(current));
            return;
        }

        Path relative;
        try {
            relative = normalizedDrive.relativize(normalizedTarget);
        } catch (Exception ex) {
            folderTree.getSelectionModel().select(current);
            folderTree.scrollTo(folderTree.getRow(current));
            return;
        }

        for (Path segment : relative) {
            if (segment == null) {
                break;
            }
            String segName = segment.toString();
            if (segName.isBlank()) {
                continue;
            }

            TreeItem<Path> next = findChildByName(current, segName);
            if (next == null) {
                break;
            }
            current.setExpanded(true);
            current = next;
        }

        folderTree.getSelectionModel().select(current);
        folderTree.scrollTo(folderTree.getRow(current));
    }

    private TreeItem<Path> findChildByName(TreeItem<Path> parent, String name) {
        if (parent == null || name == null) {
            return null;
        }
        for (TreeItem<Path> c : parent.getChildren()) {
            Path v = c.getValue();
            if (v == null) {
                continue;
            }
            Path fn = v.getFileName();
            String candidate = (fn == null) ? v.toString() : fn.toString();
            if (name.equals(candidate)) {
                return c;
            }
        }
        return null;
    }

    private void syncThemeToggleText() {
        themeToggle.setText(themeToggle.isSelected() ? "Dark" : "Light");
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text == null ? "" : text);
        }
    }
}
