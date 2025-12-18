package com.fileexplorer.ui;

import com.fileexplorer.MainApp;
import com.fileexplorer.ui.Theme;

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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Win11-style breadcrumb bar with chevrons that show popup menus.
 */
public class BreadcrumbBarController {

    @FXML
    private HBox root;

    private Consumer<Path> onNavigate;
    private Consumer<Path> onOpenInNewTab;

    private Path currentPath;

    @FXML
    private void initialize() {
        root.getStyleClass().add("breadcrumb-bar");
    }

    // ---------------------------------------------------------------------
    // Callbacks wired from MainController
    // ---------------------------------------------------------------------

    public void setOnNavigate(Consumer<Path> handler) {
        this.onNavigate = handler;
    }

    public void setOnOpenInNewTab(Consumer<Path> handler) {
        this.onOpenInNewTab = handler;
    }

    // ---------------------------------------------------------------------
    // Public API: update the bar to represent a path
    // ---------------------------------------------------------------------

    public void setPath(Path path) {
        currentPath = path;
        root.getChildren().clear();
        if (path == null) {
            return;
        }

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
                chevron.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        ContextMenu menu = buildSegmentMenu(seg);
                        menu.show(chevron, Side.BOTTOM, 0, 0);
                    }
                });
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

    // ---------------------------------------------------------------------
    // Popup menu for a crumb chevron
    // ---------------------------------------------------------------------

    private ContextMenu buildSegmentMenu(Path base) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("fluent-context-menu");

        MenuItem open = new MenuItem("Open");
        open.setOnAction(e -> navigateTo(base));

        MenuItem openNewTab = new MenuItem("Open in new tab");
        openNewTab.setOnAction(e -> {
            if (onOpenInNewTab != null) {
                onOpenInNewTab.accept(base);
            }
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

        // Extra: list immediate child folders of this segment, like Explorer
        try {
            if (Files.isDirectory(base)) {
                List<MenuItem> children = new ArrayList<>();
                try (DirectoryStream<Path> stream =
                             Files.newDirectoryStream(base, entry -> Files.isDirectory(entry))) {
                    for (Path child : stream) {
                        MenuItem mi = new MenuItem(labelFor(child));
                        mi.setOnAction(e -> navigateTo(child));
                        children.add(mi);
                    }
                }
                if (!children.isEmpty()) {
                    menu.getItems().add(new SeparatorMenuItem());
                    menu.getItems().addAll(children);
                }
            }
        } catch (IOException ignored) {
        }

        return menu;
    }

    private void copyAddressToClipboard(Path p) {
        if (p == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(p.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    // ---------------------------------------------------------------------
    // Open in new window using the current theme
    // ---------------------------------------------------------------------

    private void openInNewWindow(Path folder) {
        if (folder == null) {
            return;
        }

        Scene ownerScene = root.getScene();
        ThemeService.Theme theme = ThemeService.Theme.SYSTEM;
        if (ownerScene != null) {
            theme = ThemeService.getCurrentTheme(ownerScene);
        }

        try {
            Stage stage = new Stage();
            MainApp.configureExplorerStage(stage, folder, theme);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
