package com.fileexplorer.ui;

import com.fileexplorer.MainApp;
import com.fileexplorer.ui.service.ThemeService;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
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
        root.getStyleClass().add("breadcrumb-bar");
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public void setPath(Path path) {
        if (path == null) {
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
                chevron.setOnAction(e -> showSegmentMenu(chevron, seg));
                root.getChildren().add(chevron);
            }
        }
    }

    private String labelFor(Path p) {
        Path name = p.getFileName();
        return (name != null) ? name.toString() : p.toString();
    }

    private void navigateTo(Path target) {
        if (onNavigate != null) {
            onNavigate.accept(target);
        }
    }

    private void showSegmentMenu(Node owner, Path base) {
        ContextMenu menu = buildContextMenu(base);
        menu.show(owner, Side.BOTTOM, 0, 0);
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
                ThemeService.copyToClipboard(base.toString());
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
                    Path rootPath = Paths.get(roots[0].getAbsolutePath());
                    navigateTo(rootPath);
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
        ThemeService.Theme theme = ThemeService.Theme.SYSTEM;
        if (ownerScene != null) {
            theme = ThemeService.getCurrentTheme(ownerScene);
        }

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
}
