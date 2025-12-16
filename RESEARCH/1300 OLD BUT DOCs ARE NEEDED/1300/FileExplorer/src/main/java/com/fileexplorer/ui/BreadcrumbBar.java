package com.fileexplorer.ui;

import com.fileexplorer.MainApp;
import com.fileexplorer.service.ThemeService;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for BreadcrumbBar.fxml.
 *
 * Exposes callback hooks so MainController can wire navigation:
 *   - onNavigate
 *   - onOpenInNewTab
 *   - onOpenInNewWindow
 *   - onCopyAddress
 *   - onBrowseNetwork
 */
public class BreadcrumbBar {

    @FXML
    private HBox root;

    private Path currentPath;

    private java.util.function.Consumer<Path> onNavigate;
    private java.util.function.Consumer<Path> onOpenInNewTab;
    private java.util.function.Consumer<Path> onOpenInNewWindow;
    private java.util.function.Consumer<Path> onCopyAddress;
    private Runnable onBrowseNetwork;

    @FXML
    private void initialize() {
        // no-op
    }

    public void setPath(Path path) {
        if (root == null) {
            return;
        }
        if (path == null) {
            currentPath = null;
            root.getChildren().clear();
            return;
        }
        currentPath = path;
        root.getChildren().clear();

        List<Path> segments = new ArrayList<>();
        Path cur = path;
        while (cur != null) {
            segments.add(0, cur);
            cur = cur.getParent();
        }

        for (int i = 0; i < segments.size(); i++) {
            Path seg = segments.get(i);

            // Crumb button
            Button crumb = new Button(labelFor(seg));
            crumb.getStyleClass().add("breadcrumb-button");
            crumb.setOnAction(e -> navigateTo(seg));
            root.getChildren().add(crumb);

            // Chevron button (">") with Win11-style popup menu
            if (i < segments.size() - 1) {
                Button chevron = new Button(">");
                chevron.getStyleClass().add("breadcrumb-separator-button");
                chevron.setOnAction(e -> {
                    ContextMenu menu = buildContextMenu(seg);
                    menu.show(chevron, Side.BOTTOM, 0, 0);
                });
                root.getChildren().add(chevron);
            }
        }
    }

    public Path getCurrentPath() {
        return currentPath;
    }

    private String labelFor(Path p) {
        if (p == null) {
            return "";
        }
        if (p.getFileName() == null) {
            return p.toString();
        }
        String name = p.getFileName().toString();
        return name.isBlank() ? p.toString() : name;
    }

    private void navigateTo(Path target) {
        if (target == null) {
            return;
        }
        if (onNavigate != null) {
            onNavigate.accept(target);
        }
    }

    private ContextMenu buildContextMenu(Path base) {
        ContextMenu menu = new ContextMenu();

        MenuItem open = new MenuItem("Open");
        open.setOnAction(e -> navigateTo(base));

        MenuItem openNewTab = new MenuItem("Open in new tab");
        openNewTab.setOnAction(e -> {
            if (onOpenInNewTab != null) {
                onOpenInNewTab.accept(base);
            }
        });

        MenuItem openNewWindow = new MenuItem("Open in new window");
        openNewWindow.setOnAction(e -> {
            if (onOpenInNewWindow != null) {
                onOpenInNewWindow.accept(base);
            } else {
                spawnNewWindow(base);
            }
        });

        MenuItem copyAddress = new MenuItem("Copy address");
        copyAddress.setOnAction(e -> {
            if (onCopyAddress != null) {
                onCopyAddress.accept(base);
            } else {
                copyToClipboard(base.toString());
            }
        });

        MenuItem browseNetwork = new MenuItem("Browse network");
        browseNetwork.setOnAction(e -> {
            if (onBrowseNetwork != null) {
                onBrowseNetwork.run();
            } else {
                // Default: open the root of the file system
                File[] roots = File.listRoots();
                if (roots != null && roots.length > 0) {
                    navigateTo(Paths.get(roots[0].toURI()));
                }
            }
        });

        menu.getItems().addAll(
                open,
                openNewTab,
                openNewWindow,
                new SeparatorMenuItem(),
                copyAddress,
                browseNetwork
        );

        return menu;
    }

    /**
     * Fallback: open a second Explorer window in-process using the current theme.
     */
    private void spawnNewWindow(Path target) {
        if (target == null) {
            return;
        }

        // Derive current theme from owning Scene (if available)
        Scene ownerScene = root.getScene();
        ThemeService.Theme theme = inferTheme(ownerScene);

        try {
            Stage stage = new Stage();
            MainApp.configureExplorerStage(stage, target, theme);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ---------------------------------------------------------------------
    // Callback setters
    // ---------------------------------------------------------------------

    public void setOnNavigate(java.util.function.Consumer<Path> onNavigate) {
        this.onNavigate = onNavigate;
    }

    public void setOnOpenInNewTab(java.util.function.Consumer<Path> onOpenInNewTab) {
        this.onOpenInNewTab = onOpenInNewTab;
    }

    public void setOnOpenInNewWindow(java.util.function.Consumer<Path> onOpenInNewWindow) {
        this.onOpenInNewWindow = onOpenInNewWindow;
    }

    public void setOnCopyAddress(java.util.function.Consumer<Path> onCopyAddress) {
        this.onCopyAddress = onCopyAddress;
    }

    public void setOnBrowseNetwork(Runnable onBrowseNetwork) {
        this.onBrowseNetwork = onBrowseNetwork;
    }

    private static void copyToClipboard(String text) {
        if (text == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static ThemeService.Theme inferTheme(Scene scene) {
        // Prefer detecting from the currently applied stylesheets.
        if (scene != null) {
            for (String s : scene.getStylesheets()) {
                if (s == null) {
                    continue;
                }
                String lower = s.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("explorer-dark-win.css")) {
                    return ThemeService.Theme.DARK;
                }
                if (lower.contains("explorer-light-win.css")) {
                    return ThemeService.Theme.LIGHT;
                }
            }
        }
        // Fallback to persisted preference.
        return ThemeService.fromPreference();
    }
}
