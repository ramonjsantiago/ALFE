package com.fileexplorer.app;

import com.fileexplorer.controller.MainController;
import com.fileexplorer.controller.ProgressPaneController;
import com.fileexplorer.ui.zoom.ZoomRoot;
import com.fileexplorer.service.theme.ThemeService;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import com.fileexplorer.util.LogSupport;
import com.fileexplorer.util.StartupTrace;
import javafx.geometry.Insets;
import java.util.concurrent.atomic.AtomicReference;
import com.fileexplorer.lifecycle.Lifecycle;
import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.filesystem.FileMetadataService;
import com.fileexplorer.service.filesystem.TreeBuildService;
import com.fileexplorer.service.icon.IconCacheService;
import com.fileexplorer.service.diag.CrashReportService;
import com.fileexplorer.service.diag.DiagnosticsBundleService;
import com.fileexplorer.service.diag.StartupSelfCheckService;

import java.nio.file.Files;
import java.time.Instant;

/**
 * Application bootstrap.
 * <p>
 * Changes in this version: - Larger default UI font (especially on 4K) while
 * remaining conservative to avoid clipping. - Keeps 75% visual-bounds startup
 * sizing. - Still supports override: -Dfileexplorer.uiFontPx=22
 */
public final class MainApp extends Application {

    private static final Logger LOG = Logger.getLogger("com.fileexplorer.resources");
    

    private Stage primaryStage;
private static final Set<String> LOGGED_RESOURCES = ConcurrentHashMap.newKeySet();

    private static final Set<String> SCANNED_STYLESHEETS = ConcurrentHashMap.newKeySet();
    private static volatile boolean RESOURCE_LOGGER_CONFIGURED;
    private static final String PROP_UI_FONT_PX = "main.uiFontPx";
    private static final String PROP_UI_FONT_FAMILY = "main.uiFontFamily";

    // Diagnostics (printed once per process).
    // Disable via: -Dfileexplorer.debug.printJvmArgs=false
    private static final String SYS_PRINT_JVM_ARGS = "fileexplorer.debug.printJvmArgs";
    private static volatile boolean JVM_ARGS_PRINTED;


    // For diagnostics: resource paths of fonts successfully loaded at startup.
    private static final List<String> loadedFontResourcePaths = new ArrayList<>();

    /**
     * Optional override: -Dfileexplorer.uiFontPx=22
     */
    private static final String SYS_UI_FONT_PX = "fileexplorer.uiFontPx";

    private static final String UI_FONT_FAMILY = "Segoe UI Variable";
    private static final String UI_FONT_FAMILY_FALLBACK = "Segoe UI";
    private static final String SYSTEM_FONT_FAMILY_KEYWORD = "System";

    // Startup window sizing (per request)
    private static final double STARTUP_WIDTH_FRACTION = 0.75;
    private static final double STARTUP_HEIGHT_FRACTION = 0.75;
    private static final double MIN_STARTUP_WIDTH = 1200.0;
    private static final double MIN_STARTUP_HEIGHT = 800.0;

	    // Default window sizing (used once the scene is fully built)
	    private static final double DEFAULT_WIDTH = 1200.0;
	    private static final double DEFAULT_HEIGHT = 800.0;
	    private static final double DEFAULT_MIN_WIDTH = 900.0;
	    private static final double DEFAULT_MIN_HEIGHT = 600.0;

    // Font sizing: larger by default, but still capped to avoid control clipping.
    private static final double MIN_FONT_PX = 18.0;
    private static final double MAX_FONT_PX = 26.0;

    private static volatile boolean fontsBootstrapped;
    private static volatile String resolvedUiFamily = SYSTEM_FONT_FAMILY_KEYWORD;
    private static volatile String resolvedSystemFamily = SYSTEM_FONT_FAMILY_KEYWORD;

/**
 * isSafeMode.
 *
 * @return TODO
 */
    private static boolean isSafeMode() {
        return Boolean.getBoolean("fileexplorer.safeMode");
    }

/**
 * isResourceAuditEnabled.
 *
 * @return TODO
 */
    private static boolean isResourceAuditEnabled() {
        // Safe mode forces resource audit off to keep startup as minimal as possible.
        if (isSafeMode()) {
            return false;
        }
        return Boolean.getBoolean("fileexplorer.resourceAudit");
    }


/**
 * printJvmDiagnosticsOnce.
 *
 * @param phase TODO
 */
    private static void printJvmDiagnosticsOnce(String phase) {
        if (JVM_ARGS_PRINTED) {
            return;
        }
        if (!Boolean.parseBoolean(System.getProperty(SYS_PRINT_JVM_ARGS, "true"))) {
            return;
        }
        JVM_ARGS_PRINTED = true;

        try {
            RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
            long max = Runtime.getRuntime().maxMemory();
            long total = Runtime.getRuntime().totalMemory();
            long free = Runtime.getRuntime().freeMemory();
            String ln = System.lineSeparator();

            StringBuilder sb = new StringBuilder(2048);
            sb.append("=== FileExplorer JVM diagnostics [").append(phase).append("] ===").append(ln);
            sb.append("java.version=").append(System.getProperty("java.version")).append(ln);
            sb.append("java.runtime.name=").append(System.getProperty("java.runtime.name")).append(ln);
            sb.append("java.vm.name=").append(System.getProperty("java.vm.name")).append(ln);
            sb.append("java.vendor=").append(System.getProperty("java.vendor")).append(ln);
            sb.append("os=").append(System.getProperty("os.name")).append(" ")
              .append(System.getProperty("os.version")).append(" ")
              .append(System.getProperty("os.arch")).append(ln);
            sb.append("user.dir=").append(System.getProperty("user.dir")).append(ln);
            sb.append("user.home=").append(System.getProperty("user.home")).append(ln);
            sb.append("sun.java.command=").append(System.getProperty("sun.java.command")).append(ln);
            sb.append("inputArguments=").append(mx.getInputArguments()).append(ln);
            sb.append("memoryMB max=").append(max / 1024 / 1024)
              .append(" total=").append(total / 1024 / 1024)
              .append(" free=").append(free / 1024 / 1024).append(ln);
            sb.append("JAVA_TOOL_OPTIONS=").append(System.getenv("JAVA_TOOL_OPTIONS")).append(ln);
            sb.append("_JAVA_OPTIONS=").append(System.getenv("_JAVA_OPTIONS")).append(ln);
            sb.append("MAVEN_OPTS=").append(System.getenv("MAVEN_OPTS")).append(ln);
            sb.append("fileexplorer.log.enter=").append(System.getProperty("fileexplorer.log.enter")).append(ln);
            sb.append("fileexplorer.resourceAudit=").append(System.getProperty("fileexplorer.resourceAudit")).append(ln);
            sb.append("fileexplorer.safeMode=").append(System.getProperty("fileexplorer.safeMode")).append(ln);
            sb.append("fileexplorer.safeMode.maxDirEntries=").append(System.getProperty("fileexplorer.safeMode.maxDirEntries")).append(ln);
            sb.append("fileexplorer.safeMode.maxDirEntries.hardMax=").append(System.getProperty("fileexplorer.safeMode.maxDirEntries.hardMax")).append(ln);
            sb.append("SAFE_MODE=").append(isSafeMode() ? "ENABLED" : "DISABLED").append(ln);
            sb.append("fileexplorer.ui.loadBundledFonts=").append(System.getProperty("fileexplorer.ui.loadBundledFonts")).append(ln);
            sb.append("fileexplorer.ui.enforceMinMetrics=").append(System.getProperty("fileexplorer.ui.enforceMinMetrics")).append(ln);
            sb.append("fileexplorer.ui.tree.fixedCellSize=").append(System.getProperty("fileexplorer.ui.tree.fixedCellSize")).append(ln);
            sb.append("fileexplorer.ui.table.fixedCellSize=").append(System.getProperty("fileexplorer.ui.table.fixedCellSize")).append(ln);
            sb.append("fileexplorer.maxTreeChildDirs=").append(System.getProperty("fileexplorer.maxTreeChildDirs")).append(ln);
            sb.append("fileexplorer.maxDirEntries=").append(System.getProperty("fileexplorer.maxDirEntries")).append(ln);
            sb.append("java.util.logging.config.file=")
              .append(System.getProperty("java.util.logging.config.file")).append(ln);

            System.err.print(sb.toString());
        } catch (Exception ex) {
            System.err.println("JVM diagnostics failed: " + ex);
        }
    }

    @Override
/**
 * start.
 *
 * @param stage TODO
 */
    public void start(Stage stage) throws Exception {
StartupTrace.mark("MainApp.start enter");

// Crash snapshot: capture any uncaught exceptions best-effort.
Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
    try {
        CrashReportService.writeCrashReport(t, e);
    } catch (Throwable ignored) {
    }
});

// Show *something* ASAP. Anything non-trivial (CSS conversion, theme prefs, self-checks)
// is deferred until after the first window paint.
final Path initialFolder;
{
    java.nio.file.Path home = null;
    try {
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            home = java.nio.file.Path.of(userHome);
        }
    } catch (Exception ignored) {}

    initialFolder = (home != null) ? home : java.nio.file.Path.of(".").toAbsolutePath().normalize();
}


// Minimal stage content is built inside configureExplorerStage (loading scene),
// which calls stage.show() immediately.
configureExplorerStage(stage, initialFolder, /*darkHint*/ true);

// Defer heavier/optional startup work to avoid impacting first paint.
Platform.runLater(() -> {
    // UA stylesheet override can trigger CSS parsing/conversion; do it after first paint.
    try {
        javafx.application.Application.setUserAgentStylesheet(
                "data:text/css,.scroll-bar%20.increment-arrow,%20.scroll-bar%20.decrement-arrow%20{%20-fx-effect:%20null;%20}%20.scroll-bar%20.increment-button,%20.scroll-bar%20.decrement-button%20{%20-fx-effect:%20null;%20}%20.scroll-bar%20.thumb,%20.scroll-bar%20.track%20{%20-fx-effect:%20null;%20}%20.table-view%20.column-header-background,%20.table-view%20.filler,%20.table-view%20.show-hide-columns-button,%20.table-view%20.show-hide-column-image,%20.table-view%20.column-drag-header,%20.table-view%20.column-resize-line,%20.tree-view%20.corner,%20.table-view%20.corner,%20.scroll-pane%20.corner,%20.tree-view%20.virtual-flow%20.corner,%20.table-view%20.virtual-flow%20.corner,%20.scroll-pane%20>%20.corner,%20.scroll-pane%20>%20.viewport%20{%20-fx-background-color:%20transparent;%20-fx-effect:%20null;%20}"
        );
    } catch (Exception ignored) {
    }

    try {
        configureResourceLoggerIfNeeded();
    } catch (Exception ignored) {
    }

    try {
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("heapdumps"));
    } catch (Exception ignore) {
    }

    StartupTrace.mark("post-first-paint deferred startup finished");
});

StartupTrace.mark("MainApp.start exit");

    }

/**
 * configureResourceLoggerIfNeeded.
 *
 */
    private static void configureResourceLoggerIfNeeded() {
        LogSupport.enter(LOG, "configureResourceLoggerIfNeeded");

        if (RESOURCE_LOGGER_CONFIGURED) {
            LogSupport.enter(LOG, "end");
            return;
        }

        synchronized (MainApp.class) {
            if (RESOURCE_LOGGER_CONFIGURED) {
                LogSupport.enter(LOG, "end");
                return;
            }

            try {
                LOG.setLevel(Level.INFO);
            } catch (RuntimeException ex) {
                // ignore
            }

            Handler[] handlers;
            try {
                handlers = LOG.getHandlers();
            } catch (RuntimeException ex) {
                handlers = new Handler[0];
            }

            boolean hasConsole = false;
            for (Handler h : handlers) {
                if (h instanceof ConsoleHandler) {
                    hasConsole = true;
                    break;
                }
            }

            if (!hasConsole) {
                try {
                    ConsoleHandler ch = new ConsoleHandler();
                    ch.setLevel(Level.INFO);
                    ch.setFormatter(new SimpleConsoleFormatter());
                    LOG.addHandler(ch);
                    LOG.setUseParentHandlers(false);
                } catch (RuntimeException ex) {
                    // ignore
                }
            }

            RESOURCE_LOGGER_CONFIGURED = true;
        }
        LogSupport.enter(LOG, "end");
    }

/**
 * configureExplorerStage.
 *
 * @param stage TODO
 * @param initialFolder TODO
 */
    public static void configureExplorerStage(Stage stage, Path initialFolder) throws IOException {
        LogSupport.enter(LOG, "configureExplorerStage");
        ThemeService themeService = new ThemeService();
        themeService.setDarkPreferred(true);
        boolean dark = themeService.isDarkPreferred();
        configureExplorerStage(stage, initialFolder, dark);
        LogSupport.enter(LOG, "end");
    }

/**
 * configureExplorerStage.
 *
 * @param stage TODO
 * @param initialFolder TODO
 * @param dark TODO
 */
    public static void configureExplorerStage(Stage stage, Path initialFolder, boolean dark) throws IOException {

if (stage == null) {
    throw new IllegalArgumentException("stage must not be null");
}

// Build a visible window immediately (fast) and defer FXML/controller wiring by a single pulse.
// This prevents "nothing appears" when FXMLLoader/controller initialization takes time.
stage.setTitle("FileExplorer");

        // App icon is optional; defer until after first paint.
stage.setMinWidth(DEFAULT_MIN_WIDTH);
stage.setMinHeight(DEFAULT_MIN_HEIGHT);

// Ultra-light loading UI (no Controls) to avoid Modena/CSS/skin initialization before first paint.
javafx.scene.text.Text lbl = new javafx.scene.text.Text("Loading…");
lbl.setStyle("-fx-font-size: 16px;");

javafx.scene.shape.Rectangle bg = new javafx.scene.shape.Rectangle();
bg.setManaged(false);
bg.setFill(javafx.scene.paint.Color.web(dark ? "#1e1e1e" : "#ffffff"));

javafx.scene.layout.StackPane loadingRoot = new javafx.scene.layout.StackPane(bg, lbl);
loadingRoot.setPadding(new Insets(24));
loadingRoot.widthProperty().addListener((obs, ov, nv) -> bg.setWidth(nv.doubleValue()));
loadingRoot.heightProperty().addListener((obs, ov, nv) -> bg.setHeight(nv.doubleValue()));

Scene loadingScene = new Scene(loadingRoot, DEFAULT_WIDTH, DEFAULT_HEIGHT);
loadingScene.setFill(javafx.scene.paint.Color.TRANSPARENT);


// User-agent overrides are optional; we keep the default MODENA UA stylesheet.

stage.setScene(loadingScene);
stage.show();
        StartupTrace.mark("stage.show (loading scene)");
        Platform.runLater(() -> {
            try {
                stage.getIcons().add(new Image(MainApp.class.getResourceAsStream("/icons/app.png")));
            } catch (Exception ignored) {
            }
        });

        // Approximate first paint: first FX pulse after stage.show
        Platform.runLater(() -> StartupTrace.mark("FX pulse after show (runLater1)"));


// Phase 3.4: allow deterministic controller teardown on window close.
final AtomicReference<MainController> mainControllerRef = new AtomicReference<>();
AtomicReference<ExplorerContext> contextRef = new AtomicReference<>();
AtomicReference<com.fileexplorer.util.HeapPressureService> heapPressureRef = new AtomicReference<>();
AtomicReference<com.fileexplorer.util.SoakNavigator> soakRef = new AtomicReference<>();
stage.setOnCloseRequest(e -> {
    MainController c = mainControllerRef.get();
    if (c != null) {
        try {
            c.dispose();
        } catch (Exception ignored) {
        }
    }
    com.fileexplorer.util.SoakNavigator sn = soakRef.get();
    if (sn != null) {
        try { sn.close(); } catch (Exception ignored) {}
    }
    com.fileexplorer.util.HeapPressureService hps = heapPressureRef.get();
    if (hps != null) {
        try { hps.close(); } catch (Exception ignored) {}
    }
    ExplorerContext ctx = contextRef.get();
    if (ctx != null) {
        try {
            ctx.close();
        } catch (Exception ignored) {
        }
    }
});
// Defer the heavy FXML load so the window can paint at least once.
        // We use a double runLater instead of a fixed delay so it's pulse-driven.
        Platform.runLater(() -> Platform.runLater(() -> {
    try {
        StartupTrace.mark("begin FXML load");
            java.net.URL fxmlUrl = MainApp.class.getResource("/com/fileexplorer/ui/layout/MainLayout.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("Missing FXML resource: /com/fileexplorer/ui/layout/MainLayout.fxml");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        // Phase 3.4.4: MainApp owns the ExplorerContext (single instance).
        ThemeService themeService = new ThemeService();
        // Resolve preferred theme lazily (after first paint)
        try {
            themeService.setDarkPreferred(true);
        } catch (Exception ignored) {
        }
        boolean darkActual = false;
        try {
            darkActual = themeService.isDarkPreferred();
        } catch (Exception ignored) {
        }

        FileMetadataService fileMetadataService = new FileMetadataService();
        TreeBuildService treeBuildService = new TreeBuildService();
        EventBus eventBus = new EventBus();
        ExplorerContext context = new ExplorerContext(
                themeService,
                fileMetadataService,
                IconCacheService.getInstance(),
                treeBuildService,
                eventBus,
                isSafeMode()
        );
        contextRef.set(context);
        ZoomRoot zoomRoot = new ZoomRoot(root);

        // Phase 4C.1: wrap UI root in a StackPane so we can host an optional perf HUD overlay.
        StackPane overlayRoot = new StackPane(zoomRoot.getRoot());
        Scene scene = new Scene(overlayRoot, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        addStylesheet(scene, darkActual ? "/com/fileexplorer/ui/css/explorer-dark-win.css" : "/com/fileexplorer/ui/css/explorer-light-win.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-win.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-table.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/ui_fixes.css");
        addStylesheet(scene, darkActual ? "/com/fileexplorer/ui/css/fluent-dark.css" : "/com/fileexplorer/ui/css/fluent-light.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/fluent-explorer.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-override-everything.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/progress_pane.css");

        // Wire controller
        MainController controller = loader.getController();
        if (controller != null) {
            mainControllerRef.set(controller);
            // Phase 3.4.4: Attach shared ExplorerContext before any scene-dependent work.
            controller.attach(context);

            // Phase 3.6.2: attach included controllers (e.g., ProgressPane)
            Object ppcObj = loader.getNamespace().get("progressPaneController");
            if (ppcObj instanceof ProgressPaneController ppc) {
                ppc.attach(context);
            }

            // Keep scene wiring minimal for fast first paint.
            controller.setScene(scene);
        }

        // Optional perf HUD.
        // Enabled via -Dfileexplorer.perfHud=true.
        // Also auto-enabled during soak runs unless explicitly disabled with -Dfileexplorer.perfHud=false.
        final String perfHudProp = System.getProperty("fileexplorer.perfHud");
        final boolean soakEnabled = Boolean.getBoolean("fileexplorer.soak.enabled");
        final boolean perfHudEnabled =
                (perfHudProp == null ? false : Boolean.parseBoolean(perfHudProp))
                        || (soakEnabled && !"false".equalsIgnoreCase(perfHudProp));

        if (perfHudEnabled) {
            try {
                com.fileexplorer.ui.perf.PerfHudPane hud = new com.fileexplorer.ui.perf.PerfHudPane(
                        com.fileexplorer.service.icon.AsyncThumbnailService.getInstance(),
                        com.fileexplorer.service.icon.IconCacheService.getInstance(),
                        controller != null ? controller.getMetadataBudgetService() : null
                );
                overlayRoot.getChildren().add(hud);
                StackPane.setAlignment(hud, Pos.TOP_RIGHT);
                StackPane.setMargin(hud, new Insets(10));
                StartupTrace.mark("Perf HUD enabled");
            } catch (Throwable ignored) {
            }
        } else {
            StartupTrace.mark("Perf HUD disabled");
        }

        stage.setScene(scene);

        StartupTrace.mark("stage.setScene (main UI)");

        // Phase 4A.2: centralize post-scene startup scheduling.
        final com.fileexplorer.util.StartupWorkQueue workQueue = new com.fileexplorer.util.StartupWorkQueue();
        workQueue.attachToScene(scene);
        workQueue.markUiReady();

        // Enable thumbnail decoding after first full UI render (avoid startup slowdown).
        workQueue.runAfterUiReady(() -> Platform.runLater(() ->
                com.fileexplorer.service.icon.AsyncThumbnailService.getInstance().setEnabled(true)
        ));

        // Phase 4C.1: heap pressure monitor (best-effort) to trim caches and prevent long-run creep.
        final com.fileexplorer.util.HeapPressureService heapPressure = new com.fileexplorer.util.HeapPressureService(
                Double.parseDouble(System.getProperty("fileexplorer.heapPressure.threshold", "0.85")),
                Long.parseLong(System.getProperty("fileexplorer.heapPressure.intervalMs", "2500")),
                usedFrac -> {
                    try { com.fileexplorer.service.icon.IconCacheService.getInstance().trimStale(); } catch (Throwable ignored) {}
                    try { com.fileexplorer.service.icon.AsyncThumbnailService.getInstance().trimCacheUnderPressure(); } catch (Throwable ignored) {}
                }
        );
        workQueue.runAfterUiReady(() -> {
            try { heapPressure.start(); } catch (Throwable ignored) {}
        });
        // Ensure monitor stops on close (integrated into the existing close handler).
        heapPressureRef.set(heapPressure);

        // Open the initial folder (defaults to user.home) right after the first UI render.
        // This keeps startup paint fast while ensuring the user lands in a useful location.
        if (controller != null && initialFolder != null) {
            workQueue.runAfterUiReady(() -> Platform.runLater(() -> controller.openInitialFolder(initialFolder)));
        }

        // Phase 4C.1: optional successive-folder navigation soak runner.
        if (controller != null && Boolean.getBoolean("fileexplorer.soak.enabled")) {
            workQueue.runAfterUiReady(() -> {
                try {
                    com.fileexplorer.util.SoakNavigator sn = new com.fileexplorer.util.SoakNavigator(controller);
                    soakRef.set(sn);
                    sn.start();
                } catch (Throwable ignored) {}
            });
        }

        // Defer heavy startup tasks until the user is idle (does not block interaction).
        workQueue.runIdle(() -> {
            try {
                Object ppcObj2 = loader.getNamespace().get("progressPaneController");
                if (ppcObj2 instanceof ProgressPaneController ppc2) {
                    // Phase 6.4.0: Startup self-check + quarantine (best-effort).
                    try {
                        StartupSelfCheckService.SelfCheckResult r = new StartupSelfCheckService().run(context);
                        if (r != null && r.hadIssues()) {
                            Platform.runLater(() -> showSelfCheckDialog(r.report()));
                        }
                    } catch (Exception ignored) {
                    }

                    // Phase 3.6.7: restore any persisted operations from prior session.
                    // Phase 6.4.0: Safe mode disables auto-recovery re-enqueue.
                    if (!isSafeMode()) {
                        int recovered = context.operationQueueService().restoreSavedQueue();
                        if (recovered > 0) {
                            Platform.runLater(() -> showRecoveredOpsDialog(context, recovered));
                        }
                    }

                    // Phase 3.6.10: scan for orphan atomic-copy temp files (best-effort).
                    context.operationQueueService().scanForOrphanTempFiles();

                    // Phase 4.5.0: scan for incomplete transaction journals (crash recovery).
                    Platform.runLater(() -> showJournalRecoveryDialog(context));
                }

                if (controller != null && Boolean.getBoolean("fileexplorer.safeMode")) {
                    Platform.runLater(controller::enterSafeMode);
                }

                // Phase 6.4.0: if there is a prior crash snapshot, surface it and offer a one-click bundle.
                Platform.runLater(() -> showLastCrashDialogIfPresent(context));

                // Give controller a chance to release any startup guards after the first real scene is installed.
                if (controller != null) {
                    Platform.runLater(controller::releaseStartupVirtualizationGuards);
                }
            } finally {
                CrashReportService.writeSuccessMarker();
                StartupTrace.mark("deferred heavy startup tasks done");
            }
        });
    } catch (Exception ex) {
        // Keep the loading scene visible and surface the error.
        ex.printStackTrace();
        lbl.setText("Failed to load UI (see console)."
        );
    }
}));
    }

/**
 * bootstrapFonts.
 *
 */
    private static void bootstrapFonts() {
        LogSupport.enter(LOG, "bootstrapFonts");
        if (fontsBootstrapped) {
            LogSupport.enter(LOG, "end");
            return;
        }
        fontsBootstrapped = true;

        boolean loadBundledFonts = Boolean.parseBoolean(System.getProperty("fileexplorer.ui.loadBundledFonts", "false"));
        if (isSafeMode()) {
            loadBundledFonts = false;
        }

        // Always load the icon glyph fonts so glyph-based icons render correctly even when
// fileexplorer.ui.loadBundledFonts=false (Option 2A: keep Fluent icons).
loadFontsFromResources(List.of(
    "/com/fileexplorer/ui/fonts/Segoe Fluent Icons.ttf",
    "/com/fileexplorer/ui/fonts/SegoeIcons.ttf",
    "/fonts/Segoe Fluent Icons.ttf",
    "/fonts/SegoeIcons.ttf",
    "/fonts/SegoeFluentIcons.ttf",
    "/fonts/Segoe-Fluent-Icons.ttf",
    "/fonts/SegoeFluentIcons-Regular.ttf"
));

// Load all bundled UI fonts (weights/styles) so CSS font-family resolution can
        // find them.
        // We support both legacy "/fonts/..." and the preferred
        // "/com/fileexplorer/ui/fonts/..." locations.
        if (loadBundledFonts) {
            loadFontsFromResources(List.of(
                // Preferred location (bundled with the app)
                "/com/fileexplorer/ui/fonts/SegUIVar.ttf", "/com/fileexplorer/ui/fonts/segoeui.ttf",
                "/com/fileexplorer/ui/fonts/segoeuib.ttf", "/com/fileexplorer/ui/fonts/segoeuii.ttf",
                "/com/fileexplorer/ui/fonts/segoeuil.ttf", "/com/fileexplorer/ui/fonts/segoeuisl.ttf",
                "/com/fileexplorer/ui/fonts/segoeuiz.ttf", "/com/fileexplorer/ui/fonts/seguibl.ttf",
                "/com/fileexplorer/ui/fonts/seguibli.ttf", "/com/fileexplorer/ui/fonts/seguili.ttf",
                "/com/fileexplorer/ui/fonts/seguisb.ttf", "/com/fileexplorer/ui/fonts/seguisbi.ttf",
                "/com/fileexplorer/ui/fonts/seguisli.ttf", "/com/fileexplorer/ui/fonts/Segoe Fluent Icons.ttf",
                "/com/fileexplorer/ui/fonts/SegoeIcons.ttf",

                // Legacy/alternate location
                "/fonts/SegUIVar.ttf", "/fonts/segoeui.ttf", "/fonts/segoeuib.ttf", "/fonts/segoeuii.ttf",
                "/fonts/segoeuil.ttf", "/fonts/segoeuisl.ttf", "/fonts/segoeuiz.ttf", "/fonts/seguibl.ttf",
                "/fonts/seguibli.ttf", "/fonts/seguili.ttf", "/fonts/seguisb.ttf", "/fonts/seguisbi.ttf",
                "/fonts/seguisli.ttf", "/fonts/Segoe Fluent Icons.ttf", "/fonts/SegoeIcons.ttf",

                // Older expected names (kept for compatibility)
                "/fonts/SegoeUIVariable.ttf", "/fonts/Segoe UI Variable.ttf", "/fonts/SegoeUIVariable-Regular.ttf",
                "/fonts/SegoeUIVariableText.ttf", "/fonts/SegoeUIVariableDisplay.ttf", "/fonts/SegoeFluentIcons.ttf",
                "/fonts/Segoe-Fluent-Icons.ttf", "/fonts/SegoeFluentIcons-Regular.ttf")
            );
        }

        resolvedSystemFamily = resolveSystemFontFamily();
        resolvedUiFamily = resolveUiFontFamily();
    }

/**
 * loadFirstFontFromResources.
 *
 * @param resourcePaths TODO
 */
    private static void loadFirstFontFromResources(List<String> resourcePaths) {
        LogSupport.enter(LOG, "loadFirstFontFromResources");
        if (resourcePaths == null || resourcePaths.isEmpty()) {
            return;
        }
        for (String path : resourcePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }

            URL url = MainApp.class.getResource(path);
            if (url == null) {
                continue;
            }

            try (InputStream is = url.openStream()) {
                Font loaded = Font.loadFont(is, 12);
                if (loaded != null) {
                    logFontLoaded(path, url, loaded);
                    return;
                }
            } catch (IOException | RuntimeException ex) {
                // ignore
            }
        }
    }

/**
 * loadFontsFromResources.
 *
 * @param resourcePaths TODO
 */
    private static void loadFontsFromResources(List<String> resourcePaths) {
        LogSupport.enter(LOG, "loadFontsFromResources");
        if (resourcePaths == null || resourcePaths.isEmpty()) {
            return;
        }
        for (String path : resourcePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }

            URL url = MainApp.class.getResource(path);
            if (url == null) {
                continue;
            }

            try (InputStream is = url.openStream()) {
                Font loaded = Font.loadFont(is, 12);
                if (loaded != null) {
                    loadedFontResourcePaths.add(path);
                }
            } catch (Exception ex) {
                // ignore
            }
        }
    }

/**
 * resolveUiFontFamily.
 *
 * @return TODO
 */
    private static String resolveUiFontFamily() {
        LogSupport.enter(LOG, "resolveUiFontFamily");
        try {
            if (Font.getFamilies().contains(UI_FONT_FAMILY)) {
                return UI_FONT_FAMILY;
            }
            if (Font.getFamilies().contains(UI_FONT_FAMILY_FALLBACK)) {
                return UI_FONT_FAMILY_FALLBACK;
            }
        } catch (RuntimeException ex) {
            // ignore
        }
        return resolvedSystemFamily;
    }

/**
 * resolveSystemFontFamily.
 *
 * @return TODO
 */
    private static String resolveSystemFontFamily() {
        LogSupport.enter(LOG, "resolveSystemFontFamily");
        try {
            if (Font.getDefault() != null && Font.getDefault().getFamily() != null) {
                return Font.getDefault().getFamily();
            }
        } catch (RuntimeException ex) {
            // ignore
        }
        return SYSTEM_FONT_FAMILY_KEYWORD;
    }

/**
 * computeStartupFontPx.
 *
 * @param stage TODO
 * @return TODO
 */
    private static double computeStartupFontPx(Stage stage) {
        LogSupport.enter(LOG, "computeStartupFontPx");
        double override = parseFontOverride();
        if (override > 0) {
            return clamp(override, MIN_FONT_PX, MAX_FONT_PX);
        }

        // Heuristic: scale up slightly on very large displays, but keep conservative
        // bounds.
        Rectangle2D vb = getTargetVisualBoundsForStage(stage);
        double w = vb.getWidth();
        double h = vb.getHeight();
        double diag = Math.sqrt(w * w + h * h);

        double base = 20.0;
        if (diag >= 4000) {
            base = 22.0;
        }
        if (diag >= 5500) {
            base = 24.0;
        }

        return clamp(base, MIN_FONT_PX, MAX_FONT_PX);
    }

/**
 * parseFontOverride.
 *
 * @return TODO
 */
    private static double parseFontOverride() {
        LogSupport.enter(LOG, "parseFontOverride");
        try {
            String v = System.getProperty(SYS_UI_FONT_PX);
            if (v == null || v.isBlank()) {
                return -1;
            }
            return Double.parseDouble(v.trim());
        } catch (RuntimeException ex) {
            return -1;
        }
    }

/**
 * buildFontFamilyCss.
 *
 * @return TODO
 */
    private static String buildFontFamilyCss() {
        LogSupport.enter(LOG, "buildFontFamilyCss");
        // Primary UI family, with fallback.
        String family = resolvedUiFamily;
        if (family == null || family.isBlank() || SYSTEM_FONT_FAMILY_KEYWORD.equalsIgnoreCase(family)) {
            return "-fx-font-family: " + SYSTEM_FONT_FAMILY_KEYWORD + ";";
        }
        return "-fx-font-family: \"" + family + "\", \"" + UI_FONT_FAMILY_FALLBACK + "\", " + SYSTEM_FONT_FAMILY_KEYWORD
                + ";";
    }

/**
 * appendBaseStyle.
 *
 * @param existingStyle TODO
 * @param fontFamilyCss TODO
 * @param fontPx TODO
 * @return TODO
 */
    private static String appendBaseStyle(String existingStyle, String fontFamilyCss, double fontPx) {
        LogSupport.enter(LOG, "appendBaseStyle");
        String base = (existingStyle == null) ? "" : existingStyle.trim();
        String sep = base.isEmpty() ? "" : (base.endsWith(";") ? " " : "; ");
        return base + sep + fontFamilyCss + " " + buildFontSizeCss(fontPx);
    }

/**
 * buildFontSizeCss.
 *
 * @param fontPx TODO
 * @return TODO
 */
    private static String buildFontSizeCss(double fontPx) {
        LogSupport.enter(LOG, "buildFontSizeCss");
        double clamped = clamp(fontPx, MIN_FONT_PX, MAX_FONT_PX);
        return "-fx-font-size: " + clamped + "px;";
    }

/**
 * clamp.
 *
 * @param v TODO
 * @param lo TODO
 * @param hi TODO
 * @return TODO
 */
    private static double clamp(double v, double lo, double hi) {
        LogSupport.enter(LOG, "clamp");
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

/**
 * getTargetVisualBoundsForStage.
 *
 * @param stage TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStage(Stage stage) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStage");
        try {
            // Prefer the screen containing the stage
            if (stage != null) {
                Rectangle2D sb = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
                for (Screen s : Screen.getScreens()) {
                    if (s.getVisualBounds().intersects(sb)) {
                        return s.getVisualBounds();
                    }
                }
            }
        } catch (RuntimeException ex) {
            // ignore
        }
        return Screen.getPrimary().getVisualBounds();
    }

/**
 * safeVisualBounds.
 *
 * @param b TODO
 * @return TODO
 */
    private static Rectangle2D safeVisualBounds(Rectangle2D b) {
        LogSupport.enter(LOG, "safeVisualBounds");
        if (b == null) {
            return Screen.getPrimary().getVisualBounds();
        }
        double w = Math.max(1.0, b.getWidth());
        double h = Math.max(1.0, b.getHeight());
        return new Rectangle2D(b.getMinX(), b.getMinY(), w, h);
    }

/**
 * getTargetVisualBoundsForStartup.
 *
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStartup() {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStartup");
        Rectangle2D vb = safeVisualBounds(Screen.getPrimary().getVisualBounds());
        try {
            List<Screen> screens = Screen.getScreens();
            if (screens != null && !screens.isEmpty()) {
                vb = safeVisualBounds(screens.getFirst().getVisualBounds());
            }
        } catch (RuntimeException ex) {
            // ignore
        }
        return vb;
    }

/**
 * getTargetVisualBoundsForStartup.
 *
 * @param stage TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStartup(Stage stage) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStartup");
        if (stage != null) {
            return getTargetVisualBoundsForStage(stage);
        }
        return getTargetVisualBoundsForStartup();
    }

/**
 * getTargetVisualBoundsForStageSafe.
 *
 * @param stage TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStageSafe(Stage stage) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafe");
        if (stage != null) {
            return getTargetVisualBoundsForStage(stage);
        }
        return Screen.getPrimary().getVisualBounds();
    }

/**
 * getTargetVisualBoundsForStage.
 *
 * @param stage TODO
 * @param safe TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStage(Stage stage, boolean safe) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStage");
        if (!safe) {
            return getTargetVisualBoundsForStage(stage);
        }
        return getTargetVisualBoundsForStageSafe(stage);
    }

/**
 * getTargetVisualBoundsForStageSafeIfNull.
 *
 * @param stage TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStageSafeIfNull(Stage stage) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafeIfNull");
        if (stage == null) {
            return Screen.getPrimary().getVisualBounds();
        }
        return getTargetVisualBoundsForStage(stage);
    }

/**
 * getTargetVisualBoundsForStage.
 *
 * @param stage TODO
 * @param fallback TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStage(Stage stage, Rectangle2D fallback) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStage");
        try {
            Rectangle2D vb = getTargetVisualBoundsForStage(stage);
            if (vb != null) {
                return vb;
            }
        } catch (RuntimeException ex) {
            // ignore
        }
        return fallback != null ? fallback : Screen.getPrimary().getVisualBounds();
    }

/**
 * getTargetVisualBoundsForStageSafe.
 *
 * @param stage TODO
 * @param fallback TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStageSafe(Stage stage, Rectangle2D fallback) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafe");
        try {
            Rectangle2D vb = getTargetVisualBoundsForStage(stage);
            if (vb != null) {
                return vb;
            }
        } catch (RuntimeException ex) {
            // ignore
        }
        return fallback != null ? fallback : Screen.getPrimary().getVisualBounds();
    }

/**
 * getTargetVisualBoundsForStage.
 *
 * @param stage TODO
 * @param fallback TODO
 * @param safe TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStage(Stage stage, Rectangle2D fallback, boolean safe) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStage");
        if (!safe) {
            return getTargetVisualBoundsForStage(stage, fallback);
        }
        return getTargetVisualBoundsForStageSafe(stage, fallback);
    }

/**
 * getTargetVisualBoundsForStageSafeIfNull.
 *
 * @param stage TODO
 * @param fallback TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStageSafeIfNull(Stage stage, Rectangle2D fallback) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafeIfNull");
        if (stage == null) {
            return fallback != null ? fallback : Screen.getPrimary().getVisualBounds();
        }
        return getTargetVisualBoundsForStage(stage, fallback);
    }

/**
 * getTargetVisualBoundsForStageSafe.
 *
 * @param stage TODO
 * @param fallback TODO
 * @param safe TODO
 * @return TODO
 */
    private static Rectangle2D getTargetVisualBoundsForStageSafe(Stage stage, Rectangle2D fallback, boolean safe) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafe");
        if (!safe) {
            return getTargetVisualBoundsForStage(stage, fallback);
        }
        return getTargetVisualBoundsForStageSafe(stage, fallback);
    }

    private static Rectangle2D getTargetVisualBoundsForStageSafeIfNull(Stage stage, Rectangle2D fallback,
            boolean safe) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafeIfNull");
        return getTargetVisualBoundsForStageSafeIfNull(stage, fallback);
    }

    private static Rectangle2D getTargetVisualBoundsForStageSafeIfNull(Stage stage, Rectangle2D fallback, boolean safe,
            boolean safe2) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafeIfNull");
        return getTargetVisualBoundsForStageSafeIfNull(stage, fallback, safe && safe2);
    }

    // ---------------------------------------------------------------------
    // Resource audit (CSS / fonts / images) via java.util.logging
    // ---------------------------------------------------------------------

/**
 * addStylesheet.
 *
 * @param scene TODO
 * @param resourcePath TODO
 */
    private static void addStylesheet(Scene scene, String resourcePath) {
        LogSupport.enter(LOG, "addStylesheet");
        if (scene == null || resourcePath == null || resourcePath.isBlank()) {
            return;
        }

        URL url = MainApp.class.getResource(resourcePath);
        if (url == null) {
            return;
        }

        String resolved = url.toExternalForm();
        if (!scene.getStylesheets().contains(resolved)) {
            scene.getStylesheets().add(resolved);
            logCssLoaded("Scene.stylesheets(added)", null, resolved);
        } else {
            logCssLoaded("Scene.stylesheets(skip-duplicate)", null, resolved);
        }
    }

/**
 * attachStylesheetAudit.
 *
 * @param scene TODO
 */
    private static void attachStylesheetAudit(Scene scene) {
        LogSupport.enter(LOG, "attachStylesheetAudit");
        if (scene == null) {
            return;
        }
        if (!isResourceAuditEnabled()) {
            return;
        }

        for (String existing : scene.getStylesheets()) {
            logCssLoaded("Scene.stylesheets(existing)", null, existing);
        }

        scene.getStylesheets().addListener((ListChangeListener<String>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (String added : change.getAddedSubList()) {
                        logCssLoaded("Scene.stylesheets(added)", null, added);
                    }
                }
            }
        });
    }

/**
 * attachStylesheetAudit.
 *
 * @param parent TODO
 */
    private static void attachStylesheetAudit(Parent parent) {
        LogSupport.enter(LOG, "attachStylesheetAudit");
        if (parent == null) {
            return;
        }
        if (!isResourceAuditEnabled()) {
            return;
        }

        for (String existing : parent.getStylesheets()) {
            logCssLoaded("Parent.stylesheets(existing)", null, existing);
        }

        parent.getStylesheets().addListener((ListChangeListener<String>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (String added : change.getAddedSubList()) {
                        logCssLoaded("Parent.stylesheets(added)", null, added);
                    }
                }
            }
        });
    }

/**
 * logFontLoaded.
 *
 * @param requestedPath TODO
 * @param url TODO
 * @param loaded TODO
 */
    private static void logFontLoaded(String requestedPath, URL url, Font loaded) {
        LogSupport.enter(LOG, "logFontLoaded");
        if (!isResourceAuditEnabled()) {
            return;
        }
        if (url == null || loaded == null) {
            return;
        }
        String resolved = url.toExternalForm();
        String key = "FONT|" + resolved;
        if (LOGGED_RESOURCES.add(key)) {
            String family = safeString(loaded.getFamily());
            String name = safeString(loaded.getName());
            LOG.info("FONT loaded requestedPath=" + requestedPath + " resolvedUrl=" + resolved + " family=" + family
                    + " name=" + name);
        }
    }

/**
 * logCssLoaded.
 *
 * @param source TODO
 * @param requestedPath TODO
 * @param resolvedUrl TODO
 */
    private static void logCssLoaded(String source, String requestedPath, String resolvedUrl) {
        LogSupport.enter(LOG, "logCssLoaded");
        if (!isResourceAuditEnabled()) {
            return;
        }
        boolean firstSeen = logCssLoadedInternal(source, requestedPath, resolvedUrl);
        if (firstSeen) {
            scanStylesheetForUrlReferences(resolvedUrl);
        }
    }

/**
 * logCssLoadedInternal.
 *
 * @param source TODO
 * @param requestedPath TODO
 * @param resolvedUrl TODO
 * @return TODO
 */
    private static boolean logCssLoadedInternal(String source, String requestedPath, String resolvedUrl) {
        LogSupport.enter(LOG, "logCssLoadedInternal");
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            return false;
        }
        String lower = resolvedUrl.toLowerCase();
        if (!lower.endsWith(".css")) {
            return false;
        }
        String key = "CSS|" + resolvedUrl;
        if (LOGGED_RESOURCES.add(key)) {
            LOG.info("CSS loaded source=" + source + " requestedPath=" + requestedPath + " resolvedUrl=" + resolvedUrl);
            return true;
        }
        return false;
    }

/**
 * logImageDeclared.
 *
 * @param source TODO
 * @param requestedPath TODO
 * @param resolvedUrl TODO
 */
    private static void logImageDeclared(String source, String requestedPath, String resolvedUrl) {
        LogSupport.enter(LOG, "logImageDeclared");
        if (!isResourceAuditEnabled()) {
            return;
        }
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            return;
        }
        String key = "IMG|" + resolvedUrl;
        if (LOGGED_RESOURCES.add(key)) {
            LOG.info("IMAGE loaded source=" + source + " requestedPath=" + requestedPath + " resolvedUrl="
                    + resolvedUrl);
        }
    }

/**
 * logFontDeclared.
 *
 * @param source TODO
 * @param requestedPath TODO
 * @param resolvedUrl TODO
 */
    private static void logFontDeclared(String source, String requestedPath, String resolvedUrl) {
        LogSupport.enter(LOG, "logFontDeclared");
        if (!isResourceAuditEnabled()) {
            return;
        }
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            return;
        }
        String key = "FONTURL|" + resolvedUrl;
        if (LOGGED_RESOURCES.add(key)) {
            LOG.info("FONT referenced source=" + source + " requestedPath=" + requestedPath + " resolvedUrl="
                    + resolvedUrl);
        }
    }

/**
 * scanStylesheetForUrlReferences.
 *
 * @param stylesheetExternalForm TODO
 */
    private static void scanStylesheetForUrlReferences(String stylesheetExternalForm) {
        LogSupport.enter(LOG, "scanStylesheetForUrlReferences");
        if (!isResourceAuditEnabled()) {
            return;
        }
        if (stylesheetExternalForm == null || stylesheetExternalForm.isBlank()) {
            return;
        }

        String normalized = normalizeForExtensionChecks(stylesheetExternalForm);
        if (!normalized.toLowerCase().endsWith(".css")) {
            return;
        }

        if (!SCANNED_STYLESHEETS.add(stylesheetExternalForm)) {
            return;
        }

        try {
            URL cssUrl = new URL(stylesheetExternalForm);
            scanStylesheet(cssUrl);
        } catch (MalformedURLException | RuntimeException _) {
            // ignore
        }
    }

/**
 * scanStylesheet.
 *
 * @param cssUrl TODO
 */
    private static void scanStylesheet(URL cssUrl) {
        LogSupport.enter(LOG, "scanStylesheet");
        if (cssUrl == null) {
            return;
        }

        String cssKey = cssUrl.toExternalForm();
        // Log it as a CSS load discovered via scanning, but avoid re-scanning loops.
        logCssLoadedInternal("CSS.scan", null, cssKey);

        String cssText = readUrlAsString(cssUrl);
        if (cssText == null || cssText.isBlank()) {
            return;
        }

        String stripped = stripCssComments(cssText);

        // Resolve @import chains first.
        for (String imp : extractImports(stripped)) {
            LogSupport.enter(LOG, "extractImports");
            URL imported = resolveCssReference(cssUrl, imp);
            if (imported != null) {
                scanStylesheet(imported);
            } else {
                LOG.info("CSS import unresolved base=" + cssKey + " ref=" + imp);
            }
        }

        // Then resolve url(...) references (images, fonts, etc.)
        for (String ref : extractUrlFunctions(stripped)) {
            LogSupport.enter(LOG, "extractUrlFunctions");
            if (ref == null) {
                continue;
            }
            String cleaned = ref.trim();
            if (cleaned.isEmpty()) {
                continue;
            }

            if (cleaned.startsWith("#") || cleaned.regionMatches(true, 0, "data:", 0, 5)) {
                continue;
            }

            URL resolved = resolveCssReference(cssUrl, cleaned);
            if (resolved == null) {
                LOG.info("CSS url unresolved base=" + cssKey + " ref=" + cleaned);
                continue;
            }

            String resolvedExternal = resolved.toExternalForm();
            String resolvedNormalized = normalizeForExtensionChecks(resolvedExternal);
            String lower = resolvedNormalized.toLowerCase();

            if (lower.endsWith(".css")) {
                scanStylesheet(resolved);
                continue;
            }

            if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc") || lower.endsWith(".woff")
                    || lower.endsWith(".woff2")) {
                logFontDeclared("CSS.url", cleaned, resolvedExternal);
                continue;
            }

            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                    || lower.endsWith(".bmp") || lower.endsWith(".webp") || lower.endsWith(".svg")) {
                logImageDeclared("CSS.url", cleaned, resolvedExternal);
            }
        }
    }

/**
 * resolveCssReference.
 *
 * @param cssUrl TODO
 * @param ref TODO
 * @return TODO
 */
    private static URL resolveCssReference(URL cssUrl, String ref) {
        LogSupport.enter(LOG, "resolveCssReference");
        if (cssUrl == null || ref == null) {
            return null;
        }

        String trimmed = ref.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // If it's classpath-absolute ("/com/..."), resolve via application ClassLoader.
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            URL u = MainApp.class.getResource(trimmed);
            if (u != null) {
                return u;
            }
        }

        // Try absolute URL first.
        try {
            return new URL(trimmed);
        } catch (MalformedURLException ex) {
            // fall through
        }

        // Relative URL resolution (works for file: and jar:file: URLs).
        try {
            return new URL(cssUrl, trimmed);
        } catch (MalformedURLException ex) {
            // fall through
        }

        // Fallback: string-based concatenation.
        try {
            String base = cssUrl.toExternalForm();
            int idx = base.lastIndexOf('/');
            if (idx >= 0) {
                String prefix = base.substring(0, idx + 1);
                return new URL(prefix + trimmed);
            }
        } catch (MalformedURLException | RuntimeException _) {
            // ignore
        }

        return null;
    }

/**
 * readUrlAsString.
 *
 * @param url TODO
 * @return TODO
 */
    private static String readUrlAsString(URL url) {
        LogSupport.enter(LOG, "readUrlAsString");
        try (InputStream in = url.openStream()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return "";
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException _) {
            return null;
        }
    }

/**
 * stripCssComments.
 *
 * @param css TODO
 * @return TODO
 */
    private static String stripCssComments(String css) {
        LogSupport.enter(LOG, "stripCssComments");
        if (css == null || css.isEmpty()) {
            return css;
        }

        StringBuilder out = new StringBuilder(css.length());
        int i = 0;
        int n = css.length();
        while (i < n) {
            char c = css.charAt(i);
            if (c == '/' && (i + 1) < n && css.charAt(i + 1) == '*') {
                i += 2;
                while (i < n) {
                    if (css.charAt(i) == '*' && (i + 1) < n && css.charAt(i + 1) == '/') {
                        i += 2;
                        break;
                    }
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

/**
 * extractImports.
 *
 * @param css TODO
 * @return TODO
 */
    private static List<String> extractImports(String css) {
        LogSupport.enter(LOG, "extractImports");
        List<String> imports = new ArrayList<>();
        if (css == null || css.isEmpty()) {
            return imports;
        }

        String lower = css.toLowerCase();
        int idx = 0;
        while (idx >= 0) {
            idx = lower.indexOf("@import", idx);
            if (idx < 0) {
                break;
            }

            int i = idx + 7;
            i = skipWhitespace(css, i);
            if (i >= css.length()) {
                break;
            }

            String ref;
            if (startsWithIgnoreCase(lower, i, "url(")) {
                LogSupport.enter(LOG, "startsWithIgnoreCase");
                ParsedFunction f = parseFunctionArgument(css, i + 4);
                ref = f.value;
                i = f.nextIndex;
            } else {
                ParsedString s = parseStringOrBare(css, i);
                ref = s.value;
                i = s.nextIndex;
            }

            if (ref != null && !ref.isBlank()) {
                imports.add(ref.trim());
            }

            int semi = css.indexOf(';', i);
            if (semi >= 0) {
                idx = semi + 1;
            } else {
                idx = i + 1;
            }
        }

        return imports;
    }

/**
 * extractUrlFunctions.
 *
 * @param css TODO
 * @return TODO
 */
    private static List<String> extractUrlFunctions(String css) {
        LogSupport.enter(LOG, "extractUrlFunctions");
        List<String> urls = new ArrayList<>();
        if (css == null || css.isEmpty()) {
            return urls;
        }

        String lower = css.toLowerCase();
        int idx = 0;
        while (idx >= 0) {
            idx = lower.indexOf("url(", idx);
            if (idx < 0) {
                break;
            }
            ParsedFunction f = parseFunctionArgument(css, idx + 4);
            if (f.value != null && !f.value.isBlank()) {
                urls.add(f.value.trim());
            }
            idx = Math.max(f.nextIndex, idx + 4);
        }

        return urls;
    }

/**
 * startsWithIgnoreCase.
 *
 * @param lower TODO
 * @param index TODO
 * @param tokenLower TODO
 * @return TODO
 */
    private static boolean startsWithIgnoreCase(String lower, int index, String tokenLower) {
        LogSupport.enter(LOG, "startsWithIgnoreCase");
        if (lower == null || tokenLower == null) {
            return false;
        }
        if (index < 0 || index + tokenLower.length() > lower.length()) {
            return false;
        }
        return lower.startsWith(tokenLower, index);
    }

/**
 * skipWhitespace.
 *
 * @param s TODO
 * @param i TODO
 * @return TODO
 */
    private static int skipWhitespace(String s, int i) {
        LogSupport.enter(LOG, "skipWhitespace");
        int n = s.length();
        int j = i;
        while (j < n) {
            char c = s.charAt(j);
            if (!Character.isWhitespace(c)) {
                break;
            }
            j++;
        }
        return j;
    }

/**
 * parseFunctionArgument.
 *
 * @param s TODO
 * @param startIndex TODO
 * @return TODO
 */
    private static ParsedFunction parseFunctionArgument(String s, int startIndex) {
        LogSupport.enter(LOG, "parseFunctionArgument");
        int i = startIndex;
        int n = s.length();
        i = skipWhitespace(s, i);

        if (i >= n) {
            return new ParsedFunction("", n);
        }

        String value;
        char first = s.charAt(i);
        if (first == '"' || first == '\'') {
            ParsedString ps = parseQuoted(s, i);
            value = ps.value;
            i = ps.nextIndex;
        } else {
            StringBuilder sb = new StringBuilder();
            while (i < n) {
                char c = s.charAt(i);
                if (c == ')') {
                    i++;
                    break;
                }
                sb.append(c);
                i++;
            }
            value = sb.toString().trim();
        }

        while (i < n && s.charAt(i) != ')') {
            i++;
        }
        if (i < n && s.charAt(i) == ')') {
            i++;
        }

        return new ParsedFunction(unquote(value), i);
    }

/**
 * parseStringOrBare.
 *
 * @param s TODO
 * @param startIndex TODO
 * @return TODO
 */
    private static ParsedString parseStringOrBare(String s, int startIndex) {
        LogSupport.enter(LOG, "parseStringOrBare");
        int i = skipWhitespace(s, startIndex);
        if (i >= s.length()) {
            return new ParsedString("", s.length());
        }

        char c = s.charAt(i);
        if (c == '"' || c == '\'') {
            ParsedString ps = parseQuoted(s, i);
            return new ParsedString(unquote(ps.value), ps.nextIndex);
        }

        StringBuilder sb = new StringBuilder();
        int n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch) || ch == ';') {
                break;
            }
            sb.append(ch);
            i++;
        }
        return new ParsedString(unquote(sb.toString()), i);
    }

/**
 * parseQuoted.
 *
 * @param s TODO
 * @param startIndex TODO
 * @return TODO
 */
    private static ParsedString parseQuoted(String s, int startIndex) {
        LogSupport.enter(LOG, "parseQuoted");
        int n = s.length();
        char quote = s.charAt(startIndex);
        int i = startIndex + 1;
        StringBuilder sb = new StringBuilder();

        while (i < n) {
            char c = s.charAt(i);
            if (c == quote) {
                i++;
                break;
            }
            if (c == '\\' && (i + 1) < n) {
                i++;
                sb.append(s.charAt(i));
                i++;
                continue;
            }
            sb.append(c);
            i++;
        }
        return new ParsedString(sb.toString(), i);
    }

/**
 * unquote.
 *
 * @param v TODO
 * @return TODO
 */
    private static String unquote(String v) {
        LogSupport.enter(LOG, "unquote");
        if (v == null) {
            return null;
        }
        String s = v.trim();
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

/**
 * normalizeForExtensionChecks.
 *
 * @param url TODO
 * @return TODO
 */
    private static String normalizeForExtensionChecks(String url) {
        LogSupport.enter(LOG, "normalizeForExtensionChecks");
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        int h = url.indexOf('#');
        int cut = -1;
        if (q >= 0 && h >= 0) {
            cut = Math.min(q, h);
        } else if (q >= 0) {
            cut = q;
        } else if (h >= 0) {
            cut = h;
        }
        if (cut >= 0) {
            return url.substring(0, cut);
        }
        return url;
    }

    private record ParsedFunction(String value, int nextIndex) {
        private ParsedFunction {
            LogSupport.enter(LOG, "ParsedFunction");
        }
        }

    private record ParsedString(String value, int nextIndex) {
        private ParsedString {
            LogSupport.enter(LOG, "ParsedString");
        }
        }

/**
 * logImagesInGraph.
 *
 * @param root TODO
 */
    private static void logImagesInGraph(Parent root) {
        LogSupport.enter(LOG, "logImagesInGraph");
        if (!isResourceAuditEnabled()) {
            return;
        }
        if (root == null) {
            return;
        }
        walkAndLogImages(root);
    }

/**
 * walkAndLogImages.
 *
 * @param node TODO
 */
    private static void walkAndLogImages(Node node) {
        LogSupport.enter(LOG, "walkAndLogImages");
        if (node == null) {
            return;
        }

        if (node instanceof ImageView iv) {
            Image img = iv.getImage();
            if (img != null) {
                String url = img.getUrl();
                if (url != null && !url.isBlank()) {
                    logImageDeclared("ImageView", null, url);
                }
            }

            iv.imageProperty().addListener((_, _, newImg) -> {
                if (newImg == null) {
                    return;
                }
                String u = newImg.getUrl();
                if (u != null && !u.isBlank()) {
                    logImageDeclared("ImageView", null, u);
                }
            });
        }

        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                walkAndLogImages(child);
            }
        }
    }

/**
 * safeString.
 *
 * @param v TODO
 * @return TODO
 */
    private static String safeString(String v) {
        LogSupport.enter(LOG, "safeString");
        return v == null ? "" : v;
    }

/**
 * attachStylesheets.
 *
 * @param scene TODO
 */
    private static void attachStylesheets(Scene scene) {
        LogSupport.enter(LOG, "attachStylesheets");
        if (scene == null) {
            return;
        }

        LogSupport.enter(LOG, "zoom_overrides");
        addStylesheet(scene, "/com/fileexplorer/ui/zoom_overrides.css");

        LogSupport.enter(LOG, "table_header_hover");
        addStylesheet(scene, "/com/fileexplorer/ui/table_header_hover.css");

        // Must be last so it wins over fluent defaults.
        LogSupport.enter(LOG, "ui_fixes");
        addStylesheet(scene, "/com/fileexplorer/ui/ui_fixes.css");
    }

/**
 * configureStartupWindowSize.
 *
 * @param stage TODO
 */
    private static void configureStartupWindowSize(Stage stage) {
        LogSupport.enter(LOG, "configureStartupWindowSize");
        if (stage == null) {
            return;
        }

        double w = stage.getWidth();
        double h = stage.getHeight();

        if (w >= MIN_STARTUP_WIDTH && h >= MIN_STARTUP_HEIGHT) {
            return;
        }

        Rectangle2D vb = getTargetVisualBoundsForStage(stage);

        double targetW = Math.max(w, vb.getWidth() * STARTUP_WIDTH_FRACTION);
        double targetH = Math.max(h, vb.getHeight() * STARTUP_HEIGHT_FRACTION);

        targetW = Math.max(targetW, MIN_STARTUP_WIDTH);
        targetH = Math.max(targetH, MIN_STARTUP_HEIGHT);

        targetW = Math.min(targetW, vb.getWidth());
        targetH = Math.min(targetH, vb.getHeight());

        stage.setWidth(targetW);
        stage.setHeight(targetH);

        stage.setX(vb.getMinX() + (vb.getWidth() - targetW) / 2.0);
        stage.setY(vb.getMinY() + (vb.getHeight() - targetH) / 2.0);
    }

/**
 * main.
 *
 * @param args TODO
 */
    public static void main(String[] args) {
        printJvmDiagnosticsOnce("main");
        LogSupport.enter(LOG, "main");
        launch(args);
    }

    /**
     * Phase 3.6.7.1: Prompt the user what to do with recovered operations.
     * Recovered operations are loaded in paused mode; "Resume" starts processing, "Discard" clears both
     * the persisted queue file and the recovered in-memory operations.
     */
    private static void showRecoveredOpsDialog(ExplorerContext context, int recovered) {
        if (context == null || recovered <= 0) return;

        // Phase 3.6.7.2: persisted recovery policy
        // Values: ASK (default), ALWAYS_RESUME, ALWAYS_RESUME_QUEUED_ONLY, ALWAYS_DISCARD
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(MainApp.class);
        String policy = prefs.get("operations.recoveryPolicy", "ASK");
        if ("ALWAYS_RESUME_ALL".equalsIgnoreCase(policy)) {
            context.operationQueueService().resumeRecoveredAllIncludingRunning();
            return;
        }
        if ("ALWAYS_RESUME".equalsIgnoreCase(policy)) {
            // Safe resume: previously-running operations remain blocked until explicitly allowed.
            context.operationQueueService().resume();
            return;
        }
        if ("ALWAYS_DISCARD".equalsIgnoreCase(policy)) {
            context.operationQueueService().discardRecoveredAndClearQueue();
            return;
        }

        ButtonType resume = new ButtonType("Resume (safe)");
        ButtonType resumeQueuedOnly = new ButtonType("Resume queued only");
        ButtonType resumeAll = new ButtonType("Resume all (unsafe)");
        ButtonType discard = new ButtonType("Discard");
        ButtonType keepPaused = new ButtonType("Keep paused");

        int runningRecovered = context.operationQueueService().getRecoveredRunningCount();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Recovered operations");
        alert.setHeaderText("Recovered " + recovered + " operation" + (recovered == 1 ? "" : "s") + " from the previous session."
                + (runningRecovered > 0 ? " (" + runningRecovered + " were running)" : ""));

        javafx.scene.control.CheckBox remember = new javafx.scene.control.CheckBox("Remember my choice");
        javafx.scene.control.Label msg = new javafx.scene.control.Label(
                "Resume processing now, resume queued-only (skip those that were running), discard the recovered operations, or keep the queue paused.");
        msg.setWrapText(true);
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(8, msg, remember);
        alert.getDialogPane().setContent(box);
        if (runningRecovered > 0) {
            alert.getButtonTypes().setAll(resume, resumeQueuedOnly, resumeAll, discard, keepPaused);
        } else {
            alert.getButtonTypes().setAll(resume, resumeQueuedOnly, discard, keepPaused);
        }
        com.fileexplorer.util.DialogTheme.apply(alert, null);
        ButtonType chosen = alert.showAndWait().orElse(keepPaused);
        if (chosen == resume) {
            context.operationQueueService().resume();
            if (remember.isSelected()) {
                prefs.put("operations.recoveryPolicy", "ALWAYS_RESUME");
            }
        } else if (chosen == resumeQueuedOnly) {
            context.operationQueueService().resumeRecoveredQueuedOnly();
            if (remember.isSelected()) {
                prefs.put("operations.recoveryPolicy", "ALWAYS_RESUME_QUEUED_ONLY");
            }
        } else if (chosen == resumeAll) {
            context.operationQueueService().resumeRecoveredAllIncludingRunning();
            if (remember.isSelected()) {
                prefs.put("operations.recoveryPolicy", "ALWAYS_RESUME_ALL");
            }
        } else if (chosen == discard) {
            context.operationQueueService().discardRecoveredAndClearQueue();
            if (remember.isSelected()) {
                prefs.put("operations.recoveryPolicy", "ALWAYS_DISCARD");
            }
        } else {
            // Keep paused; user can resume later from the Operations pane.
            if (remember.isSelected()) {
                prefs.put("operations.recoveryPolicy", "ASK");
            }
        }
    }

    /**
     * Phase 6.4.0: Surface startup self-check results.
     */
    private static void showSelfCheckDialog(String report) {
        if (report == null || report.isBlank()) return;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Startup self-check");
        alert.setHeaderText("Recovered from on-disk issues");
        javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(report);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefColumnCount(80);
        ta.setPrefRowCount(18);
        alert.getDialogPane().setContent(ta);
        com.fileexplorer.util.DialogTheme.apply(alert, null);
        alert.showAndWait();
    }

    /**
     * Phase 6.4.0: If a crash snapshot exists, offer a one-click support bundle generation.
     */
    private static void showLastCrashDialogIfPresent(ExplorerContext context) {
        if (context == null) return;

        // Policy:
        //   -Dfileexplorer.crashDialog=false   => never show
        //   -Dfileexplorer.crashDialog=always => always show if file exists
        //   default / "fresh"                 => show only if crash is newer than last-success marker AND within N hours
        String policy = System.getProperty("fileexplorer.crashDialog", "fresh").trim().toLowerCase();

        if ("false".equals(policy) || "0".equals(policy) || "off".equals(policy) || "never".equals(policy)) {
            return;
        }

        try {
            java.nio.file.Path crash = CrashReportService.lastCrashFile();
            if (crash == null || !Files.exists(crash)) return;

            if (!"always".equals(policy)) {
                // Freshness window
                long freshHours = 24;
                try {
                    freshHours = Long.parseLong(System.getProperty("fileexplorer.crashDialog.freshHours", "24"));
                } catch (Exception ignored) {
                }
                if (freshHours < 1) freshHours = 1;

                // Only show if crash happened since last successful startup
                java.nio.file.Path success = CrashReportService.lastSuccessFile();
                java.time.Instant crashTime = Files.getLastModifiedTime(crash).toInstant();

                if (Files.exists(success)) {
                    java.time.Instant successTime = Files.getLastModifiedTime(success).toInstant();
                    if (!crashTime.isAfter(successTime)) {
                        return; // crash is older than a successful run
                    }
                }

                java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofHours(freshHours));
                if (crashTime.isBefore(cutoff)) {
                    return; // crash is too old to bother the user
                }
            }

            ButtonType bundle = new ButtonType("Generate support bundle");
            ButtonType open = new ButtonType("Open crash report");
            ButtonType dismiss = new ButtonType("Dismiss", ButtonType.CANCEL.getButtonData());

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Previous crash detected");
            alert.setHeaderText("A crash report from a previous run was found.");
            alert.setContentText("You can generate a support bundle that includes the crash snapshot.");
            alert.getButtonTypes().setAll(bundle, open, dismiss);
            com.fileexplorer.util.DialogTheme.apply(alert, null);
            ButtonType chosen = alert.showAndWait().orElse(dismiss);

            if (chosen == open) {
                try {
                    java.awt.Desktop.getDesktop().open(crash.toFile());
                } catch (Exception ignored) {
                }
                return;
            }
            if (chosen == bundle) {
                DiagnosticsBundleService svc = new DiagnosticsBundleService();
                java.nio.file.Path out = CrashReportService.crashDir().resolve(svc.defaultFileName());
                try {
                    svc.generate(context, out);
                    Alert ok = new Alert(Alert.AlertType.INFORMATION);
                    ok.setTitle("Support bundle generated");
                    ok.setHeaderText("Support bundle created");
                    ok.setContentText(out.toString());
                    com.fileexplorer.util.DialogTheme.apply(ok, null);
                    ok.showAndWait();
                } catch (Exception ex) {
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Support bundle failed");
                    err.setHeaderText("Could not generate support bundle");
                    err.setContentText(String.valueOf(ex));
                    com.fileexplorer.util.DialogTheme.apply(err, null);
                    err.showAndWait();
                }
            }
        } catch (Exception ignored) {
        }
    }


    /**
     * Phase 4.5.0: Prompt the user about incomplete transaction journals (crash recovery).
     */
    private static void showJournalRecoveryDialog(ExplorerContext context) {
        if (context == null) return;

        java.util.List<com.fileexplorer.service.ops.journal.OperationJournalService.RecoveryCandidate> candidates =
                context.operationQueueService().findRecoveryCandidates();
        if (candidates == null || candidates.isEmpty()) return;

        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(MainApp.class);
        String policy = prefs.get("journal.recoveryPolicy", "ASK");

        if ("AUTO_RESUME".equalsIgnoreCase(policy)) {
            for (var c : candidates) {
                context.operationQueueService().resumeFromJournal(c.operationId(), c.driftPolicy());
            }
            return;
        }
        if ("AUTO_FAIL".equalsIgnoreCase(policy)) {
            for (var c : candidates) {
                context.operationQueueService().markRecoveryFailed(c.operationId());
            }
            return;
        }
        if ("IGNORE".equalsIgnoreCase(policy)) {
            return;
        }

        int n = candidates.size();
        ButtonType resume = new ButtonType("Resume");
        ButtonType fail = new ButtonType("Mark failed");
        ButtonType ignore = new ButtonType("Ignore");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Crash recovery");
        alert.setHeaderText("Found " + n + " incomplete operation journal" + (n == 1 ? "" : "s") + " from a prior session.");

        StringBuilder detail = new StringBuilder();
        int show = Math.min(6, n);
        for (int i = 0; i < show; i++) {
            var c = candidates.get(i);
            detail.append("• ").append(c.type()).append(" (").append(c.operationId()).append(")");
            if (c.previewHash() != null && !c.previewHash().isBlank()) {
                detail.append(" — previewHash=").append(c.previewHash());
            }
            detail.append("\n");
        }
        if (n > show) {
            detail.append("…and ").append(n - show).append(" more");
        }

        javafx.scene.control.Label msg = new javafx.scene.control.Label(
                "You can resume these operations (re-run deterministically from the journal plan), mark them failed, or ignore for now.\n\n" + detail);
        msg.setWrapText(true);
        javafx.scene.control.CheckBox remember = new javafx.scene.control.CheckBox("Remember my choice");

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10, msg, remember);
        alert.getDialogPane().setContent(box);
        alert.getButtonTypes().setAll(resume, fail, ignore);
        com.fileexplorer.util.DialogTheme.apply(alert, null);
        ButtonType chosen = alert.showAndWait().orElse(ignore);
        if (chosen == resume) {
            for (var c : candidates) {
                context.operationQueueService().resumeFromJournal(c.operationId(), c.driftPolicy());
            }
            if (remember.isSelected()) prefs.put("journal.recoveryPolicy", "AUTO_RESUME");
        } else if (chosen == fail) {
            for (var c : candidates) {
                context.operationQueueService().markRecoveryFailed(c.operationId());
            }
            if (remember.isSelected()) prefs.put("journal.recoveryPolicy", "AUTO_FAIL");
        } else {
            if (remember.isSelected()) prefs.put("journal.recoveryPolicy", "IGNORE");
        }
    }



    private static final class SimpleConsoleFormatter extends Formatter {
        @Override
/**
 * format.
 *
 * @param record TODO
 * @return TODO
 */
        public String format(LogRecord record) {
            LogSupport.enter(LOG, "format");
            return record.getLevel().getName() + " " + record.getLoggerName() + " - " + formatMessage(record)
                    + System.lineSeparator();
        }
    }

}
