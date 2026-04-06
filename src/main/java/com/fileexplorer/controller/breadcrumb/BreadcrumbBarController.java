package com.fileexplorer.controller.breadcrumb;

import com.fileexplorer.lifecycle.Lifecycle;
import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.util.CompositeCloseable;

import com.fileexplorer.app.MainApp;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

/**
 * Win11-style breadcrumb bar with chevrons that show popup menus.
 */
public class BreadcrumbBarController implements Lifecycle {

    private static final Logger LOG = Logger.getLogger(BreadcrumbBarController.class.getName());


    private ExplorerContext context;
    private final CompositeCloseable disposables = new CompositeCloseable();

    @FXML
    private HBox root;

    private Consumer<Path> onNavigate;
    private Consumer<Path> onOpenInNewTab;

    private Path currentPath;

    @FXML
/**
 * initialize.
 *
 */
    private void initialize() {
        LogSupport.enter(LOG, "initialize");
        root.getStyleClass().add("breadcrumb-bar");
    }

    // ---------------------------------------------------------------------
    // Callbacks wired from MainController
    // ---------------------------------------------------------------------

/**
 * setOnNavigate.
 *
 * @param handler TODO
 */
    public void setOnNavigate(Consumer<Path> handler) {
        LogSupport.enter(LOG, "setOnNavigate");
        this.onNavigate = handler;
    }

/**
 * setOnOpenInNewTab.
 *
 * @param handler TODO
 */
    public void setOnOpenInNewTab(Consumer<Path> handler) {
        LogSupport.enter(LOG, "setOnOpenInNewTab");
        this.onOpenInNewTab = handler;
    }

    // ---------------------------------------------------------------------
    // Public API: update the bar to represent a path
    // ---------------------------------------------------------------------

/**
 * setPath.
 *
 * @param path TODO
 */
    public void setPath(Path path) {
        LogSupport.enter(LOG, "setPath");
        currentPath = path;
        root.getChildren().clear();
        if (path == null) {
            return;
        }

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

/**
 * labelFor.
 *
 * @param p TODO
 * @return TODO
 */
    private String labelFor(Path p) {
        LogSupport.enter(LOG, "labelFor");
        Path name = p.getFileName();
        return (name != null) ? name.toString() : p.toString();
    }

/**
 * navigateTo.
 *
 * @param target TODO
 */
    private void navigateTo(Path target) {
        LogSupport.enter(LOG, "navigateTo");
        if (onNavigate != null) {
            onNavigate.accept(target);
        }
    }

    // ---------------------------------------------------------------------
    // Popup menu for a crumb chevron
    // ---------------------------------------------------------------------

/**
 * buildSegmentMenu.
 *
 * @param base TODO
 * @return TODO
 */
    private ContextMenu buildSegmentMenu(Path base) {
        LogSupport.enter(LOG, "buildSegmentMenu");
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("fluent-context-menu");

        MenuItem open = new MenuItem("Open");
        open.setOnAction(_ -> navigateTo(base));

        MenuItem openNewTab = new MenuItem("Open in new tab");
        openNewTab.setOnAction(_ -> {
            if (onOpenInNewTab != null) {
                onOpenInNewTab.accept(base);
            }
        });

        MenuItem openNewWindow = new MenuItem("Open in new window");
        openNewWindow.setOnAction(_ -> openInNewWindow(base));

        MenuItem copyAddress = new MenuItem("Copy address");
        copyAddress.setOnAction(_ -> copyAddressToClipboard(base));

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
                        Files.newDirectoryStream(base, Files::isDirectory)) {
                    for (Path child : stream) {
                        MenuItem mi = new MenuItem(labelFor(child));
                        mi.setOnAction(_ -> navigateTo(child));
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

/**
 * copyAddressToClipboard.
 *
 * @param p TODO
 */
    private void copyAddressToClipboard(Path p) {
        LogSupport.enter(LOG, "copyAddressToClipboard");
        if (p == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(p.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    // ---------------------------------------------------------------------
    // Open in new window using the persisted theme preference
    // ---------------------------------------------------------------------

/**
 * openInNewWindow.
 *
 * @param folder TODO
 */
    private void openInNewWindow(Path folder) {
        LogSupport.enter(LOG, "openInNewWindow");
        if (folder == null) {
            return;
        }

        try {
            Stage stage = new Stage();
            MainApp.configureExplorerStage(stage, folder, "breadcrumb.controller.new-window");
        } catch (IOException ex) {
            LOG.log(java.util.logging.Level.WARNING, "Failed to open breadcrumb new window for " + folder, ex);
        }
    }

    @Override
/**
 * attach.
 *
 * @param context TODO
 */
    public void attach(ExplorerContext context) {
        this.context = context;
        // Ensure this controller's disposables are closed when the shared context is disposed.
        if (context != null) {
            context.disposables().add(disposables);
        }
    }

    @Override
/**
 * dispose.
 *
 */
    public void dispose() {
        try {
            disposables.close();
        } catch (Exception ignored) {
        }
    }

}
