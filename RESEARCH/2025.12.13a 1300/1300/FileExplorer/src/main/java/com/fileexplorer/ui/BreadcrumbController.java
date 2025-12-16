package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controller for the BreadcrumbBar.fxml include.
 *
 * Responsibilities:
 *  - Render the current path as clickable segments.
 *  - Expose callbacks to MainController for navigation and commands
 *    (open in new window, copy address, browse network).
 */
public class BreadcrumbController {

    @FXML
    private HBox root;

    @FXML
    private ScrollPane breadcrumbScroll;

    @FXML
    private HBox crumbContainer;

    @FXML
    private Button dropdownButton;

    private Path currentPath;

    private Consumer<Path> onNavigate;
    private Consumer<Path> onOpenInNewWindow;
    private Consumer<Path> onCopyAddress;
    private Runnable onBrowseNetwork;

    @FXML
    private void initialize() {
        if (root != null) {
            root.getStyleClass().add("breadcrumb-bar");
            root.setPadding(new Insets(4, 8, 4, 8));
        }
        if (dropdownButton != null) {
            dropdownButton.setOnMouseClicked(evt -> {
                if (evt.getButton() == MouseButton.PRIMARY) {
                    showOverflowMenu();
                }
            });
        }
    }

    // ---------------------------------------------------------------------
    // Public API used by MainController
    // ---------------------------------------------------------------------

    public void setOnNavigate(Consumer<Path> onNavigate) {
        this.onNavigate = onNavigate;
    }

    public void setOnOpenInNewWindow(Consumer<Path> onOpenInNewWindow) {
        this.onOpenInNewWindow = onOpenInNewWindow;
    }

    public void setOnCopyAddress(Consumer<Path> onCopyAddress) {
        this.onCopyAddress = onCopyAddress;
    }

    public void setOnBrowseNetwork(Runnable onBrowseNetwork) {
        this.onBrowseNetwork = onBrowseNetwork;
    }

    public void setPath(Path path) {
        this.currentPath = path;
        rebuildCrumbs();
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    private void rebuildCrumbs() {
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

            button.setOnAction(e -> {
                if (onNavigate != null) {
                    onNavigate.accept(segment);
                }
            });

            button.setOnMouseClicked(evt -> {
                if (evt.getButton() == MouseButton.SECONDARY) {
                    showItemContextMenu(button, segment, evt.getScreenX(), evt.getScreenY());
                }
            });

            crumbContainer.getChildren().add(button);
        }

        // Scroll to the right so the last segment is visible.
        if (breadcrumbScroll != null) {
            breadcrumbScroll.applyCss();
            breadcrumbScroll.layout();
            breadcrumbScroll.setHvalue(1.0);
        }
    }

    private List<Path> buildSegments(Path path) {
        List<Path> segments = new ArrayList<>();
        Path current = path;
        while (current != null) {
            segments.add(0, current);
            current = current.getParent();
        }
        return segments;
    }

    private String labelFor(Path path) {
        Path name = path.getFileName();
        if (name != null) {
            return name.toString();
        }
        String s = path.toString();
        return s.isEmpty() ? path.toAbsolutePath().toString() : s;
    }

    // ---------------------------------------------------------------------
    // Menus
    // ---------------------------------------------------------------------

    private void showItemContextMenu(Button owner, Path path, double screenX, double screenY) {
        ContextMenu menu = new ContextMenu();

        MenuItem openInNewWindow = new MenuItem("Open in new window");
        openInNewWindow.setOnAction(e -> {
            if (onOpenInNewWindow != null) {
                onOpenInNewWindow.accept(path);
            }
        });

        MenuItem copyAddress = new MenuItem("Copy address");
        copyAddress.setOnAction(e -> {
            if (onCopyAddress != null) {
                onCopyAddress.accept(path);
            }
        });

        MenuItem browseNetwork = new MenuItem("Browse network");
        browseNetwork.setOnAction(e -> {
            if (onBrowseNetwork != null) {
                onBrowseNetwork.run();
            }
        });

        menu.getItems().addAll(openInNewWindow, copyAddress, new SeparatorMenuItem(), browseNetwork);
        menu.show(owner, screenX, screenY);
    }

    private void showOverflowMenu() {
        ContextMenu menu = new ContextMenu();

        if (currentPath != null) {
            MenuItem copyAddress = new MenuItem("Copy address");
            copyAddress.setOnAction(e -> {
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
            browseNetwork.setOnAction(e -> onBrowseNetwork.run());
            menu.getItems().add(browseNetwork);
        }

        menu.show(dropdownButton, Side.BOTTOM, 0, 0);
    }
}
