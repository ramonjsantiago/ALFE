package com.fileexplorer.ui;

import com.fileexplorer.service.CursorService;
import com.fileexplorer.service.IconService;
import com.fileexplorer.service.ThemeService;
import com.fileexplorer.service.TreeBuildService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

public final class MainController implements Initializable {

    @FXML private SplitPane mainSplitPane;

    @FXML private TreeView<Path> folderTree;
    @FXML private TableView<Path> fileTable;

    @FXML private TableColumn<Path, Path> iconColumn;
    @FXML private TableColumn<Path, String> nameColumn;
    @FXML private TableColumn<Path, String> typeColumn;
    @FXML private TableColumn<Path, String> sizeColumn;
    @FXML private TableColumn<Path, String> modifiedColumn;

    @FXML private ToggleButton themeToggle;
    @FXML private Label statusLabel;

    @FXML private TextField pathField;

    @FXML private ResizeGrip resizeGrip;

    // injected by <fx:include fx:id="breadcrumbBar" .../>
    @FXML private BreadcrumbBarController breadcrumbBarController;

    private final TreeBuildService treeBuildService;
    private final ThemeService themeService;
    private final IconService iconService;
    private final CursorService cursorService;

    private final ObservableList<Path> tableItems;

    private Stage stage;
    private Scene scene;

    private double uiScale;

    private static final DateTimeFormatter MODIFIED_FMT =
            DateTimeFormatter.ofPattern("MMMM, d, uuuu", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    public MainController() {
        this.treeBuildService = new TreeBuildService();
        this.themeService = new ThemeService();
        this.iconService = new IconService();
        this.cursorService = new CursorService();
        this.tableItems = FXCollections.observableArrayList();

        this.stage = null;
        this.scene = null;
        this.uiScale = 1.0;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        if (resizeGrip != null && stage != null) {
            resizeGrip.attach(stage, cursorService);
        }
    }

    public void setScene(Scene scene) {
        this.scene = scene;

        if (scene != null) {
            installZoomShortcuts(scene);

            cursorService.setUiScale(uiScale);
            cursorService.install(scene);

            themeService.apply(scene);
            syncThemeUi();
        }
    }

    public void openInitialFolder(Path initialFolder) {
        if (initialFolder == null) return;
        Platform.runLater(() -> openFolder(initialFolder));
    }

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        configureTree();
        configureTable();
        configureBreadcrumb();
        configureThemeToggle();
        configurePathField();

        Platform.runLater(this::selectInitialTreeItem);
    }

    private void configureTree() {
        folderTree.setShowRoot(true);

        TreeItem<Path> root = treeBuildService.buildComputerRoot();
        folderTree.setRoot(root);

        folderTree.setCellFactory(tv -> new TreeCell<Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String label = treeBuildService.toDisplayName(item, getTreeItem());
                setText(label);
                setGraphic(iconService.iconForPath(item, true));
            }
        });

        folderTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null || newItem.getValue() == null) return;
            openFolder(newItem.getValue());
        });
    }

    private void configureTable() {
        fileTable.setItems(tableItems);

        iconColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue()));
        iconColumn.setCellFactory(col -> new TableCell<Path, Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                setGraphic(iconService.iconForPath(item, false));
                setText(null);
            }
        });

        nameColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(displayName(param.getValue())));
        typeColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(detectType(param.getValue())));
        sizeColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(humanSize(param.getValue())));
        modifiedColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(modifiedFormatted(param.getValue())));

        for (TableColumn<?, ?> c : fileTable.getColumns()) {
            c.setResizable(true);
        }
        fileTable.setTableMenuButtonVisible(true);
    }

    private void configureBreadcrumb() {
        if (breadcrumbBarController != null) {
            breadcrumbBarController.setOnNavigate(this::openFolder);
        }
    }

    private void configureThemeToggle() {
        if (themeToggle == null) return;

        themeToggle.setSelected(themeService.getTheme() == ThemeService.Theme.DARK);
        syncThemeUi();

        themeToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            themeService.setTheme(Boolean.TRUE.equals(newVal) ? ThemeService.Theme.DARK : ThemeService.Theme.LIGHT);
            if (scene != null) {
                themeService.apply(scene);
            }
            syncThemeUi();
        });
    }

    private void configurePathField() {
        if (pathField == null) return;
        pathField.setOnAction(e -> {
            String t = pathField.getText();
            if (t == null || t.isBlank()) return;
            openFolder(Path.of(t.trim()));
        });
    }

    private void syncThemeUi() {
        boolean dark = themeService.getTheme() == ThemeService.Theme.DARK;

        if (themeToggle != null) {
            themeToggle.setText(dark ? "Dark" : "Light");
            themeToggle.setSelected(dark);
        }
        if (resizeGrip != null) {
            resizeGrip.setDark(dark);
        }
    }

    private void openFolder(Path dir) {
        if (dir == null) return;
        if (!Files.isDirectory(dir)) {
            setStatus("Not a folder: " + dir);
            return;
        }

        if (breadcrumbBarController != null) {
            breadcrumbBarController.setPath(dir);
        }
        if (pathField != null) {
            pathField.setText(dir.toString());
        }

        loadDirectoryIntoTable(dir);
        setStatus("Showing: " + dir);
    }

    private void loadDirectoryIntoTable(Path dir) {
        ObservableList<Path> children = FXCollections.observableArrayList();

        try (Stream<Path> s = Files.list(dir)) {
            s.sorted(Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
                    .thenComparing(this::displayName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(children::add);
        } catch (IOException ex) {
            setStatus("Failed to list: " + dir);
        }

        tableItems.setAll(children);
    }

    private void selectInitialTreeItem() {
        TreeItem<Path> root = folderTree.getRoot();
        if (root == null) return;

        TreeItem<Path> preferred = treeBuildService.findBestInitialSelection(root);
        if (preferred != null) {
            folderTree.getSelectionModel().select(preferred);
            folderTree.scrollTo(folderTree.getRow(preferred));
        } else {
            folderTree.getSelectionModel().select(root);
        }
    }

    private void installZoomShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!e.isControlDown()) return;

            if (e.getCode() == KeyCode.EQUALS || e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD) {
                setUiScale(uiScale + 0.10);
                e.consume();
            } else if (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT) {
                setUiScale(uiScale - 0.10);
                e.consume();
            } else if (e.getCode() == KeyCode.DIGIT0 || e.getCode() == KeyCode.NUMPAD0) {
                setUiScale(1.0);
                e.consume();
            }
        });
    }

    private void setUiScale(double scale) {
        uiScale = Math.max(0.6, Math.min(2.0, scale));
        if (scene != null && scene.getRoot() != null) {
            scene.getRoot().setScaleX(uiScale);
            scene.getRoot().setScaleY(uiScale);
        }
        cursorService.setUiScale(uiScale);
        if (scene != null) {
            cursorService.install(scene);
        }
        setStatus("Zoom: " + Math.round(uiScale * 100.0) + "%");
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text == null ? "" : text);
        }
    }

    private String displayName(Path p) {
        if (p == null) return "";
        Path fn = p.getFileName();
        if (fn == null) {
            String s = p.toString();
            return s.isBlank() ? "Computer" : s;
        }
        String name = fn.toString();
        return name.isBlank() ? p.toString() : name;
    }

    private String detectType(Path p) {
        if (p == null) return "";
        if (Files.isDirectory(p)) return "File folder";
        String name = displayName(p);
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toUpperCase(Locale.ROOT) + " file";
        }
        return "File";
    }

    private String humanSize(Path p) {
        if (p == null || Files.isDirectory(p)) return "";
        try {
            long bytes = Files.size(p);
            if (bytes < 1024) return bytes + " B";
            double kb = bytes / 1024.0;
            if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
            double mb = kb / 1024.0;
            if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
            double gb = mb / 1024.0;
            return String.format(Locale.ROOT, "%.1f GB", gb);
        } catch (IOException ex) {
            return "";
        }
    }

    private String modifiedFormatted(Path p) {
        if (p == null) return "";
        try {
            Instant t = Files.getLastModifiedTime(p).toInstant();
            return MODIFIED_FMT.format(t);
        } catch (IOException ex) {
            return "";
        }
    }
}
