package com.fileexplorer.ui.tree;

import com.fileexplorer.service.filesystem.TreeBuildService;
import com.fileexplorer.service.theme.ThemeService;
import com.fileexplorer.util.IconLoader;
import java.nio.file.Path;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * TreeCell for Path with file icons.
 *
 * Uses a font-free, hollow chevron disclosure (stroke-only Path) to match the
 * Windows Explorer look without requiring bundled icon fonts.
 */
public class IconPathTreeCell extends TreeCell<Path> {

    private final ImageView iconView = new ImageView();
    private final StackPane iconContainer = new StackPane();
    private final ThemeService themeService;
    private final TreeBuildService displayService;

    private final StackPane disclosureContainer = new StackPane();
    private final javafx.scene.shape.Path disclosureChevron = new javafx.scene.shape.Path();

    private final ChangeListener<Boolean> expandedListener = (obs, oldV, newV) -> updateDisclosure();
    private TreeItem<Path> observedTreeItem;

    private final ChangeListener<TreeItem<Path>> treeItemListener = (obs, oldTi, newTi) -> {
        // Virtualized controls can re-use cells and swap TreeItems; keep disclosure spacing stable.
        detach();
        observedTreeItem = newTi;
        if (observedTreeItem != null) {
            observedTreeItem.expandedProperty().addListener(expandedListener);
        }
        updateDisclosure();
    };

/**
 * IconPathTreeCell.
 *
 * @param fixedCellSize TODO
 * @param themeService TODO
 * @param displayService TODO
 * @return TODO
 */
    public IconPathTreeCell(double fixedCellSize, ThemeService themeService, TreeBuildService displayService) {
        this.displayService = displayService;
        this.themeService = themeService;

        setContentDisplay(ContentDisplay.LEFT);
        setAlignment(Pos.CENTER_LEFT);
        setGraphicTextGap(5.0);
        // Match Explorer's left edge: reduce left inset by 1px to avoid subtle over-indent.
        setPadding(new Insets(0, 8, 0, 6));

        if (Double.isFinite(fixedCellSize) && fixedCellSize > 0) {
            setMinHeight(fixedCellSize);
            setPrefHeight(fixedCellSize);
            setMaxHeight(fixedCellSize);
        }

        iconView.setFitWidth(16.0);
        iconView.setFitHeight(16.0);
        iconView.setPreserveRatio(true);

        // Fixed-size graphic box ensures all icons align vertically across rows.
        iconContainer.setAlignment(Pos.CENTER);
        iconContainer.setMinSize(18.0, 18.0);
        iconContainer.setPrefSize(18.0, 18.0);
        iconContainer.setMaxSize(18.0, 18.0);
        iconContainer.getChildren().setAll(iconView);
        setGraphic(iconContainer);

        // Hollow chevron (stroke-only). The shape is a right-pointing chevron; we rotate it 90° for expanded.
        disclosureChevron.getElements().setAll(chevronRight());
        disclosureChevron.setFill(null);
        disclosureChevron.setStrokeLineCap(StrokeLineCap.ROUND);
        disclosureChevron.setStrokeLineJoin(StrokeLineJoin.ROUND);
        disclosureChevron.setStrokeWidth(1.5);
        // Track the cell's text color for a native look across themes.
        disclosureChevron.strokeProperty().bind(textFillProperty());

        // Reserve a fixed area for the disclosure control so text/icons align like Explorer.
        disclosureContainer.setAlignment(Pos.CENTER);
        disclosureContainer.setMinSize(18.0, 18.0);
        disclosureContainer.setPrefSize(18.0, 18.0);
        disclosureContainer.setMaxSize(18.0, 18.0);
        // Increase spacing between chevron and icon (Explorer-like).
        disclosureContainer.setPadding(new Insets(0, 2, 0, 0));
        disclosureContainer.getChildren().add(disclosureChevron);

        disclosureContainer.setMouseTransparent(false);
        disclosureContainer.setPickOnBounds(true);
        disclosureContainer.setOnMouseClicked(e -> {
            TreeItem<Path> ti = getTreeItem();
            if (ti == null || ti.isLeaf()) {
                return;
            }
            ti.setExpanded(!ti.isExpanded());
            e.consume();
        });

        setDisclosureNode(disclosureContainer);

        // Ensure disclosure spacing remains consistent even if the TreeItem is swapped after updateItem.
        treeItemProperty().addListener(treeItemListener);

        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<Path> ti = getTreeItem();
                if (ti != null && !ti.isLeaf()) {
                    ti.setExpanded(!ti.isExpanded());
                    e.consume();
                }
            }
        });
    }

    @Override
/**
 * updateItem.
 *
 * @param item TODO
 * @param empty TODO
 */
    protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);

        detach();

        if (empty) {
            setText(null);
            iconView.setImage(null);
            disclosureContainer.setVisible(false);
            disclosureContainer.setManaged(false);
            return;
        }

        // Always reserve disclosure space for non-empty rows (Explorer-like alignment).
        disclosureContainer.setVisible(true);
        disclosureContainer.setManaged(true);

        // Placeholder child (lazy loading): show a visible row so expansion isn't confusing.
        if (item == null && getTreeItem() != null && getTreeItem().getParent() != null) {
            setDisable(true);
            setText("Loading...");
            iconView.setImage(null);
            // Keep disclosure spacing so "Loading..." aligns with normal rows.
            disclosureContainer.setOpacity(0.0);
            disclosureContainer.setMouseTransparent(true);
            return;
        }

        setDisable(false);

        setText(displayService != null
                ? displayService.toDisplayName(item, getTreeItem())
                : (getTreeItem() != null && getTreeItem().getParent() == null
                        ? "Computer"
                        : (item == null ? "" : item.toString())));

        boolean dark = themeService != null && themeService.isDarkPreferred();
        int px = (int) Math.round(iconView.getFitWidth() > 0 ? iconView.getFitWidth() : 16);
        iconView.setImage(IconLoader.load(IconLoader.IconType.FOLDER, dark, Math.max(16, px)));

        observedTreeItem = getTreeItem();
        if (observedTreeItem != null) {
            observedTreeItem.expandedProperty().addListener(expandedListener);
        }
        updateDisclosure();
    }

/**
 * detach.
 *
 */
    private void detach() {
        if (observedTreeItem != null) {
            observedTreeItem.expandedProperty().removeListener(expandedListener);
            observedTreeItem = null;
        }
    }

/**
 * updateDisclosure.
 *
 */
    private void updateDisclosure() {
        TreeItem<Path> ti = getTreeItem();

        if (ti == null) {
            disclosureContainer.setVisible(false);
            disclosureContainer.setManaged(false);
            return;
        }

        disclosureContainer.setVisible(true);
        disclosureContainer.setManaged(true);

        if (ti.isLeaf()) {
            // Keep spacing, but make it visually empty and non-interactive.
            disclosureChevron.setRotate(0.0);
            disclosureContainer.setOpacity(0.0);
            disclosureContainer.setMouseTransparent(true);
            return;
        }

        disclosureContainer.setOpacity(1.0);
        disclosureContainer.setMouseTransparent(false);
        disclosureChevron.setRotate(ti.isExpanded() ? 90.0 : 0.0);
    }

/**
 * chevronRight.
 *
 * @return TODO
 */
    private static PathElement[] chevronRight() {
        // A hollow chevron centered in a 16x16 box, matching Windows Explorer-ish proportions.
        // Points: (6,4) -> (10,8) -> (6,12)
        return new PathElement[] {
                new MoveTo(6.0, 4.0),
                new LineTo(10.0, 8.0),
/**
 * LineTo.
 *
 * @param 6.0 TODO
 * @param 12.0 TODO
 * @return TODO
 */
                new LineTo(6.0, 12.0)
        };
    }
}
