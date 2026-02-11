package com.fileexplorer.app;

import com.fileexplorer.controller.MainController;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import com.fileexplorer.util.LogSupport;
import javafx.geometry.Insets;
import java.util.concurrent.atomic.AtomicReference;
import com.fileexplorer.lifecycle.Lifecycle;
import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.filesystem.FileMetadataService;
import com.fileexplorer.service.filesystem.TreeBuildService;
import com.fileexplorer.service.icon.IconCacheService;

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

    private static boolean isSafeMode() {
        return Boolean.getBoolean("fileexplorer.safeMode");
    }

    private static boolean isResourceAuditEnabled() {
        // Safe mode forces resource audit off to keep startup as minimal as possible.
        if (isSafeMode()) {
            return false;
        }
        return Boolean.getBoolean("fileexplorer.resourceAudit");
    }


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
    public void start(Stage stage) throws Exception {

        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("heapdumps"));
        } catch (Exception ignore) {
        }

        LogSupport.enter(LOG, "start");
        // Replace Modena user-agent stylesheet with a minimal UA CSS (data URL) to avoid JavaFX 25 Linux CSS conversion warnings.
        javafx.application.Application.setUserAgentStylesheet("data:text/css,.scroll-bar%20.increment-arrow,%20.scroll-bar%20.decrement-arrow%20{%20-fx-effect:%20null;%20}%20.scroll-bar%20.increment-button,%20.scroll-bar%20.decrement-button%20{%20-fx-effect:%20null;%20}%20.scroll-bar%20.thumb,%20.scroll-bar%20.track%20{%20-fx-effect:%20null;%20}%20.table-view%20.column-header-background,%20.table-view%20.filler,%20.table-view%20.show-hide-columns-button,%20.table-view%20.show-hide-column-image,%20.table-view%20.column-drag-header,%20.table-view%20.column-resize-line,%20.tree-view%20.corner,%20.table-view%20.corner,%20.scroll-pane%20.corner,%20.tree-view%20.virtual-flow%20.corner,%20.table-view%20.virtual-flow%20.corner,%20.scroll-pane%20>%20.corner,%20.scroll-pane%20>%20.viewport%20{%20-fx-background-color:%20transparent;%20-fx-effect:%20null;%20}");
        configureResourceLoggerIfNeeded();
        Path initialFolder = null;
        ThemeService themeService = new ThemeService();
        themeService.setDarkPreferred(true);
        boolean dark = themeService.isDarkPreferred();
        configureExplorerStage(stage, initialFolder, dark);
        LogSupport.enter(LOG, "end");
    }

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

    public static void configureExplorerStage(Stage stage, Path initialFolder) throws IOException {
        LogSupport.enter(LOG, "configureExplorerStage");
        ThemeService themeService = new ThemeService();
        themeService.setDarkPreferred(true);
        boolean dark = themeService.isDarkPreferred();
        configureExplorerStage(stage, initialFolder, dark);
        LogSupport.enter(LOG, "end");
    }

    public static void configureExplorerStage(Stage stage, Path initialFolder, boolean dark) throws IOException {

if (stage == null) {
    throw new IllegalArgumentException("stage must not be null");
}

// Build a visible window immediately (fast) and defer FXML/controller wiring by a single pulse.
// This prevents "nothing appears" when FXMLLoader/controller initialization takes time.
stage.setTitle("FileExplorer");
try {
    stage.getIcons().add(new Image(MainApp.class.getResourceAsStream("/icons/app.png")));
} catch (Exception ignored) {
    // icon is optional
}

stage.setMinWidth(DEFAULT_MIN_WIDTH);
stage.setMinHeight(DEFAULT_MIN_HEIGHT);

// Lightweight loading UI
javafx.scene.control.ProgressIndicator pi = new javafx.scene.control.ProgressIndicator();
pi.setMaxSize(64, 64);
javafx.scene.control.Label lbl = new javafx.scene.control.Label("Loading...");
lbl.setStyle("-fx-font-size: 14px;");
javafx.scene.layout.VBox loadingBox = new javafx.scene.layout.VBox(12, pi, lbl);
loadingBox.setStyle("-fx-alignment: center; -fx-padding: 24;");
javafx.scene.layout.StackPane loadingRoot = new javafx.scene.layout.StackPane(loadingBox);
loadingRoot.getStyleClass().add("explorer-root");

Scene loadingScene = new Scene(loadingRoot, DEFAULT_WIDTH, DEFAULT_HEIGHT);

// Base styles early so at least the loading screen looks correct.
String baseCss = MainApp.class.getResource("/com/fileexplorer/ui/css/explorer-base.css") != null
        ? MainApp.class.getResource("/com/fileexplorer/ui/css/explorer-base.css").toExternalForm()
        : null;
if (baseCss != null) {
    loadingScene.getStylesheets().add(baseCss);
}

// User-agent overrides are optional; we keep the default MODENA UA stylesheet.

stage.setScene(loadingScene);
stage.show();

// Phase 3.4: allow deterministic controller teardown on window close.
final AtomicReference<MainController> mainControllerRef = new AtomicReference<>();
AtomicReference<ExplorerContext> contextRef = new AtomicReference<>();
stage.setOnCloseRequest(e -> {
    MainController c = mainControllerRef.get();
    if (c != null) {
        try {
            c.dispose();
        } catch (Exception ignored) {
        }
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
javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(75));
delay.setOnFinished(evt -> {
    try {
        java.net.URL fxmlUrl = MainApp.class.getResource("/com/fileexplorer/ui/layout/MainLayout.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("Missing FXML resource: /com/fileexplorer/ui/layout/MainLayout.fxml");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        // Phase 3.4.4: MainApp owns the ExplorerContext (single instance).
        ThemeService themeService = new ThemeService();
        FileMetadataService fileMetadataService = new FileMetadataService();
        TreeBuildService treeBuildService = new TreeBuildService();
        EventBus eventBus = new EventBus();
        ExplorerContext context = new ExplorerContext(
                themeService,
                fileMetadataService,
                IconCacheService.getInstance(),
                treeBuildService,
                eventBus
        );
        contextRef.set(context);
        ZoomRoot zoomRoot = new ZoomRoot(root);
        Scene scene = new Scene(zoomRoot.getRoot(), DEFAULT_WIDTH, DEFAULT_HEIGHT);

        if (baseCss != null) {
            scene.getStylesheets().add(baseCss);
        }
        addStylesheet(scene, dark ? "/com/fileexplorer/ui/css/explorer-dark-win.css" : "/com/fileexplorer/ui/css/explorer-light-win.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-win.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-table.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/ui_fixes.css");
        addStylesheet(scene, dark ? "/com/fileexplorer/ui/css/fluent-dark.css" : "/com/fileexplorer/ui/css/fluent-light.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/fluent-explorer.css");
        addStylesheet(scene, "/com/fileexplorer/ui/css/explorer-override-everything.css");

        // Wire controller
        MainController controller = loader.getController();
        if (controller != null) {
            mainControllerRef.set(controller);
            // Phase 3.4.4: Attach shared ExplorerContext before any scene-dependent work.
            controller.attach(context);
            controller.setScene(scene);

            if (Boolean.getBoolean("fileexplorer.safeMode")) {
                controller.enterSafeMode();
            }

            if (initialFolder != null) {
                controller.openInitialFolder(initialFolder);
            }
        }

        stage.setScene(scene);

        // Give controller a chance to release any startup guards after the first real scene is installed.
        if (controller != null) {
            javafx.application.Platform.runLater(controller::releaseStartupVirtualizationGuards);
        }
    } catch (Exception ex) {
        // Keep the loading scene visible and surface the error.
        ex.printStackTrace();
        lbl.setText("Failed to load UI (see console)."
        );
    }
});
delay.play();
    }

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

    private static String appendBaseStyle(String existingStyle, String fontFamilyCss, double fontPx) {
        LogSupport.enter(LOG, "appendBaseStyle");
        String base = (existingStyle == null) ? "" : existingStyle.trim();
        String sep = base.isEmpty() ? "" : (base.endsWith(";") ? " " : "; ");
        return base + sep + fontFamilyCss + " " + buildFontSizeCss(fontPx);
    }

    private static String buildFontSizeCss(double fontPx) {
        LogSupport.enter(LOG, "buildFontSizeCss");
        double clamped = clamp(fontPx, MIN_FONT_PX, MAX_FONT_PX);
        return "-fx-font-size: " + clamped + "px;";
    }

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

    private static Rectangle2D safeVisualBounds(Rectangle2D b) {
        LogSupport.enter(LOG, "safeVisualBounds");
        if (b == null) {
            return Screen.getPrimary().getVisualBounds();
        }
        double w = Math.max(1.0, b.getWidth());
        double h = Math.max(1.0, b.getHeight());
        return new Rectangle2D(b.getMinX(), b.getMinY(), w, h);
    }

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

    private static Rectangle2D getTargetVisualBoundsForStartup(Stage stage) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStartup");
        if (stage != null) {
            return getTargetVisualBoundsForStage(stage);
        }
        return getTargetVisualBoundsForStartup();
    }

    private static Rectangle2D getTargetVisualBoundsForStageSafe(Stage stage) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafe");
        if (stage != null) {
            return getTargetVisualBoundsForStage(stage);
        }
        return Screen.getPrimary().getVisualBounds();
    }

    private static Rectangle2D getTargetVisualBoundsForStage(Stage stage, boolean safe) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStage");
        if (!safe) {
            return getTargetVisualBoundsForStage(stage);
        }
        return getTargetVisualBoundsForStageSafe(stage);
    }

    private static Rectangle2D getTargetVisualBoundsForStageSafeIfNull(Stage stage) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafeIfNull");
        if (stage == null) {
            return Screen.getPrimary().getVisualBounds();
        }
        return getTargetVisualBoundsForStage(stage);
    }

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

    private static Rectangle2D getTargetVisualBoundsForStage(Stage stage, Rectangle2D fallback, boolean safe) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStage");
        if (!safe) {
            return getTargetVisualBoundsForStage(stage, fallback);
        }
        return getTargetVisualBoundsForStageSafe(stage, fallback);
    }

    private static Rectangle2D getTargetVisualBoundsForStageSafeIfNull(Stage stage, Rectangle2D fallback) {
        LogSupport.enter(LOG, "getTargetVisualBoundsForStageSafeIfNull");
        if (stage == null) {
            return fallback != null ? fallback : Screen.getPrimary().getVisualBounds();
        }
        return getTargetVisualBoundsForStage(stage, fallback);
    }

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

    private static String safeString(String v) {
        LogSupport.enter(LOG, "safeString");
        return v == null ? "" : v;
    }

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

    public static void main(String[] args) {
        printJvmDiagnosticsOnce("main");
        LogSupport.enter(LOG, "main");
        launch(args);
    }

    private static final class SimpleConsoleFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            LogSupport.enter(LOG, "format");
            return record.getLevel().getName() + " " + record.getLoggerName() + " - " + formatMessage(record)
                    + System.lineSeparator();
        }
    }

}
