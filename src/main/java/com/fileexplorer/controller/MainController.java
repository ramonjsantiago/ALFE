package com.fileexplorer.controller;
import com.fileexplorer.app.MainApp;
import com.fileexplorer.perf.viewport.BudgetedViewportScheduler;
import com.fileexplorer.perf.viewport.RealizationPriorityBand;
import com.fileexplorer.perf.viewport.ViewportBandClassifier;
import com.fileexplorer.perf.viewport.ViewportSchedulerTelemetry;
import com.fileexplorer.perf.viewport.ViewportWorkItem;
import com.fileexplorer.util.CompositeCloseable;
import com.fileexplorer.util.FolderSnapshotCache;
import com.fileexplorer.lifecycle.Lifecycle;
import com.fileexplorer.service.filesystem.FileMetadataService;
import com.fileexplorer.service.theme.ThemeService;
import com.fileexplorer.service.filesystem.TreeBuildService;
import java.io.File;
import java.time.Clock;
import java.io.IOException;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.stage.Screen;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.OverrunStyle;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import java.util.prefs.Preferences;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.InvalidationListener;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.css.PseudoClass;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
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
import javafx.stage.Popup;
import javafx.util.Callback;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.control.TableRow;
import javafx.scene.input.MouseButton;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.geometry.Orientation;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.fileexplorer.util.LogSupport;
import com.fileexplorer.util.StartupTrace;
import com.fileexplorer.util.StartupWorkQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;
import java.awt.Desktop;
import java.awt.HeadlessException;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import java.nio.charset.StandardCharsets;
import com.fileexplorer.controller.breadcrumb.BreadcrumbController;
import com.fileexplorer.util.IconLoader;
import com.fileexplorer.util.ImageSupport;
import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.ui.fileview.details.DetailsViewController;
import com.fileexplorer.ui.fileview.host.FileViewHost;
import com.fileexplorer.ui.fileview.shared.AbstractIconFlowFileViewController;
import com.fileexplorer.service.icon.AsyncIconService;
import com.fileexplorer.service.icon.AsyncThumbnailService;
import com.fileexplorer.service.icon.IconCacheService;
import com.fileexplorer.ui.table.TableViewSupport;
import com.fileexplorer.ui.table.TableHeaderContextMenuInstaller;
import com.fileexplorer.ui.table.DetailsViewRefreshCoordinator;
import com.fileexplorer.ui.table.VisibleThumbnailManager;
import com.fileexplorer.ui.table.DetailColumnCatalog;
import com.fileexplorer.ui.dialog.ChooseDetailsDialog;
import com.fileexplorer.ui.tree.TreeViewSupport;
import com.fileexplorer.ui.tree.SimplePathTreeCell;
import com.fileexplorer.ui.tree.IconPathTreeCell;
import com.fileexplorer.ui.motion.FluentMotionSupport;
import com.fileexplorer.model.FileItem;
import com.fileexplorer.service.filesystem.DirectoryListingService;
import com.fileexplorer.service.filesystem.DirectoryLoadManager;
import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.coordinator.DirectoryCoordinator;
import com.fileexplorer.service.event.events.DirectoryLoadSucceeded;
import com.fileexplorer.service.event.events.DirectoryLoadFailed;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Modality;
/**
 * MainController.
 * <p>
 * Auto-generated API documentation for this type.
 */
public final class MainController implements Initializable, Lifecycle {
    private static final Logger LOG = Logger.getLogger(MainController.class.getName());
    private final CompositeCloseable localDisposables = new CompositeCloseable();
    // ---------------------------
    // Phase 3.6.0: File operations (copy/move/delete/rename)
    private com.fileexplorer.service.ops.FileOperationService fileOperationService;
    private final java.util.List<java.nio.file.Path> clipboardPaths = new java.util.ArrayList<>();
    private boolean clipboardCut = false;
    private volatile long activeFileOpJobId = -1L;
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
    private static final double FOLDER_TREE_ROW_HEIGHT_PX = 24.0;
    private static final Insets FOLDER_TREE_CELL_PADDING = new Insets(1.0, 8.0, 1.0, 6.0);
    private static final String EXPLORER_ICON_TILE_PATH_KEY = "explorer.iconTilePath";
    private static final String EXPLORER_ICON_TILE_HOVER_HANDLER_KEY = "explorer.iconTileHoverHandlerInstalled";
    private static final String EXPLORER_ICON_TILE_INLINE_RENAME_NODE_KEY = "explorer.iconTileInlineRenameNode";
    private static final double UI_FONT_DEFAULT_PX = 16.0;
    private static final double UI_FONT_MIN_PX = 12.0;
    private static final double UI_FONT_MAX_PX = 32.0;
    private static final double UI_FONT_STEP_PX = 2.0;
    // Minimum vertical padding budget (top and bottom) used for runtime metrics.
    private static final double UI_MIN_VPAD_PX = 5.0;
    private static final double ICON_SIZE_MIN = 48.0;
    private static final double ICON_SIZE_MAX = 256.0;
    private static final double EXTRA_LARGE_ICON_CELL_PX = 256.0;
    private static final double ICON_SIZE_STEP = 12.0;
    private static final String PROP_UI_FONT_PX = "main.uiFontPx";
    private static final String PROP_UI_FONT_FAMILY = "main.uiFontFamily";
    private static final double STARTUP_WIDTH = 1280.0;
    private static final double STARTUP_HEIGHT = 800.0;
    private static final double MIN_WINDOW_WIDTH = 980.0;
    private static final double MIN_WINDOW_HEIGHT = 640.0;
    private static final String REALIZATION_ICON_STAMP_KEY = "explorer.iconStamp";
    private static final int DETAILS_PREFETCH_ROWS_BEFORE = Integer.getInteger("fileexplorer.realization.details.prefetchBefore", 16);
    private static final int DETAILS_PREFETCH_ROWS_AFTER = Integer.getInteger("fileexplorer.realization.details.prefetchAfter", 32);
    private static final int ICON_PREFETCH_ROWS_BEFORE = Integer.getInteger("fileexplorer.realization.icon.prefetchBeforeRows", 1);
    private static final int ICON_PREFETCH_ROWS_AFTER = Integer.getInteger("fileexplorer.realization.icon.prefetchAfterRows", 2);
    private static final long SCROLL_HITCH_LOG_THRESHOLD_MS = Long.getLong("fileexplorer.scroll.hitchLogThresholdMs", 28L);
    private static final boolean LOG_SCROLL_TELEMETRY = Boolean.getBoolean("fileexplorer.scroll.telemetry");
    private static final boolean LOG_REALIZATION_TELEMETRY = Boolean.getBoolean("fileexplorer.realization.telemetry");
    private static final boolean LOG_CHROME_TELEMETRY = Boolean.getBoolean("fileexplorer.chrome.telemetry");
    private static final long CHROME_HITCH_LOG_THRESHOLD_MS = Long.getLong("fileexplorer.chrome.hitchLogThresholdMs", 18L);
    private static final String VIEW_MENU_BUTTON_GRAPHIC_SIGNATURE_KEY = "explorer.viewMenuButtonGraphicSignature";
    private static final Map<String, Image> VIEW_MENU_WHITE_ICON_CACHE = new ConcurrentHashMap<>();
    private static final long VIEWPORT_SETTLE_PASS_DELAY_MS = Long.getLong("fileexplorer.realization.settlePassDelayMs", 135L);
    private static final long VIEWPORT_FRAME_BUDGET_NANOS = Long.getLong("fileexplorer.realization.frameBudgetNanos", 6_000_000L);
    private static final long VIEWPORT_SCROLL_STOP_QUIET_MS = Long.getLong("fileexplorer.realization.scrollStopQuietMs", 120L);
    private static final int VIEWPORT_NEAR_THRESHOLD_CELLS = Integer.getInteger("fileexplorer.realization.nearThresholdCells", 12);
    private static final int VIEWPORT_FAR_WORK_LIMIT = Integer.getInteger("fileexplorer.realization.farWorkLimit", 24);
    private static final long VIEWPORT_VISIBLE_REALIZE_ESTIMATE_NANOS = Long.getLong("fileexplorer.realization.visibleRealizeEstimateNanos", 55_000L);
    private static final long VIEWPORT_NEAR_REALIZE_ESTIMATE_NANOS = Long.getLong("fileexplorer.realization.nearRealizeEstimateNanos", 40_000L);
    private static final long VIEWPORT_FAR_REALIZE_ESTIMATE_NANOS = Long.getLong("fileexplorer.realization.farRealizeEstimateNanos", 25_000L);
    private static final long VIEWPORT_VISIBLE_PROMOTION_ESTIMATE_NANOS = Long.getLong("fileexplorer.realization.visiblePromotionEstimateNanos", 30_000L);
    private static final long VIEWPORT_NEAR_PROMOTION_ESTIMATE_NANOS = Long.getLong("fileexplorer.realization.nearPromotionEstimateNanos", 20_000L);
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

    private enum ScrollVelocityBucket {
        SETTLE(0.65, 0.45),
        SLOW(1.00, 0.75),
        MEDIUM(1.35, 0.85),
        FAST(1.85, 1.00),
        FLING(2.60, 1.20);

        final double leadingMultiplier;
        final double trailingMultiplier;

        ScrollVelocityBucket(double leadingMultiplier, double trailingMultiplier) {
            this.leadingMultiplier = leadingMultiplier;
            this.trailingMultiplier = trailingMultiplier;
        }
    }

    private static final class ExplorerCommandStateSnapshot {
        private final int selectionCount;
        private final Path primarySelection;
        private final boolean homeActive;
        private final boolean canPaste;
        private final boolean hasDirectory;
        private final boolean hasVisibleItems;
        private final boolean canUndo;
        private final String undoMenuLabel;
        private final boolean singleDirectorySelection;
        private final boolean pinnedDirectorySelection;
        private final ViewMode viewMode;
        private final SortKey sortKey;

        private ExplorerCommandStateSnapshot(int selectionCount,
                                             Path primarySelection,
                                             boolean homeActive,
                                             boolean canPaste,
                                             boolean hasDirectory,
                                             boolean hasVisibleItems,
                                             boolean canUndo,
                                             String undoMenuLabel,
                                             boolean singleDirectorySelection,
                                             boolean pinnedDirectorySelection,
                                             ViewMode viewMode,
                                             SortKey sortKey) {
            this.selectionCount = selectionCount;
            this.primarySelection = primarySelection;
            this.homeActive = homeActive;
            this.canPaste = canPaste;
            this.hasDirectory = hasDirectory;
            this.hasVisibleItems = hasVisibleItems;
            this.canUndo = canUndo;
            this.undoMenuLabel = undoMenuLabel;
            this.singleDirectorySelection = singleDirectorySelection;
            this.pinnedDirectorySelection = pinnedDirectorySelection;
            this.viewMode = viewMode;
            this.sortKey = sortKey;
        }

        private boolean semanticallyEquals(ExplorerCommandStateSnapshot other) {
            if (other == null) {
                return false;
            }
            return selectionCount == other.selectionCount
                    && homeActive == other.homeActive
                    && canPaste == other.canPaste
                    && hasDirectory == other.hasDirectory
                    && hasVisibleItems == other.hasVisibleItems
                    && canUndo == other.canUndo
                    && singleDirectorySelection == other.singleDirectorySelection
                    && pinnedDirectorySelection == other.pinnedDirectorySelection
                    && viewMode == other.viewMode
                    && sortKey == other.sortKey
                    && Objects.equals(primarySelection, other.primarySelection)
                    && Objects.equals(undoMenuLabel, other.undoMenuLabel);
        }
    }

    private static final class ViewportContinuityState {
        private final long token;
        private final Path directory;
        private final java.util.List<Path> selectedPaths;
        private final Path focusPath;
        private final Path anchorPath;
        private final Path firstVisiblePath;
        private final int firstVisibleIndex;
        private final double tableScrollValue;
        private final double flowScrollValue;
        private final double virtualGridScrollValue;
        private final double virtualListScrollValue;
        private final ViewMode viewMode;

        private ViewportContinuityState(long token,
                                        Path directory,
                                        java.util.List<Path> selectedPaths,
                                        Path focusPath,
                                        Path anchorPath,
                                        Path firstVisiblePath,
                                        int firstVisibleIndex,
                                        double tableScrollValue,
                                        double flowScrollValue,
                                        double virtualGridScrollValue,
                                        double virtualListScrollValue,
                                        ViewMode viewMode) {
            this.token = token;
            this.directory = directory;
            this.selectedPaths = selectedPaths == null ? java.util.List.of() : java.util.List.copyOf(selectedPaths);
            this.focusPath = focusPath;
            this.anchorPath = anchorPath;
            this.firstVisiblePath = firstVisiblePath;
            this.firstVisibleIndex = firstVisibleIndex;
            this.tableScrollValue = tableScrollValue;
            this.flowScrollValue = flowScrollValue;
            this.virtualGridScrollValue = virtualGridScrollValue;
            this.virtualListScrollValue = virtualListScrollValue;
            this.viewMode = viewMode;
        }
    }
    @FXML private TreeView<Path> folderTree;
    @FXML private TableView<FileItem> fileTable;
    @FXML private TableColumn<FileItem, String> colName;
    @FXML private javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, javafx.scene.Node> colStatus;
    @FXML private TableColumn<FileItem, String> colType;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colModified;
    // Optional Explorer-like columns (created lazily from the Choose Details dialog).
    private TableColumn<FileItem, String> colDateCreated;
    private TableColumn<FileItem, String> colAuthors;
    private TableColumn<FileItem, String> colTags;
    private TableColumn<FileItem, String> colTitle;
    private static final String PROP_DETAIL_COLUMN_KEY = "fileexplorer.detailColumnKey";
    private static final String PROP_DETAILS_HEADER_INTERACTION_INSTALLED = "fileexplorer.detailsHeaderInteractionInstalled";
    private static final double DETAILS_HEADER_RESIZE_EDGE_PX = 6.0;
    private final java.util.LinkedHashMap<String, javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?>> lazyDetailColumns = new java.util.LinkedHashMap<>();
    private java.util.List<com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec> chooseDetailSpecs = java.util.List.of();
    private java.util.Map<String, com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec> chooseDetailSpecsByKey = java.util.Map.of();
    private static final PseudoClass PSEUDO_EXPLORER_HOVER = PseudoClass.getPseudoClass("explorer-hover");
    private static final PseudoClass PSEUDO_EXPLORER_SELECTED = PseudoClass.getPseudoClass("explorer-selected");
    private static final String PROP_DETAILS_ROW_STYLE_CACHE = "fileexplorer.detailsRowStyleCache";
    private final IntegerProperty detailsHoverRowIndex = new SimpleIntegerProperty(-1);
    private final ObjectProperty<TableRow<FileItem>> activeDetailsHoverRow = new SimpleObjectProperty<>(null);
    private boolean stableDetailsHoverTrackingInstalled = false;
    private final java.util.Map<String, Double> detailColumnWidths = new java.util.HashMap<>();
    private final java.util.List<String> detailOrderedKeys = new java.util.ArrayList<>();
    private final javafx.animation.PauseTransition detailsColumnsPersistDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(180));
    private final ObjectProperty<Node> activeDetailsResizeHotHeader = new SimpleObjectProperty<>(null);
    private boolean detailsColumnsPersistenceWired = false;
    @FXML private ToggleButton themeToggle;
    @FXML private ToggleButton detailsToggle;
    @FXML private ToggleButton previewToggle;
    @FXML private ToggleButton operationsToggle;
    @FXML private BorderPane root;
    @FXML private HBox workspaceShell;
    @FXML private BorderPane contentPane;
    @FXML private StackPane navigationPaneShell;
    @FXML private Region navigationResizer;
    @FXML private Region inspectorResizer;
    @FXML private StackPane inspectorHost;
    @FXML private SplitPane mainSplitPane;
    @FXML private SplitPane contentSplitPane;
    @FXML private RadioButton viewExtraLargeIcons;
    @FXML private RadioButton viewLargeIcons;
    @FXML private RadioButton viewMediumIcons;
    @FXML private RadioButton viewSmallIcons;
    @FXML private RadioButton viewList;
    @FXML private RadioButton viewDetails;
    @FXML private RadioButton viewTiles;
    @FXML private RadioButton viewContent;
    @FXML private CustomMenuItem viewContentItem;
    @FXML private CustomMenuItem detailsPaneRowItem;
    @FXML private CustomMenuItem previewPaneRowItem;
    @FXML private RadioButton detailsPaneMenuItem;
    @FXML private RadioButton previewPaneMenuItem;
    @FXML private CheckBox showNavigationPaneMenuItem;
    @FXML private CheckBox showCompactViewMenuItem;
    @FXML private CheckBox showItemCheckBoxesMenuItem;
    @FXML private CheckBox showFileNameExtensionsMenuItem;
    @FXML private CheckBox showHiddenItemsMenuItem;
    @FXML private Label statusLabel;
    @FXML private Label locationLabel;
    @FXML private ToggleButton statusDetailsButton;
    @FXML private ToggleButton statusLargeIconsButton;
    @FXML private HBox searchShell;
    @FXML private TextField searchField;
    @FXML private Button searchClearButton;
    @FXML private javafx.scene.control.ToolBar commandBar;
// --- Top command bar / toolbar (wired programmatically) -----------------
@FXML private javafx.scene.control.MenuButton newMenuButton;
@FXML private javafx.scene.control.Button backButton;
@FXML private javafx.scene.control.Button forwardButton;
@FXML private javafx.scene.control.Button upButton;
@FXML private javafx.scene.control.Button refreshButton;
@FXML private javafx.scene.control.Button cutButton;
@FXML private javafx.scene.control.Button copyButton;
@FXML private javafx.scene.control.Button pasteButton;
@FXML private javafx.scene.control.Button renameButton;
@FXML private javafx.scene.control.Button shareButton;
@FXML private javafx.scene.control.Button deleteButton;
@FXML private javafx.scene.control.MenuButton sortMenuButton;
@FXML private javafx.scene.control.MenuButton viewMenuButton;
@FXML private javafx.scene.control.MenuButton filterMenuButton;
    @FXML private javafx.scene.control.MenuButton seeMoreMenuButton;
    @FXML private HBox tabStrip;
    @FXML private Button homeTabButton;
    @FXML private Button currentTabButton;
    @FXML private Button closeTabButton;
    @FXML private Button newTabButton;
    @FXML private ScrollPane iconScroll;
    @FXML private StackPane viewHost;
    @FXML private FlowPane iconFlow;
    @FXML private StackPane detailsViewShell;
    private FileViewHost modularFileViewHost;
    private DetailsViewController modularDetailsViewController;
    private final java.util.EnumMap<ViewMode, AbstractIconFlowFileViewController> modularIconViewControllers = new java.util.EnumMap<>(ViewMode.class);
    private final java.util.Set<ScrollPane> iconScrollPagingTargets = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    @FXML private VBox homePane;
    @FXML private HBox homePinnedRow;
    @FXML private VBox recentFoldersBox;
    @FXML private Label homeCurrentLocationLabel;
    @FXML private VBox sidePane;
    @FXML private VBox previewBox;
    @FXML private VBox detailsBox;
    @FXML private VBox operationsBox;
    /**
     * Master visibility for the right-hand side pane (Details/Preview). This is controlled
     * by the toolbar "Details" toggle and by the View menu "Details pane" / "Preview pane" items.
     */
    private boolean sidePaneMasterVisible = false;
    private enum InspectorMode {
        HIDDEN,
        DETAILS,
        PREVIEW,
        OPERATIONS
    }
    private InspectorMode inspectorMode = InspectorMode.HIDDEN;
    private InspectorMode lastContentInspectorMode = InspectorMode.DETAILS;
    private Path inlineRenameTablePath;
    private Path inlineRenameTreePath;
    private Path pendingInlineRenameSelectionPath;
    private int pendingInlineRenameSelectionIndex = -1;
    private Path pendingCreateAndRenamePath;
    private InlineRenameSession pendingCreatedInlineRenameSession;
    private InlineRenameSession activeInlineRenameSession;
    private InlineRenameSession pendingInlineRenameRestoreSession;
    private Path pendingInlineRenameDraftPath;
    private String pendingInlineRenameDraftText;
    private boolean pendingInlineRenameDraftSelectAll;
    private Path inlineRenameEditTrackingPath;
    private boolean inlineRenameExplicitFullNameEdit;
    private InlineRenameSession pendingShellCommandRestoreSession;
    private Path pendingShellCommandRestorePath;
    private boolean pendingShellCommandRestoreFocusActiveSurface;
    private boolean pendingReselectPreferIndexOnMissing;

    private enum InlineRenameSessionKind { RENAME_EXISTING, CREATE_NEW }

    private enum InlineRenameSurface { FILE_DETAILS, FILE_ICON, TREE }

    private enum ExplorerCommandAction { EXECUTE, UNDO, REDO }

    private enum SearchSessionState { IDLE, TYPING, SEARCHING, RESULTS, NO_RESULTS }

    private static final class SearchComputationResult {
        private final int snapshotItemCount;
        private final int matchCount;
        private final java.util.List<FileItem> hugeFolderMatches;

        private SearchComputationResult(int snapshotItemCount, int matchCount, java.util.List<FileItem> hugeFolderMatches) {
            this.snapshotItemCount = snapshotItemCount;
            this.matchCount = matchCount;
            this.hugeFolderMatches = hugeFolderMatches == null ? java.util.List.of() : java.util.List.copyOf(hugeFolderMatches);
        }
    }

    private static final class InlineRenameSession {
        private final InlineRenameSessionKind kind;
        private final InlineRenameSurface surface;
        private final Path sourcePath;
        private final String originalDisplayName;
        private final java.util.List<Path> selectedPathsBefore;
        private final Path focusPathBefore;
        private final Path anchorPathBefore;
        private final Path treeSelectionPathBefore;
        private final int selectedIndexBefore;
        private final int focusedIndexBefore;
        private final ViewMode viewModeBefore;
        private final boolean treeFocusedBefore;
        private final boolean tableFocusedBefore;
        private final boolean iconSurfaceFocusedBefore;
        private final java.lang.ref.WeakReference<Node> priorFocusOwnerRef;
        private String requestedName;
        private Path pendingResultPath;
        private boolean awaitingCompletion;
        private Path commitViewportAnchorPath;
        private int commitViewportAnchorIndex = -1;
        private int commitViewportVisibleCount = -1;
        private double commitFlowScrollValue = Double.NaN;
        private double commitVirtualGridScrollValue = Double.NaN;
        private double commitVirtualListScrollValue = Double.NaN;
        private String originatingCommandId;

        private InlineRenameSession(InlineRenameSessionKind kind,
                                    InlineRenameSurface surface,
                                    Path sourcePath,
                                    String originalDisplayName,
                                    java.util.List<Path> selectedPathsBefore,
                                    Path focusPathBefore,
                                    Path anchorPathBefore,
                                    Path treeSelectionPathBefore,
                                    int selectedIndexBefore,
                                    int focusedIndexBefore,
                                    ViewMode viewModeBefore,
                                    boolean treeFocusedBefore,
                                    boolean tableFocusedBefore,
                                    boolean iconSurfaceFocusedBefore,
                                    Node priorFocusOwner) {
            this.kind = kind;
            this.surface = surface;
            this.sourcePath = sourcePath;
            this.originalDisplayName = originalDisplayName;
            this.selectedPathsBefore = selectedPathsBefore != null
                    ? new java.util.ArrayList<>(selectedPathsBefore)
                    : new java.util.ArrayList<>();
            this.focusPathBefore = focusPathBefore;
            this.anchorPathBefore = anchorPathBefore;
            this.treeSelectionPathBefore = treeSelectionPathBefore;
            this.selectedIndexBefore = selectedIndexBefore;
            this.focusedIndexBefore = focusedIndexBefore;
            this.viewModeBefore = viewModeBefore;
            this.treeFocusedBefore = treeFocusedBefore;
            this.tableFocusedBefore = tableFocusedBefore;
            this.iconSurfaceFocusedBefore = iconSurfaceFocusedBefore;
            this.priorFocusOwnerRef = new java.lang.ref.WeakReference<>(priorFocusOwner);
        }
    }

    private int inlineRenameFocusGuardPulsesRemaining;
    private boolean suppressTreeInlineRenameCancelEvent;
    private final java.util.List<Path> recentHomeLocations = new java.util.ArrayList<>();
    private final java.util.List<Path> userPinnedHomeLocations = new java.util.ArrayList<>();
    private boolean homeActive = false;
    private boolean homeTabVisible = true;
    private boolean currentTabVisible = true;
    private static final int HOME_RECENT_MAX = 8;
    private static final int HOME_PINNED_MAX = 8;
    @FXML private TextArea previewText;
    @FXML private javafx.scene.image.ImageView previewImage;
    @FXML private TextArea detailsText;
    @FXML private StackPane breadcrumbHost;
    @FXML private VBox progressPaneHost;
    private volatile BreadcrumbController breadcrumbBarController;
    private volatile ProgressPaneController progressPaneController;
    private final java.util.concurrent.atomic.AtomicBoolean deferredBreadcrumbLoadScheduled = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean deferredProgressPaneLoadScheduled = new java.util.concurrent.atomic.AtomicBoolean(false);
    
    // Phase 3.6.10 FIX2: Toolbar Sort menu state
    private enum SortKey { NAME, MODIFIED, TYPE, SIZE }
    private SortKey currentSortKey = SortKey.NAME;
    private boolean sortAscending = true;
private FileMetadataService fileMetadataService;
    private ThemeService themeService;
    private DirectoryListingService listingService;
    private DirectoryLoadManager directoryLoadManager;
    private EventBus eventBus;
    private DirectoryCoordinator directoryCoordinator;
    private volatile java.nio.file.Path lastRequestedDirectory;
    private volatile boolean lastRequestedShowHidden;
    private volatile long lastRequestedRequestId;
    // Phase 4A: progressive directory load for fast first render
    private final java.util.concurrent.atomic.AtomicLong progressiveLoadSeq = new java.util.concurrent.atomic.AtomicLong(0L);
    private final javafx.animation.PauseTransition iconRebuildDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(90));
    private final javafx.animation.PauseTransition iconViewportLayoutDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(75));
    private final javafx.animation.PauseTransition tableViewportLayoutDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(55));
    private volatile double lastResponsiveIconViewportWidth = -1.0;
    private volatile double lastAppliedResponsiveIconViewportWidth = -1.0;
    private volatile double lastResponsiveTableViewportWidth = -1.0;
    private volatile double lastAppliedResponsiveTableViewportWidth = -1.0;
    private volatile double lastVirtualIconGridScrollValue = Double.NaN;

    // Phase 4B.2 (lowest CPU): debounce metadata requests for "visible" rows after scroll/input idle.
    private javafx.animation.PauseTransition visibleMetadataDebounce;
    private final java.util.concurrent.atomic.AtomicBoolean visibleMetadataDebounceArmed = new java.util.concurrent.atomic.AtomicBoolean(false);
// Phase 4B.1 (tier ~250k): Huge-folder paging state (bounded UI list with page navigation).
private volatile boolean hugeFolderModeActive = false;
private volatile java.nio.file.Path hugeFolderPath = null;
private final java.util.ArrayList<FileItem> hugeFolderItems = new java.util.ArrayList<>(64_000);
private volatile boolean hugeFolderSearchActive = false;
private volatile String hugeFolderSearchQuery = "";
private final java.util.ArrayList<FileItem> hugeFolderSearchItems = new java.util.ArrayList<>(16_000);
private volatile int hugeFolderPageStart = 0;
private volatile long hugeFolderScannedTotal = 0L;
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
    /**
     * Sorted view used by {@link javafx.scene.control.TableView}.
     *
     * IMPORTANT: JavaFX TableView sorting does not work when the items list is a
     * {@link javafx.collections.transformation.FilteredList} directly.
     * The TableView expects to drive sorting via a {@link javafx.collections.transformation.SortedList}
     * whose comparator is bound to {@code table.comparatorProperty()}.
     */
    private final SortedList<FileItem> sortedTableItems;
    // Phase 3.5.4: Search (fast filter of current folder)
    private final javafx.animation.PauseTransition searchDebounce;
    private volatile String activeSearchQuery = "";
    private volatile String searchSessionDisplayQuery = "";
    private volatile SearchSessionState searchSessionState = SearchSessionState.IDLE;
    private final AtomicLong searchSessionSeq = new AtomicLong(0L);
    private volatile Path searchSessionScopeRoot;
    private volatile Path searchSessionRestorePath;
    private volatile int searchSessionRestoreIndex = -1;
    private volatile int searchSessionSnapshotItemCount = 0;
    private volatile int searchSessionPredictedMatchCount = -1;
    private volatile boolean suppressSearchFieldListener = false;
    private VBox searchResultsStateSurface;
    private Label searchResultsStateTitle;
    private Label searchResultsStateSubtitle;
    // Phase 4B.3.x: Find Next / Previous within search results
    private volatile String lastFindQuery = "";
    private volatile int lastFindIndex = -1;
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
    private volatile Path iconSelectionAnchorPath;
    private volatile ViewportContinuityState pendingViewportContinuityState;
    private javafx.animation.PauseTransition viewportRealizationDebounce;
    private final java.util.concurrent.atomic.AtomicLong realizationViewportGeneration = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong realizationDirectoryGeneration = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong detailsAsyncBindingEpoch = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong realizationScrollDirection = new java.util.concurrent.atomic.AtomicLong(1L);
    private final java.util.concurrent.atomic.AtomicLong scrollTelemetryLastMotionNanos = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong scrollTelemetryBurstStartNanos = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong scrollTelemetryBurstEvents = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong scrollTelemetryMaxGapNanos = new java.util.concurrent.atomic.AtomicLong(0L);
    private javafx.animation.PauseTransition viewportSettlePassDebounce;
    private volatile double scrollVelocityPixelsPerMs = 0.0;
    private volatile ScrollVelocityBucket scrollVelocityBucket = ScrollVelocityBucket.SETTLE;
    private final ViewportSchedulerTelemetry viewportSchedulerTelemetry = new ViewportSchedulerTelemetry();
    private final ViewportBandClassifier viewportBandClassifier = new ViewportBandClassifier(Math.max(1, VIEWPORT_NEAR_THRESHOLD_CELLS));
    private final BudgetedViewportScheduler viewportScheduler = new BudgetedViewportScheduler(
            viewportBandClassifier,
            viewportSchedulerTelemetry,
            Clock.systemUTC(),
            Math.max(50L, VIEWPORT_SCROLL_STOP_QUIET_MS),
            TimeUnit.MILLISECONDS,
            this::onViewportScrollStopCommit
    );
    private Rectangle iconMarqueeSelectionRect;
    private boolean iconMarqueeInteractionInstalled = false;
    private boolean iconMarqueePressArmed = false;
    private boolean iconMarqueeDragStarted = false;
    private boolean iconMarqueeAdditive = false;
    private boolean iconMarqueePressOnExistingItem = false;
    private boolean iconMarqueeGestureOwnsSelection = false;
    private boolean suppressExplorerIconClickSelection = false;
    private int suppressExplorerIconClickSelectionPulsesRemaining = 0;
    private double iconMarqueePressSceneX = Double.NaN;
    private double iconMarqueePressSceneY = Double.NaN;
    private final java.util.LinkedHashSet<Path> iconMarqueeBaseSelection = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Path> pendingExplorerMarqueeSelectionPaths = new java.util.LinkedHashSet<>();
    private Path pendingExplorerMarqueeFocusPath = null;
    private ViewMode marqueeSelectionMode = null;
    private final java.util.LinkedHashSet<Path> detailsPresentationSelectedPaths = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Path> iconPresentationSelectedPaths = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Path> explorerSelectionSnapshotBeforePrimaryPress = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashSet<Path> explorerSelectionStabilizationPaths = new java.util.LinkedHashSet<>();
    private Path explorerSelectionStabilizationFocusPath = null;
    private long explorerSelectionStabilizationTicket = 0L;
    private boolean explorerSelectionStabilizationActive = false;
    private int explorerSelectionPresentationTransactionDepth = 0;
    private int explorerSelectionModelNotificationDepth = 0;
    private boolean deferredExplorerSelectionStabilizationApplyScheduled = false;
    private final java.util.LinkedHashSet<Path> deferredExplorerPathSelectionPaths = new java.util.LinkedHashSet<>();
    private Path deferredExplorerPathSelectionFocusPath = null;
    private boolean deferredExplorerPathSelectionApplyScheduled = false;
    private boolean explorerContextMenuSelectionPresentationHold = false;
    private final java.util.LinkedHashSet<Path> explorerContextMenuHeldSelectionPaths = new java.util.LinkedHashSet<>();
    private Path explorerContextMenuHeldFocusPath = null;
    private Path explorerContextMenuOwnedPath = null;
    private long explorerItemContextMenuSuppressUntilNanos = 0L;
    private long explorerItemContextMenuRequestTicket = 0L;
    private long explorerMetadataPopupSuppressUntilNanos = 0L;
    private boolean explorerFileViewContextMenuPending = false;
    private long explorerFileViewContextMenuPendingUntilNanos = 0L;
    private Path armedExplorerItemContextMenuPath = null;
    private double armedExplorerItemContextMenuScreenX = Double.NaN;
    private double armedExplorerItemContextMenuScreenY = Double.NaN;
    private long armedExplorerItemContextMenuUntilNanos = 0L;
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
    // Table "Details" column layout persistence (Explorer-like)
    private static final String PREF_TABLE_DETAILS_COLS_VISIBLE = "table.details.cols.visible";
    private static final String PREF_TABLE_DETAILS_COLS_ORDER = "table.details.cols.order";
    private static final String PREF_TABLE_DETAILS_COLS_WIDTHS = "table.details.cols.widths";
    private static final String PREF_WORKSPACE_NAV_WIDTH_PX = "workspace.nav.width.px";
    private static final String PREF_WORKSPACE_NAV_VISIBLE = "workspace.nav.visible";
    private static final String PREF_WORKSPACE_INSPECTOR_WIDTH_PX = "workspace.inspector.width.px";
    private static final String PREF_WORKSPACE_INSPECTOR_MODE = "workspace.inspector.mode";
    private static final String PREF_WORKSPACE_INSPECTOR_CONTENT_MODE = "workspace.inspector.content.mode";
    private final ToggleGroup viewModeToggleGroup;
    private boolean windowPrefsInstalled;
    private boolean windowChromeStateInstalled;
    private boolean zoomShortcutsInstalled;
    private boolean explorerShortcutsInstalled;
    private volatile Path currentDirectory;
    private volatile Path visibleDirectoryScope;
    private final java.util.concurrent.atomic.AtomicBoolean startupTreeSkeletonMarked = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupTreeRootVisibleMarked = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupInitialDirectoryLoadStarted = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupInitialDirectoryLoadFinished = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupPostShowHydrationScheduled = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupInitialDirectoryFirstBatchCommitted = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupIconWarmupGateOpened = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupThumbnailWarmupGateOpened = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupFirstInteractionTrackingArmed = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupFirstInteractionReadyMarked = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean startupZeroHitchPrewarmScheduled = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean deferredOperationQueueBindingsInstalled = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean deferredExplorerContextActivationScheduled = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile boolean directoryLoading;
    private final List<Path> backHistory;
    private final List<Path> forwardHistory;
    private final FolderSnapshotCache folderSnapshotCache;
    // Phase 4A.6: Active snapshot used for "in-place" hydration (avoid scroll/selection jumps).
    private volatile FolderSnapshotCache.FolderSnapshot activeHydrationSnapshot;
    private boolean suppressTreeSelection;
    private boolean treeSelectionUserInitiated;
    private boolean suppressTreeSelectionDirectoryLoadOnce;
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
    private boolean navigationPaneGrowthLockInstalled;
    private boolean navigationPaneDividerAdjustPending;
    private boolean navigationPaneDividerProgrammaticChange;
    private boolean navigationPaneDividerTrackingInstalled;
    private double pendingNavigationPaneShellWidthPx = -1.0;
    private double lastKnownNavigationPaneShellWidthPx = NAV_TREE_PREF_WIDTH_PX + (NAV_TREE_SHELL_PADDING_PX * 2.0);
    private double lastKnownMainSplitWidthPx = -1.0;
    private double lastKnownInspectorWidthPx = SIDE_PANE_PREF_WIDTH_PX;
    private boolean workspaceShellResizersInstalled;
    private double navigationResizerDragScreenX;
    private double navigationResizerDragStartWidthPx;
    private double inspectorResizerDragScreenX;
    private double inspectorResizerDragStartWidthPx;
    // Navigation tree sizing: keep the left navigation shell width pinned while the window resizes,
    // so growth and shrink are absorbed by the file view unless the user explicitly drags the divider.
    private static final double NAV_TREE_MIN_WIDTH_PX = 54.0;
    private static final double NAV_TREE_SHELL_PADDING_PX = 3.0;
    private static final double NAV_TREE_SHELL_MIN_WIDTH_PX = NAV_TREE_MIN_WIDTH_PX + (NAV_TREE_SHELL_PADDING_PX * 2.0);
    private static final double SIDE_PANE_MIN_WIDTH_PX = 304.0;
    private static final double SIDE_PANE_PREF_WIDTH_PX = 356.0;
    private static final double NAV_TREE_SHELL_MAX_WIDTH_PX = 520.0;
    private static final double INSPECTOR_HOST_MAX_WIDTH_PX = 640.0;
    private static final double NAV_TREE_PREF_WIDTH_PX = 320.0;
    // Shared background I/O executor for directory listing and paste/copy/move operations.
    private final ExecutorService ioExecutor;
    private final java.util.concurrent.atomic.AtomicBoolean controllerDisposed = new java.util.concurrent.atomic.AtomicBoolean(false);
    // Phase 4B.2: Low-CPU, budgeted metadata fetching (size/mtime/type) for selected/nearby rows.
    private com.fileexplorer.service.filesystem.FileMetadataBudgetService metadataBudgetService;
    // Phase 4B.2: fill-all metadata pass (bounded, low CPU but complete)
    private final java.util.concurrent.atomic.AtomicLong fillAllMetadataSeq = new java.util.concurrent.atomic.AtomicLong(-1L);
    private final java.util.concurrent.atomic.AtomicBoolean fillAllMetadataRunning = new java.util.concurrent.atomic.AtomicBoolean(false);
    // Phase 4B.2+: batch metadata UI updates to avoid per-item Platform.runLater churn.
    private final java.util.concurrent.ConcurrentHashMap<java.nio.file.Path, com.fileexplorer.service.filesystem.FileMetadataBudgetService.Metadata> pendingMetadataUpdates
            = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean metadataFlushArmed = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final javafx.animation.PauseTransition metadataFlushDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(
            Long.getLong("fileexplorer.metadata.flushMs", 90L)
    ));
    // Phase 4I: debounce top-chrome compaction so resize does not churn CSS classes every pulse.
    private final javafx.animation.PauseTransition commandBarCompactionDebounce = new javafx.animation.PauseTransition(
            javafx.util.Duration.millis(Long.getLong("fileexplorer.chrome.compactionDebounceMs", 70L))
    );
    private volatile double pendingCommandBarWidth = -1.0;
    private final javafx.animation.PauseTransition selectionChromeDebounce = new javafx.animation.PauseTransition(
            javafx.util.Duration.millis(Long.getLong("fileexplorer.chrome.selectionDebounceMs", 32L))
    );
    private final java.util.concurrent.atomic.AtomicBoolean selectionChromeRefreshQueued = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile ExplorerCommandStateSnapshot lastExplorerCommandStateSnapshot;
    // Phase 4I: keep details/status updates immediate, but debounce heavier preview thumbnail work.
    private final javafx.animation.PauseTransition previewLoadDebounce = new javafx.animation.PauseTransition(
            javafx.util.Duration.millis(Long.getLong("fileexplorer.preview.selectionDebounceMs", 60L))
    );
    // Phase 4P.9EM follow-up: selection refresh churn can briefly clear selection while thumbnails
    // are being refreshed. Delay the preview clear slightly so a resolved image is not replaced by
    // a temporary placeholder or blank state during those transient null-selection windows.
    private final javafx.animation.PauseTransition previewClearDebounce = new javafx.animation.PauseTransition(
            javafx.util.Duration.millis(Long.getLong("fileexplorer.preview.clearDebounceMs", 220L))
    );
    private final java.util.concurrent.atomic.AtomicLong previewLoadSeq = new java.util.concurrent.atomic.AtomicLong(0L);
    private volatile java.nio.file.Path pendingPreviewPath;
    // Phase 4O.6: warm only the current folder's thumbnail candidates after navigation settles.
    private final javafx.animation.PauseTransition folderThumbnailWarmupDebounce = new javafx.animation.PauseTransition(
            javafx.util.Duration.millis(Long.getLong("fileexplorer.thumb.warmup.delayMs", 220L))
    );
    private final java.util.concurrent.atomic.AtomicLong folderThumbnailWarmupSeq = new java.util.concurrent.atomic.AtomicLong(0L);
    private final javafx.animation.PauseTransition startupThumbnailGateDebounce = new javafx.animation.PauseTransition(
            javafx.util.Duration.millis(Long.getLong("fileexplorer.startup.thumbnailGateDelayMs", 320L))
    );
    private final javafx.animation.PauseTransition startupFirstInteractionFallback = new javafx.animation.PauseTransition(
            javafx.util.Duration.millis(Long.getLong("fileexplorer.startup.firstInteractionFallbackMs", 900L))
    );
    // Phase 4I: coalesce expensive table.refresh() calls triggered by metadata fill.
    private final javafx.animation.PauseTransition tableRefreshDebounce = new javafx.animation.PauseTransition(
            javafx.util.Duration.millis(Long.getLong("fileexplorer.table.refreshDebounceMs", 75L))
    );
    private final java.util.concurrent.atomic.AtomicBoolean tableRefreshQueued = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile boolean tableRefreshDeferredWhileHover = false;
    private final PauseTransition explorerMetadataPopupDelay = new PauseTransition(Duration.millis(90));
    private Popup explorerMetadataPopup;
    private StackPane explorerMetadataPopupRoot;
    private Label explorerMetadataPopupLabel;
    private Node explorerMetadataPopupAnchor;
    private java.util.function.Supplier<String> explorerMetadataPopupTextSupplier;
    private double explorerMetadataPopupScreenX = Double.NaN;
    private double explorerMetadataPopupScreenY = Double.NaN;
    private int explorerMetadataPopupDetailsRowIndex = -1;
    private final java.util.concurrent.ConcurrentHashMap<Path, String> explorerMetadataTextCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.HashMap<String, String> detailsRowStyleTemplateCache = new java.util.HashMap<>();
    private String explorerMetadataPopupLastText = "";
private boolean hoverPrefetchEnabled;
    private int focusCycleIndex;
/**
 * MainController.
 *
 * @return TODO
 */
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
        this.sortedTableItems = new SortedList<>(this.filteredTableItems);
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
        // Phase 4B.2+: coalesce metadata UI updates.
        this.metadataFlushDebounce.setOnFinished(_ -> flushPendingMetadataUpdates());
        this.detailsColumnsPersistDebounce.setOnFinished(_ -> persistDetailsColumnsState());
        this.commandBarCompactionDebounce.setOnFinished(_ -> applyCommandBarCompactionNow(pendingCommandBarWidth));
        this.selectionChromeDebounce.setOnFinished(_ -> {
            selectionChromeRefreshQueued.set(false);
            applySelectionCommandStateNow(false);
        });
        this.previewLoadDebounce.setOnFinished(_ -> loadPreviewThumbnailNow(previewLoadSeq.get(), pendingPreviewPath));
        this.previewClearDebounce.setOnFinished(_ -> {
            if (previewImage == null) {
                return;
            }
            if (getPrimarySelection() != null) {
                return;
            }
            boolean retainRenderedPreviewOnTransientNull = shouldRetainDisplayedPreviewOnTransientNullSelection();
            if (retainRenderedPreviewOnTransientNull) {
                return;
            }
            previewImage.setImage(null);
            previewImage.getProperties().put("previewResolved", Boolean.FALSE);
            previewImage.getProperties().put("previewLastResolvedPath", null);
            previewImage.getProperties().put("previewPath", null);
            previewImage.getProperties().put("previewIdentity", null);
            if (previewText != null) {
                previewText.setText(buildPreviewFallbackText(null));
                previewText.setVisible(true);
                previewText.setManaged(true);
            }
        });
        this.folderThumbnailWarmupDebounce.setOnFinished(_ -> warmCurrentFolderThumbnailsNow(folderThumbnailWarmupSeq.get()));
        this.startupThumbnailGateDebounce.setOnFinished(_ -> openStartupThumbnailWarmupGate());
        this.startupFirstInteractionFallback.setOnFinished(_ -> noteStartupInteractionReady());
        this.iconViewportLayoutDebounce.setOnFinished(_ -> applyResponsiveIconViewportLayoutNow());
        this.tableViewportLayoutDebounce.setOnFinished(_ -> applyResponsiveTableViewportLayoutNow());
        this.tableRefreshDebounce.setOnFinished(_ -> {
            tableRefreshQueued.set(false);
            if (fileTable == null || isIconMode(viewMode)) {
                return;
            }
            int maxRefresh = Integer.getInteger("fileexplorer.table.refreshAllMax", 2500);
            if (tableItems != null && tableItems.size() > maxRefresh) {
                return;
            }
            try {
                if (fileTable.isVisible()) {
                    advanceDetailsAsyncBindingEpoch();
                    fileTable.refresh();
                }
            } catch (Exception ignored) {
            }
        });
this.tableIndexByPath = new HashMap<>();
        this.iconBuildGeneration = 0L;
        this.iconBuildNextIndex = 0;
        this.prefs = Preferences.userNodeForPackage(MainController.class);
        this.viewModeToggleGroup = new ToggleGroup();
        this.uiFontSizePx = UI_FONT_DEFAULT_PX;
        this.backHistory = new ArrayList<>();
        this.forwardHistory = new ArrayList<>();
        this.folderSnapshotCache = new FolderSnapshotCache();
        this.cutBuffer = new ArrayList<>();
        this.cutMode = false;
        this.suppressTreeSelection = false;
        this.treeSelectionUserInitiated = false;
        this.suppressTreeSelectionDirectoryLoadOnce = false;
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

    private boolean isIoExecutorAvailable() {
        return !controllerDisposed.get()
                && ioExecutor != null
                && !ioExecutor.isShutdown()
                && !ioExecutor.isTerminated();
    }

    private boolean isHoverPrefetchExecutorAvailable() {
        return !controllerDisposed.get()
                && hoverPrefetchExecutor != null
                && !hoverPrefetchExecutor.isShutdown()
                && !hoverPrefetchExecutor.isTerminated();
    }

    private void executeOnIoExecutor(String purpose, Runnable task) {
        if (task == null || !isIoExecutorAvailable()) {
            return;
        }
        try {
            ioExecutor.execute(() -> {
                if (controllerDisposed.get()) {
                    return;
                }
                task.run();
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            LOG.fine(() -> "Skipping IO task after executor shutdown: " + purpose);
        }
    }

    private void executeOnHoverPrefetchExecutor(String purpose, Runnable task) {
        if (task == null || !isHoverPrefetchExecutorAvailable()) {
            return;
        }
        try {
            hoverPrefetchExecutor.execute(() -> {
                if (controllerDisposed.get()) {
                    return;
                }
                task.run();
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            LOG.fine(() -> "Skipping hover-prefetch task after executor shutdown: " + purpose);
        }
    }

@Override
/**
 * attach.

 *
 * @param context TODO
 */
public void attach(ExplorerContext context) {
    if (context == null) {
        return;
    }
    // Idempotent attach: tolerate repeated calls.
    if (this.context == context) {
        return;
    }
    this.context = context;
    // HOTFIX185: keep the queue/history/template graph out of attach(); these bindings are installed once startup is interactive.
    scheduleDeferredOperationQueueBindings();
    this.themeService = context.themeService();
    this.fileMetadataService = context.fileMetadataService();
    if (this.metadataBudgetService == null) {
        this.metadataBudgetService = new com.fileexplorer.service.filesystem.FileMetadataBudgetService(this.fileMetadataService, this.ioExecutor);
    }
    this.treeBuildService = context.treeBuildService();
    this.eventBus = context.eventBus();
    // Phase 3.6.0: create file operation service (uses IO executor + EventBus)
    if (this.fileOperationService == null) {
        this.fileOperationService = new com.fileexplorer.service.ops.FileOperationService(this.eventBus, this.ioExecutor);
    }
    // Phase 3.6.0: subscribe to file operation progress for status updates
    localDisposables.add(eventBus.subscribe(com.fileexplorer.service.event.events.FileOpStarted.class, e -> {
        activeFileOpJobId = e.jobId();
        if (statusLabel != null) {
            statusLabel.setText(e.type() + " started (" + e.totalItems() + " item(s))...");
        }
    }));
    localDisposables.add(eventBus.subscribe(com.fileexplorer.service.event.events.FileOpProgress.class, e -> {
        if (e.jobId() != activeFileOpJobId) return;
        if (statusLabel != null) {
            statusLabel.setText("Working... " + e.processedItems() + "/" + e.totalItems() + " (" + e.percent() + "%) — " + safeName(e.currentPath()));
        }
    }));
    localDisposables.add(eventBus.subscribe(com.fileexplorer.service.event.events.FileOpCompleted.class, e -> {
        if (e.jobId() != activeFileOpJobId) return;
        activeFileOpJobId = -1L;
        if (statusLabel != null) {
            statusLabel.setText("Done.");
        }
        // refresh current view after op
        refresh();
    }));
    localDisposables.add(eventBus.subscribe(com.fileexplorer.service.event.events.FileOpCancelled.class, e -> {
        if (e.jobId() != activeFileOpJobId) return;
        activeFileOpJobId = -1L;
        pendingInlineRenameSelectionPath = null;
        pendingInlineRenameSelectionIndex = -1;
        if (statusLabel != null) {
            statusLabel.setText("Operation cancelled.");
        }
    }));
    localDisposables.add(eventBus.subscribe(com.fileexplorer.service.event.events.FileOpFailed.class, e -> {
        if (e.jobId() != activeFileOpJobId) return;
        activeFileOpJobId = -1L;
        pendingInlineRenameSelectionPath = null;
        pendingInlineRenameSelectionIndex = -1;
        if (statusLabel != null) {
            statusLabel.setText("Operation failed: " + e.error().getClass().getSimpleName());
        }
    }));
    
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
/**
 * configureToolbarActions.
 *
 */
    private void configureToolbarActions() {
    LogSupport.enter(LOG, "configureToolbarActions");
    installLazyCommandBarMenuPopulation();
    if (newMenuButton != null) {
        configureStructuredCommandBarMenuButton(newMenuButton);
    }
    if (cutButton != null) {
        configureCommandBarIconAndTextControl(cutButton);
        cutButton.setOnAction(e -> cutSelection());
    }
    if (copyButton != null) {
        configureCommandBarIconAndTextControl(copyButton);
        copyButton.setOnAction(e -> copySelection());
    }
    if (pasteButton != null) {
        configureCommandBarIconAndTextControl(pasteButton);
        pasteButton.setOnAction(e -> pasteIntoCurrentFolder());
    }
    if (renameButton != null) {
        configureCommandBarIconAndTextControl(renameButton);
        renameButton.setOnAction(e -> renameSelection());
    }
    // Optional / not yet implemented features in this codebase:
    if (shareButton != null) {
        configureCommandBarIconAndTextControl(shareButton);
        shareButton.setOnAction(e -> setStatus("Share: not implemented yet."));
    }
    if (deleteButton != null) {
        configureCommandBarIconAndTextControl(deleteButton);
        deleteButton.setOnAction(e -> moveSelectionToTrash());
    }
    if (backButton != null) {
        if (!backButton.getStyleClass().contains("icon-only")) {
            backButton.getStyleClass().add("icon-only");
        }
        backButton.setOnAction(e -> navigateBack());
    }
    if (forwardButton != null) {
        if (!forwardButton.getStyleClass().contains("icon-only")) {
            forwardButton.getStyleClass().add("icon-only");
        }
        forwardButton.setOnAction(e -> navigateForward());
    }
    if (upButton != null) {
        if (!upButton.getStyleClass().contains("icon-only")) {
            upButton.getStyleClass().add("icon-only");
        }
        upButton.setOnAction(e -> navigateUp());
    }
    if (refreshButton != null) {
        if (!refreshButton.getStyleClass().contains("icon-only")) {
            refreshButton.getStyleClass().add("icon-only");
        }
        refreshButton.setOnAction(e -> refresh());
    }
    if (sortMenuButton != null) {
        configureStructuredCommandBarMenuButton(sortMenuButton);
    }
    if (viewMenuButton != null) {
        configureStructuredCommandBarMenuButton(viewMenuButton);
    }
    if (filterMenuButton != null) {
        configureStructuredCommandBarMenuButton(filterMenuButton);
    }
    if (previewToggle != null) {
        configureCommandBarRailToggle(previewToggle);
        previewToggle.setOnAction(e -> setPreviewPaneVisible(previewToggle.isSelected()));
    }
    if (operationsToggle != null) {
        configureCommandBarRailToggle(operationsToggle);
    }
    if (detailsToggle != null) {
        configureCommandBarRailToggle(detailsToggle);
    }
    wireSeeMoreMenuActions();
    updateNavigationButtonsState();
    updateSelectionCommandState();
    updateTopChromeState();
    // sortMenuButton and viewMenuButton actions are handled by their MenuItems' onAction in FXML.
}

    private void configureCommandBarIconOnlyControl(javafx.scene.control.Labeled control) {
        if (control == null) {
            return;
        }
        syncCommandBarTooltip(control);
        if (!control.getStyleClass().contains("icon-only")) {
            control.getStyleClass().add("icon-only");
        }
        String label = control.getText();
        if (label != null && !label.isBlank()) {
            control.setAccessibleText(label.trim());
        }
    }

    private void configureCommandBarIconAndTextControl(javafx.scene.control.Labeled control) {
        if (control == null) {
            return;
        }
        syncCommandBarTooltip(control);
        control.getStyleClass().remove("icon-only");
        if (!control.getStyleClass().contains("command-bar-icon-text")) {
            control.getStyleClass().add("command-bar-icon-text");
        }
        control.setContentDisplay(ContentDisplay.LEFT);
        control.setGraphicTextGap(6.0);
        String label = control.getText();
        if (label != null && !label.isBlank()) {
            control.setAccessibleText(label.trim());
        }
    }

    private void configureCommandBarRailToggle(javafx.scene.control.ToggleButton control) {
        if (control == null) {
            return;
        }
        configureCommandBarIconAndTextControl(control);
        if (!control.getStyleClass().contains("command-right-rail-toggle")) {
            control.getStyleClass().add("command-right-rail-toggle");
        }
        control.setMnemonicParsing(false);
        control.setContentDisplay(ContentDisplay.LEFT);
        control.setGraphicTextGap(control.getGraphic() == null ? 0.0 : 6.0);
    }


    private void configureStructuredCommandBarMenuButton(javafx.scene.control.MenuButton menuButton) {
        if (menuButton == null) {
            return;
        }
        syncCommandBarTooltip(menuButton);
        menuButton.getStyleClass().remove("icon-only");
        menuButton.getStyleClass().remove("command-bar-icon-text");
        menuButton.getStyleClass().remove("command-bar-native-menu-button");
        if (!menuButton.getStyleClass().contains("command-bar-structured-menu-button")) {
            menuButton.getStyleClass().add("command-bar-structured-menu-button");
        }

        String labelText = menuButton.getText();
        if ((labelText == null || labelText.isBlank()) && menuButton.getProperties().get("commandBarOriginalText") instanceof String storedText) {
            labelText = storedText;
        }
        if ((labelText == null || labelText.isBlank()) && menuButton.getAccessibleText() != null) {
            labelText = menuButton.getAccessibleText();
        }
        if ((labelText == null || labelText.isBlank()) && menuButton.getTooltip() != null) {
            labelText = menuButton.getTooltip().getText();
        }
        if (labelText == null) {
            labelText = "";
        }
        labelText = labelText.trim();
        menuButton.getProperties().put("commandBarOriginalText", labelText);
        menuButton.setAccessibleText(labelText);

        Object storedGraphic = menuButton.getProperties().get("commandBarOriginalGraphic");
        if (!(storedGraphic instanceof Node) && menuButton.getGraphic() != null) {
            menuButton.getProperties().put("commandBarOriginalGraphic", menuButton.getGraphic());
            storedGraphic = menuButton.getGraphic();
        }

        Node leadingGraphic = buildStructuredCommandBarMenuIcon(menuButton,
                storedGraphic instanceof Node storedNode ? cloneCommandBarMenuGraphic(storedNode) : null,
                labelText);
        Node content = buildStructuredCommandBarMenuContent(menuButton, leadingGraphic, labelText);
        if (content != null) {
            content.setMouseTransparent(true);
            /*
             * HOTFIX195 final alignment corrective pass:
             * the structured MenuButton skin still reads visually low compared to
             * adjacent command-bar buttons, so lift the custom graphic content a
             * full additional 2 px so it matches the Delete / Share / Preview row.
             */
            content.setTranslateY(-4.5);
            menuButton.setGraphic(content);
        }
        menuButton.setText("");
        menuButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        menuButton.setGraphicTextGap(0.0);
    }

    private Node buildStructuredCommandBarMenuContent(javafx.scene.control.MenuButton menuButton, Node leadingGraphic, String labelText) {
        HBox content = new HBox();
        content.getStyleClass().add("command-bar-menu-content");
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMouseTransparent(true);

        StackPane iconSlot = new StackPane();
        iconSlot.getStyleClass().add("command-bar-menu-icon-slot");
        iconSlot.setMouseTransparent(true);
        Node iconNode = buildStructuredCommandBarMenuIcon(menuButton, leadingGraphic, labelText);
        if (iconNode != null) {
            iconNode.setMouseTransparent(true);
            iconSlot.getChildren().add(iconNode);
        }

        Label textLabel = new Label(labelText == null ? "" : labelText);
        textLabel.getStyleClass().add("command-bar-menu-text");
        textLabel.setMouseTransparent(true);

        StackPane chevronSlot = new StackPane();
        iconSlot.setTranslateY(-1.0);
        textLabel.setTranslateY(-0.75);
        chevronSlot.getStyleClass().add("command-bar-menu-chevron-slot");
        chevronSlot.setTranslateY(-0.75);
        chevronSlot.setMouseTransparent(true);
        chevronSlot.getChildren().add(buildStructuredCommandBarMenuChevron());

        content.getChildren().addAll(iconSlot, textLabel, chevronSlot);
        return content;
    }

    private Node buildStructuredCommandBarMenuChevron() {
        javafx.scene.shape.SVGPath chevron = new javafx.scene.shape.SVGPath();
        chevron.setContent("M 0 0 L 4 4 L 8 0");
        chevron.getStyleClass().add("command-bar-menu-chevron-shape");
        chevron.setMouseTransparent(true);
        return chevron;
    }

    private Node buildStructuredCommandBarMenuIcon(javafx.scene.control.MenuButton menuButton, Node fallbackGraphic, String labelText) {
        Node vectorIcon = buildStructuredCommandBarVectorIcon(menuButton);
        if (vectorIcon != null) {
            vectorIcon.setMouseTransparent(true);
            return vectorIcon;
        }
        if (fallbackGraphic != null) {
            fallbackGraphic.setMouseTransparent(true);
            return fallbackGraphic;
        }
        String glyph = null;
        if (labelText != null && !labelText.isBlank()) {
            glyph = "";
        }
        if (glyph == null) {
            return null;
        }
        Label icon = new Label(glyph);
        icon.getStyleClass().addAll("fluent-icon", "command-bar-menu-leading-glyph");
        icon.setMouseTransparent(true);
        return icon;
    }

    private Node buildStructuredCommandBarVectorIcon(javafx.scene.control.MenuButton menuButton) {
        if (menuButton == newMenuButton) {
            return buildStructuredCommandBarNewVectorIcon();
        }
        if (menuButton == sortMenuButton) {
            return buildStructuredCommandBarSortVectorIcon();
        }
        if (menuButton == viewMenuButton) {
            return buildStructuredCommandBarViewVectorIcon();
        }
        if (menuButton == filterMenuButton) {
            return buildStructuredCommandBarFilterVectorIcon();
        }
        return null;
    }

    private Node buildStructuredCommandBarNewVectorIcon() {
        StackPane root = createStructuredCommandBarVectorIconRoot();
        root.getChildren().addAll(
                createStructuredCommandBarStrokePath("M 3 2 H 9.5 L 12.5 5 V 14 H 3 Z M 9.5 2 V 5 H 12.5"),
                createStructuredCommandBarStrokePath("M 7.75 7 V 11"),
                createStructuredCommandBarStrokePath("M 5.75 9 H 9.75")
        );
        return root;
    }

    private Node buildStructuredCommandBarSortVectorIcon() {
        Node rasterIcon = createStructuredCommandBarRasterIcon("/icons/toolbar.sort.png", 18.0, 18.0, true);
        if (rasterIcon != null) {
            return rasterIcon;
        }
        StackPane root = createStructuredCommandBarVectorIconRoot();
        root.getChildren().addAll(
                createStructuredCommandBarStrokePath("M 2.5 4 H 9.5"),
                createStructuredCommandBarStrokePath("M 2.5 8 H 8"),
                createStructuredCommandBarStrokePath("M 2.5 12 H 6.5"),
                createStructuredCommandBarStrokePath("M 11.5 3.5 V 12"),
                createStructuredCommandBarStrokePath("M 9.5 10 L 11.5 12 L 13.5 10")
        );
        return root;
    }

    private Node createStructuredCommandBarRasterIcon(String resourcePath, double fitWidth, double fitHeight) {
        return createStructuredCommandBarRasterIcon(resourcePath, fitWidth, fitHeight, false);
    }

    private Node createStructuredCommandBarRasterIcon(String resourcePath, double fitWidth, double fitHeight,
            boolean forceWhiteMask) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        URL resourceUrl = MainController.class.getResource(resourcePath);
        if (resourceUrl == null) {
            return null;
        }
        Image image;
        if (forceWhiteMask) {
            image = loadMenuIconImage(resourceUrl, true);
        } else {
            image = new Image(resourceUrl.toExternalForm(), fitWidth, fitHeight, true, true, true);
        }
        if (image == null || image.isError()) {
            return null;
        }
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(fitWidth);
        imageView.setFitHeight(fitHeight);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setPickOnBounds(true);
        imageView.setMouseTransparent(true);
        imageView.getStyleClass().add("command-bar-menu-raster-icon");
        imageView.setTranslateY(-0.35);
        return imageView;
    }

    private Node buildStructuredCommandBarViewVectorIcon() {
        Node rasterIcon = createStructuredCommandBarRasterIcon(viewModeIconResource(viewMode), 18.0, 18.0, true);
        if (rasterIcon != null) {
            return rasterIcon;
        }
        StackPane root = createStructuredCommandBarVectorIconRoot();
        root.getChildren().addAll(
                createStructuredCommandBarStrokePath("M 2.75 3 H 6.5 V 6.75 H 2.75 Z"),
                createStructuredCommandBarStrokePath("M 8.5 3 H 12.25 V 6.75 H 8.5 Z"),
                createStructuredCommandBarStrokePath("M 2.75 8.75 H 6.5 V 12.5 H 2.75 Z"),
                createStructuredCommandBarStrokePath("M 8.5 8.75 H 12.25 V 12.5 H 8.5 Z")
        );
        return root;
    }

    private Node buildStructuredCommandBarFilterVectorIcon() {
        StackPane root = createStructuredCommandBarVectorIconRoot();
        root.getChildren().addAll(
                createStructuredCommandBarStrokePath("M 2.5 3.25 H 13.5"),
                createStructuredCommandBarStrokePath("M 2.5 3.25 L 7.65 8.4"),
                createStructuredCommandBarStrokePath("M 13.5 3.25 L 8.35 8.4"),
                createStructuredCommandBarStrokePath("M 8 8.4 V 13")
        );
        return root;
    }

    private StackPane createStructuredCommandBarVectorIconRoot() {
        StackPane root = new StackPane();
        root.getStyleClass().add("command-bar-menu-vector-icon-root");
        root.setTranslateY(-0.35);
        root.setMinSize(24, 24);
        root.setPrefSize(24, 24);
        root.setMaxSize(24, 24);
        root.setMouseTransparent(true);
        return root;
    }

    private javafx.scene.shape.SVGPath createStructuredCommandBarStrokePath(String content) {
        javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
        path.setContent(content);
        path.getStyleClass().add("command-bar-menu-icon-stroke-shape");
        path.setScaleX(1.46);
        path.setScaleY(1.46);
        path.setMouseTransparent(true);
        return path;
    }

    private Node cloneCommandBarMenuGraphic(Node graphic) {
        if (graphic == null) {
            return null;
        }
        if (graphic instanceof Label labelGraphic) {
            Label clone = new Label(labelGraphic.getText());
            clone.getStyleClass().setAll(labelGraphic.getStyleClass());
            clone.setStyle(labelGraphic.getStyle());
            clone.setFont(labelGraphic.getFont());
            clone.setTextFill(labelGraphic.getTextFill());
            clone.setUnderline(labelGraphic.isUnderline());
            clone.setWrapText(labelGraphic.isWrapText());
            clone.setMinSize(labelGraphic.getMinWidth(), labelGraphic.getMinHeight());
            clone.setPrefSize(labelGraphic.getPrefWidth(), labelGraphic.getPrefHeight());
            clone.setMaxSize(labelGraphic.getMaxWidth(), labelGraphic.getMaxHeight());
            clone.setMouseTransparent(true);
            return clone;
        }
        if (graphic instanceof Labeled labeledGraphic) {
            Label clone = new Label(labeledGraphic.getText());
            clone.getStyleClass().setAll(labeledGraphic.getStyleClass());
            clone.setStyle(labeledGraphic.getStyle());
            clone.setContentDisplay(labeledGraphic.getContentDisplay());
            clone.setGraphicTextGap(labeledGraphic.getGraphicTextGap());
            clone.setMouseTransparent(true);
            return clone;
        }
        return null;
    }

    private void syncCommandBarTooltip(javafx.scene.control.Labeled control) {
        if (control == null) {
            return;
        }
        String label = control.getText();
        if (label == null) {
            return;
        }
        label = label.trim();
        if (label.isEmpty()) {
            return;
        }
        javafx.scene.control.Tooltip tooltip = control.getTooltip();
        if (tooltip == null) {
            control.setTooltip(new javafx.scene.control.Tooltip(label));
        } else {
            tooltip.setText(label);
        }
    }

    private void configureCommandFlyoutParity() {
        styleCommandFlyout(newMenuButton);
        styleCommandFlyout(sortMenuButton);
        styleCommandFlyout(viewMenuButton);
        styleCommandFlyout(seeMoreMenuButton);
    }

    private void styleCommandFlyout(javafx.scene.control.MenuButton menuButton) {
        if (menuButton == null) {
            return;
        }
        if (!menuButton.getStyleClass().contains("explorer-flyout-button")) {
            menuButton.getStyleClass().add("explorer-flyout-button");
        }
        menuButton.getItems().forEach(this::applyExplorerMenuStyle);
    }

    private void applyExplorerMenuStyle(javafx.scene.control.MenuItem item) {
        if (item == null) {
            return;
        }
        if (!(item instanceof javafx.scene.control.SeparatorMenuItem)
                && !item.getStyleClass().contains("explorer-menu-item")) {
            item.getStyleClass().add("explorer-menu-item");
        }
        if (item instanceof javafx.scene.control.Menu menu) {
            if (!menu.getStyleClass().contains("explorer-flyout-submenu")) {
                menu.getStyleClass().add("explorer-flyout-submenu");
            }
            menu.getItems().forEach(this::applyExplorerMenuStyle);
        }
    }


private void wireSeeMoreMenuActions() {
    if (seeMoreMenuButton == null) {
        return;
    }
    for (javafx.scene.control.MenuItem item : seeMoreMenuButton.getItems()) {
        String label = item.getText();
        if (label == null || item instanceof javafx.scene.control.SeparatorMenuItem) {
            continue;
        }
        switch (label) {
            case "Undo" -> item.setOnAction(e -> setStatus("Undo: not implemented yet."));
            case "Copy path" -> item.setOnAction(e -> copyPrimaryPathToClipboard());
            case "Pin to Quick access" -> item.setOnAction(e -> pinCurrentLocationToQuickAccess());
            case "Select all" -> item.setOnAction(e -> selectAll());
            case "Select none" -> item.setOnAction(e -> clearSelection());
            case "Invert selection" -> item.setOnAction(e -> invertSelection());
            case "Properties" -> item.setOnAction(e -> openPropertiesForSelection());
            default -> {
            }
        }
    }
}

private void installLazyCommandBarMenuPopulation() {
    installLazyMenuPopulation(newMenuButton, "newMenuButton materialize", this::materializeNewMenuButton);
    installLazyMenuPopulation(sortMenuButton, "sortMenuButton materialize", this::materializeSortMenuButton);
    installLazyMenuPopulation(viewMenuButton, "viewMenuButton materialize", this::materializeViewMenuButton);
    installLazyMenuPopulation(seeMoreMenuButton, "seeMoreMenuButton materialize", this::materializeSeeMoreMenuButton);
}

private void installLazyMenuPopulation(javafx.scene.control.MenuButton menuButton, String traceLabel, Runnable populator) {
    if (menuButton == null || populator == null) {
        return;
    }
    menuButton.setOnShowing(e -> ensureMenuButtonMaterialized(menuButton, traceLabel, populator));
}

private void ensureMenuButtonMaterialized(javafx.scene.control.MenuButton menuButton, String traceLabel, Runnable populator) {
    if (menuButton == null || populator == null) {
        return;
    }
    if (Boolean.TRUE.equals(menuButton.getProperties().get("fileexplorer.lazyMenu.materialized"))) {
        return;
    }
    menuButton.getProperties().put("fileexplorer.lazyMenu.materialized", Boolean.TRUE);
    StartupTrace.mark(traceLabel + " begin");
    try {
        populator.run();
        styleCommandFlyout(menuButton);
    } finally {
        StartupTrace.mark(traceLabel + " end");
    }
}

private javafx.scene.control.MenuItem createTextMenuItem(String text, Node graphic) {
    javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(text);
    if (graphic != null) {
        item.setGraphic(graphic);
    }
    return item;
}

private javafx.scene.control.SeparatorMenuItem createSeparatorMenuItem() {
    return new javafx.scene.control.SeparatorMenuItem();
}

private Node createMenuGlyph(String glyph, String... styleClasses) {
    Label label = new Label(glyph == null ? "" : glyph);
    for (String styleClass : styleClasses) {
        if (styleClass != null && !styleClass.isBlank()) {
            label.getStyleClass().add(styleClass);
        }
    }
    return label;
}

private ImageView createMenuImageIcon(String resourcePath, double size) {
    return createMenuImageIcon(resourcePath, size, false);
}

private ImageView createMenuImageIcon(String resourcePath, double size, boolean forceWhiteMask) {
    if (resourcePath == null || resourcePath.isBlank()) {
        return null;
    }
    URL url = getClass().getResource(resourcePath);
    if (url == null) {
        return null;
    }
    Image image = loadMenuIconImage(url, forceWhiteMask);
    if (image == null) {
        return null;
    }
    ImageView view = new ImageView(image);
    view.setFitWidth(size);
    view.setFitHeight(size);
    view.setPreserveRatio(true);
    view.setSmooth(true);
    view.setPickOnBounds(true);
    view.getStyleClass().add("view-menu-icon");
    return view;
}

private Image loadMenuIconImage(URL url, boolean forceWhiteMask) {
    if (url == null) {
        return null;
    }
    String key = (forceWhiteMask ? "twoToneWhite:" : "plain:") + url.toExternalForm();
    return VIEW_MENU_WHITE_ICON_CACHE.computeIfAbsent(key, unused -> {
        Image source = new Image(url.toExternalForm(), false);
        if (!forceWhiteMask) {
            return source;
        }
        return createTwoToneWhiteBlueAccentImage(source);
    });
}

private Image createTwoToneWhiteBlueAccentImage(Image source) {
    if (source == null) {
        return null;
    }
    PixelReader reader = source.getPixelReader();
    int width = Math.max(1, (int) Math.round(source.getWidth()));
    int height = Math.max(1, (int) Math.round(source.getHeight()));
    if (reader == null || width <= 0 || height <= 0) {
        return source;
    }
    WritableImage tinted = new WritableImage(width, height);
    PixelWriter writer = tinted.getPixelWriter();
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int argb = reader.getArgb(x, y);
            int alpha = (argb >>> 24) & 0xFF;
            if (alpha == 0) {
                writer.setArgb(x, y, argb);
                continue;
            }
            int red = (argb >>> 16) & 0xFF;
            int green = (argb >>> 8) & 0xFF;
            int blue = argb & 0xFF;
            if (isBlueAccentPixel(red, green, blue)) {
                writer.setArgb(x, y, argb);
            } else {
                writer.setArgb(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
    }
    return tinted;
}

private boolean isBlueAccentPixel(int red, int green, int blue) {
    if (blue < red + 12 || blue < green + 8) {
        return false;
    }
    Color color = Color.rgb(red, green, blue);
    double hue = color.getHue();
    double saturation = color.getSaturation();
    double brightness = color.getBrightness();
    return hue >= 185.0 && hue <= 235.0 && saturation >= 0.30 && brightness >= 0.18;
}

private String viewModeIconResource(ViewMode mode) {
    ViewMode effectiveMode = mode == null ? ViewMode.DETAILS : mode;
    return switch (effectiveMode) {
        case EXTRA_LARGE_ICONS -> "/icons/view.extra.large.png";
        case LARGE_ICONS -> "/icons/view.large.png";
        case MEDIUM_ICONS -> "/icons/view.medium.png";
        case SMALL_ICONS -> "/icons/view.small.png";
        case LIST -> "/icons/view.list.png";
        case DETAILS -> "/icons/view.details.png";
        case TILES -> "/icons/view.tiles.png";
        case CONTENT -> "/icons/view.content.png";
    };
}

private void updateViewMenuButtonGraphic() {
    if (viewMenuButton == null) {
        return;
    }
    ViewMode effectiveMode = viewMode == null ? ViewMode.DETAILS : viewMode;
    boolean structured = viewMenuButton.getStyleClass().contains("command-bar-structured-menu-button");
    String labelText = "View";
    if (structured) {
        Object storedText = viewMenuButton.getProperties().get("commandBarOriginalText");
        if (storedText instanceof String storedLabel && !storedLabel.isBlank()) {
            labelText = storedLabel.trim();
        }
        if ((labelText == null || labelText.isBlank()) && viewMenuButton.getAccessibleText() != null) {
            labelText = viewMenuButton.getAccessibleText().trim();
        }
        if ((labelText == null || labelText.isBlank()) && viewMenuButton.getTooltip() != null) {
            labelText = viewMenuButton.getTooltip().getText();
        }
        if (labelText == null || labelText.isBlank()) {
            labelText = "View";
        }
    }
    String signature = effectiveMode.name() + "|" + (structured ? "structured" : "plain") + "|" + labelText;
    Object previousSignature = viewMenuButton.getProperties().get(VIEW_MENU_BUTTON_GRAPHIC_SIGNATURE_KEY);
    if (Objects.equals(previousSignature, signature)) {
        return;
    }

    String resourcePath = viewModeIconResource(effectiveMode);
    Node graphic = createMenuImageIcon(resourcePath, 18.0, true);
    if (graphic == null && !Objects.equals(resourcePath, "/icons/view.details.png")) {
        graphic = createMenuImageIcon("/icons/view.details.png", 18.0, true);
    }
    if (graphic == null) {
        graphic = createMenuGlyph("\uE8A7", "fluent-icon");
    }

    if (structured) {
        Node content = buildStructuredCommandBarMenuContent(viewMenuButton, graphic, labelText);
        if (content != null) {
            content.setMouseTransparent(true);
            content.setTranslateY(-4.5);
            viewMenuButton.setGraphic(content);
            viewMenuButton.setText("");
            viewMenuButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            viewMenuButton.setGraphicTextGap(0.0);
            viewMenuButton.getProperties().put(VIEW_MENU_BUTTON_GRAPHIC_SIGNATURE_KEY, signature);
            return;
        }
    }

    viewMenuButton.setText("View");
    viewMenuButton.setGraphic(graphic);
    viewMenuButton.setContentDisplay(ContentDisplay.LEFT);
    viewMenuButton.setGraphicTextGap(6.0);
    viewMenuButton.getProperties().put(VIEW_MENU_BUTTON_GRAPHIC_SIGNATURE_KEY, signature);
}

private HBox createViewMenuRow(Node selector, Node icon, String labelText) {
    HBox row = new HBox(10);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("view-menu-row");
    if (selector != null) {
        row.getChildren().add(selector);
    }
    if (icon != null) {
        row.getChildren().add(icon);
    } else {
        Region placeholder = new Region();
        placeholder.getStyleClass().add("view-menu-icon-placeholder");
        row.getChildren().add(placeholder);
    }
    Label label = new Label(labelText == null ? "" : labelText);
    label.getStyleClass().add("view-menu-text");
    row.getChildren().add(label);
    return row;
}

private CustomMenuItem createViewModeMenuItem(ViewMode mode, String labelText, String resourcePath, boolean selected) {
    RadioButton radio = new RadioButton();
    radio.setFocusTraversable(false);
    radio.getStyleClass().add("view-menu-radio");
    radio.setToggleGroup(viewModeToggleGroup);
    radio.setUserData(mode.name());
    radio.setSelected(selected);
    CustomMenuItem item = new CustomMenuItem(createViewMenuRow(radio, createMenuImageIcon(resourcePath, 18.0, true), labelText), true);
    item.setUserData(mode.name());
    item.getProperties().put("viewRadio", radio);
    item.setOnAction(this::onViewModeRowAction);
    return item;
}

private CustomMenuItem createPaneToggleMenuItem(String labelText, String resourcePath, boolean selected) {
    RadioButton radio = new RadioButton();
    radio.setFocusTraversable(false);
    radio.getStyleClass().add("view-menu-radio");
    radio.setSelected(selected);
    CustomMenuItem item = new CustomMenuItem(createViewMenuRow(radio, createMenuImageIcon(resourcePath, 18.0, true), labelText), false);
    item.getProperties().put("paneRadio", radio);
    return item;
}

private CustomMenuItem createCheckToggleMenuItem(String labelText, Node icon, boolean selected) {
    CheckBox checkBox = new CheckBox();
    checkBox.setFocusTraversable(false);
    checkBox.getStyleClass().add("view-menu-checkbox");
    checkBox.setSelected(selected);
    CustomMenuItem item = new CustomMenuItem(createViewMenuRow(checkBox, icon, labelText), false);
    item.getProperties().put("viewCheckBox", checkBox);
    return item;
}

private javafx.scene.control.Menu createShowSubMenu() {
    javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Show");
    menu.getStyleClass().add("view-menu-submenu");
    HBox graphic = new HBox(10);
    graphic.setAlignment(Pos.CENTER_LEFT);
    graphic.getStyleClass().addAll("view-menu-leading", "view-menu-submenu-row");
    CheckBox phantomCheck = new CheckBox();
    phantomCheck.setOpacity(0.0);
    phantomCheck.setDisable(true);
    phantomCheck.setFocusTraversable(false);
    phantomCheck.getStyleClass().add("view-menu-checkbox");
    Region placeholder = new Region();
    placeholder.getStyleClass().add("view-menu-icon-placeholder");
    graphic.getChildren().addAll(phantomCheck, placeholder);
    menu.setGraphic(graphic);

    CustomMenuItem navigationItem = createCheckToggleMenuItem("Navigation pane", createMenuImageIcon("/icons/view.navigation.pane.png", 18.0, true), showNavigationPane);
    showNavigationPaneMenuItem = (CheckBox) navigationItem.getProperties().get("viewCheckBox");

    CustomMenuItem compactItem = createCheckToggleMenuItem("Compact view", createMenuGlyph("\uE7D8", "mdl2-icon", "view-menu-icon"), compactView);
    showCompactViewMenuItem = (CheckBox) compactItem.getProperties().get("viewCheckBox");

    CustomMenuItem itemCheckItem = createCheckToggleMenuItem("Item check boxes", createMenuGlyph("\uE739", "mdl2-icon", "view-menu-icon"), showItemCheckBoxes);
    showItemCheckBoxesMenuItem = (CheckBox) itemCheckItem.getProperties().get("viewCheckBox");

    CustomMenuItem extensionsItem = createCheckToggleMenuItem("File name extensions", createMenuGlyph("\uE8EC", "mdl2-icon", "view-menu-icon"), showFileNameExtensions);
    showFileNameExtensionsMenuItem = (CheckBox) extensionsItem.getProperties().get("viewCheckBox");

    CustomMenuItem hiddenItem = createCheckToggleMenuItem("Hidden items", createMenuGlyph("\uE890", "mdl2-icon", "view-menu-icon"), showHiddenItems);
    showHiddenItemsMenuItem = (CheckBox) hiddenItem.getProperties().get("viewCheckBox");

    CustomMenuItem operationHistoryItem = new CustomMenuItem(createViewMenuRow(new RadioButton(), createMenuGlyph("\uE8A7", "fluent-icon", "view-menu-icon"), "Operation History..."), true);
    wireMenuItemContentAction(operationHistoryItem, () -> onShowOperationHistory(new ActionEvent(operationHistoryItem, operationHistoryItem)), true);
    CustomMenuItem commandLogItem = new CustomMenuItem(createViewMenuRow(new RadioButton(), createMenuGlyph("\uE8A7", "fluent-icon", "view-menu-icon"), "Command Log..."), true);
    wireMenuItemContentAction(commandLogItem, () -> onShowCommandLog(new ActionEvent(commandLogItem, commandLogItem)), true);

    menu.getItems().setAll(
            navigationItem,
            compactItem,
            itemCheckItem,
            extensionsItem,
            hiddenItem,
            createSeparatorMenuItem(),
            operationHistoryItem,
            commandLogItem
    );
    return menu;
}

private void materializeNewMenuButton() {
    if (newMenuButton == null) {
        return;
    }
    javafx.scene.control.MenuItem folder = createTextMenuItem("Folder", createMenuGlyph("\uE8B7", "fluent-icon"));
    folder.setOnAction(e -> createNewFolder());
    javafx.scene.control.MenuItem shortcut = createTextMenuItem("Shortcut", createMenuGlyph("\uE71B", "fluent-icon"));
    shortcut.setOnAction(e -> setStatus("Shortcut creation: not implemented yet."));
    javafx.scene.control.MenuItem textDocument = createTextMenuItem("Text Document", createMenuGlyph("\uE8A5", "fluent-icon"));
    textDocument.setOnAction(e -> setStatus("Text document template: not implemented yet."));
    javafx.scene.control.MenuItem zipFolder = createTextMenuItem("Compressed (zipped) Folder", createMenuGlyph("\uF012", "fluent-icon"));
    zipFolder.setOnAction(e -> setStatus("ZIP folder template: not implemented yet."));
    newMenuButton.getItems().setAll(folder, shortcut, textDocument, zipFolder);
}

private void materializeSortMenuButton() {
    if (sortMenuButton == null) {
        return;
    }
    javafx.scene.control.MenuItem name = new javafx.scene.control.MenuItem("Name");
    name.setUserData("NAME");
    name.setOnAction(this::onSortMenuItem);
    javafx.scene.control.MenuItem modified = new javafx.scene.control.MenuItem("Date modified");
    modified.setUserData("MODIFIED");
    modified.setOnAction(this::onSortMenuItem);
    javafx.scene.control.MenuItem type = new javafx.scene.control.MenuItem("Type");
    type.setUserData("TYPE");
    type.setOnAction(this::onSortMenuItem);
    javafx.scene.control.MenuItem size = new javafx.scene.control.MenuItem("Size");
    size.setUserData("SIZE");
    size.setOnAction(this::onSortMenuItem);
    sortMenuButton.getItems().setAll(name, modified, type, size);
}

private void materializeViewMenuButton() {
    if (viewMenuButton == null) {
        return;
    }
    CustomMenuItem extraLargeItem = createViewModeMenuItem(ViewMode.EXTRA_LARGE_ICONS, "Extra large icons", "/icons/view.extra.large.png", false);
    viewExtraLargeIcons = (RadioButton) extraLargeItem.getProperties().get("viewRadio");

    CustomMenuItem largeItem = createViewModeMenuItem(ViewMode.LARGE_ICONS, "Large icons", "/icons/view.large.png", false);
    viewLargeIcons = (RadioButton) largeItem.getProperties().get("viewRadio");

    CustomMenuItem mediumItem = createViewModeMenuItem(ViewMode.MEDIUM_ICONS, "Medium icons", "/icons/view.medium.png", false);
    viewMediumIcons = (RadioButton) mediumItem.getProperties().get("viewRadio");

    CustomMenuItem smallItem = createViewModeMenuItem(ViewMode.SMALL_ICONS, "Small icons", "/icons/view.small.png", false);
    viewSmallIcons = (RadioButton) smallItem.getProperties().get("viewRadio");

    CustomMenuItem listItem = createViewModeMenuItem(ViewMode.LIST, "List", "/icons/view.list.png", false);
    viewList = (RadioButton) listItem.getProperties().get("viewRadio");

    CustomMenuItem detailsItem = createViewModeMenuItem(ViewMode.DETAILS, "Details", "/icons/view.details.png", true);
    viewDetails = (RadioButton) detailsItem.getProperties().get("viewRadio");

    CustomMenuItem tilesItem = createViewModeMenuItem(ViewMode.TILES, "Tiles", "/icons/view.tiles.png", false);
    viewTiles = (RadioButton) tilesItem.getProperties().get("viewRadio");

    viewContentItem = createViewModeMenuItem(ViewMode.CONTENT, "Content", "/icons/view.content.png", false);
    viewContent = (RadioButton) viewContentItem.getProperties().get("viewRadio");

    detailsPaneRowItem = createPaneToggleMenuItem("Details pane", "/icons/view.details.pane.png", detailsBox != null && detailsBox.isVisible());
    detailsPaneMenuItem = (RadioButton) detailsPaneRowItem.getProperties().get("paneRadio");

    previewPaneRowItem = createPaneToggleMenuItem("Preview pane", "/icons/view.preview.pane.png", previewBox != null && previewBox.isVisible());
    previewPaneMenuItem = (RadioButton) previewPaneRowItem.getProperties().get("paneRadio");

    viewMenuButton.getItems().setAll(
            extraLargeItem,
            largeItem,
            mediumItem,
            smallItem,
            listItem,
            detailsItem,
            tilesItem,
            viewContentItem,
            createSeparatorMenuItem(),
            detailsPaneRowItem,
            previewPaneRowItem,
            createShowSubMenu()
    );
    configureViewMenu();
}

private void materializeSeeMoreMenuButton() {
    if (seeMoreMenuButton == null) {
        return;
    }
    seeMoreMenuButton.getItems().setAll(
            createTextMenuItem("Undo", createMenuImageIcon("/icons/see_more_undo.png", 16.0)),
            createSeparatorMenuItem(),
            createTextMenuItem("Compress to ZIP file", createMenuGlyph("\uF012", "fluent-icon", "menuitem-icon-gap")),
            createTextMenuItem("Pin to Quick access", createMenuImageIcon("/icons/see_more_pin_to_quick_access.png", 16.0)),
            createTextMenuItem("Copy path", createMenuGlyph("\uE8C8", "fluent-icon", "menuitem-icon-gap")),
            createSeparatorMenuItem(),
            createTextMenuItem("Select all", createMenuImageIcon("/icons/see_more_select_all.png", 16.0)),
            createTextMenuItem("Select none", createMenuImageIcon("/icons/see_more_select_none.png", 16.0)),
            createTextMenuItem("Invert selection", createMenuImageIcon("/icons/see_more_invert_selection.png", 16.0)),
            createSeparatorMenuItem(),
            createTextMenuItem("Properties", createMenuGlyph("\uE946", "fluent-icon", "menuitem-icon-gap")),
            createTextMenuItem("Options:", createMenuGlyph("\uE713", "fluent-icon", "menuitem-icon-gap"))
    );
    wireSeeMoreMenuActions();
}

private StartupWorkQueue getStartupWorkQueue() {
    Scene scene = root != null ? root.getScene() : null;
    if (scene == null) {
        scene = boundScene;
    }
    if (scene == null) {
        return null;
    }
    Object value = scene.getProperties().get(MainApp.PROP_STARTUP_WORK_QUEUE);
    return value instanceof StartupWorkQueue queue ? queue : null;
}

private void scheduleZeroHitchPrewarm() {
    if (!startupZeroHitchPrewarmScheduled.compareAndSet(false, true)) {
        return;
    }
    StartupWorkQueue queue = getStartupWorkQueue();
    if (queue == null) {
        Platform.runLater(this::runZeroHitchPrewarmFallback);
        return;
    }
    queue.runOpportunisticIdle(() -> prewarmMenuButton(viewMenuButton, "viewMenuButton prewarm", this::materializeViewMenuButton));
    queue.runOpportunisticIdle(() -> prewarmMenuButton(seeMoreMenuButton, "seeMoreMenuButton prewarm", this::materializeSeeMoreMenuButton));
    queue.runOpportunisticIdle(() -> prewarmMenuButton(sortMenuButton, "sortMenuButton prewarm", this::materializeSortMenuButton));
    queue.runOpportunisticIdle(() -> prewarmMenuButton(newMenuButton, "newMenuButton prewarm", this::materializeNewMenuButton));
    for (ViewMode mode : buildPredictivePrewarmModes()) {
        final ViewMode nextMode = mode;
        queue.runOpportunisticIdle(() -> prewarmFileViewMode(nextMode));
    }
}

private void runZeroHitchPrewarmFallback() {
    prewarmMenuButton(viewMenuButton, "viewMenuButton prewarm", this::materializeViewMenuButton);
    prewarmMenuButton(seeMoreMenuButton, "seeMoreMenuButton prewarm", this::materializeSeeMoreMenuButton);
    prewarmMenuButton(sortMenuButton, "sortMenuButton prewarm", this::materializeSortMenuButton);
    prewarmMenuButton(newMenuButton, "newMenuButton prewarm", this::materializeNewMenuButton);
    for (ViewMode mode : buildPredictivePrewarmModes()) {
        prewarmFileViewMode(mode);
    }
}

private void prewarmMenuButton(javafx.scene.control.MenuButton menuButton, String traceLabel, Runnable populator) {
    ensureMenuButtonMaterialized(menuButton, traceLabel, populator);
}

private List<ViewMode> buildPredictivePrewarmModes() {
    List<ViewMode> order = new ArrayList<>();
    ViewMode anchor = isGridIconMode(lastIconViewMode) ? lastIconViewMode : ViewMode.MEDIUM_ICONS;
    switch (anchor) {
        case EXTRA_LARGE_ICONS -> {
            order.add(ViewMode.EXTRA_LARGE_ICONS);
            order.add(ViewMode.LARGE_ICONS);
            order.add(ViewMode.MEDIUM_ICONS);
            order.add(ViewMode.SMALL_ICONS);
        }
        case LARGE_ICONS -> {
            order.add(ViewMode.LARGE_ICONS);
            order.add(ViewMode.EXTRA_LARGE_ICONS);
            order.add(ViewMode.MEDIUM_ICONS);
            order.add(ViewMode.SMALL_ICONS);
        }
        case SMALL_ICONS -> {
            order.add(ViewMode.SMALL_ICONS);
            order.add(ViewMode.MEDIUM_ICONS);
            order.add(ViewMode.LARGE_ICONS);
            order.add(ViewMode.EXTRA_LARGE_ICONS);
        }
        default -> {
            order.add(ViewMode.MEDIUM_ICONS);
            order.add(ViewMode.LARGE_ICONS);
            order.add(ViewMode.SMALL_ICONS);
            order.add(ViewMode.EXTRA_LARGE_ICONS);
        }
    }
    order.add(ViewMode.LIST);
    order.add(ViewMode.TILES);
    order.add(ViewMode.CONTENT);
    return order;
}

private void prewarmFileViewMode(ViewMode mode) {
    if (mode == null) {
        return;
    }
    initializeFileViewModules();
    if (modularFileViewHost == null) {
        return;
    }
    if (mode == ViewMode.DETAILS) {
        ensureDetailsFileViewLoaded();
        return;
    }
    String viewKey = fileViewKeyFor(mode);
    if (viewKey == null) {
        return;
    }
    if (modularFileViewHost.getViewRoot(viewKey) != null) {
        return;
    }
    ensureIconFileViewLoaded(mode);
}

private void scheduleDeferredOperationQueueBindings() {
    if (context == null) {
        return;
    }
    if (!startupFirstInteractionReadyMarked.get()) {
        return;
    }
    installDeferredOperationQueueBindings();
}

private void activateDeferredExplorerContextServices() {
    if (context == null) {
        return;
    }
    if (!deferredExplorerContextActivationScheduled.compareAndSet(false, true)) {
        return;
    }
    StartupTrace.mark("ExplorerContext stageB request");
    context.activateDeferredServicesAsync();
    Platform.runLater(this::installDeferredOperationQueueBindings);
}

private void installDeferredOperationQueueBindings() {
    if (context == null) {
        return;
    }
    if (!deferredOperationQueueBindingsInstalled.compareAndSet(false, true)) {
        return;
    }
    StartupTrace.mark("MainController operation queue bindings begin");
    var operationQueueService = context.operationQueueService();
    operationQueueService.activeOperationProperty().addListener((obs, oldOp, newOp) -> {
        if (newOp != null && operationsToggle != null) {
            Platform.runLater(() -> operationsToggle.setSelected(true));
        }
    });
    operationQueueService.runningProperty().addListener((obs, wasRunning, isRunning) -> {
        if (!isRunning && operationsToggle != null) {
            PauseTransition pt = new PauseTransition(Duration.seconds(2));
            pt.setOnFinished(ev -> {
                boolean empty = operationQueueService.getQueue() == null || operationQueueService.getQueue().isEmpty();
                if (empty && !operationQueueService.runningProperty().get()) {
                    operationsToggle.setSelected(false);
                }
            });
            pt.play();
        }
    });
    StartupTrace.mark("MainController operation queue bindings end");
}
@Override
/**
 * initialize.
 *
 * @param location TODO
 * @param resources TODO
 */
public void initialize(URL location, ResourceBundle resources) {
    LogSupport.enter(LOG, "initialize");
    StartupTrace.mark("MainController.initialize enter");
    this.fxmlInitialized = true;
    initializeFileViewModules();
    updateViewMenuButtonGraphic();
    // Phase 3.6.3+: operations now render through the shared inspector host.
    if (operationsToggle != null) {
        operationsToggle.selectedProperty().addListener((obs, oldV, newV) -> updateSidePaneVisibility());
    }
    // Phase 3.4.4: ExplorerContext is injected by MainApp via Lifecycle.attach(context) AFTER FXMLLoader construction.
    // JavaFX calls initialize() during load, so we must defer initialization that depends on context/services until attach().
    if (this.context == null) {
        StartupTrace.mark("MainController.initialize exit (awaiting context)");
        return;
    }
    initializeWithContext();
    StartupTrace.mark("MainController.initialize exit");
}
/**
 * initializeWithContext.
 *
 */
private void initializeWithContext() {
        LogSupport.enter(LOG, "initializeWithContext");
        StartupTrace.mark("MainController.initializeWithContext enter");
        if (contextInitialized) {
            StartupTrace.mark("MainController.initializeWithContext skip (already initialized)");
            return;
        }
        contextInitialized = true;
        restoreWorkspaceShellGeometryPreferences();
        if (detailsToggle != null) {
            detailsToggle.setOnAction(this::onDetailsToggle);
        }
        if (previewToggle != null) {
            previewToggle.setOnAction(this::onPreviewToggle);
        }
        if (operationsToggle != null) {
            operationsToggle.setOnAction(this::onOperationsToggle);
        }
        configureTree();
        configureNavigationPaneParity();
        configureTable();
        configureResponsiveTableViewportLayout();
        configureThemeToggle();
        configureBreadcrumbs();
        configureSearch();
        configureStatusBar();
        configureToolbarActions();
        configureTabsAndHome();
        configureCommandFlyoutParity();
        configureViewMenu();
        configureSidePaneParity();
        configureIconActivation();
        scheduleDeferredBootstrapIncludes();
        updateTopChromeState();
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
        StartupTrace.mark("MainController.initializeWithContext exit");
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
        restoreInspectorModePreference();
        updateSidePaneVisibility();
        setStatus("Ready.");
    
    }
/**
 * setScene.
 *
 * @param scene TODO
 */
    public void setScene(Scene scene) {
        LogSupport.enter(LOG, "setScene");
        if (scene == null) {
            return;
        }

        this.boundScene = scene;
        // Phase 4A.3: Keep setScene itself lightweight. Anything that triggers broad
        // CSS/layout work must be deferred so we do not block first interactivity.
        StartupTrace.mark("MainController.setScene enter");

        if (!zoomShortcutsInstalled) {
            installZoomShortcuts(scene);
            zoomShortcutsInstalled = true;
        }
        if (!explorerShortcutsInstalled) {
            installExplorerShortcuts(scene);
            installCtrlScrollViewShortcuts(scene);
            explorerShortcutsInstalled = true;
        }
        scheduleDeferredBootstrapIncludes();
        scene.widthProperty().addListener((obs, oldV, newV) -> scheduleCommandBarCompaction(newV == null ? 0.0 : newV.doubleValue()));
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
        // Phase 4P.9BX: route theme/font/motion startup work through the shared startup queue when present
        // so cold-start shell visibility is not competing with all follow-on CSS/layout work at once.
        final StartupWorkQueue startupWorkQueue = scene.getProperties().get(MainApp.PROP_STARTUP_WORK_QUEUE) instanceof StartupWorkQueue q ? q : null;
        final Runnable themeTask = () -> {
            try {
                StartupTrace.mark("MainController.setScene deferred theme begin");
            } catch (Throwable ignored) {}
            try {
                uiFontFamilyCss = buildUiFontFamilyCss(scene);
                applyThemeToCurrentScene(scene);
            } catch (Throwable ignored) {
            }
            try {
                StartupTrace.mark("MainController.setScene deferred theme end");
            } catch (Throwable ignored) {}
        };
        final Runnable fontTask = () -> {
            try {
                StartupTrace.mark("MainController.setScene deferred font begin");
            } catch (Throwable ignored) {}
            try {
                applyUiFontSize(scene);
            } catch (Throwable ignored) {
            }
            try {
                StartupTrace.mark("MainController.setScene deferred font end");
            } catch (Throwable ignored) {}
        };
        final Runnable motionTask = () -> {
            try {
                StartupTrace.mark("MainController.setScene deferred motion begin");
            } catch (Throwable ignored) {}
            try {
                FluentMotionSupport.install(root != null ? root : scene.getRoot());
                Platform.runLater(() -> FluentMotionSupport.install(root != null ? root : scene.getRoot()));
            } catch (Throwable ignored) {
            }
            try {
                StartupTrace.mark("MainController.setScene deferred motion end");
            } catch (Throwable ignored) {}
        };
        if (startupWorkQueue != null) {
            startupWorkQueue.runCritical(themeTask);
            startupWorkQueue.runIdle(fontTask);
            startupWorkQueue.runIdle(motionTask);
        } else {
            Platform.runLater(() -> {
                themeTask.run();
                Platform.runLater(fontTask);
                Platform.runLater(() -> Platform.runLater(motionTask));
            });
        }
        // Phase 4B.2+: feed user-activity signals to the metadata budgeter so it can stay idle during interaction.
        try {
            scene.addEventFilter(javafx.scene.input.InputEvent.ANY, e -> {
                if (metadataBudgetService != null) {
                    metadataBudgetService.notifyUserActivity();
                }
            });
            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> noteStartupInteractionReady());
            scene.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> noteStartupInteractionReady());
            scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> noteStartupInteractionReady());
        } catch (Exception ignored) {
        }
        Platform.runLater(() -> {
            ensureStartupWindowSize(scene);
            bindWindowChromeState(scene);
            updateWindowTitle(currentDirectory);
            updateNavigationButtonsState();
        updateSearchPrompt(currentDirectory);
        updateTopChromeState();
        });
        StartupTrace.mark("MainController.setScene exit");
    }
/**
 * enterSafeMode.
 *
 */
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
/**
 * openInitialFolder.
 *
 * @param initialFolder TODO
 */
    public void openInitialFolder(Path initialFolder) {
        LogSupport.enter(LOG, "openInitialFolder");
        if (SAFE_MODE && !Boolean.getBoolean("fileexplorer.safeMode.allowInitialDirectoryLoad")) {
            setStatus("Safe Mode enabled: initial directory load is disabled. "
                + "To load the initial directory anyway, run with -Dfileexplorer.safeMode.allowInitialDirectoryLoad=true.");
            noteStartupInitialDirectoryFirstBatchCommitted();
            noteStartupInteractionReady();
            openStartupThumbnailWarmupGate();
            return;
        }
        if (SAFE_MODE) {
            setStatus("Safe Mode enabled: initial directory load override is ON.");
        }
        if (initialFolder == null) {
            return;
        }
        Path target = initialFolder.normalize();
        Runnable openTask = () -> {
            if (startupInitialDirectoryLoadStarted.compareAndSet(false, true)) {
                StartupTrace.mark("initial directory hydration begin: " + target);
            }
            navigateToFolder(target, false);
        };
        if (Platform.isFxApplicationThread()) {
            openTask.run();
        } else {
            Platform.runLater(openTask);
        }
    }

    /**
     * Phase 4C.1: Optional soak navigation helper (used only when fileexplorer.soak.enabled=true).
     */
    public void soakNavigateToFolder(Path folder) {
        if (folder == null) return;
        Path target = folder.normalize();
        Platform.runLater(() -> navigateToFolder(target, false));
    }

    /**
     * Phase 4C.1: expose internal budget service for diagnostics HUD.
     */
    public com.fileexplorer.service.filesystem.FileMetadataBudgetService getMetadataBudgetService() {
        return metadataBudgetService;
    }

    /**
     * Schedules the first directory hydration after the shell and main UI have both painted.
     *
     * @param initialFolder directory to hydrate once post-show startup work begins
     */
    public void beginPostShowHydration(Path initialFolder) {
        if (initialFolder == null) {
            return;
        }
        if (!startupPostShowHydrationScheduled.compareAndSet(false, true)) {
            return;
        }
        StartupTrace.mark("post-show hydration scheduled");
        Runnable task = () -> {
            startupFirstInteractionTrackingArmed.set(true);
            StartupTrace.mark("begin post-show hydration");
            openInitialFolder(initialFolder);
        };
        if (Platform.isFxApplicationThread()) {
            Platform.runLater(() -> Platform.runLater(task));
        } else {
            Platform.runLater(() -> Platform.runLater(task));
        }
    }

    private void noteStartupInitialDirectoryFirstBatchCommitted() {
        if (!startupInitialDirectoryFirstBatchCommitted.compareAndSet(false, true)) {
            return;
        }
        StartupTrace.mark("initial directory hydration first batch committed");
        if (startupIconWarmupGateOpened.compareAndSet(false, true)) {
            AsyncIconService.getInstance().setEnabled(true);
            StartupTrace.mark("icon warmup gate open");
        }
        scheduleZeroHitchPrewarm();
        startupFirstInteractionFallback.playFromStart();
    }

    private void noteStartupInteractionReady() {
        if (!startupFirstInteractionTrackingArmed.get() || !startupInitialDirectoryFirstBatchCommitted.get()) {
            return;
        }
        if (!startupFirstInteractionReadyMarked.compareAndSet(false, true)) {
            return;
        }
        StartupTrace.mark("first interaction ready");
        activateDeferredExplorerContextServices();
        scheduleDeferredProgressPaneLoad();
        startupThumbnailGateDebounce.playFromStart();
    }

    private void openStartupThumbnailWarmupGate() {
        if (!startupFirstInteractionReadyMarked.get()) {
            return;
        }
        if (!startupThumbnailWarmupGateOpened.compareAndSet(false, true)) {
            return;
        }
        AsyncThumbnailService.getInstance().setEnabled(true);
        StartupTrace.mark("thumbnail warmup gate open");
        scheduleCurrentFolderThumbnailWarmup();
    }
    // ---------------------------------------------------------------------
    // FXML actions
    // ---------------------------------------------------------------------
    @FXML
    private void onNavigateBackButton(ActionEvent e) {
        LogSupport.enter(LOG, "onNavigateBackButton");
        navigateBack();
    }

    @FXML
    private void onNavigateForwardButton(ActionEvent e) {
        LogSupport.enter(LOG, "onNavigateForwardButton");
        navigateForward();
    }

    @FXML
    private void onNavigateUpButton(ActionEvent e) {
        LogSupport.enter(LOG, "onNavigateUpButton");
        navigateUp();
    }

    @FXML
    private void onRefreshButton(ActionEvent e) {
        LogSupport.enter(LOG, "onRefreshButton");
        refresh();
    }

    @FXML
/**
 * onViewDetails.
 *
 * @param e TODO
 */
    private void onViewDetails(ActionEvent e) {
        LogSupport.enter(LOG, "onViewDetails");
        setViewMode(ViewMode.DETAILS);
    }
    @FXML
/**
 * onViewLargeIcons.
 *
 * @param e TODO
 */
    private void onViewLargeIcons(ActionEvent e) {
        LogSupport.enter(LOG, "onViewLargeIcons");
        setViewMode(ViewMode.LARGE_ICONS);
    }
    @FXML
    private void onStatusDetailsView(ActionEvent e) {
        LogSupport.enter(LOG, "onStatusDetailsView");
        setViewMode(ViewMode.DETAILS);
    }
    @FXML
    private void onStatusLargeIconsView(ActionEvent e) {
        LogSupport.enter(LOG, "onStatusLargeIconsView");
        setViewMode(ViewMode.EXTRA_LARGE_ICONS);
    }
    @FXML
/**
 * onDetailsToggle.
 *
 * @param e TODO
 */
    private void onDetailsToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onDetailsToggle");
        boolean show = detailsToggle != null && detailsToggle.isSelected();
        setDetailsPaneVisible(show);
        updateTopChromeState();
    }
    @FXML
    private void onPreviewToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onPreviewToggle");
        boolean show = previewToggle != null && previewToggle.isSelected();
        setPreviewPaneVisible(show);
        updateTopChromeState();
    }
    @FXML
/**
 * onOperationsToggle.
 *
 * @param e TODO
 */
    private void onOperationsToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onOperationsToggle");
        updateSidePaneVisibility();
        updateTopChromeState();
    }
    @FXML
/**
 * onSortMenuItem.
 *
 * @param e TODO
 */
    private void onSortMenuItem(ActionEvent e) {
        LogSupport.enter(LOG, "onSortMenuItem");
        if (!(e.getSource() instanceof javafx.scene.control.MenuItem mi)) {
            return;
        }
        Object ud = mi.getUserData();
        String key = (ud != null) ? ud.toString() : mi.getText();
        SortKey newKey = switch (key) {
            case "NAME", "Name" -> SortKey.NAME;
            case "MODIFIED", "Date modified", "Modified" -> SortKey.MODIFIED;
            case "TYPE", "Type" -> SortKey.TYPE;
            case "SIZE", "Size" -> SortKey.SIZE;
            default -> SortKey.NAME;
        };
        toggleSortKeyFromToolbarMenu(newKey);
        e.consume();
    }

    private void toggleSortKeyFromToolbarMenu(SortKey newKey) {
        if (newKey == null) {
            newKey = SortKey.NAME;
        }
        if (newKey == currentSortKey) {
            sortAscending = !sortAscending;
        } else {
            currentSortKey = newKey;
            sortAscending = true;
        }
        applyToolbarSort();
    }

    private void setSortKeyFromBackgroundMenu(SortKey newKey) {
        if (newKey == null) {
            newKey = SortKey.NAME;
        }
        if (newKey != currentSortKey) {
            currentSortKey = newKey;
            sortAscending = true;
        }
        applyToolbarSort();
        setStatus("Sort by: " + switch (newKey) {
            case NAME -> "Name";
            case MODIFIED -> "Date modified";
            case TYPE -> "Type";
            case SIZE -> "Size";
        });
    }
    @FXML
/**
 * onShowOperationHistory.
 *
 * @param e TODO
 */
    private void onShowOperationHistory(javafx.event.ActionEvent e) {
        LogSupport.enter(LOG, "onShowOperationHistory");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fileexplorer/ui/layout/OperationHistoryWindow.fxml"));
            Parent root = loader.load();
            OperationHistoryController c = loader.getController();
            if (c != null) {
                c.attach(context);
            }
            Stage s = new Stage();
            s.setTitle("Operation History");
            s.initModality(Modality.NONE);
            Scene scene = new Scene(root, 1050, 600);
            if (context != null && context.themeService() != null) {
                context.themeService().apply(scene);
            }
            s.setScene(scene);
            s.show();
        } catch (Exception ex) {
            // best effort - avoid crashing the app
            LOG.log(Level.WARNING, "Failed to open Operation History", ex);
        }
    }
    @FXML
/**
 * onShowCommandLog.
 *
 * @param e TODO
 */
    private void onShowCommandLog(javafx.event.ActionEvent e) {
        LogSupport.enter(LOG, "onShowCommandLog");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fileexplorer/ui/layout/CommandLogWindow.fxml"));
            Parent root = loader.load();
            CommandLogController c = loader.getController();
            if (c != null) {
                c.attach(context);
            }
            Stage s = new Stage();
            s.setTitle("Command Log");
            s.initModality(Modality.NONE);
            Scene scene = new Scene(root, 1050, 600);
            if (context != null && context.themeService() != null) {
                context.themeService().apply(scene);
            }
            s.setScene(scene);
            s.show();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to open Command Log", ex);
        }
    }
/**
 * applyToolbarSort.
 *
 */
    private void applyExplorerSortToTableItems() {
        if (tableItems == null) {
            return;
        }
        // Phase 4B.3: Huge-folder mode is optimized for low CPU; avoid expensive filesystem stat sorts.
        if (hugeFolderModeActive && (currentSortKey == SortKey.SIZE || currentSortKey == SortKey.MODIFIED)) {
            currentSortKey = SortKey.NAME;
            sortAscending = true;
            if (statusLabel != null) {
                statusLabel.setText("Large folder: heavy sort deferred — sorting by Name (toggle via filter/search)");
            }
        }
        java.util.Comparator<FileItem> cmp = switch (currentSortKey) {
            case NAME -> java.util.Comparator.comparing(
                    (FileItem fi) -> fi.name() == null ? "" : fi.name(),
                    String.CASE_INSENSITIVE_ORDER
            );
            case TYPE -> java.util.Comparator.comparing(
                    (FileItem fi) -> fi.type() == null ? "" : fi.type(),
                    String.CASE_INSENSITIVE_ORDER
            ).thenComparing(fi -> fi.name() == null ? "" : fi.name(), String.CASE_INSENSITIVE_ORDER);
            case SIZE -> java.util.Comparator.comparingLong((FileItem fi) -> {
                if (fi == null) return -1L;
                if (java.nio.file.Files.isDirectory(fi.path())) return -1L;
                try { return java.nio.file.Files.size(fi.path()); } catch (Exception ex) { return -1L; }
            }).thenComparing(fi -> fi.name() == null ? "" : fi.name(), String.CASE_INSENSITIVE_ORDER);
            case MODIFIED -> java.util.Comparator.comparingLong((FileItem fi) -> {
                if (fi == null) return 0L;
                try { return java.nio.file.Files.getLastModifiedTime(fi.path()).toMillis(); } catch (Exception ex) { return 0L; }
            }).thenComparing(fi -> fi.name() == null ? "" : fi.name(), String.CASE_INSENSITIVE_ORDER);
        };
        // Explorer-style: directories first.
        cmp = java.util.Comparator.comparing((FileItem fi) -> !java.nio.file.Files.isDirectory(fi.path())).thenComparing(cmp);
        if (!sortAscending) {
            cmp = cmp.reversed();
        }
        javafx.collections.FXCollections.sort(tableItems, cmp);
    }

    private void applyToolbarSort() {
        // IMPORTANT: The TableView is bound to a SortedList whose comparator is driven by
        // the TableView's internal sort order. If any column sort order is active (even implicitly),
        // it will override the underlying list order.
        // Toolbar sort is intended to behave like Explorer's sort button, so we clear any
        // active column sort order first and then sort the backing list.
        if (fileTable != null) {
            try {
                fileTable.getSortOrder().clear();
            } catch (Exception ignore) {
                // ignore
            }
        }
        applyExplorerSortToTableItems();
        // Ensure TableView refreshes after a toolbar-driven resort.
        if (fileTable != null) {
            try {
                advanceDetailsAsyncBindingEpoch();
                fileTable.sort();
                fileTable.refresh();
            } catch (Exception ignore) {
                // ignore
            }
        }
        // Rebuild icon tiles if currently in icon modes
        if (!SAFE_MODE && isIconMode(viewMode)) {
            rebuildIconTiles();
        }
        // TableView uses SortedList wrapper; sorting its backing list changes the "unsorted" order.
        // If user has an active sort on table columns, TableView will still apply it.
    }
    
    @FXML
/**
 * onViewModeRadio.
 *
 * @param e TODO
 */
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
    private void onViewModeRowAction(ActionEvent e) {
        LogSupport.enter(LOG, "onViewModeRowAction");
        if (e == null || e.getSource() == null) {
            return;
        }
        Object src = e.getSource();
        if (src instanceof javafx.scene.control.MenuItem mi) {
            Object ud = mi.getUserData();
            String mode = ud == null ? "" : String.valueOf(ud).trim();
            ViewMode parsed = parseViewMode(mode);
            if (parsed != null) {
                setViewMode(parsed);
            }
        }
    }
    @FXML
/**
 * onDetailsPaneRadioToggle.
 *
 * @param e TODO
 */
    private void onDetailsPaneRadioToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onDetailsPaneRadioToggle");
        boolean show = detailsPaneMenuItem != null && detailsPaneMenuItem.isSelected();
        setDetailsPaneVisible(show);
        updateTopChromeState();
    }

    private void setMenuSidePaneSelection(boolean showDetails, boolean showPreview) {
        LogSupport.enter(LOG, "setMenuSidePaneSelection");
        if (showPreview) {
            setPreviewPaneVisible(true);
            return;
        }
        if (showDetails) {
            setDetailsPaneVisible(true);
            return;
        }
        setDetailsPaneVisible(false);
        setPreviewPaneVisible(false);
    }

    @FXML
/**
 * onPreviewPaneRadioToggle.
 *
 * @param e TODO
 */
    private void onPreviewPaneRadioToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onPreviewPaneRadioToggle");
        boolean show = previewPaneMenuItem != null && previewPaneMenuItem.isSelected();
        setPreviewPaneVisible(show);
        updateTopChromeState();
    }
    @FXML
    private void onDetailsPaneRowAction(ActionEvent e) {
        LogSupport.enter(LOG, "onDetailsPaneRowAction");
        if (detailsPaneMenuItem == null) {
            return;
        }
        detailsPaneMenuItem.setSelected(!detailsPaneMenuItem.isSelected());
        onDetailsPaneRadioToggle(e);
    }
    @FXML
    private void onPreviewPaneRowAction(ActionEvent e) {
        LogSupport.enter(LOG, "onPreviewPaneRowAction");
        if (previewPaneMenuItem == null) {
            return;
        }
        previewPaneMenuItem.setSelected(!previewPaneMenuItem.isSelected());
        onPreviewPaneRadioToggle(e);
    }
    @FXML
/**
 * onShowNavigationPaneToggle.
 *
 * @param e TODO
 */
    private void onShowNavigationPaneToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onShowNavigationPaneToggle");
        boolean show = showNavigationPaneMenuItem != null && showNavigationPaneMenuItem.isSelected();
        setNavigationPaneVisible(show);
    }
    @FXML
/**
 * onCompactViewToggle.
 *
 * @param e TODO
 */
    private void onCompactViewToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onCompactViewToggle");
        boolean on = showCompactViewMenuItem != null && showCompactViewMenuItem.isSelected();
        setCompactView(on);
    }
    @FXML
/**
 * onItemCheckBoxesToggle.
 *
 * @param e TODO
 */
    private void onItemCheckBoxesToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onItemCheckBoxesToggle");
        boolean on = showItemCheckBoxesMenuItem != null && showItemCheckBoxesMenuItem.isSelected();
        showItemCheckBoxes = on;
        // Re-apply the current view mode so icon tiles re-render with/without checkboxes.
        // Table view may ignore this setting; icon view uses it during tile construction.
        setViewMode(viewMode);
    }
    @FXML
/**
 * onFileNameExtensionsToggle.
 *
 * @param e TODO
 */
    private void onFileNameExtensionsToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onFileNameExtensionsToggle");
        boolean on = showFileNameExtensionsMenuItem != null && showFileNameExtensionsMenuItem.isSelected();
        showFileNameExtensions = on;
        refreshCurrentDirectoryView();
    }
    @FXML
/**
 * onHiddenItemsToggle.
 *
 * @param e TODO
 */
    private void onHiddenItemsToggle(ActionEvent e) {
        LogSupport.enter(LOG, "onHiddenItemsToggle");
        boolean on = showHiddenItemsMenuItem != null && showHiddenItemsMenuItem.isSelected();
        showHiddenItems = on;
        refreshCurrentDirectoryView();
    }
/**
 * parseViewMode.
 *
 * @param s TODO
 * @return TODO
 */
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
/**
 * installExplorerShortcuts.
 *
 * @param scene TODO
 */
    private void installExplorerShortcuts(Scene scene) {
        LogSupport.enter(LOG, "installExplorerShortcuts");
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            KeyCode code = e.getCode();
            
// Ctrl + Z / Ctrl + Y: Command undo/redo. Do not steal from text inputs.
if (e.isControlDown() && !e.isAltDown() && !e.isMetaDown() && !e.isShiftDown()) {
    if (!(e.getTarget() instanceof TextInputControl)) {
        if (code == KeyCode.Z) {
            try {
                if (context != null && context.commandManager() != null && context.commandManager().canUndo()) {
                    com.fileexplorer.service.ops.command.CommandManager.ExecutedCommand undoCommand = peekUndoCommand();
                    InlineRenameSession shellStateSession = captureShellCommandRefreshSessionForUndoRedo(undoCommand != null ? undoCommand.command() : null);
                    context.commandManager().undo();
                    applyShellStateRefreshPlanForUndoRedo(undoCommand != null ? undoCommand.command() : null,
                            ExplorerCommandAction.UNDO,
                            shellStateSession);
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Undo failed", ex);
            }
            e.consume();
            return;
        }
        if (code == KeyCode.Y) {
            try {
                if (context != null && context.commandManager() != null && context.commandManager().canRedo()) {
                    com.fileexplorer.service.ops.command.CommandManager.ExecutedCommand redoCommand = peekRedoCommand();
                    InlineRenameSession shellStateSession = captureShellCommandRefreshSessionForUndoRedo(redoCommand != null ? redoCommand.command() : null);
                    context.commandManager().redo();
                    applyShellStateRefreshPlanForUndoRedo(redoCommand != null ? redoCommand.command() : null,
                            ExplorerCommandAction.REDO,
                            shellStateSession);
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Redo failed", ex);
            }
            e.consume();
            return;
        }
    }
}
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
            
// PageUp/PageDown: huge-folder paging (do not steal from text inputs)
if (!e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
    if (!(e.getTarget() instanceof TextInputControl)) {
        if (code == KeyCode.PAGE_DOWN) {
            navigateHugeFolderPage(+1);
            e.consume();
            return;
        }
        if (code == KeyCode.PAGE_UP) {
            navigateHugeFolderPage(-1);
            e.consume();
            return;
        }
    }
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
            // Ctrl + T: New tab (new window fallback for now)
            if (e.isControlDown() && !e.isShiftDown() && code == KeyCode.T) {
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
            // Ctrl + F / Ctrl + E: focus search box
            if (e.isControlDown() && !e.isShiftDown() && (code == KeyCode.F || code == KeyCode.E)) {
                focusSearch();
                e.consume();
                return;
            }
            // F3 / Shift+F3: Find Next / Previous (if a search query is active), otherwise focus search
            if (!e.isAltDown() && !e.isControlDown() && code == KeyCode.F3) {
                if (activeSearchQuery == null || activeSearchQuery.isBlank()) {
                    focusSearch();
                } else {
                    findNextMatch(!e.isShiftDown());
                }
                e.consume();
                return;
            }
            // Ctrl+G / Ctrl+Shift+G: Find Next / Previous
            if (e.isControlDown() && code == KeyCode.G) {
                if (activeSearchQuery == null || activeSearchQuery.isBlank()) {
                    focusSearch();
                } else {
                    findNextMatch(!e.isShiftDown());
                }
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
    
/**
 * installCtrlScrollViewShortcuts.
 *
 * @param scene TODO
 */
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
/**
 * installZoomShortcuts.
 *
 * @param scene TODO
 */
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
    
/**
 * clampUiFont.
 *
 */
    private void clampUiFont() {
        LogSupport.enter(LOG, "clampUiFont");
        uiFontSizePx = clamp(uiFontSizePx, UI_FONT_MIN_PX, UI_FONT_MAX_PX);
    }
/**
 * adjustUiFontSize.
 *
 * @param deltaPx TODO
 */
private void adjustUiFontSize(double deltaPx) {
    LogSupport.enter(LOG, "adjustUiFontSize");
        uiFontSizePx = clamp(uiFontSizePx + deltaPx, UI_FONT_MIN_PX, UI_FONT_MAX_PX);
        setStatus("UI size: " + (int) uiFontSizePx + "px");
    }
/**
 * applyUiFontSize.
 *
 * @param scene TODO
 */
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
            applyFolderTreeMetricsHard();
            // Ensure row height stays compact and Windows Explorer-like while still respecting the active font size.
            double rowH = Math.max(FOLDER_TREE_ROW_HEIGHT_PX, Math.ceil(treeFontPx + 10.0));
            folderTree.setFixedCellSize(rowH);
            // Refresh can be expensive on startup; only do it if explicitly enabled.
            if (Boolean.parseBoolean(System.getProperty("fileexplorer.ui.refreshAfterFontApply", "false"))) {
                Platform.runLater(() -> {
                    try { folderTree.refresh(); } catch (Exception ignored) {}
                });
            }
        }
        // Table: keep base font; headers/rows will follow.
        if (fileTable != null) {
            fileTable.setStyle("-fx-font-family: " + uiFontFamilyCss + "; -fx-font-size: " + uiFontSizePx + "px;");
            // Enforce: row height must be at least (font size + 5px top + 5px bottom).
            double tableRowH = Math.max(30.0, Math.ceil(uiFontSizePx + (UI_MIN_VPAD_PX * 2.0)));
            fileTable.setFixedCellSize(tableRowH);
            if (folderTree != null) {
                folderTree.setFixedCellSize(Math.max(FOLDER_TREE_ROW_HEIGHT_PX, tableRowH));
                applyFolderTreeMetricsHard();
            }
            // Header lookups can be expensive; gate behind enforceMinMetrics.
            if (Boolean.parseBoolean(System.getProperty("fileexplorer.ui.enforceMinMetrics", "false"))) {
                Platform.runLater(() -> applyTableHeaderMetrics(tableRowH));
            }
        }
        // Apply the same minimum vertical metric policy to other key controls.
        applyMinimumMetrics(scene, uiFontSizePx);
    }
    
/**
 * applyTableHeaderMetrics.
 *
 * @param headerAndRowHeightPx TODO
 */
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
/**
 * applyMinimumMetrics.
 *
 * @param scene TODO
 * @param fontPx TODO
 */
    private void applyMinimumMetrics(Scene scene, double fontPx) {
        LogSupport.enter(LOG, "applyMinimumMetrics");
        // Phase 4A.3: This can be very expensive (lookupAll across the full scene graph).
        // Keep it opt-in.
        if (!Boolean.parseBoolean(System.getProperty("fileexplorer.ui.enforceMinMetrics", "false"))) {
            return;
        }
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
/**
 * enforceMinHeight.
 *
 * @param scene TODO
 * @param selector TODO
 * @param minHeightPx TODO
 */
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
/**
 * clamp.
 *
 * @param v TODO
 * @param lo TODO
 * @param hi TODO
 * @return TODO
 */
private double clamp(double v, double lo, double hi) {
    LogSupport.enter(LOG, "clamp");
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return lo;
        }
        if (Double.isNaN(lo) || Double.isInfinite(lo)) {
            lo = 0.0;
        }
        if (Double.isNaN(hi) || Double.isInfinite(hi)) {
            hi = lo;
        }
        if (hi < lo) {
            double swap = lo;
            lo = hi;
            hi = swap;
        }
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    private double sanitizeInspectorWidth(double widthPx) {
        double sanitized = widthPx;
        if (Double.isNaN(sanitized) || Double.isInfinite(sanitized) || sanitized <= 0.0) {
            sanitized = SIDE_PANE_PREF_WIDTH_PX;
        }
        return clamp(sanitized, SIDE_PANE_MIN_WIDTH_PX, INSPECTOR_HOST_MAX_WIDTH_PX);
    }

    private void forceInspectorVisibilityState(boolean show) {
        double targetWidth = sanitizeInspectorWidth(lastKnownInspectorWidthPx);
        if (show) {
            lastKnownInspectorWidthPx = targetWidth;
        }
        if (inspectorHost != null) {
            inspectorHost.setVisible(show);
            inspectorHost.setManaged(show);
            if (show) {
                inspectorHost.setMinWidth(SIDE_PANE_MIN_WIDTH_PX);
                inspectorHost.setPrefWidth(targetWidth);
                inspectorHost.setMaxWidth(INSPECTOR_HOST_MAX_WIDTH_PX);
            } else {
                inspectorHost.setMinWidth(0.0);
                inspectorHost.setPrefWidth(0.0);
            }
            inspectorHost.requestLayout();
        }
        if (sidePane != null) {
            sidePane.setVisible(show);
            sidePane.setManaged(show);
            if (show) {
                sidePane.setMinWidth(SIDE_PANE_MIN_WIDTH_PX);
                sidePane.setPrefWidth(targetWidth);
                sidePane.setMaxWidth(INSPECTOR_HOST_MAX_WIDTH_PX);
            } else {
                sidePane.setMinWidth(0.0);
                sidePane.setPrefWidth(0.0);
            }
            sidePane.requestLayout();
        }
        if (inspectorResizer != null) {
            inspectorResizer.setVisible(show);
            inspectorResizer.setManaged(show);
        }
        if (workspaceShell != null) {
            workspaceShell.requestLayout();
        }
        if (root != null) {
            root.requestLayout();
        }
    }

    private void forceInspectorModePresentation(InspectorMode mode) {
        if (mode == null || mode == InspectorMode.HIDDEN) {
            applyInspectorModeNodeVisibility(InspectorMode.HIDDEN);
            forceInspectorVisibilityState(false);
            return;
        }
        ensureInspectorShellCreated();
        if (mode == InspectorMode.DETAILS) {
            ensureDetailsInspectorCardCreated();
        } else if (mode == InspectorMode.PREVIEW) {
            ensurePreviewInspectorCardCreated();
        } else if (mode == InspectorMode.OPERATIONS) {
            ensureOperationsInspectorCardCreated();
        }
        applyInspectorModeNodeVisibility(mode);
        forceInspectorVisibilityState(true);
    }

private void restoreWorkspaceShellGeometryPreferences() {
    lastKnownNavigationPaneShellWidthPx = clamp(
            prefs.getDouble(PREF_WORKSPACE_NAV_WIDTH_PX, lastKnownNavigationPaneShellWidthPx),
            NAV_TREE_SHELL_MIN_WIDTH_PX,
            NAV_TREE_SHELL_MAX_WIDTH_PX);
    lastKnownInspectorWidthPx = sanitizeInspectorWidth(
            prefs.getDouble(PREF_WORKSPACE_INSPECTOR_WIDTH_PX, lastKnownInspectorWidthPx));
    showNavigationPane = prefs.getBoolean(PREF_WORKSPACE_NAV_VISIBLE, showNavigationPane);
}

private void restoreInspectorModePreference() {
    InspectorMode restored = parseInspectorModePreference(
            prefs.get(PREF_WORKSPACE_INSPECTOR_MODE, InspectorMode.HIDDEN.name()));
    InspectorMode restoredContentMode = normalizeContentInspectorMode(parseInspectorModePreference(
            prefs.get(PREF_WORKSPACE_INSPECTOR_CONTENT_MODE, lastContentInspectorMode.name())));
    lastContentInspectorMode = restoredContentMode;
    if (homeActive) {
        restored = InspectorMode.HIDDEN;
    }
    if (isContentInspectorMode(restored)) {
        lastContentInspectorMode = restored;
    }
    inspectorMode = restored;
    sidePaneMasterVisible = restored == InspectorMode.DETAILS
            || restored == InspectorMode.PREVIEW
            || restored == InspectorMode.OPERATIONS;
    if (operationsToggle != null) {
        operationsToggle.setSelected(restored == InspectorMode.OPERATIONS);
    }
}

private boolean isContentInspectorMode(InspectorMode mode) {
    return mode == InspectorMode.DETAILS || mode == InspectorMode.PREVIEW;
}

private InspectorMode normalizeContentInspectorMode(InspectorMode mode) {
    return mode == InspectorMode.PREVIEW ? InspectorMode.PREVIEW : InspectorMode.DETAILS;
}

private InspectorMode preferredContentInspectorMode() {
    return normalizeContentInspectorMode(lastContentInspectorMode);
}

private void rememberInspectorContentMode(InspectorMode mode) {
    if (isContentInspectorMode(mode)) {
        lastContentInspectorMode = normalizeContentInspectorMode(mode);
    }
}

private void refreshInspectorPresentationForCurrentContext() {
    if (homeActive) {
        applyWorkspaceInspectorVisibility(false);
        applyInspectorModeNodeVisibility(InspectorMode.HIDDEN);
        syncPaneTogglesFromUiState();
        return;
    }
    if (inspectorMode == InspectorMode.HIDDEN && operationsToggle != null && operationsToggle.isSelected()) {
        inspectorMode = InspectorMode.OPERATIONS;
    }
    if (inspectorMode == InspectorMode.OPERATIONS && (operationsToggle == null || !operationsToggle.isSelected())) {
        inspectorMode = sidePaneMasterVisible ? preferredContentInspectorMode() : InspectorMode.HIDDEN;
    }
    if (isContentInspectorMode(inspectorMode)) {
        rememberInspectorContentMode(inspectorMode);
    }
    boolean show = inspectorMode != InspectorMode.HIDDEN;
    forceInspectorModePresentation(inspectorMode);
    applyWorkspaceInspectorVisibility(show);
    if (show && (inspectorMode == InspectorMode.DETAILS || inspectorMode == InspectorMode.PREVIEW)) {
        updateSelectionDetails(getPrimarySelection());
    }
    if (inspectorHost != null) {
        inspectorHost.requestLayout();
    }
    if (sidePane != null) {
        sidePane.requestLayout();
    }
    if (workspaceShell != null) {
        workspaceShell.requestLayout();
    }
    if (root != null) {
        root.requestLayout();
    }
    syncPaneTogglesFromUiState();
}

private InspectorMode parseInspectorModePreference(String raw) {
    if (raw == null || raw.isBlank()) {
        return InspectorMode.HIDDEN;
    }
    try {
        return InspectorMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
        return InspectorMode.HIDDEN;
    }
}

private void persistWorkspaceShellPreferences() {
    if (prefs == null) {
        return;
    }
    prefs.putDouble(PREF_WORKSPACE_NAV_WIDTH_PX, clamp(lastKnownNavigationPaneShellWidthPx,
            NAV_TREE_SHELL_MIN_WIDTH_PX, NAV_TREE_SHELL_MAX_WIDTH_PX));
    prefs.putBoolean(PREF_WORKSPACE_NAV_VISIBLE, showNavigationPane);
    prefs.putDouble(PREF_WORKSPACE_INSPECTOR_WIDTH_PX, sanitizeInspectorWidth(lastKnownInspectorWidthPx));
    InspectorMode persistedMode = homeActive ? InspectorMode.HIDDEN : inspectorMode;
    prefs.put(PREF_WORKSPACE_INSPECTOR_MODE, persistedMode.name());
    prefs.put(PREF_WORKSPACE_INSPECTOR_CONTENT_MODE, preferredContentInspectorMode().name());
}

    // ---------------------------------------------------------------------
    // Tree + Table
    // ---------------------------------------------------------------------
    private void configureNavigationPaneParity() {
        LogSupport.enter(LOG, "configureNavigationPaneParity");
        if (navigationPaneShell != null) {
            navigationPaneShell.setMinWidth(NAV_TREE_SHELL_MIN_WIDTH_PX);
            navigationPaneShell.setPrefWidth(Math.max(lastKnownNavigationPaneShellWidthPx, NAV_TREE_PREF_WIDTH_PX + (NAV_TREE_SHELL_PADDING_PX * 2.0)));
            navigationPaneShell.setMaxWidth(NAV_TREE_SHELL_MAX_WIDTH_PX);
        }
        if (folderTree != null) {
            if (!folderTree.getStyleClass().contains("explorer-navigation-pane")) {
                folderTree.getStyleClass().add("explorer-navigation-pane");
            }
            folderTree.setMinWidth(NAV_TREE_MIN_WIDTH_PX);
            folderTree.setPrefWidth(Math.max(folderTree.getPrefWidth(), NAV_TREE_PREF_WIDTH_PX));
            folderTree.setFocusTraversable(true);
        }
        configureWorkspaceShellLayout();
    }

    private void configureWorkspaceShellLayout() {
        if (contentPane != null) {
            HBox.setHgrow(contentPane, Priority.ALWAYS);
            contentPane.setMinWidth(0.0);
            contentPane.setMaxWidth(Double.MAX_VALUE);
        }
        if (navigationPaneShell != null) {
            navigationPaneShell.setMinWidth(NAV_TREE_SHELL_MIN_WIDTH_PX);
            navigationPaneShell.setPrefWidth(Math.max(lastKnownNavigationPaneShellWidthPx, NAV_TREE_PREF_WIDTH_PX + (NAV_TREE_SHELL_PADDING_PX * 2.0)));
            navigationPaneShell.setMaxWidth(NAV_TREE_SHELL_MAX_WIDTH_PX);
            navigationPaneShell.widthProperty().addListener((obs, oldWidth, newWidth) -> {
                if (showNavigationPane && newWidth != null && newWidth.doubleValue() >= NAV_TREE_SHELL_MIN_WIDTH_PX) {
                    lastKnownNavigationPaneShellWidthPx = Math.max(NAV_TREE_SHELL_MIN_WIDTH_PX, newWidth.doubleValue());
                }
            });
        }
        if (inspectorHost != null) {
            inspectorHost.setMinWidth(0.0);
            inspectorHost.setPrefWidth(0.0);
            inspectorHost.setMaxWidth(INSPECTOR_HOST_MAX_WIDTH_PX);
            inspectorHost.widthProperty().addListener((obs, oldWidth, newWidth) -> {
                if (newWidth != null && newWidth.doubleValue() >= SIDE_PANE_MIN_WIDTH_PX) {
                    lastKnownInspectorWidthPx = Math.max(SIDE_PANE_MIN_WIDTH_PX, newWidth.doubleValue());
                }
            });
        }
        installWorkspaceShellResizers();
        if (navigationResizer != null) {
            navigationResizer.setVisible(showNavigationPane);
            navigationResizer.setManaged(showNavigationPane);
        }
    }

    private void installWorkspaceShellResizers() {
        if (workspaceShellResizersInstalled) {
            return;
        }
        workspaceShellResizersInstalled = true;
        if (navigationResizer != null) {
            navigationResizer.setCursor(Cursor.H_RESIZE);
            navigationResizer.setOnMousePressed(this::onNavigationResizerPressed);
            navigationResizer.setOnMouseDragged(this::onNavigationResizerDragged);
        }
        if (inspectorResizer != null) {
            inspectorResizer.setCursor(Cursor.H_RESIZE);
            inspectorResizer.setOnMousePressed(this::onInspectorResizerPressed);
            inspectorResizer.setOnMouseDragged(this::onInspectorResizerDragged);
        }
    }

    private void onNavigationResizerPressed(MouseEvent event) {
        if (!showNavigationPane || navigationPaneShell == null) {
            return;
        }
        navigationResizerDragScreenX = event.getScreenX();
        navigationResizerDragStartWidthPx = effectiveWidth(navigationPaneShell, lastKnownNavigationPaneShellWidthPx);
        event.consume();
    }

    private void onNavigationResizerDragged(MouseEvent event) {
        if (!showNavigationPane || navigationPaneShell == null) {
            return;
        }
        double delta = event.getScreenX() - navigationResizerDragScreenX;
        double target = clamp(navigationResizerDragStartWidthPx + delta, NAV_TREE_SHELL_MIN_WIDTH_PX, NAV_TREE_SHELL_MAX_WIDTH_PX);
        applyNavigationPaneShellWidth(target);
        scheduleResponsiveTableViewportLayoutRefresh();
        scheduleResponsiveIconViewportLayoutRefresh();
        event.consume();
    }

    private void onInspectorResizerPressed(MouseEvent event) {
        if (inspectorHost == null || !inspectorHost.isManaged()) {
            return;
        }
        inspectorResizerDragScreenX = event.getScreenX();
        inspectorResizerDragStartWidthPx = effectiveWidth(inspectorHost, lastKnownInspectorWidthPx);
        event.consume();
    }

    private void onInspectorResizerDragged(MouseEvent event) {
        if (inspectorHost == null || !inspectorHost.isManaged()) {
            return;
        }
        double delta = inspectorResizerDragScreenX - event.getScreenX();
        double target = clamp(inspectorResizerDragStartWidthPx + delta, SIDE_PANE_MIN_WIDTH_PX, INSPECTOR_HOST_MAX_WIDTH_PX);
        applyInspectorHostWidth(target);
        scheduleResponsiveTableViewportLayoutRefresh();
        scheduleResponsiveIconViewportLayoutRefresh();
        event.consume();
    }

    private void applyNavigationPaneShellWidth(double widthPx) {
        double clamped = clamp(widthPx, NAV_TREE_SHELL_MIN_WIDTH_PX, NAV_TREE_SHELL_MAX_WIDTH_PX);
        lastKnownNavigationPaneShellWidthPx = clamped;
        persistWorkspaceShellPreferences();
        if (navigationPaneShell != null) {
            navigationPaneShell.setMinWidth(NAV_TREE_SHELL_MIN_WIDTH_PX);
            navigationPaneShell.setPrefWidth(clamped);
            navigationPaneShell.setMaxWidth(NAV_TREE_SHELL_MAX_WIDTH_PX);
        }
        if (folderTree != null) {
            double treeWidth = Math.max(NAV_TREE_MIN_WIDTH_PX, clamped - (NAV_TREE_SHELL_PADDING_PX * 2.0));
            folderTree.setMinWidth(NAV_TREE_MIN_WIDTH_PX);
            folderTree.setPrefWidth(treeWidth);
        }
    }

    private void applyInspectorHostWidth(double widthPx) {
        double clamped = sanitizeInspectorWidth(widthPx);
        lastKnownInspectorWidthPx = clamped;
        persistWorkspaceShellPreferences();
        if (inspectorHost != null) {
            inspectorHost.setMinWidth(SIDE_PANE_MIN_WIDTH_PX);
            inspectorHost.setPrefWidth(clamped);
            inspectorHost.setMaxWidth(INSPECTOR_HOST_MAX_WIDTH_PX);
        }
        if (sidePane != null) {
            sidePane.setMinWidth(SIDE_PANE_MIN_WIDTH_PX);
            sidePane.setPrefWidth(clamped);
            sidePane.setMaxWidth(INSPECTOR_HOST_MAX_WIDTH_PX);
        }
    }

    private void applyWorkspaceInspectorVisibility(boolean show) {
        if (inspectorHost != null) {
            inspectorHost.setVisible(show);
            inspectorHost.setManaged(show);
            if (show) {
                applyInspectorHostWidth(sanitizeInspectorWidth(lastKnownInspectorWidthPx));
            } else {
                inspectorHost.setMinWidth(0.0);
                inspectorHost.setPrefWidth(0.0);
            }
        }
        if (sidePane != null) {
            sidePane.setVisible(show);
            sidePane.setManaged(show);
            if (show) {
                sidePane.setMinWidth(SIDE_PANE_MIN_WIDTH_PX);
                sidePane.setPrefWidth(sanitizeInspectorWidth(lastKnownInspectorWidthPx));
            } else {
                sidePane.setMinWidth(0.0);
                sidePane.setPrefWidth(0.0);
            }
        }
        if (inspectorResizer != null) {
            inspectorResizer.setVisible(show);
            inspectorResizer.setManaged(show);
        }
    }


private void applyInspectorModeNodeVisibility(InspectorMode mode) {
    if (mode == InspectorMode.HIDDEN) {
        if (sidePane != null) {
            sidePane.getChildren().clear();
        }
        setInspectorCardMounted(detailsBox, false);
        setInspectorCardMounted(previewBox, false);
        setInspectorCardMounted(operationsBox, false);
        return;
    }

    ensureInspectorShellCreated();
    VBox activeCard = null;
    if (mode == InspectorMode.DETAILS) {
        ensureDetailsInspectorCardCreated();
        activeCard = detailsBox;
    } else if (mode == InspectorMode.PREVIEW) {
        ensurePreviewInspectorCardCreated();
        activeCard = previewBox;
    } else if (mode == InspectorMode.OPERATIONS) {
        ensureOperationsInspectorCardCreated();
        activeCard = operationsBox;
        scheduleDeferredProgressPaneLoad();
    }

    if (sidePane != null && activeCard != null) {
        if (sidePane.getChildren().size() != 1 || sidePane.getChildren().get(0) != activeCard) {
            sidePane.getChildren().setAll(activeCard);
        }
    }
    setInspectorCardMounted(detailsBox, activeCard == detailsBox);
    setInspectorCardMounted(previewBox, activeCard == previewBox);
    setInspectorCardMounted(operationsBox, activeCard == operationsBox);
    configureSidePaneParity();
}

private void ensureInspectorShellCreated() {
    if (inspectorHost == null || sidePane != null) {
        return;
    }
    VBox shell = new VBox(12.0);
    shell.setAlignment(Pos.TOP_LEFT);
    shell.setMinWidth(0.0);
    shell.setPrefWidth(sanitizeInspectorWidth(lastKnownInspectorWidthPx));
    shell.setMaxWidth(Double.MAX_VALUE);
    shell.setFillWidth(true);
    shell.getStyleClass().addAll("side-pane-surface", "explorer-side-pane");
    shell.setPadding(new Insets(12.0, 12.0, 12.0, 12.0));
    sidePane = shell;
    inspectorHost.getChildren().setAll(shell);
}

private void ensureDetailsInspectorCardCreated() {
    ensureInspectorShellCreated();
    if (detailsBox != null) {
        return;
    }
    VBox card = createInspectorCard("details-pane-card");
    card.getChildren().add(createInspectorCardHeader("\uE946", "Details", "Metadata for the selected item"));
    TextArea area = new TextArea();
    area.setEditable(false);
    area.setWrapText(true);
    area.setPrefRowCount(12);
    area.getStyleClass().addAll("side-pane-text", "details-pane-text");
    VBox.setVgrow(area, Priority.ALWAYS);
    card.getChildren().add(area);
    detailsText = area;
    detailsBox = card;
}

private void ensurePreviewInspectorCardCreated() {
    ensureInspectorShellCreated();
    if (previewBox != null) {
        return;
    }
    VBox card = createInspectorCard("preview-pane-card");
    card.getChildren().add(createInspectorCardHeader("\uE8A5", "Preview", "Quick look at the selected item"));

    StackPane imageShell = new StackPane();
    imageShell.getStyleClass().add("preview-image-shell");
    imageShell.setMinHeight(180.0);
    imageShell.setPrefHeight(220.0);
    imageShell.setMaxHeight(Double.MAX_VALUE);
    imageShell.setMaxWidth(Double.MAX_VALUE);
    VBox.setVgrow(imageShell, Priority.ALWAYS);

    ImageView imageView = new ImageView();
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true);
    imageView.setPickOnBounds(true);
    imageView.fitWidthProperty().bind(imageShell.widthProperty().subtract(24.0));
    imageView.fitHeightProperty().bind(imageShell.heightProperty().subtract(24.0));
    previewImage = imageView;
    imageShell.getChildren().add(imageView);

    TextArea area = new TextArea();
    area.setEditable(false);
    area.setWrapText(true);
    area.setPrefRowCount(8);
    area.getStyleClass().addAll("side-pane-text", "preview-pane-text");
    VBox.setVgrow(area, Priority.ALWAYS);
    previewText = area;

    card.getChildren().addAll(imageShell, area);
    previewBox = card;
}

private void ensureOperationsInspectorCardCreated() {
    ensureInspectorShellCreated();
    if (operationsBox != null) {
        return;
    }
    VBox card = createInspectorCard("operations-pane-card");
    card.getChildren().add(createInspectorCardHeader("\uE945", "Operations", "Transfers and file actions"));
    VBox host = new VBox(0.0);
    host.setMinHeight(0.0);
    host.setPrefHeight(0.0);
    VBox.setVgrow(host, Priority.ALWAYS);
    progressPaneHost = host;
    card.getChildren().add(host);
    operationsBox = card;
}

private VBox createInspectorCard(String... extraStyleClasses) {
    VBox card = new VBox(10.0);
    card.setAlignment(Pos.TOP_LEFT);
    card.setFillWidth(true);
    card.setMaxWidth(Double.MAX_VALUE);
    card.getStyleClass().addAll("fluent-preview-box", "side-pane-card");
    if (extraStyleClasses != null && extraStyleClasses.length > 0) {
        card.getStyleClass().addAll(extraStyleClasses);
    }
    return card;
}

private HBox createInspectorCardHeader(String glyph, String title, String subtitle) {
    HBox header = new HBox(8.0);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("side-pane-card-header");

    Label icon = new Label(glyph);
    icon.getStyleClass().addAll("fluent-icon", "side-pane-card-icon");

    VBox textBox = new VBox(1.0);
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().addAll("fluent-preview-title", "side-pane-card-title");
    Label subtitleLabel = new Label(subtitle);
    subtitleLabel.getStyleClass().add("side-pane-card-subtitle");
    textBox.getChildren().addAll(titleLabel, subtitleLabel);
    HBox.setHgrow(textBox, Priority.ALWAYS);

    header.getChildren().addAll(icon, textBox);
    return header;
}

private void setInspectorCardMounted(Node node, boolean show) {
    if (node == null) {
        return;
    }
    node.setVisible(show);
    node.setManaged(show);
}

    private double effectiveWidth(Region region, double fallback) {
        if (region == null) {
            return fallback;
        }
        return firstPositiveWidth(region.getWidth(), boundsWidth(region.getLayoutBounds()), fallback);
    }

    private void installNavigationPaneGrowthLock() {
        if (navigationPaneGrowthLockInstalled || mainSplitPane == null || navigationPaneShell == null) {
            return;
        }
        navigationPaneGrowthLockInstalled = true;
        navigationPaneShell.setMinWidth(NAV_TREE_SHELL_MIN_WIDTH_PX);
        installNavigationPaneDividerWidthTracking();
        mainSplitPane.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (!showNavigationPane || newWidth == null) {
                return;
            }
            double currentWidth = newWidth.doubleValue();
            if (currentWidth <= 0.0) {
                return;
            }
            double previousWidth = oldWidth == null ? lastKnownMainSplitWidthPx : oldWidth.doubleValue();
            lastKnownMainSplitWidthPx = currentWidth;
            if (previousWidth > 0.0 && Math.abs(currentWidth - previousWidth) < 0.5) {
                return;
            }
            double targetWidth = lastKnownNavigationPaneShellWidthPx > 0.0
                    ? lastKnownNavigationPaneShellWidthPx
                    : (navigationPaneShell.getWidth() > 0.0 ? navigationPaneShell.getWidth() : NAV_TREE_SHELL_MIN_WIDTH_PX);
            scheduleNavigationPaneDividerForShellWidth(targetWidth);
        });
        Platform.runLater(() -> {
            if (navigationPaneShell.getWidth() > 0.0) {
                lastKnownNavigationPaneShellWidthPx = Math.max(NAV_TREE_SHELL_MIN_WIDTH_PX, navigationPaneShell.getWidth());
            }
            if (mainSplitPane.getWidth() > 0.0) {
                lastKnownMainSplitWidthPx = mainSplitPane.getWidth();
            }
            installNavigationPaneDividerWidthTracking();
            if (showNavigationPane) {
                scheduleNavigationPaneDividerForShellWidth(lastKnownNavigationPaneShellWidthPx);
            }
        });
    }

    private void installNavigationPaneDividerWidthTracking() {
        if (navigationPaneDividerTrackingInstalled || mainSplitPane == null || mainSplitPane.getDividers().isEmpty()) {
            return;
        }
        navigationPaneDividerTrackingInstalled = true;
        mainSplitPane.getDividers().get(0).positionProperty().addListener((obs, oldValue, newValue) -> {
            if (!showNavigationPane || navigationPaneDividerProgrammaticChange || navigationPaneShell == null) {
                return;
            }
            Platform.runLater(() -> {
                if (!showNavigationPane || navigationPaneDividerProgrammaticChange || navigationPaneShell == null) {
                    return;
                }
                double shellWidth = navigationPaneShell.getWidth();
                if (shellWidth > 0.0) {
                    lastKnownNavigationPaneShellWidthPx = Math.max(NAV_TREE_SHELL_MIN_WIDTH_PX, shellWidth);
                }
            });
        });
    }

    private void scheduleNavigationPaneDividerForShellWidth(double targetShellWidthPx) {
        if (mainSplitPane == null || mainSplitPane.getDividers().isEmpty()) {
            return;
        }
        pendingNavigationPaneShellWidthPx = Math.max(NAV_TREE_SHELL_MIN_WIDTH_PX, targetShellWidthPx);
        if (navigationPaneDividerAdjustPending) {
            return;
        }
        navigationPaneDividerAdjustPending = true;
        Platform.runLater(() -> {
            navigationPaneDividerAdjustPending = false;
            if (mainSplitPane == null || mainSplitPane.getDividers().isEmpty() || !showNavigationPane) {
                return;
            }
            double totalWidth = mainSplitPane.getWidth();
            if (totalWidth <= 0.0) {
                return;
            }
            double ratio = clamp(pendingNavigationPaneShellWidthPx / totalWidth, 0.0, 0.95);
            try {
                navigationPaneDividerProgrammaticChange = true;
                mainSplitPane.setDividerPositions(ratio);
                lastKnownNavigationPaneShellWidthPx = Math.max(NAV_TREE_SHELL_MIN_WIDTH_PX, pendingNavigationPaneShellWidthPx);
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Unable to preserve navigation pane width during window resize", ex);
            } finally {
                Platform.runLater(() -> navigationPaneDividerProgrammaticChange = false);
            }
        });
    }
/**
 * configureTree.
 *
 */
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
        
        // Enforce navigation tree minimum width while allowing compact shrink on window squeeze.
        if (navigationPaneShell != null) {
            navigationPaneShell.setMinWidth(NAV_TREE_SHELL_MIN_WIDTH_PX);
        }
        folderTree.setMinWidth(NAV_TREE_MIN_WIDTH_PX);
        folderTree.setPrefWidth(Math.max(folderTree.getPrefWidth(), NAV_TREE_PREF_WIDTH_PX));
// Reset per-run counters used for runaway detection.
        TREE_CELL_CREATED.set(0);
        final boolean safeMode = SAFE_MODE;
        final boolean hoverPrefetchEnabled = !safeMode && Boolean.parseBoolean(System.getProperty("fileexplorer.ui.hoverPrefetch", "false"));
        final int treeFixedCellSize = (int) Math.round(Math.max(folderTree.getFixedCellSize(), FOLDER_TREE_ROW_HEIGHT_PX));
        folderTree.setFixedCellSize(treeFixedCellSize);
        // Build the real root off the FX thread to avoid freezing the initial render.
        folderTree.setShowRoot(false);
        // Show a placeholder root immediately (not shown since showRoot=false).
        TreeItem<Path> placeholderRoot = new TreeItem<>(Paths.get("/"));
        folderTree.setRoot(placeholderRoot);
        placeholderRoot.setExpanded(false);
        if (startupTreeSkeletonMarked.compareAndSet(false, true)) {
            StartupTrace.mark("navigation tree skeleton attached");
        }
        executeOnIoExecutor("buildNavigationTreeRoot", () -> {
            TreeItem<Path> root = treeBuildService.buildComputerRoot();
            Platform.runLater(() -> {
                folderTree.setRoot(root);
                folderTree.setShowRoot(false);
                if (startupTreeRootVisibleMarked.compareAndSet(false, true)) {
                    StartupTrace.mark("navigation tree root visible");
                }
            });
        });
folderTree.setEditable(true);
folderTree.setCellFactory(tv -> {
            int created = TREE_CELL_CREATED.incrementAndGet();
            int maxCells = Integer.getInteger("fileexplorer.ui.tree.maxCells", 5000);
            if (created > maxCells) {
                LOG.severe(() -> "TreeCell runaway detected: created=" + created + " max=" + maxCells
                        + " (hint: set -Dfileexplorer.ui.tree.prefHeight=<px> to bound preferred sizing)");
                throw new IllegalStateException("TreeCell runaway detected (created=" + created + ")");
            }
            if (safeMode) {
                return new SimplePathTreeCell(treeFixedCellSize, treeBuildService, this::commitTreeInlineRename);
            }
            return new IconPathTreeCell(treeFixedCellSize, themeService, treeBuildService, this::commitTreeInlineRename);
        });
        folderTree.setOnEditCancel(event -> {
            if (suppressTreeInlineRenameCancelEvent) {
                return;
            }
            Path cancelledPath = null;
            if (event != null && event.getTreeItem() != null) {
                cancelledPath = event.getTreeItem().getValue();
            }
            if (cancelledPath == null) {
                cancelledPath = inlineRenameTreePath;
            }
            if (cancelledPath != null) {
                cancelInlineRenameSession(cancelledPath);
            }
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
            if (suppressTreeSelectionDirectoryLoadOnce) {
                suppressTreeSelectionDirectoryLoadOnce = false;
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
                    if (e.getButton() == MouseButton.SECONDARY) {
                        suppressTreeSelectionDirectoryLoadOnce = true;
                        folderTree.getSelectionModel().select((TreeItem<Path>) item);
                        e.consume();
                        return;
                    }
                    if (e.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
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

    private void updateNavigationButtonsState() {
        boolean canGoBack = !backHistory.isEmpty();
        boolean canGoForward = !forwardHistory.isEmpty();
        boolean canGoUp = currentDirectory != null && currentDirectory.getParent() != null;
        if (backButton != null) {
            backButton.setDisable(!canGoBack);
        }
        if (forwardButton != null) {
            forwardButton.setDisable(!canGoForward);
        }
        if (upButton != null) {
            upButton.setDisable(!canGoUp);
        }
    }
/**
 * configureIconActivation.
 *
 */
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
        if (iconScroll != null) {
            iconScroll.addEventFilter(ScrollEvent.ANY, e -> noteViewportMotion(e.getDeltaY()));
        }
        if (viewHost != null) {
            viewHost.addEventFilter(ScrollEvent.ANY, e -> noteViewportMotion(e.getDeltaY()));
        }
        if (iconScroll != null) {
            iconScroll.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> { scheduleResponsiveIconViewportLayoutRefresh(); scheduleViewportScopedRealizationRefresh(); });
            iconScroll.widthProperty().addListener((obs, oldValue, newValue) -> { scheduleResponsiveIconViewportLayoutRefresh(); scheduleViewportScopedRealizationRefresh(); });
            iconScroll.heightProperty().addListener((obs, oldValue, newValue) -> { scheduleResponsiveIconViewportLayoutRefresh(); scheduleViewportScopedRealizationRefresh(); });
            iconScroll.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> { scheduleResponsiveIconViewportLayoutRefresh(); scheduleViewportScopedRealizationRefresh(); });
        }
        if (viewHost != null) {
            ensureExplorerFileViewSelectionInteractionsInstalled();
            viewHost.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
            viewHost.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
            viewHost.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveIconViewportLayoutRefresh());
            viewHost.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.widthProperty().addListener((obs2, oldValue2, newValue2) -> scheduleResponsiveIconViewportLayoutRefresh());
                    newScene.heightProperty().addListener((obs2, oldValue2, newValue2) -> scheduleResponsiveIconViewportLayoutRefresh());
                    Window window = newScene.getWindow();
                    if (window != null) {
                        wireResponsiveIconWindowListeners(window);
                    }
                    newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> wireResponsiveIconWindowListeners(newWindow));
                }
            });
        }
    }

    private void configureResponsiveTableViewportLayout() {
        if (fileTable == null) {
            return;
        }
        installResponsiveTableViewportBindings();
        fileTable.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
        fileTable.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
        fileTable.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveTableViewportLayoutRefresh());
        if (detailsViewShell != null) {
            detailsViewShell.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            detailsViewShell.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            detailsViewShell.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveTableViewportLayoutRefresh());
        }
        if (viewHost != null) {
            viewHost.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            viewHost.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            viewHost.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveTableViewportLayoutRefresh());
            viewHost.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.widthProperty().addListener((obs2, oldValue2, newValue2) -> scheduleResponsiveTableViewportLayoutRefresh());
                    newScene.heightProperty().addListener((obs2, oldValue2, newValue2) -> scheduleResponsiveTableViewportLayoutRefresh());
                    Window window = newScene.getWindow();
                    if (window != null) {
                        wireResponsiveTableWindowListeners(window);
                    }
                    newScene.windowProperty().addListener((obs2, oldWindow, newWindow) -> wireResponsiveTableWindowListeners(newWindow));
                }
            });
        }
        if (workspaceShell != null) {
            workspaceShell.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            workspaceShell.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            workspaceShell.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveTableViewportLayoutRefresh());
        }
        if (contentPane != null) {
            contentPane.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            contentPane.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            contentPane.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveTableViewportLayoutRefresh());
        }
        if (inspectorHost != null) {
            inspectorHost.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            inspectorHost.visibleProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            inspectorHost.managedProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            inspectorHost.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveTableViewportLayoutRefresh());
        }
        if (sidePane != null) {
            sidePane.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            sidePane.visibleProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            sidePane.managedProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
            sidePane.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveTableViewportLayoutRefresh());
        }
        Platform.runLater(this::scheduleResponsiveTableViewportLayoutRefresh);
    }
/**
 * activateFromTableSelection.
 *
 */
    private void installResponsiveTableViewportBindings() {
        if (viewHost == null || fileTable == null) {
            return;
        }
        if (detailsViewShell != null && !detailsViewShell.prefWidthProperty().isBound()) {
            detailsViewShell.prefWidthProperty().bind(viewHost.widthProperty());
        }
        if (!fileTable.prefWidthProperty().isBound()) {
            fileTable.prefWidthProperty().bind(viewHost.widthProperty());
        }
        if (detailsViewShell != null) {
            detailsViewShell.setMinWidth(0.0);
            detailsViewShell.setMaxWidth(Double.MAX_VALUE);
        }
        fileTable.setMinWidth(0.0);
        fileTable.setMaxWidth(Double.MAX_VALUE);
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
        openPath(selected);
    }

    private void openPrimarySelection() {
        Path selected = getPrimarySelection();
        if (selected != null) {
            openPath(selected);
        }
    }

    private void openSelection() {
        java.util.List<Path> selectedPaths = new java.util.ArrayList<>(getSelectedItems());
        if (selectedPaths.isEmpty()) {
            Path primarySelection = getPrimarySelection();
            if (primarySelection != null) {
                selectedPaths.add(primarySelection);
            }
        }
        if (selectedPaths.isEmpty()) {
            return;
        }
        hideExplorerTransientUi();
        if (selectedPaths.size() == 1) {
            openPath(selectedPaths.get(0));
            return;
        }
        int openedCount = 0;
        for (Path path : selectedPaths) {
            if (path == null) {
                continue;
            }
            try {
                if (isDirectoryPath(path)) {
                    openNewWindow(path);
                } else if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(path.toFile());
                }
                openedCount++;
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Open selection path failed", ex);
            }
        }
        if (openedCount > 0) {
            setStatus("Opened " + openedCount + " item(s).");
        }
    }

    private void openSelectionInNewTab() {
        Path primarySelection = getPrimarySelection();
        if (!isDirectoryPath(primarySelection)) {
            return;
        }
        hideExplorerTransientUi();
        openNewWindow(primarySelection);
        setStatus("Opened in new window (tab fallback): " + directoryDisplayName(primarySelection));
    }

    private boolean isDirectoryPath(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return Files.isDirectory(path);
        } catch (Exception ex) {
            return false;
        }
    }

    private void openPath(Path selected) {
        if (selected == null) {
            return;
        }
        try {
            if (Files.isDirectory(selected)) {
                navigateToFolder(selected, true);
                return;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(selected.toFile());
            }
        } catch (Exception ex) {
            setStatus("Open failed.");
            LOG.log(Level.FINE, "Open path failed", ex);
        }
    }
/**
 * consumeTreeSelectionUserInitiated.
 *
 * @return TODO
 */
    private boolean consumeTreeSelectionUserInitiated() {
        if (treeSelectionUserInitiated) {
            treeSelectionUserInitiated = false;
            return true;
        }
        return false;
    }
/**
 * createStatusCheckIcon.
 *
 * @param color TODO
 * @return TODO
 */
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
     * Phase 4G: Explorer-style navigation context menu and inline rename entry point.
     */
    private void configureTreeContextMenu() {
        if (folderTree == null) {
            return;
        }
        folderTree.setOnContextMenuRequested(ev -> {
            try {
                TreeItem<java.nio.file.Path> sel = folderTree.getSelectionModel().getSelectedItem();
                if (sel == null || sel.getValue() == null) {
                    return;
                }
                Path selectedPath = sel.getValue();
                javafx.scene.control.ContextMenu menu = createExplorerContextMenu("tree");
                javafx.scene.control.MenuItem openItem = createExplorerMenuItem("Open", "", () -> navigateToFolder(selectedPath, true));
                javafx.scene.control.MenuItem expandCollapseItem = createExplorerMenuItem(
                        sel.isExpanded() ? "Collapse" : "Expand",
                        sel.isExpanded() ? "" : "",
                        () -> sel.setExpanded(!sel.isExpanded()));
                expandCollapseItem.setDisable(sel.isLeaf());
                javafx.scene.control.MenuItem refreshItem = createExplorerMenuItem("Refresh", "", () -> {
                    if (sel instanceof com.fileexplorer.service.filesystem.TreeBuildService.LazyLoadingTreeItem lazy) {
                        lazy.invalidate();
                    }
                    if (selectedPath.equals(currentDirectory)) {
                        refresh();
                    }
                });
                javafx.scene.control.MenuItem renameItem = createExplorerMenuItem("Rename", "", () -> beginTreeInlineRename(sel));
                javafx.scene.control.MenuItem propertiesItem = createExplorerMenuItem("Properties", "", () -> openPropertiesForPath(selectedPath));
                renameItem.setDisable(sel.getParent() == null);
                menu.getItems().addAll(openItem, expandCollapseItem, createExplorerSeparator(), refreshItem,
                        createExplorerSeparator(), renameItem, propertiesItem);
                menu.show(folderTree, ev.getScreenX(), ev.getScreenY());
                ev.consume();
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Tree context menu failed", ex);
            }
        });
    }
/**
 * configureTable.
 *
 */
private void configureTable() {
        LogSupport.enter(LOG, "configureTable");
        // Default-enabled guard: cap preferred height/width to prevent runaway layout sizing.
        enforceVirtualizedPrefSize(fileTable, "fileexplorer.ui.table.prefHeight", 720,
                "fileexplorer.ui.table.prefWidth", -1);
        // Stretch columns to the end of the container (last column flexes).
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        // Phase 4L.1: keep a SortedList wrapper, but drive it with a FileItem-aware sort policy
        // so Details header sorting can use real file semantics (numeric size, actual mtime, etc.).
        fileTable.setItems(sortedTableItems);
        configureDetailsSortPolicy();
        fileTable.setEditable(true);
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
                fi -> fileMetadataService.humanReadableSizeForTable(fi.path()),
                fi -> fileMetadataService.lastModifiedLocalString(fi.path())
        );
        colName.setCellValueFactory(param -> {
            FileItem fi = param.getValue();
            Path p = (fi != null) ? fi.path() : null;
            String name = displayNameForTable(p);
            return new ReadOnlyObjectWrapper<>(name);
        });
        colName.setCellFactory(_ -> new ExplorerNameTableCell());
        
// Status column (icon-only): check outline placeholder with hover/selection tint.
if (colStatus != null) {
    colStatus.setSortable(false);
    colStatus.setReorderable(false);
    // Phase 4A.3 tweak: hide Status column by default (can be re-enabled later).
    colStatus.setVisible(false);
    if (fileTable != null) {
        fileTable.getColumns().remove(colStatus);
    }
    final Color normal = Color.web("#6CCB5F");
    final Color hover = Color.web("#86E173");
    final Color selected = Color.web("#FFFFFF");
    colStatus.setCellFactory(_ -> new TableCell<>() {
        private Label icon;
/**
 * syncTint.
 *
 */
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
/**
 * updateItem.
 *
 * @param item TODO
 * @param empty TODO
 */
        protected void updateItem(Node item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                icon = null;
                setMouseTransparent(true);
                return;
            }
            if (icon == null) {
                icon = createStatusCheckIcon(normal);
                tableRowProperty().addListener((obs, oldR, newR) -> syncTint());
            }
            setGraphic(icon);
            setText(null);
            setMouseTransparent(true);
            syncTint();
        }
    });
}
colType.setCellValueFactory(param -> {
            FileItem fi = param.getValue();
            return new ReadOnlyObjectWrapper<>(fi != null ? fi.type() : "");
        });
        colType.setCellFactory(_ -> createExplorerTextTableCell(Pos.CENTER_LEFT));
        colSize.setCellValueFactory(param -> {
            FileItem fi = param.getValue();
            return new ReadOnlyObjectWrapper<>(fi != null ? fi.size() : "");
        });
        // Right align Size
        if (!colSize.getStyleClass().contains("explorer-size-column")) {
            colSize.getStyleClass().add("explorer-size-column");
        }
        colSize.setStyle("-fx-alignment: CENTER-RIGHT;");
        colSize.setCellFactory(_ -> {
            TableCell<FileItem, String> cell = createExplorerTextTableCell(Pos.CENTER_RIGHT);
            if (!cell.getStyleClass().contains("explorer-size-column-cell")) {
                cell.getStyleClass().add("explorer-size-column-cell");
            }
            return cell;
        });
        colModified.setCellValueFactory(param -> {
            FileItem fi = param.getValue();
            return new ReadOnlyObjectWrapper<>(fi != null ? fi.modified() : "");
        });
        colModified.setCellFactory(_ -> createExplorerTextTableCell(Pos.CENTER_LEFT));
        if (fileTable != null && !fileTable.getStyleClass().contains("explorer-details-table")) {
            fileTable.getStyleClass().add("explorer-details-table");
        }
        if (colName != null && !colName.getStyleClass().contains("details-col-name")) {
            colName.getStyleClass().add("details-col-name");
        }
        if (colType != null && !colType.getStyleClass().contains("details-col-type")) {
            colType.getStyleClass().add("details-col-type");
        }
        if (colSize != null && !colSize.getStyleClass().contains("details-col-size")) {
            colSize.getStyleClass().add("details-col-size");
        }
        if (colModified != null && !colModified.getStyleClass().contains("details-col-modified")) {
            colModified.getStyleClass().add("details-col-modified");
        }
        if (colName != null && colName.getPrefWidth() < 375.0) {
            colName.setPrefWidth(375.0);
        }
        if (colType != null && colType.getPrefWidth() < 176.0) {
            colType.setPrefWidth(176.0);
        }
        if (colSize != null && colSize.getPrefWidth() < 108.0) {
            colSize.setPrefWidth(108.0);
        }
        if (colModified != null && colModified.getPrefWidth() < 184.0) {
            colModified.setPrefWidth(184.0);
        }
        if (fileTable != null) {
            fileTable.setFixedCellSize(33.0);
        }
        syncDetailsSortHeaderState();
        fileTable.getSelectionModel().selectedItemProperty().addListener((_, oldSel, newSel) ->
                runWithinExplorerSelectionModelNotification(() -> {
                    Path p = newSel != null ? newSel.path() : null;
                    updateSelectionDetails(p);
                    scheduleSelectionCommandStateRefresh();
                    if (!isExplorerSelectionPresentationTransactionActive()) {
                        if (isExplorerSelectionStabilizationActive()) {
                            maintainExplorerSelectionStabilization();
                        } else {
                            syncIconPresentationSelectedPathsFromTableSelection();
                        }
                    }
                    if (isExplorerSelectionStabilizationActive()) {
                        applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths);
                    } else {
                        refreshVisibleIconTileSelectionState();
                        refreshVisibleDetailsSelectionPresentation();
                    }
                    refreshExplorerMetadataPopupForSelection(newSel);
                    if (p != null) {
                        requestMetadataForFocus(p);
                    }
                    if (inlineRenameTablePath != null && !Objects.equals(inlineRenameTablePath, p)) {
                        Path restorePath = inlineRenameTablePath;
                        clearInlineRenameTargets();
                        Platform.runLater(() -> restoreFocusToTablePath(restorePath));
                    }
                    publishDetailsRefreshContext();
                }));
        fileTable.getSelectionModel().getSelectedItems().addListener((ListChangeListener<FileItem>) change ->
                runWithinExplorerSelectionModelNotification(() -> {
                    scheduleSelectionCommandStateRefresh();
                    if (!isExplorerSelectionPresentationTransactionActive()) {
                        if (isExplorerSelectionStabilizationActive()) {
                            maintainExplorerSelectionStabilization();
                        } else {
                            syncIconPresentationSelectedPathsFromTableSelection();
                        }
                    }
                    if (isExplorerSelectionStabilizationActive()) {
                        applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths);
                    } else {
                        refreshVisibleIconTileSelectionState();
                        refreshVisibleDetailsSelectionPresentation();
                    }
                    publishDetailsRefreshContext();
                }));
        if (fileTable.getFocusModel() != null) {
            fileTable.getFocusModel().focusedItemProperty().addListener((_, __, ___) -> publishDetailsRefreshContext());
        }
        fileTable.comparatorProperty().addListener((_, __, ___) -> publishDetailsRefreshContext());
        fileTable.getSortOrder().addListener((ListChangeListener<TableColumn<FileItem, ?>>) change -> publishDetailsRefreshContext());
        publishDetailsRefreshContext();

        // Phase 4B.2 (lowest CPU): metadata for Size/Modified is lazy. Without this,
        // those columns stay blank until a row is clicked (USER priority). We request
        // metadata for a small "visible-ish" window after idle / scroll settles.
        initVisibleMetadataDebounce();
        // Debounce on scroll.
        fileTable.addEventFilter(javafx.scene.input.ScrollEvent.ANY, ev -> { armVisibleMetadataRequest(); noteViewportMotion(ev.getDeltaY()); });
        // Debounce when items stream in (progressive load updates tableItems).
        tableItems.addListener((javafx.collections.ListChangeListener<FileItem>) c -> { armVisibleMetadataRequest(); scheduleViewportScopedRealizationRefresh(); });
    
        // Activate folders on double-click and Enter (Details/List views use the TableView).
        fileTable.setRowFactory(_ -> createExplorerDetailsTableRow());
        fileTable.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) {
                return;
            }
            Path targetPath = null;
            Node target = event.getPickResult() != null ? event.getPickResult().getIntersectedNode() : null;
            TableRow<?> row = findAncestorTableRow(target);
            if (row instanceof TableRow<?> tableRow && !tableRow.isEmpty() && tableRow.getItem() instanceof FileItem item) {
                targetPath = item.path();
            }
            if (targetPath != null && Files.isDirectory(targetPath)) {
                lastIconActivatedPath = targetPath;
                navigateToFolder(targetPath, true);
                event.consume();
            }
        });
        installStableDetailsHoverTracking();
        
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
            Path sel = getFocusedOrSelectedPath();
            if (sel != null) {
                openPath(sel);
                e.consume();
            }
        }
    });
        // Phase 3.6.0: wire file operations context menu + shortcuts
        // Explorer-like header context menu (column visibility + sizing + Choose Details)
        ensureOptionalDetailsColumns();
        installHeaderDetailsMenu();
        installDetailsHeaderInteractionParity();
        configureFileOperationsUi();
}

    private void configureDetailsSortPolicy() {
        if (fileTable == null) {
            return;
        }
        fileTable.setSortPolicy(table -> {
            advanceDetailsAsyncBindingEpoch();
            sortedTableItems.setComparator(buildExplorerTableComparator(table));
            syncDetailsSortHeaderState();
            return true;
        });
        fileTable.getSortOrder().addListener((ListChangeListener<TableColumn<FileItem, ?>>) change -> syncDetailsSortHeaderState());
        for (TableColumn<FileItem, ?> column : List.of(colName, colType, colSize, colModified)) {
            if (column != null) {
                column.sortTypeProperty().addListener((obs, oldType, newType) -> syncDetailsSortHeaderState());
            }
        }
        Platform.runLater(this::syncDetailsSortHeaderState);
    }

    private Comparator<FileItem> buildExplorerTableComparator(TableView<FileItem> table) {
        if (table == null || table.getSortOrder().isEmpty()) {
            return null;
        }

        Comparator<FileItem> comparator = Comparator.comparingInt(this::detailsDirectoryBucket);
        for (TableColumn<FileItem, ?> column : table.getSortOrder()) {
            Comparator<FileItem> nextComparator = comparatorForDetailsColumn(column);
            if (column.getSortType() == TableColumn.SortType.DESCENDING) {
                nextComparator = nextComparator.reversed();
            }
            comparator = comparator.thenComparing(nextComparator);
        }

        return comparator
                .thenComparing(this::detailsSortNameKey, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item != null && item.path() != null ? item.path().toAbsolutePath().toString() : "",
                        String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<FileItem> comparatorForDetailsColumn(TableColumn<FileItem, ?> column) {
        if (column == colName) {
            return Comparator.comparing(this::detailsSortNameKey, String.CASE_INSENSITIVE_ORDER);
        }
        if (column == colType) {
            return Comparator.comparing(this::detailsSortTypeKey, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(this::detailsSortNameKey, String.CASE_INSENSITIVE_ORDER);
        }
        if (column == colSize) {
            return Comparator.comparingLong(this::detailsSortSizeKey)
                    .thenComparing(this::detailsSortNameKey, String.CASE_INSENSITIVE_ORDER);
        }
        if (column == colModified) {
            return Comparator.comparingLong(this::detailsSortModifiedKey)
                    .thenComparing(this::detailsSortNameKey, String.CASE_INSENSITIVE_ORDER);
        }
        return Comparator.comparing(this::detailsSortNameKey, String.CASE_INSENSITIVE_ORDER);
    }

    private void syncDetailsSortHeaderState() {
        syncDetailsVisibleColumnRoleClasses();
        updateDetailsSortHeaderClasses(colName);
        updateDetailsSortHeaderClasses(colType);
        updateDetailsSortHeaderClasses(colSize);
        updateDetailsSortHeaderClasses(colModified);
    }

    private void updateDetailsSortHeaderClasses(TableColumn<FileItem, ?> column) {
        if (column == null) {
            return;
        }
        ObservableList<String> styleClasses = column.getStyleClass();
        styleClasses.removeAll("details-sorted", "details-sorted-asc", "details-sorted-desc", "details-primary-sort",
                "details-secondary-sort");

        int sortIndex = fileTable != null ? fileTable.getSortOrder().indexOf(column) : -1;
        if (sortIndex < 0) {
            return;
        }
        styleClasses.add("details-sorted");
        styleClasses.add(column.getSortType() == TableColumn.SortType.DESCENDING ? "details-sorted-desc" : "details-sorted-asc");
        styleClasses.add(sortIndex == 0 ? "details-primary-sort" : "details-secondary-sort");
    }

    private void syncDetailsVisibleColumnRoleClasses() {
        if (fileTable == null) {
            return;
        }
        java.util.List<TableColumn<FileItem, ?>> detailColumns = new java.util.ArrayList<>();
        for (TableColumn<FileItem, ?> column : fileTable.getColumns()) {
            if (detailsColKey(column) != null && column.isVisible()) {
                detailColumns.add(column);
            }
        }
        for (TableColumn<FileItem, ?> column : detailColumns) {
            ObservableList<String> styleClasses = column.getStyleClass();
            styleClasses.removeAll("details-first-visible-column", "details-last-visible-column", "details-only-visible-column");
        }
        if (detailColumns.isEmpty()) {
            return;
        }
        detailColumns.get(0).getStyleClass().add("details-first-visible-column");
        detailColumns.get(detailColumns.size() - 1).getStyleClass().add("details-last-visible-column");
        if (detailColumns.size() == 1) {
            detailColumns.get(0).getStyleClass().add("details-only-visible-column");
        }
    }

    private int detailsDirectoryBucket(FileItem item) {
        Path path = item != null ? item.path() : null;
        return isDirectoryForDetailsSort(path) ? 0 : 1;
    }

    private boolean isDirectoryForDetailsSort(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return Files.isDirectory(path);
        } catch (Exception ex) {
            return false;
        }
    }

    private String detailsSortNameKey(FileItem item) {
        Path path = item != null ? item.path() : null;
        String name = path != null ? displayNameForTable(path) : null;
        if (name == null || name.isBlank()) {
            name = item != null ? item.name() : "";
        }
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private String detailsSortTypeKey(FileItem item) {
        String type = item != null ? item.type() : "";
        if ((type == null || type.isBlank()) && item != null && item.path() != null) {
            try {
                type = fileMetadataService.detectFileType(item.path());
            } catch (Exception ignored) {
                type = "";
            }
        }
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }

    private long detailsSortSizeKey(FileItem item) {
        Path path = item != null ? item.path() : null;
        if (path == null || isDirectoryForDetailsSort(path)) {
            return -1L;
        }
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return -1L;
        }
    }

    private long detailsSortModifiedKey(FileItem item) {
        Path path = item != null ? item.path() : null;
        if (path == null) {
            return Long.MIN_VALUE;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ex) {
            return Long.MIN_VALUE;
        }
    }


    private TableRow<FileItem> createExplorerDetailsTableRow() {
        TableRow<FileItem> row = new TableRow<>();
        row.setPickOnBounds(true);
        if (!row.getStyleClass().contains("explorer-details-row")) {
            row.getStyleClass().add("explorer-details-row");
        }

        row.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
            if (ev.isConsumed() || ev.getButton() != MouseButton.PRIMARY || row.isEmpty()) {
                return;
            }
            captureExplorerSelectionSnapshotBeforePrimaryPress();
            if (isInlineRenameFocusGuardActive()) {
                ev.consume();
                return;
            }
            FileItem fi = row.getItem();
            Path path = fi != null ? fi.path() : null;
            if (path == null) {
                return;
            }
            hideExplorerMetadataPopup();
            requestActiveDetailsSurfaceFocus();
            handleDetailsRowPrimaryPress(path, ev);
            ev.consume();
        });

        row.addEventFilter(MouseEvent.MOUSE_RELEASED, ev -> {
            if (ev.getButton() == MouseButton.SECONDARY && !row.isEmpty()) {
                if (showArmedExplorerItemContextMenuOnSecondaryRelease(ev)) {
                    ev.consume();
                }
                return;
            }
            if (ev.getButton() == MouseButton.PRIMARY && !row.isEmpty()) {
                ev.consume();
            }
        });

        row.addEventFilter(MouseEvent.MOUSE_CLICKED, me -> {
            if (me.getButton() != MouseButton.PRIMARY || row.isEmpty()) {
                return;
            }
            if (me.getClickCount() == 2) {
                FileItem fi = row.getItem();
                Path p = (fi != null) ? fi.path() : null;
                lastIconActivatedPath = p;
                if (p != null && Files.isDirectory(p)) {
                    navigateToFolder(p, true);
                }
            }
            me.consume();
        });

        row.itemProperty().addListener((obs, oldItem, newItem) -> {
            updateDetailsRowHoverState(row);
            if (row.getIndex() == detailsHoverRowIndex.get()) {
                if (row.isEmpty() || newItem == null) {
                    clearDetailsHoveredRow();
                } else {
                    refreshExplorerMetadataPopupForDetailsRowIndex(row.getIndex(), () -> buildExplorerItemTooltipText(row.getItem()));
                }
            }
        });
        row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
            if (isEmpty && row.getIndex() == detailsHoverRowIndex.get()) {
                clearDetailsHoveredRow();
            }
            updateDetailsRowHoverState(row);
            if (isEmpty) {
                row.setContextMenu(null);
            }
        });
        row.indexProperty().addListener((obs, oldV, newV) -> {
            if (row.isEmpty()) {
                if (row.getIndex() == detailsHoverRowIndex.get()) {
                    clearDetailsHoveredRow();
                }
            } else if (row.getIndex() == detailsHoverRowIndex.get()) {
                refreshExplorerMetadataPopupForDetailsRowIndex(row.getIndex(), () -> buildExplorerItemTooltipText(row.getItem()));
            }
            updateDetailsRowHoverState(row);
        });
        row.selectedProperty().addListener((obs, oldV, newV) -> updateDetailsRowHoverState(row));
        row.sceneProperty().addListener((obs, oldScene, newScene) -> updateDetailsRowHoverState(row));
        row.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
            if (ev.getButton() != MouseButton.SECONDARY || row.isEmpty()) {
                return;
            }
            FileItem item = row.getItem();
            Path path = item != null ? item.path() : null;
            armExplorerItemContextMenu(path, ev.getScreenX(), ev.getScreenY());
            ev.consume();
        });
        row.setOnContextMenuRequested(ev -> {
            if (shouldSuppressExplorerItemContextMenuRequestedEvent()) {
                ev.consume();
                return;
            }
            if (!row.isEmpty()) {
                FileItem item = row.getItem();
                Path path = item != null ? item.path() : null;
                requestExplorerItemContextMenu(path, ev.getScreenX(), ev.getScreenY());
            }
            ev.consume();
        });
        updateDetailsRowHoverState(row);
        return row;
    }

    private void installStableDetailsHoverTracking() {
        if (fileTable == null || stableDetailsHoverTrackingInstalled) {
            return;
        }
        stableDetailsHoverTrackingInstalled = true;

        detailsHoverRowIndex.addListener((obs, oldValue, newValue) -> updateDetailsHoverRowsForIndexChange(oldValue == null ? -1 : oldValue.intValue(), newValue == null ? -1 : newValue.intValue()));
        fileTable.addEventFilter(MouseEvent.MOUSE_MOVED, this::handleDetailsTableMouseMoved);
        fileTable.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleDetailsTableMouseMoved);
        fileTable.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            clearDetailsHoveredRow();
            hideExplorerMetadataPopup();
        });
        fileTable.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> hideExplorerMetadataPopup());
        fileTable.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> syncVisibleDetailsHoverRows());
        fileTable.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.windowProperty().removeListener(detailsWindowFocusRefreshListener);
                if (oldScene.getWindow() != null) {
                    oldScene.getWindow().focusedProperty().removeListener(detailsWindowFocusPresentationListener);
                }
            }
            if (newScene == null) {
                clearDetailsHoveredRow();
                hideExplorerMetadataPopup();
            } else {
                newScene.windowProperty().addListener(detailsWindowFocusRefreshListener);
                if (newScene.getWindow() != null) {
                    newScene.getWindow().focusedProperty().addListener(detailsWindowFocusPresentationListener);
                }
            }
            syncVisibleDetailsHoverRows();
        });
        if (fileTable.getScene() != null) {
            fileTable.getScene().windowProperty().addListener(detailsWindowFocusRefreshListener);
            if (fileTable.getScene().getWindow() != null) {
                fileTable.getScene().getWindow().focusedProperty().addListener(detailsWindowFocusPresentationListener);
            }
        }
    }

    private final javafx.beans.value.ChangeListener<Boolean> detailsWindowFocusPresentationListener = (obs, wasFocused, isFocused) -> syncVisibleDetailsHoverRows();

    private final javafx.beans.value.ChangeListener<Window> detailsWindowFocusRefreshListener = (obs, oldWindow, newWindow) -> {
        if (oldWindow != null) {
            oldWindow.focusedProperty().removeListener(detailsWindowFocusPresentationListener);
        }
        if (newWindow != null) {
            newWindow.focusedProperty().addListener(detailsWindowFocusPresentationListener);
        }
        syncVisibleDetailsHoverRows();
    };

    private void handleDetailsTableMouseMoved(MouseEvent event) {
        if (fileTable == null || viewMode != ViewMode.DETAILS || shouldFreezeExplorerFileViewHoverPresentation()) {
            clearDetailsHoveredRow();
            hideExplorerMetadataPopup();
            return;
        }
        TableRow<FileItem> row = findDetailsTableRowAt(event.getSceneX(), event.getSceneY());
        if (row == null || row.isEmpty() || row.getItem() == null) {
            clearDetailsHoveredRow();
            hideExplorerMetadataPopup();
            return;
        }
        setDetailsHoveredRow(row);
        armExplorerMetadataPopupForDetailsRow(row.getIndex(), () -> buildExplorerItemTooltipText(row.getItem()), event.getScreenX(), event.getScreenY());
    }

    private void handleDetailsRowTooltipEntered(TableRow<FileItem> row, MouseEvent event) {
        if (row == null || event == null || row.isEmpty() || row.getItem() == null || viewMode != ViewMode.DETAILS) {
            return;
        }
        if (shouldFreezeExplorerFileViewHoverPresentation()) {
            clearDetailsHoveredRow();
            hideExplorerMetadataPopup();
            return;
        }
        setDetailsHoveredRow(row);
        armExplorerMetadataPopupForDetailsRow(row.getIndex(), () -> buildExplorerItemTooltipText(row.getItem()), event.getScreenX(), event.getScreenY());
    }

    private void handleDetailsRowTooltipMoved(TableRow<FileItem> row, MouseEvent event) {
        if (row == null || event == null || row.isEmpty() || row.getItem() == null || viewMode != ViewMode.DETAILS) {
            return;
        }
        if (shouldFreezeExplorerFileViewHoverPresentation()) {
            clearDetailsHoveredRow();
            hideExplorerMetadataPopup();
            return;
        }
        setDetailsHoveredRow(row);
        armExplorerMetadataPopupForDetailsRow(row.getIndex(), () -> buildExplorerItemTooltipText(row.getItem()), event.getScreenX(), event.getScreenY());
    }

    private void handleDetailsRowTooltipExited(TableRow<FileItem> row, MouseEvent event) {
        if (row == null || row.isEmpty() || row.getItem() == null) {
            hideExplorerMetadataPopup();
            return;
        }
        if (detailsHoverRowIndex.get() == row.getIndex()) {
            clearDetailsHoveredRow();
        }
        hideExplorerMetadataPopup();
    }

    @SuppressWarnings("unchecked")
    private TableRow<FileItem> findDetailsTableRowAt(double sceneX, double sceneY) {
        if (fileTable == null || fileTable.getScene() == null) {
            return null;
        }
        fileTable.applyCss();
        fileTable.layout();
        for (Node node : fileTable.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> rawRow) {
                Bounds bounds = rawRow.localToScene(rawRow.getBoundsInLocal());
                if (bounds != null && bounds.contains(sceneX, sceneY) && rawRow.getTableView() == fileTable) {
                    return (TableRow<FileItem>) rawRow;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private TableRow<FileItem> findVisibleDetailsRowByIndex(int targetIndex) {
        if (fileTable == null) {
            return null;
        }
        fileTable.applyCss();
        fileTable.layout();
        for (Node node : fileTable.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> rawRow) {
                if (rawRow.getIndex() == targetIndex && rawRow.getTableView() == fileTable) {
                    return (TableRow<FileItem>) rawRow;
                }
            }
        }
        return null;
    }

    private void updateDetailsHoverRowsForIndexChange(int oldIndex, int newIndex) {
        if (oldIndex == newIndex) {
            updateDetailsHoverRowStateByIndex(newIndex);
            return;
        }
        updateDetailsHoverRowStateByIndex(oldIndex);
        updateDetailsHoverRowStateByIndex(newIndex);
    }

    private void updateDetailsHoverRowStateByIndex(int rowIndex) {
        if (rowIndex < 0) {
            return;
        }
        TableRow<FileItem> row = findVisibleDetailsRowByIndex(rowIndex);
        if (row != null) {
            updateDetailsRowHoverState(row);
        }
    }

    private void syncVisibleDetailsHoverRows() {
        if (fileTable == null) {
            return;
        }
        for (Node node : fileTable.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> rawRow && rawRow.getTableView() == fileTable) {
                @SuppressWarnings("unchecked")
                TableRow<FileItem> row = (TableRow<FileItem>) rawRow;
                updateDetailsRowHoverState(row);
            }
        }
    }

    private void refreshVisibleDetailsSelectionPresentation() {
        if (viewMode != ViewMode.DETAILS || fileTable == null) {
            return;
        }
        if (explorerContextMenuSelectionPresentationHold) {
            maintainExplorerContextMenuSelectionHold();
            Platform.runLater(() -> {
                if (explorerContextMenuSelectionPresentationHold) {
                    maintainExplorerContextMenuSelectionHold();
                }
            });
            return;
        }
        if (isExplorerSelectionStabilizationActive()) {
            applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths);
            Platform.runLater(() -> {
                if (isExplorerSelectionStabilizationActive()) {
                    applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths);
                }
            });
            return;
        }
        syncDetailsPresentationSelectedPathsFromTableSelection();
        syncVisibleDetailsHoverRows();
        Platform.runLater(() -> {
            syncDetailsPresentationSelectedPathsFromTableSelection();
            syncVisibleDetailsHoverRows();
        });
    }

    private void setDetailsHoveredRow(TableRow<FileItem> row) {
        int newIndex = (row == null || row.isEmpty() || row.getItem() == null) ? -1 : row.getIndex();
        if (newIndex >= 0 && detailsHoverRowIndex.get() == newIndex && activeDetailsHoverRow.get() == row) {
            return;
        }
        detailsHoverRowIndex.set(newIndex);
        activeDetailsHoverRow.set(newIndex >= 0 ? row : null);
        if (newIndex < 0) {
            flushDeferredTableRefreshAfterHover();
            return;
        }
        FileItem item = row.getItem();
        scheduleHoverPrefetch(item != null ? item.path() : null);
    }

    private void clearDetailsHoveredRow() {
        detailsHoverRowIndex.set(-1);
        activeDetailsHoverRow.set(null);
        flushDeferredTableRefreshAfterHover();
    }

    private void flushDeferredTableRefreshAfterHover() {
        if (tableRefreshDeferredWhileHover) {
            tableRefreshDeferredWhileHover = false;
            requestCoalescedTableRefreshNow();
        }
    }

    private Popup ensureExplorerMetadataPopup() {
        if (explorerMetadataPopup != null) {
            return explorerMetadataPopup;
        }
        explorerMetadataPopup = new Popup();
        explorerMetadataPopup.setAutoFix(true);
        explorerMetadataPopup.setAutoHide(false);
        explorerMetadataPopup.setHideOnEscape(true);
        explorerMetadataPopup.setConsumeAutoHidingEvents(false);

        explorerMetadataPopupLabel = new Label();
        explorerMetadataPopupLabel.getStyleClass().add("explorer-tooltip-label");
        explorerMetadataPopupLabel.setWrapText(true);
        explorerMetadataPopupLabel.setMaxWidth(440.0);
        explorerMetadataPopupLabel.setTextFill(Color.WHITE);
        explorerMetadataPopupLabel.setMouseTransparent(true);
        explorerMetadataPopupLabel.setStyle("-fx-text-fill: white; -fx-font-family: 'Segoe UI Variable Text'; -fx-font-size: 13px; -fx-font-weight: normal; -fx-line-spacing: 2px;");

        explorerMetadataPopupRoot = new StackPane(explorerMetadataPopupLabel);
        explorerMetadataPopupRoot.getStyleClass().add("explorer-rich-tooltip-popup");
        explorerMetadataPopupRoot.setPickOnBounds(false);
        explorerMetadataPopupRoot.setMouseTransparent(true);
        explorerMetadataPopupRoot.setPadding(new Insets(8, 12, 8, 12));
        explorerMetadataPopupRoot.setMaxWidth(460.0);
        explorerMetadataPopupRoot.setStyle("-fx-background-color: #101010; -fx-background-radius: 8; -fx-border-color: #2f2f2f; -fx-border-width: 1; -fx-border-radius: 8;");

        explorerMetadataPopup.getContent().add(explorerMetadataPopupRoot);
        return explorerMetadataPopup;
    }

    private void armExplorerMetadataPopup(Node anchor, java.util.function.Supplier<String> textSupplier, double screenX, double screenY) {
        if (anchor == null || textSupplier == null) {
            return;
        }
        if (shouldSuppressExplorerMetadataPopup()) {
            hideExplorerMetadataPopup();
            return;
        }
        boolean sameAnchorArmed = explorerMetadataPopupAnchor == anchor && explorerMetadataPopupDetailsRowIndex < 0;
        explorerMetadataPopupAnchor = anchor;
        explorerMetadataPopupDetailsRowIndex = -1;
        explorerMetadataPopupTextSupplier = textSupplier;
        explorerMetadataPopupScreenX = screenX;
        explorerMetadataPopupScreenY = screenY;

        Popup popup = ensureExplorerMetadataPopup();
        if (popup.isShowing()) {
            refreshExplorerMetadataPopupNow();
            return;
        }
        if (sameAnchorArmed && explorerMetadataPopupDelay.getStatus() == Animation.Status.RUNNING) {
            return;
        }
        explorerMetadataPopupDelay.stop();
        explorerMetadataPopupDelay.setOnFinished(_ev -> refreshExplorerMetadataPopupNow());
        explorerMetadataPopupDelay.playFromStart();
    }

    private void armExplorerMetadataPopupForDetailsRow(int rowIndex, java.util.function.Supplier<String> textSupplier, double screenX, double screenY) {
        if (fileTable == null || textSupplier == null || rowIndex < 0) {
            return;
        }
        if (shouldSuppressExplorerMetadataPopup()) {
            hideExplorerMetadataPopup();
            return;
        }
        boolean sameRowArmed = explorerMetadataPopupAnchor == fileTable && explorerMetadataPopupDetailsRowIndex == rowIndex;
        explorerMetadataPopupAnchor = fileTable;
        explorerMetadataPopupDetailsRowIndex = rowIndex;
        explorerMetadataPopupTextSupplier = textSupplier;
        explorerMetadataPopupScreenX = screenX;
        explorerMetadataPopupScreenY = screenY;

        Popup popup = ensureExplorerMetadataPopup();
        if (popup.isShowing()) {
            refreshExplorerMetadataPopupNow();
            return;
        }
        if (sameRowArmed && explorerMetadataPopupDelay.getStatus() == Animation.Status.RUNNING) {
            return;
        }
        explorerMetadataPopupDelay.stop();
        explorerMetadataPopupDelay.setOnFinished(_ev -> refreshExplorerMetadataPopupNow());
        explorerMetadataPopupDelay.playFromStart();
    }

    private void refreshExplorerMetadataPopupForAnchor(Node anchor, java.util.function.Supplier<String> textSupplier) {
        if (anchor == null || textSupplier == null) {
            return;
        }
        if (explorerMetadataPopupAnchor != anchor || explorerMetadataPopupDetailsRowIndex >= 0) {
            return;
        }
        explorerMetadataPopupTextSupplier = textSupplier;
        if (explorerMetadataPopup != null && explorerMetadataPopup.isShowing()) {
            refreshExplorerMetadataPopupNow();
        }
    }

    private void refreshExplorerMetadataPopupForDetailsRowIndex(int rowIndex, java.util.function.Supplier<String> textSupplier) {
        if (rowIndex < 0 || textSupplier == null) {
            return;
        }
        if (explorerMetadataPopupAnchor != fileTable || explorerMetadataPopupDetailsRowIndex != rowIndex) {
            return;
        }
        explorerMetadataPopupTextSupplier = textSupplier;
        if (explorerMetadataPopup != null && explorerMetadataPopup.isShowing()) {
            refreshExplorerMetadataPopupNow();
        }
    }

    private void refreshExplorerMetadataPopupForSelection(FileItem item) {
        if (item == null || explorerMetadataPopup == null || !explorerMetadataPopup.isShowing()) {
            return;
        }
        if (shouldSuppressExplorerMetadataPopup()) {
            hideExplorerMetadataPopup();
            return;
        }
        explorerMetadataPopupTextSupplier = () -> buildExplorerItemTooltipText(item);
        if (viewMode == ViewMode.DETAILS && fileTable != null && fileTable.getSelectionModel() != null) {
            explorerMetadataPopupAnchor = fileTable;
            explorerMetadataPopupDetailsRowIndex = fileTable.getSelectionModel().getSelectedIndex();
        } else {
            explorerMetadataPopupDetailsRowIndex = -1;
        }
        refreshExplorerMetadataPopupNow();
    }


    private void refreshExplorerMetadataPopupNow() {
        if (shouldSuppressExplorerMetadataPopup()) {
            hideExplorerMetadataPopup();
            return;
        }
        Popup popup = ensureExplorerMetadataPopup();
        if (explorerMetadataPopupAnchor == null || explorerMetadataPopupTextSupplier == null) {
            hideExplorerMetadataPopup();
            return;
        }
        if (explorerMetadataPopupAnchor.getScene() == null) {
            hideExplorerMetadataPopup();
            return;
        }
        Window owner = explorerMetadataPopupAnchor.getScene().getWindow();
        Bounds screenBounds = explorerMetadataPopupAnchor.localToScreen(explorerMetadataPopupAnchor.getBoundsInLocal());
        if (explorerMetadataPopupDetailsRowIndex >= 0) {
            TableRow<FileItem> row = findVisibleDetailsRowByIndex(explorerMetadataPopupDetailsRowIndex);
            if (row == null || row.isEmpty() || row.getItem() == null) {
                hideExplorerMetadataPopup();
                return;
            }
            screenBounds = row.localToScreen(row.getBoundsInLocal());
        }
        if (owner == null || screenBounds == null) {
            hideExplorerMetadataPopup();
            return;
        }
        if (explorerMetadataPopupDetailsRowIndex >= 0 && detailsHoverRowIndex.get() != explorerMetadataPopupDetailsRowIndex) {
            hideExplorerMetadataPopup();
            return;
        }
        String text = explorerMetadataPopupTextSupplier.get();
        if (text == null || text.isBlank()) {
            hideExplorerMetadataPopup();
            return;
        }

        boolean popupTextChanged = !Objects.equals(explorerMetadataPopupLastText, text)
                || !Objects.equals(explorerMetadataPopupLabel.getText(), text);
        if (popupTextChanged || !popup.isShowing()) {
            explorerMetadataPopupLabel.setText(text);
            explorerMetadataPopupLastText = text;
            explorerMetadataPopupLabel.applyCss();
            explorerMetadataPopupRoot.applyCss();
            explorerMetadataPopupRoot.autosize();
        }

        double anchorX;
        double anchorY;
        if (explorerMetadataPopupDetailsRowIndex >= 0) {
            anchorX = screenBounds.getMinX() + 20.0;
            anchorY = screenBounds.getMaxY() + 6.0;
        } else {
            anchorX = Double.isNaN(explorerMetadataPopupScreenX) ? screenBounds.getMinX() + 16.0 : explorerMetadataPopupScreenX + 18.0;
            anchorY = Double.isNaN(explorerMetadataPopupScreenY) ? screenBounds.getMinY() + 24.0 : explorerMetadataPopupScreenY + 22.0;
        }

        if (!popup.isShowing()) {
            popup.show(owner, anchorX, anchorY);
        } else {
            popup.setX(anchorX);
            popup.setY(anchorY);
        }
    }

    private boolean isExplorerFileViewContextMenuShowing() {
        return (fileOpsMenu != null && fileOpsMenu.isShowing())
                || (fileViewBackgroundMenu != null && fileViewBackgroundMenu.isShowing());
    }

    private boolean isExplorerFileViewContextMenuPending() {
        if (!explorerFileViewContextMenuPending) {
            return false;
        }
        if (explorerFileViewContextMenuPendingUntilNanos > System.nanoTime()) {
            return true;
        }
        explorerFileViewContextMenuPending = false;
        explorerFileViewContextMenuPendingUntilNanos = 0L;
        return false;
    }

    private void suppressExplorerMetadataPopupForMillis(long millis) {
        if (millis <= 0L) {
            return;
        }
        long candidate = System.nanoTime() + millis * 1_000_000L;
        if (candidate > explorerMetadataPopupSuppressUntilNanos) {
            explorerMetadataPopupSuppressUntilNanos = candidate;
        }
        hideExplorerMetadataPopup();
    }

    private void markExplorerFileViewContextMenuPending() {
        explorerFileViewContextMenuPending = true;
        explorerFileViewContextMenuPendingUntilNanos = System.nanoTime() + 3_000_000_000L;
        suppressExplorerMetadataPopupForMillis(1500L);
    }

    private void clearExplorerFileViewContextMenuPending() {
        explorerFileViewContextMenuPending = false;
        explorerFileViewContextMenuPendingUntilNanos = 0L;
    }

    private boolean isExplorerMetadataPopupTemporarilySuppressed() {
        return explorerMetadataPopupSuppressUntilNanos > System.nanoTime();
    }

    private boolean shouldFreezeExplorerFileViewHoverPresentation() {
        return isExplorerMetadataPopupTemporarilySuppressed()
                || isExplorerFileViewContextMenuShowing()
                || isExplorerFileViewContextMenuPending();
    }

    private boolean shouldSuppressExplorerMetadataPopup() {
        return shouldFreezeExplorerFileViewHoverPresentation();
    }

    private void hideExplorerMetadataPopup() {
        explorerMetadataPopupDelay.stop();
        if (explorerMetadataPopup != null) {
            explorerMetadataPopup.hide();
        }
        explorerMetadataPopupAnchor = null;
        explorerMetadataPopupDetailsRowIndex = -1;
        explorerMetadataPopupTextSupplier = null;
        explorerMetadataPopupScreenX = Double.NaN;
        explorerMetadataPopupScreenY = Double.NaN;
        explorerMetadataPopupLastText = "";
        if (explorerMetadataPopupLabel != null) {
            explorerMetadataPopupLabel.setText("");
        }
    }

    private void installExplorerItemTooltip(Node node, java.util.function.Supplier<String> textSupplier) {
        if (node == null || textSupplier == null) {
            return;
        }
        final String tooltipSupplierKey = "explorer.icon.tooltip.supplier";
        final String tooltipHandlersInstalledKey = "explorer.icon.tooltip.handlers.installed";
        node.getProperties().put(tooltipSupplierKey, textSupplier);

        if (Boolean.TRUE.equals(node.getProperties().get(tooltipHandlersInstalledKey))) {
            return;
        }
        node.getProperties().put(tooltipHandlersInstalledKey, Boolean.TRUE);

        node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            if (event.getButton() == MouseButton.SECONDARY || event.isSecondaryButtonDown() || shouldSuppressExplorerMetadataPopup()) {
                hideExplorerMetadataPopup();
                return;
            }
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<String> supplier = (java.util.function.Supplier<String>) node.getProperties().get(tooltipSupplierKey);
            if (supplier == null) {
                return;
            }
            armExplorerMetadataPopup(node, () -> normalizeExplorerTooltipText(node, supplier.get()), event.getScreenX(), event.getScreenY());
        });
        node.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            if (event.getButton() == MouseButton.SECONDARY || event.isSecondaryButtonDown() || shouldSuppressExplorerMetadataPopup()) {
                hideExplorerMetadataPopup();
                return;
            }
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<String> supplier = (java.util.function.Supplier<String>) node.getProperties().get(tooltipSupplierKey);
            if (supplier == null) {
                return;
            }
            if (explorerMetadataPopupAnchor == node && explorerMetadataPopupDetailsRowIndex < 0
                    && explorerMetadataPopup != null && explorerMetadataPopup.isShowing()) {
                explorerMetadataPopupTextSupplier = () -> normalizeExplorerTooltipText(node, supplier.get());
                explorerMetadataPopupScreenX = event.getScreenX();
                explorerMetadataPopupScreenY = event.getScreenY();
                refreshExplorerMetadataPopupNow();
                return;
            }
            armExplorerMetadataPopup(node, () -> normalizeExplorerTooltipText(node, supplier.get()), event.getScreenX(), event.getScreenY());
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            if (explorerMetadataPopupAnchor == node && explorerMetadataPopupDetailsRowIndex < 0) {
                hideExplorerMetadataPopup();
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY || event.getButton() == MouseButton.SECONDARY) {
                hideExplorerMetadataPopup();
            }
        });
        node.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null && explorerMetadataPopupAnchor == node && explorerMetadataPopupDetailsRowIndex < 0) {
                hideExplorerMetadataPopup();
            }
        });
    }

    private String normalizeExplorerTooltipText(Node node, String text) {
        String resolved = text == null ? "" : text.trim();
        if (!resolved.isBlank()) {
            return resolved;
        }
        if (node != null) {
            Object taggedPath = node.getProperties().get(EXPLORER_ICON_TILE_PATH_KEY);
            if (taggedPath instanceof Path path) {
                String fallback = buildExplorerItemTooltipText(path);
                if (fallback != null && !fallback.isBlank()) {
                    return fallback.trim();
                }
            }
            String accessibleText = node.getAccessibleText();
            if (accessibleText != null && !accessibleText.isBlank()) {
                return accessibleText.trim();
            }
        }
        return "Item";
    }

    private void clearExplorerMetadataTextCache() {
        explorerMetadataTextCache.clear();
    }

    private void invalidateExplorerMetadataTextCache(Path path) {
        if (path == null) {
            return;
        }
        explorerMetadataTextCache.remove(path);
    }

    private String cachedExplorerMetadataText(Path path, java.util.function.Supplier<String> uncachedBuilder) {
        if (uncachedBuilder == null) {
            return "";
        }
        if (path == null) {
            String uncached = uncachedBuilder.get();
            return uncached == null ? "" : uncached;
        }
        String cached = explorerMetadataTextCache.get(path);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String built = uncachedBuilder.get();
        if (built == null) {
            built = "";
        }
        if (!built.isBlank()) {
            explorerMetadataTextCache.put(path, built);
        }
        return built;
    }

    private String buildExplorerItemTooltipTextUncached(FileItem item) {
        if (item == null) {
            return "";
        }
        Path path = item.path();
        String name = firstNonBlank(item.name(), path != null ? displayNameForTable(path) : null, "Item");
        String type = firstNonBlank(item.type(), path != null ? typeForTable(path) : null, "");
        String size = firstNonBlank(item.size(), path != null ? sizeForTable(path) : null, "—");
        String modified = firstNonBlank(item.modified(), path != null ? modifiedForTable(path) : null, "—");
        boolean directory = path != null && Files.isDirectory(path);
        StringBuilder tooltip = new StringBuilder(name);
        if (!directory && type != null && !type.isBlank() && !"—".equals(type)) {
            tooltip.append("\nType: ").append(type);
        }
        if (!directory && size != null && !size.isBlank() && !"—".equals(size)) {
            tooltip.append("\nSize: ").append(size);
        }
        tooltip.append("\nDate modified: ").append(modified);
        return tooltip.toString();
    }

    private String buildExplorerItemTooltipTextUncached(Path path) {
        if (path == null) {
            return "";
        }
        String name = firstNonBlank(displayNameForTable(path), path.getFileName() != null ? path.getFileName().toString() : path.toString(), "Item");
        String type = firstNonBlank(typeForTable(path), "");
        String size = firstNonBlank(sizeForTable(path), "—");
        String modified = firstNonBlank(modifiedForTable(path), "—");
        boolean directory = Files.isDirectory(path);
        StringBuilder tooltip = new StringBuilder(name);
        if (!directory && type != null && !type.isBlank() && !"—".equals(type)) {
            tooltip.append("\nType: ").append(type);
        }
        if (!directory && size != null && !size.isBlank() && !"—".equals(size)) {
            tooltip.append("\nSize: ").append(size);
        }
        tooltip.append("\nDate modified: ").append(modified);
        return tooltip.toString();
    }

    private String detailsRowStyleForState(int rowIndex, boolean darkTheme, boolean focusedWindow, boolean selected, boolean hovered) {
        String key = (darkTheme ? "D" : "L")
                + (focusedWindow ? "F" : "B")
                + (selected ? "S" : "N")
                + (hovered ? "H" : "N")
                + (((rowIndex & 1) == 1) ? "O" : "E");
        return detailsRowStyleTemplateCache.computeIfAbsent(key, _k -> {
            String innerFill = selected
                    ? detailsSelectionFill(darkTheme, focusedWindow, hovered)
                    : (hovered ? detailsHoverFill(darkTheme) : detailsBaseFillForRow(rowIndex, darkTheme));
            String border = selected || hovered
                    ? detailsSelectionBorder(darkTheme, focusedWindow, selected)
                    : "transparent";
            String focusRing = detailsSelectionFocusRing(darkTheme, focusedWindow, selected);
            String focusRingWidth = (selected && focusedWindow) ? "1" : "0";
            return String.join(" ",
                    "-fx-background-insets: 0, 1 8 1 8;",
                    "-fx-background-radius: 0, 0;",
                    "-fx-border-insets: 0, 1 8 1 8, 1 8 1 8;",
                    "-fx-border-radius: 0, 0, 0;",
                    "-fx-border-width: 0, 1, " + focusRingWidth + ";",
                    "-fx-background-color: transparent, " + innerFill + ";",
                    "-fx-border-color: transparent, " + border + ", " + focusRing + ";");
        });
    }

    private String buildExplorerItemTooltipText(FileItem item) {
        if (item == null) {
            return "";
        }
        Path path = item.path();
        return cachedExplorerMetadataText(path, () -> buildExplorerItemTooltipTextUncached(item));
    }

    private String buildExplorerItemTooltipText(Path path) {
        if (path == null) {
            return "";
        }
        return cachedExplorerMetadataText(path, () -> buildExplorerItemTooltipTextUncached(path));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void updateDetailsHoverRowIndex(MouseEvent event) {
        if (fileTable == null || event == null) {
            detailsHoverRowIndex.set(-1);
            return;
        }
        TableRow<FileItem> row = findDetailsTableRowAt(event.getSceneX(), event.getSceneY());
        if (row == null || row.isEmpty() || row.getTableView() != fileTable) {
            detailsHoverRowIndex.set(-1);
            return;
        }
        detailsHoverRowIndex.set(row.getIndex());
    }

    private TableRow<?> findAncestorTableRow(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof TableRow<?> tableRow) {
                return tableRow;
            }
            current = current.getParent();
        }
        return null;
    }

    private Path findExplorerDetailsRowPath(Node node) {
        TableRow<?> row = findAncestorTableRow(node);
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object item = row.getItem();
        if (item instanceof FileItem fileItem) {
            return fileItem.path();
        }
        return null;
    }

    private Path resolveExplorerItemContextMenuPath(Node target) {
        Path iconPath = findExplorerIconTilePath(target);
        if (iconPath != null) {
            return iconPath;
        }
        Path virtualIconPath = resolveExplorerVirtualIconCellContextMenuPath(target);
        if (virtualIconPath != null) {
            return virtualIconPath;
        }
        return findExplorerDetailsRowPath(target);
    }

    private Path resolveExplorerVirtualIconCellContextMenuPath(Node target) {
        if (!isIconMode(viewMode) || target == null) {
            return null;
        }
        for (Node current = target; current != null; current = current.getParent()) {
            if (current instanceof ListCell<?> listCell) {
                Object item = listCell.getItem();
                if (item instanceof Path path) {
                    return path;
                }
                if (item instanceof List<?> row) {
                    if (row.size() == 1 && row.get(0) instanceof Path path) {
                        return path;
                    }
                }
            }
        }
        return null;
    }

    private boolean handleExplorerItemContextMenuRequest(Node target, double screenX, double screenY) {
        Path path = resolveExplorerItemContextMenuPath(target);
        if (path == null) {
            path = resolveArmedExplorerItemContextMenuPath(screenX, screenY);
        }
        if (path == null) {
            return false;
        }
        requestExplorerItemContextMenu(path, screenX, screenY);
        return true;
    }

    private boolean isWindowFocusedForDetailsPaint() {
        if (fileTable == null || fileTable.getScene() == null || fileTable.getScene().getWindow() == null) {
            return true;
        }
        return fileTable.getScene().getWindow().isFocused() && fileTable.isFocused();
    }

    private String detailsBaseFillForRow(int index, boolean darkTheme) {
        boolean odd = (index & 1) == 1;
        if (darkTheme) {
            return odd ? "rgba(20,24,31,0.995)" : "rgba(18,22,29,0.995)";
        }
        return odd ? "#fbfbfb" : "#191919";
    }

    private String detailsHoverFill(boolean darkTheme) {
        return "rgba(80,80,80,0.50)";
    }

    private String detailsSelectionFill(boolean darkTheme, boolean focusedWindow, boolean hovered) {
        return focusedWindow ? "rgba(80,80,80,0.50)" : "rgba(80,80,80,0.38)";
    }

    private String detailsSelectionBorder(boolean darkTheme, boolean focusedWindow, boolean selected) {
        if (!selected) {
            return "#505050";
        }
        return focusedWindow ? "#c3c3c3" : "#8e8e8e";
    }

    private String detailsSelectionFocusRing(boolean darkTheme, boolean focusedWindow, boolean selected) {
        if (!selected || !focusedWindow) {
            return "transparent";
        }
        return "rgba(195,195,195,0.22)";
    }

    private void updateDetailsRowHoverState(TableRow<FileItem> row) {
        if (row == null) {
            return;
        }
        boolean active = !shouldFreezeExplorerFileViewHoverPresentation()
                && !row.isEmpty()
                && row.getItem() != null
                && row.getIndex() >= 0
                && row.getIndex() == detailsHoverRowIndex.get();
        row.pseudoClassStateChanged(PSEUDO_EXPLORER_HOVER, active);
        Object cachedStyle = row.getProperties().get(PROP_DETAILS_ROW_STYLE_CACHE);
        if (row.isEmpty() || row.getItem() == null || row.getIndex() < 0 || viewMode != ViewMode.DETAILS) {
            row.pseudoClassStateChanged(PSEUDO_EXPLORER_SELECTED, false);
            if (!Objects.equals(cachedStyle, "")) {
                row.setStyle("");
                row.getProperties().put(PROP_DETAILS_ROW_STYLE_CACHE, "");
            }
            return;
        }
        boolean darkTheme = themeService != null && themeService.isDarkPreferred();
        Path rowPath = row.getItem() != null ? row.getItem().path() : null;
        boolean selected = row.isSelected() || (rowPath != null && detailsPresentationSelectedPaths.contains(rowPath));
        row.pseudoClassStateChanged(PSEUDO_EXPLORER_SELECTED, selected);
        boolean focusedWindow = isWindowFocusedForDetailsPaint();
        String style = detailsRowStyleForState(row.getIndex(), darkTheme, focusedWindow, selected, active);
        if (!Objects.equals(cachedStyle, style)) {
            row.setStyle(style);
            row.getProperties().put(PROP_DETAILS_ROW_STYLE_CACHE, style);
        }
        if (active) {
            FileItem fi = row.getItem();
            scheduleHoverPrefetch(fi != null ? fi.path() : null);
        }
    }

    private final class ExplorerNameTableCell extends TableCell<FileItem, String> {
        private final HBox box = new HBox(10.0);
        private final ImageView iconView = new ImageView();
        private final Label textLabel = new Label();
        private final TextField renameField = new TextField();
        private final InvalidationListener rowStateSync = obs -> syncTextFill();
        private final javafx.beans.value.ChangeListener<FileItem> rowItemListener = (obs, oldItem, newItem) -> {
            if (newItem == null) {
                sanitizeCell();
                return;
            }
            if (isEmpty()) {
                return;
            }
            String displayText = resolveDisplayText(newItem, getItem());
            if (displayText == null) {
                sanitizeCell();
                return;
            }
            rebindForCurrentRow(displayText);
        };
        private boolean suppressFocusCommit;
        private String lastIdentity;
        private Path lastPath;
        private Path lastDirectoryScope;
        private FileItem lastRowItem;
        private String lastDisplayText;
        private boolean lastFolder;
        private boolean thumbnailPublished;
        private java.util.concurrent.CompletableFuture<Image> pendingIcon;
        private long bindingStamp;
        private long lastDetailsAsyncEpoch = -1L;
        private TableRow<FileItem> observedRow;

        private ExplorerNameTableCell() {
            getStyleClass().add("explorer-name-column-cell");
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(Insets.EMPTY);
            iconView.setPreserveRatio(true);
            iconView.setSmooth(true);
            textLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            textLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textLabel, Priority.ALWAYS);
            renameField.getStyleClass().add("explorer-inline-rename-field");
            box.setMouseTransparent(true);
            textLabel.setMouseTransparent(true);
            iconView.setMouseTransparent(true);
            HBox.setHgrow(renameField, Priority.ALWAYS);
            renameField.setOnAction(e -> commitInlineRename());
            renameField.focusedProperty().addListener((obs, oldV, newV) -> {
                if (!newV && isEditingTarget() && !suppressFocusCommit) {
                    if (isInlineRenameFocusGuardActive()) {
                        Platform.runLater(() -> {
                            if (isEditingTarget() && renameField.getScene() != null) {
                                renameField.requestFocus();
                                applyInlineRenameSelection(renameField, resolveEditingPath(), shouldSelectAllInlineRenameText(resolveEditingPath()));
                            }
                        });
                        return;
                    }
                    commitInlineRename();
                }
            });
            renameField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                captureExplicitFullNameEditIntent(resolveEditingPath(), renameField, e);
                if (e.getCode() == KeyCode.ESCAPE) {
                    suppressFocusCommit = true;
                    cancelInlineRename();
                    e.consume();
                }
            });
            renameField.addEventFilter(KeyEvent.KEY_TYPED, e -> captureExplicitFullNameEditIntent(resolveEditingPath(), renameField, e.getCharacter()));
            tableRowProperty().addListener((obs, oldR, newR) -> {
                detachRowListeners(oldR);
                attachRowListeners(newR);
                syncTextFill();
                if (newR == null) {
                    sanitizeCell();
                } else if (!isEmpty()) {
                    String displayText = resolveDisplayText(newR.getItem(), getItem());
                    if (displayText == null) {
                        sanitizeCell();
                    } else {
                        rebindForCurrentRow(displayText);
                    }
                }
            });
        }

        private void attachRowListeners(TableRow<FileItem> row) {
            observedRow = row;
            if (row == null) {
                return;
            }
            row.selectedProperty().addListener(rowStateSync);
            row.itemProperty().addListener(rowItemListener);
        }

        private void detachRowListeners(TableRow<FileItem> row) {
            if (row == null) {
                return;
            }
            row.selectedProperty().removeListener(rowStateSync);
            row.itemProperty().removeListener(rowItemListener);
            if (observedRow == row) {
                observedRow = null;
            }
        }

        private Path resolveEditingPath() {
            FileItem fi = currentRowItem();
            return fi != null ? fi.path() : null;
        }

        private boolean isEditingTarget() {
            Path p = resolveEditingPath();
            return p != null && p.equals(inlineRenameTablePath);
        }

        private VisibleThumbnailManager thumbManager() {
            if (fileTable == null || context == null) {
                return null;
            }
            return TableViewSupport.visibleThumbnailManager(fileTable, context);
        }

        private FileItem currentRowItem() {
            TableRow<FileItem> row = getTableRow();
            return row == null ? null : row.getItem();
        }

        private void syncTextFill() {
            textLabel.setTextFill(resolveExplorerTableTextFill(getTableRow()));
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                sanitizeCell();
                return;
            }
            rebindForCurrentRow(item);
        }

        private void sanitizeCell() {
            cancelPendingWork();
            bindingStamp++;
            lastIdentity = null;
            lastPath = null;
            lastDirectoryScope = null;
            lastRowItem = null;
            lastDisplayText = null;
            lastFolder = false;
            thumbnailPublished = false;
            lastDetailsAsyncEpoch = -1L;
            iconView.setImage(null);
            textLabel.setText(null);
            renameField.clear();
            setText(null);
            setGraphic(null);
            setMouseTransparent(true);
        }

        private void cancelPendingWork() {
            if (pendingIcon != null) {
                pendingIcon.cancel(false);
                pendingIcon = null;
            }
            VisibleThumbnailManager thumbMgr = thumbManager();
            if (thumbMgr != null) {
                thumbMgr.unregister(this);
            }
        }

        private void rebindForCurrentRow(String displayText) {
            FileItem fi = currentRowItem();
            if (fi == null) {
                sanitizeCell();
                return;
            }

            Path p = fi.path();
            boolean dark = themeService != null && themeService.isDarkPreferred();
            boolean isFolder = isFolder(fi, p);
            int iconPx = (int) Math.round(clamp(uiFontSizePx + 4.0, 16.0, 24.0));
            String identity = resolveDetailsCellIdentity(fi, p, isFolder);
            Path bindingDirectoryScope = currentVisibleDirectoryScope();
            long bindingEpoch = currentDetailsAsyncBindingEpoch();
            iconView.setFitWidth(iconPx);
            iconView.setFitHeight(iconPx);

            if (isEquivalentBinding(fi, p, identity, displayText, isFolder, bindingDirectoryScope, bindingEpoch)) {
                if (iconView.getImage() == null) {
                    iconView.setImage(resolveDetailsPlaceholderImage(p, dark, identity, isFolder, iconPx));
                }
                showBoundContent(p, displayText);
                return;
            }

            cancelPendingWork();
            bindingStamp++;

            final long capturedStamp = bindingStamp;
            final long capturedEpoch = bindingEpoch;
            final FileItem boundItem = fi;

            lastIdentity = identity;
            lastPath = p;
            lastDirectoryScope = bindingDirectoryScope;
            lastRowItem = boundItem;
            lastDisplayText = displayText;
            lastFolder = isFolder;
            lastDetailsAsyncEpoch = capturedEpoch;
            thumbnailPublished = false;

            iconView.setFitWidth(iconPx);
            iconView.setFitHeight(iconPx);
            iconView.setImage(resolveDetailsPlaceholderImage(p, dark, identity, isFolder, iconPx));
            if (isEditingTarget()) {
                suppressFocusCommit = false;
                renameField.setText(resolveInlineRenameInitialText(p, displayText));
            }
            showBoundContent(p, displayText);

            pendingIcon = AsyncIconService.getInstance().request(
                    identity,
                    dark,
                    iconPx,
                    AsyncIconService.RequestPriority.VISIBLE);
            pendingIcon.thenAccept(img -> Platform.runLater(() -> {
                if (!isCurrentBinding(capturedStamp, capturedEpoch, boundItem, p, identity, displayText, isFolder, bindingDirectoryScope)) {
                    return;
                }
                if (img == null || thumbnailPublished) {
                    return;
                }
                iconView.setImage(img);
            }));

            VisibleThumbnailManager thumbMgr = thumbManager();
            if (thumbMgr != null && p != null && !isFolder && ImageSupport.isThumbCandidate(p)) {
                thumbMgr.register(this, p, iconPx, identity, img -> {
                    if (!isCurrentBinding(capturedStamp, capturedEpoch, boundItem, p, identity, displayText, false, bindingDirectoryScope)) {
                        return;
                    }
                    if (img == null) {
                        return;
                    }
                    thumbnailPublished = true;
                    iconView.setImage(img);
                });
            } else if (thumbMgr != null) {
                thumbMgr.unregister(this);
            }
        }

        private boolean isEquivalentBinding(FileItem rowItem,
                                            Path path,
                                            String identity,
                                            String displayText,
                                            boolean isFolder,
                                            Path directoryScope,
                                            long bindingEpoch) {
            return lastRowItem == rowItem
                    && Objects.equals(lastPath, path)
                    && Objects.equals(lastIdentity, identity)
                    && Objects.equals(lastDisplayText, displayText)
                    && lastFolder == isFolder
                    && Objects.equals(lastDirectoryScope, directoryScope)
                    && lastDetailsAsyncEpoch == bindingEpoch;
        }

        private boolean isCurrentBinding(long capturedStamp,
                                         long capturedEpoch,
                                         FileItem boundItem,
                                         Path path,
                                         String identity,
                                         String displayText,
                                         boolean isFolder,
                                         Path directoryScope) {
            if (capturedStamp != bindingStamp) {
                return false;
            }
            if (capturedEpoch != currentDetailsAsyncBindingEpoch()) {
                return false;
            }
            if (lastDetailsAsyncEpoch != capturedEpoch) {
                return false;
            }
            if (observedRow != null && getTableRow() != observedRow) {
                return false;
            }
            if (lastRowItem != boundItem) {
                return false;
            }
            if (!Objects.equals(lastDirectoryScope, directoryScope)) {
                return false;
            }
            if (!Objects.equals(lastPath, path)) {
                return false;
            }
            if (!Objects.equals(lastIdentity, identity)) {
                return false;
            }
            if (!Objects.equals(lastDisplayText, displayText)) {
                return false;
            }
            if (lastFolder != isFolder) {
                return false;
            }
            Path activeDirectoryScope = currentVisibleDirectoryScope();
            if (!Objects.equals(activeDirectoryScope, directoryScope)) {
                return false;
            }
            if (!isPathWithinDirectoryScope(path, directoryScope)) {
                return false;
            }

            FileItem current = currentRowItem();
            if (current == null || current != boundItem) {
                return false;
            }
            if (!Objects.equals(current.path(), path)) {
                return false;
            }
            if (!isPathWithinDirectoryScope(current.path(), directoryScope)) {
                return false;
            }

            boolean currentIsFolder = isFolder(current, path);
            if (currentIsFolder != isFolder) {
                return false;
            }
            String currentIdentity = resolveDetailsCellIdentity(current, current.path(), currentIsFolder);
            if (!Objects.equals(currentIdentity, identity)) {
                return false;
            }
            String currentDisplayText = resolveDisplayText(current, getItem());
            if (!Objects.equals(currentDisplayText, displayText)) {
                return false;
            }

            TableRow<FileItem> row = getTableRow();
            return row != null && row.getItem() == current && !isEmpty();
        }

        private void showBoundContent(Path path, String displayText) {
            syncTextFill();
            setText(null);
            if (isEditingTarget()) {
                box.getChildren().setAll(iconView, renameField);
                setGraphic(box);
                setMouseTransparent(false);
                Platform.runLater(() -> {
                    renameField.requestFocus();
                    applyInlineRenameSelection(renameField, path, shouldSelectAllInlineRenameText(path));
                });
            } else {
                textLabel.setText(displayText);
                box.getChildren().setAll(iconView, textLabel);
                setGraphic(box);
                setMouseTransparent(true);
            }
        }

        private String resolveDisplayText(FileItem item, String fallback) {
            if (item != null && item.name() != null) {
                return item.name();
            }
            return fallback;
        }

        private boolean isFolder(FileItem item, Path path) {
            if (item != null && "Folder".equalsIgnoreCase(Objects.requireNonNullElse(item.type(), ""))) {
                return true;
            }
            try {
                return path != null && Files.isDirectory(path);
            } catch (Exception ignored) {
                return false;
            }
        }

        private String resolveDetailsCellIdentity(FileItem item, Path path, boolean isFolder) {
            try {
                if (fileMetadataService != null && path != null) {
                    String identity = fileMetadataService.iconIdentity(path);
                    if (identity != null && !identity.isBlank()) {
                        return identity;
                    }
                }
            } catch (Exception ignored) {
            }
            return "type:" + (isFolder ? IconLoader.IconType.FOLDER.name() : IconLoader.IconType.FILE.name());
        }

        private Image resolveDetailsPlaceholderImage(Path path, boolean dark, String identity, boolean isFolder, int iconPx) {
            try {
                return IconLoader.loadForIdentity(identity, dark, iconPx);
            } catch (Exception ignored) {
            }
            try {
                return IconLoader.placeholderForPath(path, dark, iconPx);
            } catch (Exception ignored) {
            }
            return IconLoader.load(isFolder ? IconLoader.IconType.FOLDER : IconLoader.IconType.FILE, dark, iconPx);
        }

        private void commitInlineRename() {
            FileItem fi = currentRowItem();
            Path p = fi != null ? fi.path() : null;
            suppressFocusCommit = false;
            MainController.this.commitInlineRename(p, renameField.getText());
        }

        private void cancelInlineRename() {
            suppressFocusCommit = false;
            Path p = inlineRenameTablePath;
            clearPendingInlineRenameDraft();
            clearInlineRenameTargets();
            Platform.runLater(() -> restoreFocusToTablePath(p));
        }
    }

    private TableCell<FileItem, String> createExplorerTextTableCell(Pos alignment) {
        return new TableCell<>() {
            private final InvalidationListener rowStateSync = obs -> syncTextFill();
            {
                setMouseTransparent(true);
            }
            {
                tableRowProperty().addListener((obs, oldR, newR) -> {
                    if (oldR != null) {
                        oldR.selectedProperty().removeListener(rowStateSync);
                    }
                    if (newR != null) {
                        newR.selectedProperty().addListener(rowStateSync);
                    }
                    syncTextFill();
                });
            }
            private void syncTextFill() {
                setTextFill(resolveExplorerTableTextFill(getTableRow()));
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                LogSupport.enter(LOG, "updateItem3");
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setGraphic(null);
                setContentDisplay(ContentDisplay.TEXT_ONLY);
                setAlignment(alignment);
                setTextAlignment(alignment == Pos.CENTER_RIGHT ? javafx.scene.text.TextAlignment.RIGHT : javafx.scene.text.TextAlignment.LEFT);
                syncTextFill();
            }
        };
    }

    private Color resolveExplorerTableTextFill(TableRow<FileItem> row) {
        if (row != null && row.isSelected()) {
            return Color.WHITE;
        }
        if (themeService != null && themeService.isDarkPreferred()) {
            return Color.web("#f5f5f5");
        }
        return Color.web("#202020");
    }

    private void tagIconTile(Node tile, Path path, String... styleClasses) {
        if (tile == null) {
            return;
        }
        if (path == null) {
            tile.getProperties().remove(EXPLORER_ICON_TILE_PATH_KEY);
        } else {
            tile.getProperties().put(EXPLORER_ICON_TILE_PATH_KEY, path);
        }
        installStableExplorerIconTileHover(tile);
        if (styleClasses != null) {
            for (String styleClass : styleClasses) {
                if (styleClass == null || styleClass.isBlank()) {
                    continue;
                }
                if (!tile.getStyleClass().contains(styleClass)) {
                    tile.getStyleClass().add(styleClass);
                }
            }
        }
        syncExplorerIconTileSelectedState(tile, path != null && isPathCurrentlySelected(path));
    }

    private void installStableExplorerIconTileHover(Node tile) {
        if (tile == null) {
            return;
        }
        tile.setPickOnBounds(true);
        if (Boolean.TRUE.equals(tile.getProperties().get(EXPLORER_ICON_TILE_HOVER_HANDLER_KEY))) {
            return;
        }
        tile.getProperties().put(EXPLORER_ICON_TILE_HOVER_HANDLER_KEY, Boolean.TRUE);
        tile.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> syncExplorerIconTileHoverState(tile, true));
        tile.addEventHandler(MouseEvent.MOUSE_EXITED, e -> syncExplorerIconTileHoverState(tile, false));
    }

    private void syncExplorerIconTileHoverState(Node tile, boolean active) {
        if (tile == null) {
            return;
        }
        if (iconMarqueeGestureOwnsSelection || shouldFreezeExplorerFileViewHoverPresentation()) {
            active = false;
        }
        setStyleClass(tile, "explorer-hover", active);
        tile.pseudoClassStateChanged(PSEUDO_EXPLORER_HOVER, active);
        tile.applyCss();
        if (tile instanceof Parent parent) {
            parent.requestLayout();
        }
    }

    private void syncExplorerIconTileSelectedState(Node tile, boolean selected) {
        if (tile == null) {
            return;
        }
        Object taggedPath = tile.getProperties().get(EXPLORER_ICON_TILE_PATH_KEY);
        Path tilePath = taggedPath instanceof Path path ? path : null;
        boolean contextMenuOwned = isExplorerContextMenuOwnedPath(tilePath);
        boolean effectiveSelected = selected || contextMenuOwned;
        setStyleClass(tile, "explorer-selected", effectiveSelected);
        setStyleClass(tile, "explorer-context-menu-owned", contextMenuOwned);
        tile.pseudoClassStateChanged(PSEUDO_EXPLORER_SELECTED, effectiveSelected);
        tile.applyCss();
        if (tile instanceof Parent parent) {
            parent.requestLayout();
        }
    }

    private void markExplorerIconTileChild(Node node) {
        if (node == null) {
            return;
        }
        node.setMouseTransparent(true);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                markExplorerIconTileChild(child);
            }
        }
    }

    private boolean isPathCurrentlySelected(Path path) {
        if (path == null) {
            return false;
        }
        if (isExplorerContextMenuOwnedPath(path)) {
            return true;
        }
        if (isIconMode(viewMode) && iconPresentationSelectedPaths.contains(path)) {
            return true;
        }
        if (viewMode == ViewMode.DETAILS && detailsPresentationSelectedPaths.contains(path)) {
            return true;
        }
        if (fileTable == null || fileTable.getSelectionModel() == null) {
            return false;
        }
        ObservableList<FileItem> selectedItems = fileTable.getSelectionModel().getSelectedItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            return false;
        }
        for (FileItem item : selectedItems) {
            if (item != null && Objects.equals(item.path(), path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExplorerContextMenuOwnedPath(Path path) {
        return explorerContextMenuSelectionPresentationHold
                && path != null
                && java.util.Objects.equals(path, explorerContextMenuOwnedPath);
    }

    private void refreshVisibleIconTileSelectionState() {
        if (viewMode == ViewMode.DETAILS) {
            return;
        }
        switch (viewMode) {
            case EXTRA_LARGE_ICONS, LARGE_ICONS, MEDIUM_ICONS, SMALL_ICONS, LIST, TILES, CONTENT -> {
                refreshVisibleIconTileSelectionState(iconFlow);
                refreshVisibleIconTileSelectionState(virtualIconGridView);
                refreshVisibleIconTileSelectionState(virtualIconListView);
            }
            default -> {
                // no-op
            }
        }
    }

    private void refreshVisibleIconTileSelectionState(Node node) {
        if (node == null) {
            return;
        }
        Object taggedPath = node.getProperties().get(EXPLORER_ICON_TILE_PATH_KEY);
        if (taggedPath instanceof Path path) {
            syncExplorerIconTileSelectedState(node, isPathCurrentlySelected(path));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                refreshVisibleIconTileSelectionState(child);
            }
        }
    }

    /**
     * Registers the Choose Details catalog and the always-known base columns.
     */
    private void ensureOptionalDetailsColumns() {
        ensureChooseDetailsCatalog();
    }

    private void ensureChooseDetailsCatalog() {
        if (fileTable == null) return;
        if (chooseDetailSpecs.isEmpty()) {
            chooseDetailSpecs = DetailColumnCatalog.allSpecs();
            chooseDetailSpecsByKey = DetailColumnCatalog.specsByKey();
            detailOrderedKeys.clear();
            detailOrderedKeys.addAll(DetailColumnCatalog.defaultOrderedKeys());
        }
        registerCoreDetailColumn("name", "Name", colName);
        registerCoreDetailColumn("modified", "Date modified", colModified);
        registerCoreDetailColumn("type", "Type", colType);
        registerCoreDetailColumn("size", "Size", colSize);
    }

    private void registerCoreDetailColumn(String key,
                                          String label,
                                          javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?> column) {
        if (column == null) return;
        column.getProperties().put(PROP_DETAIL_COLUMN_KEY, key);
        if (column.getText() == null || column.getText().isBlank()) {
            column.setText(label);
        }
        column.setMinWidth(detailMinWidthForKey(key));
        column.setMaxWidth(detailMaxWidthForKey(key));
        lazyDetailColumns.putIfAbsent(key, column);
        applyRememberedDetailWidth(key, column);
    }

    private void wireDetailColumnPersistence(javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?> column) {
        if (column == null) return;
        if (Boolean.TRUE.equals(column.getProperties().get("fileexplorer.detailColumn.persistenceWired"))) {
            return;
        }
        column.getProperties().put("fileexplorer.detailColumn.persistenceWired", Boolean.TRUE);
        column.visibleProperty().addListener((obs, ov, nv) -> {
            syncDetailsSortHeaderState();
            armPersistDetailsColumnsState();
        });
        column.widthProperty().addListener((obs, ov, nv) -> {
            String key = detailsColKey(column);
            if (key != null) {
                detailColumnWidths.put(key, column.getWidth());
            }
            armPersistDetailsColumnsState();
        });
    }

    private javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?> ensureDetailColumnByKey(String key) {
        if (key == null || key.isBlank()) return null;
        ensureChooseDetailsCatalog();
        javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?> existing = lazyDetailColumns.get(key);
        if (existing != null) {
            applyRememberedDetailWidth(key, existing);
            return existing;
        }
        com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec spec = chooseDetailSpecsByKey.get(key);
        if (spec == null) return null;
        javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, String> column = new javafx.scene.control.TableColumn<>(spec.label());
        column.getProperties().put(PROP_DETAIL_COLUMN_KEY, key);
        column.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(detailValueForKey(key, param.getValue())));
        if ("index".equals(key)) {
            column.setCellFactory(_ -> new javafx.scene.control.TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            });
        }
        double prefWidth = Math.max(detailMinWidthForKey(key), Math.min(detailMaxWidthForKey(key), spec.label().length() * 8.5 + 32.0));
        column.setMinWidth(detailMinWidthForKey(key));
        column.setMaxWidth(detailMaxWidthForKey(key));
        column.setPrefWidth(prefWidth);
        applyRememberedDetailWidth(key, column);
        wireDetailColumnPersistence(column);
        lazyDetailColumns.put(key, column);
        if ("dateCreated".equals(key)) colDateCreated = column;
        if ("authors".equals(key)) colAuthors = column;
        if ("tags".equals(key)) colTags = column;
        if ("title".equals(key)) colTitle = column;
        return column;
    }

    private void applyRememberedDetailWidth(String key,
                                            javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?> column) {
        if (key == null || column == null) return;
        Double remembered = detailColumnWidths.get(key);
        if (remembered == null) return;
        if (remembered >= 24.0 && remembered <= 2000.0) {
            double clamped = Math.max(detailMinWidthForKey(key), Math.min(detailMaxWidthForKey(key), remembered));
            column.setPrefWidth(clamped);
        }
    }

    private double detailMinWidthForKey(String key) {
        return switch (key == null ? "" : key) {
            case "name" -> 240.0;
            case "modified", "dateCreated", "dateAccessed" -> 156.0;
            case "type", "authors", "tags", "title", "path", "folder", "fileLocation" -> 128.0;
            case "size", "index" -> 84.0;
            default -> 72.0;
        };
    }

    private double detailMaxWidthForKey(String key) {
        return switch (key == null ? "" : key) {
            case "size", "index" -> 240.0;
            case "modified", "dateCreated", "dateAccessed" -> 360.0;
            default -> 1600.0;
        };
    }

    private String detailsColKey(javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?> c) {
        if (c == null) return null;
        Object v = c.getProperties().get(PROP_DETAIL_COLUMN_KEY);
        return (v instanceof String s && !s.isBlank()) ? s : null;
    }

    private javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?> detailsColByKey(String key) {
        if (key == null) return null;
        ensureChooseDetailsCatalog();
        return lazyDetailColumns.get(key);
    }

    private java.util.Set<String> currentVisibleDetailKeys() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (fileTable == null) return out;
        for (var c : fileTable.getColumns()) {
            String key = detailsColKey(c);
            if (key != null && c.isVisible()) {
                out.add(key);
            }
        }
        out.add("name");
        return out;
    }

    private void restoreDetailsColumnsState() {
        if (fileTable == null) return;
        ensureChooseDetailsCatalog();

        detailColumnWidths.clear();
        String widthRaw = prefs.get(PREF_TABLE_DETAILS_COLS_WIDTHS, "");
        if (!widthRaw.isBlank()) {
            for (String part : widthRaw.split(",")) {
                String[] kv = part.split("=");
                if (kv.length != 2) continue;
                try {
                    double width = Double.parseDouble(kv[1].trim());
                    if (width >= 24.0 && width <= 2000.0) {
                        detailColumnWidths.put(kv[0].trim(), width);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        detailOrderedKeys.clear();
        String orderRaw = prefs.get(PREF_TABLE_DETAILS_COLS_ORDER, "");
        if (!orderRaw.isBlank()) {
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            for (String key : orderRaw.split(",")) {
                String trimmed = key.trim();
                if (!trimmed.isBlank() && chooseDetailSpecsByKey.containsKey(trimmed)) {
                    seen.add(trimmed);
                }
            }
            for (com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec spec : chooseDetailSpecs) {
                seen.add(spec.key());
            }
            detailOrderedKeys.addAll(seen);
        } else {
            detailOrderedKeys.addAll(DetailColumnCatalog.defaultOrderedKeys());
        }

        java.util.LinkedHashSet<String> visible = new java.util.LinkedHashSet<>();
        String visibleRaw = prefs.get(PREF_TABLE_DETAILS_COLS_VISIBLE, "");
        if (!visibleRaw.isBlank()) {
            for (String key : visibleRaw.split(",")) {
                String trimmed = key.trim();
                if (!trimmed.isBlank() && chooseDetailSpecsByKey.containsKey(trimmed)) {
                    visible.add(trimmed);
                }
            }
        } else {
            visible.add("name");
            visible.add("modified");
            visible.add("type");
            visible.add("size");
        }
        visible.add("name");
        detailColumnWidths.putIfAbsent("name", 375.0);

        rebuildActiveDetailColumns(visible);
    }

    private void rebuildActiveDetailColumns(java.util.Set<String> visibleKeys) {
        if (fileTable == null) return;
        ensureChooseDetailsCatalog();

        java.util.LinkedHashSet<String> orderedVisibleKeys = new java.util.LinkedHashSet<>();
        orderedVisibleKeys.add("name");
        for (String key : detailOrderedKeys) {
            if ("name".equals(key) || visibleKeys.contains(key)) {
                orderedVisibleKeys.add(key);
            }
        }

        java.util.List<javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?>> orderedVisibleColumns = new java.util.ArrayList<>();
        for (String key : orderedVisibleKeys) {
            var column = ensureDetailColumnByKey(key);
            if (column != null && !orderedVisibleColumns.contains(column)) {
                orderedVisibleColumns.add(column);
            }
        }
        orderedVisibleColumns.remove(colName);
        orderedVisibleColumns.add(0, colName);

        for (var entry : lazyDetailColumns.entrySet()) {
            entry.getValue().setVisible(orderedVisibleKeys.contains(entry.getKey()));
        }

        java.util.List<javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?>> nonDetails = new java.util.ArrayList<>();
        for (var c : fileTable.getColumns()) {
            if (detailsColKey(c) == null) {
                nonDetails.add(c);
            }
        }
        fileTable.getColumns().setAll(nonDetails);
        fileTable.getColumns().addAll(orderedVisibleColumns);
        syncDetailsSortHeaderState();
    }

    private void syncDetailOrderKeysFromTable() {
        if (fileTable == null) return;
        ensureChooseDetailsCatalog();
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        for (var c : fileTable.getColumns()) {
            String key = detailsColKey(c);
            if (key != null && chooseDetailSpecsByKey.containsKey(key)) {
                merged.add(key);
            }
        }
        for (String key : detailOrderedKeys) {
            if (chooseDetailSpecsByKey.containsKey(key)) {
                merged.add(key);
            }
        }
        for (com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec spec : chooseDetailSpecs) {
            merged.add(spec.key());
        }
        detailOrderedKeys.clear();
        detailOrderedKeys.addAll(merged);
    }

    private void persistDetailsColumnsState() {
        if (fileTable == null) return;
        ensureChooseDetailsCatalog();
        syncDetailOrderKeysFromTable();

        java.util.LinkedHashSet<String> visible = new java.util.LinkedHashSet<>(currentVisibleDetailKeys());
        visible.add("name");
        prefs.put(PREF_TABLE_DETAILS_COLS_VISIBLE, String.join(",", visible));
        prefs.put(PREF_TABLE_DETAILS_COLS_ORDER, String.join(",", detailOrderedKeys));

        java.util.LinkedHashMap<String, Double> mergedWidths = new java.util.LinkedHashMap<>(detailColumnWidths);
        for (var entry : lazyDetailColumns.entrySet()) {
            mergedWidths.put(entry.getKey(), entry.getValue().getWidth());
        }
        detailColumnWidths.clear();
        detailColumnWidths.putAll(mergedWidths);

        java.util.List<String> widthParts = new java.util.ArrayList<>();
        for (String key : detailOrderedKeys) {
            Double width = detailColumnWidths.get(key);
            if (width != null && width >= 24.0 && width <= 2000.0) {
                widthParts.add(key + "=" + width);
            }
        }
        prefs.put(PREF_TABLE_DETAILS_COLS_WIDTHS, String.join(",", widthParts));
    }

    private void wireDetailsColumnsPersistence() {
        if (fileTable == null || detailsColumnsPersistenceWired) return;
        ensureChooseDetailsCatalog();
        detailsColumnsPersistenceWired = true;
        wireDetailColumnPersistence(colName);
        wireDetailColumnPersistence(colModified);
        wireDetailColumnPersistence(colType);
        wireDetailColumnPersistence(colSize);
        fileTable.getColumns().addListener((javafx.collections.ListChangeListener<javafx.scene.control.TableColumn<FileItem, ?>>) ch -> {
            while (ch.next()) {
                if (ch.wasAdded() || ch.wasRemoved() || ch.wasPermutated() || ch.wasReplaced()) {
                    syncDetailOrderKeysFromTable();
                    syncDetailsSortHeaderState();
                    armPersistDetailsColumnsState();
                }
            }
        });
    }

    private void armPersistDetailsColumnsState() {
        if (detailsColumnsPersistDebounce == null) {
            persistDetailsColumnsState();
            return;
        }
        detailsColumnsPersistDebounce.playFromStart();
    }

    private java.util.Map<String, javafx.scene.control.TableColumn<?, ?>> currentHeaderDetailsColumns() {
        java.util.LinkedHashMap<String, javafx.scene.control.TableColumn<?, ?>> out = new java.util.LinkedHashMap<>();
        if (fileTable == null) return out;
        for (var c : fileTable.getColumns()) {
            String key = detailsColKey((javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?>) c);
            if (key == null) continue;
            String label = DetailColumnCatalog.labelForKey(key);
            out.put(label, c);
        }
        return out;
    }

    private java.util.Map<String, javafx.scene.control.TableColumn<?, ?>> allHeaderDetailsColumns() {
        java.util.LinkedHashMap<String, javafx.scene.control.TableColumn<?, ?>> out = new java.util.LinkedHashMap<>();
        ensureChooseDetailsCatalog();
        java.util.LinkedHashSet<String> orderedKeys = new java.util.LinkedHashSet<>();
        orderedKeys.add("name");
        orderedKeys.addAll(detailOrderedKeys);
        for (com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec spec : chooseDetailSpecs) {
            orderedKeys.add(spec.key());
        }
        for (String key : orderedKeys) {
            javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?> column = ensureDetailColumnByKey(key);
            if (column == null) {
                continue;
            }
            out.put(DetailColumnCatalog.labelForKey(key), column);
        }
        return out;
    }

    private String detailKeyForHeaderLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        ensureChooseDetailsCatalog();
        for (com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec spec : chooseDetailSpecs) {
            if (label.equals(spec.label())) {
                return spec.key();
            }
        }
        return null;
    }

    private boolean isHeaderDetailColumnVisible(String label) {
        String key = detailKeyForHeaderLabel(label);
        if (key == null) {
            return false;
        }
        if ("name".equals(key)) {
            return true;
        }
        return currentVisibleDetailKeys().contains(key);
    }

    private void setHeaderDetailColumnVisible(String label, boolean visible) {
        String key = detailKeyForHeaderLabel(label);
        if (key == null || "name".equals(key) || fileTable == null) {
            return;
        }
        ensureChooseDetailsCatalog();
        syncDetailOrderKeysFromTable();

        java.util.LinkedHashSet<String> visibleKeys = new java.util.LinkedHashSet<>(currentVisibleDetailKeys());
        if (visible) {
            visibleKeys.add(key);
            if (!detailOrderedKeys.contains(key)) {
                detailOrderedKeys.add(key);
            }
        } else {
            visibleKeys.remove(key);
        }
        visibleKeys.add("name");
        rebuildActiveDetailColumns(visibleKeys);
        armPersistDetailsColumnsState();
    }

    private void restoreDefaultDetailsColumns() {
        if (fileTable == null) {
            return;
        }
        ensureChooseDetailsCatalog();
        detailOrderedKeys.clear();
        detailOrderedKeys.addAll(DetailColumnCatalog.defaultOrderedKeys());

        detailColumnWidths.clear();
        detailColumnWidths.put("name", 375.0);
        detailColumnWidths.put("modified", 184.0);
        detailColumnWidths.put("type", 176.0);
        detailColumnWidths.put("size", 108.0);

        rebuildActiveDetailColumns(new java.util.LinkedHashSet<>(java.util.List.of("name", "modified", "type", "size")));
        applyRememberedDetailWidth("name", colName);
        applyRememberedDetailWidth("modified", colModified);
        applyRememberedDetailWidth("type", colType);
        applyRememberedDetailWidth("size", colSize);
        syncDetailsSortHeaderState();
        armPersistDetailsColumnsState();
    }

    private void installDetailsHeaderInteractionParity() {
        if (fileTable == null) return;
        if (Boolean.TRUE.equals(fileTable.getProperties().get(PROP_DETAILS_HEADER_INTERACTION_INSTALLED))) {
            return;
        }
        fileTable.getProperties().put(PROP_DETAILS_HEADER_INTERACTION_INSTALLED, Boolean.TRUE);

        fileTable.addEventFilter(MouseEvent.MOUSE_MOVED, this::handleDetailsHeaderMouseMoved);
        fileTable.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, evt -> clearDetailsHeaderResizeHotState());
        fileTable.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
            if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) {
                TableColumn<FileItem, ?> resizeColumn = findDetailsResizeColumn(evt);
                if (resizeColumn != null) {
                    com.fileexplorer.ui.table.ColumnAutoFitUtil.sizeToFit(fileTable, resizeColumn);
                    armPersistDetailsColumnsState();
                    evt.consume();
                }
            }
        });
    }

    private void handleDetailsHeaderMouseMoved(MouseEvent evt) {
        if (fileTable == null || evt == null) {
            return;
        }
        Node headerNode = findDetailsHeaderNode(evt);
        if (headerNode == null || !isNearHeaderResizeEdge(headerNode, evt)) {
            clearDetailsHeaderResizeHotState();
            return;
        }
        TableColumn<FileItem, ?> resizeColumn = resolveDetailsHeaderColumn(headerNode);
        if (resizeColumn == null || detailsColKey(resizeColumn) == null) {
            clearDetailsHeaderResizeHotState();
            return;
        }
        Node previous = activeDetailsResizeHotHeader.get();
        if (previous != headerNode) {
            if (previous != null) {
                setStyleClass(previous, "details-resize-hot", false);
                previous.setCursor(Cursor.DEFAULT);
            }
            activeDetailsResizeHotHeader.set(headerNode);
        }
        setStyleClass(headerNode, "details-resize-hot", true);
        headerNode.setCursor(Cursor.H_RESIZE);
    }

    private void clearDetailsHeaderResizeHotState() {
        Node previous = activeDetailsResizeHotHeader.get();
        activeDetailsResizeHotHeader.set(null);
        if (previous != null) {
            setStyleClass(previous, "details-resize-hot", false);
            previous.setCursor(Cursor.DEFAULT);
        }
    }

    private Node findDetailsHeaderNode(MouseEvent evt) {
        Node target = (evt.getPickResult() != null) ? evt.getPickResult().getIntersectedNode() : null;
        if (target == null) {
            return null;
        }
        for (Node node = target; node != null; node = node.getParent()) {
            String cn = node.getClass().getName();
            if (cn.contains("NestedTableColumnHeader")) {
                continue;
            }
            if (cn.contains("TableColumnHeader")) {
                return node;
            }
            if (node instanceof TableView) {
                break;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private TableColumn<FileItem, ?> resolveDetailsHeaderColumn(Node headerNode) {
        if (headerNode == null) {
            return null;
        }
        try {
            var method = headerNode.getClass().getMethod("getTableColumn");
            Object value = method.invoke(headerNode);
            return value instanceof TableColumn<?, ?> column ? (TableColumn<FileItem, ?>) column : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isNearHeaderResizeEdge(Node headerNode, MouseEvent evt) {
        if (headerNode == null || evt == null) {
            return false;
        }
        javafx.geometry.Point2D local = headerNode.screenToLocal(evt.getScreenX(), evt.getScreenY());
        if (local == null) {
            return false;
        }
        double width = headerNode.getLayoutBounds().getWidth();
        return local.getX() >= Math.max(0.0, width - DETAILS_HEADER_RESIZE_EDGE_PX)
                && local.getX() <= width + DETAILS_HEADER_RESIZE_EDGE_PX;
    }

    private TableColumn<FileItem, ?> findDetailsResizeColumn(MouseEvent evt) {
        Node headerNode = findDetailsHeaderNode(evt);
        if (headerNode == null || !isNearHeaderResizeEdge(headerNode, evt)) {
            return null;
        }
        return resolveDetailsHeaderColumn(headerNode);
    }

    private void installHeaderDetailsMenu() {
        if (fileTable == null) return;
        ensureChooseDetailsCatalog();
        restoreDetailsColumnsState();
        syncDetailsSortHeaderState();
        wireDetailsColumnsPersistence();
        if (Boolean.TRUE.equals(fileTable.getProperties().get("fileexplorer.headerMenuInstalled"))) {
            return;
        }
        fileTable.getProperties().put("fileexplorer.headerMenuInstalled", Boolean.TRUE);
        TableHeaderContextMenuInstaller.install(
                fileTable,
                this::allHeaderDetailsColumns,
                this::isHeaderDetailColumnVisible,
                this::setHeaderDetailColumnVisible,
                this::restoreDefaultDetailsColumns,
                this::showChooseDetailsDialog
        );
    }

    /**
     * Explorer-like "Choose Details" dialog invoked from the header menu.
     */
    private void showChooseDetailsDialog() {
        if (fileTable == null) return;
        ensureChooseDetailsCatalog();
        syncDetailOrderKeysFromTable();

        java.util.Set<String> visible = currentVisibleDetailKeys();
        java.util.LinkedHashSet<String> orderedKeys = new java.util.LinkedHashSet<>();
        for (String key : detailOrderedKeys) {
            if (chooseDetailSpecsByKey.containsKey(key)) {
                orderedKeys.add(key);
            }
        }
        for (com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec spec : chooseDetailSpecs) {
            orderedKeys.add(spec.key());
        }

        java.util.List<com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec> initial = new java.util.ArrayList<>(orderedKeys.size());
        for (String key : orderedKeys) {
            var spec = chooseDetailSpecsByKey.get(key);
            if (spec == null) continue;
            initial.add(new com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec(
                    spec.key(),
                    spec.label(),
                    visible.contains(spec.key()) || spec.locked(),
                    spec.locked()
            ));
        }

        java.util.Map<String, Double> currentWidths = new java.util.LinkedHashMap<>();
        for (com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec spec : chooseDetailSpecs) {
            javafx.scene.control.TableColumn<com.fileexplorer.model.FileItem, ?> column = detailsColByKey(spec.key());
            double width = (column != null && column.getWidth() > 0.0)
                    ? column.getWidth()
                    : detailColumnWidths.getOrDefault(spec.key(), 120.0);
            currentWidths.put(spec.key(), width);
        }

        javafx.stage.Window owner = (fileTable.getScene() != null) ? fileTable.getScene().getWindow() : null;
        var resultOpt = com.fileexplorer.ui.dialog.ChooseDetailsDialog.show(owner, initial, chooseDetailSpecs, currentWidths);
        if (resultOpt.isEmpty()) return;

        var result = resultOpt.get();
        if (result.widthByKey() != null) {
            detailColumnWidths.putAll(result.widthByKey());
        }
        detailOrderedKeys.clear();
        java.util.LinkedHashSet<String> mergedOrder = new java.util.LinkedHashSet<>();
        for (String key : result.orderedKeys()) {
            if (chooseDetailSpecsByKey.containsKey(key)) {
                mergedOrder.add(key);
            }
        }
        for (com.fileexplorer.ui.dialog.ChooseDetailsDialog.DetailSpec spec : chooseDetailSpecs) {
            mergedOrder.add(spec.key());
        }
        detailOrderedKeys.addAll(mergedOrder);

        java.util.LinkedHashSet<String> selectedVisible = new java.util.LinkedHashSet<>();
        for (String key : result.visibleKeys()) {
            if (chooseDetailSpecsByKey.containsKey(key)) {
                selectedVisible.add(key);
            }
        }
        selectedVisible.add("name");
        rebuildActiveDetailColumns(selectedVisible);
        for (var entry : lazyDetailColumns.entrySet()) {
            applyRememberedDetailWidth(entry.getKey(), entry.getValue());
        }
        syncDetailsSortHeaderState();
        persistDetailsColumnsState();
    }

    private String detailValueForKey(String key, FileItem item) {
        if (key == null || item == null) return "";
        Path path = item.path();
        return switch (key) {
            case "name" -> displayNameForTable(path);
            case "modified", "date", "dateLastSaved" -> item.modified();
            case "type", "itemType", "kind", "contentType", "perceivedType" -> item.type();
            case "size", "totalFileSize", "totalSize" -> item.size();
            case "index" -> detailRowIndex(item);
            case "dateCreated", "contentCreated", "mediaCreated" -> safeCreationTimeString(path);
            case "dateAccessed", "dateVisited" -> safeLastAccessTimeString(path);
            case "path" -> path != null ? path.toString() : "";
            case "folder", "fileLocation" -> safeParentPath(path);
            case "folderName" -> safeParentName(path);
            case "filename" -> safeFileName(path);
            case "fileExtension" -> safeFileExtension(path);
            case "title", "subject" -> safeFileStem(path);
            case "owner", "fileOwnership" -> safeOwner(path);
            case "attributes" -> safeAttributes(path);
            case "localComputer" -> java.util.Objects.toString(System.getenv("COMPUTERNAME"), "");
            default -> "";
        };
    }

    private String detailRowIndex(FileItem item) {
        if (item == null || fileTable == null || fileTable.getItems() == null) return "";
        int idx = fileTable.getItems().indexOf(item);
        return idx >= 0 ? Integer.toString(idx + 1) : "";
    }

    private String safeCreationTimeString(Path path) {
        if (path == null) return "";
        try {
            java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
            java.nio.file.attribute.FileTime ft = attrs.creationTime();
            if (ft == null) return "";
            return safeFormatFileTime(ft);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeLastAccessTimeString(Path path) {
        if (path == null) return "";
        try {
            java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
            java.nio.file.attribute.FileTime ft = attrs.lastAccessTime();
            if (ft == null) return "";
            return safeFormatFileTime(ft);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeFormatFileTime(java.nio.file.attribute.FileTime fileTime) {
        try {
            java.time.Instant instant = fileTime.toInstant();
            java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
            return java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a", java.util.Locale.US).format(ldt);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeParentPath(Path path) {
        if (path == null) return "";
        Path parent = path.getParent();
        return parent != null ? parent.toString() : "";
    }

    private String safeParentName(Path path) {
        if (path == null) return "";
        Path parent = path.getParent();
        if (parent == null) return "";
        Path fileName = parent.getFileName();
        return fileName != null ? fileName.toString() : parent.toString();
    }

    private String safeFileName(Path path) {
        if (path == null) return "";
        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : path.toString();
    }

    private String safeFileStem(Path path) {
        String name = safeFileName(path);
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    private String safeFileExtension(Path path) {
        String name = safeFileName(path);
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1);
        }
        return "";
    }

    private String safeOwner(Path path) {
        if (path == null) return "";
        try {
            var owner = Files.getOwner(path);
            return owner != null ? owner.getName() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeAttributes(Path path) {
        if (path == null) return "";
        java.util.List<String> flags = new java.util.ArrayList<>(6);
        try {
            if (Files.isDirectory(path)) flags.add("D");
            if (Files.isHidden(path)) flags.add("H");
            if (Files.isReadable(path)) flags.add("R");
            if (Files.isWritable(path)) flags.add("W");
            if (Files.isExecutable(path)) flags.add("X");
            return String.join("", flags);
        } catch (Exception ignored) {
            return "";
        }
    }

    private javafx.scene.control.ContextMenu createExplorerContextMenu(String menuKind) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        menu.getStyleClass().addAll("explorer-context-menu", "explorer-flyout-menu");
        menu.getProperties().put("explorer.context.menu.kind", menuKind == null || menuKind.isBlank() ? "unknown" : menuKind);
        // Keep Explorer-style menus open until the user explicitly clicks outside the popup.
        // Native JavaFX auto-hide is vulnerable to the heavy hover/layout churn produced by the
        // virtual file-view surfaces, so we keep the popup alive ourselves and dismiss it only on
        // deliberate outside-owner interaction, Escape, or window-focus loss.
        menu.setAutoHide(false);
        menu.setHideOnEscape(true);
        menu.setConsumeAutoHidingEvents(false);
        installExplorerContextMenuLifecycleLogging(menu);
        installExplorerContextMenuDismissOnOwnerInteraction(menu);
        return menu;
    }

    private void installExplorerContextMenuLifecycleLogging(javafx.scene.control.ContextMenu menu) {
        if (menu == null) {
            return;
        }
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING,
                event -> logExplorerContextMenuLifecycle(menu, "window-showing"));
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN,
                event -> logExplorerContextMenuLifecycle(menu, "window-shown"));
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDING,
                event -> logExplorerContextMenuLifecycle(menu, "window-hiding"));
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN,
                event -> logExplorerContextMenuLifecycle(menu, "window-hidden"));
    }

    private void logExplorerContextMenuLifecycle(javafx.scene.control.ContextMenu menu, String phase) {
        String menuKind = explorerContextMenuKind(menu);
        String selectedPath = explorerContextMenuPathLabel(getFocusedOrSelectedPath());
        String currentPath = explorerContextMenuPathLabel(currentDirectory);
        String ownerFocused = "unknown";
        double anchorX = Double.NaN;
        double anchorY = Double.NaN;
        boolean showing = false;
        if (menu != null) {
            showing = menu.isShowing();
            anchorX = menu.getAnchorX();
            anchorY = menu.getAnchorY();
            Window ownerWindow = menu.getOwnerWindow();
            if (ownerWindow != null) {
                ownerFocused = Boolean.toString(ownerWindow.isFocused());
            }
        }
        String message = "EXPLORER_CONTEXT_MENU[" + menuKind + "][" + phase + "]: showing=" + showing
                + " pending=" + isExplorerFileViewContextMenuPending()
                + " view=" + (viewMode == null ? "null" : viewMode.name())
                + " selected=" + selectedPath
                + " currentDir=" + currentPath
                + " anchor=(" + explorerContextMenuCoordinateLabel(anchorX) + ", "
                + explorerContextMenuCoordinateLabel(anchorY) + ")"
                + " ownerFocused=" + ownerFocused;
        LOG.info(message);
        System.out.println(message);
    }

    private void logExplorerContextMenuHideRequest(javafx.scene.control.ContextMenu menu,
                                                   String reason,
                                                   MouseEvent mouseEvent) {
        String menuKind = explorerContextMenuKind(menu);
        String pointer = "mouse=none";
        if (mouseEvent != null) {
            pointer = "mouseButton=" + mouseEvent.getButton()
                    + " scene=(" + explorerContextMenuCoordinateLabel(mouseEvent.getSceneX()) + ", "
                    + explorerContextMenuCoordinateLabel(mouseEvent.getSceneY()) + ")"
                    + " screen=(" + explorerContextMenuCoordinateLabel(mouseEvent.getScreenX()) + ", "
                    + explorerContextMenuCoordinateLabel(mouseEvent.getScreenY()) + ")"
                    + " consumed=" + mouseEvent.isConsumed();
        }
        String message = "EXPLORER_CONTEXT_MENU[" + menuKind + "][hide-request]: reason=" + reason + " " + pointer;
        LOG.info(message);
        System.out.println(message);
    }

    private String explorerContextMenuKind(javafx.scene.control.ContextMenu menu) {
        if (menu == null) {
            return "unknown";
        }
        Object value = menu.getProperties().get("explorer.context.menu.kind");
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return "unknown";
    }

    private String explorerContextMenuPathLabel(Path path) {
        if (path == null) {
            return "<none>";
        }
        try {
            return path.toString();
        } catch (Exception ex) {
            return "<path-error:" + ex.getClass().getSimpleName() + ">";
        }
    }

    private String explorerContextMenuCoordinateLabel(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private void installExplorerContextMenuDismissOnOwnerInteraction(javafx.scene.control.ContextMenu menu) {
        if (menu == null) {
            return;
        }
        final String installedKey = "explorer.context.menu.dismiss.installed";
        final String ownerSceneMouseHandlerKey = "explorer.context.menu.dismiss.owner.scene.mouse.press";
        final String ownerSceneRefKey = "explorer.context.menu.dismiss.owner.scene.ref";
        final String ownerWindowFocusListenerKey = "explorer.context.menu.dismiss.owner.window.focus.listener";
        final String ownerWindowRefKey = "explorer.context.menu.dismiss.owner.window.ref";
        if (Boolean.TRUE.equals(menu.getProperties().get(installedKey))) {
            return;
        }
        menu.getProperties().put(installedKey, Boolean.TRUE);

        javafx.event.EventHandler<MouseEvent> ownerSceneMouseHandler = event -> {
            if (!shouldDismissExplorerContextMenuOnOwnerMousePress(menu, event)) {
                return;
            }
            logExplorerContextMenuHideRequest(menu, "owner-scene-mouse-press", event);
            menu.hide();
        };
        javafx.beans.value.ChangeListener<Boolean> ownerWindowFocusListener = (obs, wasFocused, isFocused) -> {
            if (!Boolean.TRUE.equals(isFocused) && menu.isShowing()) {
                logExplorerContextMenuHideRequest(menu, "owner-window-focus-lost", null);
                menu.hide();
            }
        };

        menu.getProperties().put(ownerSceneMouseHandlerKey, ownerSceneMouseHandler);
        menu.getProperties().put(ownerWindowFocusListenerKey, ownerWindowFocusListener);

        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING, event -> {
            Window ownerWindow = menu.getOwnerWindow();
            if (ownerWindow == null) {
                return;
            }
            Scene ownerScene = ownerWindow.getScene();
            if (ownerScene != null) {
                ownerScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, ownerSceneMouseHandler);
                ownerScene.addEventFilter(MouseEvent.MOUSE_PRESSED, ownerSceneMouseHandler);
                menu.getProperties().put(ownerSceneRefKey, ownerScene);
            }
            ownerWindow.focusedProperty().removeListener(ownerWindowFocusListener);
            ownerWindow.focusedProperty().addListener(ownerWindowFocusListener);
            menu.getProperties().put(ownerWindowRefKey, ownerWindow);
        });
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDING, event -> uninstallExplorerContextMenuDismissHandlers(menu));
    }

    private boolean shouldDismissExplorerContextMenuOnOwnerMousePress(javafx.scene.control.ContextMenu menu, MouseEvent event) {
        if (menu == null || !menu.isShowing() || event == null) {
            return false;
        }
        if (event.getEventType() != MouseEvent.MOUSE_PRESSED) {
            return false;
        }
        MouseButton button = event.getButton();
        return button == MouseButton.SECONDARY || button == MouseButton.PRIMARY || button == MouseButton.MIDDLE;
    }

    @SuppressWarnings("unchecked")
    private void uninstallExplorerContextMenuDismissHandlers(javafx.scene.control.ContextMenu menu) {
        if (menu == null) {
            return;
        }
        final String ownerSceneMouseHandlerKey = "explorer.context.menu.dismiss.owner.scene.mouse.press";
        final String ownerSceneRefKey = "explorer.context.menu.dismiss.owner.scene.ref";
        final String ownerWindowFocusListenerKey = "explorer.context.menu.dismiss.owner.window.focus.listener";
        final String ownerWindowRefKey = "explorer.context.menu.dismiss.owner.window.ref";

        Scene ownerScene = (Scene) menu.getProperties().remove(ownerSceneRefKey);
        Window ownerWindow = (Window) menu.getProperties().remove(ownerWindowRefKey);
        javafx.event.EventHandler<MouseEvent> ownerSceneMouseHandler =
                (javafx.event.EventHandler<MouseEvent>) menu.getProperties().get(ownerSceneMouseHandlerKey);
        javafx.beans.value.ChangeListener<Boolean> ownerWindowFocusListener =
                (javafx.beans.value.ChangeListener<Boolean>) menu.getProperties().get(ownerWindowFocusListenerKey);

        if (ownerScene != null && ownerSceneMouseHandler != null) {
            ownerScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, ownerSceneMouseHandler);
        }
        if (ownerWindow != null && ownerWindowFocusListener != null) {
            ownerWindow.focusedProperty().removeListener(ownerWindowFocusListener);
        }
    }

    private javafx.scene.control.MenuItem createExplorerMenuItem(String text, String glyph, Runnable action) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(text);
        item.getStyleClass().add("explorer-menu-item");
        if (glyph != null && !glyph.isBlank()) {
            Label icon = new Label(glyph);
            icon.getStyleClass().addAll("fluent-icon", "explorer-menu-glyph");
            item.setGraphic(icon);
        }
        if (action != null) {
            item.setOnAction(e -> {
                try {
                    action.run();
                } finally {
                    if (item.getParentPopup() instanceof javafx.scene.control.ContextMenu parentPopup) {
                        logExplorerContextMenuHideRequest(parentPopup, "menu-item-action:" + text, null);
                        parentPopup.hide();
                    }
                }
            });
        }
        return item;
    }

    private javafx.scene.control.SeparatorMenuItem createExplorerSeparator() {
        javafx.scene.control.SeparatorMenuItem separator = new javafx.scene.control.SeparatorMenuItem();
        separator.getStyleClass().add("explorer-menu-separator");
        return separator;
    }

    // Reused context menu instance for file operations (prevents multiple menus stacking).
    private transient javafx.scene.control.ContextMenu fileOpsMenu;
    private transient javafx.scene.control.MenuItem fileOpsOpenItem;
    private transient javafx.scene.control.MenuItem fileOpsOpenInNewTabItem;
    private transient javafx.scene.control.MenuItem fileOpsPinToQuickAccessItem;
    private transient javafx.scene.control.MenuItem fileOpsCopyItem;
    private transient javafx.scene.control.MenuItem fileOpsCutItem;
    private transient javafx.scene.control.MenuItem fileOpsPasteItem;
    private transient javafx.scene.control.MenuItem fileOpsRenameItem;
    private transient javafx.scene.control.MenuItem fileOpsDeleteItem;
    private transient javafx.scene.control.MenuItem fileOpsPropertiesItem;
    private transient javafx.scene.control.ContextMenu fileViewBackgroundMenu;
    private transient javafx.scene.control.Menu fileViewBackgroundViewMenu;
    private transient javafx.scene.control.Menu fileViewBackgroundSortMenu;
    private transient javafx.scene.control.Menu fileViewBackgroundGroupMenu;
    private transient javafx.scene.control.Menu fileViewBackgroundNewMenu;
    private transient javafx.scene.control.MenuItem fileViewBackgroundUndoItem;
    private transient javafx.scene.control.MenuItem fileViewBackgroundPasteItem;
    private transient javafx.scene.control.MenuItem fileViewBackgroundPasteShortcutItem;
    private transient javafx.scene.control.MenuItem fileViewBackgroundNewFolderItem;
    private transient javafx.scene.control.MenuItem fileViewBackgroundNewTextDocumentItem;
    private transient javafx.scene.control.MenuItem fileViewBackgroundNewUnsupportedItem;
    private transient javafx.scene.control.MenuItem fileViewBackgroundSelectAllItem;
    private transient javafx.scene.control.MenuItem fileViewBackgroundRefreshItem;
    private transient javafx.scene.control.MenuItem fileViewBackgroundPropertiesItem;
    private transient java.util.EnumMap<ViewMode, javafx.scene.control.RadioMenuItem> fileViewBackgroundViewModeItems;
    private transient java.util.EnumMap<SortKey, javafx.scene.control.RadioMenuItem> fileViewBackgroundSortItems;
    private FileItem getFocusedOrSelectedFileItem() {
        if (fileTable == null || fileTable.getItems() == null) {
            return null;
        }
        int focusedIndex = fileTable.getFocusModel() != null ? fileTable.getFocusModel().getFocusedIndex() : -1;
        if (focusedIndex >= 0 && focusedIndex < fileTable.getItems().size()) {
            FileItem focused = fileTable.getItems().get(focusedIndex);
            if (focused != null) {
                return focused;
            }
        }
        return fileTable.getSelectionModel() != null ? fileTable.getSelectionModel().getSelectedItem() : null;
    }

    private Path getFocusedOrSelectedPath() {
        FileItem item = getFocusedOrSelectedFileItem();
        return item != null ? item.path() : null;
    }

    private int getFocusedOrSelectedIndex() {
        if (fileTable == null) {
            return -1;
        }
        int focusedIndex = fileTable.getFocusModel() != null ? fileTable.getFocusModel().getFocusedIndex() : -1;
        if (focusedIndex >= 0) {
            return focusedIndex;
        }
        return fileTable.getSelectionModel() != null ? fileTable.getSelectionModel().getSelectedIndex() : -1;
    }

    private int findTableIndexForPath(Path path) {
        if (path == null || fileTable == null || fileTable.getItems() == null) {
            return -1;
        }
        ObservableList<FileItem> items = fileTable.getItems();
        for (int i = 0; i < items.size(); i++) {
            FileItem item = items.get(i);
            if (item != null && path.equals(item.path())) {
                return i;
            }
        }
        return -1;
    }

    private void restoreFocusToTablePath(Path path) {
        if (path == null || fileTable == null) {
            return;
        }
        int idx = findTableIndexForPath(path);
        if (idx < 0) {
            return;
        }
        if (fileTable.getSelectionModel() != null) {
            fileTable.getSelectionModel().clearAndSelect(idx);
        }
        if (fileTable.getFocusModel() != null) {
            fileTable.getFocusModel().focus(idx);
        }
        scheduleExplorerPathVisibilityStabilization(path, true);
    }

    private void scheduleExplorerPathVisibilityStabilization(Path path, boolean focusActiveSurface) {
        if (path == null) {
            return;
        }
        ensureExplorerPathVisible(path, focusActiveSurface);
        Platform.runLater(() -> {
            ensureExplorerPathVisible(path, focusActiveSurface);
            Platform.runLater(() -> {
                ensureExplorerPathVisible(path, focusActiveSurface);
                Platform.runLater(() -> ensureExplorerPathVisible(path, focusActiveSurface));
            });
        });
    }

    private void ensureExplorerPathVisible(Path path, boolean focusActiveSurface) {
        if (path == null || fileTable == null) {
            return;
        }
        int idx = findTableIndexForPath(path);
        if (idx < 0) {
            return;
        }
        if (fileTable.getFocusModel() != null) {
            fileTable.getFocusModel().focus(idx);
        }
        if (viewMode == ViewMode.DETAILS) {
            fileTable.scrollTo(Math.max(0, idx - 2));
            if (focusActiveSurface) {
                fileTable.requestFocus();
            }
            return;
        }
        refreshActiveSelectionPresentation();
        scrollActiveIconPathIntoView(path);
        if (focusActiveSurface) {
            requestActiveIconSurfaceFocus();
        }
    }

    private void scrollActiveIconPathIntoView(Path path) {
        if (path == null || !isIconMode(viewMode)) {
            return;
        }
        int idx = findTableIndexForPath(path);
        if (idx < 0) {
            return;
        }
        if (virtualIconListView != null && virtualIconListView.isVisible()) {
            virtualIconListView.scrollTo(Math.max(0, idx - 2));
            return;
        }
        if (virtualIconGridView != null && virtualIconGridView.isVisible()) {
            Object configuredItemsPerRow = virtualIconGridView.getProperties().get("iconGridItemsPerRow");
            int itemsPerRow = configuredItemsPerRow instanceof Number number ? Math.max(1, number.intValue()) : Math.max(1, computeItemsPerIconRow());
            int rowIndex = Math.max(0, idx / itemsPerRow);
            virtualIconGridView.scrollTo(Math.max(0, rowIndex - 1));
            return;
        }
        if (iconScroll == null || !iconScroll.isVisible()) {
            return;
        }
        Platform.runLater(() -> {
            Node tile = findVisibleExplorerIconTileByPath(path);
            if (tile != null) {
                ensureExplorerIconTileVisible(tile);
            }
        });
    }

    private boolean isPathVisibleInDetailsViewport(Path path) {
        if (path == null || fileTable == null || !fileTable.isVisible()) {
            return false;
        }
        java.util.List<TableRow<FileItem>> rows = collectVisibleDetailsRows();
        for (TableRow<FileItem> row : rows) {
            if (row != null && row.getItem() != null && java.util.Objects.equals(path, row.getItem().path())) {
                return true;
            }
        }
        return false;
    }

    private void minimallyRevealPathInDetailsViewport(Path path, boolean focusActiveSurface) {
        if (path == null || fileTable == null) {
            return;
        }
        int idx = findTableIndexForPath(path);
        if (idx < 0) {
            return;
        }
        java.util.List<TableRow<FileItem>> rows = collectVisibleDetailsRows();
        if (rows.isEmpty()) {
            fileTable.scrollTo(Math.max(0, idx - 2));
        } else {
            int first = rows.get(0).getIndex();
            int last = rows.get(rows.size() - 1).getIndex();
            if (idx < first) {
                fileTable.scrollTo(idx);
            } else if (idx > last) {
                int visibleCount = Math.max(1, rows.size());
                fileTable.scrollTo(Math.max(0, idx - visibleCount + 1));
            }
        }
        if (fileTable.getFocusModel() != null) {
            fileTable.getFocusModel().focus(idx);
        }
        if (focusActiveSurface) {
            fileTable.requestFocus();
        }
    }

    private boolean isPathVisibleInIconViewport(Path path) {
        if (path == null || !isIconMode(viewMode)) {
            return false;
        }
        return findVisibleExplorerIconTileByPath(path) != null;
    }

    private void minimallyRevealPathInIconViewport(Path path, boolean focusActiveSurface) {
        if (path == null || !isIconMode(viewMode)) {
            return;
        }
        refreshActiveSelectionPresentation();
        if (!isPathVisibleInIconViewport(path)) {
            scrollActiveIconPathIntoView(path);
        }
        if (focusActiveSurface) {
            requestActiveIconSurfaceFocus();
        }
    }

    private void restoreInlineRenameCommitViewport(InlineRenameSession session) {
        if (session == null || session.surface == InlineRenameSurface.TREE) {
            return;
        }
        if (viewMode == ViewMode.DETAILS) {
            if (fileTable == null) {
                return;
            }
            Path anchorPath = session.commitViewportAnchorPath;
            if (anchorPath != null) {
                int idx = findTableIndexForPath(anchorPath);
                if (idx >= 0) {
                    fileTable.scrollTo(idx);
                }
            }
            return;
        }
        if (virtualIconGridView != null && virtualIconGridView.isVisible()) {
            restoreVerticalScrollValue(virtualIconGridView, session.commitVirtualGridScrollValue);
        }
        if (virtualIconListView != null && virtualIconListView.isVisible()) {
            restoreVerticalScrollValue(virtualIconListView, session.commitVirtualListScrollValue);
        }
        if (iconScroll != null && iconScroll.isVisible() && !Double.isNaN(session.commitFlowScrollValue)) {
            double clamped = Math.max(0.0, Math.min(1.0, session.commitFlowScrollValue));
            Platform.runLater(() -> iconScroll.setVvalue(clamped));
        }
        if (session.commitViewportAnchorPath != null && !java.util.Objects.equals(session.commitViewportAnchorPath, session.pendingResultPath)) {
            Platform.runLater(() -> {
                if (!isPathVisibleInIconViewport(session.commitViewportAnchorPath)) {
                    scrollActiveIconPathIntoView(session.commitViewportAnchorPath);
                }
            });
        }
    }

    private void restoreInlineRenameCommitViewportAndReveal(InlineRenameSession session,
                                                            Path committedPath,
                                                            boolean focusActiveSurface) {
        if (session == null) {
            if (committedPath != null) {
                scheduleExplorerPathVisibilityStabilization(committedPath, focusActiveSurface);
            }
            return;
        }
        restoreInlineRenameCommitViewport(session);
        Platform.runLater(() -> {
            if (viewMode == ViewMode.DETAILS) {
                minimallyRevealPathInDetailsViewport(committedPath, focusActiveSurface);
                Platform.runLater(() -> minimallyRevealPathInDetailsViewport(committedPath, focusActiveSurface));
                return;
            }
            minimallyRevealPathInIconViewport(committedPath, focusActiveSurface);
            Platform.runLater(() -> minimallyRevealPathInIconViewport(committedPath, focusActiveSurface));
        });
    }

    private Node findVisibleExplorerIconTileByPath(Path path) {
        if (path == null) {
            return null;
        }
        for (Node tile : collectVisibleExplorerIconTiles()) {
            if (java.util.Objects.equals(path, pathForExplorerIconTile(tile))) {
                return tile;
            }
        }
        return null;
    }

    private void ensureExplorerIconTileVisible(Node tile) {
        if (tile == null || iconScroll == null || iconFlow == null) {
            return;
        }
        Bounds tileBounds = iconFlow.sceneToLocal(tile.localToScene(tile.getBoundsInLocal()));
        Bounds viewportBounds = iconScroll.getViewportBounds();
        if (tileBounds == null || viewportBounds == null) {
            return;
        }
        double contentHeight = iconFlow.getBoundsInLocal().getHeight();
        double viewportHeight = viewportBounds.getHeight();
        if (contentHeight <= 0.0 || viewportHeight <= 0.0 || contentHeight <= viewportHeight) {
            return;
        }
        double currentPixels = iconScroll.getVvalue() * Math.max(0.0, contentHeight - viewportHeight);
        double tileMinY = tileBounds.getMinY();
        double tileMaxY = tileBounds.getMaxY();
        double targetPixels = currentPixels;
        if (tileMinY < currentPixels) {
            targetPixels = tileMinY;
        } else if (tileMaxY > currentPixels + viewportHeight) {
            targetPixels = tileMaxY - viewportHeight;
        }
        double maxPixels = Math.max(0.0, contentHeight - viewportHeight);
        double clampedPixels = Math.max(0.0, Math.min(maxPixels, targetPixels));
        if (maxPixels <= 0.0) {
            iconScroll.setVvalue(0.0);
            return;
        }
        iconScroll.setVvalue(clampedPixels / maxPixels);
    }

    private void hideExplorerTransientUi() {
        if (fileOpsMenu != null) {
            logExplorerContextMenuHideRequest(fileOpsMenu, "hideExplorerTransientUi", null);
            fileOpsMenu.hide();
        }
        if (fileViewBackgroundMenu != null) {
            logExplorerContextMenuHideRequest(fileViewBackgroundMenu, "hideExplorerTransientUi", null);
            fileViewBackgroundMenu.hide();
        }
        if (fileTable != null) {
            com.fileexplorer.ui.table.TableHeaderContextMenuInstaller.resetEphemeralHeaderState(fileTable);
        }
    }

    private void showFileOpsContextMenuForFocusedRow() {
        if (fileTable == null) {
            return;
        }
        int index = getFocusedOrSelectedIndex();
        if (index < 0 || index >= fileTable.getItems().size()) {
            return;
        }
        if (!fileTable.getSelectionModel().isSelected(index)) {
            fileTable.getSelectionModel().clearAndSelect(index);
        }
        if (fileTable.getFocusModel() != null) {
            fileTable.getFocusModel().focus(index);
        }
        fileTable.requestFocus();
        fileTable.applyCss();
        fileTable.layout();
        for (Node node : fileTable.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> rawRow && rawRow.getIndex() == index) {
                Bounds bounds = node.localToScreen(node.getBoundsInLocal());
                if (bounds != null) {
                    showFileOpsContextMenu(bounds.getMinX() + 24.0, bounds.getMinY() + Math.min(24.0, bounds.getHeight()));
                    return;
                }
            }
        }
        Bounds tableBounds = fileTable.localToScreen(fileTable.getBoundsInLocal());
        if (tableBounds != null) {
            showFileOpsContextMenu(tableBounds.getMinX() + 48.0, tableBounds.getMinY() + 48.0);
        }
    }

/**
 * showFileOpsContextMenu.
 *
 * @param screenX TODO
 * @param screenY TODO
 */
    private void showFileOpsContextMenu(double screenX, double screenY) {
        if (fileTable == null) return;
        suppressExplorerMetadataPopupForMillis(1500L);
        if (fileOpsMenu == null) {
            fileOpsMenu = createExplorerContextMenu("file-ops");
            fileOpsOpenItem = createExplorerMenuItem("Open", "", this::openSelection);
            fileOpsOpenInNewTabItem = createExplorerMenuItem("Open in new tab", "", this::openSelectionInNewTab);
            fileOpsPinToQuickAccessItem = createExplorerMenuItem("Pin to Quick access", "", this::toggleSelectionQuickAccessPin);
            fileOpsCopyItem = createExplorerMenuItem("Copy", "", () -> copySelection(false));
            fileOpsCutItem = createExplorerMenuItem("Cut", "", () -> copySelection(true));
            fileOpsPasteItem = createExplorerMenuItem("Paste", "", this::pasteIntoCurrentDirectory);
            fileOpsRenameItem = createExplorerMenuItem("Rename", "", this::renameSelection);
            fileOpsDeleteItem = createExplorerMenuItem("Delete", "", () -> deleteSelection(false));
            fileOpsPropertiesItem = createExplorerMenuItem("Properties", "", this::openPropertiesForSelection);
            fileOpsMenu.getItems().addAll(
                    fileOpsOpenItem,
                    fileOpsOpenInNewTabItem,
                    createExplorerSeparator(),
                    fileOpsPinToQuickAccessItem,
                    createExplorerSeparator(),
                    fileOpsCopyItem,
                    fileOpsCutItem,
                    fileOpsPasteItem,
                    createExplorerSeparator(),
                    fileOpsRenameItem,
                    fileOpsDeleteItem,
                    createExplorerSeparator(),
                    fileOpsPropertiesItem);
            fileOpsMenu.setOnShowing(e -> {
                markExplorerFileViewContextMenuPending();
                suppressExplorerMetadataPopupForMillis(1500L);
                preserveExplorerSelectionPresentationForContextMenu();
                maintainExplorerContextMenuSelectionHold();
                syncFileOpsMenuState();
            });
            fileOpsMenu.setOnShown(e -> {
                clearExplorerFileViewContextMenuPending();
                suppressExplorerMetadataPopupForMillis(300L);
                maintainExplorerContextMenuSelectionHold();
                Platform.runLater(this::maintainExplorerContextMenuSelectionHold);
            });
            fileOpsMenu.setOnHiding(e -> {
                suppressExplorerMetadataPopupForMillis(300L);
                clearExplorerContextMenuSelectionPresentationHold();
            });
            fileOpsMenu.setOnHidden(e -> {
                clearExplorerFileViewContextMenuPending();
                suppressExplorerMetadataPopupForMillis(300L);
            });
            fileTable.getProperties().put(com.fileexplorer.ui.table.TableHeaderContextMenuInstaller.PROP_FILEOPS_MENU, fileOpsMenu);
        }
        com.fileexplorer.ui.table.TableHeaderContextMenuInstaller.resetEphemeralHeaderState(fileTable);
        if (fileViewBackgroundMenu != null) {
            logExplorerContextMenuHideRequest(fileViewBackgroundMenu, "file-ops-show-hides-background-menu", null);
            fileViewBackgroundMenu.hide();
        }
        preserveExplorerSelectionPresentationForContextMenu();
        markExplorerFileViewContextMenuPending();
        logExplorerContextMenuLifecycle(fileOpsMenu, "show-request");
        logExplorerContextMenuHideRequest(fileOpsMenu, "showFileOpsContextMenu-reset-before-show", null);
        fileOpsMenu.hide();
        Node anchor = getActiveFileOpsMenuAnchor();
        if (anchor == null) {
            clearExplorerFileViewContextMenuPending();
            clearExplorerContextMenuSelectionPresentationHold();
            return;
        }
        final Node finalAnchor = anchor;
        Platform.runLater(() -> {
            if (finalAnchor.getScene() == null) {
                clearExplorerFileViewContextMenuPending();
                clearExplorerContextMenuSelectionPresentationHold();
                return;
            }
            logExplorerContextMenuHideRequest(fileOpsMenu, "showFileOpsContextMenu-replace-before-show", null);
            fileOpsMenu.hide();
            fileOpsMenu.show(finalAnchor, screenX, screenY);
        });
    }

    private void setExplorerMenuItemLabel(javafx.scene.control.MenuItem item, String text) {
        if (item == null || text == null) {
            return;
        }
        if (!java.util.Objects.equals(item.getText(), text)) {
            item.setText(text);
        }
    }

    private com.fileexplorer.service.ops.command.CommandManager.ExecutedCommand peekUndoCommand() {
        try {
            if (context == null || context.commandManager() == null) {
                return null;
            }
            java.util.List<com.fileexplorer.service.ops.command.CommandManager.ExecutedCommand> undoStack =
                    context.commandManager().undoStackSnapshot();
            return undoStack.isEmpty() ? null : undoStack.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    private com.fileexplorer.service.ops.command.CommandManager.ExecutedCommand peekRedoCommand() {
        try {
            if (context == null || context.commandManager() == null) {
                return null;
            }
            java.util.List<com.fileexplorer.service.ops.command.CommandManager.ExecutedCommand> redoStack =
                    context.commandManager().redoStackSnapshot();
            return redoStack.isEmpty() ? null : redoStack.get(0);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isShellStateManagedCommand(com.fileexplorer.service.ops.command.Command command) {
        return command instanceof com.fileexplorer.service.ops.command.CreateDirectoryCommand
                || command instanceof com.fileexplorer.service.ops.command.RenamePathCommand;
    }

    private InlineRenameSession captureShellCommandRefreshSessionForUndoRedo(com.fileexplorer.service.ops.command.Command command) {
        if (!isShellStateManagedCommand(command)) {
            return null;
        }
        Path sourcePath = null;
        if (command instanceof com.fileexplorer.service.ops.command.CreateDirectoryCommand createDirectoryCommand) {
            sourcePath = createDirectoryCommand.directoryPath();
        } else if (command instanceof com.fileexplorer.service.ops.command.RenamePathCommand renamePathCommand) {
            sourcePath = renamePathCommand.sourcePath();
        }
        InlineRenameSession session = captureInlineRenameSession(sourcePath,
                InlineRenameSessionKind.RENAME_EXISTING,
                resolveInlineRenameSurfaceForCurrentView());
        captureInlineRenameCommitViewport(session);
        return session;
    }

    private void scheduleShellCommandRestoreAfterRefresh(InlineRenameSession session,
                                                         Path targetPath,
                                                         boolean focusActiveSurface) {
        pendingShellCommandRestoreSession = session;
        pendingShellCommandRestorePath = targetPath;
        pendingShellCommandRestoreFocusActiveSurface = focusActiveSurface;
    }

    private void clearPendingShellCommandRestore() {
        pendingShellCommandRestoreSession = null;
        pendingShellCommandRestorePath = null;
        pendingShellCommandRestoreFocusActiveSurface = false;
    }

    private void applyShellStateRefreshPlanForUndoRedo(com.fileexplorer.service.ops.command.Command command,
                                                       ExplorerCommandAction action,
                                                       InlineRenameSession session) {
        if (!isShellStateManagedCommand(command) || session == null) {
            syncExplorerContextMenuShellState();
            return;
        }
        if (command instanceof com.fileexplorer.service.ops.command.CreateDirectoryCommand createDirectoryCommand) {
            Path directoryPath = createDirectoryCommand.directoryPath();
            if (action == ExplorerCommandAction.UNDO) {
                pendingRestoreSelection = true;
                pendingReselectPath = directoryPath;
                pendingReselectIndex = findTableIndexForPath(directoryPath);
                pendingReselectPreferIndexOnMissing = true;
                scheduleShellCommandRestoreAfterRefresh(session, null, true);
            } else {
                pendingInlineRenameSelectionPath = directoryPath;
                pendingInlineRenameSelectionIndex = findTableIndexForPath(directoryPath);
                pendingReselectPreferIndexOnMissing = false;
                scheduleShellCommandRestoreAfterRefresh(session, directoryPath, true);
            }
            refresh();
            syncExplorerContextMenuShellState();
            return;
        }
        if (command instanceof com.fileexplorer.service.ops.command.RenamePathCommand renamePathCommand) {
            Path targetPath = action == ExplorerCommandAction.UNDO
                    ? renamePathCommand.sourcePath()
                    : renamePathCommand.targetPath();
            pendingInlineRenameSelectionPath = targetPath;
            pendingInlineRenameSelectionIndex = findTableIndexForPath(action == ExplorerCommandAction.UNDO
                    ? renamePathCommand.targetPath()
                    : renamePathCommand.sourcePath());
            pendingReselectPreferIndexOnMissing = false;
            scheduleShellCommandRestoreAfterRefresh(session, targetPath, true);
            refresh();
            syncExplorerContextMenuShellState();
        }
    }

    private void applyPendingShellCommandRestoreIfNeeded(Path directory) {
        if (pendingShellCommandRestoreSession == null) {
            return;
        }
        InlineRenameSession session = pendingShellCommandRestoreSession;
        Path targetPath = pendingShellCommandRestorePath;
        if (targetPath != null && directory != null && targetPath.getParent() != null
                && !java.util.Objects.equals(directory, targetPath.getParent())) {
            return;
        }
        boolean focusActiveSurface = pendingShellCommandRestoreFocusActiveSurface;
        clearPendingShellCommandRestore();
        if (targetPath != null) {
            restoreInlineRenameCommitViewportAndReveal(session, targetPath, focusActiveSurface);
            Platform.runLater(() -> requestFocusForInlineRenameSession(session));
            armInlineRenameFocusGuard();
            return;
        }
        restoreInlineRenameCommitViewport(session);
        Platform.runLater(() -> requestFocusForInlineRenameSession(session));
        armInlineRenameFocusGuard();
    }

    private String formatUndoMenuLabel() {
        com.fileexplorer.service.ops.command.CommandManager.ExecutedCommand undoCommand = peekUndoCommand();
        if (undoCommand == null || undoCommand.command() == null) {
            return "Undo";
        }
        String label;
        try {
            label = undoCommand.command().label();
        } catch (Exception ex) {
            label = null;
        }
        if (label == null || label.isBlank()) {
            return "Undo";
        }
        String compact = label.strip();
        if (compact.length() > 72) {
            compact = compact.substring(0, 71) + "…";
        }
        return "Undo " + compact;
    }

    private boolean isPathPinnedToQuickAccess(Path path) {
        if (path == null) {
            return false;
        }
        Path normalized = path.normalize();
        for (Path existing : userPinnedHomeLocations) {
            if (existing != null && java.util.Objects.equals(existing.normalize(), normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean canPasteIntoCurrentDirectory() {
        Path targetDirectory = resolveActiveDirectoryForShellCommands();
        if (targetDirectory == null || clipboardPaths.isEmpty()) {
            return false;
        }
        if (!clipboardCut) {
            return true;
        }
        Path normalizedTarget = targetDirectory.normalize();
        for (Path clipboardPath : clipboardPaths) {
            if (clipboardPath == null) {
                return true;
            }
            Path parent = clipboardPath.getParent();
            if (parent == null || !java.util.Objects.equals(parent.normalize(), normalizedTarget)) {
                return true;
            }
        }
        return false;
    }

    private void syncFileOpsMenuState() {
        syncFileOpsMenuState(captureExplorerCommandStateSnapshot());
    }

    private void syncFileOpsMenuState(ExplorerCommandStateSnapshot snapshot) {
        ExplorerCommandStateSnapshot effectiveSnapshot = snapshot == null ? captureExplorerCommandStateSnapshot() : snapshot;
        boolean hasSelection = effectiveSnapshot.selectionCount > 0;
        boolean singleSelection = effectiveSnapshot.selectionCount == 1;
        if (fileOpsOpenItem != null) {
            setExplorerMenuItemLabel(fileOpsOpenItem, "Open");
            fileOpsOpenItem.setDisable(!hasSelection);
        }
        if (fileOpsOpenInNewTabItem != null) {
            setExplorerMenuItemLabel(fileOpsOpenInNewTabItem, "Open in new tab");
            fileOpsOpenInNewTabItem.setDisable(!effectiveSnapshot.singleDirectorySelection);
        }
        if (fileOpsPinToQuickAccessItem != null) {
            setExplorerMenuItemLabel(fileOpsPinToQuickAccessItem,
                    effectiveSnapshot.pinnedDirectorySelection ? "Unpin from Quick access" : "Pin to Quick access");
            fileOpsPinToQuickAccessItem.setDisable(!effectiveSnapshot.singleDirectorySelection);
        }
        if (fileOpsCopyItem != null) {
            setExplorerMenuItemLabel(fileOpsCopyItem, "Copy");
            fileOpsCopyItem.setDisable(!hasSelection);
        }
        if (fileOpsCutItem != null) {
            setExplorerMenuItemLabel(fileOpsCutItem, "Cut");
            fileOpsCutItem.setDisable(!hasSelection);
        }
        if (fileOpsPasteItem != null) {
            setExplorerMenuItemLabel(fileOpsPasteItem, "Paste");
            fileOpsPasteItem.setDisable(!effectiveSnapshot.canPaste);
        }
        if (fileOpsRenameItem != null) {
            setExplorerMenuItemLabel(fileOpsRenameItem, "Rename");
            fileOpsRenameItem.setDisable(!singleSelection);
        }
        if (fileOpsDeleteItem != null) {
            setExplorerMenuItemLabel(fileOpsDeleteItem, "Delete");
            fileOpsDeleteItem.setDisable(!hasSelection);
        }
        if (fileOpsPropertiesItem != null) {
            setExplorerMenuItemLabel(fileOpsPropertiesItem, "Properties");
            fileOpsPropertiesItem.setDisable(!singleSelection);
        }
    }

    private void syncExplorerContextMenuShellState() {
        syncExplorerContextMenuShellState(captureExplorerCommandStateSnapshot());
    }

    private void syncExplorerContextMenuShellState(ExplorerCommandStateSnapshot snapshot) {
        syncFileOpsMenuState(snapshot);
        syncFileViewBackgroundMenuState(snapshot);
    }

    private Node resolveExplorerContextMenuAnchor(Node requestedAnchor) {
        if (requestedAnchor != null && requestedAnchor.getScene() != null) {
            if (requestedAnchor == fileTable || requestedAnchor == detailsViewShell || requestedAnchor == viewHost) {
                return requestedAnchor;
            }
            if (isNodeWithinActiveIconSurface(requestedAnchor) || isNodeWithinDetailsSelectionSurface(requestedAnchor)) {
                return requestedAnchor;
            }
        }
        return getActiveFileOpsMenuAnchor();
    }


    private Path resolveActiveDirectoryForShellCommands() {
        if (currentDirectory != null) {
            return currentDirectory;
        }
        if (context != null) {
            try {
                return context.currentDirectory();
            } catch (Exception ex) {
                LOG.log(Level.FINEST, "Could not resolve current directory from context", ex);
            }
        }
        return null;
    }


    private InlineRenameSurface resolveInlineRenameSurfaceForCurrentView() {
        return viewMode == ViewMode.DETAILS ? InlineRenameSurface.FILE_DETAILS : InlineRenameSurface.FILE_ICON;
    }

    private Scene resolveControllerScene() {
        if (boundScene != null) {
            return boundScene;
        }
        if (root != null && root.getScene() != null) {
            return root.getScene();
        }
        if (fileTable != null && fileTable.getScene() != null) {
            return fileTable.getScene();
        }
        if (folderTree != null && folderTree.getScene() != null) {
            return folderTree.getScene();
        }
        return null;
    }

    private InlineRenameSession captureInlineRenameSession(Path path,
                                                           InlineRenameSessionKind kind,
                                                           InlineRenameSurface surface) {
        Path treeSelectionPath = null;
        if (folderTree != null && folderTree.getSelectionModel() != null) {
            TreeItem<Path> treeSelection = folderTree.getSelectionModel().getSelectedItem();
            treeSelectionPath = treeSelection != null ? treeSelection.getValue() : null;
        }
        Scene scene = resolveControllerScene();
        Node priorFocusOwner = scene != null ? scene.getFocusOwner() : null;
        Path focusPath = getFocusedOrSelectedPath();
        Path anchorPath = iconSelectionAnchorPath != null ? iconSelectionAnchorPath : focusPath;
        int selectedIndex = fileTable != null && fileTable.getSelectionModel() != null
                ? fileTable.getSelectionModel().getSelectedIndex()
                : -1;
        int focusedIndex = fileTable != null && fileTable.getFocusModel() != null
                ? fileTable.getFocusModel().getFocusedIndex()
                : -1;
        return new InlineRenameSession(
                kind,
                surface,
                path,
                displayNameForTable(path),
                getSelectedItems(),
                focusPath,
                anchorPath,
                treeSelectionPath,
                selectedIndex,
                focusedIndex,
                viewMode,
                folderTree != null && folderTree.isFocused(),
                fileTable != null && fileTable.isFocused(),
                isIconMode(viewMode) && isActiveIconSurfaceFocused(),
                priorFocusOwner);
    }

    private void captureInlineRenameCommitViewport(InlineRenameSession session) {
        if (session == null || session.surface == InlineRenameSurface.TREE) {
            return;
        }
        session.commitViewportAnchorPath = null;
        session.commitViewportAnchorIndex = -1;
        session.commitViewportVisibleCount = -1;
        session.commitFlowScrollValue = Double.NaN;
        session.commitVirtualGridScrollValue = Double.NaN;
        session.commitVirtualListScrollValue = Double.NaN;
        if (viewMode == ViewMode.DETAILS) {
            java.util.List<TableRow<FileItem>> rows = collectVisibleDetailsRows();
            session.commitViewportVisibleCount = rows.size();
            for (TableRow<FileItem> row : rows) {
                if (row == null || row.getItem() == null || row.getItem().path() == null) {
                    continue;
                }
                Path candidate = row.getItem().path();
                if (!java.util.Objects.equals(candidate, session.sourcePath)) {
                    session.commitViewportAnchorPath = candidate;
                    session.commitViewportAnchorIndex = row.getIndex();
                    break;
                }
            }
            if (session.commitViewportAnchorPath == null && !rows.isEmpty()) {
                TableRow<FileItem> row = rows.get(0);
                if (row != null && row.getItem() != null) {
                    session.commitViewportAnchorPath = row.getItem().path();
                    session.commitViewportAnchorIndex = row.getIndex();
                }
            }
            return;
        }
        if (iconScroll != null && iconScroll.isVisible()) {
            session.commitFlowScrollValue = iconScroll.getVvalue();
        }
        session.commitVirtualGridScrollValue = captureVerticalScrollValue(virtualIconGridView);
        session.commitVirtualListScrollValue = captureVerticalScrollValue(virtualIconListView);
        session.commitViewportAnchorPath = captureFirstVisibleExplorerIconPath(session.sourcePath);
    }

    private double captureVerticalScrollValue(Node control) {
        ScrollBar scrollBar = findVerticalScrollBar(control);
        if (scrollBar == null) {
            return Double.NaN;
        }
        return scrollBar.getValue();
    }

    private void restoreVerticalScrollValue(Node control, double value) {
        if (control == null || Double.isNaN(value)) {
            return;
        }
        Platform.runLater(() -> {
            ScrollBar scrollBar = findVerticalScrollBar(control);
            if (scrollBar == null) {
                return;
            }
            double clamped = Math.max(scrollBar.getMin(), Math.min(scrollBar.getMax(), value));
            scrollBar.setValue(clamped);
        });
    }

    private Path captureFirstVisibleExplorerIconPath(Path excludedPath) {
        java.util.List<Node> visibleTiles = collectVisibleExplorerIconTiles();
        if (visibleTiles.isEmpty()) {
            return null;
        }
        visibleTiles.sort((left, right) -> {
            Bounds leftBounds = left.localToScene(left.getBoundsInLocal());
            Bounds rightBounds = right.localToScene(right.getBoundsInLocal());
            double leftMinY = leftBounds == null ? Double.MAX_VALUE : leftBounds.getMinY();
            double rightMinY = rightBounds == null ? Double.MAX_VALUE : rightBounds.getMinY();
            int cmp = Double.compare(leftMinY, rightMinY);
            if (cmp != 0) {
                return cmp;
            }
            double leftMinX = leftBounds == null ? Double.MAX_VALUE : leftBounds.getMinX();
            double rightMinX = rightBounds == null ? Double.MAX_VALUE : rightBounds.getMinX();
            return Double.compare(leftMinX, rightMinX);
        });
        Path fallback = null;
        for (Node tile : visibleTiles) {
            Path tilePath = pathForExplorerIconTile(tile);
            if (tilePath == null) {
                continue;
            }
            if (fallback == null) {
                fallback = tilePath;
            }
            if (!java.util.Objects.equals(tilePath, excludedPath)) {
                return tilePath;
            }
        }
        return fallback;
    }

    private InlineRenameSession consumePendingCreatedInlineRenameSession(Path path) {
        if (path == null || pendingCreatedInlineRenameSession == null) {
            return null;
        }
        if (!java.util.Objects.equals(path, pendingCreatedInlineRenameSession.sourcePath)) {
            return null;
        }
        InlineRenameSession session = pendingCreatedInlineRenameSession;
        pendingCreatedInlineRenameSession = null;
        pendingCreateAndRenamePath = null;
        return session;
    }

    private boolean isInlineRenameFocusGuardActive() {
        return inlineRenameFocusGuardPulsesRemaining > 0
                || (activeInlineRenameSession != null && activeInlineRenameSession.awaitingCompletion);
    }

    private void armInlineRenameFocusGuard() {
        inlineRenameFocusGuardPulsesRemaining = Math.max(inlineRenameFocusGuardPulsesRemaining, 4);
        Platform.runLater(this::advanceInlineRenameFocusGuard);
    }

    private void advanceInlineRenameFocusGuard() {
        if (inlineRenameFocusGuardPulsesRemaining <= 0) {
            return;
        }
        inlineRenameFocusGuardPulsesRemaining--;
        if (inlineRenameFocusGuardPulsesRemaining > 0) {
            Platform.runLater(this::advanceInlineRenameFocusGuard);
        }
    }

    private boolean restorePriorInlineRenameFocusOwner(InlineRenameSession session) {
        if (session == null || session.priorFocusOwnerRef == null) {
            return false;
        }
        Node priorFocusOwner = session.priorFocusOwnerRef.get();
        if (priorFocusOwner == null || priorFocusOwner.getScene() == null) {
            return false;
        }
        try {
            priorFocusOwner.requestFocus();
            return priorFocusOwner.isFocused();
        } catch (Exception ex) {
            return false;
        }
    }

    private void requestFocusForInlineRenameSession(InlineRenameSession session) {
        if (restorePriorInlineRenameFocusOwner(session)) {
            return;
        }
        if (session != null && (session.surface == InlineRenameSurface.TREE || session.treeFocusedBefore)) {
            if (folderTree != null) {
                folderTree.requestFocus();
                return;
            }
        }
        if (session != null && (session.surface == InlineRenameSurface.FILE_ICON || session.iconSurfaceFocusedBefore)) {
            requestActiveIconSurfaceFocus();
            return;
        }
        requestActiveDetailsSurfaceFocus();
    }

    private void clearSelectionWithoutStatusUpdate() {
        if (fileTable != null && fileTable.getSelectionModel() != null) {
            fileTable.getSelectionModel().clearSelection();
        }
        if (fileTable != null && fileTable.getFocusModel() != null) {
            fileTable.getFocusModel().focus(-1);
        }
        setExplorerSelectionAnchorPath(null);
        replaceDetailsPresentationSelectedPaths(java.util.Collections.emptySet());
        replaceIconPresentationSelectedPaths(java.util.Collections.emptySet());
        refreshActiveSelectionPresentation();
    }

    private void restoreInlineRenameSessionSelectionAndFocus(InlineRenameSession session) {
        if (session == null) {
            return;
        }
        if (session.surface == InlineRenameSurface.TREE) {
            if (session.treeSelectionPathBefore != null) {
                expandAndSelectFolder(session.treeSelectionPathBefore);
            }
            Platform.runLater(() -> requestFocusForInlineRenameSession(session));
            return;
        }
        if (!session.selectedPathsBefore.isEmpty()) {
            Path focusPath = session.focusPathBefore;
            if (focusPath == null || !session.selectedPathsBefore.contains(focusPath)) {
                focusPath = session.anchorPathBefore != null && session.selectedPathsBefore.contains(session.anchorPathBefore)
                        ? session.anchorPathBefore
                        : session.selectedPathsBefore.get(0);
            }
            applyExplorerPathSelection(session.selectedPathsBefore, focusPath);
            if (focusPath != null) {
                scheduleExplorerPathVisibilityStabilization(focusPath, false);
            }
        } else if (session.focusPathBefore != null) {
            restoreFocusToTablePath(session.focusPathBefore);
        } else {
            clearSelectionWithoutStatusUpdate();
        }
        Platform.runLater(() -> requestFocusForInlineRenameSession(session));
    }

    private void finalizeInlineRenameCommitSuccess(InlineRenameSession session, Path committedPath) {
        finalizeInlineRenameCommitSuccess(session, committedPath, false);
    }

    private void finalizeInlineRenameCommitSuccess(InlineRenameSession session,
                                                   Path committedPath,
                                                   boolean preserveViewport) {
        if (session == null) {
            return;
        }
        session.awaitingCompletion = false;
        session.pendingResultPath = null;
        clearPendingInlineRenameDraft();
        if (activeInlineRenameSession == session) {
            activeInlineRenameSession = null;
        }
        if (committedPath != null) {
            if (session.surface == InlineRenameSurface.TREE) {
                expandAndSelectFolder(committedPath);
                Platform.runLater(() -> requestFocusForInlineRenameSession(session));
            } else {
                applyExplorerPathSelection(java.util.Set.of(committedPath), committedPath);
                if (preserveViewport) {
                    restoreInlineRenameCommitViewportAndReveal(session, committedPath, true);
                    Platform.runLater(() -> requestFocusForInlineRenameSession(session));
                } else {
                    scheduleExplorerPathVisibilityStabilization(committedPath, true);
                    Platform.runLater(() -> requestFocusForInlineRenameSession(session));
                }
            }
        } else {
            restoreInlineRenameSessionSelectionAndFocus(session);
        }
        armInlineRenameFocusGuard();
    }

    private void handleInlineRenameOperationFailed(InlineRenameSession session, Path source, String requestedName, String statusMessage) {
        pendingInlineRenameSelectionPath = null;
        pendingInlineRenameSelectionIndex = -1;
        if (session != null) {
            session.awaitingCompletion = false;
            session.pendingResultPath = null;
            activeInlineRenameSession = session;
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            setStatus(statusMessage);
        }
        if (source != null && Files.exists(source)) {
            retryInlineRename(source, requestedName, true);
            return;
        }
        if (session != null) {
            restoreInlineRenameSessionSelectionAndFocus(session);
            if (activeInlineRenameSession == session) {
                activeInlineRenameSession = null;
            }
        }
    }

    private void bindInlineRenameOperationHandle(com.fileexplorer.service.ops.OperationHandle handle,
                                                 InlineRenameSession session,
                                                 Path source,
                                                 String requestedName) {
        if (handle == null) {
            refresh();
            return;
        }
        handle.statusProperty().addListener(new javafx.beans.value.ChangeListener<com.fileexplorer.service.ops.OperationStatus>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends com.fileexplorer.service.ops.OperationStatus> observable,
                                com.fileexplorer.service.ops.OperationStatus oldValue,
                                com.fileexplorer.service.ops.OperationStatus newValue) {
                if (newValue == null) {
                    return;
                }
                switch (newValue) {
                    case COMPLETED -> {
                        observable.removeListener(this);
                        Platform.runLater(() -> {
                            if (session != null && session.awaitingCompletion) {
                                refresh();
                            }
                        });
                    }
                    case FAILED -> {
                        observable.removeListener(this);
                        Platform.runLater(() -> handleInlineRenameOperationFailed(session, source, requestedName, "Rename failed."));
                    }
                    case CANCELLED -> {
                        observable.removeListener(this);
                        Platform.runLater(() -> handleInlineRenameOperationFailed(session, source, requestedName, "Rename cancelled."));
                    }
                    default -> {
                    }
                }
            }
        });
    }

    private void restoreInlineRenameSessionAfterRefreshIfNeeded(Path directory) {
        if (pendingInlineRenameRestoreSession == null) {
            return;
        }
        InlineRenameSession session = pendingInlineRenameRestoreSession;
        if (directory != null && session.sourcePath != null && session.sourcePath.getParent() != null
                && !java.util.Objects.equals(directory, session.sourcePath.getParent())) {
            return;
        }
        pendingInlineRenameRestoreSession = null;
        Platform.runLater(() -> restoreInlineRenameSessionSelectionAndFocus(session));
    }

    private void finalizeAwaitingInlineRenameCommitIfPresent(Path directory, java.util.List<com.fileexplorer.model.FileItem> listing) {
        InlineRenameSession session = activeInlineRenameSession;
        if (session == null || !session.awaitingCompletion || session.pendingResultPath == null || directory == null || listing == null) {
            return;
        }
        Path committedPath = session.pendingResultPath;
        Path parent = committedPath.getParent();
        if (parent != null && !java.util.Objects.equals(directory, parent)) {
            return;
        }
        for (com.fileexplorer.model.FileItem item : listing) {
            if (item != null && java.util.Objects.equals(committedPath, item.path())) {
                Platform.runLater(() -> finalizeInlineRenameCommitSuccess(session, committedPath, true));
                return;
            }
        }
    }

    private void reopenInlineRenameEditor(Path source) {
        if (source == null) {
            return;
        }
        InlineRenameSession session = activeInlineRenameSession;
        if (session != null && session.surface == InlineRenameSurface.TREE) {
            expandAndSelectFolder(source);
            TreeItem<Path> selectedItem = folderTree != null && folderTree.getSelectionModel() != null
                    ? folderTree.getSelectionModel().getSelectedItem()
                    : null;
            if (selectedItem != null && java.util.Objects.equals(source, selectedItem.getValue())) {
                beginTreeInlineRename(selectedItem);
                return;
            }
        }
        beginTableInlineRename(source);
    }

    private void cancelInlineRenameSession(Path source) {
        clearPendingInlineRenameDraft();
        InlineRenameSession session = activeInlineRenameSession;
        activeInlineRenameSession = null;
        clearInlineRenameTargets();
        armInlineRenameFocusGuard();
        if (session != null && session.kind == InlineRenameSessionKind.CREATE_NEW && source != null) {
            if (session.originatingCommandId != null && context != null && context.commandManager() != null) {
                try {
                    context.commandManager().discardUndoCommand(session.originatingCommandId);
                } catch (Exception ex) {
                    LOG.log(Level.FINE, "Failed to discard transient create command after rename cancel", ex);
                }
            }
            try {
                Files.deleteIfExists(source);
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Failed to discard transient created item during rename cancel", ex);
            }
            pendingInlineRenameSelectionPath = null;
            pendingInlineRenameSelectionIndex = -1;
            pendingInlineRenameRestoreSession = session;
            refresh();
            return;
        }
        if (session != null) {
            restoreInlineRenameSessionSelectionAndFocus(session);
            return;
        }
        Platform.runLater(() -> restoreFocusToTablePath(source));
    }

    private void refreshActiveFolderSurface() {
        hideExplorerTransientUi();
        clearInlineRenameTargets();
        refresh();
        Platform.runLater(this::refreshActiveSelectionPresentation);
    }

    private void queueInlineRenameForCreatedPath(Path path, String originatingCommandId) {
        if (path == null) {
            return;
        }
        pendingCreatedInlineRenameSession = captureInlineRenameSession(path,
                InlineRenameSessionKind.CREATE_NEW,
                resolveInlineRenameSurfaceForCurrentView());
        if (pendingCreatedInlineRenameSession != null) {
            pendingCreatedInlineRenameSession.originatingCommandId = originatingCommandId;
        }
        activeInlineRenameSession = null;
        pendingCreateAndRenamePath = path;
        pendingInlineRenameSelectionPath = path;
        pendingInlineRenameSelectionIndex = -1;
        pendingRestoreSelection = true;
        pendingReselectPath = path;
        pendingReselectIndex = -1;
        pendingReselectPreferIndexOnMissing = false;
    }

    private void rememberPendingInlineRenameDraft(Path path, String text, boolean selectAll) {
        pendingInlineRenameDraftPath = path;
        pendingInlineRenameDraftText = text;
        pendingInlineRenameDraftSelectAll = selectAll;
    }

    private void clearPendingInlineRenameDraft() {
        pendingInlineRenameDraftPath = null;
        pendingInlineRenameDraftText = null;
        pendingInlineRenameDraftSelectAll = false;
    }

    private String resolveInlineRenameInitialText(Path path, String fallbackText) {
        if (path != null && java.util.Objects.equals(path, pendingInlineRenameDraftPath) && pendingInlineRenameDraftText != null) {
            return pendingInlineRenameDraftText;
        }
        return fallbackText == null ? "" : fallbackText;
    }

    private boolean shouldSelectAllInlineRenameText(Path path) {
        return path != null && java.util.Objects.equals(path, pendingInlineRenameDraftPath) && pendingInlineRenameDraftSelectAll;
    }

    private void beginInlineRenameEditTracking(Path path) {
        inlineRenameEditTrackingPath = path;
        inlineRenameExplicitFullNameEdit = false;
    }

    private void clearInlineRenameEditTracking() {
        inlineRenameEditTrackingPath = null;
        inlineRenameExplicitFullNameEdit = false;
    }

    private boolean isExplicitFullNameEditRequested(Path path) {
        return path != null
                && java.util.Objects.equals(path, inlineRenameEditTrackingPath)
                && inlineRenameExplicitFullNameEdit;
    }

    private boolean isWholeTextSelection(TextField renameField) {
        if (renameField == null) {
            return false;
        }
        javafx.scene.control.IndexRange selection = renameField.getSelection();
        String text = renameField.getText();
        int length = text == null ? 0 : text.length();
        return selection != null && selection.getStart() == 0 && selection.getEnd() == length && length > 0;
    }

    private void captureExplicitFullNameEditIntent(Path path, TextField renameField, KeyEvent event) {
        if (path == null || renameField == null || event == null) {
            return;
        }
        if (!java.util.Objects.equals(path, inlineRenameEditTrackingPath)) {
            return;
        }
        if (!isWholeTextSelection(renameField)) {
            return;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE) {
            inlineRenameExplicitFullNameEdit = true;
        }
    }

    private void captureExplicitFullNameEditIntent(Path path, TextField renameField, String typedText) {
        if (path == null || renameField == null) {
            return;
        }
        if (!java.util.Objects.equals(path, inlineRenameEditTrackingPath)) {
            return;
        }
        if (!isWholeTextSelection(renameField)) {
            return;
        }
        if (typedText == null || typedText.isEmpty()) {
            return;
        }
        if (typedText.chars().allMatch(Character::isISOControl)) {
            return;
        }
        inlineRenameExplicitFullNameEdit = true;
    }

    private boolean isIconInlineRenameTarget(Path path) {
        return isIconMode(viewMode) && path != null && java.util.Objects.equals(path, inlineRenameTablePath);
    }

    private TextField createExplorerInlineRenameField(Path path, String initialText) {
        String resolvedText = resolveInlineRenameInitialText(path, initialText);
        TextField renameField = new TextField(resolvedText);
        renameField.getStyleClass().add("explorer-inline-rename-field");
        renameField.setMaxWidth(Double.MAX_VALUE);
        renameField.setOnAction(e -> commitInlineRename(path, renameField.getText()));
        final boolean[] suppressFocusCommit = {false};
        renameField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV && java.util.Objects.equals(path, inlineRenameTablePath) && !suppressFocusCommit[0]) {
                if (isInlineRenameFocusGuardActive()) {
                    Platform.runLater(() -> {
                        if (!java.util.Objects.equals(path, inlineRenameTablePath) || renameField.getScene() == null) {
                            return;
                        }
                        renameField.requestFocus();
                        applyInlineRenameSelection(renameField, path, shouldSelectAllInlineRenameText(path));
                    });
                    return;
                }
                commitInlineRename(path, renameField.getText());
            }
        });
        renameField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            captureExplicitFullNameEditIntent(path, renameField, e);
            if (e.getCode() == KeyCode.ESCAPE) {
                suppressFocusCommit[0] = true;
                cancelInlineRenameFromExplorerSurface(path);
                e.consume();
            }
        });
        renameField.addEventFilter(KeyEvent.KEY_TYPED, e -> captureExplicitFullNameEditIntent(path, renameField, e.getCharacter()));
        Platform.runLater(() -> {
            renameField.requestFocus();
            applyInlineRenameSelection(renameField, path, shouldSelectAllInlineRenameText(path));
        });
        return renameField;
    }

    private void cancelInlineRenameFromExplorerSurface(Path source) {
        cancelInlineRenameSession(source);
    }

    private void applyInlineRenameSelection(TextField renameField, Path path) {
        applyInlineRenameSelection(renameField, path, false);
    }

    private void applyInlineRenameSelection(TextField renameField, Path path, boolean selectAll) {
        if (renameField == null) {
            return;
        }
        String text = renameField.getText();
        if (text == null) {
            renameField.selectAll();
            return;
        }
        if (selectAll) {
            renameField.selectAll();
            return;
        }
        int end = resolveInlineRenameSelectionEnd(path, text);
        renameField.selectRange(0, Math.max(0, end));
    }

    private int resolveInlineRenameSelectionEnd(Path path, String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return 0;
        }
        if (path == null || isDirectoryPath(path)) {
            return displayName.length();
        }
        int dotIndex = displayName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return displayName.length();
        }
        if (dotIndex == displayName.length() - 1) {
            return displayName.length();
        }
        return dotIndex;
    }

    private boolean isBlankInlineRename(String requestedName) {
        return requestedName == null || requestedName.isBlank();
    }

    private boolean containsIllegalInlineRenameCharacters(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return false;
        }
        for (int i = 0; i < requestedName.length(); i++) {
            char ch = requestedName.charAt(i);
            if (ch < 32 || "\\/:*?\"<>|".indexOf(ch) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean endsWithInvalidInlineRenameSuffix(String requestedName) {
        if (requestedName == null || requestedName.isEmpty()) {
            return false;
        }
        char last = requestedName.charAt(requestedName.length() - 1);
        return Character.isWhitespace(last) || last == '.';
    }

    private boolean isReservedInlineRenameName(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return false;
        }
        String candidate = requestedName;
        int dotIndex = candidate.indexOf('.');
        if (dotIndex > 0) {
            candidate = candidate.substring(0, dotIndex);
        }
        String upper = candidate.toUpperCase(java.util.Locale.ROOT);
        return switch (upper) {
            case "CON", "PRN", "AUX", "NUL",
                 "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                 "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" -> true;
            default -> false;
        };
    }

    private String extensionForInlineRename(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex);
    }

    private String normalizeInlineRenameRequestedName(Path source, String requestedName) {
        String normalized = requestedName == null ? "" : requestedName;
        if (normalized.isEmpty()) {
            return normalized;
        }
        if (source != null && !isDirectoryPath(source) && !normalized.contains(".") && !isExplicitFullNameEditRequested(source)) {
            String extension = extensionForInlineRename(source);
            if (!extension.isEmpty()) {
                normalized = normalized + extension;
            }
        }
        return normalized;
    }

    private void retryInlineRename(Path source) {
        if (source == null) {
            return;
        }
        Platform.runLater(() -> reopenInlineRenameEditor(source));
    }

    private void retryInlineRename(Path source, String draftText, boolean selectAll) {
        if (source == null) {
            return;
        }
        rememberPendingInlineRenameDraft(source, draftText, selectAll);
        Platform.runLater(() -> reopenInlineRenameEditor(source));
    }

    private Path nextAvailableCreatedPath(Path directory, String baseName, String duplicatePattern, String extension) {
        if (directory == null || baseName == null || baseName.isBlank()) {
            return null;
        }
        String normalizedExtension = extension == null ? "" : extension;
        Path candidate = directory.resolve(baseName + normalizedExtension);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        for (int i = 2; i <= 999; i++) {
            Path next = directory.resolve(String.format(duplicatePattern, i) + normalizedExtension);
            if (!Files.exists(next)) {
                return next;
            }
        }
        return candidate;
    }

    private void createNewTextDocument() {
        LogSupport.enter(LOG, "createNewTextDocument");
        Path dir = resolveActiveDirectoryForShellCommands();
        if (dir == null) {
            setStatus("No folder available for new text document.");
            return;
        }
        Path target = nextAvailableCreatedPath(dir, "New Text Document", "New Text Document (%d)", ".txt");
        if (target == null) {
            setStatus("Failed to resolve a name for the new text document.");
            return;
        }
        try {
            Files.writeString(
                    target,
                    "",
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE);
            queueInlineRenameForCreatedPath(target, null);
            refresh();
            setStatus("Created: " + target.getFileName());
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to create new text document", ex);
            setStatus("Failed to create text document.");
        }
    }

    private void performBackgroundPasteIntoCurrentDirectory() {
        hideExplorerTransientUi();
        pasteIntoCurrentDirectory();
    }

    private void openCurrentFolderProperties() {
        Path dir = resolveActiveDirectoryForShellCommands();
        if (dir == null) {
            setStatus("No folder available.");
            return;
        }
        hideExplorerTransientUi();
        openPropertiesForPath(dir);
    }

    private void createNewFolderFromBackgroundMenu() {
        hideExplorerTransientUi();
        createNewFolder();
    }

    private void createNewTextDocumentFromBackgroundMenu() {
        hideExplorerTransientUi();
        createNewTextDocument();
    }

    private javafx.scene.control.Menu buildFileViewBackgroundViewMenu() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("View");
        menu.getStyleClass().add("explorer-flyout-submenu");
        fileViewBackgroundViewModeItems = new java.util.EnumMap<>(ViewMode.class);
        javafx.scene.control.ToggleGroup toggleGroup = new javafx.scene.control.ToggleGroup();
        for (ViewMode mode : java.util.List.of(
                ViewMode.EXTRA_LARGE_ICONS,
                ViewMode.LARGE_ICONS,
                ViewMode.MEDIUM_ICONS,
                ViewMode.SMALL_ICONS,
                ViewMode.LIST,
                ViewMode.DETAILS,
                ViewMode.TILES,
                ViewMode.CONTENT)) {
            javafx.scene.control.RadioMenuItem item = new javafx.scene.control.RadioMenuItem(viewModeLabel(mode));
            item.getStyleClass().add("explorer-menu-item");
            item.setToggleGroup(toggleGroup);
            item.setOnAction(e -> setViewMode(mode));
            fileViewBackgroundViewModeItems.put(mode, item);
            menu.getItems().add(item);
        }
        return menu;
    }

    private javafx.scene.control.Menu buildFileViewBackgroundSortMenu() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Sort by");
        menu.getStyleClass().add("explorer-flyout-submenu");
        fileViewBackgroundSortItems = new java.util.EnumMap<>(SortKey.class);
        javafx.scene.control.ToggleGroup toggleGroup = new javafx.scene.control.ToggleGroup();
        java.util.Map<SortKey, String> labels = java.util.Map.of(
                SortKey.NAME, "Name",
                SortKey.MODIFIED, "Date modified",
                SortKey.TYPE, "Type",
                SortKey.SIZE, "Size");
        for (SortKey key : java.util.List.of(SortKey.NAME, SortKey.MODIFIED, SortKey.TYPE, SortKey.SIZE)) {
            javafx.scene.control.RadioMenuItem item = new javafx.scene.control.RadioMenuItem(labels.getOrDefault(key, key.name()));
            item.getStyleClass().add("explorer-menu-item");
            item.setToggleGroup(toggleGroup);
            item.setOnAction(e -> setSortKeyFromBackgroundMenu(key));
            fileViewBackgroundSortItems.put(key, item);
            menu.getItems().add(item);
        }
        return menu;
    }

    private javafx.scene.control.Menu buildFileViewBackgroundGroupMenu() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Group by");
        menu.getStyleClass().add("explorer-flyout-submenu");
        javafx.scene.control.MenuItem disabledItem = createExplorerMenuItem("None", null, () -> setStatus("Group by: not implemented yet."));
        disabledItem.setDisable(true);
        menu.getItems().add(disabledItem);
        return menu;
    }

    private javafx.scene.control.Menu buildFileViewBackgroundNewMenu() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("New");
        menu.getStyleClass().add("explorer-flyout-submenu");
        fileViewBackgroundNewFolderItem = createExplorerMenuItem("Folder", "", this::createNewFolderFromBackgroundMenu);
        fileViewBackgroundNewTextDocumentItem = createExplorerMenuItem("Text Document", "", this::createNewTextDocumentFromBackgroundMenu);
        fileViewBackgroundNewUnsupportedItem = createExplorerMenuItem("Bitmap image", "", () -> setStatus("Bitmap image: not implemented yet."));
        fileViewBackgroundNewUnsupportedItem.setDisable(true);
        menu.getItems().addAll(
                fileViewBackgroundNewFolderItem,
                fileViewBackgroundNewTextDocumentItem,
                createExplorerSeparator(),
                fileViewBackgroundNewUnsupportedItem);
        return menu;
    }

    private void syncFileViewBackgroundMenuState() {
        syncFileViewBackgroundMenuState(captureExplorerCommandStateSnapshot());
    }

    private void syncFileViewBackgroundMenuState(ExplorerCommandStateSnapshot snapshot) {
        ExplorerCommandStateSnapshot effectiveSnapshot = snapshot == null ? captureExplorerCommandStateSnapshot() : snapshot;
        if (fileViewBackgroundUndoItem != null) {
            setExplorerMenuItemLabel(fileViewBackgroundUndoItem, effectiveSnapshot.undoMenuLabel);
            fileViewBackgroundUndoItem.setDisable(!effectiveSnapshot.canUndo);
        }
        if (fileViewBackgroundPasteItem != null) {
            setExplorerMenuItemLabel(fileViewBackgroundPasteItem, "Paste");
            fileViewBackgroundPasteItem.setDisable(!effectiveSnapshot.canPaste);
        }
        if (fileViewBackgroundPasteShortcutItem != null) {
            setExplorerMenuItemLabel(fileViewBackgroundPasteShortcutItem, "Paste shortcut");
            fileViewBackgroundPasteShortcutItem.setDisable(true);
        }
        if (fileViewBackgroundNewFolderItem != null) {
            setExplorerMenuItemLabel(fileViewBackgroundNewFolderItem, "Folder");
            fileViewBackgroundNewFolderItem.setDisable(!effectiveSnapshot.hasDirectory);
        }
        if (fileViewBackgroundNewTextDocumentItem != null) {
            setExplorerMenuItemLabel(fileViewBackgroundNewTextDocumentItem, "Text Document");
            fileViewBackgroundNewTextDocumentItem.setDisable(!effectiveSnapshot.hasDirectory);
        }
        if (fileViewBackgroundNewUnsupportedItem != null) {
            setExplorerMenuItemLabel(fileViewBackgroundNewUnsupportedItem, "Bitmap image");
            fileViewBackgroundNewUnsupportedItem.setDisable(true);
        }
        if (fileViewBackgroundSelectAllItem != null) {
            setExplorerMenuItemLabel(fileViewBackgroundSelectAllItem, "Select all");
            fileViewBackgroundSelectAllItem.setDisable(!effectiveSnapshot.hasVisibleItems);
        }
        if (fileViewBackgroundRefreshItem != null) {
            setExplorerMenuItemLabel(fileViewBackgroundRefreshItem, "Refresh");
            fileViewBackgroundRefreshItem.setDisable(!effectiveSnapshot.hasDirectory);
        }
        if (fileViewBackgroundPropertiesItem != null) {
            setExplorerMenuItemLabel(fileViewBackgroundPropertiesItem, "Properties");
            fileViewBackgroundPropertiesItem.setDisable(!effectiveSnapshot.hasDirectory);
        }
        if (fileViewBackgroundViewModeItems != null) {
            for (java.util.Map.Entry<ViewMode, javafx.scene.control.RadioMenuItem> entry : fileViewBackgroundViewModeItems.entrySet()) {
                entry.getValue().setSelected(entry.getKey() == effectiveSnapshot.viewMode);
            }
        }
        if (fileViewBackgroundSortItems != null) {
            for (java.util.Map.Entry<SortKey, javafx.scene.control.RadioMenuItem> entry : fileViewBackgroundSortItems.entrySet()) {
                entry.getValue().setSelected(entry.getKey() == effectiveSnapshot.sortKey);
            }
        }
        if (fileViewBackgroundGroupMenu != null) {
            fileViewBackgroundGroupMenu.setDisable(true);
        }
    }

    private void performBackgroundUndo() {
        try {
            if (context != null && context.commandManager() != null && context.commandManager().canUndo()) {
                com.fileexplorer.service.ops.command.CommandManager.ExecutedCommand undoCommand = peekUndoCommand();
                InlineRenameSession shellStateSession = captureShellCommandRefreshSessionForUndoRedo(undoCommand != null ? undoCommand.command() : null);
                context.commandManager().undo();
                applyShellStateRefreshPlanForUndoRedo(undoCommand != null ? undoCommand.command() : null,
                        ExplorerCommandAction.UNDO,
                        shellStateSession);
                setStatus("Undid last action.");
                syncExplorerContextMenuShellState();
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Undo failed", ex);
            setStatus("Undo failed.");
        }
    }

    private void showFileViewBackgroundContextMenu(Node requestedAnchor, double screenX, double screenY) {
        clearArmedExplorerItemContextMenu();
        markExplorerFileViewContextMenuPending();
        suppressExplorerMetadataPopupForMillis(1500L);
        Node anchor = resolveExplorerContextMenuAnchor(requestedAnchor);
        if (anchor == null) {
            clearExplorerFileViewContextMenuPending();
            return;
        }
        if (fileViewBackgroundMenu == null) {
            fileViewBackgroundMenu = createExplorerContextMenu("file-view-background");
            fileViewBackgroundViewMenu = buildFileViewBackgroundViewMenu();
            fileViewBackgroundSortMenu = buildFileViewBackgroundSortMenu();
            fileViewBackgroundGroupMenu = buildFileViewBackgroundGroupMenu();
            fileViewBackgroundUndoItem = createExplorerMenuItem("Undo", "", this::performBackgroundUndo);
            fileViewBackgroundPasteItem = createExplorerMenuItem("Paste", "", this::performBackgroundPasteIntoCurrentDirectory);
            fileViewBackgroundPasteShortcutItem = createExplorerMenuItem("Paste shortcut", "", () -> setStatus("Paste shortcut: not implemented yet."));
            fileViewBackgroundNewMenu = buildFileViewBackgroundNewMenu();
            fileViewBackgroundSelectAllItem = createExplorerMenuItem("Select all", "", this::selectAll);
            fileViewBackgroundRefreshItem = createExplorerMenuItem("Refresh", "", this::refreshActiveFolderSurface);
            fileViewBackgroundPropertiesItem = createExplorerMenuItem("Properties", "", this::openCurrentFolderProperties);
            fileViewBackgroundMenu.getItems().addAll(
                    fileViewBackgroundViewMenu,
                    fileViewBackgroundSortMenu,
                    fileViewBackgroundGroupMenu,
                    createExplorerSeparator(),
                    fileViewBackgroundUndoItem,
                    fileViewBackgroundPasteItem,
                    fileViewBackgroundPasteShortcutItem,
                    createExplorerSeparator(),
                    fileViewBackgroundNewMenu,
                    fileViewBackgroundSelectAllItem,
                    createExplorerSeparator(),
                    fileViewBackgroundRefreshItem,
                    createExplorerSeparator(),
                    fileViewBackgroundPropertiesItem);
            fileViewBackgroundMenu.setOnShowing(e -> {
                markExplorerFileViewContextMenuPending();
                suppressExplorerMetadataPopupForMillis(1500L);
                syncFileViewBackgroundMenuState();
            });
            fileViewBackgroundMenu.setOnShown(e -> {
                clearExplorerFileViewContextMenuPending();
                suppressExplorerMetadataPopupForMillis(300L);
            });
            fileViewBackgroundMenu.setOnHidden(e -> {
                clearExplorerFileViewContextMenuPending();
                suppressExplorerMetadataPopupForMillis(300L);
            });
        }
        if (fileOpsMenu != null) {
            logExplorerContextMenuHideRequest(fileOpsMenu, "background-menu-show-hides-file-ops-menu", null);
            fileOpsMenu.hide();
        }
        if (fileTable != null) {
            com.fileexplorer.ui.table.TableHeaderContextMenuInstaller.resetEphemeralHeaderState(fileTable);
        }
        logExplorerContextMenuLifecycle(fileViewBackgroundMenu, "show-request");
        logExplorerContextMenuHideRequest(fileViewBackgroundMenu, "showFileViewBackgroundContextMenu-reset-before-show", null);
        fileViewBackgroundMenu.hide();
        final Node finalAnchor = anchor;
        Platform.runLater(() -> {
            if (finalAnchor.getScene() == null) {
                clearExplorerFileViewContextMenuPending();
                return;
            }
            logExplorerContextMenuHideRequest(fileViewBackgroundMenu, "showFileViewBackgroundContextMenu-replace-before-show", null);
            fileViewBackgroundMenu.hide();
            fileViewBackgroundMenu.show(finalAnchor, screenX, screenY);
        });
    }

    private Node getActiveFileOpsMenuAnchor() {
        if (root != null && root.getScene() != null) {
            return root;
        }
        if (boundScene != null && boundScene.getRoot() != null) {
            return boundScene.getRoot();
        }
        if (viewHost != null && viewHost.getScene() != null) {
            return viewHost;
        }
        if (viewMode == ViewMode.DETAILS) {
            if (fileTable != null && fileTable.getScene() != null) {
                return fileTable;
            }
            if (detailsViewShell != null && detailsViewShell.getScene() != null) {
                return detailsViewShell;
            }
        }
        if (virtualIconGridView != null && virtualIconGridView.isVisible() && virtualIconGridView.getScene() != null) {
            return virtualIconGridView;
        }
        if (virtualIconListView != null && virtualIconListView.isVisible() && virtualIconListView.getScene() != null) {
            return virtualIconListView;
        }
        if (iconFlow != null && iconFlow.isVisible() && iconFlow.getScene() != null) {
            return iconFlow;
        }
        if (iconScroll != null && iconScroll.isVisible() && iconScroll.getScene() != null) {
            return iconScroll;
        }
        return fileTable != null && fileTable.getScene() != null ? fileTable : null;
    }

    /**
     * Phase 3.6.0: Table context menu + keyboard shortcuts for file operations.
     */
    private void configureFileOperationsUi() {
        if (fileTable == null) return;
        // Use a filter so we can ignore header clicks (header menu is handled separately).
        fileTable.addEventFilter(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, ev -> {
            try {
                if (shouldSuppressExplorerItemContextMenuRequestedEvent()) {
                    ev.consume();
                    return;
                }
                javafx.scene.Node target = (ev.getPickResult() != null) ? ev.getPickResult().getIntersectedNode() : null;
                if (isTableHeaderContext(ev)) {
                    return;
                }
                if (isTableRowContext(ev)) {
                    if (handleExplorerItemContextMenuRequest(target, ev.getScreenX(), ev.getScreenY())) {
                        ev.consume();
                    }
                    return;
                }
                showFileViewBackgroundContextMenu(target != null ? target : fileTable, ev.getScreenX(), ev.getScreenY());
                ev.consume();
            } catch (Throwable ignored) {
                // keep context menu best-effort
            }
        });
        if (fileTable.getSelectionModel() != null) {
            fileTable.getSelectionModel().getSelectedItems().addListener(
                    (javafx.collections.ListChangeListener<FileItem>) change -> syncExplorerContextMenuShellState());
        }
        fileTable.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                Path restorePath = inlineRenameTablePath;
                hideExplorerTransientUi();
                if (restorePath != null) {
                    clearInlineRenameTargets();
                    Platform.runLater(() -> restoreFocusToTablePath(restorePath));
                }
                e.consume();
                return;
            }
            if (e.getCode() == javafx.scene.input.KeyCode.CONTEXT_MENU || (e.getCode() == javafx.scene.input.KeyCode.F10 && e.isShiftDown())) {
                if (getFocusedOrSelectedIndex() >= 0) {
                    showFileOpsContextMenuForFocusedRow();
                } else {
                    Bounds tableBounds = fileTable.localToScreen(fileTable.getBoundsInLocal());
                    if (tableBounds != null) {
                        showFileViewBackgroundContextMenu(fileTable, tableBounds.getMinX() + 48.0, tableBounds.getMinY() + 48.0);
                    }
                }
                e.consume();
                return;
            }
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.C) {
                copySelection(false);
                e.consume();
                return;
            }
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.X) {
                copySelection(true);
                e.consume();
                return;
            }
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.V) {
                pasteIntoCurrentDirectory();
                e.consume();
                return;
            }
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE) {
                deleteSelection(e.isShiftDown());
                e.consume();
                return;
            }
            if (e.getCode() == javafx.scene.input.KeyCode.F2) {
                renameSelection();
                e.consume();
            }
        });
    }
    /** True if the ContextMenuEvent originated from the TableView header area. */
    private boolean isTableHeaderContext(javafx.scene.input.ContextMenuEvent ev) {
        javafx.scene.Node target = (ev.getPickResult() != null) ? ev.getPickResult().getIntersectedNode() : null;
        if (target == null) return false;
        for (javafx.scene.Node n = target; n != null; n = n.getParent()) {
            String cn = n.getClass().getName();
            if (cn.contains("TableColumnHeader") || cn.contains("TableHeaderRow")) return true;
            if (n == fileTable) break;
        }
        return false;
    }

    private boolean isTableRowContext(javafx.scene.input.ContextMenuEvent ev) {
        javafx.scene.Node target = (ev.getPickResult() != null) ? ev.getPickResult().getIntersectedNode() : null;
        if (target == null) return false;
        for (javafx.scene.Node n = target; n != null; n = n.getParent()) {
            if (n instanceof TableRow<?>) {
                return true;
            }
            if (n == fileTable) {
                break;
            }
        }
        return false;
    }
/**
 * copySelection.
 *
 * @param cut TODO
 */
    private void copySelection(boolean cut) {
        if (fileTable == null) return;
        java.util.List<com.fileexplorer.model.FileItem> sel = new java.util.ArrayList<>(fileTable.getSelectionModel().getSelectedItems());
        clipboardPaths.clear();
        for (com.fileexplorer.model.FileItem it : sel) {
            if (it != null && it.path() != null) {
                clipboardPaths.add(it.path());
            }
        }
        clipboardCut = cut;
        if (statusLabel != null) {
            statusLabel.setText((cut ? "Cut" : "Copied") + " " + clipboardPaths.size() + " item(s)");
        }
        syncExplorerContextMenuShellState();
    }
/**
 * pasteIntoCurrentDirectory.
 *
 */
    private void pasteIntoCurrentDirectory() {
        if (context == null || fileOperationService == null) return;
        java.nio.file.Path targetDir = resolveActiveDirectoryForShellCommands();
        if (targetDir == null) return;
        if (clipboardPaths.isEmpty()) return;
        com.fileexplorer.service.ops.FileOperationType type =
                clipboardCut ? com.fileexplorer.service.ops.FileOperationType.MOVE : com.fileexplorer.service.ops.FileOperationType.COPY;
        int n = clipboardPaths.size();
        String label = (clipboardCut ? "Move " : "Copy " ) + n + " item(s) -> " + targetDir;
        com.fileexplorer.service.ops.command.Command cmd;
        boolean overwrite = false;
        boolean skipConflicts = false;
        com.fileexplorer.service.ops.conflict.ConflictPolicyConfig policyOverride = null;
        // Build a preview command (policy is decided in the preview dialog if needed).
        if (clipboardCut) {
            cmd = new com.fileexplorer.service.ops.command.MoveCommand(label, java.util.List.copyOf(clipboardPaths), targetDir, false, false);
        } else {
            cmd = new com.fileexplorer.service.ops.command.CopyCommand(label, java.util.List.copyOf(clipboardPaths), targetDir, false, false);
        }
        if (cmd instanceof com.fileexplorer.service.ops.command.PreviewableCommand pc) {
            PreviewResult pr = showCommandPreviewDialog(pc.preview(), true);
            PreviewDecision d = pr.decision();
            policyOverride = pr.conflictPolicyOverride();
            if (d == PreviewDecision.CANCEL) {
                return;
            }
            overwrite = (d == PreviewDecision.RUN_OVERWRITE);
            skipConflicts = (d == PreviewDecision.RUN_SKIP);
        }
        // Rebuild the command if the policy changed.
        if (clipboardCut) {
            cmd = new com.fileexplorer.service.ops.command.MoveCommand(label, java.util.List.copyOf(clipboardPaths), targetDir, overwrite, skipConflicts, policyOverride);
        } else {
            cmd = new com.fileexplorer.service.ops.command.CopyCommand(label, java.util.List.copyOf(clipboardPaths), targetDir, overwrite, skipConflicts, policyOverride);
        }
        context.commandManager().execute(cmd);
        
        if (clipboardCut) {
            clipboardPaths.clear();
            clipboardCut = false;
        }
        syncExplorerContextMenuShellState();
    }
/**
 * deleteSelection.
 *
 * @param permanent TODO
 */
    private void deleteSelection(boolean permanent) {
    if (fileTable == null || fileOperationService == null) return;
    java.util.List<com.fileexplorer.model.FileItem> sel = new java.util.ArrayList<>(
            fileTable.getSelectionModel().getSelectedItems()
    );
    java.util.List<java.nio.file.Path> paths = new java.util.ArrayList<>();
    for (com.fileexplorer.model.FileItem it : sel) {
        if (it != null && it.path() != null) paths.add(it.path());
    }
    if (paths.isEmpty()) return;
    javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
    confirm.setTitle(permanent ? "Delete permanently" : "Delete");
    confirm.setHeaderText((permanent ? "Permanently delete " : "Move to Recycle Bin ") + paths.size() + " item(s)?");
    confirm.setContentText(permanent
            ? "This will permanently delete the selected item(s)."
            : "This will move the selected item(s) to the Recycle Bin."
    );
    java.util.Optional<javafx.scene.control.ButtonType> res = confirm.showAndWait();
    if (res.isEmpty() || res.get() != javafx.scene.control.ButtonType.OK) return;
    String label = "Delete " + paths.size() + " item(s)" + (permanent ? " (Permanent)" : " (Recycle Bin)");
com.fileexplorer.service.ops.command.Command cmd =
        new com.fileexplorer.service.ops.command.DeleteCommand(label, paths, !permanent);
if (cmd instanceof com.fileexplorer.service.ops.command.PreviewableCommand pc) {
    if (showCommandPreviewDialog(pc.preview(), false).decision() == PreviewDecision.CANCEL) {
        return;
    }
}
context.commandManager().execute(cmd);
    }
    
// ---------------------------------------------------------------------
// Phase 4.1.0: Dry-run preview for commands (best-effort)
// ---------------------------------------------------------------------
private enum PreviewDecision { CANCEL, RUN, RUN_SKIP, RUN_OVERWRITE, RUN_ASK }
private record PreviewResult(PreviewDecision decision,
                             com.fileexplorer.service.ops.conflict.ConflictPolicyConfig conflictPolicyOverride) {}
/**
 * showCommandPreviewDialog.
 *
 * @param p TODO
 * @param allowConflictPolicy TODO
 * @return TODO
 */
private PreviewResult showCommandPreviewDialog(com.fileexplorer.service.ops.command.CommandPreview p, boolean allowConflictPolicy) {
    javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
    a.setTitle(p.title());
    a.setHeaderText(p.summary());
    javafx.stage.Window owner = (root != null && root.getScene() != null) ? root.getScene().getWindow() : null;
    if (owner != null) {
        a.initOwner(owner);
    }
    com.fileexplorer.util.DialogTheme.apply(a, owner);
    javafx.scene.layout.VBox contentBox = new javafx.scene.layout.VBox(8);
    contentBox.setFillWidth(true);
    // Phase 4.2.1: optional per-operation policy override (snapshot at dialog close)
    final java.util.concurrent.atomic.AtomicReference<com.fileexplorer.service.ops.conflict.ConflictPolicyConfig> policyRef =
            new java.util.concurrent.atomic.AtomicReference<>(null);
    boolean hasConflicts = p.conflicts() != null && !p.conflicts().isEmpty();
    javafx.scene.control.ButtonType cancel = new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
    javafx.scene.control.ButtonType run;
    javafx.scene.control.ButtonType runSkip;
    javafx.scene.control.ButtonType runOverwrite;
    if (allowConflictPolicy && hasConflicts) {
        runSkip = new javafx.scene.control.ButtonType("Run (skip conflicts)", javafx.scene.control.ButtonBar.ButtonData.NO);
        runOverwrite = new javafx.scene.control.ButtonType("Run (overwrite all)", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType runAsk = new javafx.scene.control.ButtonType("Run (ask per conflict)", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        a.getButtonTypes().setAll(runOverwrite, runAsk, runSkip, cancel);
    } else {
        run = new javafx.scene.control.ButtonType("Run", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        a.getButtonTypes().setAll(run, cancel);
    }
    if (allowConflictPolicy && hasConflicts) {
        javafx.scene.control.CheckBox overridePolicyCheck = new javafx.scene.control.CheckBox("Override conflict policy for this operation");
        javafx.scene.control.ComboBox<com.fileexplorer.service.ops.conflict.ConflictPolicyProfile> profileCombo = new javafx.scene.control.ComboBox<>();
        profileCombo.getItems().setAll(com.fileexplorer.service.ops.conflict.ConflictPolicyProfile.values());
        profileCombo.getSelectionModel().select(context != null && context.operationQueueService() != null
                ? context.operationQueueService().getConflictPolicyProfile()
                : com.fileexplorer.service.ops.conflict.ConflictPolicyProfile.DEFAULT);
        javafx.scene.control.ComboBox<com.fileexplorer.service.ops.conflict.ConflictPolicyAction> actionCombo = new javafx.scene.control.ComboBox<>();
        actionCombo.getItems().setAll(
                com.fileexplorer.service.ops.conflict.ConflictPolicyAction.PROMPT,
                com.fileexplorer.service.ops.conflict.ConflictPolicyAction.SKIP,
                com.fileexplorer.service.ops.conflict.ConflictPolicyAction.OVERWRITE,
                com.fileexplorer.service.ops.conflict.ConflictPolicyAction.RENAME
        );
        actionCombo.getSelectionModel().select(context != null && context.operationQueueService() != null
                ? context.operationQueueService().getCustomConflictDefaultAction()
                : com.fileexplorer.service.ops.conflict.ConflictPolicyAction.PROMPT);
        javafx.scene.control.Label profLabel = new javafx.scene.control.Label("Profile:");
        javafx.scene.control.Label actLabel = new javafx.scene.control.Label("CUSTOM action:");
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(8, overridePolicyCheck, new javafx.scene.layout.Region());
        javafx.scene.layout.HBox.setHgrow(row.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox row2 = new javafx.scene.layout.HBox(8, profLabel, profileCombo, actLabel, actionCombo);
        row2.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        // Enable custom action only when CUSTOM is selected.
        Runnable updateEnable = () -> {
            boolean enabled = overridePolicyCheck.isSelected();
            profileCombo.setDisable(!enabled);
            com.fileexplorer.service.ops.conflict.ConflictPolicyProfile pSel = profileCombo.getValue();
            boolean custom = enabled && (pSel == com.fileexplorer.service.ops.conflict.ConflictPolicyProfile.CUSTOM);
            actionCombo.setDisable(!custom);
            actionCombo.setOpacity(custom ? 1.0 : 0.75);
        };
        overridePolicyCheck.selectedProperty().addListener((o, ov, nv) -> updateEnable.run());
        profileCombo.valueProperty().addListener((o, ov, nv) -> updateEnable.run());
        updateEnable.run();
        contentBox.getChildren().addAll(row, row2);
        // Snapshot override at close time.
        a.setOnHidden(ev -> {
            if (overridePolicyCheck.isSelected()) {
                com.fileexplorer.service.ops.conflict.ConflictPolicyProfile pp = profileCombo.getValue();
                com.fileexplorer.service.ops.conflict.ConflictPolicyAction aa = actionCombo.getValue();
                if (pp == null) pp = com.fileexplorer.service.ops.conflict.ConflictPolicyProfile.DEFAULT;
                if (aa == null) aa = com.fileexplorer.service.ops.conflict.ConflictPolicyAction.PROMPT;
                policyRef.set(new com.fileexplorer.service.ops.conflict.ConflictPolicyConfig(pp, aa));
            } else {
                policyRef.set(null);
            }
        });
    }
StringBuilder sb = new StringBuilder();
    if (p.warnings() != null && !p.warnings().isEmpty()) {
        sb.append("Warnings:\n");
        for (String w : p.warnings()) sb.append(" - ").append(w).append("\n");
        sb.append("\n");
    }
    if (p.conflicts() != null && !p.conflicts().isEmpty()) {
        sb.append("Conflicts (targets exist): ").append(p.conflicts().size()).append("\n");
        int max = Math.min(50, p.conflicts().size());
        for (int i = 0; i < max; i++) {
            sb.append(" - ").append(p.conflicts().get(i)).append("\n");
        }
        if (p.conflicts().size() > max) {
            sb.append(" ... (").append(p.conflicts().size() - max).append(" more)\n");
        }
        sb.append("\n");
    }
    if (p.items() != null && !p.items().isEmpty()) {
        sb.append("Items (sample): ").append(p.items().size()).append("\n");
        int max = Math.min(100, p.items().size());
        for (int i = 0; i < max; i++) {
            sb.append(" - ").append(p.items().get(i)).append("\n");
        }
        if (p.items().size() > max) {
            sb.append(" ... (").append(p.items().size() - max).append(" more)\n");
        }
    }
    javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(sb.toString());
    ta.setEditable(false);
    ta.setWrapText(false);
    ta.setPrefRowCount(18);
    a.getDialogPane().setContent(contentBox);
    a.getDialogPane().setExpandableContent(ta);
    a.getDialogPane().setExpanded(true);
    var res = a.showAndWait();
    com.fileexplorer.service.ops.conflict.ConflictPolicyConfig selectedPolicyOverride = policyRef.get();
    if (res.isEmpty() || res.get() == cancel) {
        return new PreviewResult(PreviewDecision.CANCEL, null);
    }
    if (allowConflictPolicy && hasConflicts) {
        String txt = res.get().getText();
        if (txt != null && txt.contains("skip")) return new PreviewResult(PreviewDecision.RUN_SKIP, selectedPolicyOverride);
        if (txt != null && txt.contains("overwrite")) return new PreviewResult(PreviewDecision.RUN_OVERWRITE, selectedPolicyOverride);
        if (txt != null && txt.contains("ask")) return new PreviewResult(PreviewDecision.RUN_ASK, selectedPolicyOverride);
        return new PreviewResult(PreviewDecision.RUN, selectedPolicyOverride);
    }
    return new PreviewResult(PreviewDecision.RUN, selectedPolicyOverride);
}
    private void initViewportRealizationDebounce() {
        if (viewportRealizationDebounce != null) {
            return;
        }
        long ms = Long.getLong("fileexplorer.realization.idleDebounceMs", 90L);
        viewportRealizationDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(Math.max(30L, ms)));
        viewportRealizationDebounce.setOnFinished(_ -> {
            noteViewportIdle();
            requestViewportScopedRealizationRefresh();
        });
    }

    private void noteDirectoryRealizationScopeChanged() {
        realizationDirectoryGeneration.incrementAndGet();
        advanceDetailsAsyncBindingEpoch();
        try {
            AsyncIconService.getInstance().cancelAll();
        } catch (Exception ignored) {
        }
        scheduleViewportScopedRealizationRefresh();
    }

    private long currentDetailsAsyncBindingEpoch() {
        return detailsAsyncBindingEpoch.get();
    }

    private long advanceDetailsAsyncBindingEpoch() {
        return detailsAsyncBindingEpoch.incrementAndGet();
    }

    private Path normalizeDirectoryScope(Path directory) {
        return directory == null ? null : directory.normalize();
    }

    private Path currentVisibleDirectoryScope() {
        return normalizeDirectoryScope(visibleDirectoryScope);
    }

    private void setVisibleDirectoryScope(Path directory) {
        visibleDirectoryScope = normalizeDirectoryScope(directory);
        publishDetailsRefreshContext();
    }

    private void publishDetailsRefreshContext() {
        if (fileTable == null) {
            return;
        }
        Path directoryScope = currentVisibleDirectoryScope();
        Path anchorPath = currentSelectionAnchorPathForRefresh();
        Path leadPath = currentSelectionLeadPathForRefresh();
        DetailsViewRefreshCoordinator.publishTableState(fileTable, directoryScope, anchorPath, leadPath);
    }

    private Path currentSelectionAnchorPathForRefresh() {
        Path directoryScope = currentVisibleDirectoryScope();
        Path anchorPath = iconSelectionAnchorPath;
        return isPathWithinDirectoryScope(anchorPath, directoryScope) ? anchorPath : null;
    }

    private Path currentSelectionLeadPathForRefresh() {
        if (fileTable == null || fileTable.getItems() == null) {
            return null;
        }
        Path directoryScope = currentVisibleDirectoryScope();
        Path leadPath = null;
        if (fileTable.getSelectionModel() != null) {
            int selectedIndex = fileTable.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < fileTable.getItems().size()) {
                FileItem leadItem = fileTable.getItems().get(selectedIndex);
                leadPath = leadItem == null ? null : leadItem.path();
            }
        }
        if (!isPathWithinDirectoryScope(leadPath, directoryScope)) {
            leadPath = getFocusedOrSelectedPath();
        }
        return isPathWithinDirectoryScope(leadPath, directoryScope) ? leadPath : null;
    }

    private void setExplorerSelectionAnchorPath(Path path) {
        iconSelectionAnchorPath = path;
        publishDetailsRefreshContext();
    }

    private boolean isPathWithinDirectoryScope(Path path, Path directoryScope) {
        Path normalizedScope = normalizeDirectoryScope(directoryScope);
        if (path == null || normalizedScope == null) {
            return false;
        }
        Path normalizedPath = path.normalize();
        return java.util.Objects.equals(normalizedPath, normalizedScope)
                || normalizedPath.startsWith(normalizedScope);
    }

    private java.util.List<Path> filterPathsToDirectoryScope(java.util.Collection<Path> paths, Path directoryScope) {
        java.util.ArrayList<Path> filtered = new java.util.ArrayList<>();
        if (paths == null) {
            return filtered;
        }
        for (Path path : paths) {
            if (path != null && isPathWithinDirectoryScope(path, directoryScope)) {
                filtered.add(path);
            }
        }
        return filtered;
    }

    private void clearExplorerPresentationSelectionState() {
        replaceDetailsPresentationSelectedPaths(java.util.Collections.emptySet());
        replaceIconPresentationSelectedPaths(java.util.Collections.emptySet());
        pendingExplorerMarqueeSelectionPaths.clear();
        pendingExplorerMarqueeFocusPath = null;
        if (viewMode == ViewMode.DETAILS) {
            syncVisibleDetailsHoverRows();
        } else if (isIconMode(viewMode)) {
            refreshVisibleIconTileSelectionState();
        }
    }

    private void noteViewportMotion(double deltaY) {
        recordScrollTelemetry(deltaY);
        viewportScheduler.markScrollActivity();
        if (deltaY < 0.0) {
            realizationScrollDirection.set(1L);
        } else if (deltaY > 0.0) {
            realizationScrollDirection.set(-1L);
        }
        try {
            AsyncIconService.getInstance().noteViewportMotion();
        } catch (Exception ignored) {
        }
        try {
            AsyncThumbnailService.getInstance().noteViewportMotion();
        } catch (Exception ignored) {
        }
        scheduleViewportScopedRealizationRefresh();
    }

    private void noteViewportIdle() {
        long burstStart = scrollTelemetryBurstStartNanos.getAndSet(0L);
        long lastMotion = scrollTelemetryLastMotionNanos.getAndSet(0L);
        long events = scrollTelemetryBurstEvents.getAndSet(0L);
        long maxGapNanos = scrollTelemetryMaxGapNanos.getAndSet(0L);
        try {
            AsyncIconService.getInstance().noteViewportIdle();
        } catch (Exception ignored) {
        }
        try {
            AsyncThumbnailService.getInstance().noteViewportIdle();
        } catch (Exception ignored) {
        }
        long settleLatencyMs = lastMotion <= 0L ? 0L : Math.max(0L, Math.round((System.nanoTime() - lastMotion) / 1_000_000.0));
        if (!LOG_SCROLL_TELEMETRY || burstStart == 0L || lastMotion == 0L || events <= 0L) {
            return;
        }
        long burstMs = Math.max(0L, Math.round((lastMotion - burstStart) / 1_000_000.0));
        long maxGapMs = Math.max(0L, Math.round(maxGapNanos / 1_000_000.0));
        ScrollVelocityBucket bucket = resolveScrollVelocityBucket();
        double velocity = scrollVelocityPixelsPerMs;
        LOG.info(() -> "[SCROLL] idle burstMs=" + burstMs
                + " events=" + events
                + " maxGapMs=" + maxGapMs
                + " settleLatencyMs=" + settleLatencyMs
                + " velocityPxPerMs=" + String.format(java.util.Locale.ROOT, "%.2f", velocity)
                + " bucket=" + bucket
                + " view=" + viewMode);
    }

    private void recordScrollTelemetry(double deltaY) {
        long now = System.nanoTime();
        long previous = scrollTelemetryLastMotionNanos.getAndSet(now);
        if (scrollTelemetryBurstStartNanos.get() == 0L) {
            scrollTelemetryBurstStartNanos.set(now);
        }
        scrollTelemetryBurstEvents.incrementAndGet();
        if (previous <= 0L) {
            return;
        }
        long gap = Math.max(0L, now - previous);
        scrollTelemetryMaxGapNanos.accumulateAndGet(gap, Math::max);
        updateScrollVelocityEstimate(deltaY, gap);
        long gapMs = Math.round(gap / 1_000_000.0);
        if (gapMs >= SCROLL_HITCH_LOG_THRESHOLD_MS) {
            LOG.info(() -> "[SCROLL] hitch gapMs=" + gapMs
                    + " deltaY=" + String.format(java.util.Locale.ROOT, "%.2f", deltaY)
                    + " velocityPxPerMs=" + String.format(java.util.Locale.ROOT, "%.2f", scrollVelocityPixelsPerMs)
                    + " bucket=" + resolveScrollVelocityBucket()
                    + " view=" + viewMode);
        }
    }

    private void initViewportSettlePassDebounce() {
        if (viewportSettlePassDebounce != null) {
            return;
        }
        viewportSettlePassDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(Math.max(60L, VIEWPORT_SETTLE_PASS_DELAY_MS)));
        viewportSettlePassDebounce.setOnFinished(_ -> runViewportSettlePass());
    }

    private void scheduleViewportSettlePass() {
        initViewportSettlePassDebounce();
        if (viewportSettlePassDebounce == null) {
            return;
        }
        if (javafx.application.Platform.isFxApplicationThread()) {
            viewportSettlePassDebounce.playFromStart();
        } else {
            javafx.application.Platform.runLater(() -> viewportSettlePassDebounce.playFromStart());
        }
    }

    private void runViewportSettlePass() {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(this::runViewportSettlePass);
            return;
        }
        scrollVelocityPixelsPerMs = 0.0;
        scrollVelocityBucket = ScrollVelocityBucket.SETTLE;
        if (tableItems == null || tableItems.isEmpty()) {
            return;
        }
        java.util.List<Path> selectedPaths = new java.util.ArrayList<>(getSelectedItems());
        Path focusPath = getFocusedOrSelectedPath();
        Path settleFocus = focusPath != null && selectedPaths.contains(focusPath)
                ? focusPath
                : iconSelectionAnchorPath != null && selectedPaths.contains(iconSelectionAnchorPath)
                ? iconSelectionAnchorPath
                : (!selectedPaths.isEmpty() ? selectedPaths.get(0) : focusPath);
        if (!selectedPaths.isEmpty() && settleFocus != null) {
            applyExplorerPathSelection(selectedPaths, settleFocus);
        } else if (settleFocus != null && viewMode == ViewMode.DETAILS) {
            restoreFocusToTablePath(settleFocus);
        }
        requestViewportScopedRealizationRefresh();
    }

    private void updateScrollVelocityEstimate(double deltaY, long gapNanos) {
        if (gapNanos <= 0L) {
            return;
        }
        double gapMs = Math.max(0.001, gapNanos / 1_000_000.0);
        double instantaneous = Math.abs(deltaY) / gapMs;
        double previous = scrollVelocityPixelsPerMs;
        double alpha = previous <= 0.0 ? 1.0 : 0.35;
        scrollVelocityPixelsPerMs = previous <= 0.0 ? instantaneous : (previous * (1.0 - alpha)) + (instantaneous * alpha);
        scrollVelocityBucket = classifyScrollVelocityBucket(scrollVelocityPixelsPerMs);
    }

    private ScrollVelocityBucket classifyScrollVelocityBucket(double velocityPxPerMs) {
        if (velocityPxPerMs >= 2.20) {
            return ScrollVelocityBucket.FLING;
        }
        if (velocityPxPerMs >= 1.10) {
            return ScrollVelocityBucket.FAST;
        }
        if (velocityPxPerMs >= 0.45) {
            return ScrollVelocityBucket.MEDIUM;
        }
        if (velocityPxPerMs >= 0.12) {
            return ScrollVelocityBucket.SLOW;
        }
        return ScrollVelocityBucket.SETTLE;
    }

    private ScrollVelocityBucket resolveScrollVelocityBucket() {
        return scrollVelocityBucket == null ? ScrollVelocityBucket.SETTLE : scrollVelocityBucket;
    }

    private int adaptivePrefetchCount(int baseCount, double multiplier, int minCount, int maxCount) {
        int scaled = (int) Math.round(baseCount * multiplier);
        return Math.max(minCount, Math.min(maxCount, scaled));
    }

    private void logViewportRealizationTelemetry(String mode,
                                                 int visibleStart,
                                                 int visibleEnd,
                                                 int visibleCount,
                                                 int prefetchedCount,
                                                 ScrollVelocityBucket bucket) {
        if (!LOG_SCROLL_TELEMETRY && !LOG_REALIZATION_TELEMETRY) {
            return;
        }
        double velocity = scrollVelocityPixelsPerMs;
        LOG.info(() -> "[REALIZATION] mode=" + mode
                + " visibleRange=" + visibleStart + "-" + visibleEnd
                + " visibleCount=" + visibleCount
                + " prefetchedCount=" + prefetchedCount
                + " velocityPxPerMs=" + String.format(java.util.Locale.ROOT, "%.2f", velocity)
                + " bucket=" + bucket
                + " view=" + viewMode);
    }

    private void scheduleViewportScopedRealizationRefresh() {
        initViewportRealizationDebounce();
        realizationViewportGeneration.incrementAndGet();
        if (viewportRealizationDebounce == null) {
            return;
        }
        if (javafx.application.Platform.isFxApplicationThread()) {
            viewportRealizationDebounce.playFromStart();
        } else {
            javafx.application.Platform.runLater(() -> viewportRealizationDebounce.playFromStart());
        }
    }

    private void requestViewportScopedRealizationRefresh() {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(this::requestViewportScopedRealizationRefresh);
            return;
        }
        if (tableItems == null || tableItems.isEmpty()) {
            return;
        }
        final long expectedDirectoryGeneration = realizationDirectoryGeneration.get();
        final long expectedViewportGeneration = realizationViewportGeneration.get();
        java.util.List<ViewportWorkItem> workItems = buildViewportWorkItems(expectedDirectoryGeneration, expectedViewportGeneration);
        if (workItems.isEmpty()) {
            return;
        }
        viewportScheduler.submit(workItems);
        ViewportSchedulerTelemetry.Snapshot snapshot = viewportScheduler.runFrame(Math.max(0L, VIEWPORT_FRAME_BUDGET_NANOS));
        logViewportSchedulerTelemetry(snapshot, workItems.size());
    }

    private java.util.List<ViewportWorkItem> buildViewportWorkItems(long expectedDirectoryGeneration, long expectedViewportGeneration) {
        if (viewMode == ViewMode.DETAILS) {
            return buildDetailsViewportWorkItems(expectedDirectoryGeneration, expectedViewportGeneration);
        }
        if (isIconMode(viewMode)) {
            return buildIconViewportWorkItems(expectedDirectoryGeneration, expectedViewportGeneration);
        }
        return java.util.Collections.emptyList();
    }

    private java.util.List<ViewportWorkItem> buildDetailsViewportWorkItems(long expectedDirectoryGeneration, long expectedViewportGeneration) {
        java.util.List<TableRow<FileItem>> rows = collectVisibleDetailsRows();
        if (rows.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        rows.sort(java.util.Comparator.comparingInt(TableRow::getIndex));
        int visibleStart = Math.max(0, rows.get(0).getIndex());
        int visibleEnd = Math.max(visibleStart, rows.get(rows.size() - 1).getIndex());
        boolean scrollingDown = realizationScrollDirection.get() >= 0L;
        ScrollVelocityBucket bucket = resolveScrollVelocityBucket();
        int preBeforeBase = scrollingDown ? Math.max(4, DETAILS_PREFETCH_ROWS_BEFORE / 2) : DETAILS_PREFETCH_ROWS_BEFORE;
        int preAfterBase = scrollingDown ? DETAILS_PREFETCH_ROWS_AFTER : Math.max(6, DETAILS_PREFETCH_ROWS_AFTER / 2);
        double beforeMultiplier = scrollingDown ? bucket.trailingMultiplier : bucket.leadingMultiplier;
        double afterMultiplier = scrollingDown ? bucket.leadingMultiplier : bucket.trailingMultiplier;
        int preBefore = adaptivePrefetchCount(preBeforeBase, beforeMultiplier, 2, Math.max(preBeforeBase, DETAILS_PREFETCH_ROWS_BEFORE * 3));
        int preAfter = adaptivePrefetchCount(preAfterBase, afterMultiplier, 4, Math.max(preAfterBase, DETAILS_PREFETCH_ROWS_AFTER * 4));
        java.util.List<ViewportWorkItem> workItems = buildViewportBandWorkItems(
                "details",
                visibleStart,
                visibleEnd,
                preBefore,
                preAfter,
                1,
                expectedDirectoryGeneration,
                expectedViewportGeneration);
        int visibleCount = Math.max(0, visibleEnd - visibleStart + 1);
        int prefetchedCount = Math.max(0, workItems.size() - visibleCount);
        logViewportRealizationTelemetry("details", visibleStart, visibleEnd, visibleCount, prefetchedCount, bucket);
        return workItems;
    }

    private java.util.List<ViewportWorkItem> buildIconViewportWorkItems(long expectedDirectoryGeneration, long expectedViewportGeneration) {
        java.util.List<Node> visibleTiles = collectVisibleExplorerIconTiles();
        if (visibleTiles.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        visibleTiles.sort((left, right) -> {
            Bounds leftBounds = left.localToScene(left.getBoundsInLocal());
            Bounds rightBounds = right.localToScene(right.getBoundsInLocal());
            double leftMinY = leftBounds == null ? Double.MAX_VALUE : leftBounds.getMinY();
            double rightMinY = rightBounds == null ? Double.MAX_VALUE : rightBounds.getMinY();
            int cmp = Double.compare(leftMinY, rightMinY);
            if (cmp != 0) {
                return cmp;
            }
            double leftMinX = leftBounds == null ? Double.MAX_VALUE : leftBounds.getMinX();
            double rightMinX = rightBounds == null ? Double.MAX_VALUE : rightBounds.getMinX();
            return Double.compare(leftMinX, rightMinX);
        });
        Path firstVisiblePath = pathForExplorerIconTile(visibleTiles.get(0));
        Path lastVisiblePath = pathForExplorerIconTile(visibleTiles.get(visibleTiles.size() - 1));
        int visibleStart = Math.max(0, indexOfTableItem(firstVisiblePath));
        int visibleEnd = Math.max(visibleStart, indexOfTableItem(lastVisiblePath));
        int itemsPerRow = Math.max(1, computeItemsPerIconRow(resolveResponsiveIconViewportWidth()));
        boolean scrollingDown = realizationScrollDirection.get() >= 0L;
        ScrollVelocityBucket bucket = resolveScrollVelocityBucket();
        int preBeforeBase = itemsPerRow * (scrollingDown ? Math.max(1, ICON_PREFETCH_ROWS_BEFORE / 2) : ICON_PREFETCH_ROWS_BEFORE);
        int preAfterBase = itemsPerRow * (scrollingDown ? ICON_PREFETCH_ROWS_AFTER : Math.max(1, ICON_PREFETCH_ROWS_AFTER / 2));
        double beforeMultiplier = scrollingDown ? bucket.trailingMultiplier : bucket.leadingMultiplier;
        double afterMultiplier = scrollingDown ? bucket.leadingMultiplier : bucket.trailingMultiplier;
        int preBefore = adaptivePrefetchCount(preBeforeBase, beforeMultiplier, itemsPerRow, Math.max(preBeforeBase, itemsPerRow * ICON_PREFETCH_ROWS_BEFORE * 4));
        int preAfter = adaptivePrefetchCount(preAfterBase, afterMultiplier, itemsPerRow, Math.max(preAfterBase, itemsPerRow * ICON_PREFETCH_ROWS_AFTER * 5));
        java.util.List<ViewportWorkItem> workItems = buildViewportBandWorkItems(
                "icons",
                visibleStart,
                visibleEnd,
                preBefore,
                preAfter,
                itemsPerRow,
                expectedDirectoryGeneration,
                expectedViewportGeneration);
        int visibleCount = Math.max(0, visibleEnd - visibleStart + 1);
        int prefetchedCount = Math.max(0, workItems.size() - visibleCount);
        logViewportRealizationTelemetry("icons", visibleStart, visibleEnd, visibleCount, prefetchedCount, bucket);
        return workItems;
    }

    private java.util.List<ViewportWorkItem> buildViewportBandWorkItems(String mode,
                                                                        int visibleStart,
                                                                        int visibleEnd,
                                                                        int preBeforeCount,
                                                                        int preAfterCount,
                                                                        int logicalCellStride,
                                                                        long expectedDirectoryGeneration,
                                                                        long expectedViewportGeneration) {
        if (tableItems == null || tableItems.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        int farExtraCount = Math.max(logicalCellStride, VIEWPORT_FAR_WORK_LIMIT);
        int startInclusive = Math.max(0, visibleStart - Math.max(0, preBeforeCount) - farExtraCount);
        int endInclusive = Math.min(tableItems.size() - 1, visibleEnd + Math.max(0, preAfterCount) + farExtraCount);
        java.util.List<ViewportWorkItem> workItems = new java.util.ArrayList<>(Math.max(0, endInclusive - startInclusive + 1));
        for (int index = startInclusive; index <= endInclusive; index++) {
            FileItem item = tableItems.get(index);
            if (item == null || item.path() == null) {
                continue;
            }
            Path path = item.path();
            final int distance = viewportDistanceInLogicalCells(index, visibleStart, visibleEnd, logicalCellStride);
            final RealizationPriorityBand band = viewportBandClassifier.classify(distance);
            final boolean thumbCandidate = ImageSupport.isThumbCandidate(path);
            final boolean decodePromotionEligible = isDecodePromotionEligible(band, thumbCandidate);
            final int workIndex = index;
            workItems.add(new ViewportWorkItem.Basic(
                    mode + ":" + workIndex + ":" + path,
                    distance,
                    estimateViewportRealizeCostNanos(band),
                    decodePromotionEligible ? estimateViewportPromotionCostNanos(band) : 0L,
                    true,
                    decodePromotionEligible,
                    () -> requestViewportAsset(workIndex, band, false, expectedDirectoryGeneration, expectedViewportGeneration),
                    () -> requestViewportAsset(workIndex, band, true, expectedDirectoryGeneration, expectedViewportGeneration)
            ));
        }
        return workItems;
    }

    private int viewportDistanceInLogicalCells(int index, int visibleStart, int visibleEnd, int logicalCellStride) {
        int stride = Math.max(1, logicalCellStride);
        if (index < visibleStart) {
            return (int) Math.ceil((visibleStart - index) / (double) stride);
        }
        if (index > visibleEnd) {
            return (int) Math.ceil((index - visibleEnd) / (double) stride);
        }
        return 0;
    }

    private boolean isDecodePromotionEligible(RealizationPriorityBand band, boolean thumbCandidate) {
        return band == RealizationPriorityBand.VISIBLE || (!thumbCandidate && band == RealizationPriorityBand.NEAR_VIEWPORT);
    }

    private long estimateViewportRealizeCostNanos(RealizationPriorityBand band) {
        return switch (band) {
            case VISIBLE -> VIEWPORT_VISIBLE_REALIZE_ESTIMATE_NANOS;
            case NEAR_VIEWPORT -> VIEWPORT_NEAR_REALIZE_ESTIMATE_NANOS;
            case FAR_OFFSCREEN -> VIEWPORT_FAR_REALIZE_ESTIMATE_NANOS;
        };
    }

    private long estimateViewportPromotionCostNanos(RealizationPriorityBand band) {
        return switch (band) {
            case VISIBLE -> VIEWPORT_VISIBLE_PROMOTION_ESTIMATE_NANOS;
            case NEAR_VIEWPORT -> VIEWPORT_NEAR_PROMOTION_ESTIMATE_NANOS;
            case FAR_OFFSCREEN -> 0L;
        };
    }

    private void requestViewportAsset(int index,
                                      RealizationPriorityBand band,
                                      boolean promoteDecode,
                                      long expectedDirectoryGeneration,
                                      long expectedViewportGeneration) {
        if (tableItems == null || index < 0 || index >= tableItems.size()) {
            return;
        }
        if (expectedDirectoryGeneration != realizationDirectoryGeneration.get()
                || expectedViewportGeneration != realizationViewportGeneration.get()) {
            return;
        }
        FileItem item = tableItems.get(index);
        if (item == null || item.path() == null) {
            return;
        }
        final boolean dark = themeService != null && themeService.isDarkPreferred();
        final int px = (int) Math.round(clamp(iconSizePx, 16.0, ICON_SIZE_MAX));
        Path path = item.path();
        if (ImageSupport.isThumbCandidate(path)) {
            AsyncThumbnailService.getInstance().request(
                    path,
                    px,
                    thumbnailPriorityForBand(band, promoteDecode)
            ).whenComplete((img, ex) -> {
                // best-effort realization only
            });
            return;
        }
        String identity;
        try {
            identity = fileMetadataService != null ? fileMetadataService.iconIdentity(path) : null;
        } catch (Exception ex) {
            identity = null;
        }
        if (identity == null || identity.isBlank()) {
            identity = "type:" + IconLoader.IconType.FILE.name();
        }
        AsyncIconService.getInstance().request(
                identity,
                dark,
                px,
                iconPriorityForBand(band, promoteDecode)
        ).whenComplete((img, ex) -> {
            // best-effort realization only
        });
    }

    private AsyncIconService.RequestPriority iconPriorityForBand(RealizationPriorityBand band, boolean promoteDecode) {
        if (!promoteDecode) {
            return AsyncIconService.RequestPriority.BACKGROUND;
        }
        return switch (band) {
            case VISIBLE -> AsyncIconService.RequestPriority.VISIBLE;
            case NEAR_VIEWPORT -> AsyncIconService.RequestPriority.PREFETCH;
            case FAR_OFFSCREEN -> AsyncIconService.RequestPriority.BACKGROUND;
        };
    }

    private AsyncThumbnailService.RequestPriority thumbnailPriorityForBand(RealizationPriorityBand band, boolean promoteDecode) {
        if (!promoteDecode || band != RealizationPriorityBand.VISIBLE) {
            return AsyncThumbnailService.RequestPriority.BACKGROUND;
        }
        return AsyncThumbnailService.RequestPriority.VISIBLE;
    }

    private void onViewportScrollStopCommit(ViewportSchedulerTelemetry.Snapshot snapshot) {
        if (LOG_REALIZATION_TELEMETRY) {
            double latencyMs = snapshot.averageScrollStopCommitLatencyNanos() / 1_000_000.0;
            LOG.info(() -> "[REALIZATION] scrollStopCommit commits=" + snapshot.scrollStopCommits()
                    + " avgLatencyMs=" + String.format(java.util.Locale.ROOT, "%.2f", latencyMs)
                    + " realizeRuns=" + snapshot.realizeRuns()
                    + " decodePromotions=" + snapshot.decodePromotions()
                    + " decodePromotionDrops=" + snapshot.decodePromotionDrops()
                    + " budgetOverruns=" + snapshot.budgetOverruns()
                    + " view=" + viewMode);
        }
        if (javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(this::runViewportSettlePass);
        } else {
            javafx.application.Platform.runLater(this::runViewportSettlePass);
        }
    }

    private void logViewportSchedulerTelemetry(ViewportSchedulerTelemetry.Snapshot snapshot, int submittedWorkItemCount) {
        if (!LOG_REALIZATION_TELEMETRY) {
            return;
        }
        double latencyMs = snapshot.averageScrollStopCommitLatencyNanos() / 1_000_000.0;
        LOG.info(() -> "[REALIZATION] scheduler submitted=" + submittedWorkItemCount
                + " visibleQueue=" + viewportScheduler.visibleQueueSize()
                + " nearQueue=" + viewportScheduler.nearViewportQueueSize()
                + " farQueue=" + viewportScheduler.farOffscreenQueueSize()
                + " realizeRuns=" + snapshot.realizeRuns()
                + " decodePromotions=" + snapshot.decodePromotions()
                + " decodePromotionDrops=" + snapshot.decodePromotionDrops()
                + " budgetOverruns=" + snapshot.budgetOverruns()
                + " avgScrollStopLatencyMs=" + String.format(java.util.Locale.ROOT, "%.2f", latencyMs)
                + " view=" + viewMode);
    }

    private ViewportContinuityState captureViewportContinuityState(Path directory, long token) {
        if (directory == null) {
            return null;
        }
        java.util.List<Path> selectedPaths = filterPathsToDirectoryScope(getSelectedItems(), directory);
        Path focusPath = getFocusedOrSelectedPath();
        if (!isPathWithinDirectoryScope(focusPath, directory)) {
            focusPath = null;
        }
        Path anchorPath = iconSelectionAnchorPath != null && isPathWithinDirectoryScope(iconSelectionAnchorPath, directory)
                ? iconSelectionAnchorPath
                : focusPath;
        Path firstVisiblePath = null;
        int firstVisibleIndex = -1;
        double tableScroll = captureVerticalScrollValue(fileTable);
        double flowScroll = iconScroll != null ? iconScroll.getVvalue() : Double.NaN;
        double gridScroll = captureVerticalScrollValue(virtualIconGridView);
        double listScroll = captureVerticalScrollValue(virtualIconListView);
        if (viewMode == ViewMode.DETAILS) {
            java.util.List<TableRow<FileItem>> rows = collectVisibleDetailsRows();
            if (!rows.isEmpty()) {
                rows.sort(java.util.Comparator.comparingInt(TableRow::getIndex));
                TableRow<FileItem> first = rows.get(0);
                if (first != null && first.getItem() != null) {
                    firstVisiblePath = first.getItem().path();
                    firstVisibleIndex = first.getIndex();
                }
            }
        } else if (isIconMode(viewMode)) {
            firstVisiblePath = captureFirstVisibleExplorerIconPath(null);
            firstVisibleIndex = indexOfTableItem(firstVisiblePath);
        }
        return new ViewportContinuityState(token, directory, selectedPaths, focusPath, anchorPath, firstVisiblePath, firstVisibleIndex, tableScroll, flowScroll, gridScroll, listScroll, viewMode);
    }

    private void restoreViewportContinuityState(ViewportContinuityState state, long token) {
        if (state == null || token != state.token || !java.util.Objects.equals(currentDirectory, state.directory)) {
            return;
        }
        java.util.List<Path> scopedSelection = filterPathsToDirectoryScope(state.selectedPaths, state.directory);
        Path scopedFocusPath = isPathWithinDirectoryScope(state.focusPath, state.directory) ? state.focusPath : null;
        Path scopedAnchorPath = isPathWithinDirectoryScope(state.anchorPath, state.directory) ? state.anchorPath : null;
        if (!scopedSelection.isEmpty()) {
            Path focusPath = scopedFocusPath != null && scopedSelection.contains(scopedFocusPath)
                    ? scopedFocusPath
                    : scopedAnchorPath != null && scopedSelection.contains(scopedAnchorPath)
                    ? scopedAnchorPath
                    : scopedSelection.get(0);
            applyExplorerPathSelection(scopedSelection, focusPath);
            if (scopedAnchorPath != null && scopedSelection.contains(scopedAnchorPath)) {
                setExplorerSelectionAnchorPath(scopedAnchorPath);
            }
        } else if (scopedFocusPath != null) {
            restoreFocusToTablePath(scopedFocusPath);
        }
        if (state.viewMode == ViewMode.DETAILS) {
            if (state.firstVisiblePath != null) {
                int idx = findTableIndexForPath(state.firstVisiblePath);
                if (idx < 0) {
                    idx = state.firstVisibleIndex;
                }
                if (idx >= 0 && fileTable != null) {
                    fileTable.scrollTo(Math.max(0, idx));
                }
            }
            restoreVerticalScrollValue(fileTable, state.tableScrollValue);
        } else if (isIconMode(state.viewMode)) {
            if (state.firstVisiblePath != null) {
                scrollActiveIconPathIntoView(state.firstVisiblePath);
            }
            if (iconScroll != null && !Double.isNaN(state.flowScrollValue)) {
                iconScroll.setVvalue(Math.max(0.0, Math.min(1.0, state.flowScrollValue)));
            }
            restoreVerticalScrollValue(virtualIconGridView, state.virtualGridScrollValue);
            restoreVerticalScrollValue(virtualIconListView, state.virtualListScrollValue);
        }
        scheduleViewportScopedRealizationRefresh();
    }

// ---------------------------------------------------------------------
    // Hover prefetch (Explorer-style)
    // ---------------------------------------------------------------------
/**
 * scheduleHoverPrefetch.
 *
 * @param p TODO
 */
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
                executeOnHoverPrefetchExecutor("scheduleHoverPrefetch", () -> runHoverPrefetch(target, expected));
            });
        } else {
            hoverPrefetchTimer.setDuration(HOVER_PREFETCH_DELAY);
        }
        hoverPrefetchTimer.stop();
        hoverPrefetchTimer.playFromStart();
    }
/**
 * runHoverPrefetch.
 *
 * @param target TODO
 * @param expectedSeq TODO
 */
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
            // Warm metadata caches (best effort). Disabled by default for Phase 4B.2 lowest-CPU mode.
            if (Boolean.parseBoolean(System.getProperty("fileexplorer.metadata.prefetch", "false"))) {
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
                        fileMetadataService.humanReadableSizeForTable(target);
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
            }
            if (expectedSeq != hoverPrefetchSeq.get()) {
                return;
            }
            boolean dark = themeService != null && themeService.isDarkPreferred();
            int treePx = (int) Math.round(clamp(effectiveTreeIconPx(), 16.0, 32.0));
            int iconPx = (int) Math.round(clamp(iconSizePx, 16.0, ICON_SIZE_MAX));
            int[] sizes = new int[] { 16, 20, 24, 32, 48, 64, 96, treePx, iconPx };
            for (int s : sizes) {
                int px = (int) Math.round(clamp((double) s, 16.0, ICON_SIZE_MAX));
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
/**
 * displayNameForTable.
 *
 * @param p TODO
 * @return TODO
 */
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
/**
 * displayNameForTable.
 *
 * @param fi TODO
 * @return TODO
 */
    private String displayNameForTable(FileItem fi) {
        if (fi == null) {
            return "";
        }
        return displayNameForTable(fi.path());
    }
/**
 * typeForTable.
 *
 * @param p TODO
 * @return TODO
 */
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
/**
 * sizeForTable.
 *
 * @param p TODO
 * @return TODO
 */
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
            String size = fileMetadataService.humanReadableSizeForTable(p);
            return size == null ? "" : size;
        } catch (Exception ex) {
            return "";
        }
    }
/**
 * modifiedForTable.
 *
 * @param p TODO
 * @return TODO
 */
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
/**
 * safeFolderOrFileLabel.
 *
 * @param p TODO
 * @return TODO
 */
    private String safeFolderOrFileLabel(Path p) {
        LogSupport.enter(LOG, "safeFolderOrFileLabel");
        try {
            return Files.isDirectory(p) ? "Folder" : "File";
        } catch (Exception ex) {
            return "Item";
        }
    }
    private void scheduleDeferredBootstrapIncludes() {
        if (boundScene == null) {
            return;
        }
        if (contextInitialized) {
            scheduleDeferredBreadcrumbLoad();
        }
    }

    private void scheduleDeferredBreadcrumbLoad() {
        if (breadcrumbBarController != null || breadcrumbHost == null) {
            return;
        }
        if (!deferredBreadcrumbLoadScheduled.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            try {
                loadDeferredBreadcrumbNow();
            } finally {
                deferredBreadcrumbLoadScheduled.set(false);
            }
        });
    }

    private void loadDeferredBreadcrumbNow() {
        if (breadcrumbBarController != null || breadcrumbHost == null) {
            return;
        }
        try {
            StartupTrace.mark("deferred breadcrumb load begin");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fileexplorer/ui/breadcrumb/BreadcrumbBar.fxml"));
            Parent breadcrumbRoot = loader.load();
            Object controllerObj = loader.getController();
            BreadcrumbController controller = controllerObj instanceof BreadcrumbController bc ? bc : null;
            breadcrumbHost.getChildren().setAll(breadcrumbRoot);
            breadcrumbBarController = controller;
            configureBreadcrumbs();
            if (breadcrumbBarController != null && currentDirectory != null) {
                breadcrumbBarController.setPath(currentDirectory);
            }
            StartupTrace.mark("deferred breadcrumb load end");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Deferred breadcrumb bootstrap failed", ex);
        }
    }

    private void scheduleDeferredProgressPaneLoad() {
        if (progressPaneController != null) {
            return;
        }
        if (progressPaneHost == null) {
            ensureOperationsInspectorCardCreated();
        }
        if (progressPaneHost == null) {
            return;
        }
        if (!deferredProgressPaneLoadScheduled.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            try {
                loadDeferredProgressPaneNow();
            } finally {
                deferredProgressPaneLoadScheduled.set(false);
            }
        });
    }

    private void loadDeferredProgressPaneNow() {
        if (progressPaneController != null || progressPaneHost == null) {
            return;
        }
        try {
            StartupTrace.mark("deferred progress pane load begin");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fileexplorer/ui/layout/ProgressPane.fxml"));
            Parent progressRoot = loader.load();
            Object controllerObj = loader.getController();
            ProgressPaneController controller = controllerObj instanceof ProgressPaneController ppc ? ppc : null;
            progressPaneHost.getChildren().setAll(progressRoot);
            progressPaneController = controller;
            if (progressPaneController != null) {
                progressPaneController.attach(context);
            }
            StartupTrace.mark("deferred progress pane load end");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Deferred progress-pane bootstrap failed", ex);
        }
    }

    public ProgressPaneController getProgressPaneController() {
        return progressPaneController;
    }

/**
 * configureBreadcrumbs.
 *
 */
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
    // Ensure currentDirectory is set for this navigation (fixes empty table when callers do not set it first).
    currentDirectory = directory;
        // Track & reflect current directory
        this.currentDirectory = directory;
        setVisibleDirectoryScope(directory);
        if (breadcrumbBarController != null) {
            breadcrumbBarController.setPath(directory);
        }
        updateWindowTitle(directory);
        if (statusLabel != null) {
            statusLabel.setText(fileMetadataService.displayPathForStatus(directory));
        }
        if (listing == null) {
            listing = java.util.List.of();
        }
        tableItems.setAll(listing);
        applyExplorerSortToTableItems();
        updateStatusCounts();
        rebuildTableIndexCache(tableItems);
        rememberRecentHomeLocation(directory);
        refreshHomeSurface();
        homeActive = false;
        applyHomeModeVisibility();
        updateNavigationButtonsState();
        updateSearchPrompt(directory);
        updateTopChromeState();
        if (startupInitialDirectoryLoadStarted.get() && startupInitialDirectoryLoadFinished.compareAndSet(false, true)) {
            StartupTrace.mark("initial directory hydration finished: success");
        }

        InlineRenameSession awaitingInlineRenameSession = activeInlineRenameSession;
        boolean deferAggressiveRestore = awaitingInlineRenameSession != null
                && awaitingInlineRenameSession.awaitingCompletion
                && awaitingInlineRenameSession.pendingResultPath != null
                && java.util.Objects.equals(directory, awaitingInlineRenameSession.pendingResultPath.getParent());

        Path restoreVisiblePath = null;
        // Phase 3.5.1: Restore selection after refresh if possible.
        if (pendingRestoreSelection && pendingReselectPath != null) {
            Path requestedPath = pendingReselectPath;
            int idx = -1;
            ObservableList<FileItem> visibleItems = fileTable != null && fileTable.getItems() != null
                    ? fileTable.getItems()
                    : tableItems;
            for (int i = 0; i < visibleItems.size(); i++) {
                FileItem it = visibleItems.get(i);
                if (it != null && requestedPath.equals(it.path())) {
                    idx = i;
                    break;
                }
            }
            if (fileTable != null) {
                if (idx >= 0) {
                    fileTable.getSelectionModel().clearAndSelect(idx);
                    if (fileTable.getFocusModel() != null) {
                        fileTable.getFocusModel().focus(idx);
                    }
                    if (!deferAggressiveRestore) {
                        fileTable.scrollTo(Math.max(0, idx - 2));
                        restoreVisiblePath = requestedPath;
                    }
                } else if (!deferAggressiveRestore && pendingReselectIndex >= 0 && pendingReselectIndex < visibleItems.size()) {
                    if (pendingReselectPreferIndexOnMissing) {
                        int fallbackIndex = Math.max(0, Math.min(visibleItems.size() - 1, pendingReselectIndex));
                        fileTable.getSelectionModel().clearAndSelect(fallbackIndex);
                        if (fileTable.getFocusModel() != null) {
                            fileTable.getFocusModel().focus(fallbackIndex);
                        }
                        fileTable.scrollTo(Math.max(0, fallbackIndex - 2));
                        FileItem fallbackItem = visibleItems.get(fallbackIndex);
                        restoreVisiblePath = fallbackItem != null ? fallbackItem.path() : null;
                    } else {
                        fileTable.scrollTo(Math.max(0, pendingReselectIndex - 2));
                    }
                }
            }
            pendingRestoreSelection = false;
            pendingReselectPath = null;
            pendingReselectIndex = -1;
            pendingReselectPreferIndexOnMissing = false;
            pendingInlineRenameSelectionPath = null;
            pendingInlineRenameSelectionIndex = -1;
        }
// Update view-specific UI
        if (isIconMode(viewMode)) {
            rebuildIconTiles();
        } else {
            requestCoalescedTableRefresh();
        }
        if (restoreVisiblePath != null) {
            scheduleExplorerPathVisibilityStabilization(restoreVisiblePath, false);
        }
        refreshInspectorPresentationForCurrentContext();
        if (pendingCreateAndRenamePath != null && java.util.Objects.equals(directory, pendingCreateAndRenamePath.getParent())) {
            Path createdPath = pendingCreateAndRenamePath;
            boolean present = false;
            for (com.fileexplorer.model.FileItem it : listing) {
                if (it != null && java.util.Objects.equals(createdPath, it.path())) {
                    present = true;
                    break;
                }
            }
            if (present) {
                scheduleExplorerPathVisibilityStabilization(createdPath, false);
                Platform.runLater(() -> {
                    if (java.util.Objects.equals(createdPath, pendingCreateAndRenamePath)) {
                        beginTableInlineRename(createdPath);
                    }
                });
            }
        }
        finalizeAwaitingInlineRenameCommitIfPresent(directory, listing);
        restoreInlineRenameSessionAfterRefreshIfNeeded(directory);
        applyPendingShellCommandRestoreIfNeeded(directory);
        syncExplorerContextMenuShellState();
    }
    /**
     * UI-thread handler invoked by the DirectoryCoordinator via the event bus.
     */
    // ---------------------------------------------------------------------
    // Phase 3.5.4: Search (fast filter within current folder)
    // ---------------------------------------------------------------------
/**
 * configureSearch.
 *
 */
    private void configureSearch() {
        if (searchField == null) {
            return;
        }
        ensureSearchResultsStateSurfaceInstalled();
        searchField.textProperty().addListener((obs, oldV, newV) -> handleSearchFieldChanged(newV));
        searchField.focusedProperty().addListener((obs, oldV, newV) -> updateSearchChromeState());
        searchField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                if (!normalizeSearchQuery(searchField.getText()).isEmpty()) {
                    onClearSearchAction();
                    e.consume();
                    return;
                }
                if (searchSessionState != SearchSessionState.IDLE) {
                    endSearchSession(true, true);
                    e.consume();
                    return;
                }
                focusPrimaryFileSurface();
                e.consume();
                return;
            }
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                // Phase 4B.3.x: Enter jumps to next match (Shift+Enter = previous)
                if (activeSearchQuery != null && !activeSearchQuery.isBlank()) {
                    findNextMatch(!e.isShiftDown());
                    focusPrimaryFileSurface();
                }
                e.consume();
            }
        });
        // Initial predicate
        updateSearchPrompt(currentDirectory);
        updateSearchChromeState();
        applySearchFilterNow(searchField.getText());
        updateSearchResultSurfaceState();
    }

    private void handleSearchFieldChanged(String rawQuery) {
        updateSearchChromeState();
        if (suppressSearchFieldListener) {
            updateSearchResultSurfaceState();
            return;
        }
        if (homeActive) {
            searchDebounce.stop();
            return;
        }
        String normalized = normalizeSearchQuery(rawQuery);
        searchSessionDisplayQuery = normalized;
        if (normalized.isEmpty()) {
            searchDebounce.stop();
            endSearchSession(true, false);
            return;
        }
        beginSearchSessionIfNeeded();
        updateSearchSessionState(SearchSessionState.TYPING);
        final long token = searchSessionSeq.incrementAndGet();
        final Path scope = searchSessionScopeRoot != null ? searchSessionScopeRoot : currentDirectory;
        final java.util.List<FileItem> snapshot = snapshotItemsForSearchComputation();
        final boolean hugeMode = hugeFolderModeActive;
        final boolean includeExtensions = showFileNameExtensions;
        searchDebounce.stop();
        searchDebounce.setOnFinished(_ -> launchSearchComputation(token, scope, rawQuery, normalized, snapshot, hugeMode, includeExtensions));
        searchDebounce.playFromStart();
    }

    private void launchSearchComputation(long token,
                                         Path scope,
                                         String rawQuery,
                                         String normalizedQuery,
                                         java.util.List<FileItem> snapshot,
                                         boolean hugeMode,
                                         boolean includeExtensions) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            endSearchSession(true, false);
            return;
        }
        updateSearchSessionState(SearchSessionState.SEARCHING);
        executeOnIoExecutor("launchSearchComputation", () -> {
            SearchComputationResult computed = computeSearchResult(snapshot, normalizedQuery, hugeMode, includeExtensions);
            Platform.runLater(() -> applySearchComputationResult(token, scope, rawQuery, normalizedQuery, computed));
        });
    }

    private SearchComputationResult computeSearchResult(java.util.List<FileItem> snapshot,
                                                        String normalizedQuery,
                                                        boolean hugeMode,
                                                        boolean includeExtensions) {
        if (snapshot == null || snapshot.isEmpty()) {
            return new SearchComputationResult(0, 0, hugeMode ? java.util.List.of() : null);
        }
        java.util.ArrayList<FileItem> hugeMatches = hugeMode ? new java.util.ArrayList<>() : null;
        int matchCount = 0;
        for (FileItem item : snapshot) {
            String candidate = searchCandidateName(item, includeExtensions);
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (candidate.toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery.toLowerCase(java.util.Locale.ROOT))) {
                matchCount++;
                if (hugeMatches != null) {
                    hugeMatches.add(item);
                }
            }
        }
        return new SearchComputationResult(snapshot.size(), matchCount, hugeMatches);
    }

    private java.util.List<FileItem> snapshotItemsForSearchComputation() {
        if (hugeFolderModeActive) {
            return new java.util.ArrayList<>(hugeFolderItems);
        }
        return new java.util.ArrayList<>(tableItems);
    }

    private String searchCandidateName(FileItem item, boolean includeExtensions) {
        if (item == null) {
            return "";
        }
        String name = item.name();
        if (name == null || name.isBlank()) {
            Path path = item.path();
            Path fileName = path == null ? null : path.getFileName();
            name = fileName != null ? fileName.toString() : (path == null ? "" : path.toString());
        }
        if (includeExtensions) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private void applySearchComputationResult(long token,
                                              Path scope,
                                              String rawQuery,
                                              String normalizedQuery,
                                              SearchComputationResult computed) {
        if (token != searchSessionSeq.get()) {
            return;
        }
        if (scope != null && currentDirectory != null && !java.util.Objects.equals(scope.normalize(), currentDirectory.normalize())) {
            return;
        }
        if (!java.util.Objects.equals(normalizedQuery.toLowerCase(java.util.Locale.ROOT), normalizeSearchQuery(searchField == null ? "" : searchField.getText()).toLowerCase(java.util.Locale.ROOT))) {
            return;
        }
        searchSessionSnapshotItemCount = computed == null ? 0 : computed.snapshotItemCount;
        searchSessionPredictedMatchCount = computed == null ? -1 : computed.matchCount;
        applySearchFilterNow(rawQuery, computed == null ? null : computed.hugeFolderMatches);
    }

    private String normalizeSearchQuery(String rawQuery) {
        return rawQuery == null ? "" : rawQuery.trim();
    }

    private void beginSearchSessionIfNeeded() {
        Path scope = currentDirectory;
        if (scope == null) {
            return;
        }
        if (searchSessionScopeRoot != null && java.util.Objects.equals(searchSessionScopeRoot.normalize(), scope.normalize())) {
            return;
        }
        searchSessionScopeRoot = scope;
        searchSessionRestorePath = getFocusedOrSelectedPath();
        searchSessionRestoreIndex = getFocusedOrSelectedIndex();
        searchSessionPredictedMatchCount = -1;
        searchSessionSnapshotItemCount = 0;
    }

    private void endSearchSession(boolean restoreSelection, boolean focusFileSurface) {
        searchDebounce.stop();
        searchSessionSeq.incrementAndGet();
        Path restorePath = searchSessionRestorePath;
        int restoreIndex = searchSessionRestoreIndex;
        Path scope = searchSessionScopeRoot;
        searchSessionDisplayQuery = "";
        searchSessionPredictedMatchCount = -1;
        searchSessionSnapshotItemCount = 0;
        searchSessionScopeRoot = null;
        searchSessionRestorePath = null;
        searchSessionRestoreIndex = -1;
        updateSearchSessionState(SearchSessionState.IDLE);
        applySearchFilterNow("", null);
        if (restoreSelection && scope != null && currentDirectory != null
                && java.util.Objects.equals(scope.normalize(), currentDirectory.normalize())) {
            restoreSelectionAfterSearchExit(restorePath, restoreIndex);
        }
        if (focusFileSurface) {
            focusPrimaryFileSurface();
        }
        updateSearchResultSurfaceState();
    }

    private void restoreSelectionAfterSearchExit(Path restorePath, int restoreIndex) {
        if (fileTable == null || fileTable.getSelectionModel() == null) {
            return;
        }
        int index = findTableIndexForPath(restorePath);
        if (index < 0 && restoreIndex >= 0 && restoreIndex < fileTable.getItems().size()) {
            index = restoreIndex;
        }
        if (index < 0) {
            return;
        }
        try {
            fileTable.getSelectionModel().clearAndSelect(index);
            if (fileTable.getFocusModel() != null) {
                fileTable.getFocusModel().focus(index);
            }
            fileTable.scrollTo(Math.max(0, index - 2));
        } catch (Exception ignored) {
        }
    }

    private void cancelSearchSessionForDirectoryChange(Path nextDirectory) {
        if (nextDirectory == null) {
            return;
        }
        if (searchSessionScopeRoot == null) {
            return;
        }
        try {
            if (java.util.Objects.equals(searchSessionScopeRoot.normalize(), nextDirectory.normalize())) {
                return;
            }
        } catch (Exception ignored) {
            if (java.util.Objects.equals(searchSessionScopeRoot, nextDirectory)) {
                return;
            }
        }
        searchDebounce.stop();
        searchSessionSeq.incrementAndGet();
        searchSessionDisplayQuery = "";
        searchSessionPredictedMatchCount = -1;
        searchSessionSnapshotItemCount = 0;
        searchSessionScopeRoot = null;
        searchSessionRestorePath = null;
        searchSessionRestoreIndex = -1;
        updateSearchSessionState(SearchSessionState.IDLE);
        if (searchField != null) {
            setSearchFieldTextSilently("");
        }
        applySearchFilterNow("", null);
    }

    private void setSearchFieldTextSilently(String value) {
        if (searchField == null) {
            return;
        }
        suppressSearchFieldListener = true;
        try {
            searchField.setText(value == null ? "" : value);
        } finally {
            suppressSearchFieldListener = false;
        }
        updateSearchChromeState();
    }

    private void updateSearchSessionState(SearchSessionState nextState) {
        searchSessionState = nextState == null ? SearchSessionState.IDLE : nextState;
        updateSearchChromeState();
        updateSearchResultSurfaceState();
    }

    private void ensureSearchResultsStateSurfaceInstalled() {
        if (viewHost == null || searchResultsStateSurface != null) {
            return;
        }
        Label title = new Label();
        title.getStyleClass().add("search-results-state-title");
        title.setWrapText(true);

        Label subtitle = new Label();
        subtitle.getStyleClass().add("search-results-state-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(520.0);

        VBox surface = new VBox(6.0, title, subtitle);
        surface.getStyleClass().add("search-results-state-surface");
        surface.setAlignment(Pos.CENTER);
        surface.setManaged(false);
        surface.setVisible(false);
        surface.setMouseTransparent(true);

        searchResultsStateSurface = surface;
        searchResultsStateTitle = title;
        searchResultsStateSubtitle = subtitle;
        viewHost.getChildren().add(surface);
        StackPane.setAlignment(surface, Pos.CENTER);
    }

    private void updateSearchResultSurfaceState() {
        ensureSearchResultsStateSurfaceInstalled();
        boolean searchActive = activeSearchQuery != null && !activeSearchQuery.isBlank();
        if (viewHost != null) {
            setStyleClass(viewHost, "search-results-active", searchActive);
            setStyleClass(viewHost, "search-results-empty", searchActive && !directoryLoading && fileTable != null
                    && fileTable.getItems() != null && fileTable.getItems().isEmpty());
        }
        if (searchResultsStateSurface == null || searchResultsStateTitle == null || searchResultsStateSubtitle == null) {
            return;
        }
        if (homeActive || !searchActive) {
            searchResultsStateSurface.setVisible(false);
            searchResultsStateSurface.toBack();
            return;
        }
        int visible = (fileTable != null && fileTable.getItems() != null) ? fileTable.getItems().size() : 0;
        String scope = directoryDisplayName(searchSessionScopeRoot != null ? searchSessionScopeRoot : currentDirectory);
        if (scope == null || scope.isBlank()) {
            scope = "this folder";
        }
        String queryDisplay = searchSessionDisplayQuery == null || searchSessionDisplayQuery.isBlank()
                ? activeSearchQuery
                : searchSessionDisplayQuery;
        if (directoryLoading && visible == 0) {
            searchResultsStateTitle.setText("Searching…");
            searchResultsStateSubtitle.setText("Looking for \"" + queryDisplay + "\" in " + scope + ".");
            searchResultsStateSurface.setVisible(true);
            searchResultsStateSurface.toFront();
            return;
        }
        if (visible == 0) {
            searchResultsStateTitle.setText("No results found");
            searchResultsStateSubtitle.setText("No items matched \"" + queryDisplay + "\" in " + scope
                    + ". Clear the search to return to the folder view.");
            searchResultsStateSurface.setVisible(true);
            searchResultsStateSurface.toFront();
            return;
        }
        searchResultsStateSurface.setVisible(false);
        searchResultsStateSurface.toBack();
    }
    // Phase 4B.3.x: Find Next / Previous for the active search query.
    // Forward=true => next; Forward=false => previous.
    private void findNextMatch(boolean forward) {
        final String q = (activeSearchQuery == null) ? "" : activeSearchQuery.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty() || fileTable == null) {
            return;
        }
        // Huge-folder mode: operate over the full match list and page to the correct slice.
        if (hugeFolderModeActive) {
            final java.util.List<FileItem> matches = hugeFolderSearchItems;
            if (matches == null || matches.isEmpty()) {
                return;
            }
            final int n = matches.size();
            int next = lastFindIndex;
            if (forward) next++; else next--;
            if (next < 0) next = n - 1;
            if (next >= n) next = 0;
            lastFindIndex = next;
            final int pageSize = Integer.getInteger("fileexplorer.hugeFolder.pageSize",
                    Integer.getInteger("fileexplorer.hugeFolder.showLimit", 50000));
            final int pageStart = (next / pageSize) * pageSize;
            if (pageStart != hugeFolderPageStart) {
                hugeFolderPageStart = pageStart;
                int end = Math.min(hugeFolderPageStart + pageSize, matches.size());
                tableItems.setAll(matches.subList(hugeFolderPageStart, end));
                rebuildTableIndexCache(tableItems);
                updateStatusCounts();
            }
            final int local = next - hugeFolderPageStart;
            try {
                fileTable.requestFocus();
                fileTable.getSelectionModel().clearAndSelect(Math.max(0, Math.min(local, tableItems.size() - 1)));
                fileTable.scrollTo(Math.max(0, local));
            } catch (Exception ignored) {}
            if (statusLabel != null) {
                    final String qDisplay = (activeSearchQuery == null) ? "" : activeSearchQuery;
                    statusLabel.setText("Large folder: filter \"" + qDisplay + "\" — matches " + matches.size() + " (PageUp/PageDown) (F3/Shift+F3)");
            }
            return;
        }
        // Normal mode: iterate visible (already-filtered) items.
        final javafx.collections.ObservableList<FileItem> items = fileTable.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        int start = fileTable.getSelectionModel().getSelectedIndex();
        if (start < 0) start = 0;
        final int n = items.size();
        int idx = start;
        for (int step = 0; step < n; step++) {
            idx = forward ? (idx + 1) % n : (idx - 1 + n) % n;
            FileItem fi = items.get(idx);
            if (fi == null) continue;
            java.nio.file.Path p = fi.path();
            if (p == null) continue;
            String name = displayNameForTable(p);
            if (name == null) continue;
            if (name.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                lastFindIndex = idx;
                try {
                    fileTable.requestFocus();
                    fileTable.getSelectionModel().clearAndSelect(idx);
                    fileTable.scrollTo(idx);
                } catch (Exception ignored) {}
                return;
            }
        }
    }
/**
 * applySearchFilterNow.
 *
 * @param rawQuery TODO
 */
    private void applySearchFilterNow(String rawQuery) {
        applySearchFilterNow(rawQuery, null);
    }

    private void applySearchFilterNow(String rawQuery, java.util.List<FileItem> precomputedHugeMatches) {
        String normalized = normalizeSearchQuery(rawQuery);
        String q = normalized.toLowerCase(java.util.Locale.ROOT);
        activeSearchQuery = q;
        if (!java.util.Objects.equals(lastFindQuery, q)) {
            lastFindQuery = q;
            lastFindIndex = -1;
        }
        // Phase 4B.3: In huge-folder mode, search must apply to the full scanned set (off-UI list),
        // not just the current visible page slice.
        if (hugeFolderModeActive) {
            hugeFolderSearchQuery = q;
            hugeFolderSearchActive = (q != null && !q.isEmpty());
            hugeFolderPageStart = 0;
            hugeFolderSearchItems.clear();
            if (hugeFolderSearchActive) {
                if (precomputedHugeMatches != null && !precomputedHugeMatches.isEmpty()) {
                    hugeFolderSearchItems.addAll(precomputedHugeMatches);
                } else {
                    for (FileItem fi : hugeFolderItems) {
                        String name = (fi == null ? null : fi.name());
                        if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                            hugeFolderSearchItems.add(fi);
                        }
                    }
                }
            }
            final int pageSize = Integer.getInteger("fileexplorer.hugeFolder.pageSize",
                    Integer.getInteger("fileexplorer.hugeFolder.showLimit", 50000));
            final java.util.List<FileItem> source = hugeFolderSearchActive ? hugeFolderSearchItems : hugeFolderItems;
            int end = Math.min(pageSize, source.size());
            if (tableItems != null) {
                tableItems.setAll(source.subList(0, end));
                rebuildTableIndexCache(tableItems);
            }
            if (statusLabel != null) {
                if (hugeFolderSearchActive) {
                    statusLabel.setText("Large folder: filter \"" + rawQuery + "\" — matches " + source.size() + " (PageUp/PageDown)");
                } else {
                    statusLabel.setText("Large folder: Page 1 — showing 1–" + end + " of "
                            + Math.max(hugeFolderScannedTotal, source.size()) + " (PageUp/PageDown)");
                }
            }
            // In huge-folder mode we manage the page manually, so bypass FilteredList predicate entirely.
            filteredTableItems.setPredicate(_ -> true);
            if (!SAFE_MODE && isIconMode(viewMode)) {
                rebuildIconTiles();
            }
            return;
        }
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
        int visibleCount = (fileTable != null && fileTable.getItems() != null) ? fileTable.getItems().size() : 0;
        if (q.isEmpty()) {
            updateSearchSessionState(SearchSessionState.IDLE);
        } else if (directoryLoading && visibleCount == 0) {
            updateSearchSessionState(SearchSessionState.SEARCHING);
        } else if (visibleCount > 0) {
            updateSearchSessionState(SearchSessionState.RESULTS);
        } else {
            updateSearchSessionState(SearchSessionState.NO_RESULTS);
        }
        updateStatusCounts();
        // If icon view is currently visible, rebuild it from the filtered set.
        if (viewMode != null && viewMode != ViewMode.DETAILS) {
            tryRebuildIconViewFromVisibleItems();
        }
    }
/**
 * tryRebuildIconViewFromVisibleItems.
 *
 */
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
/**
 * updateStatusCounts.
 *
 */
    private void updateStatusCounts() {
        if (statusLabel == null) return;
        int visible = (fileTable != null && fileTable.getItems() != null) ? fileTable.getItems().size() : 0;
        int total = (tableItems != null) ? tableItems.size() : 0;
        String scope = directoryDisplayName(searchSessionScopeRoot != null ? searchSessionScopeRoot : currentDirectory);
        if (scope == null || scope.isBlank()) {
            scope = "this folder";
        }
        if (activeSearchQuery != null && !activeSearchQuery.isEmpty()) {
            String queryDisplay = searchSessionDisplayQuery == null || searchSessionDisplayQuery.isBlank()
                    ? activeSearchQuery
                    : searchSessionDisplayQuery;
            if (directoryLoading && visible == 0) {
                statusLabel.setText("Searching " + scope + " for \"" + queryDisplay + "\" …");
                updateSearchSessionState(SearchSessionState.SEARCHING);
            } else if (visible == 0) {
                statusLabel.setText("No results for \"" + queryDisplay + "\" in " + scope);
                updateSearchSessionState(SearchSessionState.NO_RESULTS);
            } else {
                statusLabel.setText(String.format(java.util.Locale.ROOT,
                        "%d result%s in %s",
                        visible,
                        visible == 1 ? "" : "s",
                        scope));
                updateSearchSessionState(SearchSessionState.RESULTS);
            }
            if (locationLabel != null) {
                String basePath = currentDirectory == null || fileMetadataService == null
                        ? scope
                        : fileMetadataService.displayPathForStatus(currentDirectory);
                locationLabel.setText((directoryLoading ? "Searching in " : "Search results in ") + basePath);
            }
        } else {
            statusLabel.setText(String.format(java.util.Locale.ROOT, "%d items", total));
            if (locationLabel != null) {
                if (homeActive) {
                    locationLabel.setText("Home");
                } else if (currentDirectory != null && fileMetadataService != null) {
                    locationLabel.setText(fileMetadataService.displayPathForStatus(currentDirectory));
                } else if (currentDirectory != null) {
                    locationLabel.setText(currentDirectory.toString());
                } else {
                    locationLabel.setText("");
                }
            }
        }
        updateSearchResultSurfaceState();
    }

    private void updateTopChromeState() {
        long startedNanos = System.nanoTime();
        updateSearchPrompt(currentDirectory);
        updateSearchChromeState();
        applySelectionCommandStateNow(false);
        syncPaneTogglesFromUiState();
        updateTabStrip();
        double width = 0.0;
        if (root != null && root.getScene() != null) {
            width = root.getScene().getWidth();
        }
        scheduleCommandBarCompaction(width);
        logChromePassIfSlow("topChromeState", startedNanos);
    }

    private void updateSearchPrompt(Path directory) {
        if (searchField == null) {
            return;
        }
        String prompt;
        if (homeActive) {
            prompt = "Search Home";
        } else {
            String target = directoryDisplayName(directory);
            if (target == null || target.isBlank()) {
                target = "this folder";
            }
            prompt = "Search " + target;
        }
        searchField.setPromptText(prompt);
        Tooltip tooltip = searchField.getTooltip();
        if (tooltip != null) {
            tooltip.setText(homeActive ? "Search is unavailable on Home" : prompt);
        }
    }

    private void scheduleCommandBarCompaction(double width) {
        pendingCommandBarWidth = width;
        try {
            commandBarCompactionDebounce.playFromStart();
        } catch (Exception ignored) {
            applyCommandBarCompactionNow(width);
        }
    }

    private void applyCommandBarCompactionNow(double width) {
        if (commandBar == null) {
            return;
        }
        commandBar.getStyleClass().removeAll("compact", "medium-compact");
        if (width > 0 && width < 1220) {
            commandBar.getStyleClass().add("compact");
        } else if (width > 0 && width < 1420) {
            commandBar.getStyleClass().add("medium-compact");
        }
    }

    private void scheduleSelectionCommandStateRefresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::scheduleSelectionCommandStateRefresh);
            return;
        }
        selectionChromeRefreshQueued.set(true);
        if (selectionChromeDebounce != null) {
            selectionChromeDebounce.playFromStart();
        } else {
            selectionChromeRefreshQueued.set(false);
            applySelectionCommandStateNow(false);
        }
    }

    private boolean canUndoShellCommand() {
        try {
            return context != null && context.commandManager() != null && context.commandManager().canUndo();
        } catch (Exception ex) {
            return false;
        }
    }

    private ExplorerCommandStateSnapshot captureExplorerCommandStateSnapshot() {
        java.util.LinkedHashSet<Path> heldSelection = explorerContextMenuSelectionPresentationHold
                ? explorerContextMenuHeldSelectionSnapshot()
                : new java.util.LinkedHashSet<>();
        int selectionCount = !homeActive
                ? (!heldSelection.isEmpty()
                        ? heldSelection.size()
                        : (fileTable != null && fileTable.getSelectionModel() != null
                                ? fileTable.getSelectionModel().getSelectedItems().size()
                                : 0))
                : 0;
        Path primarySelection = selectionCount > 0
                ? (!heldSelection.isEmpty()
                        ? (explorerContextMenuHeldFocusPath != null ? explorerContextMenuHeldFocusPath : heldSelection.iterator().next())
                        : getPrimarySelection())
                : null;
        boolean canPaste = !homeActive && canPasteIntoCurrentDirectory();
        boolean hasDirectory = resolveActiveDirectoryForShellCommands() != null;
        boolean hasVisibleItems = fileTable != null && fileTable.getItems() != null && !fileTable.getItems().isEmpty();
        boolean canUndo = canUndoShellCommand();
        String undoMenuLabel = canUndo ? formatUndoMenuLabel() : "Undo";
        boolean singleDirectorySelection = selectionCount == 1 && isDirectoryPath(primarySelection);
        boolean pinnedDirectorySelection = singleDirectorySelection && isPathPinnedToQuickAccess(primarySelection);
        return new ExplorerCommandStateSnapshot(
                selectionCount,
                primarySelection,
                homeActive,
                canPaste,
                hasDirectory,
                hasVisibleItems,
                canUndo,
                undoMenuLabel,
                singleDirectorySelection,
                pinnedDirectorySelection,
                viewMode,
                currentSortKey);
    }

    private void applySelectionCommandStateNow(boolean force) {
        long startedNanos = System.nanoTime();
        ExplorerCommandStateSnapshot snapshot = captureExplorerCommandStateSnapshot();
        if (!force && snapshot.semanticallyEquals(lastExplorerCommandStateSnapshot)) {
            return;
        }
        lastExplorerCommandStateSnapshot = snapshot;
        boolean hasSelection = snapshot.selectionCount > 0;
        boolean singleSelection = snapshot.selectionCount == 1;
        if (cutButton != null) cutButton.setDisable(!hasSelection);
        if (copyButton != null) copyButton.setDisable(!hasSelection);
        if (renameButton != null) renameButton.setDisable(!singleSelection);
        if (deleteButton != null) deleteButton.setDisable(!hasSelection);
        if (shareButton != null) shareButton.setDisable(!singleSelection);
        if (pasteButton != null) pasteButton.setDisable(snapshot.homeActive || !snapshot.canPaste);
        syncExplorerContextMenuShellState(snapshot);
        logChromePassIfSlow("selectionCommandState", startedNanos);
    }

    private void updateSelectionCommandState() {
        applySelectionCommandStateNow(false);
    }

    private void logChromePassIfSlow(String label, long startedNanos) {
        long elapsedNanos = System.nanoTime() - startedNanos;
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (!LOG_CHROME_TELEMETRY && elapsedMs < CHROME_HITCH_LOG_THRESHOLD_MS) {
            return;
        }
        LOG.info(() -> "[CHROME] " + label + " tookMs=" + elapsedMs
                + " queued=" + selectionChromeRefreshQueued.get()
                + " selectionCount=" + (fileTable != null && fileTable.getSelectionModel() != null
                ? fileTable.getSelectionModel().getSelectedItems().size()
                : 0)
                + " viewMode=" + viewMode);
    }

    private void syncPaneTogglesFromUiState() {
        boolean detailsVisible = inspectorMode == InspectorMode.DETAILS;
        boolean previewVisible = inspectorMode == InspectorMode.PREVIEW;
        if (detailsToggle != null) {
            detailsToggle.setSelected(detailsVisible && !homeActive);
        }
        if (previewToggle != null) {
            previewToggle.setSelected(previewVisible && !homeActive);
        }
        if (detailsPaneMenuItem != null) {
            detailsPaneMenuItem.setSelected(detailsVisible);
        }
        if (previewPaneMenuItem != null) {
            previewPaneMenuItem.setSelected(previewVisible);
        }
    }

/**
 * handleDirectoryListingFailed.
 *
 * @param directory TODO
 * @param error TODO
 */
    private void handleDirectoryListingFailed(Path directory, Throwable error) {
        this.currentDirectory = directory;
        if (breadcrumbBarController != null && directory != null) {
            breadcrumbBarController.setPath(directory);
        }
        homeActive = false;
        applyHomeModeVisibility();
        refreshHomeSurface();
        String msg = (error == null) ? "Directory load failed." : ("Directory load failed: " + error.getMessage());
        setStatus(msg);
        if (statusLabel != null && directory != null) {
            statusLabel.setText(fileMetadataService.displayPathForStatus(directory));
        }
        updateNavigationButtonsState();
        if (startupInitialDirectoryLoadStarted.get() && startupInitialDirectoryLoadFinished.compareAndSet(false, true)) {
            StartupTrace.mark("initial directory hydration finished: failed");
        }
    }
/**
 * configureViewMenu.
 *
 */
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
        syncPaneTogglesFromUiState();
        setNavigationPaneVisible(showNavigationPane);
        setCompactView(compactView);
        syncViewMenuSelection();
        refreshInspectorPresentationForCurrentContext();
    }

private Node captureWorkspaceFocusOwner() {
    Scene scene = root == null ? null : root.getScene();
    if (scene == null) {
        return null;
    }
    Node focusOwner = scene.getFocusOwner();
    if (focusOwner == null) {
        return null;
    }
    if (focusOwner == fileTable || isDescendantOf(focusOwner, fileTable)) {
        return fileTable;
    }
    if (focusOwner == folderTree || isDescendantOf(focusOwner, folderTree)) {
        return folderTree;
    }
    if (focusOwner == searchField || isDescendantOf(focusOwner, searchField)) {
        return searchField;
    }
    return null;
}

private void restoreWorkspaceFocus(Node preferredFocusTarget) {
    if (preferredFocusTarget == null) {
        return;
    }
    Platform.runLater(() -> {
        if (preferredFocusTarget == fileTable && fileTable != null) {
            fileTable.requestFocus();
        } else if (preferredFocusTarget == folderTree && folderTree != null && folderTree.isManaged()) {
            folderTree.requestFocus();
        } else if (preferredFocusTarget == searchField && searchField != null) {
            searchField.requestFocus();
        }
    });
}

private boolean isDescendantOf(Node child, Node ancestor) {
    if (child == null || ancestor == null) {
        return false;
    }
    Node cursor = child;
    while (cursor != null) {
        if (cursor == ancestor) {
            return true;
        }
        cursor = cursor.getParent();
    }
    return false;
}

/**
 * setDetailsPaneVisible.
 *
 * @param show TODO
 */
    private void setDetailsPaneVisible(boolean show) {
        LogSupport.enter(LOG, "setDetailsPaneVisible");
        Node focusOwner = captureWorkspaceFocusOwner();
        if (show) {
            inspectorMode = InspectorMode.DETAILS;
            rememberInspectorContentMode(InspectorMode.DETAILS);
            sidePaneMasterVisible = true;
        } else if (inspectorMode == InspectorMode.DETAILS) {
            sidePaneMasterVisible = false;
            inspectorMode = operationsToggle != null && operationsToggle.isSelected()
                    ? InspectorMode.OPERATIONS
                    : InspectorMode.HIDDEN;
        }
        updateSidePaneVisibility();
        forceInspectorModePresentation(inspectorMode);
        if (show && fileTable != null) {
            FileItem fi = fileTable.getSelectionModel().getSelectedItem();
            updateSelectionDetails(fi != null ? fi.path() : null);
        }
        syncPaneTogglesFromUiState();
        restoreWorkspaceFocus(focusOwner);
        setStatus(show ? "Details pane shown." : "Details pane hidden.");
    }
/**
 * setPreviewPaneVisible.
 *
 * @param show TODO
 */
    private void setPreviewPaneVisible(boolean show) {
        LogSupport.enter(LOG, "setPreviewPaneVisible");
        Node focusOwner = captureWorkspaceFocusOwner();
        if (show) {
            inspectorMode = InspectorMode.PREVIEW;
            rememberInspectorContentMode(InspectorMode.PREVIEW);
            sidePaneMasterVisible = true;
        } else if (inspectorMode == InspectorMode.PREVIEW) {
            sidePaneMasterVisible = false;
            inspectorMode = operationsToggle != null && operationsToggle.isSelected()
                    ? InspectorMode.OPERATIONS
                    : InspectorMode.HIDDEN;
        }
        updateSidePaneVisibility();
        forceInspectorModePresentation(inspectorMode);
        if (show && fileTable != null) {
            FileItem fi = fileTable.getSelectionModel().getSelectedItem();
            updateSelectionDetails(fi != null ? fi.path() : null);
        }
        syncPaneTogglesFromUiState();
        restoreWorkspaceFocus(focusOwner);
        setStatus(show ? "Preview pane shown." : "Preview pane hidden.");
    }
    /**
     * setSidePaneMasterVisible.
     *
     * Shows/hides the right-hand split item. When showing for the first time, if neither
     * details nor preview content has been selected, we default to the Details pane.
     */
    private void setSidePaneMasterVisible(boolean show) {
        LogSupport.enter(LOG, "setSidePaneMasterVisible");
        Node focusOwner = captureWorkspaceFocusOwner();
        if (show) {
            setDetailsPaneVisible(true);
        } else {
            if (inspectorMode == InspectorMode.DETAILS || inspectorMode == InspectorMode.PREVIEW) {
                inspectorMode = operationsToggle != null && operationsToggle.isSelected()
                        ? InspectorMode.OPERATIONS
                        : InspectorMode.HIDDEN;
            }
            sidePaneMasterVisible = false;
            updateSidePaneVisibility();
            forceInspectorModePresentation(inspectorMode);
            syncPaneTogglesFromUiState();
            restoreWorkspaceFocus(focusOwner);
        }
    }
    /**
     * updateSidePaneVisibility.
     *
     * Collapses the right-hand split item to 0px unless at least one pane inside is visible.
     */
    private void updateSidePaneVisibility() {
        if (homeActive) {
            applyWorkspaceInspectorVisibility(false);
            applyInspectorModeNodeVisibility(InspectorMode.HIDDEN);
            return;
        }
        if (inspectorMode == InspectorMode.HIDDEN && operationsToggle != null && operationsToggle.isSelected()) {
            inspectorMode = InspectorMode.OPERATIONS;
        }
        if (inspectorMode == InspectorMode.OPERATIONS && (operationsToggle == null || !operationsToggle.isSelected())) {
            inspectorMode = sidePaneMasterVisible ? preferredContentInspectorMode() : InspectorMode.HIDDEN;
        }
        boolean show = inspectorMode != InspectorMode.HIDDEN;
        refreshInspectorPresentationForCurrentContext();
        persistWorkspaceShellPreferences();
        Platform.runLater(() -> {
            scheduleResponsiveTableViewportLayoutRefresh();
            scheduleResponsiveIconViewportLayoutRefresh();
        });
    }
/**
 * setNavigationPaneVisible.
 *
 * @param show TODO
 */
    private void setNavigationPaneVisible(boolean show) {
        LogSupport.enter(LOG, "setNavigationPaneVisible");
        showNavigationPane = show;
        if (show) {
            applyNavigationPaneShellWidth(lastKnownNavigationPaneShellWidthPx > 0.0
                    ? lastKnownNavigationPaneShellWidthPx
                    : (NAV_TREE_PREF_WIDTH_PX + (NAV_TREE_SHELL_PADDING_PX * 2.0)));
        }
        if (navigationPaneShell != null) {
            navigationPaneShell.setVisible(show);
            navigationPaneShell.setManaged(show);
            navigationPaneShell.setMinWidth(show ? NAV_TREE_SHELL_MIN_WIDTH_PX : 0.0);
            navigationPaneShell.setPrefWidth(show ? Math.max(NAV_TREE_SHELL_MIN_WIDTH_PX, lastKnownNavigationPaneShellWidthPx) : 0.0);
        }
        if (folderTree != null) {
            folderTree.setVisible(show);
            folderTree.setManaged(show);
            if (show) {
                folderTree.setMinWidth(NAV_TREE_MIN_WIDTH_PX);
                folderTree.setPrefWidth(Math.max(folderTree.getPrefWidth(), NAV_TREE_PREF_WIDTH_PX));
            } else {
                folderTree.setMinWidth(0.0);
                folderTree.setPrefWidth(0.0);
            }
        }
        if (navigationResizer != null) {
            navigationResizer.setVisible(show);
            navigationResizer.setManaged(show);
        }
        persistWorkspaceShellPreferences();
        Platform.runLater(() -> {
            scheduleResponsiveTableViewportLayoutRefresh();
            scheduleResponsiveIconViewportLayoutRefresh();
        });
    }

    private void setCompactView(boolean on) {
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
/**
 * refreshCurrentDirectoryView.
 *
 */
    private void refreshCurrentDirectoryView() {
        LogSupport.enter(LOG, "refreshCurrentDirectoryView");
        Path dir = currentDirectory;
        if (dir == null) {
            return;
        }
        loadDirectoryIntoTableAsync(dir);
    }
/**
 * configureThemeToggle.
 *
 */
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
/**
 * applyThemeToCurrentScene.
 *
 * @param scene TODO
 */
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

    private void bindWindowChromeState(Scene scene) {
        if (scene == null) {
            return;
        }
        Window window = scene.getWindow();
        if (window == null) {
            Platform.runLater(() -> bindWindowChromeState(scene));
            return;
        }
        if (!windowChromeStateInstalled) {
            windowChromeStateInstalled = true;
            window.focusedProperty().addListener((obs, oldV, newV) -> applyWindowChromeState(window));
            if (window instanceof Stage stage) {
                stage.maximizedProperty().addListener((obs, oldV, newV) -> applyWindowChromeState(stage));
            }
        }
        applyWindowChromeState(window);
    }

    private void applyWindowChromeState(Window window) {
        Node target = root != null ? root : (boundScene != null ? boundScene.getRoot() : null);
        if (target == null) {
            return;
        }
        boolean active = window != null && window.isFocused();
        setStyleClass(target, "window-active", active);
        setStyleClass(target, "window-inactive", !active);
        if (window instanceof Stage stage) {
            setStyleClass(target, "window-maximized", stage.isMaximized());
        } else {
            setStyleClass(target, "window-maximized", false);
        }
    }

    private static void setStyleClass(Node node, String styleClass, boolean enabled) {
        if (node == null || styleClass == null || styleClass.isBlank()) {
            return;
        }
        var classes = node.getStyleClass();
        if (enabled) {
            if (!classes.contains(styleClass)) {
                classes.add(styleClass);
            }
        } else {
            classes.remove(styleClass);
        }
    }

    private void updateWindowTitle(Path directory) {
        Scene scene = boundScene != null ? boundScene : (root != null ? root.getScene() : null);
        if (scene == null) {
            return;
        }
        Window window = scene.getWindow();
        if (window instanceof Stage stage) {
            stage.setTitle(computeWindowTitle(directory));
        }
    }

    private String computeWindowTitle(Path directory) {
        if (directory == null) {
            return homeActive ? "Home - FileExplorer" : "FileExplorer";
        }
        String leaf = null;
        try {
            Path fileName = directory.getFileName();
            if (fileName != null) {
                leaf = fileName.toString();
            }
        } catch (Exception ignored) {
        }
        if (leaf == null || leaf.isBlank()) {
            leaf = directory.toString();
        }
        if (leaf == null || leaf.isBlank()) {
            leaf = "FileExplorer";
        }
        return leaf + " - FileExplorer";
    }
/**
 * loadDirectoryIntoTableAsync.
 *
 * @param directory TODO
 */
// Phase 4B.1: reset paging state for a new navigation.
private void resetHugeFolderPaging(java.nio.file.Path directory) {
    hugeFolderModeActive = false;
    hugeFolderPath = directory;
    hugeFolderItems.clear();
    hugeFolderSearchActive = false;
    hugeFolderSearchQuery = "";
    hugeFolderSearchItems.clear();
    hugeFolderPageStart = 0;
    hugeFolderScannedTotal = 0L;
}
// Phase 4B.1: enable huge-folder mode (snapshot current visible list as the first page).
private void enableHugeFolderMode(java.nio.file.Path directory, int pageSize) {
    hugeFolderModeActive = true;
    hugeFolderPath = directory;
    hugeFolderItems.clear();
    hugeFolderSearchActive = false;
    hugeFolderSearchQuery = "";
    hugeFolderSearchItems.clear();
    hugeFolderItems.addAll(tableItems);
    hugeFolderPageStart = 0;
    hugeFolderScannedTotal = Math.max(hugeFolderScannedTotal, hugeFolderItems.size());
    if (statusLabel != null) {
        statusLabel.setText("Large folder: paging enabled (Page 1) — use PageUp/PageDown");
    }
    if (tableItems.size() > pageSize) {
        tableItems.setAll(hugeFolderItems.subList(0, pageSize));
        rebuildTableIndexCache(tableItems);
        updateStatusCounts();
    }
}
// Phase 4B.1: navigate pages in huge-folder mode.
private void navigateHugeFolderPage(int deltaPages) {
    final java.util.List<FileItem> source = (hugeFolderSearchActive ? hugeFolderSearchItems : hugeFolderItems);
    if (!hugeFolderModeActive || source.isEmpty() || fileTable == null) {
        return;
    }
    final int pageSize = Integer.getInteger("fileexplorer.hugeFolder.pageSize",
            Integer.getInteger("fileexplorer.hugeFolder.showLimit", 50000));
    int nextStart = hugeFolderPageStart + (deltaPages * pageSize);
    if (nextStart < 0) nextStart = 0;
    if (nextStart >= source.size()) {
        return;
    }
    hugeFolderPageStart = nextStart;
    int end = Math.min(hugeFolderPageStart + pageSize, source.size());
    java.util.List<FileItem> slice = source.subList(hugeFolderPageStart, end);
    tableItems.setAll(slice);
    rebuildTableIndexCache(tableItems);
    updateStatusCounts();
    try {
        fileTable.requestFocus();
        if (!tableItems.isEmpty()) {
            fileTable.getSelectionModel().clearAndSelect(0);
            fileTable.scrollTo(0);
        }
    } catch (Exception ignored) {}
    if (statusLabel != null) {
        int page = (hugeFolderPageStart / pageSize) + 1;
        long total = Math.max(hugeFolderScannedTotal, source.size());
        statusLabel.setText("Large folder: Page " + page + " — showing " + (hugeFolderPageStart + 1) + "–" + (hugeFolderPageStart + slice.size()) + " of " + total + " (PageUp/PageDown)");
    }
}
private void loadDirectoryIntoTableAsync(Path directory) {
    loadDirectoryIntoTableAsync(directory, false);
}
/**
 * loadDirectoryIntoTableAsync.
 *
 * @param directory directory to open
 * @param keepExistingUntilFirstBatch if true, keep any already-painted (cached) items visible
 *                                   until the first real batch arrives (reduces flicker).
 */
private void loadDirectoryIntoTableAsync(Path directory, boolean keepExistingUntilFirstBatch) {
    LogSupport.enter(LOG, "loadDirectoryIntoTableAsync", directory, keepExistingUntilFirstBatch);
    if (directory == null) {
        return;
    }
    // IMPORTANT: always bind the requested directory to controller state up-front.
    // Several downstream helpers (breadcrumb/status/progressive loader) rely on currentDirectory.
    // If callers pass a directory without having updated currentDirectory first, the table would
    // appear empty because the loader runs against the previous directory.
    Path normalizedDirectory = normalizeDirectoryScope(directory);
    Path previousVisibleScope = currentVisibleDirectoryScope();
    boolean preserveViewportContinuity = java.util.Objects.equals(previousVisibleScope, normalizedDirectory);
    cancelSearchSessionForDirectoryChange(normalizedDirectory);
    currentDirectory = normalizedDirectory;
    noteDirectoryRealizationScopeChanged();
    clearExplorerMetadataTextCache();
    hideExplorerMetadataPopup();
    updateNavigationButtonsState();
    // Phase 4B.1: reset huge-folder paging state for this navigation.
    resetHugeFolderPaging(normalizedDirectory);
// Phase 4A.2/4A.3: cancel any queued thumbnail work when navigating to a new directory.
    try {
        com.fileexplorer.service.icon.AsyncThumbnailService.getInstance().cancelAll();
    } catch (Exception ignored) {
    }
    folderThumbnailWarmupSeq.incrementAndGet();
    folderThumbnailWarmupDebounce.stop();
    // Bump progressive request token (stale completions ignored).
    final long token = progressiveLoadSeq.incrementAndGet();
    // Phase 4C.1: per-folder cancellation scope for budgeted metadata work.
    try {
        if (metadataBudgetService != null) {
            metadataBudgetService.beginScope(token);
        }
    } catch (Exception ignored) {}
    directoryLoading = true;
    // Update chrome immediately so the app doesn't look frozen while IO runs.
    if (breadcrumbBarController != null) {
        breadcrumbBarController.setPath(normalizedDirectory);
    }
    if (statusLabel != null) {
        if (keepExistingUntilFirstBatch && !tableItems.isEmpty()) {
            statusLabel.setText("Refreshing " + fileMetadataService.displayPathForStatus(normalizedDirectory) + " …");
        } else {
            statusLabel.setText("Loading " + fileMetadataService.displayPathForStatus(normalizedDirectory) + " …");
        }
    }
    pendingViewportContinuityState = preserveViewportContinuity
            ? captureViewportContinuityState(normalizedDirectory, token)
            : null;
    setVisibleDirectoryScope(normalizedDirectory);
    // When hydrating over a cached snapshot, keep selection/rows until we have real data.
    if (!keepExistingUntilFirstBatch) {
        if (fileTable != null) {
            fileTable.getSelectionModel().clearSelection();
            if (fileTable.getFocusModel() != null && !preserveViewportContinuity) {
                fileTable.getFocusModel().focus(-1);
            }
        }
        if (!preserveViewportContinuity) {
            clearExplorerPresentationSelectionState();
        }
        tableItems.clear();
        tableIndexByPath.clear();
        updateStatusCounts();
        if (isIconMode(viewMode)) {
            clearIconTiles();
        }
    }
    lastRequestedDirectory = normalizedDirectory;
    lastRequestedShowHidden = showHiddenItems;
    lastRequestedRequestId = token;
    int batchSize = Integer.getInteger("fileexplorer.dirload.chunkSize", 350);
    int firstBatchSize = Integer.getInteger("fileexplorer.dirload.firstBatchSize", Math.min(batchSize, 96));
    if (!startupInitialDirectoryFirstBatchCommitted.get()) {
        batchSize = Integer.getInteger("fileexplorer.dirload.startupBatchSize", Math.max(96, Math.min(batchSize, 160)));
        firstBatchSize = Integer.getInteger("fileexplorer.dirload.startupFirstBatchSize", Math.min(batchSize, 48));
    }
// Phase 4B.1 (tier ~250k): Huge-folder mode with paging (bounded TableView list).
final int hugeThreshold = Integer.getInteger("fileexplorer.hugeFolder.threshold", 50000);
final int hugePageSize = Integer.getInteger("fileexplorer.hugeFolder.pageSize",
        Integer.getInteger("fileexplorer.hugeFolder.showLimit", 50000));
final int hugeStopScanLimit = Integer.getInteger("fileexplorer.hugeFolder.stopScanLimit", 300000);
final java.util.concurrent.atomic.AtomicLong scannedCount = new java.util.concurrent.atomic.AtomicLong(0L);
final java.util.concurrent.atomic.AtomicBoolean hugeMode = new java.util.concurrent.atomic.AtomicBoolean(false);
final java.util.concurrent.atomic.AtomicBoolean scanCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
    final java.util.concurrent.atomic.AtomicBoolean firstBatch = new java.util.concurrent.atomic.AtomicBoolean(true);
    final java.util.concurrent.atomic.AtomicBoolean firstVisualCommit = new java.util.concurrent.atomic.AtomicBoolean(false);
    // Phase 4A.6: snapshot hydration state (diffing + stable scroll/selection).
    final FolderSnapshotCache.FolderSnapshot hydrationSnapshot =
            keepExistingUntilFirstBatch ? activeHydrationSnapshot : null;
    final boolean hydratingOverSnapshot =
            keepExistingUntilFirstBatch && hydrationSnapshot != null && !tableItems.isEmpty();
    final java.util.concurrent.atomic.AtomicInteger hydrationOffset = new java.util.concurrent.atomic.AtomicInteger(0);
    directoryLoadManager.loadProgressive(
            normalizedDirectory,
            showHiddenItems,
            firstBatchSize,
            batchSize,
            batch -> javafx.application.Platform.runLater(() -> {
                if (token != progressiveLoadSeq.get()) return;
// Phase 4B.1: track scanned count and enable huge-folder paging when threshold is crossed.
long scanned = scannedCount.addAndGet(batch.size());
hugeFolderScannedTotal = scanned;
if (!hugeMode.get() && scanned >= hugeThreshold) {
    hugeMode.set(true);
    enableHugeFolderMode(currentDirectory, hugePageSize);
}
// Optionally stop scanning after a hard limit to protect IO/CPU in pathological folders.
if (hugeMode.get() && !scanCancelled.get() && scanned >= hugeStopScanLimit) {
    scanCancelled.set(true);
    try { directoryLoadManager.cancelActive(); } catch (Exception ignored) {}
}
// In huge-folder mode, we keep ALL scanned items in an off-UI list (for paging),
// but we only show one page (hugePageSize) at a time in the TableView.
java.util.List<FileItem> effectiveBatch = batch;
if (hugeMode.get()) {
    hugeFolderItems.addAll(batch);
                // Phase 4B.3: If a huge-folder name filter is active, incrementally extend matches.
                if (hugeFolderSearchActive && hugeFolderSearchQuery != null && !hugeFolderSearchQuery.isEmpty()) {
                    final String qLower = hugeFolderSearchQuery.toLowerCase(java.util.Locale.ROOT);
                    for (FileItem fi : batch) {
                        String name = (fi == null ? null : fi.name());
                        if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(qLower)) {
                            hugeFolderSearchItems.add(fi);
                        }
                    }
                }
    if (hugeFolderPageStart == 0) {
        int remaining = hugePageSize - tableItems.size();
        if (remaining <= 0) {
            if (statusLabel != null) {
                int page = (hugeFolderPageStart / hugePageSize) + 1;
                statusLabel.setText("Large folder: Page " + page + " — showing 1–" + hugePageSize + " of " + scanned + " (PageUp/PageDown)");
            }
            return;
        }
        if (effectiveBatch.size() > remaining) {
            effectiveBatch = batch.subList(0, remaining);
        }
    } else {
        if (statusLabel != null) {
            int page = (hugeFolderPageStart / hugePageSize) + 1;
            statusLabel.setText("Large folder: Page " + page + " — loaded " + scanned + " items (PageUp/PageDown)");
        }
        return;
    }
}
                // Phase 4A.6: If we painted from a snapshot, do not replace the entire list with the first batch.
                // Instead, patch batches in-place (offset-based) so scroll/selection don't "jump" and we avoid duplicates.
                if (keepExistingUntilFirstBatch) {
                    if (hydratingOverSnapshot) {
                        int offset = hydrationOffset.getAndAdd(effectiveBatch.size());
                        boolean isFirstRealBatch = firstBatch.getAndSet(false);
                        applyHydrationBatchDiff(offset, effectiveBatch, isFirstRealBatch, hydrationSnapshot);
                    } else if (firstBatch.getAndSet(false) && !tableItems.isEmpty()) {
                        // keepExistingUntilFirstBatch was requested but we don't have a usable snapshot; fall back.
                        tableItems.setAll(effectiveBatch);
                        rebuildTableIndexCache(tableItems);
                        updateStatusCounts();
                    } else if (firstBatch.getAndSet(false) && tableItems.isEmpty()) {
                        tableItems.setAll(effectiveBatch);
                        rebuildTableIndexCache(tableItems);
                        updateStatusCounts();
                    } else {
                        int base = tableItems.size();
                        tableItems.addAll(effectiveBatch);
                        for (int i = 0; i < effectiveBatch.size(); i++) {
                            FileItem fi = effectiveBatch.get(i);
                            if (fi != null && fi.path() != null) {
                                tableIndexByPath.put(fi.path(), base + i);
                            }
                        }
                        updateStatusCounts();
                    }
                } else {
                    int base = tableItems.size();
                    tableItems.addAll(effectiveBatch);
                    for (int i = 0; i < effectiveBatch.size(); i++) {
                        FileItem fi = effectiveBatch.get(i);
                        if (fi != null && fi.path() != null) {
                            tableIndexByPath.put(fi.path(), base + i);
                        }
                    }
                    updateStatusCounts();
                }
                if (isIconMode(viewMode)) {
                    iconRebuildDebounce.stop();
                    iconRebuildDebounce.setOnFinished(_ -> {
                        if (token != progressiveLoadSeq.get()) return;
                        rebuildIconTiles();
                    });
                    iconRebuildDebounce.playFromStart();
                } else if (fileTable != null) {
                    // Keep table responsive; avoid heavy sort during stream.
                    fileTable.refresh();
                }
                if (firstVisualCommit.compareAndSet(false, true)) {
                    noteStartupInitialDirectoryFirstBatchCommitted();
                }
                scheduleCurrentFolderThumbnailWarmup();
            }),
            () -> javafx.application.Platform.runLater(() -> {
                if (token != progressiveLoadSeq.get()) return;
                directoryLoading = false;
                if (firstVisualCommit.compareAndSet(false, true)) {
                    noteStartupInitialDirectoryFirstBatchCommitted();
                }
                // Phase 4A.6: If we hydrated over a snapshot, truncate any stale tail items that were never produced
                // by the live progressive loader (e.g., deleted files), and drop the active snapshot handle.
                if (hydratingOverSnapshot) {
                    try {
                        int liveCount = hydrationOffset.get();
                        if (liveCount >= 0 && liveCount < tableItems.size()) {
                            tableItems.subList(liveCount, tableItems.size()).clear();
                            rebuildTableIndexCache(tableItems);
                        }
                    } catch (Exception ignored) {
                    }
                    activeHydrationSnapshot = null;
                }
                // Finalize status
                if (statusLabel != null) {
                    if (hugeMode.get()) {
                        long scanned = scannedCount.get();
                        int shown = tableItems.size();
                        if (scanCancelled.get()) {
                            statusLabel.setText("Large folder: showing " + shown + " of " + scanned + " (scan capped) — " +
                                    fileMetadataService.displayPathForStatus(currentDirectory));
                        } else {
                            statusLabel.setText("Large folder: showing " + shown + " of " + scanned + " — " +
                                    fileMetadataService.displayPathForStatus(currentDirectory));
                        }
                    } else {
                        statusLabel.setText(fileMetadataService.displayPathForStatus(currentDirectory));
                    }
                }
                updateStatusCounts();
                if (isIconMode(viewMode)) {
                    iconRebuildDebounce.stop();
                    rebuildIconTiles();
                }
                ViewportContinuityState continuityState = pendingViewportContinuityState;
                pendingViewportContinuityState = null;
                javafx.application.Platform.runLater(() -> restoreViewportContinuityState(continuityState, token));
                scheduleViewportScopedRealizationRefresh();
                scheduleCurrentFolderThumbnailWarmup();
            }),
            err -> javafx.application.Platform.runLater(() -> {
                if (token != progressiveLoadSeq.get()) return;
                directoryLoading = false;
                pendingViewportContinuityState = null;
                // If we had a cached view visible, keep it; just show error.
                setStatus("Failed to open folder: " + currentDirectory);
            })
    );
}
/**
 * rebuildTableIndexCache.
 *
 * @param items TODO
 */
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
/**
 * indexOfTableItem.
 *
 * @param p TODO
 * @return TODO
 */
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
    // ---------------------------------------------------------------------
    // Phase 4B.2: Budgeted metadata fill (lowest CPU)
    // ---------------------------------------------------------------------

private void startFillAllMetadataPassIfNeeded(long requestId) {
    if (fileMetadataService == null || ioExecutor == null || !isIoExecutorAvailable()) return;
    if (fillAllMetadataSeq.get() == requestId && fillAllMetadataRunning.get()) return;
    // Only start one fill-all pass per directory-load sequence.
    if (!fillAllMetadataRunning.compareAndSet(false, true)) return;
    fillAllMetadataSeq.set(requestId);

    // Snapshot the current table items on the FX thread.
    final java.util.ArrayList<java.nio.file.Path> targets = new java.util.ArrayList<>(tableItems.size());
    for (int i = 0; i < tableItems.size(); i++) {
        FileItem fi = tableItems.get(i);
        if (fi == null || fi.path() == null) continue;
        if (!isBlank(fi.size()) && !isBlank(fi.modified())) continue;
        targets.add(fi.path());
    }

    final int perSecond = Math.max(1, Integer.getInteger("fileexplorer.metadata.fillAllStatsPerSecond", 25));
    final long nanosPer = 1_000_000_000L / perSecond;

    executeOnIoExecutor("startFillAllMetadataPassIfNeeded", () -> {
        long next = System.nanoTime();
        try {
            for (java.nio.file.Path p : targets) {
                if (p == null) continue;
                if (requestId != progressiveLoadSeq.get()) return; // canceled by new load

                // Rate-limit
                long now = System.nanoTime();
                if (now < next) {
                    try { java.util.concurrent.locks.LockSupport.parkNanos(next - now); } catch (Exception ignored) {}
                }
                next = Math.max(next + nanosPer, System.nanoTime());

                // Fetch metadata (best-effort, one path at a time).
                String type = "";
                String size = "";
                String mod = "";
                try { type = fileMetadataService.detectFileType(p); } catch (Exception ignored) {}
                try { size = fileMetadataService.humanReadableSizeForTable(p); } catch (Exception ignored) {}
                try { mod = fileMetadataService.lastModifiedLocalString(p); } catch (Exception ignored) {}

                final com.fileexplorer.service.filesystem.FileMetadataBudgetService.Metadata meta =
                        new com.fileexplorer.service.filesystem.FileMetadataBudgetService.Metadata(type, size, mod);

                // Coalesced UI update (enqueueMetadataUpdate handles batching/debouncing).
                try { enqueueMetadataUpdate(p, meta); } catch (Exception ignored) {}
            }
        } finally {
            fillAllMetadataRunning.set(false);
        }
    });
}
    private void initVisibleMetadataDebounce() {
        if (visibleMetadataDebounce != null) return;
        int ms = Integer.getInteger("fileexplorer.metadata.visibleDebounceMs", 180);
        visibleMetadataDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(ms));
        visibleMetadataDebounce.setOnFinished(_ -> {
            visibleMetadataDebounceArmed.set(false);
            requestMetadataForVisibleApprox();
        });
    }

    private void armVisibleMetadataRequest() {
        if (metadataBudgetService == null) return;
        initVisibleMetadataDebounce();
        // Don't spam: re-arm only once per debounce window.
        if (visibleMetadataDebounceArmed.compareAndSet(false, true)) {
            javafx.application.Platform.runLater(() -> visibleMetadataDebounce.playFromStart());
        } else {
            javafx.application.Platform.runLater(() -> visibleMetadataDebounce.playFromStart());
        }
    }

    private void requestMetadataForVisibleApprox() {
        if (metadataBudgetService == null || fileTable == null) return;
        if (tableItems == null || tableItems.isEmpty()) return;
        final long requestId = progressiveLoadSeq.get();

        // If there is a selection, the existing focus handler already does USER+VISIBLE.
        int selectedIdx = fileTable.getSelectionModel() != null ? fileTable.getSelectionModel().getSelectedIndex() : -1;
        if (selectedIdx >= 0) return;

        int idx = -1;
        if (fileTable.getFocusModel() != null) idx = fileTable.getFocusModel().getFocusedIndex();
        if (idx < 0) idx = 0;

        final int radius = Integer.getInteger("fileexplorer.metadata.visibleRadius", 12);
        int start;
        int end;
        // If folder size is modest (<= fillAllMax) and not in huge-folder mode,
        // request metadata for all rows so Size/Modified populate without clicking.
        final int fillAllMax = Integer.getInteger("fileexplorer.metadata.fillAllMax", 5000);
        if (!hugeFolderModeActive && tableItems.size() <= fillAllMax) {
            // Fill-all mode: populate Size/Modified for every row (bounded) without requiring clicks.
            startFillAllMetadataPassIfNeeded(requestId);
            return;
        } else {
            // Wider window than the selection-neighborhood, but still bounded and low rate.
            start = Math.max(0, idx - radius * 2);
            end = Math.min(tableItems.size(), idx + radius * 4 + 1);
        }
        for (int i = start; i < end; i++) {
            FileItem fi = tableItems.get(i);
            if (fi == null || fi.path() == null) continue;
            if (!isBlank(fi.size()) && !isBlank(fi.modified())) continue;
            final Path p = fi.path();
            metadataBudgetService.request(p,
                    com.fileexplorer.service.filesystem.FileMetadataBudgetService.Priority.VISIBLE,
                    meta -> {
                        if (requestId != progressiveLoadSeq.get()) return;
                        enqueueMetadataUpdate(p, meta);
                    });
        }
    }

    private void requestMetadataForFocus(Path focused) {
        if (focused == null || metadataBudgetService == null) return;
        if (fileTable == null) return;
        final long requestId = progressiveLoadSeq.get();
        int idx = indexOfTableItem(focused);
        if (idx < 0 || idx >= tableItems.size()) return;
        // Highest priority: selected row
        FileItem selected = tableItems.get(idx);
        if (selected != null && (isBlank(selected.size()) || isBlank(selected.modified()))) {
            metadataBudgetService.request(selected.path(),
                    com.fileexplorer.service.filesystem.FileMetadataBudgetService.Priority.USER,
                    meta -> {
                        if (requestId != progressiveLoadSeq.get()) return;
                        enqueueMetadataUpdate(selected.path(), meta);
                    });
        }
        // Nearby rows: very small neighborhood to keep CPU low
        final int radius = Integer.getInteger("fileexplorer.metadata.visibleRadius", 12);
        int start;
        int end;
        // If folder size is modest (<= fillAllMax) and not in huge-folder mode,
        // request metadata for all rows so Size/Modified populate without clicking.
        final int fillAllMax = Integer.getInteger("fileexplorer.metadata.fillAllMax", 5000);
        if (!hugeFolderModeActive && tableItems.size() <= fillAllMax) {
            // Fill-all mode: populate Size/Modified for every row (bounded) without requiring clicks.
            startFillAllMetadataPassIfNeeded(requestId);
            return;
        } else {
            // Wider window than the selection-neighborhood, but still bounded and low rate.
            start = Math.max(0, idx - radius * 2);
            end = Math.min(tableItems.size(), idx + radius * 4 + 1);
        }
        for (int i = start; i < end; i++) {
            if (i == idx) continue;
            FileItem fi = tableItems.get(i);
            if (fi == null || fi.path() == null) continue;
            if (!isBlank(fi.size()) && !isBlank(fi.modified())) continue;
            final Path p = fi.path();
            metadataBudgetService.request(p,
                    com.fileexplorer.service.filesystem.FileMetadataBudgetService.Priority.VISIBLE,
                    meta -> {
                        if (requestId != progressiveLoadSeq.get()) return;
                        enqueueMetadataUpdate(p, meta);
                    });
        }
    }
    private void enqueueMetadataUpdate(java.nio.file.Path p,
                                       com.fileexplorer.service.filesystem.FileMetadataBudgetService.Metadata meta) {
        if (p == null || meta == null) return;
        // Callback may arrive off the FX thread; just queue the update and arm one FX-thread flush.
        pendingMetadataUpdates.put(p, meta);
        if (metadataFlushArmed.compareAndSet(false, true)) {
            javafx.application.Platform.runLater(() -> {
                // Re-arm on FX thread; coalesce frequent updates.
                metadataFlushDebounce.playFromStart();
            });
        }
    }
    private void flushPendingMetadataUpdates() {
        // Runs on FX thread (PauseTransition).
        metadataFlushArmed.set(false);
        if (pendingMetadataUpdates.isEmpty()) return;
        java.util.Map<java.nio.file.Path, com.fileexplorer.service.filesystem.FileMetadataBudgetService.Metadata> batch =
                new java.util.HashMap<>(pendingMetadataUpdates);
        pendingMetadataUpdates.clear();
        for (java.util.Map.Entry<java.nio.file.Path, com.fileexplorer.service.filesystem.FileMetadataBudgetService.Metadata> e : batch.entrySet()) {
            applyMetadata(e.getKey(), e.getValue());
        }
        requestCoalescedTableRefresh();
    }
    private void requestCoalescedTableRefresh() {
        if (fileTable == null || isIconMode(viewMode)) {
            return;
        }
        if (detailsHoverRowIndex.get() >= 0) {
            tableRefreshDeferredWhileHover = true;
            return;
        }
        requestCoalescedTableRefreshNow();
    }

    private void requestCoalescedTableRefreshNow() {
        if (fileTable == null || isIconMode(viewMode)) {
            return;
        }
        if (tableRefreshQueued.compareAndSet(false, true)) {
            tableRefreshDebounce.playFromStart();
        } else {
            tableRefreshDebounce.playFromStart();
        }
    }

    private void applyMetadata(Path p, com.fileexplorer.service.filesystem.FileMetadataBudgetService.Metadata meta) {
        if (p == null || meta == null) return;
        int idx = indexOfTableItem(p);
        if (idx < 0 || idx >= tableItems.size()) return;
        FileItem old = tableItems.get(idx);
        if (old == null || old.path() == null) return;
        if (!old.path().equals(p)) return;
        // If values already filled, do nothing.
        if (!isBlank(old.size()) && !isBlank(old.modified()) && !isBlank(old.type())) return;
        String type = meta.type() != null ? meta.type() : old.type();
        String size = meta.size() != null ? meta.size() : old.size();
        String mod  = meta.modified() != null ? meta.modified() : old.modified();
        FileItem updated = new FileItem(old.path(), old.name(), type, size, mod, old.status());
        tableItems.set(idx, updated);
        tableIndexByPath.put(p, idx);
        invalidateExplorerMetadataTextCache(p);
    }
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
/**
 * updateSelectionDetails.
 *
 * @param selected TODO
 */
    private void updateSelectionDetails(Path selected) {
        LogSupport.enter(LOG, "updateSelectionDetails");
        // Status bar
        if (selected == null) {
            setStatus("Ready.");
        } else {
            String status = fileMetadataService.describeForStatusBar(selected);
            setStatus(status);
        }
        // Details text
        if (detailsText != null) {
            detailsText.setText(buildDetailsPaneText(selected));
        }
        boolean keepResolvedPreview = isResolvedPreviewBoundTo(selected) || wasResolvedPreviewRenderedFor(selected);
        boolean previewThumbCandidate = selected != null && previewImage != null
                && ImageSupport.isThumbCandidate(selected) && Files.isRegularFile(selected);
        boolean retainRenderedPreviewOnTransientNull = selected == null && shouldRetainDisplayedPreviewOnTransientNullSelection();

        if (selected != null) {
            previewClearDebounce.stop();
        }

        // Preview: keep the resolved image in place for the same selected path while refresh churn
        // or a replacement thumbnail request is pending. For thumbnail candidates, do not swap a
        // temporary placeholder icon back over a successfully resolved preview.
        if (previewImage != null && !retainRenderedPreviewOnTransientNull) {
            previewImage.getProperties().put("previewPath", selected);
            String previewIdentity = selected == null ? null : resolveIconIdentityForPath(selected);
            previewImage.getProperties().put("previewIdentity", previewIdentity);
            if (!keepResolvedPreview) {
                previewImage.getProperties().put("previewResolved", Boolean.FALSE);
                Object lastResolvedPath = previewImage.getProperties().get("previewLastResolvedPath");
                if (previewThumbCandidate) {
                    if (!java.util.Objects.equals(lastResolvedPath, selected)) {
                        previewImage.setImage(null);
                    }
                } else {
                    previewImage.setImage(null);
                    previewImage.getProperties().put("previewLastResolvedPath", null);
                }
            }
        }
        if (previewText != null && !keepResolvedPreview && !retainRenderedPreviewOnTransientNull) {
            previewText.setText(previewThumbCandidate
                    ? "Loading preview...\n\n" + selected
                    : buildPreviewFallbackText(selected));
            previewText.setVisible(true);
            previewText.setManaged(true);
        }
        if (selected == null) {
            pendingPreviewPath = null;
            previewLoadSeq.incrementAndGet();
            previewLoadDebounce.stop();
            if (retainRenderedPreviewOnTransientNull) {
                previewClearDebounce.setDuration(javafx.util.Duration.millis(
                        Long.getLong("fileexplorer.preview.transientNullClearDebounceMs", 900L)
                ));
                previewClearDebounce.playFromStart();
                return;
            }
            previewClearDebounce.setDuration(javafx.util.Duration.millis(
                    Long.getLong("fileexplorer.preview.clearDebounceMs", 220L)
            ));
            previewClearDebounce.playFromStart();
            return;
        }
        // Only do heavier preview work if the preview pane is enabled/visible.
        boolean previewEnabled = inspectorMode == InspectorMode.PREVIEW;
        if (!previewEnabled) {
            pendingPreviewPath = null;
            previewLoadSeq.incrementAndGet();
            previewLoadDebounce.stop();
            return;
        }
        if (previewThumbCandidate) {
            if (keepResolvedPreview) {
                pendingPreviewPath = null;
                previewLoadDebounce.stop();
            } else {
                pendingPreviewPath = selected;
                previewLoadSeq.incrementAndGet();
                previewLoadDebounce.playFromStart();
            }
        }
    }

    private void loadPreviewThumbnailNow(long ticket, Path selected) {
        if (selected == null || previewImage == null) {
            return;
        }
        boolean previewEnabled = inspectorMode == InspectorMode.PREVIEW;
        if (!previewEnabled) {
            return;
        }
        if (!ImageSupport.isThumbCandidate(selected) || !Files.isRegularFile(selected)) {
            return;
        }
        int px = currentPreviewRenderTargetSizePx();
        final Path captured = selected;
        final long stamp = ticket;
        final String capturedIdentity = resolveIconIdentityForPath(captured);
        boolean keepExistingResolvedPreview = isResolvedPreviewBoundTo(captured) || wasResolvedPreviewRenderedFor(captured);
        previewImage.getProperties().put("previewStamp", stamp);
        previewImage.getProperties().put("previewPath", captured);
        previewImage.getProperties().put("previewIdentity", capturedIdentity);
        if (!keepExistingResolvedPreview) {
            previewImage.getProperties().put("previewResolved", Boolean.FALSE);
        }
        AsyncThumbnailService.getInstance()
                .request(captured, px, AsyncThumbnailService.RequestPriority.USER_ACTION)
                .thenAccept(img -> Platform.runLater(() -> {
                    if (img == null || previewImage == null) {
                        return;
                    }
                    Object s = previewImage.getProperties().get("previewStamp");
                    Object boundPath = previewImage.getProperties().get("previewPath");
                    Object boundIdentity = previewImage.getProperties().get("previewIdentity");
                    if (!(s instanceof Long) || ((Long) s) != stamp) {
                        return;
                    }
                    if (!java.util.Objects.equals(boundPath, captured)) {
                        return;
                    }
                    if (!java.util.Objects.equals(boundIdentity, capturedIdentity)) {
                        return;
                    }
                    previewImage.setImage(img);
                    previewImage.getProperties().put("previewResolved", Boolean.TRUE);
                    previewImage.getProperties().put("previewLastResolvedPath", captured);
                    if (previewText != null) {
                        previewText.setVisible(false);
                        previewText.setManaged(false);
                    }
                }));
    }

    private int currentPreviewRenderTargetSizePx() {
        int px = 280;
        try {
            if (previewImage != null) {
                double fitWidth = previewImage.getFitWidth();
                double fitHeight = previewImage.getFitHeight();
                px = (int) Math.round(Math.max(Math.max(fitWidth, fitHeight), 16.0));
            }
        } catch (Exception ignored) {
        }
        return Math.max(16, px);
    }

    private int currentPreviewPlaceholderSizePx() {
        return currentPreviewRenderTargetSizePx();
    }

    private boolean isResolvedPreviewBoundTo(Path selected) {
        if (selected == null || previewImage == null) {
            return false;
        }
        Object boundPath = previewImage.getProperties().get("previewPath");
        Object resolved = previewImage.getProperties().get("previewResolved");
        return java.util.Objects.equals(boundPath, selected) && java.lang.Boolean.TRUE.equals(resolved);
    }

    private boolean wasResolvedPreviewRenderedFor(Path selected) {
        if (selected == null || previewImage == null || previewImage.getImage() == null) {
            return false;
        }
        Object lastResolvedPath = previewImage.getProperties().get("previewLastResolvedPath");
        return java.util.Objects.equals(lastResolvedPath, selected);
    }

    private boolean shouldRetainDisplayedPreviewOnTransientNullSelection() {
        if (inspectorMode != InspectorMode.PREVIEW || previewImage == null || previewImage.getImage() == null) {
            return false;
        }
        Object lastResolvedPath = previewImage.getProperties().get("previewLastResolvedPath");
        return lastResolvedPath instanceof Path;
    }

    private String resolveIconIdentityForPath(Path path) {
        try {
            if (fileMetadataService != null) {
                String identity = fileMetadataService.iconIdentity(path);
                if (identity != null && !identity.isBlank()) {
                    return identity;
                }
            }
        } catch (Exception ignored) {
        }
        return IconLoader.identityForPath(path);
    }

    private Image resolvePlaceholderImageForPath(Path path, int px) {
        try {
            return IconLoader.loadForIdentity(resolveIconIdentityForPath(path), themeService != null && themeService.isDarkPreferred(), Math.max(16, px));
        } catch (Exception ex) {
            return null;
        }
    }


    private void scheduleCurrentFolderThumbnailWarmup() {
        if (SAFE_MODE) {
            return;
        }
        if (!startupThumbnailWarmupGateOpened.get()) {
            return;
        }
        if (!Boolean.parseBoolean(System.getProperty("fileexplorer.thumb.warmup.enabled", "true"))) {
            return;
        }
        folderThumbnailWarmupSeq.incrementAndGet();
        folderThumbnailWarmupDebounce.playFromStart();
    }

    private void warmCurrentFolderThumbnailsNow(long ticket) {
        if (ticket != folderThumbnailWarmupSeq.get()) {
            return;
        }
        if (SAFE_MODE) {
            return;
        }
        if (!startupThumbnailWarmupGateOpened.get()) {
            return;
        }
        if (!Boolean.parseBoolean(System.getProperty("fileexplorer.thumb.warmup.enabled", "true"))) {
            return;
        }
        int maxItems = Integer.getInteger("fileexplorer.thumb.warmup.maxItems", isIconMode(viewMode) ? 96 : 48);
        if (maxItems <= 0) {
            return;
        }
        int warmPx = currentFolderWarmupThumbnailSizePx();
        if (warmPx <= 0) {
            return;
        }

        java.util.List<Path> candidates = new java.util.ArrayList<>(Math.min(maxItems, 128));
        javafx.collections.ObservableList<FileItem> visibleItems = null;
        try {
            if (fileTable != null) {
                visibleItems = fileTable.getItems();
            }
        } catch (Exception ignored) {
        }
        Iterable<FileItem> source = (visibleItems != null && !visibleItems.isEmpty()) ? visibleItems : tableItems;
        if (source == null) {
            return;
        }

        for (FileItem fi : source) {
            if (fi == null) {
                continue;
            }
            Path p = fi.path();
            if (p == null) {
                continue;
            }
            if ("Folder".equalsIgnoreCase(java.util.Objects.requireNonNullElse(fi.type(), ""))) {
                continue;
            }
            if (!ImageSupport.isThumbCandidate(p)) {
                continue;
            }
            candidates.add(p);
            if (candidates.size() >= maxItems) {
                break;
            }
        }

        if (candidates.isEmpty()) {
            return;
        }
        AsyncThumbnailService.getInstance().warm(candidates, warmPx);
    }

    private int currentFolderWarmupThumbnailSizePx() {
        if (viewMode == null) {
            return 18;
        }
        return switch (viewMode) {
            case DETAILS -> 18;
            case LIST -> 20;
            case SMALL_ICONS -> 64;
            case MEDIUM_ICONS -> 88;
            case LARGE_ICONS -> 120;
            case EXTRA_LARGE_ICONS -> 256;
            case TILES -> 96;
            case CONTENT -> 72;
            default -> (int) Math.round(Math.max(18.0, Math.min(256.0, iconSizePx)));
        };
    }

    private void configureSidePaneParity() {
        if (inspectorHost != null && !inspectorHost.getStyleClass().contains("explorer-inspector-host")) {
            inspectorHost.getStyleClass().add("explorer-inspector-host");
        }
        if (sidePane != null && !sidePane.getStyleClass().contains("explorer-side-pane")) {
            sidePane.getStyleClass().add("explorer-side-pane");
        }
        if (previewBox != null && !previewBox.getStyleClass().contains("preview-pane-card")) {
            previewBox.getStyleClass().add("preview-pane-card");
        }
        if (detailsBox != null && !detailsBox.getStyleClass().contains("details-pane-card")) {
            detailsBox.getStyleClass().add("details-pane-card");
        }
        if (operationsBox != null && !operationsBox.getStyleClass().contains("operations-pane-card")) {
            operationsBox.getStyleClass().add("operations-pane-card");
        }
        if (previewText != null && !previewText.getStyleClass().contains("side-pane-text")) {
            previewText.getStyleClass().add("side-pane-text");
        }
        if (detailsText != null && !detailsText.getStyleClass().contains("side-pane-text")) {
            detailsText.getStyleClass().add("side-pane-text");
        }
        if (previewImage != null) {
            previewImage.setPreserveRatio(true);
        }
    }

    private String buildDetailsPaneText(Path selected) {
        if (selected == null) {
            return "Select an item to see details.";
        }
        StringBuilder sb = new StringBuilder(256);
        String displayName = displayNameForTable(selected);
        String type = fileMetadataService.detectFileType(selected);
        String modified = fileMetadataService.lastModifiedLocalString(selected);
        String size = fileMetadataService.humanReadableSize(selected);
        sb.append(displayName == null || displayName.isBlank() ? selected.getFileName() : displayName).append("\n");
        if (type != null && !type.isBlank()) {
            sb.append(type).append("\n");
        }
        sb.append("\nLocation\n").append(selected).append("\n");
        if (modified != null && !modified.isBlank()) {
            sb.append("\nDate modified\n").append(modified).append("\n");
        }
        if (size != null && !size.isBlank()) {
            sb.append("\nSize\n").append(size).append("\n");
        }
        try {
            if (Files.isDirectory(selected)) {
                sb.append("\nKind\nFolder\n");
            } else if (Files.isRegularFile(selected)) {
                String fileName = selected.getFileName() == null ? "" : selected.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                String ext = dot >= 0 && dot + 1 < fileName.length() ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
                if (!ext.isBlank()) {
                    sb.append("\nExtension\n.").append(ext).append("\n");
                }
            }
            sb.append("\nAttributes\n")
              .append(Files.isDirectory(selected) ? "Folder" : "File")
              .append(Files.isHidden(selected) ? ", Hidden" : "")
              .append(Files.isReadable(selected) ? ", Readable" : "")
              .append(Files.isWritable(selected) ? ", Writable" : ", Read-only")
              .append("\n");
        } catch (Exception ignored) {
            // Best-effort metadata only.
        }
        return sb.toString();
    }

    private String buildPreviewFallbackText(Path selected) {
        if (selected == null) {
            return "Select an item to preview.";
        }
        if (Files.isDirectory(selected)) {
            return "Folder preview is not available.\n\n" + selected;
        }
        String textPreview = readTextPreview(selected);
        if (textPreview != null && !textPreview.isBlank()) {
            return textPreview;
        }
        return selected.toString();
    }

    private String readTextPreview(Path selected) {
        try {
            if (selected == null || !Files.isRegularFile(selected)) {
                return null;
            }
            String name = selected.getFileName() == null ? "" : selected.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean textLike = name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log")
                    || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".yaml")
                    || name.endsWith(".yml") || name.endsWith(".properties") || name.endsWith(".ini")
                    || name.endsWith(".csv") || name.endsWith(".java") || name.endsWith(".kt")
                    || name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".css")
                    || name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".fxml")
                    || name.endsWith(".sql") || name.endsWith(".bat") || name.endsWith(".cmd")
                    || name.endsWith(".ps1") || name.endsWith(".sh");
            if (!textLike) {
                return null;
            }
            long size = Files.size(selected);
            if (size > 262_144L) {
                return "Preview not shown for files larger than 256 KB.\n\n" + selected;
            }
            byte[] bytes = Files.readAllBytes(selected);
            int limit = Math.min(bytes.length, 8_192);
            for (int i = 0; i < limit; i++) {
                if (bytes[i] == 0) {
                    return null;
                }
            }
            String preview = new String(bytes, 0, limit, StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            if (preview.isBlank()) {
                return "This file is empty.\n\n" + selected;
            }
            if (bytes.length > limit) {
                preview = preview + "\n\n…";
            }
            return preview;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // View mode (Details vs Large icons)
    // ---------------------------------------------------------------------

    private void initializeFileViewModules() {
        if (viewHost == null || modularFileViewHost != null) {
            return;
        }
        modularFileViewHost = new FileViewHost(viewHost);
        ensureDetailsFileViewLoaded();
    }

    private void ensureDetailsFileViewLoaded() {
        if (modularFileViewHost == null || modularDetailsViewController != null) {
            return;
        }
        try {
            StartupTrace.mark("file-view details load begin");
            modularDetailsViewController = (DetailsViewController) modularFileViewHost.ensureLoaded(
                    "details",
                    "/com/fileexplorer/ui/fileview/details/DetailsView.fxml");
            StartupTrace.mark("file-view details load end");
            detailsViewShell = modularDetailsViewController.getDetailsViewShell();
            fileTable = modularDetailsViewController.getFileTable();
            colName = (TableColumn<FileItem, String>) modularDetailsViewController.getColName();
            colStatus = (TableColumn<FileItem, Node>) modularDetailsViewController.getColStatus();
            colType = (TableColumn<FileItem, String>) modularDetailsViewController.getColType();
            colSize = (TableColumn<FileItem, String>) modularDetailsViewController.getColSize();
            colModified = (TableColumn<FileItem, String>) modularDetailsViewController.getColModified();
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to load modular Details view", ex);
        }
    }

    private AbstractIconFlowFileViewController ensureIconFileViewLoaded(ViewMode mode) {
        if (modularFileViewHost == null || mode == null || !isIconMode(mode)) {
            return null;
        }
        AbstractIconFlowFileViewController existing = modularIconViewControllers.get(mode);
        if (existing != null) {
            return existing;
        }
        String viewKey = fileViewKeyFor(mode);
        String resource = fileViewResourceFor(mode);
        if (viewKey == null || resource == null) {
            return null;
        }
        try {
            StartupTrace.mark("file-view " + viewKey + " load begin");
            AbstractIconFlowFileViewController controller = (AbstractIconFlowFileViewController) modularFileViewHost.ensureLoaded(viewKey, resource);
            StartupTrace.mark("file-view " + viewKey + " load end");
            modularIconViewControllers.put(mode, controller);
            return controller;
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to load modular icon file view for " + mode, ex);
            return null;
        }
    }

    private void activateFileViewForMode(ViewMode mode) {
        initializeFileViewModules();
        if (modularFileViewHost == null || mode == null) {
            return;
        }
        if (isTableMode(mode)) {
            ensureDetailsFileViewLoaded();
            modularFileViewHost.activate("details");
            if (modularDetailsViewController != null) {
                detailsViewShell = modularDetailsViewController.getDetailsViewShell();
                fileTable = modularDetailsViewController.getFileTable();
            }
            return;
        }
        AbstractIconFlowFileViewController controller = ensureIconFileViewLoaded(mode);
        if (controller == null) {
            return;
        }
        iconScroll = controller.getIconScroll();
        iconFlow = controller.getIconFlow();
        modularFileViewHost.activate(fileViewKeyFor(mode));
        installIconScrollPaging();
        ensureVirtualIconViewsInstalled();
        bringIconMarqueeOverlayToFront();
    }

    private String fileViewKeyFor(ViewMode mode) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> "extra-large-icons";
            case LARGE_ICONS -> "large-icons";
            case MEDIUM_ICONS -> "medium-icons";
            case SMALL_ICONS -> "small-icons";
            case LIST -> "list";
            case DETAILS -> "details";
            case TILES -> "tiles";
            case CONTENT -> "content";
        };
    }

    private String fileViewResourceFor(ViewMode mode) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> "/com/fileexplorer/ui/fileview/extralargeicons/ExtraLargeIconsView.fxml";
            case LARGE_ICONS -> "/com/fileexplorer/ui/fileview/largeicons/LargeIconsView.fxml";
            case MEDIUM_ICONS -> "/com/fileexplorer/ui/fileview/mediumicons/MediumIconsView.fxml";
            case SMALL_ICONS -> "/com/fileexplorer/ui/fileview/smallicons/SmallIconsView.fxml";
            case LIST -> "/com/fileexplorer/ui/fileview/listview/ListFileView.fxml";
            case DETAILS -> "/com/fileexplorer/ui/fileview/details/DetailsView.fxml";
            case TILES -> "/com/fileexplorer/ui/fileview/tiles/TilesFileView.fxml";
            case CONTENT -> "/com/fileexplorer/ui/fileview/content/ContentFileView.fxml";
        };
    }

/**
 * setViewMode.
 *
 * @param mode TODO
 */
    private void setViewMode(ViewMode mode) {
        LogSupport.enter(LOG, "setViewMode");
        if (mode == null) {
            return;
        }
        hideExplorerTransientUi();
        if (SAFE_MODE && !isTableMode(mode)) {
            // Safe Mode: force table-based views only.
            mode = ViewMode.DETAILS;
        }
        viewMode = mode;
        activateFileViewForMode(viewMode);
        if (isIconMode(viewMode)) {
            LogSupport.enter(LOG, "isIconMode");
            lastIconViewMode = viewMode;
            applyIconSizePreset(viewMode);
        }
        boolean tableMode = isTableMode(viewMode);
        boolean showTable = !homeActive && tableMode;
        boolean showIcons = !homeActive && !tableMode;
        if (detailsViewShell != null) {
            detailsViewShell.setVisible(showTable);
            detailsViewShell.setManaged(showTable);
        }
        if (fileTable != null) {
            fileTable.setVisible(showTable);
            fileTable.setManaged(showTable);
        }
        if (iconScroll != null) {
            iconScroll.setVisible(showIcons);
            iconScroll.setManaged(showIcons);
        }
        if (homePane != null) {
            homePane.setVisible(homeActive);
            homePane.setManaged(homeActive);
        }
        applyTableColumnMode(viewMode);
        if (iconFlow != null) {
            configureIconFlowLayout(viewMode);
            applyIconFlowModeClasses(viewMode);
            applyIconFlowPadding(viewMode);
        }
        syncViewMenuSelection();
        refreshInspectorPresentationForCurrentContext();
        if (!homeActive && !SAFE_MODE && isIconMode(viewMode)) {
            rebuildIconTiles();
            Platform.runLater(this::scheduleResponsiveIconViewportLayoutRefresh);
        } else {
            clearIconTiles();
        }
        if (showTable) {
            Platform.runLater(this::scheduleResponsiveTableViewportLayoutRefresh);
        }
        setStatus("View: " + viewModeLabel(viewMode));
    }
/**
 * isTableMode.
 *
 * @param mode TODO
 * @return TODO
 */
    private boolean isTableMode(ViewMode mode) {
        LogSupport.enter(LOG, "isTableMode");
        return mode == ViewMode.DETAILS;
    }
/**
 * isIconMode.
 *
 * @param mode TODO
 * @return TODO
 */
    private boolean isIconMode(ViewMode mode) {
        LogSupport.enter(LOG, "isIconMode");
        return mode == ViewMode.EXTRA_LARGE_ICONS
                || mode == ViewMode.LARGE_ICONS
                || mode == ViewMode.MEDIUM_ICONS
                || mode == ViewMode.SMALL_ICONS
                || mode == ViewMode.LIST
                || mode == ViewMode.TILES
                || mode == ViewMode.CONTENT;
    }

    private boolean isGridIconMode(ViewMode mode) {
        return mode == ViewMode.EXTRA_LARGE_ICONS
                || mode == ViewMode.LARGE_ICONS
                || mode == ViewMode.MEDIUM_ICONS
                || mode == ViewMode.SMALL_ICONS;
    }

    private void configureIconFlowLayout(ViewMode mode) {
        if (iconFlow == null) {
            return;
        }
        boolean verticalFlow = mode == ViewMode.LIST || mode == ViewMode.TILES;
        if (verticalFlow) {
            iconFlow.setOrientation(javafx.geometry.Orientation.VERTICAL);
            iconFlow.setPrefWrapLength(resolveIconFlowWrapLength(mode, true));
            iconFlow.setHgap(iconListFlowHgapForMode(mode));
            iconFlow.setVgap(iconListFlowVgapForMode(mode));
        } else if (mode == ViewMode.CONTENT) {
            iconFlow.setOrientation(javafx.geometry.Orientation.VERTICAL);
            iconFlow.setPrefWrapLength(100000.0);
            iconFlow.setHgap(0.0);
            iconFlow.setVgap(4.0);
        } else {
            iconFlow.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
            iconFlow.setPrefWrapLength(resolveIconFlowWrapLength(mode, false));
            iconFlow.setHgap(iconGridFlowHgapForMode(mode));
            iconFlow.setVgap(iconGridFlowVgapForMode(mode));
        }
    }

    private void refreshIconFlowLayoutForCurrentView(double viewportWidth) {
        if (iconFlow == null || !isIconMode(viewMode)) {
            return;
        }
        configureIconFlowLayout(viewMode);
        double snappedWidth = viewportWidth > 1.0 ? Math.max(1.0, Math.floor(viewportWidth)) : 0.0;
        if (snappedWidth > 1.0 && iconFlow.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) {
            iconFlow.setPrefWrapLength(snappedWidth);
        }
        applyResponsiveFlowIconViewportWidth(snappedWidth);
    }

    private void applyResponsiveFlowIconViewportWidth(double viewportWidth) {
        if (iconFlow == null || !isIconMode(viewMode) || viewportWidth <= 1.0) {
            return;
        }
        if (!iconFlow.prefWidthProperty().isBound()) {
            iconFlow.setPrefWidth(viewportWidth);
        }
        iconFlow.setMinWidth(viewportWidth);
        iconFlow.setMaxWidth(Double.MAX_VALUE);
        iconFlow.requestLayout();
        if (iconScroll != null) {
            iconScroll.requestLayout();
        }
        if (viewHost != null) {
            viewHost.requestLayout();
        }
    }

    private void scheduleResponsiveIconViewportLayoutRefresh() {
        if (!isIconMode(viewMode)) {
            return;
        }
        if (iconViewportLayoutDebounce != null) {
            iconViewportLayoutDebounce.playFromStart();
        } else {
            applyResponsiveIconViewportLayoutNow();
        }
    }

    private void applyResponsiveIconViewportLayoutNow() {
        if (!isIconMode(viewMode)) {
            return;
        }
        double viewportWidth = resolveResponsiveIconViewportWidth();
        double snappedWidth = viewportWidth > 1.0 ? Math.max(1.0, Math.floor(viewportWidth)) : viewportWidth;
        refreshIconFlowLayoutForCurrentView(snappedWidth);
        if (!isUsingVirtualIconGridForCurrentView()) {
            lastAppliedResponsiveIconViewportWidth = snappedWidth;
            Platform.runLater(() -> {
                if (!isIconMode(viewMode) || isUsingVirtualIconGridForCurrentView()) {
                    return;
                }
                double refreshedWidth = resolveResponsiveIconViewportWidth();
                double refreshedSnappedWidth = refreshedWidth > 1.0
                        ? Math.max(1.0, Math.floor(refreshedWidth))
                        : snappedWidth;
                refreshIconFlowLayoutForCurrentView(refreshedSnappedWidth);
                lastAppliedResponsiveIconViewportWidth = refreshedSnappedWidth;
            });
            return;
        }
        applyResponsiveVirtualIconViewMetrics(snappedWidth);
        int itemsPerRow = computeItemsPerIconRow(snappedWidth);
        if (itemsPerRow < 1) {
            itemsPerRow = 1;
        }
        Object current = virtualIconGridView == null ? null : virtualIconGridView.getProperties().get("iconGridItemsPerRow");
        int currentItemsPerRow = (current instanceof Number n) ? n.intValue() : -1;
        lastAppliedResponsiveIconViewportWidth = snappedWidth;
        if (itemsPerRow != currentItemsPerRow) {
            rebuildVirtualIconGridPreservingAnchor();
        }
    }

    private void wireResponsiveIconWindowListeners(Window window) {
        if (window == null) {
            return;
        }
        window.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
        window.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
        window.xProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
        window.yProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
    }

    private void scheduleResponsiveTableViewportLayoutRefresh() {
        if (viewMode != ViewMode.DETAILS || fileTable == null) {
            return;
        }
        if (tableViewportLayoutDebounce != null) {
            tableViewportLayoutDebounce.playFromStart();
        } else {
            applyResponsiveTableViewportLayoutNow();
        }
    }

    private void applyResponsiveTableViewportLayoutNow() {
        if (viewMode != ViewMode.DETAILS || fileTable == null) {
            return;
        }
        double viewportWidth = resolveResponsiveTableViewportWidth();
        if (viewportWidth <= 1.0) {
            return;
        }
        double snappedWidth = Math.max(1.0, Math.floor(viewportWidth));
        if (Math.abs(snappedWidth - lastAppliedResponsiveTableViewportWidth) < 1.0) {
            return;
        }
        lastAppliedResponsiveTableViewportWidth = snappedWidth;
        applyResponsiveTableViewportWidth(snappedWidth);
        Platform.runLater(() -> {
            if (viewMode != ViewMode.DETAILS || fileTable == null) {
                return;
            }
            double refreshedWidth = resolveResponsiveTableViewportWidth();
            double refreshedSnappedWidth = refreshedWidth > 1.0
                    ? Math.max(1.0, Math.floor(refreshedWidth))
                    : snappedWidth;
            applyResponsiveTableViewportWidth(refreshedSnappedWidth);
            fileTable.applyCss();
            fileTable.layout();
            fileTable.refresh();
            syncDetailsVisibleColumnRoleClasses();
            if (viewHost != null) {
                viewHost.requestLayout();
            }
        });
    }

    private double resolveResponsiveTableViewportWidth() {
        double width = resolvePrimaryResponsiveTableViewportWidth();
        if (width > 1.0) {
            lastResponsiveTableViewportWidth = width;
            return width;
        }
        if (lastResponsiveTableViewportWidth > 1.0) {
            return lastResponsiveTableViewportWidth;
        }
        return 900.0;
    }

    private void applyResponsiveTableViewportWidth(double targetWidth) {
        double snappedWidth = Math.max(1.0, Math.floor(targetWidth));
        if (detailsViewShell != null) {
            detailsViewShell.setMinWidth(0.0);
            if (!detailsViewShell.prefWidthProperty().isBound()) {
                detailsViewShell.setPrefWidth(snappedWidth);
            }
            detailsViewShell.setMaxWidth(Double.MAX_VALUE);
            StackPane.setAlignment(detailsViewShell, Pos.TOP_LEFT);
            detailsViewShell.requestLayout();
        }
        fileTable.setMinWidth(0.0);
        if (!fileTable.prefWidthProperty().isBound()) {
            fileTable.setPrefWidth(snappedWidth);
        }
        fileTable.setMaxWidth(Double.MAX_VALUE);
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        fileTable.requestLayout();
        if (viewHost != null) {
            viewHost.requestLayout();
        }
    }


    private double resolvePrimaryResponsiveTableViewportWidth() {
        return firstPositiveWidth(
                viewHost == null ? 0.0 : viewHost.getWidth(),
                viewHost == null ? 0.0 : boundsWidth(viewHost.getLayoutBounds()),
                contentPane == null ? 0.0 : contentPane.getWidth(),
                contentPane == null ? 0.0 : boundsWidth(contentPane.getLayoutBounds()),
                detailsViewShell == null ? 0.0 : detailsViewShell.getWidth(),
                detailsViewShell == null ? 0.0 : boundsWidth(detailsViewShell.getLayoutBounds()),
                fileTable == null ? 0.0 : fileTable.getWidth(),
                fileTable == null ? 0.0 : boundsWidth(fileTable.getLayoutBounds()),
                workspaceShell == null ? 0.0 : workspaceShell.getWidth(),
                workspaceShell == null ? 0.0 : boundsWidth(workspaceShell.getLayoutBounds()));
    }

    private void wireResponsiveTableWindowListeners(Window window) {
        if (window == null) {
            return;
        }
        window.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
        window.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
        window.xProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
        window.yProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveTableViewportLayoutRefresh());
    }

    private void applyResponsiveVirtualIconViewMetrics(double viewportWidth) {
        if (virtualIconGridView == null || viewportWidth <= 1.0) {
            return;
        }
        double snappedWidth = Math.max(1.0, Math.floor(viewportWidth));
        if (!virtualIconGridView.prefWidthProperty().isBound()) {
            virtualIconGridView.setPrefWidth(snappedWidth);
        }
        virtualIconGridView.setMaxWidth(Double.MAX_VALUE);
        virtualIconGridView.requestLayout();
        if (virtualIconListView != null) {
            if (!virtualIconListView.prefWidthProperty().isBound()) {
                virtualIconListView.setPrefWidth(snappedWidth);
            }
            virtualIconListView.setMaxWidth(Double.MAX_VALUE);
            virtualIconListView.requestLayout();
        }
    }

    private void rebuildVirtualIconGridPreservingAnchor() {
        double anchor = captureVirtualIconGridScrollAnchor();
        rebuildIconTiles();
        restoreVirtualIconGridScrollAnchor(anchor);
    }

    private double captureVirtualIconGridScrollAnchor() {
        ScrollBar scrollBar = findVerticalScrollBar(virtualIconGridView);
        if (scrollBar == null) {
            return Double.isNaN(lastVirtualIconGridScrollValue) ? Double.NaN : lastVirtualIconGridScrollValue;
        }
        double value = scrollBar.getValue();
        lastVirtualIconGridScrollValue = value;
        return value;
    }

    private void restoreVirtualIconGridScrollAnchor(double anchor) {
        double target = Double.isNaN(anchor) ? lastVirtualIconGridScrollValue : anchor;
        if (Double.isNaN(target)) {
            return;
        }
        Platform.runLater(() -> {
            ScrollBar scrollBar = findVerticalScrollBar(virtualIconGridView);
            if (scrollBar == null) {
                return;
            }
            double clamped = Math.max(scrollBar.getMin(), Math.min(scrollBar.getMax(), target));
            scrollBar.setValue(clamped);
            lastVirtualIconGridScrollValue = clamped;
        });
    }

    private ScrollBar findVerticalScrollBar(Node control) {
        if (control == null) {
            return null;
        }
        control.applyCss();
        for (Node node : control.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar scrollBar && scrollBar.getOrientation() == Orientation.VERTICAL) {
                return scrollBar;
            }
        }
        return null;
    }

    private boolean isUsingVirtualIconGridForCurrentView() {
        return isGridIconMode(viewMode)
                && virtualIconGridView != null
                && virtualIconGridView.isVisible()
                && virtualIconGridView.isManaged();
    }

    private boolean isUsingVirtualIconListForCurrentView() {
        return (viewMode == ViewMode.LIST
                || viewMode == ViewMode.TILES
                || viewMode == ViewMode.CONTENT)
                && virtualIconListView != null
                && virtualIconListView.isVisible()
                && virtualIconListView.isManaged();
    }

    private double resolveIconFlowWrapLength(ViewMode mode, boolean verticalFlow) {
        if (!verticalFlow) {
            return resolveResponsiveIconViewportWidth();
        }
        if (iconScroll != null && iconScroll.isVisible()) {
            Bounds viewport = iconScroll.getViewportBounds();
            if (viewport != null && viewport.getHeight() > 1.0) {
                return viewport.getHeight();
            }
            Bounds scrollBounds = iconScroll.getLayoutBounds();
            if (scrollBounds != null && scrollBounds.getHeight() > 1.0) {
                return scrollBounds.getHeight();
            }
        }
        if (viewHost != null) {
            Bounds hostBounds = viewHost.getLayoutBounds();
            if (hostBounds != null && hostBounds.getHeight() > 1.0) {
                return hostBounds.getHeight();
            }
        }
        return 720.0;
    }

    private void applyIconFlowModeClasses(ViewMode mode) {
        if (iconFlow == null) {
            return;
        }
        setStyleClass(iconFlow, "explorer-icon-flow-xl", mode == ViewMode.EXTRA_LARGE_ICONS);
        setStyleClass(iconFlow, "explorer-icon-flow-large", mode == ViewMode.LARGE_ICONS);
        setStyleClass(iconFlow, "explorer-icon-flow-medium", mode == ViewMode.MEDIUM_ICONS);
        setStyleClass(iconFlow, "explorer-icon-flow-small", mode == ViewMode.SMALL_ICONS);
        setStyleClass(iconFlow, "explorer-icon-flow-list", mode == ViewMode.LIST);
        setStyleClass(iconFlow, "explorer-icon-flow-tiles", mode == ViewMode.TILES);
        setStyleClass(iconFlow, "explorer-icon-flow-content", mode == ViewMode.CONTENT);
    }

    private void applyIconFlowPadding(ViewMode mode) {
        if (iconFlow == null) {
            return;
        }
        iconFlow.setPadding(iconFlowPaddingForMode(mode));
    }

    private Insets iconFlowPaddingForMode(ViewMode mode) {
        if (mode == null) {
            return new Insets(10.0);
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> new Insets(18.0, 20.0, 18.0, 20.0);
            case LARGE_ICONS -> new Insets(16.0, 18.0, 16.0, 18.0);
            case MEDIUM_ICONS -> new Insets(14.0, 16.0, 14.0, 16.0);
            case SMALL_ICONS -> new Insets(12.0, 14.0, 12.0, 14.0);
            case LIST -> new Insets(10.0, 12.0, 10.0, 12.0);
            case TILES -> new Insets(10.0, 12.0, 10.0, 12.0);
            case CONTENT -> new Insets(8.0, 10.0, 8.0, 10.0);
            default -> new Insets(10.0);
        };
    }

    private double iconGridFlowHgapForMode(ViewMode mode) {
        if (mode == null) {
            return 16.0;
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> 22.0;
            case LARGE_ICONS -> 18.0;
            case MEDIUM_ICONS -> 14.0;
            case SMALL_ICONS -> 10.0;
            default -> 16.0;
        };
    }

    private double iconGridFlowVgapForMode(ViewMode mode) {
        if (mode == null) {
            return 16.0;
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> 18.0;
            case LARGE_ICONS -> 16.0;
            case MEDIUM_ICONS -> 14.0;
            case SMALL_ICONS -> 10.0;
            default -> 16.0;
        };
    }

    private double iconListFlowHgapForMode(ViewMode mode) {
        if (mode == ViewMode.LIST) {
            return 14.0;
        }
        if (mode == ViewMode.TILES) {
            return 16.0;
        }
        return 12.0;
    }

    private double iconListFlowVgapForMode(ViewMode mode) {
        if (mode == ViewMode.LIST) {
            return 4.0;
        }
        if (mode == ViewMode.TILES) {
            return 8.0;
        }
        return 6.0;
    }

    private String iconTileModeStyleClass(ViewMode mode) {
        if (mode == null) {
            return "explorer-icon-tile-medium";
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> "explorer-icon-tile-xl";
            case LARGE_ICONS -> "explorer-icon-tile-large";
            case MEDIUM_ICONS -> "explorer-icon-tile-medium";
            case SMALL_ICONS -> "explorer-icon-tile-small";
            case LIST -> "explorer-icon-tile-list";
            case TILES -> "explorer-icon-tile-tiles";
            case CONTENT -> "explorer-icon-tile-content";
            default -> "explorer-icon-tile-medium";
        };
    }

    private double iconGridTileWidthForMode(ViewMode mode) {
        if (mode == null) {
            return Math.max(112.0, iconSizePx + 44.0);
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> EXTRA_LARGE_ICON_CELL_PX;
            case LARGE_ICONS -> 184.0;
            case MEDIUM_ICONS -> 152.0;
            case SMALL_ICONS -> 116.0;
            default -> Math.max(112.0, iconSizePx + 44.0);
        };
    }

    private double iconGridLabelWidthForMode(ViewMode mode) {
        return Math.max(88.0, iconGridTileWidthForMode(mode) - 22.0);
    }

    private double iconGridIconSlotSizeForMode(ViewMode mode) {
        if (mode == ViewMode.EXTRA_LARGE_ICONS) {
            return EXTRA_LARGE_ICON_CELL_PX;
        }
        return Math.max(16.0, iconSizePx);
    }

    private Node wrapGridIconNodeForMode(Node icon, ViewMode mode) {
        if (icon == null) {
            return new Region();
        }
        if (mode != ViewMode.EXTRA_LARGE_ICONS) {
            return icon;
        }
        StackPane slot = new StackPane(icon);
        slot.setAlignment(Pos.CENTER);
        slot.setMinSize(EXTRA_LARGE_ICON_CELL_PX, EXTRA_LARGE_ICON_CELL_PX);
        slot.setPrefSize(EXTRA_LARGE_ICON_CELL_PX, EXTRA_LARGE_ICON_CELL_PX);
        slot.setMaxSize(EXTRA_LARGE_ICON_CELL_PX, EXTRA_LARGE_ICON_CELL_PX);
        slot.getStyleClass().add("explorer-icon-slot-xl");
        return slot;
    }

    private double iconGridTileSpacingForMode(ViewMode mode) {
        if (mode == null) {
            return 6.0;
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> 10.0;
            case LARGE_ICONS -> 8.0;
            case MEDIUM_ICONS -> 6.0;
            case SMALL_ICONS -> 4.0;
            default -> 6.0;
        };
    }

    private Insets iconGridTilePaddingForMode(ViewMode mode) {
        if (mode == null) {
            return new Insets(8.0, 10.0, 8.0, 10.0);
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> Insets.EMPTY;
            case LARGE_ICONS -> new Insets(10.0, 10.0, 8.0, 10.0);
            case MEDIUM_ICONS -> new Insets(8.0, 8.0, 6.0, 8.0);
            case SMALL_ICONS -> new Insets(6.0, 6.0, 4.0, 6.0);
            default -> new Insets(8.0, 10.0, 8.0, 10.0);
        };
    }

    private double iconGridLabelMinHeightForMode(ViewMode mode) {
        if (mode == null) {
            return 32.0;
        }
        return switch (mode) {
            case EXTRA_LARGE_ICONS -> 36.0;
            case LARGE_ICONS -> 34.0;
            case MEDIUM_ICONS -> 32.0;
            case SMALL_ICONS -> 24.0;
            default -> 32.0;
        };
    }

    private double tileRowWidthForMode(ViewMode mode) {
        if (mode == ViewMode.CONTENT) {
            return 640.0;
        }
        if (mode == ViewMode.LIST) {
            return 240.0;
        }
        return 420.0;
    }

    private double tileRowTextWidthForMode(ViewMode mode) {
        if (mode == ViewMode.CONTENT) {
            return 500.0;
        }
        if (mode == ViewMode.LIST) {
            return 188.0;
        }
        return 300.0;
    }

    private double tileRowMinHeightForMode(ViewMode mode) {
        if (mode == ViewMode.CONTENT) {
            return 54.0;
        }
        if (mode == ViewMode.LIST) {
            return 28.0;
        }
        return 44.0;
    }
/**
 * applyIconSizePreset.
 *
 * @param mode TODO
 */
    private void applyIconSizePreset(ViewMode mode) {
        LogSupport.enter(LOG, "applyIconSizePreset");
        if (mode == null) {
            return;
        }
        switch (mode) {
            case EXTRA_LARGE_ICONS:
                iconSizePx = EXTRA_LARGE_ICON_CELL_PX;
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
            case LIST:
                iconSizePx = 20.0;
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
/**
 * clampIconSize.
 *
 */
    private void clampIconSize() {
        LogSupport.enter(LOG, "clampIconSize");
        iconSizePx = Math.max(ICON_SIZE_MIN, Math.min(ICON_SIZE_MAX, iconSizePx));
    }
/**
 * applyTableColumnMode.
 *
 * @param mode TODO
 */
    private void applyTableColumnMode(ViewMode mode) {
        LogSupport.enter(LOG, "applyTableColumnMode");
        if (fileTable == null || colName == null || colType == null || colSize == null || colModified == null) {
            return;
        }
        colName.setVisible(true);
        colType.setVisible(true);
        colSize.setVisible(true);
        colModified.setVisible(true);
    }
/**
 * syncViewMenuSelection.
 *
 */
    private void syncViewMenuSelection() {
        LogSupport.enter(LOG, "syncViewMenuSelection");
        if (viewExtraLargeIcons == null) {
            updateViewMenuButtonGraphic();
            syncStatusViewToggleSelection();
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
        updateViewMenuButtonGraphic();
        syncStatusViewToggleSelection();
    }

    private void syncStatusViewToggleSelection() {
        if (statusDetailsButton != null) {
            statusDetailsButton.setSelected(viewMode == ViewMode.DETAILS);
        }
        if (statusLargeIconsButton != null) {
            statusLargeIconsButton.setSelected(viewMode == ViewMode.EXTRA_LARGE_ICONS);
        }
    }
/**
 * viewModeLabel.
 *
 * @param mode TODO
 * @return TODO
 */
    private String viewModeLabel(ViewMode mode) {
        LogSupport.enter(LOG, "viewModeLabel");
        if (mode == null) {
            return "";
        }
/**
 * switch.
 *
 * @param mode TODO
 * @return TODO
 */
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
    
/**
 * clearIconTiles.
 *
 */
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
/**
 * rebuildIconTiles.
 *
 */
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
        // List / Tiles / Content: use flow for normal folders, virtualized rows for larger ones.
        if (viewMode == ViewMode.LIST || viewMode == ViewMode.TILES || viewMode == ViewMode.CONTENT) {
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
/**
 * rebuildIconTilesIncremental.
 *
 * @param items TODO
 */
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
/**
 * appendNextIconBatch.
 *
 * @param gen TODO
 */
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
        refreshVisibleIconTileSelectionState();
    }
/**
 * installIconScrollPaging.
 *
 */
    private void installIconScrollPaging() {
        LogSupport.enter(LOG, "installIconScrollPaging");
        if (iconScroll == null) {
            return;
        }
        if (!iconScrollPagingTargets.add(iconScroll)) {
            return;
        }
        final ScrollPane pagingTarget = iconScroll;
        pagingTarget.vvalueProperty().addListener((_, _, val) -> {
            if (!pagingTarget.isVisible() || iconFlow == null) {
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
/**
 * ensureVirtualIconViewsInstalled.
 *
 */
    private void ensureVirtualIconViewsInstalled() {
        LogSupport.enter(LOG, "ensureVirtualIconViewsInstalled");
        if (virtualIconViewsInstalled) {
            return;
        }
        if (iconScroll == null) {
            return;
        }
        if (!(iconScroll.getParent() instanceof javafx.scene.layout.StackPane host)) {
            return;
        }
        virtualIconViewsInstalled = true;
        // Virtual grid (rows of icon tiles)
        virtualIconGridView = new ListView<>();
        virtualIconGridView.getStyleClass().add("icon-virtual-grid");
        virtualIconGridView.setVisible(false);
        virtualIconGridView.setManaged(false);
        virtualIconGridView.setMaxWidth(Double.MAX_VALUE);
        virtualIconGridView.setMaxHeight(Double.MAX_VALUE);
        virtualIconGridView.setCellFactory(_ -> new ListCell<>() {
            private final FlowPane rowPane = new FlowPane();
            {
                rowPane.setAlignment(Pos.TOP_LEFT);
                rowPane.setPickOnBounds(false);
                installExplorerVirtualCellGestureSuppression(this);
                installVirtualIconGridCellContextMenuHandlers(this, rowPane);
            }
            @Override
/**
 * updateItem.
 *
 * @param row TODO
 * @param empty TODO
 */
            protected void updateItem(List<Path> row, boolean empty) {
                LogSupport.enter(LOG, "updateItem4");
                super.updateItem(row, empty);
                rowPane.getChildren().clear();
                setPickOnBounds(false);
                setText(null);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                if (empty || row == null || row.isEmpty()) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                rowPane.setHgap(iconGridFlowHgapForMode(viewMode));
                rowPane.setVgap(iconGridFlowVgapForMode(viewMode));
                rowPane.setPadding(iconFlowPaddingForMode(viewMode));
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
        virtualIconListView.setMaxWidth(Double.MAX_VALUE);
        virtualIconListView.setMaxHeight(Double.MAX_VALUE);
        virtualIconListView.setCellFactory(_ -> new ListCell<>() {
            {
                installExplorerVirtualCellGestureSuppression(this);
                installVirtualIconListCellContextMenuHandlers(this);
            }
            @Override
/**
 * updateItem.
 *
 * @param item TODO
 * @param empty TODO
 */
            protected void updateItem(Path item, boolean empty) {
                LogSupport.enter(LOG, "updateItem5");
                super.updateItem(item, empty);
                setPickOnBounds(false);
                setText(null);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                if (empty || item == null) {
                    setGraphic(null);
                    setTooltip(null);
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
        virtualIconGridView.prefWidthProperty().bind(host.widthProperty());
        virtualIconGridView.prefHeightProperty().bind(host.heightProperty());
        virtualIconListView.prefWidthProperty().bind(host.widthProperty());
        virtualIconListView.prefHeightProperty().bind(host.heightProperty());
        virtualIconGridView.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
        virtualIconGridView.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
        virtualIconGridView.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveIconViewportLayoutRefresh());
        virtualIconListView.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
        virtualIconListView.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
        host.widthProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
        host.heightProperty().addListener((obs, oldValue, newValue) -> scheduleResponsiveIconViewportLayoutRefresh());
        host.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> scheduleResponsiveIconViewportLayoutRefresh());
        host.getChildren().add(virtualIconGridView);
        host.getChildren().add(virtualIconListView);
        bringIconMarqueeOverlayToFront();
    }
/**
 * hideVirtualIconViews.
 *
 */
    private void installExplorerVirtualCellGestureSuppression(ListCell<?> cell) {
        if (cell == null) {
            return;
        }
        cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && (shouldSuppressExplorerIconPrimaryGestureHandling() || iconMarqueePressArmed || iconMarqueeDragStarted)) {
                event.consume();
            }
        });
        cell.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && (shouldSuppressExplorerIconPrimaryGestureHandling() || iconMarqueePressArmed || iconMarqueeDragStarted)) {
                event.consume();
            }
        });
        cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && (shouldSuppressExplorerIconPrimaryGestureHandling() || iconMarqueePressArmed || iconMarqueeDragStarted)) {
                event.consume();
            }
        });
    }

    private void installVirtualIconGridCellContextMenuHandlers(ListCell<List<Path>> cell, FlowPane rowPane) {
        if (cell == null) {
            return;
        }
        cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.SECONDARY || !isIconMode(viewMode)) {
                return;
            }
            if (isInlineRenameFocusGuardActive()) {
                event.consume();
                return;
            }
            Path path = resolveVirtualIconGridCellContextMenuPath(cell, rowPane, resolveEventTargetNode(event), event.getScreenX(), event.getScreenY());
            if (path == null) {
                return;
            }
            if (viewMode == ViewMode.EXTRA_LARGE_ICONS) {
                debugExplorerContextMenuTarget("virtual-grid-secondary-press", path);
            }
            armExplorerItemContextMenu(path, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        cell.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() != MouseButton.SECONDARY || !isIconMode(viewMode)) {
                return;
            }
            if (showArmedExplorerItemContextMenuOnSecondaryRelease(event)) {
                event.consume();
            }
        });
        cell.setOnContextMenuRequested(event -> {
            if (!isIconMode(viewMode)) {
                return;
            }
            if (shouldSuppressExplorerItemContextMenuRequestedEvent()) {
                event.consume();
                return;
            }
            if (isInlineRenameFocusGuardActive()) {
                event.consume();
                return;
            }
            Node target = event.getPickResult() != null && event.getPickResult().getIntersectedNode() != null
                    ? event.getPickResult().getIntersectedNode()
                    : (event.getTarget() instanceof Node node ? node : null);
            Path path = resolveVirtualIconGridCellContextMenuPath(cell, rowPane, target, event.getScreenX(), event.getScreenY());
            if (path == null) {
                path = resolveArmedExplorerItemContextMenuPath(event.getScreenX(), event.getScreenY());
            }
            if (path == null) {
                return;
            }
            if (viewMode == ViewMode.EXTRA_LARGE_ICONS) {
                debugExplorerContextMenuTarget("virtual-grid-context-menu-requested", path);
            }
            requestExplorerItemContextMenu(path, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void installVirtualIconListCellContextMenuHandlers(ListCell<Path> cell) {
        if (cell == null) {
            return;
        }
        cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.SECONDARY || !isIconMode(viewMode)) {
                return;
            }
            if (isInlineRenameFocusGuardActive()) {
                event.consume();
                return;
            }
            Path path = resolveVirtualIconListCellContextMenuPath(cell, resolveEventTargetNode(event));
            if (path == null) {
                return;
            }
            armExplorerItemContextMenu(path, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        cell.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() != MouseButton.SECONDARY || !isIconMode(viewMode)) {
                return;
            }
            if (showArmedExplorerItemContextMenuOnSecondaryRelease(event)) {
                event.consume();
            }
        });
        cell.setOnContextMenuRequested(event -> {
            if (!isIconMode(viewMode)) {
                return;
            }
            if (shouldSuppressExplorerItemContextMenuRequestedEvent()) {
                event.consume();
                return;
            }
            if (isInlineRenameFocusGuardActive()) {
                event.consume();
                return;
            }
            Node target = event.getPickResult() != null && event.getPickResult().getIntersectedNode() != null
                    ? event.getPickResult().getIntersectedNode()
                    : (event.getTarget() instanceof Node node ? node : null);
            Path path = resolveVirtualIconListCellContextMenuPath(cell, target);
            if (path == null) {
                path = resolveArmedExplorerItemContextMenuPath(event.getScreenX(), event.getScreenY());
            }
            if (path == null) {
                return;
            }
            requestExplorerItemContextMenu(path, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private Path resolveVirtualIconListCellContextMenuPath(ListCell<Path> cell, Node target) {
        Path path = findExplorerIconTilePath(target);
        if (path != null) {
            return path;
        }
        if (cell == null || cell.isEmpty()) {
            return null;
        }
        return cell.getItem();
    }

    private Path resolveVirtualIconGridCellContextMenuPath(ListCell<List<Path>> cell, FlowPane rowPane, Node target, double screenX, double screenY) {
        Path path = findExplorerIconTilePath(target);
        if (path != null) {
            return path;
        }
        if (rowPane != null) {
            for (Node child : rowPane.getChildren()) {
                if (child == null || !child.isVisible()) {
                    continue;
                }
                Bounds bounds = child.localToScreen(child.getBoundsInLocal());
                if (bounds != null && bounds.contains(screenX, screenY)) {
                    Path childPath = pathForExplorerIconTile(child);
                    if (childPath != null) {
                        return childPath;
                    }
                }
            }
        }
        if (cell == null || cell.isEmpty()) {
            return null;
        }
        List<Path> row = cell.getItem();
        if (row == null || row.isEmpty()) {
            return null;
        }
        if (row.size() == 1) {
            return row.get(0);
        }
        return null;
    }

    private void hideVirtualIconViews() {
        LogSupport.enter(LOG, "hideVirtualIconViews");
        if (virtualIconGridView != null) {
            virtualIconGridView.setVisible(false);
            virtualIconGridView.setManaged(false);
            virtualIconGridView.getItems().clear();
            virtualIconGridView.getProperties().remove("iconGridItemsPerRow");
        }
        if (virtualIconListView != null) {
            virtualIconListView.setVisible(false);
            virtualIconListView.setManaged(false);
            virtualIconListView.getItems().clear();
        }
    }
/**
 * showIconScrollOnly.
 *
 */
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
/**
 * showVirtualIconGrid.
 *
 * @param items TODO
 */
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
        // Build row model (List<Path> per row) based on the current visible width.
        int itemsPerRow = computeItemsPerIconRow();
        if (itemsPerRow < 1) {
            itemsPerRow = 1;
        }
        virtualIconGridView.getProperties().put("iconGridItemsPerRow", itemsPerRow);
        List<List<Path>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i += itemsPerRow) {
            int j = Math.min(items.size(), i + itemsPerRow);
            rows.add(items.subList(i, j));
        }
        virtualIconGridView.setItems(FXCollections.observableArrayList(rows));
        virtualIconGridView.requestFocus();
        Platform.runLater(this::refreshVisibleIconTileSelectionState);
    }
/**
 * showVirtualIconList.
 *
 * @param items TODO
 */
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
        Platform.runLater(this::refreshVisibleIconTileSelectionState);
    }
/**
 * computeItemsPerIconRow.
 *
 * @return TODO
 */
    private int computeItemsPerIconRow() {
        LogSupport.enter(LOG, "computeItemsPerIconRow");
        return computeItemsPerIconRow(resolveResponsiveIconViewportWidth());
    }

    private int computeItemsPerIconRow(double viewportWidth) {
        Insets paddingInsets = iconFlowPaddingForMode(viewMode);
        double padding = paddingInsets.getLeft() + paddingInsets.getRight();
        double tileW = iconGridTileWidthForMode(viewMode);
        double hgap = iconGridFlowHgapForMode(viewMode);
        double usable = Math.max(1.0, viewportWidth - padding);
        return (int) Math.max(1.0, Math.floor((usable + hgap) / (tileW + hgap)));
    }

    private double resolveResponsiveIconViewportWidth() {
        double w = resolvePrimaryResponsiveIconViewportWidth();
        if (w > 1.0) {
            lastResponsiveIconViewportWidth = w;
            return w;
        }
        if (lastResponsiveIconViewportWidth > 1.0) {
            return lastResponsiveIconViewportWidth;
        }
        return 900.0;
    }

    private double resolvePrimaryResponsiveIconViewportWidth() {
        if (isUsingVirtualIconGridForCurrentView()) {
            double gridWidth = firstPositiveWidth(
                    virtualIconGridView == null ? 0.0 : virtualIconGridView.getWidth(),
                    virtualIconGridView == null ? 0.0 : boundsWidth(virtualIconGridView.getLayoutBounds()),
                    viewHost == null ? 0.0 : viewHost.getWidth(),
                    viewHost == null ? 0.0 : boundsWidth(viewHost.getLayoutBounds()));
            if (gridWidth > 1.0) {
                return gridWidth;
            }
        }
        if (iconScroll != null && iconScroll.isVisible()) {
            double scrollWidth = firstPositiveWidth(
                    boundsWidth(iconScroll.getViewportBounds()),
                    iconScroll.getWidth(),
                    boundsWidth(iconScroll.getLayoutBounds()),
                    viewHost == null ? 0.0 : viewHost.getWidth(),
                    viewHost == null ? 0.0 : boundsWidth(viewHost.getLayoutBounds()));
            if (scrollWidth > 1.0) {
                return scrollWidth;
            }
        }
        return firstPositiveWidth(
                viewHost == null ? 0.0 : viewHost.getWidth(),
                viewHost == null ? 0.0 : boundsWidth(viewHost.getLayoutBounds()),
                virtualIconGridView == null ? 0.0 : virtualIconGridView.getWidth(),
                virtualIconGridView == null ? 0.0 : boundsWidth(virtualIconGridView.getLayoutBounds()),
                iconScroll == null ? 0.0 : boundsWidth(iconScroll.getViewportBounds()),
                iconScroll == null ? 0.0 : iconScroll.getWidth(),
                iconScroll == null ? 0.0 : boundsWidth(iconScroll.getLayoutBounds()));
    }

    private double firstPositiveWidth(double... candidates) {
        if (candidates == null) {
            return 0.0;
        }
        for (double candidate : candidates) {
            if (candidate > 1.0) {
                return candidate;
            }
        }
        return 0.0;
    }

    private double boundsWidth(Bounds bounds) {
        return bounds == null ? 0.0 : bounds.getWidth();
    }
/**
 * buildIconTile.
 *
 * @param p TODO
 * @return TODO
 */
        private Node buildIconTile(Path p) {
            LogSupport.enter(LOG, "buildIconTile");
        // For icon modes, use the same container (iconFlow) but vary layout:
        // - Icons: icon above name (wrapping)
        // - Tiles: icon left + (name/type/size)
        // - Content: icon left + (name/type/size/modified)
        boolean isList = viewMode == ViewMode.LIST;
        boolean isTiles = viewMode == ViewMode.TILES;
        boolean isContent = viewMode == ViewMode.CONTENT;
        if (isList || isTiles || isContent) {
            boolean dark = themeService != null && themeService.isDarkPreferred();
            HBox row = new HBox(isContent ? 14.0 : (isList ? 10.0 : 12.0));
            row.setAlignment(Pos.CENTER_LEFT);
            tagIconTile(row, p, "tile-row", "explorer-icon-tile", "explorer-icon-tile-row", iconTileModeStyleClass(viewMode));
            row.setMinHeight(tileRowMinHeightForMode(viewMode));
            row.setPrefWidth(tileRowWidthForMode(viewMode));
            row.setMaxWidth(tileRowWidthForMode(viewMode));
            Node icon = buildIconNode(p, iconSizePx, "tile-item-icon");
            VBox textCol = new VBox(isContent ? 3.0 : (isList ? 0.0 : 2.0));
            textCol.setAlignment(Pos.CENTER_LEFT);
            textCol.getStyleClass().add("explorer-tile-text-column");
            textCol.setPrefWidth(tileRowTextWidthForMode(viewMode));
            textCol.setMaxWidth(tileRowTextWidthForMode(viewMode));
            HBox.setHgrow(textCol, Priority.ALWAYS);
            if (isIconInlineRenameTarget(p)) {
                TextField renameField = createExplorerInlineRenameField(p, displayNameForTable(p));
                renameField.setPrefWidth(tileRowTextWidthForMode(viewMode));
                renameField.setMaxWidth(tileRowTextWidthForMode(viewMode));
                textCol.getChildren().add(renameField);
                row.getProperties().put(EXPLORER_ICON_TILE_INLINE_RENAME_NODE_KEY, renameField);
            } else {
                Label name = new Label(displayNameForTable(p));
                name.getStyleClass().add("explorer-icon-name-label");
                name.setWrapText(false);
                name.setTextOverrun(OverrunStyle.ELLIPSIS);
                name.setMaxWidth(Double.MAX_VALUE);
                name.setTextFill(dark ? Color.WHITE : Color.web("#202020"));
                textCol.getChildren().add(name);
                if (!isList) {
                    String typeText = typeForTable(p);
                    String sizeText = sizeForTable(p);
                    String meta = typeText;
                    if (sizeText != null && !sizeText.isBlank()) {
                        meta = meta + " · " + sizeText;
                    }
                    Label line2 = new Label(meta);
                    line2.getStyleClass().add("explorer-icon-meta-label");
                    line2.setTextFill(dark ? Color.web("#DADADA") : Color.web("#5A5A5A"));
                    textCol.getChildren().add(line2);
                    if (isContent) {
                        String modified = modifiedForTable(p);
                        Label line3 = new Label(modified);
                        line3.getStyleClass().addAll("explorer-icon-meta-label", "explorer-icon-modified-label");
                        line3.setTextFill(dark ? Color.web("#CFCFCF") : Color.web("#666666"));
                        textCol.getChildren().add(line3);
                    }
                }
            }
            row.getChildren().addAll(icon, textCol);
            markExplorerIconTileChild(icon);
            markExplorerIconTileChild(textCol);
            installExplorerItemTooltip(row, () -> buildExplorerItemTooltipText(p));
            row.setOnMouseEntered(_ -> scheduleHoverPrefetch(p));
            installExplorerIconTileSelectionHandlers(row, p);
            return row;
        }
        boolean dark = themeService != null && themeService.isDarkPreferred();
        VBox tile = new VBox(iconGridTileSpacingForMode(viewMode));
        tile.setAlignment(Pos.TOP_CENTER);
        tagIconTile(tile, p, "icon-tile", "explorer-icon-tile", "explorer-icon-tile-grid", iconTileModeStyleClass(viewMode));
        double w = iconGridTileWidthForMode(viewMode);
        tile.setMinWidth(w);
        tile.setPrefWidth(w);
        tile.setMaxWidth(w);
        tile.setPadding(iconGridTilePaddingForMode(viewMode));
        Node icon = buildIconNode(p, iconGridIconSlotSizeForMode(viewMode), "tile-item-icon");
        Node iconSlot = wrapGridIconNodeForMode(icon, viewMode);
        if (isIconInlineRenameTarget(p)) {
            TextField renameField = createExplorerInlineRenameField(p, displayNameForTable(p));
            renameField.setMaxWidth(iconGridLabelWidthForMode(viewMode));
            tile.getChildren().addAll(iconSlot, renameField);
            tile.getProperties().put(EXPLORER_ICON_TILE_INLINE_RENAME_NODE_KEY, renameField);
            markExplorerIconTileChild(iconSlot);
            markExplorerIconTileChild(renameField);
        } else {
            Label name = new Label(displayNameForTable(p));
            name.getStyleClass().add("explorer-icon-name-label");
            name.setWrapText(true);
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
            name.setMaxWidth(iconGridLabelWidthForMode(viewMode));
            name.setMinHeight(iconGridLabelMinHeightForMode(viewMode));
            name.setAlignment(Pos.TOP_CENTER);
            name.setTextFill(dark ? Color.WHITE : Color.web("#202020"));
            tile.getChildren().addAll(iconSlot, name);
            markExplorerIconTileChild(iconSlot);
            markExplorerIconTileChild(name);
        }
        installExplorerItemTooltip(tile, () -> buildExplorerItemTooltipText(p));
        tile.setOnMouseEntered(_ -> scheduleHoverPrefetch(p));
        installExplorerIconTileSelectionHandlers(tile, p);
        return tile;
        }

    private void installExplorerIconTileSelectionHandlers(Node tile, Path path) {
        if (tile == null || path == null) {
            return;
        }
        tile.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                captureExplorerSelectionSnapshotBeforePrimaryPress();
            }
            if (event.getButton() == MouseButton.SECONDARY) {
                if (viewMode == ViewMode.EXTRA_LARGE_ICONS) {
                    debugExplorerContextMenuTarget("tile-secondary-press", path);
                }
                armExplorerItemContextMenu(path, event.getScreenX(), event.getScreenY());
                event.consume();
                return;
            }
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            hideExplorerMetadataPopup();
            requestActiveIconSurfaceFocus();
            if (shouldSuppressExplorerIconPrimaryGestureHandling()) {
                event.consume();
                return;
            }
            handleExplorerIconTilePrimaryPress(path, event);
            event.consume();
        });
        tile.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                if (showArmedExplorerItemContextMenuOnSecondaryRelease(event)) {
                    event.consume();
                }
                return;
            }
            if (event.getButton() == MouseButton.PRIMARY) {
                event.consume();
            }
        });
        tile.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (shouldSuppressExplorerIconPrimaryGestureHandling()) {
                event.consume();
                return;
            }
            lastIconActivatedPath = path;
            if (event.getClickCount() == 2 && Files.isDirectory(path)) {
                navigateToFolder(path, true);
            }
            event.consume();
        });
        tile.setOnContextMenuRequested(event -> {
            if (shouldSuppressExplorerItemContextMenuRequestedEvent()) {
                event.consume();
                return;
            }
            requestExplorerItemContextMenu(path, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private boolean shouldSuppressExplorerIconPrimaryGestureHandling() {
        return iconMarqueeGestureOwnsSelection || suppressExplorerIconClickSelection;
    }

    private void prepareSelectionForContextMenuPath(Path path) {
        if (path == null || fileTable == null || fileTable.getSelectionModel() == null) {
            return;
        }
        explorerContextMenuOwnedPath = path;
        java.util.LinkedHashSet<Path> currentSelection = currentTableSelectionSnapshot();
        boolean alreadySoleSelection = currentSelection.size() == 1 && currentSelection.contains(path);
        int index = findTableIndexForPath(path);
        if (!alreadySoleSelection && index >= 0) {
            beginExplorerSelectionPresentationTransaction();
            try {
                fileTable.getSelectionModel().clearAndSelect(index);
                if (fileTable.getFocusModel() != null) {
                    fileTable.getFocusModel().focus(index);
                }
            } finally {
                endExplorerSelectionPresentationTransaction();
            }
            if (viewMode == ViewMode.DETAILS) {
                replaceDetailsPresentationSelectedPaths(java.util.Set.of(path));
                syncVisibleDetailsHoverRows();
                fileTable.refresh();
            } else if (isIconMode(viewMode)) {
                replaceIconPresentationSelectedPaths(java.util.Set.of(path));
                refreshVisibleIconTileSelectionState();
            }
        } else if (index >= 0 && fileTable.getFocusModel() != null) {
            fileTable.getFocusModel().focus(index);
        }
        setExplorerSelectionAnchorPath(path);
        lastIconActivatedPath = path;
        preserveExplorerSelectionPresentationForContextMenu();
    }

    private void armExplorerItemContextMenu(Path path, double screenX, double screenY) {
        if (path == null) {
            return;
        }
        suppressExplorerMetadataPopupForMillis(1500L);
        armedExplorerItemContextMenuPath = path;
        armedExplorerItemContextMenuScreenX = screenX;
        armedExplorerItemContextMenuScreenY = screenY;
        armedExplorerItemContextMenuUntilNanos = System.nanoTime() + 1_500_000_000L;
        markExplorerFileViewContextMenuPending();
        prepareSelectionForContextMenuPath(path);
        preserveExplorerSelectionPresentationForContextMenu();
        if (viewMode == ViewMode.EXTRA_LARGE_ICONS) {
            debugExplorerContextMenuTarget("arm", path);
        }
    }

    private void clearArmedExplorerItemContextMenu() {
        armedExplorerItemContextMenuPath = null;
        armedExplorerItemContextMenuScreenX = Double.NaN;
        armedExplorerItemContextMenuScreenY = Double.NaN;
        armedExplorerItemContextMenuUntilNanos = 0L;
    }

    private Path resolveArmedExplorerItemContextMenuPath(double screenX, double screenY) {
        if (armedExplorerItemContextMenuPath == null) {
            return null;
        }
        if (armedExplorerItemContextMenuUntilNanos <= System.nanoTime()) {
            clearArmedExplorerItemContextMenu();
            return null;
        }
        if (Double.isFinite(screenX) && Double.isFinite(screenY)
                && Double.isFinite(armedExplorerItemContextMenuScreenX)
                && Double.isFinite(armedExplorerItemContextMenuScreenY)) {
            double dx = Math.abs(screenX - armedExplorerItemContextMenuScreenX);
            double dy = Math.abs(screenY - armedExplorerItemContextMenuScreenY);
            if (dx > 24.0 || dy > 24.0) {
                return null;
            }
        }
        return armedExplorerItemContextMenuPath;
    }

    private void requestExplorerItemContextMenu(Path path, double screenX, double screenY) {
        if (path == null) {
            return;
        }
        suppressExplorerMetadataPopupForMillis(1500L);
        clearArmedExplorerItemContextMenu();
        markExplorerFileViewContextMenuPending();
        prepareSelectionForContextMenuPath(path);
        preserveExplorerSelectionPresentationForContextMenu();
        if (viewMode == ViewMode.EXTRA_LARGE_ICONS) {
            debugExplorerContextMenuTarget("request", path);
        }
        explorerItemContextMenuSuppressUntilNanos = System.nanoTime() + 5_000_000_000L;
        long requestTicket = ++explorerItemContextMenuRequestTicket;
        Runnable showMenu = () -> {
            if (requestTicket != explorerItemContextMenuRequestTicket) {
                return;
            }
            showFileOpsContextMenu(screenX, screenY);
        };
        Platform.runLater(showMenu);
    }

    private boolean showArmedExplorerItemContextMenuOnSecondaryRelease(MouseEvent event) {
        if (event == null || event.getButton() != MouseButton.SECONDARY) {
            return false;
        }
        Node target = resolveEventTargetNode(event);
        Path path = resolveExplorerItemContextMenuPath(target);
        if (path == null) {
            path = resolveArmedExplorerItemContextMenuPath(event.getScreenX(), event.getScreenY());
        }
        if (path == null) {
            return false;
        }
        requestExplorerItemContextMenu(path, event.getScreenX(), event.getScreenY());
        return true;
    }

    private boolean shouldSuppressExplorerItemContextMenuRequestedEvent() {
        return explorerItemContextMenuSuppressUntilNanos > System.nanoTime();
    }

    private void debugExplorerContextMenuTarget(String source, Path path) {
        if (path == null) {
            return;
        }
        String name;
        try {
            name = displayNameForTable(path);
        } catch (Exception ex) {
            name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        }
        String message = "EXPLORER_CONTEXT_MENU_TARGET[" + source + "]: " + name + " :: " + path;
        LOG.info(message);
        System.out.println(message);
    }

    private void beginExplorerIconMarqueeGestureOwnership() {
        iconMarqueeGestureOwnsSelection = true;
        suppressExplorerIconClickSelection = true;
    }

    private void releaseExplorerIconMarqueeGestureOwnershipSoon() {
        suppressExplorerIconClickSelectionPulsesRemaining = Math.max(suppressExplorerIconClickSelectionPulsesRemaining, 2);
        Platform.runLater(this::advanceExplorerIconGestureSuppressionRelease);
    }

    private void advanceExplorerIconGestureSuppressionRelease() {
        if (suppressExplorerIconClickSelectionPulsesRemaining > 0) {
            suppressExplorerIconClickSelectionPulsesRemaining--;
            if (suppressExplorerIconClickSelectionPulsesRemaining > 0) {
                Platform.runLater(this::advanceExplorerIconGestureSuppressionRelease);
                return;
            }
        }
        iconMarqueeGestureOwnsSelection = false;
        suppressExplorerIconClickSelection = false;
    }

    private void handleExplorerIconTilePrimaryPress(Path path, MouseEvent event) {
        if (path == null || fileTable == null || fileTable.getSelectionModel() == null) {
            return;
        }
        lastIconActivatedPath = path;
        boolean additive = isExplorerAdditiveSelectionGesture(event);
        if (event != null && event.isShiftDown()) {
            selectExplorerIconRange(path, additive);
            return;
        }
        if (additive) {
            toggleExplorerIconSelection(path);
            return;
        }
        selectOnlyExplorerIconPath(path);
    }

    private void handleDetailsRowPrimaryPress(Path path, MouseEvent event) {
        if (path == null || fileTable == null || fileTable.getSelectionModel() == null) {
            return;
        }
        lastIconActivatedPath = path;
        boolean additive = isExplorerAdditiveSelectionGesture(event);
        if (event != null && event.isShiftDown()) {
            selectExplorerIconRange(path, additive);
            return;
        }
        if (additive) {
            toggleExplorerIconSelection(path);
            return;
        }
        selectOnlyExplorerIconPath(path);
    }

    private boolean isExplorerAdditiveSelectionGesture(MouseEvent event) {
        return event != null && (event.isShortcutDown() || event.isControlDown() || event.isMetaDown());
    }

    private void selectOnlyExplorerIconPath(Path path) {
        int idx = indexOfTableItem(path);
        if (idx < 0 || fileTable == null || fileTable.getSelectionModel() == null) {
            return;
        }
        applyExplorerPathSelection(java.util.Set.of(path), path);
    }

    private void toggleExplorerIconSelection(Path path) {
        int idx = indexOfTableItem(path);
        if (idx < 0 || fileTable == null || fileTable.getSelectionModel() == null) {
            return;
        }
        java.util.LinkedHashSet<Path> paths = explorerSelectionSnapshotForGesture();
        if (paths.contains(path)) {
            paths.remove(path);
        } else {
            paths.add(path);
        }
        if (paths.isEmpty()) {
            fileTable.getSelectionModel().clearSelection();
            if (fileTable.getFocusModel() != null) {
                fileTable.getFocusModel().focus(idx);
            }
            replaceDetailsPresentationSelectedPaths(java.util.Collections.emptySet());
            replaceIconPresentationSelectedPaths(java.util.Collections.emptySet());
            refreshActiveSelectionPresentation();
            setExplorerSelectionAnchorPath(path);
            lastIconActivatedPath = path;
            stabilizeExplorerSelectionAfterGesture(java.util.Collections.emptySet(), path);
            return;
        }
        applyExplorerPathSelection(paths, path);
        stabilizeExplorerSelectionAfterGesture(paths, path);
    }

    private void selectExplorerIconRange(Path targetPath, boolean additive) {
        if (targetPath == null || fileTable == null || fileTable.getItems() == null || fileTable.getSelectionModel() == null) {
            return;
        }
        int targetIndex = indexOfTableItem(targetPath);
        if (targetIndex < 0) {
            return;
        }
        Path anchorPath = iconSelectionAnchorPath != null ? iconSelectionAnchorPath : getFocusedOrSelectedPath();
        int anchorIndex = indexOfTableItem(anchorPath);
        if (anchorIndex < 0) {
            selectOnlyExplorerIconPath(targetPath);
            return;
        }
        java.util.LinkedHashSet<Path> paths = additive
                ? explorerSelectionSnapshotForGesture()
                : new java.util.LinkedHashSet<>();
        int start = Math.min(anchorIndex, targetIndex);
        int end = Math.max(anchorIndex, targetIndex);
        for (int i = start; i <= end; i++) {
            FileItem item = fileTable.getItems().get(i);
            if (item != null && item.path() != null) {
                paths.add(item.path());
            }
        }
        applyExplorerPathSelection(paths, targetPath);
        stabilizeExplorerSelectionAfterGesture(paths, targetPath);
    }

    private void captureExplorerSelectionSnapshotBeforePrimaryPress() {
        explorerSelectionSnapshotBeforePrimaryPress.clear();
        explorerSelectionSnapshotBeforePrimaryPress.addAll(currentExplorerSelectionSnapshot());
    }

    private void clearExplorerSelectionSnapshotBeforePrimaryPress() {
        explorerSelectionSnapshotBeforePrimaryPress.clear();
    }

    private java.util.LinkedHashSet<Path> explorerSelectionSnapshotForGesture() {
        if (!explorerSelectionSnapshotBeforePrimaryPress.isEmpty()) {
            return new java.util.LinkedHashSet<>(explorerSelectionSnapshotBeforePrimaryPress);
        }
        return currentExplorerSelectionSnapshot();
    }

    private java.util.LinkedHashSet<Path> currentExplorerSelectionSnapshot() {
        if (explorerContextMenuSelectionPresentationHold && !explorerContextMenuHeldSelectionPaths.isEmpty()) {
            return new java.util.LinkedHashSet<>(explorerContextMenuHeldSelectionPaths);
        }
        java.util.LinkedHashSet<Path> selectedPaths = new java.util.LinkedHashSet<>();
        if (viewMode == ViewMode.DETAILS) {
            selectedPaths.addAll(detailsPresentationSelectedPaths);
        } else if (isIconMode(viewMode)) {
            selectedPaths.addAll(iconPresentationSelectedPaths);
        }
        if (!selectedPaths.isEmpty()) {
            return selectedPaths;
        }
        if (fileTable != null && fileTable.getSelectionModel() != null) {
            for (FileItem item : fileTable.getSelectionModel().getSelectedItems()) {
                if (item != null && item.path() != null) {
                    selectedPaths.add(item.path());
                }
            }
        }
        return selectedPaths;
    }

    private void stabilizeExplorerSelectionAfterGesture(java.util.Collection<Path> selectedPaths, Path focusPath) {
        java.util.LinkedHashSet<Path> stablePaths = new java.util.LinkedHashSet<>();
        if (selectedPaths != null) {
            for (Path path : selectedPaths) {
                if (path != null) {
                    stablePaths.add(path);
                }
            }
        }
        beginExplorerSelectionStabilization(stablePaths, focusPath);
        scheduleExplorerSelectionStabilization(explorerSelectionStabilizationTicket, stablePaths, focusPath, 8);
    }

    private void beginExplorerSelectionStabilization(java.util.Collection<Path> selectedPaths, Path focusPath) {
        explorerSelectionStabilizationTicket++;
        explorerSelectionStabilizationActive = true;
        explorerSelectionStabilizationPaths.clear();
        if (selectedPaths != null) {
            for (Path path : selectedPaths) {
                if (path != null) {
                    explorerSelectionStabilizationPaths.add(path);
                }
            }
        }
        explorerSelectionStabilizationFocusPath = focusPath;
        applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths);
    }

    private boolean isExplorerSelectionStabilizationActive() {
        return explorerSelectionStabilizationActive;
    }

    private void maintainExplorerSelectionStabilization() {
        if (!isExplorerSelectionStabilizationActive()) {
            return;
        }
        java.util.LinkedHashSet<Path> stablePaths = new java.util.LinkedHashSet<>(explorerSelectionStabilizationPaths);
        applyExplorerSelectionPresentationSnapshot(stablePaths);
        if (isExplorerSelectionModelNotificationActive()) {
            scheduleDeferredExplorerSelectionStabilizationApply();
            scheduleDeferredExplorerPathSelectionApply(stablePaths, explorerSelectionStabilizationFocusPath);
            return;
        }
        if (!selectionPathsEqual(currentTableSelectionSnapshot(), stablePaths)) {
            applyExplorerPathSelection(stablePaths, explorerSelectionStabilizationFocusPath);
        }
    }

    private void scheduleDeferredExplorerSelectionStabilizationApply() {
        if (deferredExplorerSelectionStabilizationApplyScheduled) {
            return;
        }
        deferredExplorerSelectionStabilizationApplyScheduled = true;
        Platform.runLater(() -> {
            deferredExplorerSelectionStabilizationApplyScheduled = false;
            if (!isExplorerSelectionStabilizationActive()) {
                return;
            }
            if (isExplorerSelectionModelNotificationActive()) {
                scheduleDeferredExplorerSelectionStabilizationApply();
                return;
            }
            maintainExplorerSelectionStabilization();
        });
    }

    private void clearExplorerSelectionStabilization(long ticket) {
        if (ticket != explorerSelectionStabilizationTicket) {
            return;
        }
        explorerSelectionStabilizationActive = false;
        explorerSelectionStabilizationPaths.clear();
        explorerSelectionStabilizationFocusPath = null;
        if (viewMode == ViewMode.DETAILS) {
            syncDetailsPresentationSelectedPathsFromTableSelection();
            syncVisibleDetailsHoverRows();
            if (fileTable != null) {
                fileTable.refresh();
            }
        } else if (isIconMode(viewMode)) {
            syncIconPresentationSelectedPathsFromTableSelection();
            refreshVisibleIconTileSelectionState();
        }
    }

    private void scheduleExplorerSelectionStabilization(long ticket,
                                                        java.util.LinkedHashSet<Path> selectedPaths,
                                                        Path focusPath,
                                                        int remainingPulses) {
        if (remainingPulses <= 0) {
            clearExplorerSelectionStabilization(ticket);
            return;
        }
        Platform.runLater(() -> {
            if (ticket != explorerSelectionStabilizationTicket) {
                return;
            }
            if (fileTable == null || fileTable.getSelectionModel() == null) {
                return;
            }
            if (selectedPaths == null || selectedPaths.isEmpty()) {
                beginExplorerSelectionPresentationTransaction();
                try {
                    fileTable.getSelectionModel().clearSelection();
                    int focusIndex = indexOfTableItem(focusPath);
                    if (fileTable.getFocusModel() != null) {
                        fileTable.getFocusModel().focus(focusIndex);
                    }
                } finally {
                    endExplorerSelectionPresentationTransaction();
                }
                replaceDetailsPresentationSelectedPaths(java.util.Collections.emptySet());
                replaceIconPresentationSelectedPaths(java.util.Collections.emptySet());
                refreshActiveSelectionPresentation();
                setExplorerSelectionAnchorPath(focusPath);
                lastIconActivatedPath = focusPath;
            } else if (!selectionPathsEqual(currentTableSelectionSnapshot(), selectedPaths)) {
                applyExplorerPathSelection(selectedPaths, focusPath);
            } else {
                applyExplorerSelectionPresentationSnapshot(selectedPaths);
            }
            scheduleExplorerSelectionStabilization(ticket, selectedPaths, focusPath, remainingPulses - 1);
        });
    }

    private java.util.LinkedHashSet<Path> currentTableSelectionSnapshot() {
        java.util.LinkedHashSet<Path> selectedPaths = new java.util.LinkedHashSet<>();
        if (fileTable == null || fileTable.getSelectionModel() == null) {
            return selectedPaths;
        }
        for (FileItem item : fileTable.getSelectionModel().getSelectedItems()) {
            if (item != null && item.path() != null) {
                selectedPaths.add(item.path());
            }
        }
        return selectedPaths;
    }

    private boolean selectionPathsEqual(java.util.Collection<Path> left, java.util.Collection<Path> right) {
        java.util.LinkedHashSet<Path> leftPaths = new java.util.LinkedHashSet<>();
        if (left != null) {
            for (Path path : left) {
                if (path != null) {
                    leftPaths.add(path);
                }
            }
        }
        java.util.LinkedHashSet<Path> rightPaths = new java.util.LinkedHashSet<>();
        if (right != null) {
            for (Path path : right) {
                if (path != null) {
                    rightPaths.add(path);
                }
            }
        }
        return leftPaths.equals(rightPaths);
    }

    private void applyExplorerSelectionPresentationSnapshot(java.util.Collection<Path> selectedPaths) {
        if (viewMode == ViewMode.DETAILS) {
            replaceDetailsPresentationSelectedPaths(selectedPaths);
            syncVisibleDetailsHoverRows();
            if (fileTable != null) {
                fileTable.refresh();
            }
        } else if (isIconMode(viewMode)) {
            replaceIconPresentationSelectedPaths(selectedPaths);
            refreshVisibleIconTileSelectionState();
        }
    }

private boolean isActiveIconSurfaceFocused() {
        return (virtualIconGridView != null && virtualIconGridView.isVisible() && virtualIconGridView.isFocused())
                || (virtualIconListView != null && virtualIconListView.isVisible() && virtualIconListView.isFocused())
                || (iconFlow != null && iconFlow.isVisible() && iconFlow.isFocused())
                || (iconScroll != null && iconScroll.isVisible() && iconScroll.isFocused())
                || (viewHost != null && viewHost.isFocused());
    }

    private void requestActiveIconSurfaceFocus() {
        if (virtualIconGridView != null && virtualIconGridView.isVisible()) {
            virtualIconGridView.requestFocus();
            return;
        }
        if (virtualIconListView != null && virtualIconListView.isVisible()) {
            virtualIconListView.requestFocus();
            return;
        }
        if (iconFlow != null && iconFlow.isVisible()) {
            iconFlow.requestFocus();
            return;
        }
        if (iconScroll != null && iconScroll.isVisible()) {
            iconScroll.requestFocus();
            return;
        }
        if (viewHost != null) {
            viewHost.requestFocus();
        }
    }

    private void applyExplorerPathSelection(java.util.Collection<Path> paths, Path focusPath) {
        if (fileTable == null || fileTable.getItems() == null || fileTable.getSelectionModel() == null) {
            return;
        }
        java.util.LinkedHashSet<Path> orderedPaths = new java.util.LinkedHashSet<>();
        if (paths != null) {
            for (Path path : paths) {
                if (path != null) {
                    orderedPaths.add(path);
                }
            }
        }
        if (isExplorerSelectionModelNotificationActive()) {
            applyExplorerSelectionPresentationSnapshot(orderedPaths);
            scheduleDeferredExplorerPathSelectionApply(orderedPaths, focusPath);
            return;
        }
        java.util.ArrayList<Integer> selectedIndices = new java.util.ArrayList<>();
        int focusIndex = -1;
        for (int i = 0; i < fileTable.getItems().size(); i++) {
            FileItem item = fileTable.getItems().get(i);
            if (item == null || item.path() == null) {
                continue;
            }
            if (orderedPaths.contains(item.path())) {
                selectedIndices.add(i);
                if (focusIndex < 0 || java.util.Objects.equals(item.path(), focusPath)) {
                    focusIndex = i;
                }
            }
        }
        boolean iconSelectionApply = isIconMode(viewMode);
        if (iconSelectionApply) {
            replaceIconPresentationSelectedPaths(orderedPaths);
            refreshVisibleIconTileSelectionState();
        }
        beginExplorerSelectionPresentationTransaction();
        try {
            fileTable.getSelectionModel().clearSelection();
            if (!selectedIndices.isEmpty()) {
                int firstIndex = selectedIndices.get(0);
                int[] rest = new int[Math.max(0, selectedIndices.size() - 1)];
                for (int i = 1; i < selectedIndices.size(); i++) {
                    rest[i - 1] = selectedIndices.get(i);
                }
                fileTable.getSelectionModel().selectIndices(firstIndex, rest);
            }
            if (fileTable.getFocusModel() != null) {
                fileTable.getFocusModel().focus(focusIndex);
            }
        } finally {
            endExplorerSelectionPresentationTransaction();
        }
        if (focusPath != null && orderedPaths.contains(focusPath)) {
            setExplorerSelectionAnchorPath(focusPath);
        }
        if (viewMode == ViewMode.DETAILS) {
            replaceDetailsPresentationSelectedPaths(orderedPaths);
            fileTable.refresh();
            syncVisibleDetailsHoverRows();
            Platform.runLater(() -> {
                if (isExplorerSelectionStabilizationActive()) {
                    applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths.isEmpty() ? orderedPaths : explorerSelectionStabilizationPaths);
                } else {
                    syncDetailsPresentationSelectedPathsFromTableSelection();
                    syncVisibleDetailsHoverRows();
                    fileTable.refresh();
                }
            });
        } else if (isIconMode(viewMode)) {
            replaceIconPresentationSelectedPaths(orderedPaths);
            refreshVisibleIconTileSelectionState();
            Platform.runLater(() -> {
                if (isExplorerSelectionStabilizationActive()) {
                    applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths.isEmpty() ? orderedPaths : explorerSelectionStabilizationPaths);
                } else {
                    syncIconPresentationSelectedPathsFromTableSelection();
                    refreshVisibleIconTileSelectionState();
                    Platform.runLater(() -> {
                        if (isExplorerSelectionStabilizationActive()) {
                            applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths.isEmpty() ? orderedPaths : explorerSelectionStabilizationPaths);
                        } else {
                            syncIconPresentationSelectedPathsFromTableSelection();
                            refreshVisibleIconTileSelectionState();
                        }
                    });
                }
            });
        }
    }


    private void scheduleDeferredExplorerPathSelectionApply(java.util.Collection<Path> paths, Path focusPath) {
        deferredExplorerPathSelectionPaths.clear();
        if (paths != null) {
            for (Path path : paths) {
                if (path != null) {
                    deferredExplorerPathSelectionPaths.add(path);
                }
            }
        }
        deferredExplorerPathSelectionFocusPath = focusPath;
        if (deferredExplorerPathSelectionApplyScheduled) {
            return;
        }
        deferredExplorerPathSelectionApplyScheduled = true;
        Platform.runLater(this::flushDeferredExplorerPathSelectionApply);
    }

    private void flushDeferredExplorerPathSelectionApply() {
        deferredExplorerPathSelectionApplyScheduled = false;
        if (isExplorerSelectionModelNotificationActive()) {
            deferredExplorerPathSelectionApplyScheduled = true;
            Platform.runLater(this::flushDeferredExplorerPathSelectionApply);
            return;
        }
        java.util.LinkedHashSet<Path> pendingPaths = new java.util.LinkedHashSet<>(deferredExplorerPathSelectionPaths);
        Path pendingFocusPath = deferredExplorerPathSelectionFocusPath;
        deferredExplorerPathSelectionPaths.clear();
        deferredExplorerPathSelectionFocusPath = null;
        applyExplorerPathSelection(pendingPaths, pendingFocusPath);
    }

    private java.util.LinkedHashSet<Path> explorerContextMenuHeldSelectionSnapshot() {
        return new java.util.LinkedHashSet<>(explorerContextMenuHeldSelectionPaths);
    }

    private void maintainExplorerContextMenuSelectionHold() {
        if (!explorerContextMenuSelectionPresentationHold) {
            return;
        }
        java.util.LinkedHashSet<Path> heldSelection = explorerContextMenuHeldSelectionSnapshot();
        if (explorerContextMenuOwnedPath != null) {
            heldSelection.add(explorerContextMenuOwnedPath);
        }
        if (heldSelection.isEmpty()) {
            return;
        }
        Path heldFocusPath = explorerContextMenuOwnedPath != null
                ? explorerContextMenuOwnedPath
                : explorerContextMenuHeldFocusPath;
        if (heldFocusPath == null || !heldSelection.contains(heldFocusPath)) {
            heldFocusPath = heldSelection.iterator().next();
            explorerContextMenuHeldFocusPath = heldFocusPath;
        }
        applyExplorerSelectionPresentationSnapshot(heldSelection);
        if (fileTable == null || fileTable.getSelectionModel() == null) {
            return;
        }
        if (!selectionPathsEqual(currentTableSelectionSnapshot(), heldSelection)) {
            if (isExplorerSelectionModelNotificationActive()) {
                scheduleDeferredExplorerPathSelectionApply(heldSelection, heldFocusPath);
            } else {
                applyExplorerPathSelection(heldSelection, heldFocusPath);
            }
            return;
        }
        if (heldFocusPath != null && fileTable.getFocusModel() != null) {
            int focusIndex = indexOfTableItem(heldFocusPath);
            if (focusIndex >= 0 && fileTable.getFocusModel().getFocusedIndex() != focusIndex) {
                fileTable.getFocusModel().focus(focusIndex);
            }
        }
    }

    private void preserveExplorerSelectionPresentationForContextMenu() {
        explorerContextMenuSelectionPresentationHold = true;
        explorerContextMenuHeldSelectionPaths.clear();
        java.util.LinkedHashSet<Path> snapshot = currentExplorerSelectionSnapshot();
        if (snapshot.isEmpty()) {
            Path fallbackPath = explorerContextMenuOwnedPath != null ? explorerContextMenuOwnedPath : getFocusedOrSelectedPath();
            if (fallbackPath != null) {
                snapshot.add(fallbackPath);
            }
        }
        if (explorerContextMenuOwnedPath != null) {
            snapshot.add(explorerContextMenuOwnedPath);
        }
        explorerContextMenuHeldSelectionPaths.addAll(snapshot);
        explorerContextMenuHeldFocusPath = explorerContextMenuOwnedPath != null
                ? explorerContextMenuOwnedPath
                : getFocusedOrSelectedPath();
        if ((explorerContextMenuHeldFocusPath == null || !explorerContextMenuHeldSelectionPaths.contains(explorerContextMenuHeldFocusPath))
                && !explorerContextMenuHeldSelectionPaths.isEmpty()) {
            explorerContextMenuHeldFocusPath = explorerContextMenuHeldSelectionPaths.iterator().next();
        }
        maintainExplorerContextMenuSelectionHold();
    }

    private void clearExplorerContextMenuSelectionPresentationHold() {
        if (!explorerContextMenuSelectionPresentationHold) {
            explorerContextMenuOwnedPath = null;
            return;
        }
        explorerContextMenuSelectionPresentationHold = false;
        explorerContextMenuHeldSelectionPaths.clear();
        explorerContextMenuHeldFocusPath = null;
        explorerContextMenuOwnedPath = null;
        refreshActiveSelectionPresentation();
    }

    private void beginExplorerSelectionPresentationTransaction() {
        explorerSelectionPresentationTransactionDepth++;
    }

    private void endExplorerSelectionPresentationTransaction() {
        if (explorerSelectionPresentationTransactionDepth > 0) {
            explorerSelectionPresentationTransactionDepth--;
        }
    }

    private boolean isExplorerSelectionPresentationTransactionActive() {
        return explorerSelectionPresentationTransactionDepth > 0;
    }

    private void runWithinExplorerSelectionModelNotification(Runnable action) {
        explorerSelectionModelNotificationDepth++;
        try {
            action.run();
        } finally {
            if (explorerSelectionModelNotificationDepth > 0) {
                explorerSelectionModelNotificationDepth--;
            }
        }
    }

    private boolean isExplorerSelectionModelNotificationActive() {
        return explorerSelectionModelNotificationDepth > 0;
    }

    private void syncDetailsPresentationSelectedPathsFromTableSelection() {
        if (fileTable == null || fileTable.getSelectionModel() == null) {
            replaceDetailsPresentationSelectedPaths(java.util.Collections.emptySet());
            return;
        }
        java.util.LinkedHashSet<Path> selectedPaths = new java.util.LinkedHashSet<>();
        for (FileItem item : fileTable.getSelectionModel().getSelectedItems()) {
            if (item != null && item.path() != null) {
                selectedPaths.add(item.path());
            }
        }
        replaceDetailsPresentationSelectedPaths(selectedPaths);
    }

    private void replaceDetailsPresentationSelectedPaths(java.util.Collection<Path> selectedPaths) {
        detailsPresentationSelectedPaths.clear();
        if (selectedPaths != null) {
            for (Path path : selectedPaths) {
                if (path != null) {
                    detailsPresentationSelectedPaths.add(path);
                }
            }
        }
    }

    private void syncIconPresentationSelectedPathsFromTableSelection() {
        if (fileTable == null || fileTable.getSelectionModel() == null) {
            replaceIconPresentationSelectedPaths(java.util.Collections.emptySet());
            return;
        }
        java.util.LinkedHashSet<Path> selectedPaths = new java.util.LinkedHashSet<>();
        for (FileItem item : fileTable.getSelectionModel().getSelectedItems()) {
            if (item != null && item.path() != null) {
                selectedPaths.add(item.path());
            }
        }
        replaceIconPresentationSelectedPaths(selectedPaths);
    }

    private void replaceIconPresentationSelectedPaths(java.util.Collection<Path> selectedPaths) {
        iconPresentationSelectedPaths.clear();
        if (selectedPaths != null) {
            for (Path path : selectedPaths) {
                if (path != null) {
                    iconPresentationSelectedPaths.add(path);
                }
            }
        }
    }

    private void refreshVisibleIconSelectionPresentation() {
        if (explorerContextMenuSelectionPresentationHold) {
            maintainExplorerContextMenuSelectionHold();
            Platform.runLater(() -> {
                if (explorerContextMenuSelectionPresentationHold) {
                    maintainExplorerContextMenuSelectionHold();
                }
            });
            return;
        }
        if (isExplorerSelectionStabilizationActive()) {
            applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths);
            Platform.runLater(() -> {
                if (isExplorerSelectionStabilizationActive()) {
                    applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths);
                }
            });
            return;
        }
        syncIconPresentationSelectedPathsFromTableSelection();
        refreshVisibleIconTileSelectionState();
        Platform.runLater(() -> {
            syncIconPresentationSelectedPathsFromTableSelection();
            refreshVisibleIconTileSelectionState();
            Platform.runLater(() -> {
                syncIconPresentationSelectedPathsFromTableSelection();
                refreshVisibleIconTileSelectionState();
            });
        });
    }

    private void ensureExplorerFileViewSelectionInteractionsInstalled() {
        if (iconMarqueeInteractionInstalled || viewHost == null) {
            return;
        }
        iconMarqueeInteractionInstalled = true;
        if (iconMarqueeSelectionRect == null) {
            iconMarqueeSelectionRect = new Rectangle();
            iconMarqueeSelectionRect.setManaged(false);
            iconMarqueeSelectionRect.setMouseTransparent(true);
            iconMarqueeSelectionRect.setVisible(false);
            iconMarqueeSelectionRect.setStroke(Color.rgb(200, 225, 255, 0.92));
            iconMarqueeSelectionRect.setStrokeWidth(1.0);
            iconMarqueeSelectionRect.setFill(Color.rgb(98, 163, 241, 0.22));
        }
        if (!viewHost.getChildren().contains(iconMarqueeSelectionRect)) {
            viewHost.getChildren().add(iconMarqueeSelectionRect);
        }
        bringIconMarqueeOverlayToFront();
        viewHost.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleExplorerFileViewItemSecondaryPress);
        viewHost.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleExplorerFileViewMousePressed);
        viewHost.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleExplorerFileViewMouseDragged);
        viewHost.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleExplorerFileViewMouseReleased);
        viewHost.addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleExplorerFileViewMouseClicked);
        viewHost.addEventFilter(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::handleExplorerFileViewContextMenuRequested);
    }

    private void bringIconMarqueeOverlayToFront() {
        if (iconMarqueeSelectionRect != null) {
            iconMarqueeSelectionRect.toFront();
        }
    }

    private void handleExplorerFileViewItemSecondaryPress(MouseEvent event) {
        if (event == null || event.getButton() != MouseButton.SECONDARY || !isIconMode(viewMode)) {
            return;
        }
        if (isInlineRenameFocusGuardActive()) {
            event.consume();
            return;
        }
        Node target = resolveEventTargetNode(event);
        Path path = findExplorerIconTilePath(target);
        if (path == null) {
            return;
        }
        if (viewMode == ViewMode.EXTRA_LARGE_ICONS) {
            debugExplorerContextMenuTarget("secondary-press", path);
        }
        armExplorerItemContextMenu(path, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private void handleExplorerFileViewMousePressed(MouseEvent event) {
        if (event == null || event.getButton() != MouseButton.PRIMARY || !isSelectionMarqueeMode(viewMode)) {
            return;
        }
        captureExplorerSelectionSnapshotBeforePrimaryPress();
        if (isInlineRenameFocusGuardActive()) {
            event.consume();
            return;
        }
        Node target = resolveEventTargetNode(event);
        boolean pressOnExistingItem = false;
        if (viewMode == ViewMode.DETAILS) {
            if (!isNodeWithinDetailsSelectionSurface(target) || isDetailsMarqueeExcludedTarget(target)) {
                clearExplorerSelectionSnapshotBeforePrimaryPress();
                return;
            }
            TableRow<?> targetRow = findAncestorTableRow(target);
            pressOnExistingItem = targetRow != null && !targetRow.isEmpty();
        } else {
            if (!isNodeWithinActiveIconSurface(target)) {
                clearExplorerSelectionSnapshotBeforePrimaryPress();
                return;
            }
            pressOnExistingItem = findExplorerIconTilePath(target) != null;
        }
        iconMarqueePressArmed = true;
        iconMarqueeDragStarted = false;
        iconMarqueePressOnExistingItem = pressOnExistingItem;
        iconMarqueeGestureOwnsSelection = false;
        suppressExplorerIconClickSelection = false;
        marqueeSelectionMode = viewMode;
        iconMarqueeAdditive = isExplorerAdditiveSelectionGesture(event);
        iconMarqueePressSceneX = event.getSceneX();
        iconMarqueePressSceneY = event.getSceneY();
        iconMarqueeBaseSelection.clear();
        pendingExplorerMarqueeSelectionPaths.clear();
        pendingExplorerMarqueeFocusPath = null;
        if (iconMarqueeAdditive) {
            iconMarqueeBaseSelection.addAll(explorerSelectionSnapshotForGesture());
        }
        hideExplorerMetadataPopup();
        if (marqueeSelectionMode == ViewMode.DETAILS) {
            requestActiveDetailsSurfaceFocus();
        } else {
            requestActiveIconSurfaceFocus();
        }
        if (marqueeSelectionMode == ViewMode.DETAILS && !iconMarqueeAdditive) {
            replaceDetailsPresentationSelectedPaths(java.util.Collections.emptySet());
            syncVisibleDetailsHoverRows();
        }
        if (!pressOnExistingItem) {
            event.consume();
        }
    }

    private void handleExplorerFileViewMouseDragged(MouseEvent event) {
        if (!iconMarqueePressArmed || event == null || !isSelectionMarqueeMode(marqueeSelectionMode)) {
            return;
        }
        double dx = event.getSceneX() - iconMarqueePressSceneX;
        double dy = event.getSceneY() - iconMarqueePressSceneY;
        if (!iconMarqueeDragStarted && Math.hypot(dx, dy) < 4.0) {
            if (!iconMarqueePressOnExistingItem) {
                event.consume();
            }
            return;
        }
        if (!iconMarqueeDragStarted) {
            iconMarqueeDragStarted = true;
            if (isIconMode(marqueeSelectionMode)) {
                beginExplorerIconMarqueeGestureOwnership();
            }
            bringIconMarqueeOverlayToFront();
            if (marqueeSelectionMode == ViewMode.DETAILS) {
                requestActiveDetailsSurfaceFocus();
            } else {
                requestActiveIconSurfaceFocus();
            }
        }
        updateExplorerFileViewMarquee(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    private void handleExplorerFileViewMouseReleased(MouseEvent event) {
        if (event != null && event.getButton() == MouseButton.SECONDARY && isIconMode(viewMode)) {
            if (showArmedExplorerItemContextMenuOnSecondaryRelease(event)) {
                event.consume();
            }
            return;
        }
        if (isInlineRenameFocusGuardActive()) {
            if (event != null) {
                event.consume();
            }
            return;
        }
        if (!iconMarqueePressArmed || event == null) {
            return;
        }
        boolean committedMarqueeDragStarted = iconMarqueeDragStarted;
        boolean shouldConsume = committedMarqueeDragStarted || !iconMarqueePressOnExistingItem;
        if (committedMarqueeDragStarted) {
            updatePendingExplorerMarqueeSelection(event.getSceneX(), event.getSceneY(), false);
        }
        java.util.LinkedHashSet<Path> committedMarqueeSelection = new java.util.LinkedHashSet<>(pendingExplorerMarqueeSelectionPaths);
        Path committedMarqueeFocusPath = pendingExplorerMarqueeFocusPath;
        ViewMode committedMarqueeMode = marqueeSelectionMode;
        if (!committedMarqueeDragStarted && !iconMarqueeAdditive && !iconMarqueePressOnExistingItem) {
            if (fileTable != null && fileTable.getSelectionModel() != null) {
                fileTable.getSelectionModel().clearSelection();
            }
            if (fileTable != null && fileTable.getFocusModel() != null) {
                fileTable.getFocusModel().focus(-1);
            }
            replaceIconPresentationSelectedPaths(java.util.Collections.emptySet());
            refreshVisibleIconTileSelectionState();
            setExplorerSelectionAnchorPath(null);
            resetExplorerFileViewMarquee();
            releaseExplorerIconMarqueeGestureOwnershipSoon();
        } else if (committedMarqueeDragStarted && committedMarqueeMode == ViewMode.DETAILS) {
            applyExplorerPathSelection(committedMarqueeSelection, committedMarqueeFocusPath);
            stabilizeExplorerSelectionAfterGesture(committedMarqueeSelection, committedMarqueeFocusPath);
            resetExplorerFileViewMarquee();
            releaseExplorerIconMarqueeGestureOwnershipSoon();
        } else if (committedMarqueeDragStarted && isIconMode(committedMarqueeMode)) {
            beginExplorerIconMarqueeGestureOwnership();
            replaceIconPresentationSelectedPaths(committedMarqueeSelection);
            refreshVisibleIconTileSelectionState();
            requestActiveIconSurfaceFocus();
            applyExplorerPathSelection(committedMarqueeSelection, committedMarqueeFocusPath);
            stabilizeExplorerSelectionAfterGesture(committedMarqueeSelection, committedMarqueeFocusPath);
            resetExplorerFileViewMarquee();
            Platform.runLater(() -> {
                requestActiveIconSurfaceFocus();
                replaceIconPresentationSelectedPaths(committedMarqueeSelection);
                refreshVisibleIconSelectionPresentation();
                Platform.runLater(() -> {
                    requestActiveIconSurfaceFocus();
                    replaceIconPresentationSelectedPaths(committedMarqueeSelection);
                    refreshVisibleIconSelectionPresentation();
                    releaseExplorerIconMarqueeGestureOwnershipSoon();
                });
            });
        } else {
            resetExplorerFileViewMarquee();
            releaseExplorerIconMarqueeGestureOwnershipSoon();
        }
        clearExplorerSelectionSnapshotBeforePrimaryPress();
        if (shouldConsume) {
            event.consume();
        }
    }

    private void handleExplorerFileViewMouseClicked(MouseEvent event) {
        if (event == null || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (shouldSuppressExplorerIconPrimaryGestureHandling() || iconMarqueePressArmed || iconMarqueeDragStarted) {
            event.consume();
        }
    }


    private void handleExplorerFileViewContextMenuRequested(javafx.scene.input.ContextMenuEvent event) {
        if (event == null || !isIconMode(viewMode)) {
            return;
        }
        if (shouldSuppressExplorerItemContextMenuRequestedEvent()) {
            event.consume();
            return;
        }
        if (isInlineRenameFocusGuardActive()) {
            event.consume();
            return;
        }
        Node target = event.getPickResult() != null && event.getPickResult().getIntersectedNode() != null
                ? event.getPickResult().getIntersectedNode()
                : (event.getTarget() instanceof Node node ? node : null);
        if (handleExplorerItemContextMenuRequest(target, event.getScreenX(), event.getScreenY())) {
            Path path = findExplorerIconTilePath(target);
            if (viewMode == ViewMode.EXTRA_LARGE_ICONS && path != null) {
                debugExplorerContextMenuTarget("context-menu-requested", path);
            }
            event.consume();
            return;
        }
        if (target != null && !isNodeWithinActiveIconSurface(target) && target != viewHost) {
            return;
        }
        hideExplorerMetadataPopup();
        requestActiveIconSurfaceFocus();
        showFileViewBackgroundContextMenu(target != null ? target : viewHost, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private void updateExplorerFileViewMarquee(double sceneX, double sceneY) {
        if (viewHost == null || iconMarqueeSelectionRect == null || !isSelectionMarqueeMode(marqueeSelectionMode)) {
            return;
        }
        Point2D start = viewHost.sceneToLocal(iconMarqueePressSceneX, iconMarqueePressSceneY);
        Point2D current = viewHost.sceneToLocal(sceneX, sceneY);
        double x = Math.min(start.getX(), current.getX());
        double y = Math.min(start.getY(), current.getY());
        double w = Math.abs(current.getX() - start.getX());
        double h = Math.abs(current.getY() - start.getY());
        iconMarqueeSelectionRect.setX(x);
        iconMarqueeSelectionRect.setY(y);
        iconMarqueeSelectionRect.setWidth(w);
        iconMarqueeSelectionRect.setHeight(h);
        iconMarqueeSelectionRect.setVisible(true);
        updatePendingExplorerMarqueeSelection(sceneX, sceneY, true);
    }

    private void updatePendingExplorerMarqueeSelection(double sceneX, double sceneY, boolean applyLiveSelection) {
        double minSceneX = Math.min(iconMarqueePressSceneX, sceneX);
        double minSceneY = Math.min(iconMarqueePressSceneY, sceneY);
        double maxSceneX = Math.max(iconMarqueePressSceneX, sceneX);
        double maxSceneY = Math.max(iconMarqueePressSceneY, sceneY);

        java.util.LinkedHashSet<Path> selectedPaths = iconMarqueeAdditive
                ? new java.util.LinkedHashSet<>(iconMarqueeBaseSelection)
                : new java.util.LinkedHashSet<>();
        Path focusPath = null;
        if (marqueeSelectionMode == ViewMode.DETAILS) {
            for (TableRow<FileItem> row : collectVisibleDetailsRows()) {
                FileItem item = row.getItem();
                Path path = item != null ? item.path() : null;
                if (path == null) {
                    continue;
                }
                Bounds bounds = row.localToScene(row.getBoundsInLocal());
                if (bounds == null) {
                    continue;
                }
                if (sceneBoundsIntersect(bounds, minSceneX, minSceneY, maxSceneX, maxSceneY)) {
                    selectedPaths.add(path);
                    focusPath = path;
                }
            }
        } else {
            for (Node tile : collectVisibleExplorerIconTiles()) {
                Path path = pathForExplorerIconTile(tile);
                if (path == null) {
                    continue;
                }
                Bounds bounds = tile.localToScene(tile.getBoundsInLocal());
                if (bounds == null) {
                    continue;
                }
                if (sceneBoundsIntersect(bounds, minSceneX, minSceneY, maxSceneX, maxSceneY)) {
                    selectedPaths.add(path);
                    focusPath = path;
                }
            }
        }
        boolean selectionChanged = !pendingExplorerMarqueeSelectionPaths.equals(selectedPaths)
                || !java.util.Objects.equals(pendingExplorerMarqueeFocusPath, focusPath);
        pendingExplorerMarqueeSelectionPaths.clear();
        pendingExplorerMarqueeSelectionPaths.addAll(selectedPaths);
        pendingExplorerMarqueeFocusPath = focusPath;
        if (applyLiveSelection && selectionChanged) {
            if (isIconMode(marqueeSelectionMode)) {
                replaceIconPresentationSelectedPaths(selectedPaths);
                refreshVisibleIconTileSelectionState();
            } else {
                replaceDetailsPresentationSelectedPaths(selectedPaths);
                syncVisibleDetailsHoverRows();
            }
        }
    }

    private void resetExplorerFileViewMarquee() {
        iconMarqueePressArmed = false;
        iconMarqueeDragStarted = false;
        iconMarqueeAdditive = false;
        iconMarqueePressOnExistingItem = false;
        marqueeSelectionMode = null;
        iconMarqueePressSceneX = Double.NaN;
        iconMarqueePressSceneY = Double.NaN;
        iconMarqueeBaseSelection.clear();
        pendingExplorerMarqueeSelectionPaths.clear();
        pendingExplorerMarqueeFocusPath = null;
        if (iconMarqueeSelectionRect != null) {
            iconMarqueeSelectionRect.setVisible(false);
            iconMarqueeSelectionRect.setX(0.0);
            iconMarqueeSelectionRect.setY(0.0);
            iconMarqueeSelectionRect.setWidth(0.0);
            iconMarqueeSelectionRect.setHeight(0.0);
        }
    }

    private boolean isSelectionMarqueeMode(ViewMode mode) {
        return mode == ViewMode.DETAILS || isIconMode(mode);
    }

    private void requestActiveDetailsSurfaceFocus() {
        if (fileTable != null) {
            fileTable.requestFocus();
            return;
        }
        if (detailsViewShell != null) {
            detailsViewShell.requestFocus();
            return;
        }
        if (viewHost != null) {
            viewHost.requestFocus();
        }
    }

    private boolean isNodeWithinDetailsSelectionSurface(Node node) {
        if (node == null || viewMode != ViewMode.DETAILS) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == fileTable || current == detailsViewShell || current == viewHost) {
                return true;
            }
        }
        return false;
    }

    private boolean isDetailsMarqueeExcludedTarget(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof ScrollBar) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("TableColumnHeader") || className.contains("TableHeaderRow")) {
                return true;
            }
            ObservableList<String> styleClasses = current.getStyleClass();
            if (styleClasses.contains("column-header")
                    || styleClasses.contains("nested-column-header")
                    || styleClasses.contains("filler")
                    || styleClasses.contains("show-hide-columns-button")) {
                return true;
            }
            if (current == fileTable || current == detailsViewShell || current == viewHost) {
                break;
            }
        }
        return false;
    }

    private java.util.List<TableRow<FileItem>> collectVisibleDetailsRows() {
        java.util.ArrayList<TableRow<FileItem>> rows = new java.util.ArrayList<>();
        if (fileTable == null || !fileTable.isVisible()) {
            return rows;
        }
        for (Node node : fileTable.lookupAll(".table-row-cell")) {
            if (node instanceof TableRow<?> rawRow && rawRow.getTableView() == fileTable && !rawRow.isEmpty() && rawRow.getItem() != null) {
                @SuppressWarnings("unchecked")
                TableRow<FileItem> row = (TableRow<FileItem>) rawRow;
                rows.add(row);
            }
        }
        rows.sort(java.util.Comparator.comparingInt(TableRow::getIndex));
        return rows;
    }

    private java.util.List<Node> collectVisibleExplorerIconTiles() {
        java.util.ArrayList<Node> out = new java.util.ArrayList<>();
        collectVisibleExplorerIconTiles(iconFlow, out);
        collectVisibleExplorerIconTiles(virtualIconGridView, out);
        collectVisibleExplorerIconTiles(virtualIconListView, out);
        return out;
    }

    private void collectVisibleExplorerIconTiles(Node node, java.util.List<Node> out) {
        if (node == null || !node.isVisible()) {
            return;
        }
        if (pathForExplorerIconTile(node) != null) {
            out.add(node);
            return;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectVisibleExplorerIconTiles(child, out);
            }
        }
    }

    private boolean sceneBoundsIntersect(Bounds bounds, double minSceneX, double minSceneY, double maxSceneX, double maxSceneY) {
        return bounds.getMaxX() >= minSceneX
                && bounds.getMinX() <= maxSceneX
                && bounds.getMaxY() >= minSceneY
                && bounds.getMinY() <= maxSceneY;
    }

    private Path pathForExplorerIconTile(Node node) {
        if (node == null) {
            return null;
        }
        Object taggedPath = node.getProperties().get(EXPLORER_ICON_TILE_PATH_KEY);
        return taggedPath instanceof Path path ? path : null;
    }

    private Path findExplorerIconTilePath(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            Path path = pathForExplorerIconTile(current);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private Node resolveEventTargetNode(MouseEvent event) {
        if (event == null) {
            return null;
        }
        if (event.getPickResult() != null && event.getPickResult().getIntersectedNode() != null) {
            return event.getPickResult().getIntersectedNode();
        }
        Object target = event.getTarget();
        return target instanceof Node node ? node : null;
    }

    private boolean isNodeWithinActiveIconSurface(Node node) {
        if (node == null || !isIconMode(viewMode)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == fileTable || current == detailsViewShell) {
                return false;
            }
            if (current == iconFlow || current == iconScroll || current == virtualIconGridView || current == virtualIconListView || current == viewHost) {
                return true;
            }
        }
        return false;
    }
/**
 * buildIconNode.
 *
 * @param p TODO
 * @param sizePx TODO
 * @param styleClasses TODO
 * @return TODO
 */
    private Node buildIconNode(Path p, double sizePx, String... styleClasses) {
        LogSupport.enter(LOG, "buildIconNode");
        double effective = sizePx;
        if (Double.isNaN(effective) || effective <= 0.0) {
            effective = 16.0;
        }
        final String identity = resolveIconIdentityForPath(p);
        final int px = (int) Math.round(effective);
        ImageView iv = new ImageView(resolvePlaceholderImageForPath(p, px));
        iv.setPreserveRatio(true);
        boolean intrinsicExtraLarge = viewMode == ViewMode.EXTRA_LARGE_ICONS && effective >= (EXTRA_LARGE_ICON_CELL_PX - 0.5);
        iv.setSmooth(!intrinsicExtraLarge);
        if (!intrinsicExtraLarge) {
            iv.setFitWidth(effective);
            iv.setFitHeight(effective);
        }
        if (styleClasses != null) {
            for (String s : styleClasses) {
                if (s != null && !s.isBlank()) {
                    iv.getStyleClass().add(s);
                }
            }
        }
        // If the file is a supported thumbnail candidate, lazily upgrade to a generated thumbnail.
        if (p != null && ImageSupport.isThumbCandidate(p)) {
            Path captured = p;
            final long thumbStamp = System.nanoTime();
            iv.getProperties().put("thumbStamp", thumbStamp);
            iv.getProperties().put("thumbPath", captured);
            iv.getProperties().put("thumbIdentity", identity);
            AsyncThumbnailService.getInstance()
                    .request(captured, px, AsyncThumbnailService.RequestPriority.VISIBLE)
                    .thenAccept(img -> Platform.runLater(() -> {
                        if (img == null) return;
                        Object s = iv.getProperties().get("thumbStamp");
                        Object boundPath = iv.getProperties().get("thumbPath");
                        Object boundIdentity = iv.getProperties().get("thumbIdentity");
                        if (!(s instanceof Long) || ((Long) s) != thumbStamp) return;
                        if (!java.util.Objects.equals(boundPath, captured)) return;
                        if (!java.util.Objects.equals(boundIdentity, identity)) return;
                        iv.setImage(img);
                    }));
            return iv;
        }
        // Otherwise, lazily upgrade placeholder to a real icon via the async icon service.
        final boolean dark = themeService.isDarkPreferred();
        final String capturedIdentity = identity;
        final long iconStamp = System.nanoTime();
        iv.getProperties().put(REALIZATION_ICON_STAMP_KEY, iconStamp);
        iv.getProperties().put("iconIdentity", capturedIdentity);
        AsyncIconService.getInstance()
                .request(capturedIdentity, dark, px, AsyncIconService.RequestPriority.VISIBLE)
                .thenAccept(img -> Platform.runLater(() -> {
                    if (img == null) return;
                    Object stamp = iv.getProperties().get(REALIZATION_ICON_STAMP_KEY);
                    Object boundIdentity = iv.getProperties().get("iconIdentity");
                    if (!(stamp instanceof Long) || ((Long) stamp) != iconStamp) return;
                    if (!java.util.Objects.equals(boundIdentity, capturedIdentity)) return;
                    iv.setImage(img);
                }));
        return iv;
    }
/**
 * effectiveTreeIconPx.
 *
 * @return TODO
 */
    private double effectiveTreeIconPx() {
        LogSupport.enter(LOG, "effectiveTreeIconPx");
        double base = treeFontSizePxApplied > 0.0 ? treeFontSizePxApplied : uiFontSizePx;
        return clamp(base + 4.0, 16.0, 24.0);
    }
/**
 * glyphForIdentity.
 *
 * @param identity TODO
 * @param p TODO
 * @return TODO
 */
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
                    case "png", "jpg", "jpeg", "gif", "bmp", "webp", "avif", "heif", "heic", "tif", "tiff" -> {
                        return ""; // Picture
                    }
                    case "txt", "md", "log", "json", "xml", "csv", "yml", "yaml" -> {
                        return ""; // Document
                    }
                }
            }
            if (id.startsWith("kind:")) {
                String kind = id.substring("kind:".length()).trim();
                switch (kind) {
                    case "archive" -> {
                        return "";
                    }
                    case "audio" -> {
                        return "";
                    }
                    case "image" -> {
                        return "";
                    }
                    case "text", "pdf" -> {
                        return "";
                    }
                    case "video" -> {
                        return "";
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
    private void configureTabsAndHome() {
        applyExplorerTabChrome(homeTabButton, null);
        applyExplorerTabChrome(currentTabButton, currentDirectory);
        applyCloseTabButtonChrome(closeTabButton);
        applyExplorerTabChrome(newTabButton, null);
        refreshHomeSurface();
        updateTabStrip();
        applyHomeModeVisibility();
    }

    private void applyExplorerTabChrome(Button button, Path iconPath) {
        if (button == null) {
            return;
        }
        button.setTranslateY(2.0);
        if (button == newTabButton) {
            button.setContentDisplay(ContentDisplay.LEFT);
            button.setGraphicTextGap(0.0);
            button.setGraphic(null);
            return;
        }
        String labelText = button.getText();
        Object storedLabel = button.getProperties().get("explorerTabLabelText");
        if ((labelText == null || labelText.isBlank()) && storedLabel instanceof String stored && !stored.isBlank()) {
            labelText = stored;
        }
        if (labelText == null) {
            labelText = "";
        }
        labelText = labelText.trim();
        if (!labelText.isBlank()) {
            button.getProperties().put("explorerTabLabelText", labelText);
            button.setAccessibleText(labelText);
        }
        button.setText("");
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setGraphicTextGap(0.0);

        boolean dark = themeService != null && themeService.isDarkPreferred();
        Image iconImage = iconPath != null
                ? IconLoader.loadForPath(iconPath, dark, 16)
                : (button == homeTabButton
                    ? IconLoader.loadForIdentity("special:home", dark, 16)
                    : IconLoader.load(IconLoader.IconType.FOLDER, dark, 16));
        ImageView iconView = new ImageView(iconImage);
        iconView.setFitWidth(16.0);
        iconView.setFitHeight(16.0);
        iconView.setPreserveRatio(true);
        iconView.setSmooth(true);
        iconView.setMouseTransparent(true);

        Label textLabel = new Label(labelText);
        textLabel.getStyleClass().add("explorer-tab-text");
        textLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        textLabel.setMouseTransparent(true);
        textLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textLabel, Priority.ALWAYS);

        HBox content = new HBox(iconView, textLabel);
        if (button == homeTabButton || button == currentTabButton) {
            content.getChildren().add(createInlineTabCloseSlot(button));
        }
        content.getStyleClass().add("explorer-tab-content");
        content.setAlignment(Pos.CENTER_LEFT);
        content.setFillHeight(true);
        content.setMouseTransparent(false);

        button.setGraphic(content);
    }

    private Node createInlineTabCloseSlot(Button ownerButton) {
        StackPane closeSlot = new StackPane();
        closeSlot.getStyleClass().add("explorer-tab-close-slot");
        closeSlot.setAlignment(Pos.CENTER);
        closeSlot.setPickOnBounds(true);
        closeSlot.setCursor(Cursor.HAND);
        closeSlot.setFocusTraversable(false);

        Label closeGlyph = new Label("×");
        closeGlyph.getStyleClass().add("explorer-tab-inline-close-glyph");
        closeGlyph.setMouseTransparent(true);
        closeSlot.getChildren().add(closeGlyph);

        closeSlot.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> evt.consume());
        closeSlot.addEventFilter(MouseEvent.MOUSE_RELEASED, MouseEvent::consume);
        closeSlot.addEventFilter(MouseEvent.MOUSE_CLICKED, evt -> {
            if (evt.getButton() == MouseButton.PRIMARY) {
                closeExplorerTab(ownerButton);
            }
            evt.consume();
        });
        return closeSlot;
    }

    private void applyCloseTabButtonChrome(Button button) {
        if (button == null) {
            return;
        }
        button.setTranslateY(2.0);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setGraphicTextGap(0.0);
        Label closeGlyph = new Label("×");
        closeGlyph.getStyleClass().add("explorer-strip-close-glyph");
        closeGlyph.setMouseTransparent(true);
        button.setText("");
        button.setGraphic(closeGlyph);
        button.setAccessibleText("Close current tab");
    }

    private void closeActiveExplorerTab() {
        if (!homeActive && currentTabVisible) {
            closeExplorerTab(currentTabButton);
            return;
        }
        if (homeTabVisible) {
            closeExplorerTab(homeTabButton);
            return;
        }
        if (currentTabVisible) {
            closeExplorerTab(currentTabButton);
        }
    }

    @FXML
    private void onCloseCurrentTabButton() {
        closeActiveExplorerTab();
    }

    private void closeExplorerTab(Button button) {
        if (button == null) {
            return;
        }
        if (button == homeTabButton) {
            if (homeActive && currentDirectory == null && !currentTabVisible) {
                homeTabVisible = true;
                updateTabStrip();
                return;
            }
            homeTabVisible = false;
            if (homeActive) {
                if (currentDirectory != null) {
                    hideHomePage();
                    return;
                }
                homeTabVisible = true;
            }
        } else if (button == currentTabButton) {
            currentTabVisible = false;
            if (!homeActive) {
                showHomePage();
                return;
            }
        }
        ensureAtLeastOneExplorerTabVisible();
        updateTabStrip();
    }

    private void ensureAtLeastOneExplorerTabVisible() {
        if (homeTabVisible || currentTabVisible) {
            return;
        }
        if (currentDirectory != null) {
            currentTabVisible = true;
        } else {
            homeTabVisible = true;
        }
    }

    @FXML
    private void onShowHomeTab() {
        showHomePage();
    }

    @FXML
    private void onShowCurrentTab() {
        hideHomePage();
    }

    @FXML
    private void onOpenNewTabButton() {
        openNewWindow(currentDirectory);
    }

    private void showHomePage() {
        homeActive = true;
        homeTabVisible = true;
        cancelSearchSessionForDirectoryChange(Paths.get("__home__"));
        if (searchField != null && !searchField.getText().isEmpty()) {
            setSearchFieldTextSilently("");
            activeSearchQuery = "";
            updateSearchSessionState(SearchSessionState.IDLE);
        }
        refreshHomeSurface();
        applyHomeModeVisibility();
        updateWindowTitle(null);
        updateTopChromeState();
        updateTabStrip();
        setStatus("Home");
    }

    private void hideHomePage() {
        currentTabVisible = true;
        if (!homeActive) {
            updateTabStrip();
            return;
        }
        homeActive = false;
        applyHomeModeVisibility();
        updateWindowTitle(currentDirectory);
        updateTopChromeState();
        updateTabStrip();
        if (currentDirectory != null && statusLabel != null && fileMetadataService != null) {
            statusLabel.setText(fileMetadataService.displayPathForStatus(currentDirectory));
        }
    }

    private void applyHomeModeVisibility() {
        if (homePane != null) {
            homePane.setVisible(homeActive);
            homePane.setManaged(homeActive);
        }
        if (searchField != null) {
            searchField.setDisable(homeActive);
        }
        updateSearchChromeState();
        updateSearchResultSurfaceState();
        setViewMode(viewMode == null ? ViewMode.DETAILS : viewMode);
        updateSidePaneVisibility();
    }

    private void refreshHomeSurface() {
        if (homeCurrentLocationLabel != null) {
            homeCurrentLocationLabel.setText(currentDirectory == null
                    ? "Quick access and recent folders"
                    : "Current folder: " + currentDirectory.toString());
        }
        rebuildHomePinnedRow();
        rebuildRecentLocations();
    }

    private void updateTabStrip() {
        ensureAtLeastOneExplorerTabVisible();
        if (homeTabButton != null) {
            homeTabButton.setVisible(homeTabVisible);
            homeTabButton.setManaged(homeTabVisible);
            setStyleClass(homeTabButton, "selected-tab", homeActive && homeTabVisible);
            if (homeTabVisible) {
                applyExplorerTabChrome(homeTabButton, null);
                homeTabButton.setTooltip(new javafx.scene.control.Tooltip("Open Home"));
            }
        }
        if (currentTabButton != null) {
            currentTabButton.setVisible(currentTabVisible);
            currentTabButton.setManaged(currentTabVisible);
            setStyleClass(currentTabButton, "selected-tab", !homeActive && currentTabVisible);
            currentTabButton.setText(directoryDisplayName(currentDirectory));
            currentTabButton.setDisable(currentDirectory == null);
            if (currentTabVisible) {
                applyExplorerTabChrome(currentTabButton, currentDirectory);
                currentTabButton.setTooltip(new javafx.scene.control.Tooltip(currentDirectory == null ? "Current folder" : currentDirectory.toString()));
            }
        }
        if (closeTabButton != null) {
            applyCloseTabButtonChrome(closeTabButton);
            closeTabButton.setVisible(false);
            closeTabButton.setManaged(false);
            closeTabButton.setDisable(true);
            closeTabButton.setTooltip(new javafx.scene.control.Tooltip("Close current tab"));
        }
        if (newTabButton != null) {
            applyExplorerTabChrome(newTabButton, null);
            newTabButton.setTooltip(new javafx.scene.control.Tooltip("Open current folder in a new window"));
        }
        if (tabStrip != null) {
            tabStrip.requestLayout();
        }
    }

    private String directoryDisplayName(Path directory) {
        if (directory == null) {
            return "This PC";
        }
        try {
            Path fileName = directory.getFileName();
            if (fileName != null && !fileName.toString().isBlank()) {
                return fileName.toString();
            }
        } catch (Exception ignored) {
        }
        String raw = directory.toString();
        return (raw == null || raw.isBlank()) ? "This PC" : raw;
    }

    private void rememberRecentHomeLocation(Path directory) {
        if (directory == null) {
            return;
        }
        Path normalized = directory.normalize();
        recentHomeLocations.removeIf(p -> Objects.equals(p, normalized));
        recentHomeLocations.add(0, normalized);
        while (recentHomeLocations.size() > HOME_RECENT_MAX) {
            recentHomeLocations.remove(recentHomeLocations.size() - 1);
        }
    }

    private void pinCurrentLocationToQuickAccess() {
        pinPathToQuickAccess(currentDirectory);
    }

    private void pinSelectionToQuickAccess() {
        Path primarySelection = getPrimarySelection();
        if (!isDirectoryPath(primarySelection)) {
            return;
        }
        pinPathToQuickAccess(primarySelection);
    }

    private void toggleSelectionQuickAccessPin() {
        Path primarySelection = getPrimarySelection();
        if (!isDirectoryPath(primarySelection)) {
            return;
        }
        if (isPathPinnedToQuickAccess(primarySelection)) {
            unpinPathFromQuickAccess(primarySelection);
        } else {
            pinPathToQuickAccess(primarySelection);
        }
    }

    private void pinPathToQuickAccess(Path path) {
        if (!isDirectoryPath(path)) {
            setStatus("Only folders can be pinned to Quick access.");
            return;
        }
        Path normalized = path.normalize();
        userPinnedHomeLocations.removeIf(existing -> Objects.equals(existing, normalized));
        userPinnedHomeLocations.add(0, normalized);
        while (userPinnedHomeLocations.size() > HOME_PINNED_MAX) {
            userPinnedHomeLocations.remove(userPinnedHomeLocations.size() - 1);
        }
        refreshHomeSurface();
        syncExplorerContextMenuShellState();
        setStatus("Pinned to Quick access: " + directoryDisplayName(normalized));
    }

    private void unpinPathFromQuickAccess(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.normalize();
        boolean removed = userPinnedHomeLocations.removeIf(existing -> existing != null && Objects.equals(existing.normalize(), normalized));
        refreshHomeSurface();
        syncExplorerContextMenuShellState();
        if (removed) {
            setStatus("Unpinned from Quick access: " + directoryDisplayName(normalized));
        } else {
            setStatus("Folder was not pinned: " + directoryDisplayName(normalized));
        }
    }

    private void rebuildHomePinnedRow() {
        if (homePinnedRow == null) {
            return;
        }
        homePinnedRow.getChildren().clear();
        Path home = Paths.get(System.getProperty("user.home"));
        java.util.LinkedHashSet<Path> renderedPaths = new java.util.LinkedHashSet<>();
        addHomePinnedButton("Home", home);
        renderedPaths.add(home.normalize());
        Path desktop = home.resolve("Desktop");
        addHomePinnedButton("Desktop", desktop);
        renderedPaths.add(desktop.normalize());
        Path downloads = home.resolve("Downloads");
        addHomePinnedButton("Downloads", downloads);
        renderedPaths.add(downloads.normalize());
        Path documents = home.resolve("Documents");
        addHomePinnedButton("Documents", documents);
        renderedPaths.add(documents.normalize());
        Path pictures = home.resolve("Pictures");
        addHomePinnedButton("Pictures", pictures);
        renderedPaths.add(pictures.normalize());
        Path oneDrive = home.resolve("OneDrive");
        addHomePinnedButton("OneDrive", oneDrive);
        renderedPaths.add(oneDrive.normalize());
        for (Path pinned : userPinnedHomeLocations) {
            if (pinned == null) {
                continue;
            }
            Path normalizedPinned = pinned.normalize();
            if (renderedPaths.add(normalizedPinned)) {
                addHomePinnedButton(directoryDisplayName(normalizedPinned), normalizedPinned);
            }
        }
    }

    private void addHomePinnedButton(String label, Path path) {
        if (homePinnedRow == null || path == null || !Files.isDirectory(path)) {
            return;
        }
        Button button = new Button(label);
        button.setFocusTraversable(false);
        button.setMnemonicParsing(false);
        button.getStyleClass().add("explorer-home-pinned-button");
        applyHomeLocationButtonGraphic(button, label, path);
        button.setTooltip(new javafx.scene.control.Tooltip(path.toString()));
        button.setOnAction(e -> navigateToFolder(path, true));
        homePinnedRow.getChildren().add(button);
    }

    private void applyHomeLocationButtonGraphic(Button button, String label, Path path) {
        if (button == null) {
            return;
        }
        boolean dark = themeService != null && themeService.isDarkPreferred();
        Image icon = isHomeLocation(path, label)
                ? IconLoader.loadForIdentity("special:home", dark, 16)
                : IconLoader.loadForPath(path, dark, 16);
        if (icon == null) {
            return;
        }
        ImageView graphic = new ImageView(icon);
        graphic.setFitWidth(16.0);
        graphic.setFitHeight(16.0);
        graphic.setPreserveRatio(true);
        graphic.setSmooth(true);
        graphic.setMouseTransparent(true);
        button.setGraphic(graphic);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(8.0);
    }

    private boolean isHomeLocation(Path path, String label) {
        if (label != null && label.trim().equalsIgnoreCase("Home")) {
            return true;
        }
        if (path == null) {
            return false;
        }
        try {
            Path userHome = Paths.get(System.getProperty("user.home")).normalize();
            return path.normalize().equals(userHome);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void rebuildRecentLocations() {
        if (recentFoldersBox == null) {
            return;
        }
        recentFoldersBox.getChildren().clear();
        if (recentHomeLocations.isEmpty()) {
            Label empty = new Label("No recent folders yet.");
            empty.getStyleClass().add("explorer-home-empty");
            recentFoldersBox.getChildren().add(empty);
            return;
        }
        for (Path recent : recentHomeLocations) {
            if (recent == null) {
                continue;
            }
            Button button = new Button(directoryDisplayName(recent));
            button.setFocusTraversable(false);
            button.setMnemonicParsing(false);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.getStyleClass().add("explorer-home-recent-button");
            applyHomeLocationButtonGraphic(button, directoryDisplayName(recent), recent);
            button.setTooltip(new javafx.scene.control.Tooltip(recent.toString()));
            button.setOnAction(e -> navigateToFolder(recent, true));
            VBox.setVgrow(button, Priority.NEVER);
            recentFoldersBox.getChildren().add(button);
        }
    }

    // ---------------------------------------------------------------------
    // Navigation + history
    // ---------------------------------------------------------------------
/**
 * navigateToFolder.
 *
 * @param target TODO
 * @param pushHistory TODO
 */
    private void navigateToFolder(Path target, boolean pushHistory) {
        LogSupport.enter(LOG, "navigateToFolder");
        hideExplorerTransientUi();
        if (target == null) {
            return;
        }
        if (homeActive) {
            homeActive = false;
            applyHomeModeVisibility();
            updateTopChromeState();
        }
        Path normalized = target.normalize();
        if (!Files.isDirectory(normalized)) {
            setStatus("Not a folder: " + normalized);
            return;
        }
        updateWindowTitle(normalized);
        // Phase 4A.4: Snapshot current folder before leaving (for instant Back/Forward paint).
        if (currentDirectory != null && !Objects.equals(currentDirectory.normalize(), normalized)) {
            FolderSnapshotCache.FolderSnapshot snap = captureFolderSnapshot();
            if (snap != null) {
                folderSnapshotCache.put(currentDirectory, snap);
            }
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
        // Phase 4A.4/4A.5: If we have a recent snapshot, paint it immediately, then hydrate (without blanking the table).
        FolderSnapshotCache.FolderSnapshot cached = folderSnapshotCache.get(normalized);
        boolean usedSnapshot = false;
        if (cached != null) {
            applyFolderSnapshot(normalized, cached);
            usedSnapshot = true;
            // Phase 4A.5: best-effort staleness hint (mtime mismatch).
            try {
                if (statusLabel != null && isFolderSnapshotStale(normalized, cached)) {
                    statusLabel.setText("Refreshing (cached view may be stale) …");
                }
            } catch (Exception ignored) {
            }
        }
        loadDirectoryIntoTableAsync(normalized, usedSnapshot);
    }
/**
 * navigateUp.
 *
 */
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
/**
 * navigateBack.
 *
 */
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
/**
 * navigateForward.
 *
 */
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
/**
 * getSelectedItems.
 *
 * @return TODO
 */
    private List<Path> getSelectedItems() {
    LogSupport.enter(LOG, "getSelectedItems");
    if (fileTable == null) {
        return List.of();
    }
    return fileTable.getSelectionModel().getSelectedItems().stream()
            .map(FileItem::path)
            .toList();
}
/**
 * getPrimarySelection.
 *
 * @return TODO
 */
    private Path getPrimarySelection() {
        LogSupport.enter(LOG, "getPrimarySelection");
        Path focused = getFocusedOrSelectedPath();
        if (focused != null) {
            return focused;
        }
        if (fileTable == null || fileTable.getSelectionModel() == null) {
            return null;
        }
        FileItem selItem = fileTable.getSelectionModel().getSelectedItem();
        return (selItem != null) ? selItem.path() : null;
    }
/**
 * selectAll.
 *
 */
    private void selectAll() {
        LogSupport.enter(LOG, "selectAll");
        if (fileTable == null || fileTable.getSelectionModel() == null) {
            return;
        }
        fileTable.getSelectionModel().selectAll();
        refreshActiveSelectionPresentation();
        setStatus("Selected all.");
    }

    private void clearSelection() {
        if (fileTable == null || fileTable.getSelectionModel() == null) {
            return;
        }
        fileTable.getSelectionModel().clearSelection();
        if (fileTable.getFocusModel() != null) {
            fileTable.getFocusModel().focus(-1);
        }
        setExplorerSelectionAnchorPath(null);
        refreshActiveSelectionPresentation();
        setStatus("Selection cleared.");
    }

    private void invertSelection() {
        if (fileTable == null || fileTable.getItems() == null || fileTable.getSelectionModel() == null) {
            return;
        }
        java.util.Set<Integer> selected = new java.util.HashSet<>(fileTable.getSelectionModel().getSelectedIndices());
        fileTable.getSelectionModel().clearSelection();
        for (int i = 0; i < fileTable.getItems().size(); i++) {
            if (!selected.contains(i)) {
                fileTable.getSelectionModel().select(i);
            }
        }
        refreshActiveSelectionPresentation();
        setStatus("Selection inverted.");
    }

    private void refreshActiveSelectionPresentation() {
        if (explorerContextMenuSelectionPresentationHold) {
            maintainExplorerContextMenuSelectionHold();
            scheduleSelectionCommandStateRefresh();
            return;
        }
        if (isExplorerSelectionStabilizationActive()) {
            applyExplorerSelectionPresentationSnapshot(explorerSelectionStabilizationPaths);
            scheduleSelectionCommandStateRefresh();
            return;
        }
        if (viewMode == ViewMode.DETAILS) {
            syncDetailsPresentationSelectedPathsFromTableSelection();
            syncVisibleDetailsHoverRows();
            if (fileTable != null) {
                fileTable.refresh();
            }
        } else {
            refreshVisibleIconSelectionPresentation();
        }
        scheduleSelectionCommandStateRefresh();
    }

    private void copyPrimaryPathToClipboard() {
        Path primary = getPrimarySelection();
        if (primary == null) {
            TreeItem<Path> treeItem = folderTree != null ? folderTree.getSelectionModel().getSelectedItem() : null;
            primary = treeItem != null ? treeItem.getValue() : null;
        }
        if (primary == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(primary.toString());
        Clipboard.getSystemClipboard().setContent(content);
        setStatus("Copied path: " + safeName(primary));
    }
/**
 * copySelection.
 *
 */
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
/**
 * cutSelection.
 *
 */
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
/**
 * pasteIntoCurrentFolder.
 *
 */
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
        executeOnIoExecutor("pasteIntoCurrentFolder", () -> {
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
/**
 * sameSet.
 *
 * @param a TODO
 * @param b TODO
 * @return TODO
 */
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
/**
 * resolvePasteTarget.
 *
 * @param destDir TODO
 * @param fileName TODO
 * @return TODO
 */
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
/**
 * copyRecursively.
 *
 * @param src TODO
 * @param dest TODO
 */
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
/**
 * moveRecursively.
 *
 * @param src TODO
 * @param dest TODO
 */
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
/**
 * moveSelectionToTrash.
 *
 */
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
/**
 * createNewFolder.
 *
 */
private void createNewFolder() {
        LogSupport.enter(LOG, "createNewFolder");
        Path dir = resolveActiveDirectoryForShellCommands();
        if (dir == null) {
            setStatus("No folder available for new folder.");
            return;
        }
        Path target = nextAvailableCreatedPath(dir, "New folder", "New folder (%d)", "");
        if (target == null) {
            setStatus("Failed to resolve a name for the new folder.");
            return;
        }
        try {
            String commandId = null;
            if (context != null && context.commandManager() != null) {
                com.fileexplorer.service.ops.command.CreateDirectoryCommand createDirectoryCommand =
                        new com.fileexplorer.service.ops.command.CreateDirectoryCommand("Create folder " + target.getFileName(), target);
                context.commandManager().execute(createDirectoryCommand);
                commandId = createDirectoryCommand.id();
            } else {
                Files.createDirectory(target);
            }
            queueInlineRenameForCreatedPath(target, commandId);
            refresh();
            setStatus("Created: " + target.getFileName());
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to create folder", ex);
            setStatus("Failed to create folder.");
        }
    }
/**
 * renameSelection.
 *
 */
    private void renameSelection() {
        LogSupport.enter(LOG, "renameSelection");
        if (folderTree != null && folderTree.isFocused()) {
            TreeItem<Path> sel = folderTree.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getValue() != null && sel.getParent() != null) {
                beginTreeInlineRename(sel);
                return;
            }
        }
        Path sel = getFocusedOrSelectedPath();
        if (sel != null) {
            beginTableInlineRename(sel);
            return;
        }
        TreeItem<Path> treeSel = folderTree != null ? folderTree.getSelectionModel().getSelectedItem() : null;
        if (treeSel != null && treeSel.getValue() != null && treeSel.getParent() != null) {
            beginTreeInlineRename(treeSel);
        }
    }

    private void beginTableInlineRename(Path path) {
        if (path == null || fileTable == null) {
            return;
        }
        InlineRenameSession session = consumePendingCreatedInlineRenameSession(path);
        if (session == null) {
            if (activeInlineRenameSession != null
                    && !activeInlineRenameSession.awaitingCompletion
                    && java.util.Objects.equals(path, activeInlineRenameSession.sourcePath)) {
                session = activeInlineRenameSession;
            } else {
                session = captureInlineRenameSession(path,
                        InlineRenameSessionKind.RENAME_EXISTING,
                        resolveInlineRenameSurfaceForCurrentView());
            }
        }
        activeInlineRenameSession = session;
        hideExplorerTransientUi();
        armInlineRenameFocusGuard();
        beginInlineRenameEditTracking(path);
        int idx = findTableIndexForPath(path);
        if (idx >= 0) {
            if (fileTable.getSelectionModel() != null) {
                fileTable.getSelectionModel().clearAndSelect(idx);
            }
            if (fileTable.getFocusModel() != null) {
                fileTable.getFocusModel().focus(idx);
            }
        }
        inlineRenameTreePath = null;
        inlineRenameTablePath = path;
        if (viewMode == ViewMode.DETAILS) {
            fileTable.refresh();
            scheduleExplorerPathVisibilityStabilization(path, true);
            return;
        }
        refreshActiveSelectionPresentation();
        rebuildIconTiles();
        scheduleExplorerPathVisibilityStabilization(path, true);
        Platform.runLater(() -> {
            if (!java.util.Objects.equals(path, inlineRenameTablePath)) {
                return;
            }
            rebuildIconTiles();
            scheduleExplorerPathVisibilityStabilization(path, true);
        });
    }

    private void beginTreeInlineRename(TreeItem<Path> treeItem) {
        if (treeItem == null || treeItem.getValue() == null || treeItem.getParent() == null || folderTree == null) {
            return;
        }
        if (activeInlineRenameSession == null
                || activeInlineRenameSession.awaitingCompletion
                || !java.util.Objects.equals(treeItem.getValue(), activeInlineRenameSession.sourcePath)
                || activeInlineRenameSession.surface != InlineRenameSurface.TREE) {
            activeInlineRenameSession = captureInlineRenameSession(treeItem.getValue(),
                    InlineRenameSessionKind.RENAME_EXISTING,
                    InlineRenameSurface.TREE);
        }
        inlineRenameTablePath = null;
        inlineRenameTreePath = treeItem.getValue();
        armInlineRenameFocusGuard();
        folderTree.requestFocus();
        folderTree.edit(treeItem);
    }

    private void clearInlineRenameTargets() {
        inlineRenameTablePath = null;
        inlineRenameTreePath = null;
        clearInlineRenameEditTracking();
        if (folderTree != null) {
            suppressTreeInlineRenameCancelEvent = true;
            try {
                folderTree.edit(null);
            } finally {
                suppressTreeInlineRenameCancelEvent = false;
            }
            folderTree.refresh();
        }
        if (fileTable != null) {
            fileTable.refresh();
        }
        if (!homeActive && isIconMode(viewMode)) {
            rebuildIconTiles();
            Platform.runLater(this::refreshVisibleIconSelectionPresentation);
        }
    }

    private void commitInlineRename(Path source, String requestedName) {
        InlineRenameSession session = activeInlineRenameSession;
        if (session == null || !java.util.Objects.equals(source, session.sourcePath)) {
            session = captureInlineRenameSession(source,
                    InlineRenameSessionKind.RENAME_EXISTING,
                    resolveInlineRenameSurfaceForCurrentView());
            activeInlineRenameSession = session;
        }
        if (source == null) {
            clearPendingInlineRenameDraft();
            clearInlineRenameTargets();
            activeInlineRenameSession = null;
            return;
        }
        String currentName = displayNameForTable(source);
        String requested = requestedName == null ? "" : requestedName;
        session.requestedName = requested;
        if (isBlankInlineRename(requested)) {
            setStatus("Rename failed: name cannot be blank.");
            session.awaitingCompletion = false;
            retryInlineRename(source, requested, true);
            return;
        }
        String newName = normalizeInlineRenameRequestedName(source, requested);
        if (Objects.equals(newName, currentName)) {
            clearPendingInlineRenameDraft();
            clearInlineRenameTargets();
            if (session.kind == InlineRenameSessionKind.CREATE_NEW) {
                finalizeInlineRenameCommitSuccess(session, source);
            } else {
                activeInlineRenameSession = null;
                restoreInlineRenameSessionSelectionAndFocus(session);
                armInlineRenameFocusGuard();
            }
            return;
        }
        Path parent = source.getParent();
        if (parent == null) {
            clearPendingInlineRenameDraft();
            clearInlineRenameTargets();
            activeInlineRenameSession = null;
            restoreInlineRenameSessionSelectionAndFocus(session);
            armInlineRenameFocusGuard();
            return;
        }
        if (".".equals(newName) || "..".equals(newName)) {
            setStatus("Rename failed: invalid name.");
            session.awaitingCompletion = false;
            retryInlineRename(source, requested, true);
            return;
        }
        if (containsIllegalInlineRenameCharacters(newName) || endsWithInvalidInlineRenameSuffix(newName) || isReservedInlineRenameName(newName)) {
            setStatus("Rename failed: invalid name.");
            session.awaitingCompletion = false;
            retryInlineRename(source, requested, true);
            return;
        }
        Path dest = parent.resolve(newName);
        if (!java.util.Objects.equals(source, dest) && Files.exists(dest)) {
            setStatus("Rename failed: an item with that name already exists.");
            session.awaitingCompletion = false;
            retryInlineRename(source, requested, true);
            return;
        }
        session.pendingResultPath = dest;
        session.awaitingCompletion = true;
        captureInlineRenameCommitViewport(session);
        pendingInlineRenameSelectionPath = dest;
        pendingInlineRenameSelectionIndex = findTableIndexForPath(source);
        clearPendingInlineRenameDraft();
        clearInlineRenameTargets();
        armInlineRenameFocusGuard();
        try {
            if (context != null && context.commandManager() != null) {
                String label = "Rename " + displayNameForTable(source) + " → " + newName;
                context.commandManager().execute(new com.fileexplorer.service.ops.command.RenamePathCommand(label, source, dest));
            } else {
                Files.move(source, dest);
            }
            refresh();
            setStatus("Renamed.");
        } catch (Exception ex) {
            pendingInlineRenameSelectionPath = null;
            pendingInlineRenameSelectionIndex = -1;
            session.awaitingCompletion = false;
            session.pendingResultPath = null;
            setStatus("Rename failed.");
            LOG.log(Level.FINE, "Inline rename failed", ex);
            retryInlineRename(source, requested, true);
        }
    }

    private void commitTreeInlineRename(Path source, String requestedName) {
        if (source == null) {
            clearInlineRenameTargets();
            return;
        }
        if (inlineRenameTreePath == null || !source.equals(inlineRenameTreePath)) {
            return;
        }
        commitInlineRename(source, requestedName);
    }
/**
 * openPropertiesForPath.
 *
 */
    private void openPropertiesForPath(Path sel) {
        LogSupport.enter(LOG, "openPropertiesForPath");
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
        com.fileexplorer.util.DialogTheme.apply(a, null);
        com.fileexplorer.util.DialogTheme.apply(a, null);
        a.showAndWait();
    }
/**
 * openPropertiesForSelection.
 *
 */
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
        com.fileexplorer.util.DialogTheme.apply(a, null);
        com.fileexplorer.util.DialogTheme.apply(a, null);
        a.showAndWait();
    }
    // ---------------------------------------------------------------------
    // Pane toggles
    // ---------------------------------------------------------------------
/**
 * togglePreviewPane.
 *
 */
    private void togglePreviewPane() {
        LogSupport.enter(LOG, "togglePreviewPane");
        boolean show = inspectorMode != InspectorMode.PREVIEW;
        setPreviewPaneVisible(show);
    }
/**
 * toggleDetailsPane.
 *
 */
    private void toggleDetailsPane() {
        LogSupport.enter(LOG, "toggleDetailsPane");
        boolean show = inspectorMode != InspectorMode.DETAILS;
        setDetailsPaneVisible(show);
    }
    // ---------------------------------------------------------------------
    // Focus / window helpers
    // ---------------------------------------------------------------------
/**
 * focusSearch.
 *
 */
    private void focusSearch() {
        LogSupport.enter(LOG, "focusSearch");
        if (searchField != null && !searchField.isDisabled()) {
            searchField.requestFocus();
            searchField.selectAll();
            updateSearchChromeState();
        }
    }

/**
 * onClearSearchAction.
 *
 */
    @FXML
    private void onClearSearchAction() {
        if (searchField == null) {
            return;
        }
        setSearchFieldTextSilently("");
        endSearchSession(true, false);
        if (!searchField.isDisabled()) {
            searchField.requestFocus();
        }
        updateSearchChromeState();
    }

    private void updateSearchChromeState() {
        boolean active = isSearchQueryActive();
        setStyleClass(searchShell, "search-active", active);
        setStyleClass(searchShell, "search-focused", searchField != null && searchField.isFocused());
        setStyleClass(searchShell, "search-typing", searchSessionState == SearchSessionState.TYPING);
        setStyleClass(searchShell, "search-searching", searchSessionState == SearchSessionState.SEARCHING);
        setStyleClass(searchShell, "search-results", searchSessionState == SearchSessionState.RESULTS);
        setStyleClass(searchShell, "search-no-results", searchSessionState == SearchSessionState.NO_RESULTS);
        if (searchClearButton != null) {
            searchClearButton.setVisible(active && !homeActive);
            searchClearButton.setManaged(active && !homeActive);
            searchClearButton.setDisable(homeActive);
            Tooltip tooltip = searchClearButton.getTooltip();
            if (tooltip != null) {
                tooltip.setText(active ? "Clear search" : "Search is empty");
            }
        }
    }

    private boolean isSearchQueryActive() {
        return searchField != null && searchField.getText() != null && !searchField.getText().isBlank();
    }

    private void focusPrimaryFileSurface() {
        if (homeActive) {
            if (folderTree != null) {
                folderTree.requestFocus();
            }
            return;
        }
        try {
            if (viewMode == ViewMode.DETAILS && fileTable != null) {
                fileTable.requestFocus();
                return;
            }
            if (isUsingVirtualIconGridForCurrentView() && virtualIconGridView != null) {
                virtualIconGridView.requestFocus();
                return;
            }
            if (isUsingVirtualIconListForCurrentView() && virtualIconListView != null) {
                virtualIconListView.requestFocus();
                return;
            }
            if (iconScroll != null && iconScroll.isVisible()) {
                iconScroll.requestFocus();
                return;
            }
            if (fileTable != null) {
                fileTable.requestFocus();
            }
        } catch (Exception ignored) {
        }
    }
/**
 * focusAddressBar.
 *
 */
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
/**
 * cyclePanesFocus.
 *
 */
    private void cyclePanesFocus() {
        LogSupport.enter(LOG, "cyclePanesFocus");
        List<Node> panes = new ArrayList<>();
        if (searchField != null) panes.add(searchField);
        if (folderTree != null && folderTree.isManaged()) panes.add(folderTree);
        if (fileTable != null) panes.add(fileTable);
        if (previewBox != null && previewBox.isManaged()) panes.add(previewBox);
        if (detailsBox != null && detailsBox.isManaged()) panes.add(detailsBox);
        if (operationsBox != null && operationsBox.isManaged()) panes.add(operationsBox);
        if (panes.isEmpty()) {
            return;
        }
        focusCycleIndex = (focusCycleIndex + 1) % panes.size();
        panes.get(focusCycleIndex).requestFocus();
    }
/**
 * scrollToTop.
 *
 */
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
/**
 * scrollToBottom.
 *
 */
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
/**
 * refresh.
 *
 */
    private void refresh() {
        LogSupport.enter(LOG, "refresh");
        // Preserve current selection (table) if possible.
        try {
            if (pendingInlineRenameSelectionPath != null) {
                pendingReselectPath = pendingInlineRenameSelectionPath;
                pendingReselectIndex = pendingInlineRenameSelectionIndex;
                pendingRestoreSelection = true;
            } else if (fileTable != null) {
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
            pendingReselectPreferIndexOnMissing = false;
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
/**
 * toggleFullScreen.
 *
 */
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
/**
 * closeCurrentWindow.
 *
 */
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
/**
 * openNewWindow.
 *
 */
    private void openNewWindow() {
        LogSupport.enter(LOG, "openNewWindow");
        openNewWindow(currentDirectory);
    }
/**
 * openNewWindow.
 *
 * @param initialFolder TODO
 */
    private void openNewWindow(Path initialFolder) {
        LogSupport.enter(LOG, "openNewWindow");
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                MainApp.configureExplorerStage(stage, Objects.requireNonNullElseGet(initialFolder, () -> Paths.get(System.getProperty("user.home"))), themeService.isDarkPreferred(), "user.main-controller.new-window");
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
/**
 * expandAllFoldersInNavigationPane.
 *
 */
private void expandAllFoldersInNavigationPane() {
    final TreeItem<Path> root = folderTree != null ? folderTree.getRoot() : null;
    if (root == null) {
        setStatus("Navigation tree is not available.");
        return;
    }
    setStatus("Expanding navigation tree...");
    com.fileexplorer.ui.tree.TreeViewSupport.expandAllAsync(root, NAV_EXPAND_MAX_DEPTH);
}
/**
 * collapseAllFoldersInNavigationPane.
 *
 */
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
/**
 * expandNavigationTreeLimited.
 *
 * @param root TODO
 * @param maxDepth TODO
 * @param maxNodes TODO
 */
private void expandNavigationTreeLimited(TreeItem<Path> root, int maxDepth, int maxNodes) {
    // All TreeItem interaction must occur on the JavaFX Application Thread.
    final java.util.ArrayDeque<NavExpandNode> queue = new java.util.ArrayDeque<>();
    queue.add(new NavExpandNode(root, 0));
    final java.util.concurrent.atomic.AtomicInteger expanded = new java.util.concurrent.atomic.AtomicInteger(0);
    final Runnable pump = new Runnable() {
        @Override
/**
 * run.
 *
 */
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
/**
 * expandAndSelectFolder.
 *
 * @param target TODO
 */
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
/**
 * findChildByName.
 *
 * @param parent TODO
 * @param name TODO
 * @return TODO
 */
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
/**
 * syncThemeToggleText.
 *
 */
    private void syncThemeToggleText() {
        LogSupport.enter(LOG, "syncThemeToggleText");
        themeToggle.setText(themeToggle.isSelected() ? "Dark" : "Light");
    }
/**
 * safeName.
 *
 * @param p TODO
 * @return TODO
 */
    private static String safeName(java.nio.file.Path p) {
        if (p == null) return "";
        java.nio.file.Path fn = p.getFileName();
        return fn != null ? fn.toString() : p.toString();
    }
/**
 * setStatus.
 *
 * @param text TODO
 */
    private void setStatus(String text) {
        LogSupport.enter(LOG, "setStatus");
        if (statusLabel != null) {
            statusLabel.setText(text == null ? "" : text);
        }
    }
/**
 * wireViewMenuHandlers.
 *
 */
    private void wireMenuItemContentAction(CustomMenuItem item, Runnable action, boolean hideParentPopup) {
        LogSupport.enter(LOG, "wireMenuItemContentAction");
        if (item == null || action == null) {
            return;
        }
        item.setOnAction(evt -> {
            action.run();
            if (hideParentPopup && item.getParentPopup() != null) {
                item.getParentPopup().hide();
            }
        });
        Node content = item.getContent();
        if (content != null) {
            content.setOnMouseReleased(evt -> {
                if (!evt.isStillSincePress()) {
                    return;
                }
                Object target = evt.getTarget();
                if (target instanceof Node targetNode) {
                    Node n = targetNode;
                    while (n != null) {
                        if (n instanceof RadioButton || n instanceof CheckBox) {
                            return;
                        }
                        n = n.getParent();
                    }
                }
                action.run();
                if (hideParentPopup && item.getParentPopup() != null) {
                    item.getParentPopup().hide();
                }
                evt.consume();
            });
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
        wireMenuItemContentAction(viewContentItem, () -> setViewMode(ViewMode.CONTENT), true);
        // Wire pane radios (independent "dot toggles" by design).
        if (detailsPaneMenuItem != null) {
            detailsPaneMenuItem.setOnAction(this::onDetailsPaneRadioToggle);
        }
        if (previewPaneMenuItem != null) {
            previewPaneMenuItem.setOnAction(this::onPreviewPaneRadioToggle);
        }
        wireMenuItemContentAction(detailsPaneRowItem, () -> {
            if (detailsPaneMenuItem != null) {
                detailsPaneMenuItem.setSelected(!detailsPaneMenuItem.isSelected());
                onDetailsPaneRadioToggle(new ActionEvent(detailsPaneRowItem, detailsPaneRowItem));
            }
        }, false);
        wireMenuItemContentAction(previewPaneRowItem, () -> {
            if (previewPaneMenuItem != null) {
                previewPaneMenuItem.setSelected(!previewPaneMenuItem.isSelected());
                onPreviewPaneRadioToggle(new ActionEvent(previewPaneRowItem, previewPaneRowItem));
            }
        }, false);
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
/**
 * wireViewModeRadio.
 *
 * @param item TODO
 * @param mode TODO
 */
    private void wireViewModeRadio(RadioButton item, ViewMode mode) {
        LogSupport.enter(LOG, "wireViewModeRadio");
        if (item == null || mode == null) {
            return;
        }
        item.setToggleGroup(viewModeToggleGroup);
        item.setUserData(mode.name());
        item.setOnAction(this::onViewModeRadio);
    }
/**
 * configureStatusBar.
 *
 */
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
        if (statusDetailsButton != null) {
            statusDetailsButton.setFocusTraversable(false);
        }
        if (statusLargeIconsButton != null) {
            statusLargeIconsButton.setFocusTraversable(false);
        }
        syncStatusViewToggleSelection();
    }
/**
 * ensureStartupWindowSize.
 *
 * @param scene TODO
 */
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
/**
 * buildUiFontFamilyCss.
 *
 * @param scene TODO
 * @return TODO
 */
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
/**
 * resolveSystemFontFamily.
 *
 * @return TODO
 */
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
/**
 * escapeCssFontFamily.
 *
 * @param family TODO
 * @return TODO
 */
    private String escapeCssFontFamily(String family) {
        LogSupport.enter(LOG, "escapeCssFontFamily");
        if (family == null) {
            return "";
        }
        return family.replace("'", "\\'");
    }
/**
 * resolveTreeTextFamily.
 *
 * @return TODO
 */
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
/**
 * enforceStartupFixedCellSizes.
 *
 */
private void enforceStartupFixedCellSizes() {
    // TreeView: MUST be > 0 to avoid VirtualFlow addTrailingCells() runaway when a cell reports 0 height.
    if (folderTree != null) {
        double v = syspropDouble("fileexplorer.ui.tree.fixedCellSize", FOLDER_TREE_ROW_HEIGHT_PX);
        v = clamp(v, 16.0, 96.0);
        // TreeView uses <=0 to mean "variable" sizing; enforce a positive value.
        if (!(v > 0.0)) {
            v = FOLDER_TREE_ROW_HEIGHT_PX;
        }
        folderTree.setFixedCellSize(Math.max(FOLDER_TREE_ROW_HEIGHT_PX, v));
        applyFolderTreeMetricsHard();
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
/**
 * enforceVirtualizedPrefSize.
 *
 * @param region TODO
 * @param label TODO
 * @param prefWidth TODO
 * @param prefHeight TODO
 */
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
    private void applyFolderTreeMetricsHard() {
        if (folderTree == null) {
            return;
        }
        folderTree.setFixedCellSize(FOLDER_TREE_ROW_HEIGHT_PX);
        String style = folderTree.getStyle();
        if (style == null) {
            style = "";
        }
        style = style.replaceAll("(?i)-fx-fixed-cell-size\\s*:\\s*[^;]+;?", "").trim();
        style = style.replaceAll("(?i)-fx-cell-size\\s*:\\s*[^;]+;?", "").trim();
        style = style.replaceAll("(?i)-fx-padding\\s*:\\s*[^;]+;?", "").trim();
        if (!style.isEmpty() && !style.endsWith(";")) {
            style = style + ";";
        }
        style = style + " -fx-fixed-cell-size: " + FOLDER_TREE_ROW_HEIGHT_PX + "px;"
                + " -fx-cell-size: " + FOLDER_TREE_ROW_HEIGHT_PX + "px;"
                + " -fx-padding: 0;";
        folderTree.setStyle(style);

        Runnable applyCells = () -> {
            for (Node node : folderTree.lookupAll(".tree-cell")) {
                if (node instanceof TreeCell<?> cell) {
                    cell.setMinHeight(FOLDER_TREE_ROW_HEIGHT_PX);
                    cell.setPrefHeight(FOLDER_TREE_ROW_HEIGHT_PX);
                    cell.setMaxHeight(FOLDER_TREE_ROW_HEIGHT_PX);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    cell.setPadding(FOLDER_TREE_CELL_PADDING);
                    cell.setContentDisplay(ContentDisplay.LEFT);
                    cell.setGraphicTextGap(8.0);
                    String cellStyle = cell.getStyle();
                    if (cellStyle == null) {
                        cellStyle = "";
                    }
                    if (!cellStyle.isBlank() && !cellStyle.endsWith(";")) {
                        cellStyle = cellStyle + ";";
                    }
                    cell.setStyle(cellStyle
                            + " -fx-padding: 1 8 1 6;"
                            + " -fx-alignment: CENTER-LEFT;"
                            + " -fx-cell-size: " + FOLDER_TREE_ROW_HEIGHT_PX + "px;"
                            + " -fx-min-height: " + FOLDER_TREE_ROW_HEIGHT_PX + "px;"
                            + " -fx-pref-height: " + FOLDER_TREE_ROW_HEIGHT_PX + "px;"
                            + " -fx-max-height: " + FOLDER_TREE_ROW_HEIGHT_PX + "px;");
                }
            }
        };
        folderTree.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(applyCells));
        Platform.runLater(applyCells);
    }

/**
 * syspropDouble.
 *
 * @param key TODO
 * @param def TODO
 * @return TODO
 */
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
/**
 * syspropBoolean.
 *
 * @param key TODO
 * @param def TODO
 * @return TODO
 */
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
        if (folderTree.getFixedCellSize() < FOLDER_TREE_ROW_HEIGHT_PX) {
            folderTree.setFixedCellSize(FOLDER_TREE_ROW_HEIGHT_PX);
        }
        applyFolderTreeMetricsHard();
        // Ensure explorer_tree.css is loaded once the TreeView is attached to a Scene.
        folderTree.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            try {
                var baseUrl = MainController.class.getResource("/css/explorer_tree.css");
                if (baseUrl != null) {
                    String css = baseUrl.toExternalForm();
                    if (!newScene.getStylesheets().contains(css)) {
                        newScene.getStylesheets().add(css);
                    }
                }
                var parityUrl = MainController.class.getResource("/com/fileexplorer/ui/css/navigation-pane-parity.css");
                if (parityUrl != null) {
                    String parityCss = parityUrl.toExternalForm();
                    if (newScene.getStylesheets().contains(parityCss)) {
                        newScene.getStylesheets().remove(parityCss);
                    }
                    newScene.getStylesheets().add(parityCss);
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
    
// Phase 4A.4: FolderSnapshotCache helpers (instant paint for Back/Forward).
private boolean isFolderSnapshotStale(Path dir, FolderSnapshotCache.FolderSnapshot snapshot) {
    if (dir == null || snapshot == null) return false;
    long snapMtime = snapshot.dirLastModifiedMillis();
    if (snapMtime < 0L) return false;
    try {
        long now = java.nio.file.Files.getLastModifiedTime(dir).toMillis();
        return now != snapMtime;
    } catch (Exception ignored) {
        return false;
    }
}
private FolderSnapshotCache.FolderSnapshot captureFolderSnapshot() {
    try {
        if (fileTable == null) return null;
        if (directoryLoading) return null;
        if (currentDirectory == null) return null;
        ObservableList<FileItem> items = fileTable.getItems();
        if (items == null || items.isEmpty()) return FolderSnapshotCache.snapshotOf(List.of(), null, 0);
        FileItem selected = null;
        try {
            selected = fileTable.getSelectionModel() != null ? fileTable.getSelectionModel().getSelectedItem() : null;
        } catch (Exception ignored) {
        }
        Path selectionPath = (selected != null) ? selected.path() : null;
        int anchorIndex = 0;
        try {
            anchorIndex = fileTable.getSelectionModel() != null ? fileTable.getSelectionModel().getSelectedIndex() : 0;
        } catch (Exception ignored) {
            anchorIndex = 0;
        }
        long mtime = -1L;
        try {
            mtime = java.nio.file.Files.getLastModifiedTime(currentDirectory).toMillis();
        } catch (Exception ignored2) {
        }
        return FolderSnapshotCache.snapshotOf(items, selectionPath, anchorIndex, mtime);
    } catch (Exception ignored) {
        return null;
    }
}
private void applyFolderSnapshot(Path dir, FolderSnapshotCache.FolderSnapshot snapshot) {
    if (snapshot == null || fileTable == null) return;
    setVisibleDirectoryScope(dir);
    // Phase 4A.6: track snapshot for in-place hydration diffing.
    activeHydrationSnapshot = snapshot;
    Runnable r = () -> {
        try {
            ObservableList<FileItem> tableItems = fileTable.getItems();
            if (tableItems == null) {
                fileTable.setItems(FXCollections.observableArrayList(snapshot.items()));
            } else {
                tableItems.setAll(snapshot.items());
            }
            // Restore selection (prefer explicit selection path; fallback to anchor index).
            int idx = -1;
            Path wanted = snapshot.primarySelection();
            if (wanted != null) {
                Path normalizedWanted = wanted.normalize();
                List<FileItem> list = fileTable.getItems();
                for (int i = 0; i < list.size(); i++) {
                    FileItem it = list.get(i);
                    if (it != null && it.path() != null && Objects.equals(it.path().normalize(), normalizedWanted)) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx < 0) idx = Math.min(snapshot.anchorIndex(), Math.max(0, fileTable.getItems().size() - 1));
            try {
                if (idx >= 0 && fileTable.getSelectionModel() != null) {
                    fileTable.getSelectionModel().clearAndSelect(idx);
                }
            } catch (Exception ignored) {
            }
            try {
                if (idx >= 0) fileTable.scrollTo(idx);
            } catch (Exception ignored) {
            }
            try {
                fileTable.refresh();
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    };
    if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
}
// Phase 4A.6: Apply progressive directory-load batches over a cached snapshot without "jump".
// We patch the backing list in-place at the appropriate offset, preserving scroll/selection,
// and avoiding duplicates when the snapshot already contained many/all rows.
private void applyHydrationBatchDiff(int offset, List<FileItem> batch, boolean isFirstRealBatch,
                                     FolderSnapshotCache.FolderSnapshot snapshot) {
    if (batch == null || batch.isEmpty()) return;
    if (offset < 0) offset = 0;
    if (offset > tableItems.size()) {
        // Should not happen for ordered progressive loads; clamp defensively.
        offset = tableItems.size();
    }
    // Replace items in-place for this batch.
    for (int i = 0; i < batch.size(); i++) {
        FileItem incoming = batch.get(i);
        int idx = offset + i;
        if (idx < tableItems.size()) {
            FileItem prev = tableItems.get(idx);
            // Remove old index mapping (if any).
            try {
                if (prev != null && prev.path() != null) {
                    Integer mapped = tableIndexByPath.get(prev.path());
                    if (mapped != null && mapped == idx) {
                        tableIndexByPath.remove(prev.path());
                    }
                }
            } catch (Exception ignored) {
            }
            tableItems.set(idx, incoming);
        } else {
            tableItems.add(incoming);
        }
        if (incoming != null && incoming.path() != null) {
            tableIndexByPath.put(incoming.path(), idx);
        }
    }
    updateStatusCounts();
    if (isFirstRealBatch && fileTable != null && snapshot != null) {
        // Restore selection/scroll using stable key (path) instead of raw index.
        try {
            Path sel = snapshot.primarySelection();
            if (sel != null) {
                Integer selIndex = tableIndexByPath.get(sel);
                if (selIndex == null) {
                    selIndex = findIndexByPath(sel);
                }
                if (selIndex != null && selIndex >= 0) {
                    fileTable.getSelectionModel().clearSelection();
                    fileTable.getSelectionModel().select(selIndex);
                    fileTable.scrollTo(Math.max(0, selIndex - 2));
                    return;
                }
            }
            // Fallback: keep user near where they were (anchor index).
            int anchor = Math.max(0, Math.min(snapshot.anchorIndex(), tableItems.size() - 1));
            fileTable.scrollTo(Math.max(0, anchor - 2));
        } catch (Exception ignored) {
        }
    }
}
private Integer findIndexByPath(Path p) {
    if (p == null) return null;
    for (int i = 0; i < tableItems.size(); i++) {
        FileItem fi = tableItems.get(i);
        if (fi != null && fi.path() != null && fi.path().equals(p)) {
            return i;
        }
    }
    return null;
}
public void dispose() {
        controllerDisposed.set(true);
        try {
            if (visibleMetadataDebounce != null) {
                visibleMetadataDebounce.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            searchDebounce.stop();
        } catch (Exception ignored) {
        }
        try {
            metadataFlushDebounce.stop();
        } catch (Exception ignored) {
        }
        try {
            tableRefreshDebounce.stop();
        } catch (Exception ignored) {
        }
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
        // Close budgeted metadata service.
        try {
            if (metadataBudgetService != null) {
                metadataBudgetService.close();
            }
        } catch (Exception ignored) {
        }
        // Shut down background services/executors to avoid hanging process on exit.
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
        try {
            if (progressPaneController != null) {
                progressPaneController.dispose();
            }
        } catch (Exception ignored) {
        }
    }
}
