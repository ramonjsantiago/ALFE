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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
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

    private enum BootstrapState {
        NOT_STARTED,
        SHELL_VISIBLE,
        MAIN_FXML_LOADED,
        MAIN_UI_ATTACHED,
        STARTUP_COMPLETE,
        FAILED,
        DISPOSED
    }

    private static final String STAGE_BOOTSTRAP_STATE_KEY = "fileexplorer.bootstrap.state";
    private static final String STAGE_BOOTSTRAP_ID_KEY = "fileexplorer.bootstrap.id";
    private static final String STAGE_BOOTSTRAP_REASON_KEY = "fileexplorer.bootstrap.reason";
    private static final String STAGE_BOOTSTRAP_KIND_KEY = "fileexplorer.bootstrap.kind";
    private static final String STAGE_INITIAL_FOLDER_OPENED_KEY = "fileexplorer.bootstrap.initialFolderOpened";
    private static final AtomicLong BOOTSTRAP_SEQUENCE = new AtomicLong(0L);
    private static final AtomicBoolean PRIMARY_STAGE_BOOTSTRAPPED = new AtomicBoolean(false);
    private static final AtomicReference<Stage> PRIMARY_STAGE_REF = new AtomicReference<>();

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
configureExplorerStage(stage, initialFolder, /*darkHint*/ true, "app.start.primary");

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
        configureExplorerStage(stage, initialFolder, dark, "external.unspecified");
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
        configureExplorerStage(stage, initialFolder, dark, "external.unspecified");
    }

    public static void configureExplorerStage(Stage stage, Path initialFolder, String bootstrapReason) throws IOException {
        ThemeService themeService = new ThemeService();
        themeService.setDarkPreferred(true);
        boolean dark = themeService.isDarkPreferred();
        configureExplorerStage(stage, initialFolder, dark, bootstrapReason);
    }

    public static void configureExplorerStage(Stage stage, Path initialFolder, boolean dark, String bootstrapReason) throws IOException {

if (stage == null) {
    throw new IllegalArgumentException("stage must not be null");
}

final String normalizedReason = normalizeBootstrapReason(bootstrapReason);
logBootstrapRequest(stage, initialFolder, normalizedReason);
if (!claimBootstrap(stage, normalizedReason)) {
    StartupTrace.mark("configureExplorerStage suppressed reason=" + normalizedReason + " caller=" + summarizeBootstrapCaller());
    return;
}

StartupTrace.mark("configureExplorerStage enter");

// Build a visible window immediately (fast) and defer FXML/controller wiring by a single pulse.
// This prevents "nothing appears" when FXMLLoader/controller initialization takes time.
stage.setTitle("FileExplorer");

        // App icon is optional; defer until after first paint.
// Defer min-size constraints until after the first visible frame.
// This can shave time off the initial stage.show() on some platforms.


// Phase 4A.3+: show an ultra-minimal scene FIRST, then install the shell root on the next pulse.
// This reduces work inside stage.show() and improves time-to-visible.
final StackPane preShowRoot = new StackPane();
preShowRoot.setStyle("-fx-background-color: #121212;");
final Scene shellScene = new Scene(preShowRoot, DEFAULT_WIDTH, DEFAULT_HEIGHT);
stage.setScene(shellScene);
StartupTrace.mark("stage.setScene (shell scene)");
if (!stage.isShowing()) {
    stage.show();
}
StartupTrace.mark("stage.show (shell scene)");
markBootstrapState(stage, BootstrapState.SHELL_VISIBLE);

// Install the richer shell placeholder AFTER the first window is visible.
Platform.runLater(() -> {
    try {
        StartupTrace.mark("buildShellRoot begin");
        final Parent shellRoot = buildShellRoot();
        StartupTrace.mark("buildShellRoot end");
        shellScene.setRoot(shellRoot);
        StartupTrace.mark("shellScene.setRoot (shell root)");

        // Apply min-size constraints after first paint.
        try {
            stage.setMinWidth(DEFAULT_MIN_WIDTH);
            stage.setMinHeight(DEFAULT_MIN_HEIGHT);
        } catch (Throwable ignored2) {
        }
    } catch (Throwable ignored) {
    }
});

// Phase 4A.3: first user input signal (useful for real TTI measurement).
try {
    final java.util.concurrent.atomic.AtomicBoolean firstInput = new java.util.concurrent.atomic.AtomicBoolean(false);
    final java.util.concurrent.atomic.AtomicReference<javafx.event.EventHandler<javafx.scene.input.InputEvent>> hRef =
            new java.util.concurrent.atomic.AtomicReference<>();
    javafx.event.EventHandler<javafx.scene.input.InputEvent> h = e -> {
        if (firstInput.compareAndSet(false, true)) {
            StartupTrace.mark("first input event (shell)");
            javafx.event.EventHandler<javafx.scene.input.InputEvent> hh = hRef.get();
            if (hh != null) {
                shellScene.removeEventFilter(javafx.scene.input.InputEvent.ANY, hh);
            }
        }
    };
    hRef.set(h);
    shellScene.addEventFilter(javafx.scene.input.InputEvent.ANY, h);
} catch (Throwable ignored) {
}

// App icon is optional; defer until after first paint.
Platform.runLater(() -> {
    try {
        stage.getIcons().add(new Image(MainApp.class.getResourceAsStream("/icons/app.png")));
    } catch (Exception ignored) {
    }
});

// Approximate first paint: first FX pulse after stage.show
Platform.runLater(() -> StartupTrace.mark("FX pulse after show (runLater1)"));

// Phase 4A.2: FX-thread stall detector (opt-in).
final java.util.concurrent.atomic.AtomicReference<com.fileexplorer.perf.FxThreadStallDetector> fxStallDetectorRef = new java.util.concurrent.atomic.AtomicReference<>();
final java.util.concurrent.atomic.AtomicReference<com.fileexplorer.perf.FxHeartbeat> fxHeartbeatRef = new java.util.concurrent.atomic.AtomicReference<>();
final boolean fxStallEnabled = Boolean.getBoolean("fileexplorer.perf.fxStallDetector");
if (fxStallEnabled) {
    try {
        long pollMs = Long.parseLong(System.getProperty("fileexplorer.perf.fxStallPollMs", "10"));
        long stallMs = Long.parseLong(System.getProperty("fileexplorer.perf.fxStallMs", "100"));
        com.fileexplorer.perf.FxThreadStallDetector det =
                new com.fileexplorer.perf.FxThreadStallDetector(Thread.currentThread(),
                        java.time.Duration.ofMillis(pollMs),
                        java.time.Duration.ofMillis(stallMs));
        com.fileexplorer.perf.FxHeartbeat hb = new com.fileexplorer.perf.FxHeartbeat(det);
        det.start();
        hb.start();
        fxStallDetectorRef.set(det);
        fxHeartbeatRef.set(hb);
    } catch (Throwable ignored) {
    }
}

// Phase 3.4: allow deterministic controller teardown on window close.
final AtomicReference<MainController> mainControllerRef = new AtomicReference<>();
AtomicReference<ExplorerContext> contextRef = new AtomicReference<>();
AtomicReference<com.fileexplorer.util.HeapPressureService> heapPressureRef = new AtomicReference<>();
AtomicReference<com.fileexplorer.util.SoakNavigator> soakRef = new AtomicReference<>();
stage.setOnCloseRequest(e -> {
    markBootstrapState(stage, BootstrapState.DISPOSED);
    com.fileexplorer.perf.FxHeartbeat hb = fxHeartbeatRef.get();
    if (hb != null) {
        try { hb.stop(); } catch (Exception ignored) {}
    }
    com.fileexplorer.perf.FxThreadStallDetector det = fxStallDetectorRef.get();
    if (det != null) {
        try { det.stop(); } catch (Exception ignored) {}
    }

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
        StartupTrace.mark("FXML url resolved");
        if (fxmlUrl == null) {
            throw new IllegalStateException("Missing FXML resource: /com/fileexplorer/ui/layout/MainLayout.fxml");
        }
        StartupTrace.mark("FXMLLoader created");
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        StartupTrace.mark("FXML load enter");
        Parent root = loader.load();
        StartupTrace.mark("FXML load exit");
        markBootstrapState(stage, BootstrapState.MAIN_FXML_LOADED);

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

        // Phase 4A.3: ensure root has theme classes early so deferred CSS can apply predictably.
        try {
            if (root != null) {
                java.util.List<String> sc = root.getStyleClass();
                if (!sc.contains("explorer-root")) {
                    sc.add("explorer-root");
                }
                sc.remove("theme-dark");
                sc.remove("theme-light");
                sc.add(darkActual ? "theme-dark" : "theme-light");
            }
        } catch (Throwable ignored) {
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
	Scene scene = shellScene;

	// Phase 4A.2: variables captured by deferred lambdas must be effectively final.
	final Scene sceneForCss = scene;
	final boolean darkActualForCss = darkActual;

// Phase 4A.2 / Phase 4M: centralize post-scene startup scheduling (CSS staging, idle work budget, bounded deferral).
final com.fileexplorer.util.StartupWorkQueue workQueue = new com.fileexplorer.util.StartupWorkQueue();
workQueue.attachToScene(scene);

// Swap root in-place (single Scene) to avoid Scene churn during startup.
scene.setRoot(overlayRoot);
StartupTrace.mark("shellScene.setRoot (main root)");
markBootstrapState(stage, BootstrapState.MAIN_UI_ATTACHED);

// Mark UI ready once the main root is installed; critical startup work can now drain promptly.
workQueue.markUiReady();

// Phase 4M: optionally defer stylesheet attachment, but keep the critical layer on a bounded next-pulse queue
// so it does not drift behind user interaction by seconds.
final boolean deferCss = Boolean.parseBoolean(System.getProperty("fileexplorer.startup.deferCss", "true"));
if (!deferCss) {
    StartupTrace.mark("stylesheets attach (immediate) begin");
    attachCriticalStylesheets(scene, darkActual);
    attachDeferredStylesheets(scene);
    StartupTrace.mark("stylesheets attach (immediate) end");
} else {
    workQueue.runCritical(() -> {
        StartupTrace.mark("stylesheets attach (critical deferred) begin");
        attachCriticalStylesheets(sceneForCss, darkActualForCss);
        StartupTrace.mark("stylesheets attach (critical deferred) end");
    });

    workQueue.runIdle(() -> {
        StartupTrace.mark("stylesheets attach (idle deferred) begin");
        attachDeferredStylesheets(sceneForCss);
        StartupTrace.mark("stylesheets attach (idle deferred) end");
    });
}

        // Wire controller
        MainController controller = loader.getController();
        ProgressPaneController progressPaneController = null;
        if (controller != null) {
            mainControllerRef.set(controller);
            // Phase 3.4.4: Attach shared ExplorerContext before any scene-dependent work.
            StartupTrace.mark("controller.attach enter");
            controller.attach(context);
            StartupTrace.mark("controller.attach exit");

            // Phase 3.6.2: attach included controllers (e.g., ProgressPane)
            Object ppcObj = loader.getNamespace().get("progressPaneController");
            progressPaneController = ppcObj instanceof ProgressPaneController ppc ? ppc : null;
            if (progressPaneController != null) {
                progressPaneController.attach(context);
            }

            // Keep scene wiring minimal for fast first paint.
            StartupTrace.mark("controller.setScene");
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

        // Swap root in-place (single Scene) to avoid Scene churn during startup.
        StartupTrace.mark("shellScene.setRoot (main UI)");

        // Phase 4M: enable thumbnail decoding on the idle queue instead of immediately after root swap.
        workQueue.runIdle(() ->
                com.fileexplorer.service.icon.AsyncThumbnailService.getInstance().setEnabled(true)
        );

        // Phase 4C.1: heap pressure monitor (best-effort) to trim caches and prevent long-run creep.
        final com.fileexplorer.util.HeapPressureService heapPressure = new com.fileexplorer.util.HeapPressureService(
                Double.parseDouble(System.getProperty("fileexplorer.heapPressure.threshold", "0.85")),
                Long.parseLong(System.getProperty("fileexplorer.heapPressure.intervalMs", "2500")),
                usedFrac -> {
                    try { com.fileexplorer.service.icon.IconCacheService.getInstance().trimStale(); } catch (Throwable ignored) {}
                    try { com.fileexplorer.service.icon.AsyncThumbnailService.getInstance().trimCacheUnderPressure(); } catch (Throwable ignored) {}
                }
        );
        workQueue.runIdle(() -> {
            try { heapPressure.start(); } catch (Throwable ignored) {}
        });
        // Ensure monitor stops on close (integrated into the existing close handler).
        heapPressureRef.set(heapPressure);

        // Open the initial folder on the critical queue so the shell-to-content handoff is predictable.
        if (controller != null && initialFolder != null && claimInitialFolderOpen(stage)) {
            workQueue.runCritical(() -> controller.openInitialFolder(initialFolder));
        }

        // Phase 4C.1: optional successive-folder navigation soak runner.
        if (controller != null && Boolean.getBoolean("fileexplorer.soak.enabled")) {
            workQueue.runIdle(() -> {
                try {
                    com.fileexplorer.util.SoakNavigator sn = new com.fileexplorer.util.SoakNavigator(controller);
                    soakRef.set(sn);
                    sn.start();
                } catch (Throwable ignored) {}
            });
        }

        // Phase 4M: run heavy startup maintenance off the FX thread, and let the idle queue enforce a maximum deferral.
        final ProgressPaneController progressPaneControllerForStartup = progressPaneController;
        workQueue.runIdle(() -> runDeferredHeavyStartupTasksAsync(stage, context, controller, progressPaneControllerForStartup));
    } catch (Exception ex) {
        markBootstrapState(stage, BootstrapState.FAILED);
        // Keep the loading scene visible and surface the error.
        ex.printStackTrace();
        try {
            javafx.scene.Node n = shellScene.lookup("#shellStatus");
            if (n instanceof javafx.scene.text.Text t) {
                t.setText("Failed to load UI (see console). ");
            }
        } catch (Exception ignored) {}
}
}));
    }

    private static String normalizeBootstrapReason(String bootstrapReason) {
        if (bootstrapReason == null) {
            return "external.unspecified";
        }
        String normalized = bootstrapReason.trim();
        return normalized.isEmpty() ? "external.unspecified" : normalized;
    }

    private static void logBootstrapRequest(Stage stage, Path initialFolder, String bootstrapReason) {
        String folderText = initialFolder != null ? initialFolder.toString() : "<null>";
        StartupTrace.mark("configureExplorerStage request reason=" + bootstrapReason + " folder=" + folderText + " caller=" + summarizeBootstrapCaller());
    }

    private static boolean claimBootstrap(Stage stage, String bootstrapReason) {
        BootstrapState existingState = getBootstrapState(stage);
        if (existingState != BootstrapState.NOT_STARTED) {
            return false;
        }

        Stage primaryStage = PRIMARY_STAGE_REF.get();
        if (primaryStage == null) {
            PRIMARY_STAGE_REF.compareAndSet(null, stage);
            primaryStage = PRIMARY_STAGE_REF.get();
        }

        final boolean isPrimaryStage = primaryStage == stage;
        final String bootstrapKind;
        if (isPrimaryStage) {
            if (!PRIMARY_STAGE_BOOTSTRAPPED.compareAndSet(false, true)) {
                return false;
            }
            bootstrapKind = "primary";
        } else if (isExplicitSecondaryBootstrapReason(bootstrapReason)) {
            bootstrapKind = "secondary";
        } else {
            return false;
        }

        stage.getProperties().put(STAGE_BOOTSTRAP_ID_KEY, Long.valueOf(BOOTSTRAP_SEQUENCE.incrementAndGet()));
        stage.getProperties().put(STAGE_BOOTSTRAP_REASON_KEY, bootstrapReason);
        stage.getProperties().put(STAGE_BOOTSTRAP_KIND_KEY, bootstrapKind);
        markBootstrapState(stage, BootstrapState.NOT_STARTED);
        return true;
    }

    private static boolean isExplicitSecondaryBootstrapReason(String bootstrapReason) {
        return bootstrapReason.startsWith("user.")
                || bootstrapReason.startsWith("window.")
                || bootstrapReason.startsWith("breadcrumb.")
                || bootstrapReason.startsWith("controller.")
                || bootstrapReason.startsWith("ui.");
    }

    private static BootstrapState getBootstrapState(Stage stage) {
        if (stage == null) {
            return BootstrapState.NOT_STARTED;
        }
        Object value = stage.getProperties().get(STAGE_BOOTSTRAP_STATE_KEY);
        if (value instanceof BootstrapState state) {
            return state;
        }
        return BootstrapState.NOT_STARTED;
    }

    private static void markBootstrapState(Stage stage, BootstrapState state) {
        if (stage != null && state != null) {
            stage.getProperties().put(STAGE_BOOTSTRAP_STATE_KEY, state);
        }
    }

    private static boolean claimInitialFolderOpen(Stage stage) {
        if (stage == null) {
            return true;
        }
        Object existing = stage.getProperties().get(STAGE_INITIAL_FOLDER_OPENED_KEY);
        if (Boolean.TRUE.equals(existing)) {
            StartupTrace.mark("initial directory load suppressed duplicate");
            return false;
        }
        stage.getProperties().put(STAGE_INITIAL_FOLDER_OPENED_KEY, Boolean.TRUE);
        return true;
    }

    private static String summarizeBootstrapCaller() {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : trace) {
            if (element == null) {
                continue;
            }
            String className = element.getClassName();
            if (className == null
                    || className.equals(Thread.class.getName())
                    || className.equals(MainApp.class.getName())
                    || className.startsWith("javafx.application.Platform")
                    || className.startsWith("com.sun.javafx")
                    || className.startsWith("jdk.internal.reflect")
                    || className.startsWith("java.lang.reflect")) {
                continue;
            }
            return className + "#" + element.getMethodName() + ":" + element.getLineNumber();
        }
        return "unknown";
    }



    private static void attachCriticalStylesheets(Scene scene, boolean darkActual) {
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-base.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-win11.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-fluent.css");
        addStylesheet(scene, darkActual ? "/com/fileexplorer/ui/css/explorer-dark-win.css" : "/com/fileexplorer/ui/css/explorer-light-win.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-win.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/ui_fixes.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/fluent-explorer.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-override-everything.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/window-chrome-parity.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/address-command-parity.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/navigation-pane-parity.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/selection-state-tokens.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/details-view-parity.css");
    }

    private static void attachDeferredStylesheets(Scene scene) {
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-table.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/progress_pane.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/side-pane-parity.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/context-menu-parity.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/home-tabs-parity.css");
    }

    private static void runDeferredHeavyStartupTasksAsync(
            Stage stage,
            ExplorerContext context,
            MainController controller,
            ProgressPaneController progressPaneController
    ) {
        CompletableFuture.runAsync(() -> {
            StartupTrace.mark("deferred heavy startup background begin");

            String selfCheckReport = null;
            Integer recoveredOps = null;

            try {
                if (progressPaneController != null) {
                    try {
                        StartupSelfCheckService.SelfCheckResult result = new StartupSelfCheckService().run(context);
                        if (result != null && result.hadIssues()) {
                            selfCheckReport = result.report();
                        }
                    } catch (Exception ignored) {
                    }

                    if (!isSafeMode()) {
                        try {
                            int recovered = context.operationQueueService().restoreSavedQueue();
                            if (recovered > 0) {
                                recoveredOps = recovered;
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    try {
                        context.operationQueueService().scanForOrphanTempFiles();
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                final String selfCheckReportFinal = selfCheckReport;
                final Integer recoveredOpsFinal = recoveredOps;
                Platform.runLater(() -> {
                    try {
                        if (selfCheckReportFinal != null && !selfCheckReportFinal.isBlank()) {
                            showSelfCheckDialog(selfCheckReportFinal);
                        }
                        if (recoveredOpsFinal != null) {
                            showRecoveredOpsDialog(context, recoveredOpsFinal.intValue());
                        }
                        if (progressPaneController != null) {
                            showJournalRecoveryDialog(context);
                        }
                        if (controller != null && Boolean.getBoolean("fileexplorer.safeMode")) {
                            controller.enterSafeMode();
                        }
                        showLastCrashDialogIfPresent(context);
                        if (controller != null) {
                            controller.releaseStartupVirtualizationGuards();
                        }
                    } finally {
                        CrashReportService.writeSuccessMarker();
                        markBootstrapState(stage, BootstrapState.STARTUP_COMPLETE);
                        StartupTrace.mark("deferred heavy startup tasks done");
                    }
                });
            }
        });
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

    /**
     * Builds a lightweight placeholder UI that is safe to show while the full FXML UI is loading.
     *
     * IMPORTANT: This intentionally avoids JavaFX Controls/Skins and avoids attaching the main
     * stylesheet stack. We only use shapes/text to minimize additional initialization work.
     */
    private static Parent buildShellRoot() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #242424, #181818);");

        VBox shell = new VBox(8);
        shell.setFillWidth(true);
        root.setCenter(shell);

        HBox titleStrip = new HBox(8);
        titleStrip.setAlignment(Pos.CENTER_LEFT);
        titleStrip.setPadding(new Insets(2, 4, 0, 4));

        StackPane appBadge = new StackPane();
        Rectangle appBadgeBg = new Rectangle(18, 18);
        appBadgeBg.setArcWidth(5);
        appBadgeBg.setArcHeight(5);
        appBadgeBg.setFill(Color.web("#e7b64b"));
        Text appBadgeGlyph = new Text("⌂");
        appBadgeGlyph.setFill(Color.web("#1d1d1d"));
        appBadgeGlyph.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        appBadge.getChildren().addAll(appBadgeBg, appBadgeGlyph);

        Text title = new Text("FileExplorer");
        title.setFill(Color.web("#e4e4e4"));
        title.setStyle("-fx-font-size: 13px;");

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, javafx.scene.layout.Priority.ALWAYS);

        HBox captions = new HBox(0);
        captions.setAlignment(Pos.CENTER_RIGHT);
        captions.getChildren().addAll(
                buildShellCaptionButton(42, 28, "#2a2a2a", "—", "#d5d5d5"),
                buildShellCaptionButton(42, 28, "#2a2a2a", "▢", "#d5d5d5"),
                buildShellCaptionButton(48, 28, "#c42b1c", "×", "#191919")
        );

        titleStrip.getChildren().addAll(appBadge, title, titleSpacer, captions);

        VBox topChrome = new VBox(0);
        topChrome.setStyle("-fx-background-color: linear-gradient(to bottom, #2b2b2b, #1b1b1b);"
                + "-fx-background-radius: 12; -fx-border-radius: 12;"
                + "-fx-border-color: #3a3a3a; -fx-border-width: 1;");

        BorderPane addressRow = new BorderPane();
        addressRow.setMinHeight(52);
        addressRow.setPrefHeight(52);
        addressRow.setPadding(new Insets(8, 10, 2, 10));

        HBox nav = new HBox(6);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.getChildren().addAll(
                buildShellGlyphButton("←"),
                buildShellGlyphButton("→"),
                buildShellGlyphButton("↑"),
                buildShellGlyphButton("↻")
        );

        HBox pathHost = new HBox(6);
        pathHost.setAlignment(Pos.CENTER_LEFT);
        pathHost.getChildren().addAll(
                buildShellPill(76, 32, "#242424", "#3b3b3b", "Home", "#d9d9d9"),
                buildShellChevron(),
                buildShellPill(92, 32, "#242424", "#3b3b3b", "Documents", "#d9d9d9")
        );

        StackPane searchHost = new StackPane();
        Rectangle searchBg = new Rectangle(250, 36);
        searchBg.setArcWidth(18);
        searchBg.setArcHeight(18);
        searchBg.setFill(Color.web("#111111"));
        searchBg.setStroke(Color.web("#3a3a3a"));
        Text searchText = new Text("Search");
        searchText.setFill(Color.web("#8a8a8a"));
        searchText.setStyle("-fx-font-size: 12px;");
        StackPane.setAlignment(searchText, Pos.CENTER_LEFT);
        searchHost.setPadding(new Insets(0, 0, 0, 14));
        searchHost.getChildren().addAll(searchBg, searchText);

        addressRow.setLeft(nav);
        addressRow.setCenter(pathHost);
        addressRow.setRight(searchHost);

        Rectangle chromeDivider = new Rectangle();
        chromeDivider.setHeight(1);
        chromeDivider.setFill(Color.web("#343434"));
        chromeDivider.widthProperty().bind(topChrome.widthProperty().subtract(2));

        HBox commandRow = new HBox(8);
        commandRow.setAlignment(Pos.CENTER_LEFT);
        commandRow.setPadding(new Insets(6, 10, 8, 10));
        commandRow.getChildren().addAll(
                buildShellPill(74, 34, "#2a2a2a", "#404040", "New", "#ebebeb"),
                buildShellCommandDivider(),
                buildShellPill(62, 34, "#202020", "#2d2d2d", "Cut", "#d6d6d6"),
                buildShellPill(66, 34, "#202020", "#2d2d2d", "Copy", "#d6d6d6"),
                buildShellPill(68, 34, "#202020", "#2d2d2d", "Paste", "#d6d6d6"),
                buildShellCommandDivider(),
                buildShellPill(68, 34, "#202020", "#2d2d2d", "View", "#d6d6d6")
        );

        topChrome.getChildren().addAll(addressRow, chromeDivider, commandRow);

        BorderPane contentSurface = new BorderPane();
        contentSurface.setStyle("-fx-background-color: linear-gradient(to bottom, #1c1c1c, #171717);"
                + "-fx-background-radius: 12; -fx-border-radius: 12;"
                + "-fx-border-color: #333333; -fx-border-width: 1;");
        contentSurface.setPadding(new Insets(1));

        HBox content = new HBox(8);
        content.setPadding(new Insets(0));

        Rectangle leftPane = new Rectangle(272, 520);
        leftPane.setArcWidth(12);
        leftPane.setArcHeight(12);
        leftPane.setFill(Color.web("#161616"));
        leftPane.setStroke(Color.web("#2d2d2d"));

        VBox rightPane = new VBox(8);
        rightPane.setPadding(new Insets(0));
        Rectangle headerPane = new Rectangle(780, 34);
        headerPane.setArcWidth(10);
        headerPane.setArcHeight(10);
        headerPane.setFill(Color.web("#202020"));
        headerPane.setStroke(Color.web("#313131"));
        Rectangle bodyPane = new Rectangle(780, 476);
        bodyPane.setArcWidth(12);
        bodyPane.setArcHeight(12);
        bodyPane.setFill(Color.web("#151515"));
        bodyPane.setStroke(Color.web("#2d2d2d"));
        rightPane.getChildren().addAll(headerPane, bodyPane);
        HBox.setHgrow(rightPane, javafx.scene.layout.Priority.ALWAYS);

        content.getChildren().addAll(leftPane, rightPane);
        contentSurface.setCenter(content);

        BorderPane statusBar = new BorderPane();
        statusBar.setMinHeight(52);
        statusBar.setPrefHeight(52);
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        statusBar.setStyle("-fx-background-color: linear-gradient(to bottom, #1c1c1c, #171717);"
                + "-fx-background-radius: 10; -fx-border-radius: 10;"
                + "-fx-border-color: #323232; -fx-border-width: 1;");

        Text status = new Text("Loading file explorer UI…");
        status.setId("shellStatus");
        status.setFill(Color.web("#d5d5d5"));
        status.setStyle("-fx-font-size: 12px;");
        statusBar.setLeft(status);

        HBox statusRight = new HBox(6);
        statusRight.setAlignment(Pos.CENTER_RIGHT);
        statusRight.getChildren().addAll(
                buildShellPill(66, 30, "#202020", "#303030", "Details", "#d6d6d6"),
                buildShellPill(56, 30, "#202020", "#303030", "Large", "#d6d6d6")
        );
        statusBar.setRight(statusRight);

        shell.getChildren().addAll(titleStrip, topChrome, contentSurface, statusBar);
        return root;
    }

    private static StackPane buildShellGlyphButton(String glyph) {
        StackPane button = new StackPane();
        Rectangle bg = new Rectangle(34, 34);
        bg.setArcWidth(8);
        bg.setArcHeight(8);
        bg.setFill(Color.web("#242424"));
        bg.setStroke(Color.web("#343434"));
        Text text = new Text(glyph);
        text.setFill(Color.web("#e5e5e5"));
        text.setStyle("-fx-font-size: 13px;");
        button.getChildren().addAll(bg, text);
        return button;
    }

    private static StackPane buildShellPill(double width, double height, String fill, String stroke, String label, String textFill) {
        StackPane pill = new StackPane();
        Rectangle bg = new Rectangle(width, height);
        bg.setArcWidth(Math.min(height, 18));
        bg.setArcHeight(Math.min(height, 18));
        bg.setFill(Color.web(fill));
        bg.setStroke(Color.web(stroke));
        Text text = new Text(label);
        text.setFill(Color.web(textFill));
        text.setStyle("-fx-font-size: 12px;");
        pill.getChildren().addAll(bg, text);
        return pill;
    }

    private static StackPane buildShellChevron() {
        StackPane chevron = new StackPane();
        chevron.setMinSize(10, 32);
        Text text = new Text("›");
        text.setFill(Color.web("#8f8f8f"));
        text.setStyle("-fx-font-size: 12px;");
        chevron.getChildren().add(text);
        return chevron;
    }

    private static Region buildShellCommandDivider() {
        Region divider = new Region();
        divider.setMinSize(1, 22);
        divider.setPrefSize(1, 22);
        divider.setMaxSize(1, 22);
        divider.setStyle("-fx-background-color: #343434;");
        return divider;
    }

    private static StackPane buildShellCaptionButton(double width, double height, String fill, String glyph, String textFill) {
        StackPane button = new StackPane();
        Rectangle bg = new Rectangle(width, height);
        bg.setFill(Color.web(fill));
        Text text = new Text(glyph);
        text.setFill(Color.web(textFill));
        text.setStyle("-fx-font-size: 12px;");
        button.getChildren().addAll(bg, text);
        return button;
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
