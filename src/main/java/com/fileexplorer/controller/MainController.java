package com.fileexplorer.controller;

import com.fileexplorer.app.MainApp;
import com.fileexplorer.util.CompositeCloseable;
import com.fileexplorer.lifecycle.Lifecycle;
import com.fileexplorer.service.filesystem.FileMetadataService;
import com.fileexplorer.service.theme.ThemeService;
import com.fileexplorer.service.filesystem.TreeBuildService;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.stage.Screen;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.OverrunStyle;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Insets;
import java.util.prefs.Preferences;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import java.nio.file.Paths;

import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javafx.scene.control.TableRow;

import javafx.scene.input.MouseButton;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.fileexplorer.util.LogSupport;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;
import java.awt.Desktop;
import java.awt.HeadlessException;
import javafx.scene.paint.Color;
import java.nio.charset.StandardCharsets;
import com.fileexplorer.controller.breadcrumb.BreadcrumbController;
import com.fileexplorer.util.IconLoader;
import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.service.icon.IconCacheService;
import com.fileexplorer.ui.table.TableViewSupport;
import com.fileexplorer.ui.tree.TreeViewSupport;
import com.fileexplorer.ui.tree.SimplePathTreeCell;
import com.fileexplorer.ui.tree.IconPathTreeCell;
import com.fileexplorer.model.FileItem;
import com.fileexplorer.service.filesystem.DirectoryListingService;

import com.fileexplorer.service.filesystem.DirectoryLoadManager;
import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.coordinator.DirectoryCoordinator;
import com.fileexplorer.service.event.events.DirectoryLoadSucceeded;
import com.fileexplorer.service.event.events.DirectoryLoadFailed;
public final class MainController implements Initializable, Lifecycle {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());

    private final CompositeCloseable localDisposables = new CompositeCloseable();

    

    // Scene reference passed from MainApp; may arrive before ExplorerContext is attached.
    private volatile Scene boundScene;
private ExplorerContext context;

    
// Phase 3.4.4: initialize() is called during FXML load before MainApp attaches the ExplorerContext.
// We gate initialization that depends on context/services until attach() has been invoked.
private volatile boolean fxmlInitialized = false;

    private volatile boolean contextInitialized = false;
private static final boolean SAFE_MODE = Boolean.getBoolean("fileexplorer.safeMode");
    private static final boolean RESOURCE_AUDIT = Boolean.getBoolean("fileexplorer.resourceAudit");

    // Fix16: diagnostics guard against VirtualFlow runaway cell creation during preferred-size computation
    private static final java.util.concurrent.atomic.AtomicInteger TREE_CELL_CREATED = new java.util.concurrent.atomic.AtomicInteger();

    private static final double UI_FONT_DEFAULT_PX = 16.0;
    private static final double UI_FONT_MIN_PX = 12.0;
    private static final double UI_FONT_MAX_PX = 32.0;
    private static final double UI_FONT_STEP_PX = 2.0;


    // Minimum vertical padding budget (top and bottom) used for runtime metrics.
    private static final double UI_MIN_VPAD_PX = 5.0;
    private static final double ICON_SIZE_MIN = 48.0;
    private static final double ICON_SIZE_MAX = 160.0;
    private static final double ICON_SIZE_STEP = 12.0;

    private static final String PROP_UI_FONT_PX = "main.uiFontPx";
    private static final String PROP_UI_FONT_FAMILY = "main.uiFontFamily";


    private static final double STARTUP_WIDTH = 1280.0;
    private static final double STARTUP_HEIGHT = 800.0;
    private static final double MIN_WINDOW_WIDTH = 980.0;
    private static final double MIN_WINDOW_HEIGHT = 640.0;

    private static final String PREF_WIN_W = "main.window.width";
    private static final String PREF_WIN_H = "main.window.height";
    private static final String PREF_WIN_MAX = "main.window.maximized";

    private enum ViewMode {
        EXTRA_LARGE_ICONS,
        LARGE_ICONS,
        MEDIUM_ICONS,
        SMALL_ICONS,
        LIST,
        DETAILS,
        TILES,
        CONTENT
    }

    @FXML private TreeView<Path> folderTree;
    @FXML private TableView<FileItem> fileTable;

    @FXML private TableColumn<FileItem, String> colName;
    @FXML private javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, javafx.scene.Node> colStatus;
    @FXML private TableColumn<FileItem, String> colType;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colModified;

    @FXML private ToggleButton themeToggle;
    @FXML private ToggleButton detailsToggle;

    @FXML private BorderPane root;
    @FXML private SplitPane mainSplitPane;

    @FXML private RadioButton viewExtraLargeIcons;
    @FXML private RadioButton viewLargeIcons;
    @FXML private RadioButton viewMediumIcons;
    @FXML private RadioButton viewSmallIcons;
    @FXML private RadioButton viewList;
    @FXML private RadioButton viewDetails;
    @FXML private RadioButton viewTiles;
    @FXML private RadioButton viewContent;

    @FXML private RadioButton detailsPaneMenuItem;
    @FXML private RadioButton previewPaneMenuItem;

    @FXML private CheckBox showNavigationPaneMenuItem;
    @FXML private CheckBox showCompactViewMenuItem;
    @FXML private CheckBox showItemCheckBoxesMenuItem;
    @FXML private CheckBox showFileNameExtensionsMenuItem;
    @FXML private CheckBox showHiddenItemsMenuItem;

    @FXML private Label statusLabel;
    @FXML private Label locationLabel;

    @FXML private TextField searchField;
// --- Top command bar / toolbar (wired programmatically) -----------------
@FXML private javafx.scene.control.MenuButton newMenuButton;
@FXML private javafx.scene.control.Button cutButton;
@FXML private javafx.scene.control.Button copyButton;
@FXML private javafx.scene.control.Button pasteButton;
@FXML private javafx.scene.control.Button renameButton;
@FXML private javafx.scene.control.Button shareButton;
@FXML private javafx.scene.control.Button deleteButton;
@FXML private javafx.scene.control.MenuButton sortMenuButton;
@FXML private javafx.scene.control.MenuButton viewMenuButton;

    @FXML private ScrollPane iconScroll;
    @FXML private FlowPane iconFlow;

    @FXML private VBox previewBox;
    @FXML private VBox detailsBox;

    @FXML private TextArea previewText;
    @FXML private TextArea detailsText;

    // Included controller from fx:include fx:id="breadcrumbBar"
    @FXML private BreadcrumbController breadcrumbBarController;

    private FileMetadataService fileMetadataService;
    private ThemeService themeService;
    private DirectoryListingService listingService;
    private DirectoryLoadManager directoryLoadManager;

    private EventBus eventBus;
    private DirectoryCoordinator directoryCoordinator;

    private volatile java.nio.file.Path lastRequestedDirectory;
    private volatile boolean lastRequestedShowHidden;


    private volatile long lastRequestedRequestId;

    // Phase 3.5.1: Refresh should preserve selection/scroll when possible.
    private volatile java.nio.file.Path pendingReselectPath;
    private volatile int pendingReselectIndex = -1;
    private volatile boolean pendingRestoreSelection;
/** Display-name helper used by TreeCells. */
    private com.fileexplorer.service.filesystem.TreeBuildService displayService;
    private TreeBuildService treeBuildService;

    private final ObservableList<FileItem> tableItems;
    /**
     * Visible (filtered) items shown in the TableView.
     * Backed by {@link #tableItems} (the full directory listing).
     */
    private final FilteredList<FileItem> filteredTableItems;

    // Phase 3.5.4: Search (fast filter of current folder)
    private final javafx.animation.PauseTransition searchDebounce;
    private volatile String activeSearchQuery = "";
    private final AtomicLong directoryLoadSeq;

    // Index cache to avoid O(n) indexOf calls in large folders (used by icon views).
    private final Map<Path, Integer> tableIndexByPath;

    // Icon views: memory-safe rendering (4.1Y incremental, 4.2Y virtualization, 4.3Y safety caps)
    private static final int ICON_FLOW_BATCH_SIZE = 240;
    private static final int ICON_FLOW_MAX_ITEMS = 1200;
    private static final int ICON_VIEW_FORCE_DETAILS_THRESHOLD = 75000;
    private static final double ICON_SCROLL_LOAD_MORE_THRESHOLD = 0.92;

    private ListView<List<Path>> virtualIconGridView;
    private ListView<Path> virtualIconListView;
    private boolean virtualIconViewsInstalled;
    private boolean iconScrollPagingInstalled;

    private long iconBuildGeneration;
    private List<Path> iconBuildItems = List.of();
    private int iconBuildNextIndex;

    private volatile Path lastIconActivatedPath;

    private double uiFontSizePx;
    private String uiFontFamilyCss;
    private String uiFontFamilyResolved;
    private String systemFontFamilyResolved;
    private double treeFontSizePxApplied;
    private final Preferences prefs;
    private final ToggleGroup viewModeToggleGroup;
    private boolean windowPrefsInstalled;
    private boolean zoomShortcutsInstalled;
    private boolean explorerShortcutsInstalled;

    private volatile Path currentDirectory;
    private final List<Path> backHistory;
    private final List<Path> forwardHistory;
    private boolean suppressTreeSelection;


    private boolean treeSelectionUserInitiated;
    private final List<Path> cutBuffer;
    private boolean cutMode;

    private ViewMode viewMode;
    private ViewMode lastIconViewMode;
    private double iconSizePx;

    private boolean showHiddenItems;
    private boolean showFileNameExtensions;
    private boolean compactView;
    private boolean showNavigationPane;
    private boolean showItemCheckBoxes;


// Hover prefetch (Explorer-style): warm icon + metadata caches on pointer hover.
private static final Duration HOVER_PREFETCH_DELAY = Duration.millis(175);
private final AtomicLong hoverPrefetchSeq;
private PauseTransition hoverPrefetchTimer;
private volatile Path hoverPrefetchTarget;
private final ExecutorService hoverPrefetchExecutor;

    // Navigation tree sizing: prevent the pane from collapsing to icon-only width.
    private static final double NAV_TREE_MIN_WIDTH_PX = 275.0;
    private static final double NAV_TREE_PREF_WIDTH_PX = 320.0;

    // Shared background I/O executor for directory listing and paste/copy/move operations.
    private final ExecutorService ioExecutor;
private boolean hoverPrefetchEnabled;

    private int focusCycleIndex;

    public MainController() {
        LogSupport.enter(LOG, "MainController");
        // Phase 3.4.4: ExplorerContext is owned by MainApp and injected via attach(context).
        // Services are assigned during attach().
        this.context = null;
        this.fileMetadataService = null;
        this.themeService = null;
        this.treeBuildService = null;
        this.eventBus = null;
        this.displayService = null;
        this.listingService = null;
        this.directoryLoadManager = null;
        this.directoryCoordinator = null;


        this.tableItems = FXCollections.observableArrayList();
        this.filteredTableItems = new FilteredList<>(this.tableItems, _ -> true);
        this.searchDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
        this.directoryLoadSeq = new AtomicLong(0L);

        // Shared background I/O executor for directory listing and paste/copy/move operations.
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "fe-io");
            t.setDaemon(true);
            return t;
        };
        this.ioExecutor = Executors.newSingleThreadExecutor(tf);
        // listingService is created in attach(context)        // directoryLoadManager is created in attach(context)        // directoryCoordinator is created in attach(context)
// Hover prefetch (Explorer-style): warm icon + metadata caches on pointer hover.
        this.hoverPrefetchSeq = new AtomicLong(0L);
        this.hoverPrefetchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "hover-prefetch");
            t.setDaemon(true);
            return t;
        });
        this.hoverPrefetchEnabled = !SAFE_MODE;
this.tableIndexByPath = new HashMap<>();
        this.iconBuildGeneration = 0L;
        this.iconBuildNextIndex = 0;


        this.prefs = Preferences.userNodeForPackage(MainController.class);
        this.viewModeToggleGroup = new ToggleGroup();
        this.uiFontSizePx = UI_FONT_DEFAULT_PX;

        this.backHistory = new ArrayList<>();
        this.forwardHistory = new ArrayList<>();
        this.cutBuffer = new ArrayList<>();

        this.cutMode = false;
        this.suppressTreeSelection = false;

        this.treeSelectionUserInitiated = false;
        this.viewMode = ViewMode.DETAILS;
        this.lastIconViewMode = ViewMode.MEDIUM_ICONS;
        this.iconSizePx = 88.0;

        this.showHiddenItems = false;
        this.showFileNameExtensions = true;
        this.compactView = false;
        this.showNavigationPane = true;
        this.showItemCheckBoxes = false;

        this.focusCycleIndex = 0;
    }
@Override
public void attach(ExplorerContext context) {
    if (context == null) {
        return;
    }
    // Idempotent attach: tolerate repeated calls.
    if (this.context == context) {
        return;
    }

    this.context = context;
    this.themeService = context.themeService();
    this.fileMetadataService = context.fileMetadataService();
    this.treeBuildService = context.treeBuildService();
    this.eventBus = context.eventBus();


    

        // Phase 3.4.4: MainController is constructed by FXMLLoader before ExplorerContext is attached,
        // so any services that depend on context/service instances must be initialized here.
        //
        // These were previously built in the constructor (when MainController owned the context) and are
        // required for directory listing, TreeView population, and icon/metadata loading.
        this.displayService = this.treeBuildService;

        if (this.listingService == null) {
            this.listingService = new DirectoryListingService(this.ioExecutor, this.fileMetadataService);
        }
        if (this.directoryLoadManager == null) {
            this.directoryLoadManager = new DirectoryLoadManager(this.context, this.listingService, this.ioExecutor);
        }
        if (this.directoryCoordinator == null) {
            this.directoryCoordinator = new DirectoryCoordinator(this.eventBus, this.directoryLoadManager);
        }
// If MainApp already provided a Scene, apply the theme now that ThemeService is available.
    Scene s = this.boundScene;
    if (s != null) {
        javafx.application.Platform.runLater(() -> applyThemeToCurrentScene(s));
    }
    // If FXML initialize() already ran, complete deferred initialization now on the FX thread.
    if (this.fxmlInitialized) {
        javafx.application.Platform.runLater(this::initializeWithContext);
    }
}


    private void configureToolbarActions() {
    LogSupport.enter(LOG, "configureToolbarActions");

    // New menu: bind "Folder" to createNewFolder(). Other templates can be wired later.
    if (newMenuButton != null) {
        newMenuButton.getItems().forEach(mi -> {
            String t = mi.getText();
            if (t == null) return;
            if (t.equalsIgnoreCase("Folder")) {
                mi.setOnAction(e -> createNewFolder());
            }
        });
    }

    if (cutButton != null) {
        cutButton.setOnAction(e -> cutSelection());
    }
    if (copyButton != null) {
        copyButton.setOnAction(e -> copySelection());
    }
    if (pasteButton != null) {
        pasteButton.setOnAction(e -> pasteIntoCurrentFolder());
    }
    if (renameButton != null) {
        renameButton.setOnAction(e -> renameSelection());
    }

    // Optional / not yet implemented features in this codebase:
    if (shareButton != null) {
        shareButton.setOnAction(e -> setStatus("Share: not implemented yet."));
    }
    if (deleteButton != null) {
            deleteButton.setOnAction(e -> moveSelectionToTrash());
    }

    // sortMenuButton and viewMenuButton actions are handled by their MenuItems' onAction in FXML.
}

@Override
public void initialize(URL location, ResourceBundle resources) {
    LogSupport.enter(LOG, "initialize");
    this.fxmlInitialized = true;

    // Phase 3.4.4: ExplorerContext is injected by MainApp via Lifecycle.attach(context) AFTER FXMLLoader construction.
    // JavaFX calls initialize() during load, so we must defer initialization that depends on context/services until attach().
    if (this.context == null) {
        return;
    }

    initializeWithContext();
}

private void initializeWithContext() {
        LogSupport.enter(LOG, "initializeWithContext");
        if (contextInitialized) {
            return;
        }
        contextInitialized = true;
        configureTree();
        configureTable();
        configureThemeToggle();
        configureBreadcrumbs();
        configureSearch();
        configureStatusBar();
        configureToolbarActions();
        configureViewMenu();
        configureIconActivation();

        // Phase 3.2: UI subscribes to directory load events
        localDisposables.add(eventBus.subscribe(DirectoryLoadSucceeded.class, e -> {
            if (e.requestId() != lastRequestedRequestId) return;
            if (lastRequestedDirectory != null && !lastRequestedDirectory.equals(e.directory())) return;
            applyDirectoryListing(e.directory(), e.children());
        }));
        localDisposables.add(eventBus.subscribe(DirectoryLoadFailed.class, e -> {
            if (e.requestId() != lastRequestedRequestId) return;
            if (lastRequestedDirectory != null && !lastRequestedDirectory.equals(e.directory())) return;
            handleDirectoryListingFailed(e.directory(), e.error());
        }));

        if (!SAFE_MODE) {
            Platform.runLater(() -> {
                try {
                    ensureVirtualIconViewsInstalled();
                    installIconScrollPaging();
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Deferred icon-view initialization failed", ex);
                }
            });
        }
        setViewMode(ViewMode.DETAILS);

        setStatus("Ready.");
    
    }


    public void setScene(Scene scene) {
        LogSupport.enter(LOG, "setScene");
        if (scene == null) {
            return;
        }

        
        this.boundScene = scene;
if (!zoomShortcutsInstalled) {
            installZoomShortcuts(scene);
            zoomShortcutsInstalled = true;
        }

        if (!explorerShortcutsInstalled) {
            installExplorerShortcuts(scene);
            installCtrlScrollViewShortcuts(scene);
            explorerShortcutsInstalled = true;
        }

        // Adopt startup font provided by MainApp (if present) so the first frame
        // renders at the intended size on HiDPI displays.
        if (scene.getRoot() != null) {
            Object startupFont = scene.getRoot().getProperties().get(PROP_UI_FONT_PX);
            if (startupFont instanceof Number n) {
                uiFontSizePx = n.doubleValue();
                clampUiFont();
            }
        }

        if (scene.getRoot() != null) {
            Object fam = scene.getRoot().getProperties().get(PROP_UI_FONT_FAMILY);
            if (fam instanceof String s && !s.isBlank()) {
                uiFontFamilyResolved = s;
            }
        }

        uiFontFamilyCss = buildUiFontFamilyCss(scene);


        applyUiFontSize(scene);
        applyThemeToCurrentScene(scene);

        Platform.runLater(() -> ensureStartupWindowSize(scene));
    }


    public void enterSafeMode() {
        LogSupport.enter(LOG, "enterSafeMode");
        // Force-disable potentially expensive behaviors so we can isolate startup/OOM triggers.
        hoverPrefetchEnabled = false;

        // Safe Mode: prevent any icon-grid/tile view initialization and heavy resource work.
        try {
            if (iconScroll != null) {
                iconScroll.setDisable(true);
            }
            if (viewExtraLargeIcons != null) viewExtraLargeIcons.setDisable(true);
            if (viewLargeIcons != null) viewLargeIcons.setDisable(true);
            if (viewMediumIcons != null) viewMediumIcons.setDisable(true);
            if (viewSmallIcons != null) viewSmallIcons.setDisable(true);
            if (viewList != null) viewList.setDisable(true);
            if (viewTiles != null) viewTiles.setDisable(true);
            if (viewContent != null) viewContent.setDisable(true);
        } catch (Exception ignore) {
        }
        // Keep the lightest view by default.
        try {
            setViewMode(ViewMode.DETAILS);
        } catch (Exception ex) {
            // ignore
        }
        setStatus("Safe Mode enabled: hover prefetch, tree auto-expansion, icon preloading, and initial directory load are disabled.");
    }

    public void openInitialFolder(Path initialFolder) {
        LogSupport.enter(LOG, "openInitialFolder");
        if (SAFE_MODE && !Boolean.getBoolean("fileexplorer.safeMode.allowInitialDirectoryLoad")) {
            setStatus("Safe Mode enabled: initial directory load is disabled. "
                + "To load the initial directory anyway, run with -Dfileexplorer.safeMode.allowInitialDirectoryLoad=true.");
            return;
        }
        if (SAFE_MODE) {
            setStatus("Safe Mode enabled: initial directory load override is ON.");
        }
        if (initialFolder == null) {
            return;
        }
        Path target = initialFolder.normalize();
        Platform.runLater(() -> navigateToFolder(target, false));
    }

    // ---------------------------------------------------------------------
    // FXML actions
    // ---------------------------------------------------------------------

    @FXML
    private void onViewDetails(ActionEvent e) {
        LogSupport.enter(LOG, "onViewDetails");
        setViewMode(ViewMode.DETAILS);
    }

    @FXML
    private void onViewLargeIcons(ActionEvent e) {
        LogSupport.enter(LOG, "onViewLargeIcons");
        setViewMode(ViewMode.LARGE_ICONS);
    }

    @FXML
    private void onDetailsToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onDetailsToggle");
        if (detailsToggle != null && detailsToggle.isSelected()) {
            setViewMode(ViewMode.DETAILS);
        } else {
            setViewMode(lastIconViewMode == null ? ViewMode.MEDIUM_ICONS : lastIconViewMode);
        }
    }

    @FXML
    private void onViewModeRadio(ActionEvent e) {
        LogSupport.enter(LOG, "onViewModeRadio");
        if (e == null || e.getSource() == null) {
            return;
        }

        Object src = e.getSource();
        if (src instanceof RadioButton item) {
            Object ud = item.getUserData();
            String mode = ud == null ? "" : String.valueOf(ud).trim();
            ViewMode parsed = parseViewMode(mode);
            if (parsed != null) {
                setViewMode(parsed);
            }
        }
    }

    @FXML
    private void onDetailsPaneRadioToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onDetailsPaneRadioToggle");
        boolean show = detailsPaneMenuItem != null && detailsPaneMenuItem.isSelected();
        setDetailsPaneVisible(show);
    }

    @FXML
    private void onPreviewPaneRadioToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onPreviewPaneRadioToggle");
        boolean show = previewPaneMenuItem != null && previewPaneMenuItem.isSelected();
        setPreviewPaneVisible(show);
    }

    @FXML
    private void onShowNavigationPaneToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onShowNavigationPaneToggle");
        boolean show = showNavigationPaneMenuItem != null && showNavigationPaneMenuItem.isSelected();
        setNavigationPaneVisible(show);
    }

    @FXML
    private void onCompactViewToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onCompactViewToggle");
        boolean on = showCompactViewMenuItem != null && showCompactViewMenuItem.isSelected();
        setCompactView(on);
    }

    @FXML
    private void onItemCheckBoxesToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onItemCheckBoxesToggle");
        // Present in the menu for parity with File Explorer; behavior can be expanded later.
        boolean on = showItemCheckBoxesMenuItem != null && showItemCheckBoxesMenuItem.isSelected();
        showItemCheckBoxes = on;
    }

    @FXML
    private void onFileNameExtensionsToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onFileNameExtensionsToggle");
        boolean on = showFileNameExtensionsMenuItem != null && showFileNameExtensionsMenuItem.isSelected();
        showFileNameExtensions = on;
        refreshCurrentDirectoryView();
    }

    @FXML
    private void onHiddenItemsToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onHiddenItemsToggle");
        boolean on = showHiddenItemsMenuItem != null && showHiddenItemsMenuItem.isSelected();
        showHiddenItems = on;
        refreshCurrentDirectoryView();
    }

    private ViewMode parseViewMode(String s) {
        LogSupport.enter(LOG, "parseViewMode");
        if (s == null) {
            return null;
        }
        String v = s.trim().toUpperCase(Locale.ROOT);
        try {
            return ViewMode.valueOf(v);
        } catch (Exception ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Shortcuts
    // ---------------------------------------------------------------------

    private void installExplorerShortcuts(Scene scene) {
        LogSupport.enter(LOG, "installExplorerShortcuts");
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            KeyCode code = e.getCode();

            // Win + E (best-effort; OS may intercept)
            if (e.isMetaDown() && code == KeyCode.E) {
                openNewWindow();
                e.consume();
                return;
            }

            // Alt + Enter: Properties
            if (e.isAltDown() && !e.isShiftDown() && code == KeyCode.ENTER) {
                openPropertiesForSelection();
                e.consume();
                return;
            }

            // Alt + P: toggle Preview pane
            if (e.isAltDown() && !e.isShiftDown() && code == KeyCode.P) {
                togglePreviewPane();
                e.consume();
                return;
            }

            // Alt + Shift + P: toggle Details pane
            if (e.isAltDown() && e.isShiftDown() && code == KeyCode.P) {
                toggleDetailsPane();
                e.consume();
                return;
            }

            // Alt + Up: Up one folder
            if (e.isAltDown() && code == KeyCode.UP) {
                navigateUp();
                e.consume();
                return;
            }

            // Backspace: Up one folder (Explorer-style). Do not steal Backspace from text inputs.
            if (!e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown() && code == KeyCode.BACK_SPACE) {
                if (!(e.getTarget() instanceof TextInputControl)) {
                    navigateUp();
                    e.consume();
                    return;
                }
            }

            // Alt + Left/Right: Back/Forward
            if (e.isAltDown() && code == KeyCode.LEFT) {
                navigateBack();
                e.consume();
                return;
            }
            if (e.isAltDown() && code == KeyCode.RIGHT) {
                navigateForward();
                e.consume();
                return;
            }

            // Ctrl + A: Select all
            if (e.isControlDown() && code == KeyCode.A) {
                selectAll();
                e.consume();
                return;
            }

            // Ctrl + C: Copy
            if (e.isControlDown() && !e.isShiftDown() && code == KeyCode.C) {
                copySelection();
                e.consume();
                return;
            }

            // Ctrl + X: Cut
            if (e.isControlDown() && !e.isShiftDown() && code == KeyCode.X) {
                cutSelection();
                e.consume();
                return;
            }

            // Ctrl + V: Paste
            if (e.isControlDown() && !e.isShiftDown() && code == KeyCode.V) {
                pasteIntoCurrentFolder();
                e.consume();
                return;
            }

            // Ctrl + N: New window
            if (e.isControlDown() && !e.isShiftDown() && code == KeyCode.N) {
                openNewWindow();
                e.consume();
                return;
            }

            // Ctrl + Shift + N: New folder
            if (e.isControlDown() && e.isShiftDown() && code == KeyCode.N) {
                createNewFolder();
                e.consume();
                return;
            }

            // Ctrl + Shift + E: Expand all in navigation pane
            if (e.isControlDown() && e.isShiftDown() && code == KeyCode.E) {
                expandAllFoldersInNavigationPane();
                e.consume();
                return;
            }

            // Ctrl + Shift + C: Collapse all in navigation pane
            if (e.isControlDown() && e.isShiftDown() && code == KeyCode.C) {
                collapseAllFoldersInNavigationPane();
                e.consume();
                return;
            }

            // Ctrl + W: Close window/tab (window for now)
            if (e.isControlDown() && !e.isShiftDown() && code == KeyCode.W) {
                closeCurrentWindow();
                e.consume();
                return;
            }

            // F11: Full screen toggle
            if (!e.isAltDown() && !e.isControlDown() && code == KeyCode.F11) {
                toggleFullScreen();
                e.consume();
                return;
            }

            // F2: Rename
            if (!e.isAltDown() && !e.isControlDown() && code == KeyCode.F2) {
                renameSelection();
                e.consume();
                return;
            }

            // F3 OR Ctrl + F: Search
            if ((!e.isAltDown() && !e.isControlDown() && code == KeyCode.F3)
                    || (e.isControlDown() && !e.isShiftDown() && code == KeyCode.F)) {
                focusSearch();
                e.consume();
                return;
            }

            // F4 OR Alt + D OR Ctrl + L: Address bar (breadcrumbs)
            if ((!e.isAltDown() && !e.isControlDown() && code == KeyCode.F4)
                    || (e.isAltDown() && code == KeyCode.D)
                    || (e.isControlDown() && !e.isShiftDown() && code == KeyCode.L)) {
                focusAddressBar();
                e.consume();
                return;
            }

            // F5: Refresh
            if (!e.isAltDown() && !e.isControlDown() && code == KeyCode.F5) {
                refresh();
                e.consume();
                return;
            }

            // F6: cycle focus through panes
            if (!e.isAltDown() && !e.isControlDown() && code == KeyCode.F6) {
                cyclePanesFocus();
                e.consume();
                return;
            }

            // Home/End: scroll top/bottom of view (table or icon view)
            if (!e.isAltDown() && !e.isControlDown() && code == KeyCode.HOME) {
                scrollToTop();
                e.consume();
                return;
            }
            if (!e.isAltDown() && !e.isControlDown() && code == KeyCode.END) {
                scrollToBottom();
                e.consume();
            }
        });
    }

    
        private void installCtrlScrollViewShortcuts(Scene scene) {
            LogSupport.enter(LOG, "installCtrlScrollViewShortcuts");
        scene.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (!e.isControlDown()) {
                return;
            }

            double dy = e.getDeltaY();
            if (dy == 0.0) {
                return;
            }

            // Ctrl + scroll: change view or icon size
            if (isTableMode(viewMode)) {
                LogSupport.enter(LOG, "isTableMode");
                setViewMode(lastIconViewMode == null ? ViewMode.MEDIUM_ICONS : lastIconViewMode);
                e.consume();
                return;
            }

            if (!isIconMode(viewMode)) {
                LogSupport.enter(LOG, "isIconMode");
                return;
            }

            if (dy > 0) {
                iconSizePx = iconSizePx + ICON_SIZE_STEP;
                clampIconSize();
                if (!SAFE_MODE && isIconMode(viewMode)) {
            rebuildIconTiles();
        } else {
            clearIconTiles();
        }
                setStatus("Icon size: " + (int) iconSizePx + "px");
                e.consume();
                return;
            }

            if (iconSizePx <= ICON_SIZE_MIN + 0.01) {
                setViewMode(ViewMode.DETAILS);
                e.consume();
                return;
            }

            iconSizePx = iconSizePx - ICON_SIZE_STEP;
            clampIconSize();
            rebuildIconTiles();
            setStatus("Icon size: " + (int) iconSizePx + "px");
            e.consume();
        });
    }

    // ---------------------------------------------------------------------
    // Zoom (existing)
    // ---------------------------------------------------------------------

    private void installZoomShortcuts(Scene scene) {
        LogSupport.enter(LOG, "installZoomShortcuts");
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

            // Ctrl+- :
            if (code == KeyCode.MINUS || code == KeyCode.SUBTRACT) {
                adjustUiFontSize(-UI_FONT_STEP_PX);
                applyUiFontSize(scene);
                e.consume();
                return;
            }

            // Ctrl+0 :
            if (code == KeyCode.DIGIT0 || code == KeyCode.NUMPAD0) {
                uiFontSizePx = UI_FONT_DEFAULT_PX;
                applyUiFontSize(scene);
                setStatus("UI size reset");
                e.consume();
            }
        });
    }

    
    private void clampUiFont() {
        LogSupport.enter(LOG, "clampUiFont");
        uiFontSizePx = clamp(uiFontSizePx, UI_FONT_MIN_PX, UI_FONT_MAX_PX);
    }

private void adjustUiFontSize(double deltaPx) {
    LogSupport.enter(LOG, "adjustUiFontSize");
        uiFontSizePx = clamp(uiFontSizePx + deltaPx, UI_FONT_MIN_PX, UI_FONT_MAX_PX);
        setStatus("UI size: " + (int) uiFontSizePx + "px");
    }

    private void applyUiFontSize(Scene scene) {
        LogSupport.enter(LOG, "applyUiFontSize");
        if (scene == null || scene.getRoot() == null) {
            return;
        }

        if (uiFontFamilyCss == null || uiFontFamilyCss.isBlank()) {
            uiFontFamilyCss = buildUiFontFamilyCss(scene);
        }

        // Preserve any other inline styles that may already be present on the root.
        String style = scene.getRoot().getStyle();
        if (style == null) {
            style = "";
        }

        // Remove any prior -fx-font-size declarations.
        style = style.replaceAll("(?i)-fx-font-size\s*:\s*[^;]+;?", "").trim();
        if (!style.isEmpty() && !style.endsWith(";")) {
            style = style + ";";
        }

        // Ensure a text font-family chain is present on the root (prevents icon fonts from leaking into controls).
        if (!style.toLowerCase(Locale.ROOT).contains("-fx-font-family")) {
            style = style + " -fx-font-family: " + uiFontFamilyCss + ";";
        }

        style = style + " -fx-font-size: " + uiFontSizePx + "px;";
        scene.getRoot().setStyle(style);

        // Tree: use a text font-family chain explicitly, and keep size close to base to avoid clipped rows.
        if (folderTree != null) {
            ensureTreeViewStyleClass();

            double treeFontPx = Math.max(UI_FONT_MIN_PX, uiFontSizePx * 1.10);
            treeFontSizePxApplied = treeFontPx;

            String treeStyle = "-fx-font-family: " + uiFontFamilyCss + "; -fx-font-size: " + treeFontPx + "px;";
            folderTree.setStyle(treeStyle);

            // Ensure row height tracks font size (prevents clipped glyphs on HiDPI).
            double rowH = Math.ceil(treeFontPx + (UI_MIN_VPAD_PX * 2.0));
            folderTree.setFixedCellSize(rowH);
            folderTree.refresh();
        }

        // Table: keep base font; headers/rows will follow.
        if (fileTable != null) {
            fileTable.setStyle("-fx-font-family: " + uiFontFamilyCss + "; -fx-font-size: " + uiFontSizePx + "px;");

            // Enforce: row height must be at least (font size + 5px top + 5px bottom).
            double tableRowH = Math.ceil(uiFontSizePx + (UI_MIN_VPAD_PX * 2.0));
            fileTable.setFixedCellSize(tableRowH);
            if (folderTree != null) {
                folderTree.setFixedCellSize(tableRowH);
            }

            Platform.runLater(() -> applyTableHeaderMetrics(tableRowH));
        }

        // Apply the same minimum vertical metric policy to other key controls.
        applyMinimumMetrics(scene, uiFontSizePx);
    }

    
    private void applyTableHeaderMetrics(double headerAndRowHeightPx) {
        LogSupport.enter(LOG, "applyTableHeaderMetrics");
        if (fileTable == null) {
            return;
        }

        Node headerBg = fileTable.lookup(".column-header-background");
        if (headerBg instanceof Region region) {
            region.setMinHeight(headerAndRowHeightPx);
            region.setPrefHeight(headerAndRowHeightPx);
        }

        for (Node n : fileTable.lookupAll(".column-header")) {
            if (n instanceof Region r) {
                r.setMinHeight(headerAndRowHeightPx);
                r.setPrefHeight(headerAndRowHeightPx);
            }
        }
        for (Node n : fileTable.lookupAll(".filler")) {
            if (n instanceof Region r) {
                r.setMinHeight(headerAndRowHeightPx);
                r.setPrefHeight(headerAndRowHeightPx);
            }
        }
    }

    private void applyMinimumMetrics(Scene scene, double fontPx) {
        LogSupport.enter(LOG, "applyMinimumMetrics");
        if (scene == null || scene.getRoot() == null) {
            return;
        }

        double minH = Math.ceil(fontPx + (UI_MIN_VPAD_PX * 2.0));

        // Toolbars (slightly taller to accommodate icons)
        double barH = Math.ceil(fontPx + (UI_MIN_VPAD_PX * 2.0) + 8.0);
        for (Node n : scene.getRoot().lookupAll(".tool-bar")) {
            if (n instanceof Region r) {
                if (r.getMinHeight() < barH) {
                    r.setMinHeight(barH);
                }
                if (r.getPrefHeight() < barH) {
                    r.setPrefHeight(barH);
                }
            }
        }

        // Common interactive controls: enforce minimum height.
        enforceMinHeight(scene, ".button", minH);
        enforceMinHeight(scene, ".toggle-button", minH);
        enforceMinHeight(scene, ".menu-button", minH);
        enforceMinHeight(scene, ".split-menu-button", minH);
        enforceMinHeight(scene, ".text-field", minH);
        enforceMinHeight(scene, ".combo-box", minH);
        enforceMinHeight(scene, ".choice-box", minH);
        enforceMinHeight(scene, ".spinner", minH);
        enforceMinHeight(scene, ".check-box", minH);
        enforceMinHeight(scene, ".radio-button", minH);
        enforceMinHeight(scene, ".hyperlink", minH);

        // Status bar container
        Node status = scene.getRoot().lookup(".status-bar");
        if (status instanceof Region r) {
            double statusH = Math.ceil(fontPx + (UI_MIN_VPAD_PX * 2.0) + 10.0);
            if (r.getMinHeight() < statusH) {
                r.setMinHeight(statusH);
            }
            if (r.getPrefHeight() < statusH) {
                r.setPrefHeight(statusH);
            }
        }
    }

    private void enforceMinHeight(Scene scene, String selector, double minHeightPx) {
        LogSupport.enter(LOG, "enforceMinHeight");
        if (scene == null || scene.getRoot() == null || selector == null || selector.isBlank()) {
            return;
        }
        for (Node n : scene.getRoot().lookupAll(selector)) {
            if (n instanceof Region r) {
                if (r.getMinHeight() < minHeightPx) {
                    r.setMinHeight(minHeightPx);
                }
            }
        }
    }

private double clamp(double v, double lo, double hi) {
    LogSupport.enter(LOG, "clamp");
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    // ---------------------------------------------------------------------
    // Tree + Table
    // ---------------------------------------------------------------------
    private void configureTree() {
        LogSupport.enter(LOG, "configureTree");
        ensureTreeViewStyleClass();

        folderTree.setShowRoot(true);

        // Enforce fixed cell sizes early (VirtualFlow runaway mitigation).
        // NOTE: Fixed cell size alone is NOT sufficient if the control's preferred height is allowed to
        // scale with item count. We also bound preferred sizes below.
        enforceStartupFixedCellSizes();

        // Guard against pathological preferred-size calculations of virtualized controls.
        // Without an explicit prefHeight/prefWidth, TreeView can prefer to size to *all* expanded items,
        // which, during Stage.show() sizing/layout, can drive VirtualFlow into creating an unbounded
        // number of cells and trigger OOM.
        enforceVirtualizedPrefSize(folderTree,
                "fileexplorer.ui.tree.prefHeight", 720,
                "fileexplorer.ui.tree.prefWidth", 320);

        
        // Enforce navigation tree minimum width (prevents icon-only sliver).
        folderTree.setMinWidth(NAV_TREE_MIN_WIDTH_PX);
        folderTree.setPrefWidth(Math.max(folderTree.getPrefWidth(), NAV_TREE_PREF_WIDTH_PX));

// Reset per-run counters used for runaway detection.
        TREE_CELL_CREATED.set(0);

        final boolean safeMode = SAFE_MODE;
        final boolean hoverPrefetchEnabled = !safeMode && Boolean.parseBoolean(System.getProperty("fileexplorer.ui.hoverPrefetch", "false"));
        final int treeFixedCellSize = (int) Math.round(folderTree.getFixedCellSize());

        // Build the real root off the FX thread to avoid freezing the initial render.
        folderTree.setShowRoot(false);

        // Show a placeholder root immediately (not shown since showRoot=false).
        TreeItem<Path> placeholderRoot = new TreeItem<>(Paths.get("/"));
        folderTree.setRoot(placeholderRoot);
        placeholderRoot.setExpanded(false);

        ioExecutor.execute(() -> {
            TreeItem<Path> root = treeBuildService.buildComputerRoot();
            Platform.runLater(() -> {
                folderTree.setRoot(root);
                folderTree.setShowRoot(false);

                if (!root.getChildren().isEmpty()) {
                    folderTree.getSelectionModel().select(root.getChildren().get(0));
                }
            });
        });

folderTree.setCellFactory(tv -> {
            int created = TREE_CELL_CREATED.incrementAndGet();
            int maxCells = Integer.getInteger("fileexplorer.ui.tree.maxCells", 5000);
            if (created > maxCells) {
                LOG.severe(() -> "TreeCell runaway detected: created=" + created + " max=" + maxCells
                        + " (hint: set -Dfileexplorer.ui.tree.prefHeight=<px> to bound preferred sizing)");
                throw new IllegalStateException("TreeCell runaway detected (created=" + created + ")");
            }

            if (safeMode) {
                return new SimplePathTreeCell(treeFixedCellSize, treeBuildService);
            }
            return new IconPathTreeCell(treeFixedCellSize, themeService, treeBuildService);
        });

        

        configureTreeContextMenu();
// Optional hover-based prefetch (disabled in safe mode by default).
        if (hoverPrefetchEnabled) {
            folderTree.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
                Node n = e.getPickResult() != null ? e.getPickResult().getIntersectedNode() : null;
                while (n != null && !(n instanceof TreeCell<?>)) {
                    n = n.getParent();
                }
                if (n instanceof TreeCell<?> cell) {
                    Object item = cell.getItem();
                    if (item instanceof Path p) {
                        scheduleHoverPrefetch(p);
                    }
                }
            });
        }

        folderTree.getSelectionModel().selectedItemProperty().addListener((_, _, newItem) -> {
            if (newItem == null) {
                return;
            }
            Path p = newItem.getValue();
            if (p == null) {
                return;
            }

            // UX: selecting a directory should expand it even if it only has a single child.
            if (!newItem.isExpanded() && !newItem.isLeaf()) {
                newItem.setExpanded(true);
            }
            if (SAFE_MODE) {
                // In safe mode, do not auto-load the directory on selection; user can activate explicitly.
                setStatus("Safe mode: directory load disabled; activate/open to navigate.");
                return;
            }
            // Selection-driven navigation should update the main listing, but should not
            // aggressively mutate history (double-click/Enter does that explicitly).
            loadDirectoryIntoTableAsync(p);
        });

        // Double-click activates (single handler; avoids per-cell listeners).
        folderTree.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2) {
                return;
            }
            TreeItem<Path> sel = folderTree.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getValue() == null) {
                return;
            }
            if (SAFE_MODE) {
                setStatus("Safe mode: activation blocked (directory load disabled).");
                return;
            }
            navigateToFolder(sel.getValue(), true);
        });

        folderTree.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                TreeItem<Path> sel = folderTree.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getValue() != null) {
                    if (SAFE_MODE) {
                        setStatus("Safe mode: activation blocked (directory load disabled).");
                    } else {
                        navigateToFolder(sel.getValue(), true);
                    }
                }
                e.consume();
            }
        });

        // The existing MOUSE_PRESSED selection handler is preserved (prevents null selection glitches).
        folderTree.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Node node = e.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof TreeCell)) {
                node = node.getParent();
            }
            if (node instanceof TreeCell<?> cell) {
                TreeItem<?> item = cell.getTreeItem();
                if (item != null) {
                    folderTree.getSelectionModel().select((TreeItem<Path>) item);
                    e.consume();
                }
            }
        });

        folderTree.setOnDragOver(this::onTreeDragOver);
        folderTree.setOnDragDropped(this::onTreeDragDropped);

        folderTree.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    /**
     * Conservative drag-over handler for the navigation tree.
     *
     * This exists primarily to keep wiring stable across iterations. For now, it only
     * advertises COPY support when the dragboard contains files. (Safe mode disables DnD.)
     */
    private void onTreeDragOver(javafx.scene.input.DragEvent event) {
        if (event == null) {
            return;
        }
        if (SAFE_MODE) {
            event.consume();
            return;
        }
        var db = event.getDragboard();
        if (db != null && db.hasFiles()) {
            event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
        }
        event.consume();
    }

    /**
     * Conservative drag-dropped handler for the navigation tree.
     *
     * The full file-operation behavior is intentionally disabled here to avoid introducing
     * additional I/O/memory side effects while isolating the current OOM/regression.
     */
    private void onTreeDragDropped(javafx.scene.input.DragEvent event) {
        if (event == null) {
            return;
        }
        event.setDropCompleted(false);
        event.consume();
    }



    private void configureIconActivation() {
        LogSupport.enter(LOG, "configureIconActivation");
        // Enter key should activate the currently selected item in icon-based views.
        // Icon tiles synchronize selection into the TableView selection model.
        if (iconScroll != null) {
            iconScroll.setFocusTraversable(true);
            iconScroll.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    activateFromTableSelection();
                    e.consume();
                }
            });
        }
        if (iconFlow != null) {
            iconFlow.setFocusTraversable(true);
            iconFlow.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    activateFromTableSelection();
                    e.consume();
                }
            });
        }
    }

    private void activateFromTableSelection() {
        LogSupport.enter(LOG, "activateFromTableSelection");
        if (fileTable == null) {
            return;
        }
        FileItem selectedItem = fileTable.getSelectionModel().getSelectedItem();
        Path selected = (selectedItem != null) ? selectedItem.path() : null;
        if (selected == null) {
            return;
        }
        if (Files.isDirectory(selected)) {
            navigateToFolder(selected, true);
        }
    }

    private boolean consumeTreeSelectionUserInitiated() {
        if (treeSelectionUserInitiated) {
            treeSelectionUserInitiated = false;
            return true;
        }
        return false;
    }


        private static Label createStatusCheckIcon(Color color) {
        Label icon = new Label("\uE73E"); // Fluent CheckMark (outline)
        icon.setFont(Font.font("Segoe Fluent Icons", 14));
        icon.setTextFill(color);
        icon.setMinWidth(18);
        icon.setPrefWidth(18);
        icon.setAlignment(Pos.CENTER);
        return icon;
    }


    /**
     * Phase 3.5.1: Tree context menu (Refresh re-probes the selected node).
     */
    private void configureTreeContextMenu() {
        if (folderTree == null) {
            return;
        }
        folderTree.setOnContextMenuRequested(ev -> {
            try {
                TreeItem<java.nio.file.Path> sel = folderTree.getSelectionModel().getSelectedItem();
                if (sel == null) {
                    return;
                }
                javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
                javafx.scene.control.MenuItem refreshItem = new javafx.scene.control.MenuItem("Refresh");
                refreshItem.setOnAction(ae -> {
                    if (sel instanceof com.fileexplorer.service.filesystem.TreeBuildService.LazyLoadingTreeItem lazy) {
                        lazy.invalidate();
                    }
                    if (sel.getValue() != null && sel.getValue().equals(currentDirectory)) {
                        refresh();
                    }
                });
                menu.getItems().add(refreshItem);
                menu.show(folderTree, ev.getScreenX(), ev.getScreenY());
                ev.consume();
            } catch (Exception ex) {
                // ignore
            }
        });
    }

private void configureTable() {
        LogSupport.enter(LOG, "configureTable");

        // Default-enabled guard: cap preferred height/width to prevent runaway layout sizing.
        enforceVirtualizedPrefSize(fileTable, "fileexplorer.ui.table.prefHeight", 720,
                "fileexplorer.ui.table.prefWidth", -1);
        // Stretch columns to the end of the container (last column flexes).
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
fileTable.setItems(filteredTableItems);
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Step 7: delegate TableView wiring
        TableViewSupport.configure(
                context,
                fileTable,
                colName,
                colStatus,
                colType,
                colSize,
                colModified,
                fi -> displayNameForTable(fi),
                fi -> fileMetadataService.detectFileType(fi.path()),
                fi -> fileMetadataService.humanReadableSize(fi.path()),
                fi -> fileMetadataService.lastModifiedLocalString(fi.path())
        );

        colName.setCellValueFactory(param -> {
            FileItem fi = param.getValue();
            Path p = (fi != null) ? fi.path() : null;
            String name = displayNameForTable(p);
            return new ReadOnlyObjectWrapper<>(name);
        });

        colName.setCellFactory(_ -> new TableCell<>() {
            private final HBox box;
            private final ImageView iconView;
            private final Label textLabel;

            {
                this.box = new HBox(10.0);
                this.box.setAlignment(Pos.CENTER_LEFT);

                this.iconView = new ImageView();
                this.iconView.setPreserveRatio(true);
                this.iconView.setSmooth(true);

                this.textLabel = new Label();
                this.textLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

                this.box.getChildren().addAll(iconView, textLabel);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                LogSupport.enter(LOG, "updateItem2");
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                FileItem fi = (getTableRow() != null) ? getTableRow().getItem() : null;
                Path p = (fi != null) ? fi.path() : null;

                double iconPx = clamp(uiFontSizePx + 4.0, 16.0, 24.0);
                Image img;
                try {
                    String identity = fileMetadataService.iconIdentity(p);
                    img = IconLoader.loadForIdentity(identity, themeService.isDarkPreferred(), (int) Math.round(iconPx));
                } catch (Exception ex) {
                    img = IconLoader.load(IconLoader.IconType.FILE, themeService.isDarkPreferred(), (int) Math.round(iconPx));
                }

                iconView.setFitWidth(iconPx);
                iconView.setFitHeight(iconPx);
                iconView.setImage(img);

                textLabel.setText(item);

                setText(null);
                setGraphic(box);
            }
        });

        
// Status column (icon-only): check outline placeholder with hover/selection tint.
if (colStatus != null) {
    colStatus.setSortable(false);
    colStatus.setReorderable(false);

    final Color normal = Color.web("#6CCB5F");
    final Color hover = Color.web("#86E173");
    final Color selected = Color.web("#FFFFFF");

    colStatus.setCellFactory(_ -> new TableCell<>() {
        private Label icon;

        private void syncTint() {
            if (icon == null) return;
            TableRow<FileItem> row = getTableRow();
            if (row != null && row.isSelected()) {
                icon.setTextFill(selected);
            } else if (row != null && row.isHover()) {
                icon.setTextFill(hover);
            } else {
                icon.setTextFill(normal);
            }
        }

        @Override
        protected void updateItem(Node item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                icon = null;
                return;
            }
            if (icon == null) {
                icon = createStatusCheckIcon(normal);
                tableRowProperty().addListener((obs, oldR, newR) -> syncTint());
            }
            setGraphic(icon);
            setText(null);
            syncTint();
        }
    });
}

colType.setCellValueFactory(param -> {
            FileItem fi = param.getValue();
            Path p = (fi != null) ? fi.path() : null;
            String type = fileMetadataService.detectFileType(p);
            return new ReadOnlyObjectWrapper<>(type);
        });

        colSize.setCellValueFactory(param -> {
            FileItem fi = param.getValue();
            Path p = (fi != null) ? fi.path() : null;
            String size = fileMetadataService.humanReadableSize(p);
            return new ReadOnlyObjectWrapper<>(size);
        });

        // Right align Size
        colSize.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                LogSupport.enter(LOG, "updateItem3");
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });

        colModified.setCellValueFactory(param -> {
            FileItem fi = param.getValue();
            Path p = (fi != null) ? fi.path() : null;
            String mod = fileMetadataService.lastModifiedLocalString(p);
            return new ReadOnlyObjectWrapper<>(mod);
        });

        fileTable.getSelectionModel().selectedItemProperty().addListener((_, _, newSel) ->
            updateSelectionDetails(newSel != null ? newSel.path() : null));
    

        // Activate folders on double-click and Enter (Details/List views use the TableView).
        fileTable.setRowFactory(_ -> {
            TableRow<FileItem> row = new TableRow<>();

            row.setOnMouseClicked(me -> {
                if (me.getButton() == MouseButton.PRIMARY && me.getClickCount() == 2 && !row.isEmpty()) {
                    FileItem fi = row.getItem();
                    Path p = (fi != null) ? fi.path() : null;
                    lastIconActivatedPath = p;
                    if (p != null && Files.isDirectory(p)) {
                        navigateToFolder(p, true);
                    }
                }
            });

            // Hover prefetch: warm icon + metadata caches for the hovered item.
            row.hoverProperty().addListener((_, _, isHover) -> {
                if (isHover && !row.isEmpty()) {
                    FileItem fi2 = row.getItem();
                    scheduleHoverPrefetch(fi2 != null ? fi2.path() : null);
                }
            });

            return row;
        });

        
    fileTable.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
        // Keyboard parity: RIGHT enters folder; LEFT goes to parent; ENTER enters folder.
        if (e.getCode() == KeyCode.LEFT && !e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
            navigateUp();
            e.consume();
            return;
        }

        if (e.getCode() == KeyCode.RIGHT && !e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
            FileItem selItem = fileTable.getSelectionModel().getSelectedItem();
        Path sel = (selItem != null) ? selItem.path() : null;
            if (sel != null && Files.isDirectory(sel)) {
                navigateToFolder(sel, true);
                e.consume();
            }
            return;
        }

        if (e.getCode() == KeyCode.ENTER) {
            FileItem selItem = fileTable.getSelectionModel().getSelectedItem();
        Path sel = (selItem != null) ? selItem.path() : null;
            if (sel != null && Files.isDirectory(sel)) {
                navigateToFolder(sel, true);
                e.consume();
            }
        }
    });
}
    // ---------------------------------------------------------------------
    // Hover prefetch (Explorer-style)
    // ---------------------------------------------------------------------

    private void scheduleHoverPrefetch(Path p) {
        LogSupport.enter(LOG, "scheduleHoverPrefetch");
        if (!hoverPrefetchEnabled) {
            return;
        }
        if (p == null) {
            return;
        }

        hoverPrefetchTarget = p;
        hoverPrefetchSeq.incrementAndGet();

        if (hoverPrefetchTimer == null) {
            hoverPrefetchTimer = new PauseTransition(HOVER_PREFETCH_DELAY);
            hoverPrefetchTimer.setOnFinished(_ -> {
                Path target = hoverPrefetchTarget;
                long expected = hoverPrefetchSeq.get();
                if (!hoverPrefetchEnabled || target == null) {
                    return;
                }
                hoverPrefetchExecutor.execute(() -> runHoverPrefetch(target, expected));
            });
        } else {
            hoverPrefetchTimer.setDuration(HOVER_PREFETCH_DELAY);
        }

        hoverPrefetchTimer.stop();
        hoverPrefetchTimer.playFromStart();
    }

    private void runHoverPrefetch(Path target, long expectedSeq) {
        LogSupport.enter(LOG, "runHoverPrefetch");
        try {
            if (!hoverPrefetchEnabled) {
                return;
            }
            if (expectedSeq != hoverPrefetchSeq.get()) {
                return;
            }
            if (!Objects.equals(target, hoverPrefetchTarget)) {
                return;
            }

            String identity = null;
            try {
                if (fileMetadataService != null) {
                    identity = fileMetadataService.iconIdentity(target);
                }
            } catch (Exception ex) {
                identity = null;
            }
            if (identity == null || identity.isBlank()) {
                identity = "type:" + IconLoader.IconType.FILE.name();
            }

            // Warm metadata caches (best effort). These calls must not touch UI state.
            try {
                if (fileMetadataService != null) {
                    fileMetadataService.detectFileType(target);
                }
            } catch (Exception ex) {
                // ignore
            }
            if (expectedSeq != hoverPrefetchSeq.get()) {
                return;
            }
            try {
                if (fileMetadataService != null) {
                    fileMetadataService.humanReadableSize(target);
                }
            } catch (Exception ex) {
                // ignore
            }
            if (expectedSeq != hoverPrefetchSeq.get()) {
                return;
            }
            try {
                if (fileMetadataService != null) {
                    fileMetadataService.lastModifiedLocalString(target);
                }
            } catch (Exception ex) {
                // ignore
            }

            if (expectedSeq != hoverPrefetchSeq.get()) {
                return;
            }

            boolean dark = themeService != null && themeService.isDarkPreferred();
            int treePx = (int) Math.round(clamp(effectiveTreeIconPx(), 16.0, 32.0));
            int iconPx = (int) Math.round(clamp(iconSizePx, 16.0, 128.0));

            int[] sizes = new int[] { 16, 20, 24, 32, 48, 64, 96, treePx, iconPx };
            for (int s : sizes) {
                int px = (int) Math.round(clamp((double) s, 16.0, 128.0));
                try {
                    IconLoader.loadForIdentity(identity, dark, px);
                } catch (Exception ex) {
                    // ignore
                }
                if (expectedSeq != hoverPrefetchSeq.get()) {
                    return;
                }
            }
        } catch (Exception ex) {
            // Prefetch is strictly best-effort; never fail UI operations due to prefetch errors.
        }
    }

private String displayNameForTable(Path p) {
    LogSupport.enter(LOG, "displayNameForTable");
    if (p == null) {
        return "";
    }

    String name;
    if (fileMetadataService != null) {
        try {
            name = fileMetadataService.displayName(p);
        } catch (Exception ex) {
            name = null;
        }
    } else {
        name = null;
    }

    if (name == null || name.isBlank()) {
        Path fn = p.getFileName();
        name = Objects.requireNonNullElse(fn, p).toString();
    }

    if (showFileNameExtensions) {
        return name;
    }

    int dot = name.lastIndexOf('.');
    if (dot <= 0) {
        return name;
    }
    return name.substring(0, dot);
}

    private String displayNameForTable(FileItem fi) {
        if (fi == null) {
            return "";
        }
        return displayNameForTable(fi.path());
    }


    private String typeForTable(Path p) {
        LogSupport.enter(LOG, "typeForTable");
        if (p == null) {
            return "";
        }
        if (fileMetadataService == null) {
            return safeFolderOrFileLabel(p);
        }
        try {
            String type = fileMetadataService.detectFileType(p);
            if (type != null && !type.isBlank()) {
                return type;
            }
        } catch (Exception ex) {
            // Ignore and fall back.
        }
        return safeFolderOrFileLabel(p);
    }

    private String sizeForTable(Path p) {
        LogSupport.enter(LOG, "sizeForTable");
        if (p == null) {
            return "";
        }
        try {
            if (Files.isDirectory(p)) {
                return "";
            }
        } catch (Exception ex) {
            // Ignore and attempt best-effort size.
        }

        if (fileMetadataService == null) {
            return "";
        }
        try {
            String size = fileMetadataService.humanReadableSize(p);
            return size == null ? "" : size;
        } catch (Exception ex) {
            return "";
        }
    }

    private String modifiedForTable(Path p) {
        LogSupport.enter(LOG, "modifiedForTable");
        if (p == null) {
            return "";
        }
        if (fileMetadataService == null) {
            return "";
        }
        try {
            String mod = fileMetadataService.lastModifiedLocalString(p);
            return mod == null ? "" : mod;
        } catch (Exception ex) {
            return "";
        }
    }

    private String safeFolderOrFileLabel(Path p) {
        LogSupport.enter(LOG, "safeFolderOrFileLabel");
        try {
            return Files.isDirectory(p) ? "Folder" : "File";
        } catch (Exception ex) {
            return "Item";
        }
    }



    private void configureBreadcrumbs() {
        LogSupport.enter(LOG, "configureBreadcrumbs");

        if (breadcrumbBarController instanceof com.fileexplorer.lifecycle.Lifecycle lc) {
            lc.attach(context);
        }
            if (breadcrumbBarController == null) {
                return;
            }

            breadcrumbBarController.setOnNavigate(path -> {
                if (path != null) {
                    navigateToFolder(path, true);
                }
            });

            breadcrumbBarController.setOnOpenInNewWindow(path -> {
                if (path != null) {
                    openNewWindow(path);
                }
            });

            breadcrumbBarController.setOnCopyAddress(path -> {
                if (path == null) {
                    return;
                }
                ClipboardContent cc = new ClipboardContent();
                cc.putString(path.toString());
                Clipboard.getSystemClipboard().setContent(cc);
                setStatus("Copied path.");
            });
        }


    /**
     * UI-thread handler invoked by the DirectoryCoordinator via the event bus.
     * Applies a completed directory listing to the table/icon views and updates chrome.
     */
    private void applyDirectoryListing(Path directory, java.util.List<com.fileexplorer.model.FileItem> listing) {
        if (directory == null) {
            return;
        }

        // Track & reflect current directory
        this.currentDirectory = directory;

        if (breadcrumbBarController != null) {
            breadcrumbBarController.setPath(directory);
        }

        if (statusLabel != null) {
            statusLabel.setText(fileMetadataService.displayPathForStatus(directory));
        }

        if (listing == null) {
            listing = java.util.List.of();
        }

        tableItems.setAll(listing);
        updateStatusCounts();
        rebuildTableIndexCache(listing);

        
        // Phase 3.5.1: Restore selection after refresh if possible.
        if (pendingRestoreSelection && pendingReselectPath != null) {
            int idx = -1;
            for (int i = 0; i < listing.size(); i++) {
                com.fileexplorer.model.FileItem it = listing.get(i);
                if (it != null && pendingReselectPath.equals(it.path())) {
                    idx = i;
                    break;
                }
            }
            if (fileTable != null) {
                if (idx >= 0) {
                    fileTable.getSelectionModel().clearAndSelect(idx);
                    fileTable.scrollTo(Math.max(0, idx - 2));
                } else if (pendingReselectIndex >= 0 && pendingReselectIndex < listing.size()) {
                    fileTable.scrollTo(Math.max(0, pendingReselectIndex - 2));
                }
            }
            pendingRestoreSelection = false;
            pendingReselectPath = null;
            pendingReselectIndex = -1;
        }
// Update view-specific UI
        if (isIconMode(viewMode)) {
            rebuildIconTiles();
        } else {
            if (fileTable != null) {
                fileTable.refresh();
            }
        }
    }

    /**
     * UI-thread handler invoked by the DirectoryCoordinator via the event bus.
     */

    // ---------------------------------------------------------------------
    // Phase 3.5.4: Search (fast filter within current folder)
    // ---------------------------------------------------------------------

    private void configureSearch() {
        if (searchField == null) {
            return;
        }
        // Keep the search box lightweight: debounce changes and filter the current listing in-memory.
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            searchDebounce.stop();
            searchDebounce.setOnFinished(_ -> applySearchFilterNow(newV));
            searchDebounce.playFromStart();
        });

        searchField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                if (!searchField.getText().isEmpty()) {
                    searchField.clear();
                    e.consume();
                    return;
                }
            }
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                // For now: ENTER just keeps focus; deep search comes later.
                e.consume();
            }
        });

        // Initial predicate
        applySearchFilterNow(searchField.getText());
    }

    private void applySearchFilterNow(String rawQuery) {
        String q = (rawQuery == null) ? "" : rawQuery.trim().toLowerCase(java.util.Locale.ROOT);
        activeSearchQuery = q;

        if (filteredTableItems == null) {
            return;
        }

        if (q.isEmpty()) {
            filteredTableItems.setPredicate(_ -> true);
        } else {
            filteredTableItems.setPredicate(fi -> {
                if (fi == null) return false;
                java.nio.file.Path p = fi.path();
                if (p == null) return false;
                String name = displayNameForTable(p);
                if (name == null) return false;
                return name.toLowerCase(java.util.Locale.ROOT).contains(q);
            });
        }

        // Keep status text honest when filtering is active.
        updateStatusCounts();
        // If icon view is currently visible, rebuild it from the filtered set.
        if (viewMode != null && viewMode != ViewMode.DETAILS) {
            tryRebuildIconViewFromVisibleItems();
        }
    }

    private void tryRebuildIconViewFromVisibleItems() {
        if (fileTable == null) return;
        if (iconFlow == null && virtualIconGridView == null && virtualIconListView == null) return;

        try {
            java.util.List<java.nio.file.Path> items = fileTable.getItems().stream().map(com.fileexplorer.model.FileItem::path).filter(java.util.Objects::nonNull).toList();
            rebuildTableIndexCache(fileTable.getItems());
            if (iconScroll != null && iconScroll.isVisible()) {
                rebuildIconTilesIncremental(items);
            }
        } catch (Exception ignore) {
        }
    }

    private void updateStatusCounts() {
        if (statusLabel == null) return;
        int visible = (fileTable != null && fileTable.getItems() != null) ? fileTable.getItems().size() : 0;
        int total = (tableItems != null) ? tableItems.size() : 0;
        if (activeSearchQuery != null && !activeSearchQuery.isEmpty() && total != visible) {
            statusLabel.setText(String.format(java.util.Locale.ROOT, "%d of %d items", visible, total));
        } else {
            statusLabel.setText(String.format(java.util.Locale.ROOT, "%d items", total));
        }
    }


    private void handleDirectoryListingFailed(Path directory, Throwable error) {
        this.currentDirectory = directory;

        if (breadcrumbBarController != null && directory != null) {
            breadcrumbBarController.setPath(directory);
        }

        String msg = (error == null) ? "Directory load failed." : ("Directory load failed: " + error.getMessage());
        setStatus(msg);

        if (statusLabel != null && directory != null) {
            statusLabel.setText(fileMetadataService.displayPathForStatus(directory));
        }
    }

    private void configureViewMenu() {
        LogSupport.enter(LOG, "configureViewMenu");
        // Keep menu state aligned with pane visibility and persisted options.
        if (detailsBox != null && detailsPaneMenuItem != null) {
            detailsPaneMenuItem.setSelected(detailsBox.isVisible());
        }
        if (previewBox != null && previewPaneMenuItem != null) {
            previewPaneMenuItem.setSelected(previewBox.isVisible());
        }

        if (showNavigationPaneMenuItem != null) {
            showNavigationPaneMenuItem.setSelected(showNavigationPane);
        }
        if (showCompactViewMenuItem != null) {
            showCompactViewMenuItem.setSelected(compactView);
        }
        if (showItemCheckBoxesMenuItem != null) {
            showItemCheckBoxesMenuItem.setSelected(showItemCheckBoxes);
        }
        if (showFileNameExtensionsMenuItem != null) {
            showFileNameExtensionsMenuItem.setSelected(showFileNameExtensions);
        }
        if (showHiddenItemsMenuItem != null) {
            showHiddenItemsMenuItem.setSelected(showHiddenItems);
        }

        wireViewMenuHandlers();

        setNavigationPaneVisible(showNavigationPane);
        setCompactView(compactView);
        syncViewMenuSelection();
    }

    private void setDetailsPaneVisible(boolean show) {
        LogSupport.enter(LOG, "setDetailsPaneVisible");
        if (detailsBox == null) {
            return;
        }
        detailsBox.setVisible(show);
        detailsBox.setManaged(show);

        if (detailsPaneMenuItem != null) {
            detailsPaneMenuItem.setSelected(show);
        }

        setStatus(show ? "Details pane shown." : "Details pane hidden.");
    }

    private void setPreviewPaneVisible(boolean show) {
        LogSupport.enter(LOG, "setPreviewPaneVisible");
        if (previewBox == null) {
            return;
        }
        previewBox.setVisible(show);
        previewBox.setManaged(show);

        if (previewPaneMenuItem != null) {
            previewPaneMenuItem.setSelected(show);
        }

        setStatus(show ? "Preview pane shown." : "Preview pane hidden.");
    }

    private void setNavigationPaneVisible(boolean show) {
        LogSupport.enter(LOG, "setNavigationPaneVisible");
        showNavigationPane = show;

        if (folderTree != null) {
            folderTree.setVisible(show);
            folderTree.setManaged(show);

            if (show) {
                folderTree.setMinWidth(NAV_TREE_MIN_WIDTH_PX);
                folderTree.setPrefWidth(Math.max(folderTree.getPrefWidth(), NAV_TREE_PREF_WIDTH_PX));
            } else {
                folderTree.setMinWidth(0);
                folderTree.setPrefWidth(0);
            }
        }

        if (mainSplitPane != null) {
            Platform.runLater(() -> {
                try {
                    mainSplitPane.setDividerPositions(show ? 0.22 : 0.0);
                } catch (Exception ex) {
                    // Ignore layout exceptions.
                }
            });
        }
    }private void setCompactView(boolean on) {
        LogSupport.enter(LOG, "setCompactView");
        compactView = on;
        if (root == null) {
            return;
        }
        if (on) {
            if (!root.getStyleClass().contains("compact-view")) {
                root.getStyleClass().add("compact-view");
            }
        } else {
            root.getStyleClass().remove("compact-view");
        }
    }

    private void refreshCurrentDirectoryView() {
        LogSupport.enter(LOG, "refreshCurrentDirectoryView");
        Path dir = currentDirectory;
        if (dir == null) {
            return;
        }
        loadDirectoryIntoTableAsync(dir);
    }

private void configureThemeToggle() {
    LogSupport.enter(LOG, "configureThemeToggle");
        if (themeToggle == null) {
            return;
        }

        themeToggle.setSelected(themeService.isDarkPreferred());
        syncThemeToggleText();

        themeToggle.selectedProperty().addListener((_, _, sel) -> {
            themeService.setDarkPreferred(sel != null && sel);
            syncThemeToggleText();

            Scene scene = themeToggle.getScene();
            if (scene != null) {
                themeService.apply(scene);
            }
        });
    }

    private void applyThemeToCurrentScene(Scene scene) {
        LogSupport.enter(LOG, "applyThemeToCurrentScene");
        if (scene == null) {
            return;
        }
        // Scene may be set before ExplorerContext is attached.
        if (themeService == null) {
            return;
        }
        themeService.apply(scene);
    }

    private void loadDirectoryIntoTableAsync(Path directory) {
        LogSupport.enter(LOG, "loadDirectoryIntoTableAsync", directory);

        if (directory == null) {
            return;
        }

        currentDirectory = directory.normalize();

        // Update UI immediately so the app doesn't look "frozen" while IO runs.
        if (statusLabel != null) {
            statusLabel.setText("Loading " + fileMetadataService.displayPathForStatus(currentDirectory) + " …");
        }
        if (fileTable != null) {
            fileTable.getSelectionModel().clearSelection();
        }
        if (isIconMode(viewMode)) {
            clearIconTiles();
        }

        lastRequestedDirectory = currentDirectory;
        lastRequestedShowHidden = showHiddenItems;
        lastRequestedRequestId = directoryCoordinator.requestLoad(currentDirectory, showHiddenItems);
    }


    private void rebuildTableIndexCache(List<FileItem> items) {
        LogSupport.enter(LOG, "rebuildTableIndexCache");
        tableIndexByPath.clear();
        if (items == null || items.isEmpty()) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            FileItem fi = items.get(i);
            Path p = (fi != null) ? fi.path() : null;
            if (p != null) {
                tableIndexByPath.put(p, i);
            }
        }
    }

    private int indexOfTableItem(Path p) {
        LogSupport.enter(LOG, "indexOfTableItem");
        if (p == null) {
            return -1;
        }
        Integer idx = tableIndexByPath.get(p);
        if (idx != null) {
            return idx;
        }

        // Fallback: resolve via current table items (equals-based)
        int linear = tableItems.indexOf(p);
        if (linear >= 0) {
            return linear;
        }

        // Last-resort: compare normalized absolute paths (handles Path instances from different sources)
        try {
            Path key = p.toAbsolutePath().normalize();
            for (var e : tableIndexByPath.entrySet()) {
                try {
                    Path k = e.getKey();
                    if (k != null && k.toAbsolutePath().normalize().equals(key)) {
                        return e.getValue();
                    }
                } catch (Exception ex) {
                    // ignore
                }
            }
        } catch (Exception ex) {
            // ignore
        }

        return -1;
    }

    private void updateSelectionDetails(Path selected) {
        LogSupport.enter(LOG, "updateSelectionDetails");
        if (selected == null) {
            if (detailsText != null) {
                detailsText.setText("");
            }
            if (previewText != null) {
                previewText.setText("");
            }
            return;
        }

        String status = fileMetadataService.describeForStatusBar(selected);
        setStatus(status);

        if (detailsText != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("Name: ").append(displayNameForTable(selected)).append("\n");
            sb.append("Path: ").append(selected).append("\n");
            sb.append("Type: ").append(fileMetadataService.detectFileType(selected)).append("\n");
            String size = fileMetadataService.humanReadableSize(selected);
            if (!size.isBlank()) {
                sb.append("Size: ").append(size).append("\n");
            }
            String mod = fileMetadataService.lastModifiedLocalString(selected);
            if (!mod.isBlank()) {
                sb.append("Date modified: ").append(mod).append("\n");
            }
            detailsText.setText(sb.toString());
        }

        if (previewText != null) {
            previewText.setText(selected.toString());
        }
    }

    // ---------------------------------------------------------------------
    // View mode (Details vs Large icons)
    // ---------------------------------------------------------------------

    
    private void setViewMode(ViewMode mode) {
        LogSupport.enter(LOG, "setViewMode");
        if (mode == null) {
            return;
        }

        if (SAFE_MODE && !isTableMode(mode)) {
            // Safe Mode: force table-based views only.
            mode = ViewMode.DETAILS;
        }

        viewMode = mode;

        if (isIconMode(viewMode)) {
            LogSupport.enter(LOG, "isIconMode");
            lastIconViewMode = viewMode;
            applyIconSizePreset(viewMode);
        }

        boolean tableMode = isTableMode(viewMode);

        if (fileTable != null) {
            fileTable.setVisible(tableMode);
            fileTable.setManaged(tableMode);
        }
        if (iconScroll != null) {
            iconScroll.setVisible(!tableMode);
            iconScroll.setManaged(!tableMode);
        }

        applyTableColumnMode(viewMode);

        if (iconFlow != null) {
            if (viewMode == ViewMode.TILES || viewMode == ViewMode.CONTENT) {
                iconFlow.setOrientation(javafx.geometry.Orientation.VERTICAL);
                iconFlow.setPrefWrapLength(100000.0);
                iconFlow.setHgap(0.0);
                iconFlow.setVgap(8.0);
            } else {
                iconFlow.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
                iconFlow.setPrefWrapLength(600.0);
                iconFlow.setHgap(16.0);
                iconFlow.setVgap(16.0);
            }
        }

        if (detailsToggle != null) {
            detailsToggle.setSelected(tableMode);
        }

        syncViewMenuSelection();

        if (!SAFE_MODE && isIconMode(viewMode)) {
            rebuildIconTiles();
        } else {
            clearIconTiles();
        }
        setStatus("View: " + viewModeLabel(viewMode));
    }

    private boolean isTableMode(ViewMode mode) {
        LogSupport.enter(LOG, "isTableMode");
        return mode == ViewMode.DETAILS || mode == ViewMode.LIST;
    }

    private boolean isIconMode(ViewMode mode) {
        LogSupport.enter(LOG, "isIconMode");
        return mode == ViewMode.EXTRA_LARGE_ICONS
                || mode == ViewMode.LARGE_ICONS
                || mode == ViewMode.MEDIUM_ICONS
                || mode == ViewMode.SMALL_ICONS
                || mode == ViewMode.TILES
                || mode == ViewMode.CONTENT;
    }

    private void applyIconSizePreset(ViewMode mode) {
        LogSupport.enter(LOG, "applyIconSizePreset");
        if (mode == null) {
            return;
        }
        switch (mode) {
            case EXTRA_LARGE_ICONS:
                iconSizePx = 160.0;
                break;
            case LARGE_ICONS:
                iconSizePx = 120.0;
                break;
            case MEDIUM_ICONS:
                iconSizePx = 88.0;
                break;
            case SMALL_ICONS:
                iconSizePx = 64.0;
                break;
            case TILES:
                iconSizePx = 96.0;
                break;
            case CONTENT:
                iconSizePx = 72.0;
                break;
            default:
                break;
        }
        clampIconSize();
    }

    private void clampIconSize() {
        LogSupport.enter(LOG, "clampIconSize");
        iconSizePx = Math.max(ICON_SIZE_MIN, Math.min(ICON_SIZE_MAX, iconSizePx));
    }

    private void applyTableColumnMode(ViewMode mode) {
        LogSupport.enter(LOG, "applyTableColumnMode");
        if (fileTable == null || colName == null || colType == null || colSize == null || colModified == null) {
            return;
        }

        boolean list = (mode == ViewMode.LIST);

        colName.setVisible(true);
        colType.setVisible(!list);
        colSize.setVisible(!list);
        colModified.setVisible(!list);
    }

    private void syncViewMenuSelection() {
        LogSupport.enter(LOG, "syncViewMenuSelection");
        if (viewExtraLargeIcons == null) {
            return;
        }
        // If menu items are present, ensure selection matches viewMode.
        switch (viewMode) {
            case EXTRA_LARGE_ICONS:
                viewExtraLargeIcons.setSelected(true);
                break;
            case LARGE_ICONS:
                if (viewLargeIcons != null) {
                    viewLargeIcons.setSelected(true);
                }
                break;
            case MEDIUM_ICONS:
                if (viewMediumIcons != null) {
                    viewMediumIcons.setSelected(true);
                }
                break;
            case SMALL_ICONS:
                if (viewSmallIcons != null) {
                    viewSmallIcons.setSelected(true);
                }
                break;
            case LIST:
                if (viewList != null) {
                    viewList.setSelected(true);
                }
                break;
            case DETAILS:
                if (viewDetails != null) {
                    viewDetails.setSelected(true);
                }
                break;
            case TILES:
                if (viewTiles != null) {
                    viewTiles.setSelected(true);
                }
                break;
            case CONTENT:
                if (viewContent != null) {
                    viewContent.setSelected(true);
                }
                break;
            default:
                break;
        }
    }

    private String viewModeLabel(ViewMode mode) {
        LogSupport.enter(LOG, "viewModeLabel");
        if (mode == null) {
            return "";
        }

        return switch (mode) {
            case EXTRA_LARGE_ICONS -> "Extra large icons";
            case LARGE_ICONS       -> "Large icons";
            case MEDIUM_ICONS      -> "Medium icons";
            case SMALL_ICONS       -> "Small icons";
            case LIST              -> "List";
            case DETAILS           -> "Details";
            case TILES             -> "Tiles";
            case CONTENT           -> "Content";
            default                -> mode.name();
        };
    }

    
    private void clearIconTiles() {
        if (iconFlow != null) {
            iconFlow.getChildren().clear();
        }
        if (virtualIconGridView != null) {
            virtualIconGridView.getItems().clear();
        }
        if (virtualIconListView != null) {
            virtualIconListView.getItems().clear();
        }
        hideVirtualIconViews();
    }

    private void rebuildIconTiles() {
        LogSupport.enter(LOG, "rebuildIconTiles");
        if (SAFE_MODE) {
            clearIconTiles();
            return;
        }
        if (!isIconMode(viewMode)) {
            LogSupport.enter(LOG, "isIconMode");
            clearIconTiles();
            return;
        }

        // Safety cap: if the folder is extremely large, icon views can still be expensive even with virtualization.
        // Details/List views remain virtualized and are always safe.
        int count = tableItems == null ? 0 : tableItems.size();
        if (count > ICON_VIEW_FORCE_DETAILS_THRESHOLD) {
            setViewMode(ViewMode.DETAILS);
            setStatus("Folder has " + count + " items; using Details view for stability.");
            return;
        }

        ensureVirtualIconViewsInstalled();

        List<Path> items = (tableItems == null) ? List.of() : tableItems.stream().map(FileItem::path).toList();

        // Grid icon modes: use FlowPane only for small folders; otherwise use a virtualized grid.
        if (viewMode == ViewMode.EXTRA_LARGE_ICONS
                || viewMode == ViewMode.LARGE_ICONS
                || viewMode == ViewMode.MEDIUM_ICONS
                || viewMode == ViewMode.SMALL_ICONS) {

            if (items.size() <= ICON_FLOW_MAX_ITEMS) {
                hideVirtualIconViews();
                showIconScrollOnly();
                rebuildIconTilesIncremental(items);
            } else {
                // 4.2Y virtualization for large icon folders
                showVirtualIconGrid(items);
            }
            return;
        }

        // Tiles / Content: treat as a virtualized list when the folder is non-trivial.
        if (viewMode == ViewMode.TILES || viewMode == ViewMode.CONTENT) {
            if (items.size() <= ICON_FLOW_MAX_ITEMS) {
                hideVirtualIconViews();
                showIconScrollOnly();
                rebuildIconTilesIncremental(items);
            } else {
                showVirtualIconList(items);
            }
            return;
        }

        // Fallback
        hideVirtualIconViews();
        showIconScrollOnly();
        rebuildIconTilesIncremental(items);
    }

    private void rebuildIconTilesIncremental(List<Path> items) {
        LogSupport.enter(LOG, "rebuildIconTilesIncremental");
        if (iconFlow == null) {
            return;
        }
        iconFlow.getChildren().clear();

        if (items == null || items.isEmpty()) {
            return;
        }

        // 4.1Y incremental paging for FlowPane icon view (small folders only).
        iconBuildGeneration++;
        long gen = iconBuildGeneration;

        iconBuildItems = items;
        iconBuildNextIndex = 0;

        appendNextIconBatch(gen);
    }

    private void appendNextIconBatch(long gen) {
        LogSupport.enter(LOG, "appendNextIconBatch");
        if (iconFlow == null) {
            return;
        }
        if (gen != iconBuildGeneration) {
            return; // cancelled by a newer rebuild
        }

        int start = iconBuildNextIndex;
        int end = Math.min(iconBuildItems.size(), start + ICON_FLOW_BATCH_SIZE);
        if (start >= end) {
            return;
        }

        for (int i = start; i < end; i++) {
            Path p = iconBuildItems.get(i);
            if (p == null) {
                continue;
            }
            iconFlow.getChildren().add(buildIconTile(p));
        }

        iconBuildNextIndex = end;
    }

    private void installIconScrollPaging() {
        LogSupport.enter(LOG, "installIconScrollPaging");
        if (iconScrollPagingInstalled) {
            return;
        }
        iconScrollPagingInstalled = true;

        if (iconScroll == null) {
            return;
        }

        iconScroll.vvalueProperty().addListener((_, _, val) -> {
            if (!iconScroll.isVisible() || iconFlow == null) {
                return;
            }
            // Only page when using FlowPane (virtual views have their own scrolling).
            if (virtualIconGridView != null && virtualIconGridView.isVisible()) {
                return;
            }
            if (virtualIconListView != null && virtualIconListView.isVisible()) {
                return;
            }

            double v = val == null ? 0.0 : val.doubleValue();
            if (v >= ICON_SCROLL_LOAD_MORE_THRESHOLD) {
                appendNextIconBatch(iconBuildGeneration);
            }
        });
    }

    private void ensureVirtualIconViewsInstalled() {
        LogSupport.enter(LOG, "ensureVirtualIconViewsInstalled");
        if (virtualIconViewsInstalled) {
            return;
        }
        virtualIconViewsInstalled = true;

        if (iconScroll == null) {
            return;
        }
        if (!(iconScroll.getParent() instanceof javafx.scene.layout.StackPane host)) {
            return;
        }

        // Virtual grid (rows of icon tiles)
        virtualIconGridView = new ListView<>();
        virtualIconGridView.getStyleClass().add("icon-virtual-grid");
        virtualIconGridView.setVisible(false);
        virtualIconGridView.setManaged(false);

        virtualIconGridView.setCellFactory(_ -> new ListCell<>() {
            private final FlowPane rowPane = new FlowPane();

            {
                rowPane.setHgap(16.0);
                rowPane.setVgap(16.0);
                rowPane.setPadding(new Insets(16.0));
                rowPane.setAlignment(Pos.TOP_LEFT);
            }

            @Override
            protected void updateItem(List<Path> row, boolean empty) {
                LogSupport.enter(LOG, "updateItem4");
                super.updateItem(row, empty);
                rowPane.getChildren().clear();

                if (empty || row == null || row.isEmpty()) {
                    setGraphic(null);
                    return;
                }

                for (Path p : row) {
                    if (p == null) {
                        continue;
                    }
                    rowPane.getChildren().add(buildIconTile(p));
                }

                setGraphic(rowPane);
            }
        });

        // Virtual list (Tiles / Content)
        virtualIconListView = new ListView<>();
        virtualIconListView.getStyleClass().add("icon-virtual-list");
        virtualIconListView.setVisible(false);
        virtualIconListView.setManaged(false);

        virtualIconListView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                LogSupport.enter(LOG, "updateItem5");
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(buildIconTile(item));
            }
        });

        // Keyboard activation (Enter) for virtual icon views
        virtualIconGridView.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && lastIconActivatedPath != null) {
                Path p = lastIconActivatedPath;
                if (Files.isDirectory(p)) {
                    navigateToFolder(p, true);
                    e.consume();
                }
            }
        });

        virtualIconListView.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Path p = virtualIconListView.getSelectionModel().getSelectedItem();
                if (p == null) {
                    p = lastIconActivatedPath;
                }
                if (p != null && Files.isDirectory(p)) {
                    navigateToFolder(p, true);
                    e.consume();
                }
            }
        });

        // Insert into the same StackPane as the table and iconScroll (viewHost in FXML).
        host.getChildren().add(virtualIconGridView);
        host.getChildren().add(virtualIconListView);
    }

    private void hideVirtualIconViews() {
        LogSupport.enter(LOG, "hideVirtualIconViews");
        if (virtualIconGridView != null) {
            virtualIconGridView.setVisible(false);
            virtualIconGridView.setManaged(false);
            virtualIconGridView.getItems().clear();
        }
        if (virtualIconListView != null) {
            virtualIconListView.setVisible(false);
            virtualIconListView.setManaged(false);
            virtualIconListView.getItems().clear();
        }
    }

    private void showIconScrollOnly() {
        LogSupport.enter(LOG, "showIconScrollOnly");
        if (iconScroll != null) {
            iconScroll.setVisible(true);
            iconScroll.setManaged(true);
        }
        if (virtualIconGridView != null) {
            virtualIconGridView.setVisible(false);
            virtualIconGridView.setManaged(false);
        }
        if (virtualIconListView != null) {
            virtualIconListView.setVisible(false);
            virtualIconListView.setManaged(false);
        }
    }

    private void showVirtualIconGrid(List<Path> items) {
        LogSupport.enter(LOG, "showVirtualIconGrid");
        if (iconScroll != null) {
            iconScroll.setVisible(false);
            iconScroll.setManaged(false);
        }
        if (virtualIconListView != null) {
            virtualIconListView.setVisible(false);
            virtualIconListView.setManaged(false);
            virtualIconListView.getItems().clear();
        }
        if (virtualIconGridView == null) {
            hideVirtualIconViews();
            showIconScrollOnly();
            rebuildIconTilesIncremental(items);
            return;
        }

        virtualIconGridView.setVisible(true);
        virtualIconGridView.setManaged(true);

        // Build row model (List<Path> per row) based on available width.
        int itemsPerRow = computeItemsPerIconRow();
        if (itemsPerRow < 1) {
            itemsPerRow = 1;
        }

        List<List<Path>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i += itemsPerRow) {
            int j = Math.min(items.size(), i + itemsPerRow);
            rows.add(items.subList(i, j));
        }

        virtualIconGridView.setItems(FXCollections.observableArrayList(rows));
        virtualIconGridView.requestFocus();
    }

    private void showVirtualIconList(List<Path> items) {
        LogSupport.enter(LOG, "showVirtualIconList");
        if (iconScroll != null) {
            iconScroll.setVisible(false);
            iconScroll.setManaged(false);
        }
        if (virtualIconGridView != null) {
            virtualIconGridView.setVisible(false);
            virtualIconGridView.setManaged(false);
            virtualIconGridView.getItems().clear();
        }
        if (virtualIconListView == null) {
            hideVirtualIconViews();
            showIconScrollOnly();
            rebuildIconTilesIncremental(items);
            return;
        }

        virtualIconListView.setVisible(true);
        virtualIconListView.setManaged(true);
        virtualIconListView.setItems(FXCollections.observableArrayList(items));
        virtualIconListView.requestFocus();
    }

    private int computeItemsPerIconRow() {
        LogSupport.enter(LOG, "computeItemsPerIconRow");
        double w = 900.0;

        if (virtualIconGridView != null) {
            w = virtualIconGridView.getWidth();
        }
        if (w <= 0.0 && iconScroll != null) {
            w = iconScroll.getViewportBounds().getWidth();
        }
        if (w <= 0.0 && iconScroll != null && iconScroll.getParent() != null) {
            w = iconScroll.getParent().getLayoutBounds().getWidth();
        }
        if (w <= 0.0) {
            w = 900.0;
        }

        double tileW = Math.max(96.0, iconSizePx + 40.0);
        double hgap = 16.0;
        double padding = 32.0;
        double usable = Math.max(1.0, w - padding);
        return (int) Math.max(1.0, Math.floor(usable / (tileW + hgap)));
    }



        private Node buildIconTile(Path p) {
            LogSupport.enter(LOG, "buildIconTile");
        // For icon modes, use the same container (iconFlow) but vary layout:
        // - Icons: icon above name (wrapping)
        // - Tiles: icon left + (name/type/size)
        // - Content: icon left + (name/type/size/modified)
        boolean isTiles = viewMode == ViewMode.TILES;
        boolean isContent = viewMode == ViewMode.CONTENT;

        if (isTiles || isContent) {
            HBox row = new HBox(12.0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("tile-row");
            row.setMinHeight(36.0);

            Node icon = buildIconNode(p, iconSizePx, "tile-item-icon");

            VBox textCol = new VBox(2.0);
            textCol.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(displayNameForTable(p));
            name.setWrapText(false);

            String typeText = typeForTable(p);
            String sizeText = sizeForTable(p);
            String meta = typeText;
            if (sizeText != null && !sizeText.isBlank()) {
                meta = meta + " - " + sizeText;
            }

            Label line2 = new Label(meta);

            textCol.getChildren().addAll(name, line2);

            if (isContent) {
                String modified = modifiedForTable(p);
                Label line3 = new Label(modified);
                textCol.getChildren().add(line3);
            }

            row.getChildren().addAll(icon, textCol);


            row.setOnMouseClicked(me -> {
                lastIconActivatedPath = p;
                fileTable.getSelectionModel().clearSelection();
                int idx = indexOfTableItem(p);
                if (idx >= 0) {
                                    fileTable.getSelectionModel().select(idx);
                    fileTable.scrollTo(idx);
                    if (virtualIconGridView != null && virtualIconGridView.isVisible()) {
                        virtualIconGridView.requestFocus();
                    } else if (virtualIconListView != null && virtualIconListView.isVisible()) {
                        virtualIconListView.requestFocus();
                    } else if (virtualIconGridView != null && virtualIconGridView.isVisible()) {
                        virtualIconGridView.requestFocus();
                    } else if (virtualIconListView != null && virtualIconListView.isVisible()) {
                        virtualIconListView.requestFocus();
                    } else if (iconScroll != null && iconScroll.isVisible()) {
                        iconScroll.requestFocus();
                    } else {
                        fileTable.requestFocus();
                    }
                }
                if (me.getClickCount() == 2 && Files.isDirectory(p)) {
                    navigateToFolder(p, true);
                }
            });

            return row;
        }

        VBox tile = new VBox(6.0);
        tile.setAlignment(Pos.TOP_CENTER);

        double w = Math.max(96.0, iconSizePx + 40.0);
        tile.setPrefWidth(w);
        tile.setMaxWidth(w);

        Node icon = buildIconNode(p, iconSizePx, "tile-item-icon");

        Label name = new Label(displayNameForTable(p));
        name.setWrapText(true);
        name.setMaxWidth(w);
        name.setAlignment(Pos.TOP_CENTER);

        tile.getChildren().addAll(icon, name);

        tile.setOnMouseEntered(_ -> scheduleHoverPrefetch(p));

        tile.setOnMouseClicked(me -> {
            lastIconActivatedPath = p;
            fileTable.getSelectionModel().clearSelection();
            int idx = indexOfTableItem(p);
            if (idx >= 0) {
                                fileTable.getSelectionModel().select(idx);
                    fileTable.scrollTo(idx);
                    if (virtualIconGridView != null && virtualIconGridView.isVisible()) {
                        virtualIconGridView.requestFocus();
                    } else if (virtualIconListView != null && virtualIconListView.isVisible()) {
                        virtualIconListView.requestFocus();
                    } else if (virtualIconGridView != null && virtualIconGridView.isVisible()) {
                        virtualIconGridView.requestFocus();
                    } else if (virtualIconListView != null && virtualIconListView.isVisible()) {
                        virtualIconListView.requestFocus();
                    } else if (iconScroll != null && iconScroll.isVisible()) {
                        iconScroll.requestFocus();
                    } else {
                        fileTable.requestFocus();
                    }
            }

            if (me.getClickCount() == 2) {
                if (Files.isDirectory(p)) {
                    navigateToFolder(p, true);
                }
            }
        });

        return tile;
        }


    private Node buildIconNode(Path p, double sizePx, String... styleClasses) {
        LogSupport.enter(LOG, "buildIconNode");
        double effective = sizePx;
        if (Double.isNaN(effective) || effective <= 0.0) {
            effective = 16.0;
        }

        String identity;
        try {
            identity = fileMetadataService.iconIdentity(p);
        } catch (Exception ex) {
            identity = "type:" + IconLoader.IconType.FILE.name();
        }

        Image img = null;
        try {
            img = IconLoader.loadForIdentity(identity, themeService.isDarkPreferred(), (int) Math.round(effective));
        } catch (Exception ex) {
            img = null;
        }

        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setFitWidth(effective);
            iv.setFitHeight(effective);
            if (styleClasses != null) {
                for (String s : styleClasses) {
                    if (s != null && !s.isBlank()) {
                        iv.getStyleClass().add(s);
                    }
                }
            }
            return iv;
        }

        // Fallback: fluent glyph label (rare; used only if Image creation fails)
        Label glyph = new Label(glyphForIdentity(identity, p));
        glyph.getStyleClass().add("fluent-icon");
        if (styleClasses != null) {
            for (String s : styleClasses) {
                if (s != null && !s.isBlank()) {
                    glyph.getStyleClass().add(s);
                }
            }
        }
        glyph.setStyle("-fx-font-size: " + effective + "px;");
        return glyph;
    }

    private double effectiveTreeIconPx() {
        LogSupport.enter(LOG, "effectiveTreeIconPx");
        double base = treeFontSizePxApplied > 0.0 ? treeFontSizePxApplied : uiFontSizePx;
        return clamp(base + 4.0, 16.0, 24.0);
    }

    private String glyphForIdentity(String identity, Path p) {
        LogSupport.enter(LOG, "glyphForIdentity");
        if (identity != null) {
            String id = identity.trim().toLowerCase(Locale.ROOT);
            if (id.startsWith("type:")) {
                String t = id.substring("type:".length()).trim();
                if (t.equals("folder")) {
                    return ""; // Folder
                }
            }
            if (id.startsWith("ext:")) {
                String ext = id.substring("ext:".length()).trim();
                switch (ext) {
                    case "pdf" -> {
                        return ""; // Document (best effort)
                    }
                    case "zip", "7z", "rar" -> {
                        return ""; // Archive-ish glyph (best effort)
                    }
                    case "mp3", "wav", "flac", "m4a", "ogg" -> {
                        return ""; // Audio-ish glyph
                    }
                    case "mp4", "mkv", "mov", "avi", "wmv", "webm" -> {
                        return ""; // Video-ish glyph
                    }
                    case "png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff" -> {
                        return ""; // Picture
                    }
                    case "txt", "md", "log", "json", "xml", "csv", "yml", "yaml" -> {
                        return ""; // Document
                    }
                }
            }
        }

        try {
            if (p != null && Files.isDirectory(p)) {
                return ""; // Folder
            }
        } catch (Exception ex) {
            // ignore
        }
        return ""; // generic document
    }
    // ---------------------------------------------------------------------
    // Navigation + history
    // ---------------------------------------------------------------------

    private void navigateToFolder(Path target, boolean pushHistory) {
        LogSupport.enter(LOG, "navigateToFolder");
        if (target == null) {
            return;
        }

        Path normalized = target.normalize();
        if (!Files.isDirectory(normalized)) {
            setStatus("Not a folder: " + normalized);
            return;
        }

        if (pushHistory && currentDirectory != null && !Objects.equals(currentDirectory.normalize(), normalized)) {
            backHistory.add(currentDirectory);
            forwardHistory.clear();
        }

        if (!SAFE_MODE) {
            boolean prevSuppress = suppressTreeSelection;
            suppressTreeSelection = true;
            try {
                expandAndSelectFolder(normalized);
            } finally {
                suppressTreeSelection = prevSuppress;
            }
        }

        loadDirectoryIntoTableAsync(normalized);
    }

    private void navigateUp() {
        LogSupport.enter(LOG, "navigateUp");
        Path dir = currentDirectory;
        if (dir == null) {
            return;
        }
        Path parent = dir.getParent();
        if (parent == null) {
            return;
        }
        navigateToFolder(parent, true);
    }

    private void navigateBack() {
        LogSupport.enter(LOG, "navigateBack");
        if (backHistory.isEmpty()) {
            return;
        }
        Path prev = backHistory.removeLast();
        if (currentDirectory != null) {
            forwardHistory.add(currentDirectory);
        }
        navigateToFolder(prev, false);
    }

    private void navigateForward() {
        LogSupport.enter(LOG, "navigateForward");
        if (forwardHistory.isEmpty()) {
            return;
        }
        Path next = forwardHistory.removeLast();
        if (currentDirectory != null) {
            backHistory.add(currentDirectory);
        }
        navigateToFolder(next, false);
    }

    // ---------------------------------------------------------------------
    // Operations (copy/cut/paste/rename/new folder/properties)
    // ---------------------------------------------------------------------

    private List<Path> getSelectedItems() {
    LogSupport.enter(LOG, "getSelectedItems");
    if (fileTable == null) {
        return List.of();
    }
    return fileTable.getSelectionModel().getSelectedItems().stream()
            .map(FileItem::path)
            .toList();
}

    private Path getPrimarySelection() {
        LogSupport.enter(LOG, "getPrimarySelection");
        FileItem selItem = fileTable.getSelectionModel().getSelectedItem();
        return (selItem != null) ? selItem.path() : null;
    }

    private void selectAll() {
        LogSupport.enter(LOG, "selectAll");
        fileTable.getSelectionModel().selectAll();
        setStatus("Selected all.");
    }

    private void copySelection() {
        LogSupport.enter(LOG, "copySelection");
        List<Path> selected = getSelectedItems();
        if (selected.isEmpty()) {
            return;
        }

        ClipboardContent content = new ClipboardContent();
        List<File> files = new ArrayList<>();
        for (Path p : selected) {
            files.add(p.toFile());
        }
        content.putFiles(files);
        Clipboard.getSystemClipboard().setContent(content);

        cutMode = false;
        cutBuffer.clear();

        setStatus("Copied: " + selected.size());
    }

    private void cutSelection() {
        LogSupport.enter(LOG, "cutSelection");
        List<Path> selected = getSelectedItems();
        if (selected.isEmpty()) {
            return;
        }

        ClipboardContent content = new ClipboardContent();
        List<File> files = new ArrayList<>();
        for (Path p : selected) {
            files.add(p.toFile());
        }
        content.putFiles(files);
        Clipboard.getSystemClipboard().setContent(content);

        cutMode = true;
        cutBuffer.clear();
        cutBuffer.addAll(selected);

        setStatus("Cut: " + selected.size());
    }

    private void pasteIntoCurrentFolder() {
        LogSupport.enter(LOG, "pasteIntoCurrentFolder");
        Path dir = currentDirectory;
        if (dir == null) {
            return;
        }

        Clipboard cb = Clipboard.getSystemClipboard();
        List<File> files = cb.getFiles();
        if (files == null || files.isEmpty()) {
            return;
        }

        List<Path> src = new ArrayList<>();
        for (File f : files) {
            src.add(f.toPath());
        }

        boolean doMove = cutMode && !cutBuffer.isEmpty() && sameSet(cutBuffer, src);

        ioExecutor.execute(() -> {
            int count = 0;
                        for (Path s : src) {
                            try {
                                Path target = resolvePasteTarget(dir, s.getFileName() == null ? s : s.getFileName());
                                if (target == null) {
                                    continue;
                                }

                                if (doMove) {
                                    moveRecursively(s, target);
                                } else {
                                    copyRecursively(s, target);
                                }
                                count++;
                            } catch (Exception ex) {
                                // ignore; continue
                            }
                        }

                        int finalCount = count;
                        Platform.runLater(() -> {
                            if (doMove) {
                                cutMode = false;
                                cutBuffer.clear();
                            }
                            refresh();
                            setStatus((doMove ? "Moved " : "Copied ") + finalCount + " item(s).");
                        });
        });
}

    private boolean sameSet(List<Path> a, List<Path> b) {
        LogSupport.enter(LOG, "sameSet");
        if (a.size() != b.size()) {
            return false;
        }
        for (Path p : a) {
            boolean found = false;
            for (Path q : b) {
                if (Objects.equals(p.normalize(), q.normalize())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private Path resolvePasteTarget(Path destDir, Path fileName) {
        LogSupport.enter(LOG, "resolvePasteTarget");
        if (destDir == null || fileName == null) {
            return null;
        }
        Path candidate = destDir.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String base = fileName.toString();
        String name = base;
        String ext = "";

        int dot = base.lastIndexOf('.');
        if (dot > 0 && dot < base.length() - 1) {
            name = base.substring(0, dot);
            ext = base.substring(dot);
        }

        for (int i = 2; i <= 999; i++) {
            String trial = name + " (" + i + ")" + ext;
            Path p = destDir.resolve(trial);
            if (!Files.exists(p)) {
                return p;
            }
        }
        return null;
    }

    private void copyRecursively(Path src, Path dest) throws IOException {
        LogSupport.enter(LOG, "copyRecursively");
        if (Files.isDirectory(src)) {
            Files.createDirectories(dest);
            try (var stream = Files.list(src)) {
                for (Path child : (Iterable<Path>) stream::iterator) {
                    copyRecursively(child, dest.resolve(child.getFileName()));
                }
            }
            return;
        }
        Files.copy(src, dest, new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES });
    }

    private void moveRecursively(Path src, Path dest) throws IOException {
        LogSupport.enter(LOG, "moveRecursively");
        // Fast path
        try {
            Files.move(src, dest, new CopyOption[] { StandardCopyOption.ATOMIC_MOVE });
            return;
        } catch (Exception ex) {
            // fallthrough
        }

        if (Files.isDirectory(src)) {
            Files.createDirectories(dest);
            try (var stream = Files.list(src)) {
                for (Path child : (Iterable<Path>) stream::iterator) {
                    moveRecursively(child, dest.resolve(child.getFileName()));
                }
            }
            // best-effort delete
            try {
                Files.deleteIfExists(src);
            } catch (Exception ex) {
                // ignore
            }
            return;
        }

        Files.move(src, dest, new CopyOption[] { StandardCopyOption.REPLACE_EXISTING });
    }

    private void moveSelectionToTrash() {
    LogSupport.enter(LOG, "moveSelectionToTrash");

    List<Path> selected = getSelectedItems();
    if (selected.isEmpty()) {
        setStatus("No selection.");
        return;
    }

    Desktop desktop;
    try {
        if (!Desktop.isDesktopSupported()) {
            setStatus("Trash not supported on this platform.");
            return;
        }
        desktop = Desktop.getDesktop();
    } catch (HeadlessException ex) {
        setStatus("Trash not supported (headless).");
        return;
    } catch (Exception ex) {
        setStatus("Trash not available.");
        return;
    }

    int moved = 0;
    for (Path p : selected) {
        try {
            boolean ok = desktop.moveToTrash(p.toFile());
            if (ok) {
                moved++;
            }
        } catch (Exception ignore) {
            // continue
        }
    }

    if (moved > 0) {
        setStatus("Moved to trash: " + moved + (moved == 1 ? " item." : " items."));
        // Refresh current directory view
        if (currentDirectory != null) {
            refresh();
        }
    } else {
        setStatus("Could not move selection to trash.");
    }
}

private void createNewFolder() {
        LogSupport.enter(LOG, "createNewFolder");
        Path dir = currentDirectory;
        if (dir == null) {
            return;
        }

        Path target = dir.resolve("New folder");
        if (Files.exists(target)) {
            for (int i = 2; i <= 999; i++) {
                Path p = dir.resolve("New folder (" + i + ")");
                if (!Files.exists(p)) {
                    target = p;
                    break;
                }
            }
        }

        try {
            Files.createDirectories(target);
            refresh();
            setStatus("Created: " + target.getFileName());
        } catch (Exception ex) {
            setStatus("Failed to create folder.");
        }
    }

    private void renameSelection() {
        LogSupport.enter(LOG, "renameSelection");
        Path sel = getPrimarySelection();
        if (sel == null) {
            return;
        }

        Path parent = sel.getParent();
        if (parent == null) {
            return;
        }

        String currentName = displayNameForTable(sel);

        TextInputDialog d = new TextInputDialog(currentName);
        d.setTitle("Rename");
        d.setHeaderText("Rename item");
        d.setContentText("New name:");

        Optional<String> result = d.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String newName = result.get().trim();
        if (newName.isEmpty() || newName.equals(currentName)) {
            return;
        }

        Path dest = parent.resolve(newName);

        try {
            Files.move(sel, dest, new CopyOption[] { StandardCopyOption.REPLACE_EXISTING });
            refresh();
            setStatus("Renamed.");
        } catch (Exception ex) {
            setStatus("Rename failed.");
        }
    }

    private void openPropertiesForSelection() {
        LogSupport.enter(LOG, "openPropertiesForSelection");
        Path sel = getPrimarySelection();
        if (sel == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(displayNameForTable(sel)).append("\n");
        sb.append("Path: ").append(sel).append("\n");
        sb.append("Type: ").append(fileMetadataService.detectFileType(sel)).append("\n");

        String size = fileMetadataService.humanReadableSize(sel);
        if (!size.isBlank()) {
            sb.append("Size: ").append(size).append("\n");
        }

        String mod = fileMetadataService.lastModifiedLocalString(sel);
        if (!mod.isBlank()) {
            sb.append("Date modified: ").append(mod).append("\n");
        }

        Alert a = new Alert(AlertType.INFORMATION);
        a.setTitle("Properties");
        a.setHeaderText(displayNameForTable(sel));
        a.setContentText(sb.toString());
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
    }

    // ---------------------------------------------------------------------
    // Pane toggles
    // ---------------------------------------------------------------------

    private void togglePreviewPane() {
        LogSupport.enter(LOG, "togglePreviewPane");
        boolean show = previewBox != null && !previewBox.isVisible();
        setPreviewPaneVisible(show);
    }

    private void toggleDetailsPane() {
        LogSupport.enter(LOG, "toggleDetailsPane");
        boolean show = detailsBox != null && !detailsBox.isVisible();
        setDetailsPaneVisible(show);
    }

    // ---------------------------------------------------------------------
    // Focus / window helpers
    // ---------------------------------------------------------------------

    private void focusSearch() {
        LogSupport.enter(LOG, "focusSearch");
        if (searchField != null) {
            searchField.requestFocus();
            searchField.selectAll();
        }
    }

    private void focusAddressBar() {
        LogSupport.enter(LOG, "focusAddressBar");
        if (breadcrumbBarController != null) {
            breadcrumbBarController.requestAddressFocus();
            return;
        }
        // fallback
        if (folderTree != null) {
            folderTree.requestFocus();
        }
    }

    private void cyclePanesFocus() {
        LogSupport.enter(LOG, "cyclePanesFocus");
        List<Node> panes = new ArrayList<>();
        if (searchField != null) panes.add(searchField);
        if (folderTree != null) panes.add(folderTree);
        if (fileTable != null) panes.add(fileTable);
        if (previewBox != null && previewBox.isManaged()) panes.add(previewBox);
        if (detailsBox != null && detailsBox.isManaged()) panes.add(detailsBox);

        if (panes.isEmpty()) {
            return;
        }

        focusCycleIndex = (focusCycleIndex + 1) % panes.size();
        panes.get(focusCycleIndex).requestFocus();
    }

    private void scrollToTop() {
        LogSupport.enter(LOG, "scrollToTop");
        if (viewMode == ViewMode.DETAILS) {
            if (!tableItems.isEmpty()) {
                fileTable.scrollTo(0);
            }
            return;
        }

        if (iconScroll != null) {
            iconScroll.setVvalue(0.0);
        }
    }

    private void scrollToBottom() {
        LogSupport.enter(LOG, "scrollToBottom");
        if (viewMode == ViewMode.DETAILS) {
            if (!tableItems.isEmpty()) {
                fileTable.scrollTo(tableItems.size() - 1);
            }
            return;
        }

        if (iconScroll != null) {
            iconScroll.setVvalue(1.0);
        }
    }

    private void refresh() {
        LogSupport.enter(LOG, "refresh");

        // Preserve current selection (table) if possible.
        try {
            if (fileTable != null) {
                com.fileexplorer.model.FileItem sel = fileTable.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    pendingReselectPath = sel.path();
                    pendingReselectIndex = fileTable.getSelectionModel().getSelectedIndex();
                    pendingRestoreSelection = true;
                } else {
                    pendingRestoreSelection = false;
                    pendingReselectPath = null;
                    pendingReselectIndex = -1;
                }
            }
        } catch (Exception ex) {
            pendingRestoreSelection = false;
            pendingReselectPath = null;
            pendingReselectIndex = -1;
        }

        // Refresh the selected tree node (re-probe chevron/children) if it supports lazy loading.
        refreshSelectedTreeNode();

        Path dir = currentDirectory;
        if (dir != null) {
            loadDirectoryIntoTableAsync(dir);
        }
    }

    
    /**
     * Phase 3.5.1: Re-probe the currently selected TreeView node if it supports lazy loading.
     */
    private void refreshSelectedTreeNode() {
        try {
            if (folderTree == null) {
                return;
            }
            TreeItem<java.nio.file.Path> sel = folderTree.getSelectionModel().getSelectedItem();
            if (sel instanceof com.fileexplorer.service.filesystem.TreeBuildService.LazyLoadingTreeItem lazy) {
                lazy.invalidate();
            }
        } catch (Exception ex) {
            // ignore
        }
    }

private void toggleFullScreen() {
        LogSupport.enter(LOG, "toggleFullScreen");
        Scene scene = (themeToggle != null) ? themeToggle.getScene() : (fileTable != null ? fileTable.getScene() : null);
        if (scene == null) {
            return;
        }
        Window w = scene.getWindow();
        if (!(w instanceof Stage stage)) {
            return;
        }
        stage.setFullScreen(!stage.isFullScreen());
    }

    private void closeCurrentWindow() {
        LogSupport.enter(LOG, "closeCurrentWindow");
        Scene scene = (themeToggle != null) ? themeToggle.getScene() : (fileTable != null ? fileTable.getScene() : null);
        if (scene == null) {
            return;
        }
        Window w = scene.getWindow();
        if (w instanceof Stage) {
            ((Stage) w).close();
        }
    }

    private void openNewWindow() {
        LogSupport.enter(LOG, "openNewWindow");
        openNewWindow(currentDirectory);
    }

    private void openNewWindow(Path initialFolder) {
        LogSupport.enter(LOG, "openNewWindow");
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                MainApp.configureExplorerStage(stage, Objects.requireNonNullElseGet(initialFolder, () -> Paths.get(System.getProperty("user.home"))), themeService.isDarkPreferred());
                stage.show();
            } catch (IOException ex) {
                // ignore
            }
        });
    }

// NOTE: Fully expanding the entire filesystem tree is an easy way to create millions of TreeItems,
// which will exhaust the heap (and can also violate JavaFX thread-confinement if done off-thread).
// This implementation performs a *bounded* expansion on the JavaFX Application Thread.
private static final int NAV_EXPAND_MAX_DEPTH = 64;     // 0=root, 1=children, 2=grandchildren
private static final int NAV_EXPAND_MAX_NODES = 5_000; // hard cap to prevent OOME
private static final int NAV_EXPAND_BATCH = 150;       // nodes expanded per pulse

private void expandAllFoldersInNavigationPane() {
    final TreeItem<Path> root = folderTree != null ? folderTree.getRoot() : null;
    if (root == null) {
        setStatus("Navigation tree is not available.");
        return;
    }

    setStatus("Expanding navigation tree...");
    com.fileexplorer.ui.tree.TreeViewSupport.expandAllAsync(root, NAV_EXPAND_MAX_DEPTH);
}

private void collapseAllFoldersInNavigationPane() {
    final TreeItem<Path> root = folderTree != null ? folderTree.getRoot() : null;
    if (root == null) {
        setStatus("Navigation tree is not available.");
        return;
    }
    setStatus("Collapsing navigation tree...");
    com.fileexplorer.ui.tree.TreeViewSupport.collapseAll(root);
}

    private record NavExpandNode(TreeItem<Path> item, int depth) {
    }

private void expandNavigationTreeLimited(TreeItem<Path> root, int maxDepth, int maxNodes) {
    // All TreeItem interaction must occur on the JavaFX Application Thread.
    final java.util.ArrayDeque<NavExpandNode> queue = new java.util.ArrayDeque<>();
    queue.add(new NavExpandNode(root, 0));

    final java.util.concurrent.atomic.AtomicInteger expanded = new java.util.concurrent.atomic.AtomicInteger(0);

    final Runnable pump = new Runnable() {
        @Override
        public void run() {
            int budget = NAV_EXPAND_BATCH;

            while (budget-- > 0 && !queue.isEmpty()) {
                final NavExpandNode nd = queue.removeFirst();
                final TreeItem<Path> item = nd.item;
                if (item == null) {
                    continue;
                }

                final int n = expanded.incrementAndGet();
                if (n > maxNodes) {
                    setStatus("Expansion stopped after " + maxNodes + " nodes (safety cap)." );
                    return;
                }

                if (!item.isExpanded()) {
                    item.setExpanded(true); // may trigger lazy-loading of children
                }

                if (nd.depth >= maxDepth) {
                    continue;
                }

                // Only traverse children currently present. We do NOT wait for lazy-load completion,
                // and we do NOT force-load deeper levels (that is precisely what caused OOME).
                for (TreeItem<Path> child : item.getChildren()) {
                    queue.addLast(new NavExpandNode(child, nd.depth + 1));
                }
            }

            if (!queue.isEmpty()) {
                Platform.runLater(this);
            } else {
                setStatus("Expanded " + expanded.get() + " navigation nodes (depth ≤ " + maxDepth + ").");
            }
        }
    };

    Platform.runLater(pump);
}


    // ---------------------------------------------------------------------
    // Breadcrumb selection support (existing)
    // ---------------------------------------------------------------------

    private void expandAndSelectFolder(Path target) {
        LogSupport.enter(LOG, "expandAndSelectFolder");
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

        for (Path seg : relative) {
            if (seg == null) {
                continue;
            }
            String segName = seg.toString();
            if (segName == null || segName.isBlank()) {
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
        LogSupport.enter(LOG, "findChildByName");
        if (parent == null || name == null) {
            return null;
        }

        List<TreeItem<Path>> children = parent.getChildren();
        if (children == null || children.isEmpty()) {
            return null;
        }

        String needle = name.toLowerCase(Locale.ROOT);

        for (TreeItem<Path> c : children) {
            if (c == null || c.getValue() == null) {
                continue;
            }
            String disp = fileMetadataService.displayName(c.getValue());
            if (disp == null) {
                continue;
            }
            if (disp.toLowerCase(Locale.ROOT).equals(needle)) {
                return c;
            }
        }
        return null;
    }

    private void syncThemeToggleText() {
        LogSupport.enter(LOG, "syncThemeToggleText");
        themeToggle.setText(themeToggle.isSelected() ? "Dark" : "Light");
    }

    private void setStatus(String text) {
        LogSupport.enter(LOG, "setStatus");
        if (statusLabel != null) {
            statusLabel.setText(text == null ? "" : text);
        }
    }

    private void wireViewMenuHandlers() {
        LogSupport.enter(LOG, "wireViewMenuHandlers");
        // Wire view mode radio items (mutually exclusive).
        wireViewModeRadio(viewExtraLargeIcons, ViewMode.EXTRA_LARGE_ICONS);
        wireViewModeRadio(viewLargeIcons, ViewMode.LARGE_ICONS);
        wireViewModeRadio(viewMediumIcons, ViewMode.MEDIUM_ICONS);
        wireViewModeRadio(viewSmallIcons, ViewMode.SMALL_ICONS);
        wireViewModeRadio(viewList, ViewMode.LIST);
        wireViewModeRadio(viewDetails, ViewMode.DETAILS);
        wireViewModeRadio(viewTiles, ViewMode.TILES);
        wireViewModeRadio(viewContent, ViewMode.CONTENT);

        // Wire pane radios (independent "dot toggles" by design).
        if (detailsPaneMenuItem != null) {
            detailsPaneMenuItem.setOnAction(this::onDetailsPaneRadioToggle);
        }
        if (previewPaneMenuItem != null) {
            previewPaneMenuItem.setOnAction(this::onPreviewPaneRadioToggle);
        }

        // Wire "Show" submenu checkboxes.
        if (showNavigationPaneMenuItem != null) {
            showNavigationPaneMenuItem.setOnAction(this::onShowNavigationPaneToggle);
        }
        if (showCompactViewMenuItem != null) {
            showCompactViewMenuItem.setOnAction(this::onCompactViewToggle);
        }
        if (showItemCheckBoxesMenuItem != null) {
            showItemCheckBoxesMenuItem.setOnAction(this::onItemCheckBoxesToggle);
        }
        if (showFileNameExtensionsMenuItem != null) {
            showFileNameExtensionsMenuItem.setOnAction(this::onFileNameExtensionsToggle);
        }
        if (showHiddenItemsMenuItem != null) {
            showHiddenItemsMenuItem.setOnAction(this::onHiddenItemsToggle);
        }
    }

    private void wireViewModeRadio(RadioButton item, ViewMode mode) {
        LogSupport.enter(LOG, "wireViewModeRadio");
        if (item == null || mode == null) {
            return;
        }
        item.setToggleGroup(viewModeToggleGroup);
        item.setUserData(mode.name());
        item.setOnAction(this::onViewModeRadio);
    }

    private void configureStatusBar() {
        LogSupport.enter(LOG, "configureStatusBar");
        if (statusLabel != null) {
            statusLabel.setMinHeight(Region.USE_PREF_SIZE);
            statusLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            statusLabel.setPadding(new Insets(2, 8, 2, 8));
            statusLabel.setMaxWidth(Double.MAX_VALUE);
            statusLabel.setAlignment(Pos.CENTER_LEFT);
        }
        if (locationLabel != null) {
            locationLabel.setMinHeight(Region.USE_PREF_SIZE);
            locationLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            locationLabel.setPadding(new Insets(2, 8, 2, 8));
            locationLabel.setMaxWidth(Double.MAX_VALUE);
            locationLabel.setAlignment(Pos.CENTER_LEFT);
        }
    }

    private void ensureStartupWindowSize(Scene scene) {
        LogSupport.enter(LOG, "ensureStartupWindowSize");
        if (scene == null) {
            return;
        }
        Window w = scene.getWindow();
        if (!(w instanceof Stage stage)) {
            return;
        }

        stage.setMinWidth(MIN_WINDOW_WIDTH);
        stage.setMinHeight(MIN_WINDOW_HEIGHT);

        // Clamp to visible bounds to avoid off-screen restore.
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();

        double prefW = prefs.getDouble(PREF_WIN_W, STARTUP_WIDTH);
        double prefH = prefs.getDouble(PREF_WIN_H, STARTUP_HEIGHT);

        if (prefW < MIN_WINDOW_WIDTH) {
            prefW = MIN_WINDOW_WIDTH;
        }
        if (prefH < MIN_WINDOW_HEIGHT) {
            prefH = MIN_WINDOW_HEIGHT;
        }

        if (prefW > bounds.getWidth()) {
            prefW = bounds.getWidth();
        }
        if (prefH > bounds.getHeight()) {
            prefH = bounds.getHeight();
        }

        // If the stage hasn't been sized yet, apply our defaults/restored preferences.
        if (stage.getWidth() < 200.0 || stage.getHeight() < 200.0) {
            stage.setWidth(prefW);
            stage.setHeight(prefH);
        }

        boolean maximized = prefs.getBoolean(PREF_WIN_MAX, false);
        stage.setMaximized(maximized);

        if (!windowPrefsInstalled) {
            windowPrefsInstalled = true;

            stage.widthProperty().addListener((_, _, newV) -> {
                if (!stage.isMaximized()) {
                    prefs.putDouble(PREF_WIN_W, Math.max(MIN_WINDOW_WIDTH, newV.doubleValue()));
                }
            });

            stage.heightProperty().addListener((_, _, newV) -> {
                if (!stage.isMaximized()) {
                    prefs.putDouble(PREF_WIN_H, Math.max(MIN_WINDOW_HEIGHT, newV.doubleValue()));
                }
            });

            stage.maximizedProperty().addListener((_, _, newV) ->
                prefs.putBoolean(PREF_WIN_MAX, newV));
        }
    }



    private String buildUiFontFamilyCss(Scene scene) {
        LogSupport.enter(LOG, "buildUiFontFamilyCss");
        String preferred = null;

        // Highest priority: value passed from MainApp.
        if (scene != null && scene.getRoot() != null) {
            Object fam = scene.getRoot().getProperties().get(PROP_UI_FONT_FAMILY);
            if (fam instanceof String s && !s.isBlank()) {
                preferred = s;
            }
        }

        // Next: installed families (if present).
        if (preferred == null || preferred.isBlank()) {
            if (Font.getFamilies().contains("Segoe UI Variable")) {
                preferred = "Segoe UI Variable";
            } else if (Font.getFamilies().contains("Segoe UI")) {
                preferred = "Segoe UI";
            }
        }

        // System default family as second fallback (e.g., DejaVu Sans on Linux).
        systemFontFamilyResolved = resolveSystemFontFamily();
        if (preferred == null || preferred.isBlank()) {
            preferred = systemFontFamilyResolved;
        }

        uiFontFamilyResolved = preferred;

        String sys = (systemFontFamilyResolved == null || systemFontFamilyResolved.isBlank())
                ? "System"
                : systemFontFamilyResolved;

        String pref = (preferred == null || preferred.isBlank())
                ? sys
                : preferred;

        // Quote to handle spaces.
        return "'" + escapeCssFontFamily(pref) + "', 'Segoe UI', '" + escapeCssFontFamily(sys) + "'";
    }

    private String resolveSystemFontFamily() {
        LogSupport.enter(LOG, "resolveSystemFontFamily");
        try {
            Font f = Font.getDefault();
            if (f != null) {
                String fam = f.getFamily();
                if (fam != null && !fam.isBlank()) {
                    return fam;
                }
            }
        } catch (RuntimeException ex) {
            // ignore
        }
        return "System";
    }

    private String escapeCssFontFamily(String family) {
        LogSupport.enter(LOG, "escapeCssFontFamily");
        if (family == null) {
            return "";
        }
        return family.replace("'", "\\'");
    }

    private String resolveTreeTextFamily() {
        LogSupport.enter(LOG, "resolveTreeTextFamily");
        // Prefer the resolved UI family, otherwise system default.
        if (uiFontFamilyResolved != null && !uiFontFamilyResolved.isBlank()) {
            return uiFontFamilyResolved;
        }
        if (systemFontFamilyResolved != null && !systemFontFamilyResolved.isBlank()) {
            return systemFontFamilyResolved;
        }
        return "System";
    }



// ---------------------------------------------------------------------
// Startup guards
// ---------------------------------------------------------------------
    private final IdentityHashMap<Region, Double> startupGuardOriginalMaxHeights = new IdentityHashMap<>();
    private final IdentityHashMap<Region, Double> startupGuardOriginalMaxWidths = new IdentityHashMap<>();

    /**
     * Releases the temporary max-size clamps applied by the startup virtualization guards.
     * <p>
     * These clamps exist to prevent VirtualFlow from allocating an unbounded number of cells during the first
     * Stage.show() / CSS / initial layout pass (a known failure mode when a virtualized control reports an
     * extreme preferred size early in startup).
     */
    public void releaseStartupVirtualizationGuards() {
        if (Boolean.getBoolean("fileexplorer.ui.guard.keepMaxClamps")) {
            if (RESOURCE_AUDIT) {
                LOG.info("Startup virtualization guards retained due to fileexplorer.ui.guard.keepMaxClamps=true");
            }
            return;
        }
        if (startupGuardOriginalMaxHeights.isEmpty() && startupGuardOriginalMaxWidths.isEmpty()) {
            return;
        }

        for (Region r : startupGuardOriginalMaxHeights.keySet()) {
            Double mh = startupGuardOriginalMaxHeights.get(r);
            if (mh != null) {
                r.setMaxHeight(mh);
            }
            Double mw = startupGuardOriginalMaxWidths.get(r);
            if (mw != null) {
                r.setMaxWidth(mw);
            }
        }

        startupGuardOriginalMaxHeights.clear();
        startupGuardOriginalMaxWidths.clear();

        if (RESOURCE_AUDIT) {
            LOG.info("Startup virtualization guards released.");
        }
    }



private void enforceStartupFixedCellSizes() {
    // TreeView: MUST be > 0 to avoid VirtualFlow addTrailingCells() runaway when a cell reports 0 height.
    if (folderTree != null) {
        double v = syspropDouble("fileexplorer.ui.tree.fixedCellSize", 22.0);
        v = clamp(v, 16.0, 96.0);
        // TreeView uses <=0 to mean "variable" sizing; enforce a positive value.
        if (!(v > 0.0)) {
            v = 22.0;
        }
        folderTree.setFixedCellSize(v);
    }

    // TableView: optional; keep disabled by default to preserve variable row heights unless requested.
    if (fileTable != null) {
        double v = syspropDouble("fileexplorer.ui.table.fixedCellSize", -1.0);
        if (v > 0.0) {
            v = clamp(v, 16.0, 128.0);
            fileTable.setFixedCellSize(v);
        }
    }
}

    /**
     * Defensive sizing guard for virtualized controls (TreeView/TableView/ListView/etc).
     * The goal is to prevent extremely large computed preferred sizes from propagating
     * into Stage/Scene preferred sizing during the first layout pass, which can trigger
     * runaway VirtualFlow cell creation.
     *
     * Set values via:
     *   -Dfileexplorer.ui.tree.prefHeight=720
     *   -Dfileexplorer.ui.tree.prefWidth=280
     *   -Dfileexplorer.ui.table.prefHeight=720
     */

    private static double readDoubleSystemProperty(String key, double defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private void enforceVirtualizedPrefSize(Region region,
                                           String prefHeightKey,
                                           double defaultPrefHeight,
                                           String prefWidthKey,
                                           double defaultPrefWidth) {
        if (region == null) {
            return;
        }
        double prefHeight = readDoubleSystemProperty(prefHeightKey, defaultPrefHeight);
        double prefWidth = readDoubleSystemProperty(prefWidthKey, defaultPrefWidth);
        String label = region.getClass().getSimpleName();
        enforceVirtualizedPrefSize(region, label, prefWidth, prefHeight);
    }

    private void enforceVirtualizedPrefSize(Region region, String label, double prefWidth, double prefHeight) {
        if (region == null) {
            return;
        }

        // Record originals once so we can release the clamps after first successful show/layout.
        if (!startupGuardOriginalMaxHeights.containsKey(region)) {
            startupGuardOriginalMaxHeights.put(region, region.getMaxHeight());
            startupGuardOriginalMaxWidths.put(region, region.getMaxWidth());
        }

        if (prefWidth > 0) {
            region.setPrefWidth(prefWidth);
            region.setMaxWidth(prefWidth);
        }

        if (prefHeight > 0) {
            region.setPrefHeight(prefHeight);
            region.setMaxHeight(prefHeight);
        }

        if (RESOURCE_AUDIT) {
            LOG.info("Startup virtualization guard applied for " + label + ": prefWidth=" + prefWidth + ", prefHeight=" + prefHeight);
        }
    }








private static double syspropDouble(String key, double def) {
    try {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        return Double.parseDouble(v.trim());
    } catch (Exception ex) {
        return def;
    }
}


private static boolean syspropBoolean(String key, boolean def) {
    try {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        String s = v.trim().toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "y".equals(s) || "on".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "0".equals(s) || "no".equals(s) || "n".equals(s) || "off".equals(s)) {
            return false;
        }
        return Boolean.parseBoolean(s);
    } catch (Exception ex) {
        return def;
    }
}


    /**
     * Ensures the TreeView has the style class required by ui_fixes.css for readable text rendering.
     * This is critical on some themes where TreeCell text-fill is otherwise computed as transparent/low-contrast.
     */
        /**
     * Ensures the TreeView has the style class required by ui_fixes.css for readable text rendering.
     * Adds the Explorer-like class and stylesheet used for consistent spacing/colors.
     */
    private void ensureTreeViewStyleClass() {
        LogSupport.enter(LOG, "ensureTreeViewStyleClass");
        if (folderTree == null) {
            return;
        }
        if (!folderTree.getStyleClass().contains("tree-view-fixed")) {
            folderTree.getStyleClass().add("tree-view-fixed");
        }
        if (!folderTree.getStyleClass().contains("explorer-tree")) {
            folderTree.getStyleClass().add("explorer-tree");
        }

        // Ensure explorer_tree.css is loaded once the TreeView is attached to a Scene.
        folderTree.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            try {
                var url = MainController.class.getResource("/css/explorer_tree.css");
                if (url == null) return;
                String css = url.toExternalForm();
                if (!newScene.getStylesheets().contains(css)) {
                    newScene.getStylesheets().add(css);
                }
            } catch (Exception ignored) {
                // Non-fatal: styling will fall back to existing CSS.
            }
        });
    }

    /**
     * Phase 3.4: deterministic teardown hook.
     * <p>
     * Releases EventBus subscriptions and any other aggregated listeners.
     * Safe to call multiple times.
     */
    public void dispose() {
        try {
            localDisposables.close();
        } catch (Exception ignored) {
        }

        // Stop hover prefetch timer if it exists.
        try {
            if (hoverPrefetchTimer != null) {
                hoverPrefetchTimer.stop();
            }
        } catch (Exception ignored) {
        }

        // Shut down executors to avoid hanging process on exit.
        try {
            if (hoverPrefetchExecutor != null) {
                hoverPrefetchExecutor.shutdownNow();
            }
        } catch (Exception ignored) {
        }
        try {
            if (ioExecutor != null) {
                ioExecutor.shutdownNow();
            }
        } catch (Exception ignored) {
        }

        // Dispose child controllers.
        try {
            if (breadcrumbBarController != null) {
                breadcrumbBarController.dispose();
            }
        } catch (Exception ignored) {
        }
    }

}