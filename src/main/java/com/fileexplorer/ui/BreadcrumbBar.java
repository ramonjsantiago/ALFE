package com.fileexplorer.ui;

import com.fileexplorer.MainApp;
import com.fileexplorer.ui.service.ThemeService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Legacy breadcrumb bar implementation (not used by BreadcrumbBar.fxml, which uses BreadcrumbController),
 * but must compile cleanly as part of sources.
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

            Button crumb = new Button(labelFor(seg));
            crumb.getStyleClass().add("breadcrumb-button");
            crumb.setFocusTraversable(false);
            crumb.setOnAction(e -> {
                if (onNavigate != null) {
                    onNavigate.accept(seg);
                }
            });

            crumb.setOnMouseClicked(e -> {
                if (e.isSecondaryButtonDown()) {
                    ContextMenu menu = buildCrumbMenu(seg);
                    menu.show(crumb, Side.BOTTOM, 0, 0);
                    e.consume();
                }
            });

            root.getChildren().add(crumb);

            if (i < segments.size() - 1) {
                Node sep = new Button(">");
                sep.getStyleClass().add("breadcrumb-separator");
                root.getChildren().add(sep);
            }
        }
    }

    private ContextMenu buildCrumbMenu(Path base) {
        ContextMenu menu = new ContextMenu();

        MenuItem open = new MenuItem("Open");
        open.setOnAction(e -> {
            if (onNavigate != null) {
                onNavigate.accept(base);
            }
        });

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

        MenuItem openRoot = new MenuItem("Open root");
        openRoot.setOnAction(e -> {
            File[] roots = File.listRoots();
            if (roots != null && roots.length > 0) {
                Path rp = Paths.get(roots[0].getAbsolutePath());
                if (onNavigate != null) {
                    onNavigate.accept(rp);
                }
            }
        });

        MenuItem copyAddress = new MenuItem("Copy address");
        copyAddress.setOnAction(e -> {
            if (onCopyAddress != null) {
                onCopyAddress.accept(base);
            } else {
                copyToClipboard(base == null ? "" : base.toString());
            }
        });

        MenuItem browseNetwork = new MenuItem("Browse network");
        browseNetwork.setOnAction(e -> {
            if (onBrowseNetwork != null) {
                onBrowseNetwork.run();
            }
        });

        menu.getItems().addAll(
            open,
            openNewTab,
            openNewWindow,
            openRoot,
            new SeparatorMenuItem(),
            copyAddress,
            browseNetwork
        );

        return menu;
    }

    private void spawnNewWindow(Path target) {
        if (target == null) {
            return;
        }

        Scene ownerScene = root == null ? null : root.getScene();
        Boolean darkOverride = ownerScene == null ? null : ThemeService.isDarkApplied(ownerScene);

        try {
            Stage stage = new Stage();
            MainApp.configureExplorerStage(stage, target, darkOverride);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

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
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static String labelFor(Path path) {
        if (path == null) {
            return "";
        }
        Path name = path.getFileName();
        if (name != null) {
            return name.toString();
        }
        String s = path.toString();
        return s.isEmpty() ? path.toAbsolutePath().toString() : s;
    }
}
