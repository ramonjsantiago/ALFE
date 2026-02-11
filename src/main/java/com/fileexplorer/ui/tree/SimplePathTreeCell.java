package com.fileexplorer.ui.tree;

import com.fileexplorer.service.filesystem.TreeBuildService;
import java.nio.file.Path;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;

/**
 * Extracted from MainController (Step 7.1): TreeCell for Path without file icons.
 */
public class SimplePathTreeCell extends TreeCell<Path> {

        private static final String GLYPH_CHEVRON_RIGHT = "\uE974";
        private static final String GLYPH_CHEVRON_DOWN  = "\uE972";

        private final Label chevron = new Label(GLYPH_CHEVRON_RIGHT);
        private final com.fileexplorer.service.filesystem.TreeBuildService displayService;
        private final javafx.beans.value.ChangeListener<Boolean> expandedListener = (obs, oldV, newV) -> updateChevron();
        private TreeItem<Path> observedTreeItem;
    public SimplePathTreeCell(double fixedCellSize, com.fileexplorer.service.filesystem.TreeBuildService displayService) {
            this.displayService = displayService;
            if (Double.isFinite(fixedCellSize) && fixedCellSize > 0) {
                setMinHeight(fixedCellSize);
                setPrefHeight(fixedCellSize);
                setMaxHeight(fixedCellSize);
            }


            // Explorer-like cell metrics (padding/gap)
            setContentDisplay(ContentDisplay.LEFT);
            setGraphicTextGap(5.0);
            setPadding(new Insets(0, 8, 0, 8));
            chevron.getStyleClass().add("fluent-icon");
            // Custom disclosure node -> we must handle expand/collapse ourselves.
            chevron.setMouseTransparent(false);
            chevron.setPickOnBounds(true);
            chevron.setOnMouseClicked(e -> {
                if (getTreeItem() == null) {
                    return;
                }
                getTreeItem().setExpanded(!getTreeItem().isExpanded());
                e.consume();
            });

            // Double-clicking the row should also toggle expansion (Explorer-like).
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && getTreeItem() != null && !getTreeItem().isLeaf()) {
                    getTreeItem().setExpanded(!getTreeItem().isExpanded());
                }
            });
            setDisclosureNode(chevron);
        }

        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);

            detach();

            if (empty) {
                setText(null);
                setGraphic(null);
                chevron.setVisible(false);
                chevron.setManaged(false);
                return;
            }

            // Placeholder child (lazy loading): show a visible row so expansion isn't confusing.
            if (item == null && getTreeItem() != null && getTreeItem().getParent() != null) {
                setDisable(true);
                setText("Loading...");
                return;
            }

            setDisable(false);

            // Root items may legitimately have a null Path value ("Computer").
            setText(displayService != null ? displayService.toDisplayName(item, getTreeItem()) : (getTreeItem()!=null && getTreeItem().getParent()==null ? "Computer" : (item==null?"":item.toString())));
            setGraphic(null);

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
