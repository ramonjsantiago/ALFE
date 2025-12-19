package com.fileexplorer.ui;

import com.fileexplorer.MainApp;
import com.fileexplorer.ui.service.ThemeService;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Win11-style breadcrumb bar with chevrons that show popup menus.
 *
 * Note: This controller is currently not used by BreadcrumbBar.fxml (that include uses BreadcrumbController),
 * but it must compile cleanly as part of the project sources.
 */
public class BreadcrumbBarController {

    @FXML private HBox root;

    private Path currentPath;

    private Consumer<Path> onNavigate;
    private Consumer<Path> onOpenInNewTab;
    private Runnable onBrowseNetwork;

    @FXML
    private void initialize() {
        // no-op
    }

    public void setOnNavigate(Consumer<Path> onNavigate) {
        this.onNavigate = onNavigate;
    }

    public void setOnOpenInNewTab(Consumer<Path> onOpenInNewTab) {
        this.onOpenInNewTab = onOpenInNewTab;
    }

    public void setOnBrowseNetwork(Runnable onBrowseNetwork) {
        this.onBrowseNetwork = onBrowseNetwork;
    }

    public void setPath(Path path) {
        this.currentPath = path;
        render();
    }

    private void render() {
        if (root == null) {
            return;
        }
        root.getChildren().clear();

        if (currentPath == null) {
            return;
        }

        List<Path> parts = splitPath(currentPath);
        for (int i = 0; i < parts.size(); i++) {
            Path part = parts.get(i);

            Button crumb = new Button(labelFor(part));
            crumb.getStyleClass().add("breadcrumb-item");
            crumb.setFocusTraversable(false);

            final Path target = part;
            crumb.setOnAction(e -> {
                if (onNavigate != null) onNavigate.accept(target);
            });

            crumb.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.SECONDARY) {
                    ContextMenu menu = buildContextMenu(target);
                    menu.show(crumb, Side.BOTTOM, 0, 0);
                    e.consume();
                }
            });

            root.getChildren().add(crumb);

            if (i < parts.size() - 1) {
                Button chevron = new Button(">");
                chevron.getStyleClass().add("breadcrumb-chevron");
                chevron.setFocusTraversable(false);

                final Path menuBase = part;
                chevron.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        ContextMenu menu = buildChildrenMenu(menuBase);
                        menu.show(chevron, Side.BOTTOM, 0, 0);
                        e.consume();
                    }
                });

                root.getChildren().add(chevron);
            }
        }
    }

    private ContextMenu buildContextMenu(Path base) {
        ContextMenu menu = new ContextMenu();

        MenuItem open = new MenuItem("Open");
        open.setOnAction(e -> {
            if (onNavigate != null) onNavigate.accept(base);
        });

        MenuItem openNewTab = new MenuItem("Open in new tab");
        openNewTab.setOnAction(e -> {
            if (onOpenInNewTab != null) onOpenInNewTab.accept(base);
        });

        MenuItem openNewWindow = new MenuItem("Open in new window");
        openNewWindow.setOnAction(e -> openInNewWindow(base));

        MenuItem copyAddress = new MenuItem("Copy address");
        copyAddress.setOnAction(e -> copyAddressToClipboard(base));

        menu.getItems().addAll(
            open,
            openNewTab,
            openNewWindow,
            new SeparatorMenuItem(),
            copyAddress
        );

        if (onBrowseNetwork != null) {
            menu.getItems().add(new SeparatorMenuItem());
            MenuItem browseNetwork = new MenuItem("Browse network");
            browseNetwork.setOnAction(e -> onBrowseNetwork.run());
            menu.getItems().add(browseNetwork);
        }

        return menu;
    }

    private ContextMenu buildChildrenMenu(Path base) {
        ContextMenu menu = new ContextMenu();

        List<Path> children = listChildren(base);
        if (children.isEmpty()) {
            MenuItem empty = new MenuItem("(empty)");
            empty.setDisable(true);
            menu.getItems().add(empty);
            return menu;
        }

        for (Path child : children) {
            MenuItem mi = new MenuItem(labelFor(child));
            mi.setOnAction(e -> {
                if (onNavigate != null) onNavigate.accept(child);
            });
            menu.getItems().add(mi);
        }

        return menu;
    }

    private List<Path> listChildren(Path base) {
        List<Path> out = new ArrayList<>();
        if (base == null) return out;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(base)) {
            for (Path p : ds) {
                out.add(p);
            }
        } catch (IOException ignored) {
            // best-effort
        }

        out.sort((a, b) -> labelFor(a).compareToIgnoreCase(labelFor(b)));
        return out;
    }

    private void copyAddressToClipboard(Path p) {
        if (p == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(p.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void openInNewWindow(Path folder) {
        if (folder == null) {
            return;
        }

        Scene ownerScene = root == null ? null : root.getScene();
        Boolean darkOverride = ownerScene == null ? null : ThemeService.isDarkApplied(ownerScene);

        try {
            Stage stage = new Stage();
            MainApp.configureExplorerStage(stage, folder, darkOverride);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static List<Path> splitPath(Path p) {
        List<Path> parts = new ArrayList<>();
        if (p == null) return parts;

        Path cur = p;
        while (cur != null) {
            parts.add(0, cur);
            cur = cur.getParent();
        }
        return parts;
    }

    private static String labelFor(Path p) {
        if (p == null) return "";
        Path fileName = p.getFileName();
        if (fileName == null) {
            return p.toString();
        }
        String s = fileName.toString();
        return s.isBlank() ? p.toString() : s;
    }
}
