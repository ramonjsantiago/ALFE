package com.fileexplorer.ui.tree;

import com.fileexplorer.service.filesystem.TreeBuildService;
import java.nio.file.Path;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * TreeCell for Path without file icons.
 *
 * Uses a font-free, hollow chevron disclosure (stroke-only Path) to match the
 * Windows Explorer look without requiring bundled icon fonts.
 */
public class SimplePathTreeCell extends TreeCell<Path> {

    private final TreeBuildService displayService;

    private final StackPane disclosureContainer = new StackPane();
    private final javafx.scene.shape.Path disclosureChevron = new javafx.scene.shape.Path();

    private final ChangeListener<Boolean> expandedListener = (obs, oldV, newV) -> updateDisclosure();
    private TreeItem<Path> observedTreeItem;

    public SimplePathTreeCell(double fixedCellSize, TreeBuildService displayService) {
        this.displayService = displayService;

        if (Double.isFinite(fixedCellSize) && fixedCellSize > 0) {
            setMinHeight(fixedCellSize);
            setPrefHeight(fixedCellSize);
            setMaxHeight(fixedCellSize);
        }

        setContentDisplay(ContentDisplay.LEFT);
        setGraphicTextGap(5.0);
        setPadding(new Insets(0, 8, 0, 8));

        // Hollow chevron (stroke-only). Right-pointing base; rotate 90° for expanded.
        disclosureChevron.getElements().setAll(chevronRight());
        disclosureChevron.setFill(null);
        disclosureChevron.setStrokeLineCap(StrokeLineCap.ROUND);
        disclosureChevron.setStrokeLineJoin(StrokeLineJoin.ROUND);
        disclosureChevron.setStrokeWidth(1.5);
        disclosureChevron.strokeProperty().bind(textFillProperty());

        disclosureContainer.setAlignment(Pos.CENTER);
        disclosureContainer.setMinSize(16.0, 16.0);
        disclosureContainer.setPrefSize(16.0, 16.0);
        disclosureContainer.setMaxSize(16.0, 16.0);
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

        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<Path> ti = getTreeItem();
                if (ti != null && !ti.isLeaf()) {
                    ti.setExpanded(!ti.isExpanded());
                    e.consume();
                }
            }
        });

        setDisclosureNode(disclosureContainer);
    }

    @Override
    protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);

        detach();

        if (empty) {
            setText(null);
            setGraphic(null);
            disclosureContainer.setVisible(false);
            disclosureContainer.setManaged(false);
            return;
        }

        // Placeholder child (lazy loading): show a visible row so expansion isn't confusing.
        if (item == null && getTreeItem() != null && getTreeItem().getParent() != null) {
            setDisable(true);
            setText("Loading...");
            disclosureContainer.setVisible(false);
            disclosureContainer.setManaged(false);
            return;
        }

        setDisable(false);

        setText(displayService != null
                ? displayService.toDisplayName(item, getTreeItem())
                : (getTreeItem() != null && getTreeItem().getParent() == null
                        ? "Computer"
                        : (item == null ? "" : item.toString())));

        setGraphic(null);

        observedTreeItem = getTreeItem();
        if (observedTreeItem != null) {
            observedTreeItem.expandedProperty().addListener(expandedListener);
        }
        updateDisclosure();
    }

    private void detach() {
        if (observedTreeItem != null) {
            observedTreeItem.expandedProperty().removeListener(expandedListener);
            observedTreeItem = null;
        }
    }

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
            disclosureChevron.setRotate(0.0);
            disclosureContainer.setOpacity(0.0);
            disclosureContainer.setMouseTransparent(true);
            return;
        }

        disclosureContainer.setOpacity(1.0);
        disclosureContainer.setMouseTransparent(false);
        disclosureChevron.setRotate(ti.isExpanded() ? 90.0 : 0.0);
    }

    private static PathElement[] chevronRight() {
        return new PathElement[] {
                new MoveTo(6.0, 4.0),
                new LineTo(10.0, 8.0),
                new LineTo(6.0, 12.0)
        };
    }
}