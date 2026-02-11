package com.fileexplorer.ui.tree;

import com.fileexplorer.service.filesystem.TreeBuildService;
import com.fileexplorer.service.theme.ThemeService;
import com.fileexplorer.util.IconLoader;
import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Extracted from MainController (Step 7.1): TreeCell for Path with file icons.
 */
public class IconPathTreeCell extends TreeCell<Path> {

        private static final String GLYPH_CHEVRON_RIGHT = "\uE974";
        private static final String GLYPH_CHEVRON_DOWN  = "\uE972";

        private final ImageView iconView = new ImageView();
        private final ThemeService themeService;
        private final com.fileexplorer.service.filesystem.TreeBuildService displayService;
        private final Label chevron = new Label(GLYPH_CHEVRON_RIGHT);
        private final javafx.beans.value.ChangeListener<Boolean> expandedListener = (obs, oldV, newV) -> updateChevron();
        private TreeItem<Path> observedTreeItem;
    public IconPathTreeCell(double fixedCellSize, ThemeService themeService, com.fileexplorer.service.filesystem.TreeBuildService displayService) {
            this.displayService = displayService;
            this.themeService = themeService;


            // Explorer-like cell metrics (padding/gap)
            setContentDisplay(ContentDisplay.LEFT);
            setGraphicTextGap(5.0);
            setPadding(new Insets(0, 8, 0, 8));
            if (Double.isFinite(fixedCellSize) && fixedCellSize > 0) {
                setMinHeight(fixedCellSize);
                setPrefHeight(fixedCellSize);
                setMaxHeight(fixedCellSize);
            }

            // Icon sizing: fixed 16x16 to match Explorer-like nav metrics.
            iconView.setFitWidth(16.0);
            iconView.setFitHeight(16.0);

            iconView.setPreserveRatio(true);
            setGraphic(iconView);

            chevron.getStyleClass().add("fluent-icon");
            // Custom disclosure node -> handle expand/collapse ourselves.
            chevron.setMouseTransparent(false);
            chevron.setPickOnBounds(true);
            chevron.setOnMouseClicked(e -> {
                TreeItem<Path> ti = getTreeItem();
                if (ti == null) {
                    return;
                }
                ti.setExpanded(!ti.isExpanded());
                e.consume();
            });
            setDisclosureNode(chevron);

            // Double-click row toggles expansion, similar to native Explorer.
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && getTreeItem() != null) {
                    getTreeItem().setExpanded(!getTreeItem().isExpanded());
                    e.consume();
                }
            });
        }

        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);

            detach();

            if (empty) {
                setText(null);
                iconView.setImage(null);
                chevron.setVisible(false);
                chevron.setManaged(false);
                return;
            }

            // Placeholder child (lazy loading): show a visible row so expansion isn't confusing.
            if (item == null && getTreeItem() != null && getTreeItem().getParent() != null) {
                setDisable(true);
                setText("Loading...");
                iconView.setImage(null);
                chevron.setVisible(false);
                chevron.setManaged(false);
                return;
            }

            setDisable(false);

            // Root items may legitimately have a null Path value ("Computer").
            setText(displayService != null ? displayService.toDisplayName(item, getTreeItem()) : (getTreeItem()!=null && getTreeItem().getParent()==null ? "Computer" : (item==null?"":item.toString())));

            boolean dark = themeService != null && themeService.isDarkPreferred();
            int px = (int) Math.round(iconView.getFitWidth() > 0 ? iconView.getFitWidth() : 16);
            iconView.setImage(IconLoader.load(IconLoader.IconType.FOLDER, dark, Math.max(16, px)));

            observedTreeItem = getTreeItem();
            if (observedTreeItem != null) {
                observedTreeItem.expandedProperty().addListener(expandedListener);
            }
            updateChevron();
        }

        private void detach() {
            if (observedTreeItem != null) {
                observedTreeItem.expandedProperty().removeListener(expandedListener);
                observedTreeItem = null;
            }
        }

        private void updateChevron() {
            TreeItem<Path> ti = getTreeItem();
            boolean show = ti != null && !ti.isLeaf();
            chevron.setVisible(show);
            chevron.setManaged(show);
            chevron.setText(ti != null && ti.isExpanded() ? GLYPH_CHEVRON_DOWN : GLYPH_CHEVRON_RIGHT);
        }

}
