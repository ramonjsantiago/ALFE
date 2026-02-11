package com.fileexplorer.controller.breadcrumb;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

/**
 * Breadcrumb bar controller.
 * <p>
 * This controller is designed to work with BreadcrumbBar.fxml that declares:
 *  - fx:id="crumbScroll" (ScrollPane)
 *  - fx:id="crumbContainer" (HBox) as the ScrollPane content
 *  - fx:id="addressField" (TextField) for address mode
 *  - fx:id="dropdownButton" (Button) for overflow/actions
 */
public final class BreadcrumbController {

    private static final Logger LOG = Logger.getLogger(BreadcrumbController.class.getName());

    private static final String STYLE_INVALID = "address-invalid";

    @FXML
    private HBox root;

    /**
     * Must match BreadcrumbBar.fxml: fx:id="crumbScroll"
     */
    @FXML
    private ScrollPane crumbScroll;

    /**
     * Explicitly declared by BreadcrumbBar.fxml as the ScrollPane content.
     * If older FXML omits it, we create it at runtime.
     */
    @FXML
    private HBox crumbContainer;

    @FXML
    private TextField addressField;

    @FXML
    private Button dropdownButton;

    private Path currentPath;

    private Consumer<Path> onNavigate;
    private Consumer<Path> onOpenInNewWindow;
    private Consumer<Path> onCopyAddress;
    private Runnable onBrowseNetwork;

    private boolean addressMode;

    @FXML
    private void initialize() {
        LogSupport.enter(LOG, "initialize");
        if (root != null) {
            root.getStyleClass().add("breadcrumb-bar");
            root.setPadding(Insets.EMPTY);
        }

        ensureCrumbInfrastructure();

        if (dropdownButton != null) {
            dropdownButton.setOnMouseClicked(evt -> {
                if (evt.getButton() == MouseButton.PRIMARY && !addressMode) {
                    showOverflowMenu();
                }
            });
        }

        if (addressField != null) {
            addressField.setOnAction(_ -> commitAddress());
            addressField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleAddressKeys);
            addressField.textProperty().addListener((_, _, _) -> clearInvalidState());
            addressField.focusedProperty().addListener((_, _, focused) -> {
                if (Boolean.FALSE.equals(focused) && addressMode) {
                    exitAddressMode(false);
                }
            });
        }

        if (currentPath == null) {
            currentPath = preferredHomeDirectoryPath();
        }

        setAddressMode(false);
    }

    // ---------------------------------------------------------------------
    // Public API (used by MainController)
    // ---------------------------------------------------------------------

    public void setOnNavigate(Consumer<Path> onNavigate) {
        LogSupport.enter(LOG, "setOnNavigate");
        this.onNavigate = onNavigate;
    }

    public void setOnOpenInNewWindow(Consumer<Path> onOpenInNewWindow) {
        LogSupport.enter(LOG, "setOnOpenInNewWindow");
        this.onOpenInNewWindow = onOpenInNewWindow;
    }

    public void setOnCopyAddress(Consumer<Path> onCopyAddress) {
        LogSupport.enter(LOG, "setOnCopyAddress");
        this.onCopyAddress = onCopyAddress;
    }

    public void setOnBrowseNetwork(Runnable onBrowseNetwork) {
        LogSupport.enter(LOG, "setOnBrowseNetwork");
        this.onBrowseNetwork = onBrowseNetwork;
    }

    public void setPath(Path path) {
        LogSupport.enter(LOG, "setPath");
        this.currentPath = (path != null) ? path.normalize() : preferredHomeDirectoryPath();
        if (addressMode) {
            syncAddressText();
        } else {
            rebuildCrumbs();
        }
    }

    public void requestAddressFocus() {
        LogSupport.enter(LOG, "requestAddressFocus");
        enterAddressMode();
    }

    public void exitAddressMode() {
        LogSupport.enter(LOG, "exitAddressMode");
        exitAddressMode(false);
    }

    // ---------------------------------------------------------------------
    // Modes
    // ---------------------------------------------------------------------

    private void enterAddressMode() {
        LogSupport.enter(LOG, "enterAddressMode");
        if (addressField == null) {
            return;
        }
        setAddressMode(true);
        syncAddressText();
        addressField.requestFocus();
        addressField.selectAll();
    }

    private void exitAddressMode(boolean keepFocusInAddress) {
        LogSupport.enter(LOG, "exitAddressMode");
        setAddressMode(false);
        if (!keepFocusInAddress && crumbScroll != null) {
            crumbScroll.requestFocus();
        }
    }

    private void setAddressMode(boolean enable) {
        LogSupport.enter(LOG, "setAddressMode");
        addressMode = enable;

        if (crumbScroll != null) {
            crumbScroll.setVisible(!enable);
            crumbScroll.setManaged(!enable);
        }

        if (addressField != null) {
            addressField.setVisible(enable);
            addressField.setManaged(enable);
        }

        if (dropdownButton != null) {
            dropdownButton.setVisible(!enable);
            dropdownButton.setManaged(!enable);
        }

        if (!enable) {
            rebuildCrumbs();
        }
    }

    // ---------------------------------------------------------------------
    // Address handling
    // ---------------------------------------------------------------------

    private void handleAddressKeys(KeyEvent evt) {
        LogSupport.enter(LOG, "handleAddressKeys");
        if (evt == null) {
            return;
        }

        if (evt.getCode() == KeyCode.ESCAPE) {
            evt.consume();
            exitAddressMode(false);
            return;
        }

        if (evt.getCode() == KeyCode.ENTER) {
            evt.consume();
            commitAddress();
        }
    }

    private void commitAddress() {
        LogSupport.enter(LOG, "commitAddress");
        if (addressField == null) {
            return;
        }

        String raw = addressField.getText();
        ResolvedAddress resolved = resolveUserAddressText(raw);

        if (!resolved.isValidDirectory) {
            markInvalidAndKeepFocus();
            return;
        }

        clearInvalidState();

        if (onNavigate != null) {
            onNavigate.accept(resolved.path);
        }

        exitAddressMode(false);
    }

    private void syncAddressText() {
        LogSupport.enter(LOG, "syncAddressText");
        if (addressField == null) {
            return;
        }
        if (currentPath == null) {
            addressField.setText("");
            return;
        }
        addressField.setText(normalizeForDisplay(currentPath));
        addressField.positionCaret(addressField.getText().length());
    }

    private void markInvalidAndKeepFocus() {
        LogSupport.enter(LOG, "markInvalidAndKeepFocus");
        if (addressField == null) {
            return;
        }
        if (!addressField.getStyleClass().contains(STYLE_INVALID)) {
            addressField.getStyleClass().add(STYLE_INVALID);
        }
        addressField.requestFocus();
        addressField.selectAll();
    }

    private void clearInvalidState() {
        LogSupport.enter(LOG, "clearInvalidState");
        if (addressField == null) {
            return;
        }
        addressField.getStyleClass().remove(STYLE_INVALID);
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    private void ensureCrumbInfrastructure() {
        LogSupport.enter(LOG, "ensureCrumbInfrastructure");
        if (crumbContainer == null) {
            HBox box = new HBox();
            box.setSpacing(6.0);
            box.setFillHeight(true);
            box.getStyleClass().add("breadcrumb-crumb-container");
            crumbContainer = box;
        }

        if (crumbScroll != null) {
            if (crumbScroll.getContent() == null) {
                crumbScroll.setContent(crumbContainer);
            } else if (crumbScroll.getContent() instanceof HBox hb) {
                crumbContainer = hb;
            }
        }
    }

    private void rebuildCrumbs() {
        LogSupport.enter(LOG, "rebuildCrumbs");
        ensureCrumbInfrastructure();

        if (crumbContainer == null) {
            return;
        }

        crumbContainer.getChildren().clear();

        if (currentPath == null) {
            return;
        }

        List<Path> segments = buildSegments(currentPath);

        boolean first = true;
        for (Path segment : segments) {
            if (!first) {
                Label sep = new Label(">");
                sep.getStyleClass().add("breadcrumb-separator");
                crumbContainer.getChildren().add(sep);
            }
            first = false;

            Button button = new Button(labelFor(segment));
            button.getStyleClass().add("breadcrumb-button");
            button.setFocusTraversable(false);

            button.setOnAction(_ -> {
                if (onNavigate != null) {
                    onNavigate.accept(segment);
                }
            });

            button.setOnMouseClicked(evt -> {
                if (evt.getButton() == MouseButton.SECONDARY) {
                    showItemContextMenu(button, segment, evt.getScreenX(), evt.getScreenY());
                    return;
                }
                if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) {
                    enterAddressMode();
                }
            });

            crumbContainer.getChildren().add(button);
        }

        if (crumbScroll != null) {
            crumbScroll.applyCss();
            crumbScroll.layout();
            crumbScroll.setHvalue(1.0);
        }
    }

    private void showOverflowMenu() {
        LogSupport.enter(LOG, "showOverflowMenu");
        if (dropdownButton == null) {
            return;
        }

        ContextMenu menu = new ContextMenu();

        if (currentPath != null) {
            MenuItem copyAddress = new MenuItem("Copy address");
            copyAddress.setOnAction(_ -> {
                if (onCopyAddress != null) {
                    onCopyAddress.accept(currentPath);
                }
            });
            menu.getItems().add(copyAddress);
        }

        if (onBrowseNetwork != null) {
            if (!menu.getItems().isEmpty()) {
                menu.getItems().add(new SeparatorMenuItem());
            }
            MenuItem browseNetwork = new MenuItem("Browse network");
            browseNetwork.setOnAction(_ -> onBrowseNetwork.run());
            menu.getItems().add(browseNetwork);
        }

        menu.show(dropdownButton, Side.BOTTOM, 0, 0);
    }

    private void showItemContextMenu(Button owner, Path path, double screenX, double screenY) {
        LogSupport.enter(LOG, "showItemContextMenu");
        Objects.requireNonNull(owner, "owner");

        ContextMenu menu = new ContextMenu();

        MenuItem openInNewWindow = new MenuItem("Open in new window");
        openInNewWindow.setOnAction(_ -> {
            if (onOpenInNewWindow != null) {
                onOpenInNewWindow.accept(path);
            }
        });

        MenuItem copyAddress = new MenuItem("Copy address");
        copyAddress.setOnAction(_ -> {
            if (onCopyAddress != null) {
                onCopyAddress.accept(path);
            }
        });

        menu.getItems().addAll(openInNewWindow, copyAddress);

        if (onBrowseNetwork != null) {
            menu.getItems().add(new SeparatorMenuItem());
            MenuItem browseNetwork = new MenuItem("Browse network");
            browseNetwork.setOnAction(_ -> onBrowseNetwork.run());
            menu.getItems().add(browseNetwork);
        }

        menu.show(owner, screenX, screenY);
    }

    // ---------------------------------------------------------------------
    // Path resolution helpers
    // ---------------------------------------------------------------------

    private record ResolvedAddress(Path path, boolean isValidDirectory) {
        private ResolvedAddress {
            LogSupport.enter(LOG, "ResolvedAddress");
        }
        }

    private ResolvedAddress resolveUserAddressText(String text) {
        LogSupport.enter(LOG, "resolveUserAddressText");
        String t = stripWrappingQuotes(text);
        if (t.isBlank()) {
            Path home = preferredHomeDirectoryPath();
            return new ResolvedAddress(home, isDirectory(home));
        }

        Path p = resolveTildePath(t);
        if (p == null) {
            try {
                p = Path.of(t);
            } catch (RuntimeException ex) {
                return new ResolvedAddress(preferredHomeDirectoryPath(), false);
            }
        }

        Path alias = resolveShellAlias(t);
        if (alias != null) {
            p = alias;
        }

        p = p.normalize();

        boolean ok = isDirectory(p);
        return new ResolvedAddress(p, ok);
    }

    private Path resolveTildePath(String t) {
        LogSupport.enter(LOG, "resolveTildePath");
        if (t == null) {
            return null;
        }
        String s = t.trim();
        if (!s.startsWith("~")) {
            return null;
        }

        Path home = preferredHomeDirectoryPath();
        if (s.equals("~")) {
            return home;
        }

        if (s.startsWith("~/") || s.startsWith("~\\")) {
            String rest = s.substring(2);
            return home.resolve(rest);
        }

        return null;
    }

    private Path resolveShellAlias(String raw) {
        LogSupport.enter(LOG, "resolveShellAlias");
        if (raw == null) {
            return null;
        }
        String key = normalizeAliasKey(raw);
        if (key.isBlank()) {
            return null;
        }

        Path base = shellBasePath();
        if (base == null) {
            return null;
        }

        return switch (key) {
            case "home" -> preferredHomeDirectoryPath();
            case "root" -> base.getRoot();
            default -> null;
        };
    }

    private static String normalizeAliasKey(String raw) {
        LogSupport.enter(LOG, "normalizeAliasKey");
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replace('\\', '/');
        int slash = indexOfFirstSlash(s);
        if (slash > 0) {
            s = s.substring(0, slash);
        }
        return s;
    }

    private static int indexOfFirstSlash(String s) {
        LogSupport.enter(LOG, "indexOfFirstSlash");
        if (s == null) {
            return -1;
        }
        int a = s.indexOf('/');
        int b = s.indexOf('\\');
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private Path shellBasePath() {
        LogSupport.enter(LOG, "shellBasePath");
        try {
            return FileSystems.getDefault().getRootDirectories().iterator().next();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean isDirectory(Path p) {
        LogSupport.enter(LOG, "isDirectory");
        if (p == null) {
            return false;
        }
        try {
            return Files.isDirectory(p);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Path preferredHomeDirectoryPath() {
        LogSupport.enter(LOG, "preferredHomeDirectoryPath");
        String home = env("HOME");
        if (home != null && !home.isBlank()) {
            try {
                Path p = Path.of(home).normalize();
                if (Files.exists(p)) {
                    return p;
                }
            } catch (RuntimeException ex) {
                // ignore
            }
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            try {
                Path p = Path.of(userHome).normalize();
                if (Files.exists(p)) {
                    return p;
                }
            } catch (RuntimeException ex) {
                // ignore
            }
        }

        try {
            return FileSystems.getDefault().getRootDirectories().iterator().next();
        } catch (RuntimeException ex) {
            return Path.of("/");
        }
    }

    private static String env(String key) {
        LogSupport.enter(LOG, "env");
        try {
            return System.getenv(key);
        } catch (SecurityException ex) {
            return null;
        }
    }

    private static String stripWrappingQuotes(String s) {
        LogSupport.enter(LOG, "stripWrappingQuotes");
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() >= 2) {
            if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
                return t.substring(1, t.length() - 1).trim();
            }
        }
        return t;
    }

    private static String normalizeForDisplay(Path p) {
        LogSupport.enter(LOG, "normalizeForDisplay");
        if (p == null) {
            return "";
        }
        return p.toString();
    }

    private static List<Path> buildSegments(Path path) {
        LogSupport.enter(LOG, "buildSegments");
        List<Path> segments = new ArrayList<>();
        if (path == null) {
            return segments;
        }

        Path normalized = path.normalize();

        Path cur = normalized;
        while (cur != null) {
            segments.addFirst(cur);
            cur = cur.getParent();
        }
        return segments;
    }

    private static String labelFor(Path path) {
        LogSupport.enter(LOG, "labelFor");
        if (path == null) {
            return "";
        }
        Path name = path.getFileName();
        if (name != null) {
            return name.toString();
        }
        String s = path.toString();
        return (s == null) ? "" : s;
    }
}
