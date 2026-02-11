package com.fileexplorer.ui.breadcrumb;

import com.fileexplorer.app.MainApp;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;
import com.fileexplorer.util.ClipboardUtil;

/**
 * Controller for BreadcrumbBar.fxml.
 * <p>
 * Exposes callback hooks so MainController can wire navigation:
 *   - onNavigate
 *   - onOpenInNewTab
 *   - onOpenInNewWindow
 *   - onCopyAddress
 *   - onBrowseNetwork
 */
public class BreadcrumbBar {

    private static final Logger LOG = Logger.getLogger(BreadcrumbBar.class.getName());

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
        LogSupport.enter(LOG, "initialize");
        root.getStyleClass().add("breadcrumb-bar");
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public void setPath(Path path) {
        LogSupport.enter(LOG, "setPath");
        if (path == null) {
            return;
        }
        currentPath = path;
        root.getChildren().clear();

        List<Path> segments = new ArrayList<>();
        Path cur = path;
        while (cur != null) {
            segments.addFirst(cur);
            cur = cur.getParent();
        }

        for (int i = 0; i < segments.size(); i++) {
            Path seg = segments.get(i);

            // Crumb button
            Button crumb = new Button(labelFor(seg));
            crumb.getStyleClass().add("breadcrumb-button");
            crumb.setOnAction(_ -> navigateTo(seg));
            root.getChildren().add(crumb);

            // Chevron button (">") with Win11-style popup menu
            if (i < segments.size() - 1) {
                Button chevron = new Button(">");
                chevron.getStyleClass().add("breadcrumb-separator-button");
                chevron.setOnAction(_ -> showSegmentMenu(chevron, seg));
                root.getChildren().add(chevron);
            }
        }
    }

    private String labelFor(Path p) {
        LogSupport.enter(LOG, "labelFor");
        Path name = p.getFileName();
        return (name != null) ? name.toString() : p.toString();
    }

    private void navigateTo(Path target) {
        LogSupport.enter(LOG, "navigateTo");
        if (onNavigate != null) {
            onNavigate.accept(target);
        }
    }

    private void showSegmentMenu(Node owner, Path base) {
        LogSupport.enter(LOG, "showSegmentMenu");
        ContextMenu menu = buildContextMenu(base);
        menu.show(owner, Side.BOTTOM, 0, 0);
    }

    private ContextMenu buildContextMenu(Path base) {
        LogSupport.enter(LOG, "buildContextMenu");
        ContextMenu menu = new ContextMenu();

        MenuItem open = new MenuItem("Open");
        open.setOnAction(_ -> navigateTo(base));

        MenuItem openNewTab = new MenuItem("Open in new tab");
        openNewTab.setOnAction(_ -> {
            if (onOpenInNewTab != null) {
                onOpenInNewTab.accept(base);
            }
        });

        MenuItem openNewWindow = new MenuItem("Open in new window");
        openNewWindow.setOnAction(_ -> {
            if (onOpenInNewWindow != null) {
                onOpenInNewWindow.accept(base);
            } else {
                spawnNewWindow(base);
            }
        });

        MenuItem copyAddress = new MenuItem("Copy address");
        copyAddress.setOnAction(_ -> {
            if (onCopyAddress != null) {
                onCopyAddress.accept(base);
            } else {
                ClipboardUtil.copyToClipboard(base.toString());
            }
        });

        MenuItem browseNetwork = new MenuItem("Browse network");
        browseNetwork.setOnAction(_ -> {
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
     * Fallback: open a second Explorer window in-process using the persisted theme preference.
     */
    private void spawnNewWindow(Path target) {
        LogSupport.enter(LOG, "spawnNewWindow");
        if (target == null) {
            return;
        }

        try {
            Stage stage = new Stage();
            MainApp.configureExplorerStage(stage, target);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ---------------------------------------------------------------------
    // Callback setters
    // ---------------------------------------------------------------------

    public void setOnNavigate(java.util.function.Consumer<Path> onNavigate) {
        LogSupport.enter(LOG, "setOnNavigate");
        this.onNavigate = onNavigate;
    }

    public void setOnOpenInNewTab(java.util.function.Consumer<Path> onOpenInNewTab) {
        LogSupport.enter(LOG, "setOnOpenInNewTab");
        this.onOpenInNewTab = onOpenInNewTab;
    }

    public void setOnOpenInNewWindow(java.util.function.Consumer<Path> onOpenInNewWindow) {
        LogSupport.enter(LOG, "setOnOpenInNewWindow");
        this.onOpenInNewWindow = onOpenInNewWindow;
    }

    public void setOnCopyAddress(java.util.function.Consumer<Path> onCopyAddress) {
        LogSupport.enter(LOG, "setOnCopyAddress");
        this.onCopyAddress = onCopyAddress;
    }

    public void setOnBrowseNetwork(Runnable onBrowseNetwork) {
        LogSupport.enter(LOG, "setOnBrowseNetwork");
        this.onBrowseNetwork = onBrowseNetwork;
    }
}
