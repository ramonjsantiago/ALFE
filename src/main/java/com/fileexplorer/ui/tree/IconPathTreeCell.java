package com.fileexplorer.ui.tree;

import com.fileexplorer.service.filesystem.TreeBuildService;
import com.fileexplorer.service.theme.ThemeService;
import com.fileexplorer.util.IconLoader;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
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

    private static final PseudoClass TOP_LEVEL_PSEUDO = PseudoClass.getPseudoClass("top-level");
    private static final PseudoClass ROOT_DRIVE_PSEUDO = PseudoClass.getPseudoClass("root-drive");
    private static final PseudoClass SPECIAL_FOLDER_PSEUDO = PseudoClass.getPseudoClass("special-folder");
    private static final PseudoClass LEAF_ITEM_PSEUDO = PseudoClass.getPseudoClass("leaf-item");
    private static final PseudoClass BRANCH_ITEM_PSEUDO = PseudoClass.getPseudoClass("branch-item");

    private final ImageView iconView = new ImageView();
    private final StackPane iconContainer = new StackPane();
    private final ThemeService themeService;
    private final TreeBuildService displayService;
    private final BiConsumer<Path, String> renameHandler;

    private final StackPane disclosureContainer = new StackPane();
    private final javafx.scene.shape.Path disclosureChevron = new javafx.scene.shape.Path();

    private final HBox inlineRenameBox = new HBox(8.0);
    private final ImageView inlineRenameIconView = new ImageView();
    private final StackPane inlineRenameIconContainer = new StackPane();
    private final TextField inlineRenameField = new TextField();
    private boolean suppressFocusCommit;

    private final ChangeListener<Boolean> expandedListener = (obs, oldV, newV) -> updateDisclosure();
    private TreeItem<Path> observedTreeItem;

    private final ChangeListener<TreeItem<Path>> treeItemListener = (obs, oldTi, newTi) -> {
        detach();
        observedTreeItem = newTi;
        if (observedTreeItem != null) {
            observedTreeItem.expandedProperty().addListener(expandedListener);
        }
        updateDisclosure();
    };

    public IconPathTreeCell(double fixedCellSize, ThemeService themeService, TreeBuildService displayService) {
        this(fixedCellSize, themeService, displayService, null);
    }

    public IconPathTreeCell(double fixedCellSize,
                            ThemeService themeService,
                            TreeBuildService displayService,
                            BiConsumer<Path, String> renameHandler) {
        this.displayService = displayService;
        this.themeService = themeService;
        this.renameHandler = renameHandler;

        setContentDisplay(ContentDisplay.LEFT);
        setAlignment(Pos.CENTER_LEFT);
        setGraphicTextGap(8.0);
        getStyleClass().add("explorer-nav-cell");
        setPadding(new Insets(4, 8, 4, 6));
        setStyle("-fx-padding: 4 8 4 6; -fx-alignment: CENTER-LEFT;");
        setEditable(true);

        if (Double.isFinite(fixedCellSize) && fixedCellSize > 0) {
            setMinHeight(fixedCellSize);
            setPrefHeight(fixedCellSize);
            setMaxHeight(fixedCellSize);
        }

        iconView.setFitWidth(16.0);
        iconView.setFitHeight(16.0);
        iconView.setPreserveRatio(true);

        iconContainer.setAlignment(Pos.CENTER);
        iconContainer.setMinSize(18.0, 18.0);
        iconContainer.setPrefSize(18.0, 18.0);
        iconContainer.setMaxSize(18.0, 18.0);
        iconContainer.getChildren().setAll(iconView);
        setGraphic(iconContainer);

        inlineRenameIconView.setFitWidth(16.0);
        inlineRenameIconView.setFitHeight(16.0);
        inlineRenameIconView.setPreserveRatio(true);
        inlineRenameIconContainer.setAlignment(Pos.CENTER);
        inlineRenameIconContainer.setMinSize(18.0, 18.0);
        inlineRenameIconContainer.setPrefSize(18.0, 18.0);
        inlineRenameIconContainer.setMaxSize(18.0, 18.0);
        inlineRenameIconContainer.getChildren().setAll(inlineRenameIconView);
        inlineRenameBox.setAlignment(Pos.CENTER_LEFT);
        inlineRenameField.getStyleClass().add("explorer-inline-rename-field");
        inlineRenameField.setPrefColumnCount(20);
        inlineRenameField.setOnAction(e -> commitInlineRename());
        inlineRenameField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV && isEditing() && !suppressFocusCommit) {
                commitInlineRename();
            }
        });
        inlineRenameField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                suppressFocusCommit = true;
                cancelEdit();
                e.consume();
            }
        });
        inlineRenameBox.getChildren().setAll(inlineRenameIconContainer, inlineRenameField);

        disclosureChevron.getElements().setAll(chevronRight());
        disclosureChevron.setFill(null);
        disclosureChevron.setStrokeLineCap(StrokeLineCap.ROUND);
        disclosureChevron.setStrokeLineJoin(StrokeLineJoin.ROUND);
        disclosureChevron.setStrokeWidth(1.2);
        disclosureChevron.setOpacity(0.82);
        disclosureChevron.strokeProperty().bind(textFillProperty());

        disclosureContainer.setAlignment(Pos.CENTER);
        disclosureContainer.setMinSize(16.0, 16.0);
        disclosureContainer.setPrefSize(16.0, 16.0);
        disclosureContainer.setMaxSize(16.0, 16.0);
        disclosureContainer.setPadding(new Insets(0, 1, 0, 0));
        disclosureContainer.setTranslateY(0.0);
        disclosureChevron.setTranslateY(0.0);
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
        treeItemProperty().addListener(treeItemListener);

        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !isEditing()) {
                TreeItem<Path> ti = getTreeItem();
                if (ti != null && !ti.isLeaf()) {
                    ti.setExpanded(!ti.isExpanded());
                    e.consume();
                }
            }
        });
    }

    @Override
    public void startEdit() {
        javafx.scene.control.TreeView<Path> treeView = getTreeView();
        if (!isEditable() || treeView == null || !treeView.isEditable() || isEmpty()) {
            return;
        }
        Path item = getItem();
        TreeItem<Path> treeItem = getTreeItem();
        if (item == null || treeItem == null || treeItem.getParent() == null) {
            return;
        }
        super.startEdit();
        suppressFocusCommit = false;
        showInlineRenameEditor(item);
        Platform.runLater(() -> {
            inlineRenameField.requestFocus();
            inlineRenameField.selectAll();
        });
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        suppressFocusCommit = false;
        restoreNormalPresentation(getItem());
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        alignRowContentToVisualCenter();
    }

    private void alignRowContentToVisualCenter() {
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double rowCenterY = top + ((getHeight() - top - bottom) * 0.5);

        centerNodeVertically(lookup(".text"), rowCenterY, 0.5);
        centerNodeVertically(getGraphic(), rowCenterY, 0.0);
        centerNodeVertically(getDisclosureNode(), rowCenterY, 0.0);
    }

    private void centerNodeVertically(Node node, double rowCenterY, double extraNudgeY) {
        if (node == null || !node.isVisible()) {
            return;
        }
        double height = node.getBoundsInParent().getHeight();
        if (height <= 0.0) {
            return;
        }
        double baseCenterY = node.getBoundsInParent().getMinY() + (height * 0.5) - node.getTranslateY();
        double targetTranslateY = (rowCenterY - baseCenterY) + extraNudgeY;
        node.setTranslateY(Math.rint(targetTranslateY));
    }

    @Override
    protected void updateItem(Path item, boolean empty) {
        super.updateItem(item, empty);

        detach();

        if (empty) {
            setText(null);
            setGraphic(null);
            iconView.setImage(null);
            inlineRenameIconView.setImage(null);
            disclosureContainer.setVisible(false);
            disclosureContainer.setManaged(false);
            clearStructuralPseudoClasses();
            return;
        }

        disclosureContainer.setVisible(true);
        disclosureContainer.setManaged(true);

        if (item == null && getTreeItem() != null && getTreeItem().getParent() != null) {
            setDisable(true);
            setText("Loading...");
            setGraphic(iconContainer);
            iconView.setImage(null);
            inlineRenameIconView.setImage(null);
            disclosureContainer.setOpacity(0.0);
            disclosureContainer.setMouseTransparent(true);
            return;
        }

        setDisable(false);
        updateStructuralPseudoClasses(item);
        updateFolderIcon();

        observedTreeItem = getTreeItem();
        if (observedTreeItem != null) {
            observedTreeItem.expandedProperty().addListener(expandedListener);
        }
        updateDisclosure();

        if (isEditing()) {
            showInlineRenameEditor(item);
        } else {
            restoreNormalPresentation(item);
        }
    }

    private void updateFolderIcon() {
        boolean dark = themeService != null && themeService.isDarkPreferred();
        int px = (int) Math.round(iconView.getFitWidth() > 0 ? iconView.getFitWidth() : 16);
        Image image = IconLoader.load(IconLoader.IconType.FOLDER, dark, Math.max(16, px));
        iconView.setImage(image);
        inlineRenameIconView.setImage(image);
    }

    private void showInlineRenameEditor(Path item) {
        inlineRenameField.setText(currentDisplayName(item));
        inlineRenameField.positionCaret(inlineRenameField.getText().length());
        setText(null);
        setGraphic(inlineRenameBox);
    }

    private void restoreNormalPresentation(Path item) {
        if (item == null && getTreeItem() != null && getTreeItem().getParent() != null) {
            setText("Loading...");
            setGraphic(iconContainer);
            return;
        }
        setText(currentDisplayName(item));
        setGraphic(iconContainer);
    }

    private String currentDisplayName(Path item) {
        if (displayService != null) {
            return displayService.toDisplayName(item, getTreeItem());
        }
        if (getTreeItem() != null && getTreeItem().getParent() == null) {
            return "Computer";
        }
        return item == null ? "" : item.toString();
    }

    private void commitInlineRename() {
        Path item = getItem();
        if (item == null) {
            cancelEdit();
            return;
        }
        String newName = inlineRenameField.getText();
        super.cancelEdit();
        suppressFocusCommit = false;
        if (renameHandler != null) {
            renameHandler.accept(item, newName);
        } else {
            restoreNormalPresentation(item);
        }
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

    private void clearStructuralPseudoClasses() {
        pseudoClassStateChanged(TOP_LEVEL_PSEUDO, false);
        pseudoClassStateChanged(ROOT_DRIVE_PSEUDO, false);
        pseudoClassStateChanged(SPECIAL_FOLDER_PSEUDO, false);
        pseudoClassStateChanged(LEAF_ITEM_PSEUDO, false);
        pseudoClassStateChanged(BRANCH_ITEM_PSEUDO, false);
    }

    private void updateStructuralPseudoClasses(Path item) {
        TreeItem<Path> ti = getTreeItem();
        boolean topLevel = ti != null && ti.getParent() != null && ti.getParent().getParent() == null;
        boolean rootDrive = item != null && item.getParent() == null;
        boolean specialFolder = topLevel && item != null && !rootDrive;
        boolean leaf = ti != null && ti.isLeaf();

        pseudoClassStateChanged(TOP_LEVEL_PSEUDO, topLevel);
        pseudoClassStateChanged(ROOT_DRIVE_PSEUDO, rootDrive);
        pseudoClassStateChanged(SPECIAL_FOLDER_PSEUDO, specialFolder);
        pseudoClassStateChanged(LEAF_ITEM_PSEUDO, leaf);
        pseudoClassStateChanged(BRANCH_ITEM_PSEUDO, !leaf);
    }

    private static PathElement[] chevronRight() {
        return new PathElement[] {
                new MoveTo(6.5, 4.5),
                new LineTo(9.5, 8.0),
                new LineTo(6.5, 11.5)
        };
    }
}
