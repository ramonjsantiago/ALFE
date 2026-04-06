package com.fileexplorer.controller.navigation;

import com.fileexplorer.lifecycle.Lifecycle;
import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.util.CompositeCloseable;

import com.fileexplorer.util.IconLoader;
import com.fileexplorer.service.theme.ThemeService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Navigation pane controller.
 *
 * This project ZIP does not include the earlier experimental nav classes under com.fileexplorer.ui.nav.
 * This controller is implemented using only the core project classes + JavaFX so it compiles cleanly.
 *
 * Explorer-like metrics (from the provided reference image):
 * - Icon: 16x16
 * - Row pitch: 24px
 * - Icon-to-text gap: 5px
 * - Left padding: 8px (indent handled by -fx-indent:16px via explorer_tree.css)
 */
public final class NavigationPaneController implements Lifecycle {

    @FXML private BorderPane navRoot;
    @FXML private TreeView<NavEntry> navTreeView;
    @FXML private Button seeMoreMenuButton;

    private final ThemeService themeService = new ThemeService();

    // --- Metrics ------------------------------------------------------------
    private static final double TREE_ROW_HEIGHT_PX = 24.0;


    private ExplorerContext context;
    private final CompositeCloseable disposables = new CompositeCloseable();
    private static final double TREE_ICON_SIZE_PX = 16.0;
    private static final double TREE_GRAPHIC_TEXT_GAP_PX = 5.0;
    private static final Insets TREE_CELL_PADDING = new Insets(3, 8, 3, 8);

    @FXML
/**
 * initialize.
 *
 */
    private void initialize() {
        // Optional: if you later add a glyph system, set it here. For now keep it simple.
        if (seeMoreMenuButton != null && (seeMoreMenuButton.getText() == null || seeMoreMenuButton.getText().isBlank())) {
            seeMoreMenuButton.setText("⋯");
        }

        if (navTreeView == null) {
            return;
        }

        // Explorer-like styling hook
        if (!navTreeView.getStyleClass().contains("explorer-tree")) {
            navTreeView.getStyleClass().add("explorer-tree");
        }

        // Load explorer_tree.css when attached to a Scene
        navTreeView.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            try {
                var url = NavigationPaneController.class.getResource("/css/explorer_tree.css");
                if (url == null) return;
                String css = url.toExternalForm();
                if (!newScene.getStylesheets().contains(css)) {
                    newScene.getStylesheets().add(css);
                }
            } catch (Exception ignored) { }
        });

        navTreeView.setFixedCellSize(TREE_ROW_HEIGHT_PX);

        TreeItem<NavEntry> root = buildRoot();
        navTreeView.setRoot(root);
        navTreeView.setShowRoot(false);
        root.setExpanded(true);

        navTreeView.setCellFactory(tv -> {
            ExplorerMetricsTreeCell cell = new ExplorerMetricsTreeCell();
            // keep row height deterministic even if fixedCellSize changes later
            cell.setMinHeight(TREE_ROW_HEIGHT_PX);
            cell.setPrefHeight(TREE_ROW_HEIGHT_PX);
            cell.setMaxHeight(TREE_ROW_HEIGHT_PX);
            return cell;
        });

        navTreeView.setOnMouseClicked(evt -> {
            if (evt.getButton() != MouseButton.PRIMARY) return;

            TreeItem<NavEntry> sel = navTreeView.getSelectionModel().getSelectedItem();
            if (sel == null) return;

            NavEntry entry = sel.getValue();
            if (entry == null) return;

            if (evt.getClickCount() == 2) {
                sel.setExpanded(!sel.isExpanded());
            } else {
                onEntrySelected(entry);
            }
        });

        // Apply min width after CSS/layout is available.
        Platform.runLater(this::applyMinWidthRule);
    }

/**
 * buildRoot.
 *
 * @return TODO
 */
    private TreeItem<NavEntry> buildRoot() {
        TreeItem<NavEntry> root = new TreeItem<>(new NavEntry("Navigation", null));

        Path home = Path.of(System.getProperty("user.home"));
        addIfExists(root, "Desktop", home.resolve("Desktop"));
        addIfExists(root, "Downloads", home.resolve("Downloads"));
        addIfExists(root, "Documents", home.resolve("Documents"));
        addIfExists(root, "Pictures", home.resolve("Pictures"));
        addIfExists(root, "Music", home.resolve("Music"));
        addIfExists(root, "Videos", home.resolve("Videos"));

        // Always add workspace-like entry (even if missing) so users can wire it later.
        Path workspace = home.resolve("workspace");
        root.getChildren().add(new TreeItem<>(new NavEntry("workspace", workspace)));

        return root;
    }

/**
 * addIfExists.
 *
 * @param parent TODO
 * @param label TODO
 * @param path TODO
 */
    private static void addIfExists(TreeItem<NavEntry> parent, String label, Path path) {
        if (path != null && Files.exists(path)) {
            parent.getChildren().add(new TreeItem<>(new NavEntry(label, path)));
        }
    }

    /**
     * Enforces: minWidth >= (2 * iconWidth + padding).
     * Applied to both the TreeView and the root BorderPane so SplitPane can't drag smaller.
     */
    private void applyMinWidthRule() {
        double iconW = measureTextWidth("▶", Font.font(12));
        double computedMin = Math.ceil((2.0 * iconW) + 24.0);
        double minWidth = Math.max(48.0, computedMin);

        if (navTreeView != null) {
            navTreeView.setMinWidth(minWidth);
        }
        if (navRoot != null) {
            navRoot.setMinWidth(minWidth);
        }
    }

/**
 * measureTextWidth.
 *
 * @param s TODO
 * @param font TODO
 * @return TODO
 */
    private static double measureTextWidth(String s, Font font) {
        Text t = new Text(s);
        t.setFont(font);
        return t.getLayoutBounds().getWidth();
    }

    @FXML
/**
 * onSeeMore.
 *
 * @param e TODO
 */
    private void onSeeMore(ActionEvent e) {
        // TODO: add context menu/actions as needed
    }

/**
 * onEntrySelected.
 *
 * @param entry TODO
 */
    private void onEntrySelected(NavEntry entry) {
        // This controller is intentionally self-contained. Wire selection into your app here if needed.
        // Example: publish an event, call a callback, or inject a navigation service.
        // Path p = entry.path();
    }

    /**
     * Minimal model for the nav tree: label + optional path.
     */
    public record NavEntry(String label, Path path) { }

    /**
     * TreeCell enforcing the measured spacing constraints.
     */
    private final class ExplorerMetricsTreeCell extends TreeCell<NavEntry> {

        ExplorerMetricsTreeCell() {
            setContentDisplay(ContentDisplay.LEFT);
            setGraphicTextGap(TREE_GRAPHIC_TEXT_GAP_PX);
            setPadding(TREE_CELL_PADDING);
        }

        @Override
/**
 * updateItem.
 *
 * @param item TODO
 * @param empty TODO
 */
        protected void updateItem(NavEntry item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            setText(item.label());

            // Icon (16x16): if we have a real path, load an icon; else no icon.
            if (item.path() != null) {
                boolean dark = themeService.isDarkPreferred();
                Image img = IconLoader.loadForPath(item.path(), dark, (int) TREE_ICON_SIZE_PX);
                if (img != null) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(TREE_ICON_SIZE_PX);
                    iv.setFitHeight(TREE_ICON_SIZE_PX);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    setGraphic(iv);
                } else {
                    setGraphic(null);
                }
            } else {
                setGraphic(null);
            }
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
