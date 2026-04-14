package com.fileexplorer.ui.table;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Installs an Explorer-like header context menu on a JavaFX TableView.
 */
public final class TableHeaderContextMenuInstaller {

    private TableHeaderContextMenuInstaller() {}

    /** TableView property keys used to coordinate context menu visibility. */
    public static final String PROP_HEADER_MENU = "fileexplorer.headerMenu";
    public static final String PROP_HEADER_PRESET_MENU = "fileexplorer.headerPresetMenu";
    public static final String PROP_FILEOPS_MENU = "fileexplorer.fileOpsMenu";
    private static final String PROP_HEADER_SELECTED_PRESET_KEYS = "fileexplorer.headerSelectedPresetKeys";
    private static final String PROP_HEADER_MENU_CHEVRON_POPUP = "fileexplorer.headerMenuChevronPopup";
    private static final String PROP_HEADER_MENU_CHEVRON_NODE = "fileexplorer.headerMenuChevronNode";
    private static final String PROP_HEADER_SCENE_DISMISS_HANDLER = "fileexplorer.headerMenuSceneDismissHandler";
    private static final String PROP_HEADER_SCENE_DISMISS_SCENE = "fileexplorer.headerMenuSceneDismissScene";
    private static final double HEADER_MENU_HOTZONE_PX = 24.0;
    private static final double HEADER_MENU_RESIZE_EXCLUSION_PX = 6.0;
    private static final double HEADER_MENU_CHEVRON_WIDTH_PX = 8.0;
    private static final double HEADER_MENU_CHEVRON_HEIGHT_PX = 5.0;
    private static final double HEADER_MENU_CHEVRON_RIGHT_INSET_PX = 10.0;

        public static void install(
            TableView<?> table,
            Map<String, TableColumn<?, ?>> detailsColumns,
            Runnable showChooseDetailsDialog
    ) {
        Objects.requireNonNull(detailsColumns, "detailsColumns");
        install(table, () -> detailsColumns, showChooseDetailsDialog);
    }

    public static void install(
            TableView<?> table,
            Supplier<Map<String, TableColumn<?, ?>>> detailsColumnsSupplier,
            Runnable showChooseDetailsDialog
    ) {
        Objects.requireNonNull(detailsColumnsSupplier, "detailsColumnsSupplier");
        install(
                table,
                detailsColumnsSupplier,
                label -> {
                    TableColumn<?, ?> col = detailsColumnsSupplier.get().get(label);
                    return col != null && col.isVisible();
                },
                (label, visible) -> {
                    TableColumn<?, ?> col = detailsColumnsSupplier.get().get(label);
                    if (col != null) {
                        col.setVisible(visible);
                    }
                },
                () -> { },
                showChooseDetailsDialog
        );
    }

    public static void install(
            TableView<?> table,
            Supplier<Map<String, TableColumn<?, ?>>> detailsColumnsSupplier,
            Function<String, Boolean> visibilityStateResolver,
            BiConsumer<String, Boolean> visibilityApplier,
            Runnable restoreDefaultColumns,
            Runnable showChooseDetailsDialog
    ) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(detailsColumnsSupplier, "detailsColumnsSupplier");
        Objects.requireNonNull(visibilityStateResolver, "visibilityStateResolver");
        Objects.requireNonNull(visibilityApplier, "visibilityApplier");
        Objects.requireNonNull(restoreDefaultColumns, "restoreDefaultColumns");
        Objects.requireNonNull(showChooseDetailsDialog, "showChooseDetailsDialog");

        // One shared menu instance per TableView, always hide before re-showing.
        final Holder<TableColumn<?, ?>> currentClickedColumn = new Holder<>();

        Platform.runLater(() -> {
            installSceneDismissOnOutsideClick(table);

            table.addEventFilter(MouseEvent.MOUSE_MOVED, evt -> {
                updateHeaderHotzoneState(evt);
            });

            table.addEventFilter(MouseEvent.MOUSE_EXITED, evt -> clearAllHeaderHotzoneState(table));

            table.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
                if (evt.getButton() != MouseButton.PRIMARY) return;
                if (!isHeaderEvent(evt) || !isHeaderMenuTriggerZone(evt)) {
                    hideExistingMenus(table);
                    if (!isHeaderEvent(evt)) {
                        clearAllHeaderHotzoneState(table);
                    }
                    return;
                }
                evt.consume();
            });

            table.addEventFilter(MouseEvent.MOUSE_RELEASED, evt -> {
                if (evt.getButton() != MouseButton.PRIMARY) return;
                if (!isHeaderEvent(evt)) return;
                if (!isHeaderMenuTriggerZone(evt)) return;

                TableColumn<?, ?> clickedColumnSnapshot = findClickedColumn(evt);
                currentClickedColumn.value = clickedColumnSnapshot;
                if (clickedColumnSnapshot == null) {
                    return;
                }

                hideExistingMenus(table);

                Map<String, TableColumn<?, ?>> currentColumnsSnapshot = snapshotDetailsColumns(detailsColumnsSupplier.get());
                final ContextMenu presetMenu = buildCompactVisibleColumnsMenu(
                        table,
                        clickedColumnSnapshot,
                        currentColumnsSnapshot,
                        visibilityStateResolver
                );
                table.getProperties().put(PROP_HEADER_PRESET_MENU, presetMenu);

                Bounds headerBounds = findHeaderBounds(evt);
                if (headerBounds != null) {
                    showMenuAttachedToHeader(presetMenu, table, headerBounds, true);
                } else {
                    presetMenu.show(table, evt.getScreenX(), evt.getScreenY());
                }

                evt.consume();
            });

            // Capture phase on the standard ContextMenuEvent (fires on release). This avoids header nodes
            // consuming mouse events and makes behavior consistent across skins.
            table.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, evt -> {
                if (!isHeaderEvent(evt)) return;

                TableColumn<?, ?> clickedColumnSnapshot = findClickedColumn(evt);
                currentClickedColumn.value = clickedColumnSnapshot;

                hideExistingMenus(table);

                Map<String, TableColumn<?, ?>> currentColumnsSnapshot = snapshotDetailsColumns(detailsColumnsSupplier.get());
                final ContextMenu headerMenu = buildMenu(
                        table,
                        clickedColumnSnapshot,
                        currentColumnsSnapshot,
                        visibilityStateResolver,
                        visibilityApplier,
                        restoreDefaultColumns,
                        showChooseDetailsDialog
                );
                table.getProperties().put(PROP_HEADER_MENU, headerMenu);
                headerMenu.show(table, evt.getScreenX(), evt.getScreenY());

                evt.consume();
            });
        });
    }

    private static final class Holder<T> { T value; }

    private record PresetDefinition(
            String key,
            String label,
            String iconText,
            TableColumn.SortType sortType
    ) {}


    private static ContextMenu buildCompactVisibleColumnsMenu(
            TableView<?> table,
            TableColumn<?, ?> clickedColumn,
            Map<String, TableColumn<?, ?>> detailsColumns,
            Function<String, Boolean> visibilityStateResolver
    ) {
        ContextMenu menu = new ContextMenu();
        menu.setAutoHide(true);
        menu.getStyleClass().addAll("explorer-header-details-menu", "explorer-header-details-preset-menu");
        rebuildCompactVisibleColumnsMenu(menu, table, clickedColumn, detailsColumns, visibilityStateResolver);
        return menu;
    }

    private static void rebuildCompactVisibleColumnsMenu(
            ContextMenu menu,
            TableView<?> table,
            TableColumn<?, ?> clickedColumn,
            Map<String, TableColumn<?, ?>> detailsColumns,
            Function<String, Boolean> visibilityStateResolver
    ) {
        menu.getItems().clear();
        if (detailsColumns == null || detailsColumns.isEmpty()) {
            return;
        }

        List<VisibleColumnSnapshot> visibleColumns = resolveVisibleColumnSnapshots(detailsColumns, visibilityStateResolver, clickedColumn);
        for (VisibleColumnSnapshot visibleColumn : visibleColumns) {
            ExplorerPresetRow row = new ExplorerPresetRow(visibleColumn.label(), visibleColumn.iconText());
            row.setSelected(true);
            if (visibleColumn.active()) {
                row.text.getStyleClass().add("explorer-menu-active-column-text");
            }
            row.item.setOnAction(ae -> {
                applyPrimarySort(table, visibleColumn.column(), TableColumn.SortType.ASCENDING);
                menu.hide();
            });
            menu.getItems().add(row.item);
        }
    }


    private static ContextMenu buildMenu(
            TableView<?> table,
            TableColumn<?, ?> clickedColumn,
            Map<String, TableColumn<?, ?>> detailsColumns,
            Function<String, Boolean> visibilityStateResolver,
            BiConsumer<String, Boolean> visibilityApplier,
            Runnable restoreDefaultColumns,
            Runnable showChooseDetailsDialog
    ) {
        ContextMenu menu = new ContextMenu();
        menu.setAutoHide(true);
        menu.getStyleClass().add("explorer-header-details-menu");

        CustomMenuItem sizeCol = buildAlignedActionItem("Size Column to Fit", () -> {
            if (clickedColumn != null) {
                ColumnAutoFitUtil.sizeToFit(table, clickedColumn);
            }
            menu.hide();
        });

        CustomMenuItem sizeAll = buildAlignedActionItem("Size All Columns to Fit", () -> {
            ColumnAutoFitUtil.sizeAllToFit(table);
            menu.hide();
        });

        addMenuItem(menu, sizeCol, false);
        addMenuItem(menu, sizeAll, true);

        // Only show currently checked / visible details in the header popup, in the canonical Explorer order.
        final Map<String, ExplorerCheckRow> checkRows = new LinkedHashMap<>();
        final List<String> visibleDetailLabels = new ArrayList<>();
        for (Map.Entry<String, TableColumn<?, ?>> e : detailsColumns.entrySet()) {
            String label = e.getKey();
            TableColumn<?, ?> col = e.getValue();
            if (col == null) {
                continue;
            }

            boolean visible = "Name".equals(label) || Boolean.TRUE.equals(visibilityStateResolver.apply(label));
            if (visible) {
                visibleDetailLabels.add(label);
            }
        }

        for (String label : visibleDetailLabels) {
            ExplorerCheckRow row = new ExplorerCheckRow(label);
            if ("Name".equals(label)) {
                row.setLocked(true);
                row.setSelected(true);
            } else {
                row.setSelected(true);
                row.item.setOnAction((ActionEvent ae) -> {
                    visibilityApplier.accept(label, row.isSelected());
                    menu.hide();
                });
            }

            checkRows.put(label, row);
            boolean separatorAfter = shouldAddTrailingDetailsSeparator(label, visibleDetailLabels);
            addMenuItem(menu, row.item, separatorAfter);
        }

        CustomMenuItem more = buildAlignedActionItem("More...", () -> {
            menu.hide();
            showChooseDetailsDialog.run();
        });
        menu.getItems().add(more);

        sizeCol.setDisable(clickedColumn == null);

        return menu;
    }


    private static boolean shouldAddTrailingDetailsSeparator(String label, List<String> visibleDetailLabels) {
        if (label == null || visibleDetailLabels == null || visibleDetailLabels.isEmpty()) {
            return false;
        }
        if ("Title".equals(label)) {
            return true;
        }
        String lastVisibleLabel = visibleDetailLabels.get(visibleDetailLabels.size() - 1);
        return !visibleDetailLabels.contains("Title") && label.equals(lastVisibleLabel);
    }


    private record VisibleColumnSnapshot(
            String label,
            String iconText,
            TableColumn<?, ?> column,
            boolean active
    ) {}

    private static List<VisibleColumnSnapshot> resolveVisibleColumnSnapshots(
            Map<String, TableColumn<?, ?>> detailsColumns,
            Function<String, Boolean> visibilityStateResolver,
            TableColumn<?, ?> clickedColumn
    ) {
        List<VisibleColumnSnapshot> visibleColumns = new ArrayList<>();
        if (detailsColumns == null || detailsColumns.isEmpty()) {
            return visibleColumns;
        }
        for (Map.Entry<String, TableColumn<?, ?>> entry : detailsColumns.entrySet()) {
            String label = entry.getKey();
            TableColumn<?, ?> column = entry.getValue();
            if (label == null || column == null) {
                continue;
            }
            boolean visible = "Name".equals(label) || Boolean.TRUE.equals(visibilityStateResolver.apply(label));
            if (!visible) {
                continue;
            }
            visibleColumns.add(new VisibleColumnSnapshot(
                    label,
                    resolveVisibleColumnIconText(label),
                    column,
                    Objects.equals(clickedColumn, column)
            ));
        }
        return visibleColumns;
    }

    private static String resolveVisibleColumnIconText(String label) {
        if (label == null || label.isBlank()) {
            return "•";
        }
        return switch (label.trim().toLowerCase()) {
            case "name" -> "A";
            case "date modified" -> "D";
            case "type" -> "T";
            case "size" -> "S";
            case "date created" -> "C";
            case "authors" -> "A";
            case "tags" -> "#";
            case "title" -> "T";
            default -> label.substring(0, 1).toUpperCase();
        };
    }

    private static Map<String, TableColumn<?, ?>> snapshotDetailsColumns(Map<String, TableColumn<?, ?>> detailsColumns) {
        Map<String, TableColumn<?, ?>> snapshot = new LinkedHashMap<>();
        if (detailsColumns == null || detailsColumns.isEmpty()) {
            return snapshot;
        }
        for (Map.Entry<String, TableColumn<?, ?>> entry : detailsColumns.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                snapshot.put(entry.getKey(), entry.getValue());
            }
        }
        return snapshot;
    }

    private static void showMenuAttachedToHeader(ContextMenu menu, TableView<?> table, Bounds headerBounds, boolean rightEdgeAligned) {
        if (menu == null || table == null || headerBounds == null) {
            return;
        }
        double initialX = rightEdgeAligned ? headerBounds.getMaxX() - 2.0 : headerBounds.getMinX();
        double initialY = headerBounds.getMaxY() - 1.0;
        menu.setOnShown(evt -> alignMenuToHeader(menu, headerBounds, rightEdgeAligned));
        menu.show(table, initialX, initialY);
    }

    private static void alignMenuToHeader(ContextMenu menu, Bounds headerBounds, boolean rightEdgeAligned) {
        if (menu == null || headerBounds == null || menu.getScene() == null || menu.getScene().getWindow() == null) {
            return;
        }
        Window window = menu.getScene().getWindow();
        double width = window.getWidth();
        double x = rightEdgeAligned
                ? Math.round(headerBounds.getMaxX() - Math.max(0.0, width) + 2.0)
                : Math.round(headerBounds.getMinX());
        double y = Math.round(headerBounds.getMaxY() - 1.0);
        window.setX(x);
        window.setY(y);
    }

    private static void addMenuItem(ContextMenu menu, MenuItem item, boolean separatorAfter) {
        if (menu == null || item == null) {
            return;
        }
        menu.getItems().add(item);
        if (separatorAfter) {
            menu.getItems().add(new SeparatorMenuItem());
        }
    }


    private static CustomMenuItem buildAlignedActionItem(String label, Runnable action) {
        Label lead = new Label("");
        lead.getStyleClass().add("explorer-menu-checkmark");
        lead.setMinWidth(18);
        lead.setPrefWidth(18);
        lead.setMaxWidth(18);

        Label text = new Label(label);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(8, lead, text, spacer);
        row.getStyleClass().add("explorer-menu-checkrow");

        CustomMenuItem item = new CustomMenuItem(row, true);
        item.getStyleClass().add("explorer-menu-plainitem");
        item.setOnAction(ae -> action.run());
        row.setOnMouseReleased(ev -> item.fire());
        return item;
    }




    private static void installSceneDismissOnOutsideClick(TableView<?> table) {
        if (table == null) {
            return;
        }

        table.sceneProperty().addListener((obs, oldScene, newScene) -> {
            uninstallSceneDismissOnOutsideClick(table, oldScene);
            installSceneDismissOnOutsideClick(table, newScene);
        });
        installSceneDismissOnOutsideClick(table, table.getScene());
    }

    private static void installSceneDismissOnOutsideClick(TableView<?> table, Scene scene) {
        if (table == null || scene == null) {
            return;
        }
        if (table.getProperties().get(PROP_HEADER_SCENE_DISMISS_SCENE) == scene) {
            return;
        }

        EventHandler<MouseEvent> handler = evt -> {
            MouseButton button = evt.getButton();
            if (button != MouseButton.PRIMARY && button != MouseButton.SECONDARY && button != MouseButton.MIDDLE) {
                return;
            }
            if (isHeaderEvent(evt) && isHeaderMenuTriggerZone(evt)) {
                return;
            }
            hideExistingMenus(table);
            if (!isHeaderEvent(evt)) {
                clearAllHeaderHotzoneState(table);
            }
        };

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, handler);
        table.getProperties().put(PROP_HEADER_SCENE_DISMISS_HANDLER, handler);
        table.getProperties().put(PROP_HEADER_SCENE_DISMISS_SCENE, scene);
    }

    private static void uninstallSceneDismissOnOutsideClick(TableView<?> table, Scene scene) {
        if (table == null || scene == null) {
            return;
        }
        Object existingHandler = table.getProperties().get(PROP_HEADER_SCENE_DISMISS_HANDLER);
        if (existingHandler instanceof EventHandler<?> rawHandler) {
            @SuppressWarnings("unchecked")
            EventHandler<MouseEvent> handler = (EventHandler<MouseEvent>) rawHandler;
            scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, handler);
        }
        if (table.getProperties().get(PROP_HEADER_SCENE_DISMISS_SCENE) == scene) {
            table.getProperties().remove(PROP_HEADER_SCENE_DISMISS_SCENE);
        }
        table.getProperties().remove(PROP_HEADER_SCENE_DISMISS_HANDLER);
    }

    private static void hideExistingMenus(TableView<?> table) {
        hideHeaderMenuChevron(table);

        if (table.getContextMenu() != null) table.getContextMenu().hide();

        Object other = table.getProperties().get(PROP_FILEOPS_MENU);
        if (other instanceof ContextMenu cm) cm.hide();

        Object preset = table.getProperties().get(PROP_HEADER_PRESET_MENU);
        if (preset instanceof ContextMenu cm) {
            cm.hide();
        }

        Object existing = table.getProperties().get(PROP_HEADER_MENU);
        if (existing instanceof ContextMenu cm) {
            cm.hide();
        }
    }


    public static void resetEphemeralHeaderState(TableView<?> table) {
        if (table == null) {
            return;
        }
        hideExistingMenus(table);
        clearAllHeaderHotzoneState(table);
    }

    private static boolean isHeaderEvent(MouseEvent evt) {
        return findHeaderNode(evt) != null;
    }

    private static Bounds findHeaderBounds(MouseEvent evt) {
        Node header = findHeaderNode(evt);
        return header != null ? header.localToScreen(header.getBoundsInLocal()) : null;
    }

    private static boolean isHeaderMenuTriggerZone(MouseEvent evt) {
        Node header = findHeaderNode(evt);
        if (header == null) return false;
        Point2D local = header.screenToLocal(evt.getScreenX(), evt.getScreenY());
        if (local == null) return false;
        double width = header.getLayoutBounds().getWidth();
        if (width <= HEADER_MENU_HOTZONE_PX + HEADER_MENU_RESIZE_EXCLUSION_PX) return false;
        double left = Math.max(0.0, width - HEADER_MENU_HOTZONE_PX);
        double right = Math.max(left, width - HEADER_MENU_RESIZE_EXCLUSION_PX);
        return local.getX() >= left && local.getX() <= right;
    }

    private static Node findHeaderNode(MouseEvent evt) {
        Node target = (evt.getPickResult() != null) ? evt.getPickResult().getIntersectedNode() : null;
        if (target == null) return null;
        for (Node n = target; n != null; n = n.getParent()) {
            String cn = n.getClass().getName();
            if (cn.contains("TableColumnHeader")) return n;
            if (n instanceof TableView) break;
        }
        return null;
    }

    private static TableColumn<?, ?> findClickedColumn(MouseEvent evt) {
        Node target = (evt.getPickResult() != null) ? evt.getPickResult().getIntersectedNode() : null;
        if (target == null) return null;
        for (Node n = target; n != null; n = n.getParent()) {
            String cn = n.getClass().getName();
            if (cn.contains("TableColumnHeader")) {
                try {
                    var m = n.getClass().getMethod("getTableColumn");
                    Object o = m.invoke(n);
                    if (o instanceof TableColumn<?, ?> col) return col;
                } catch (Throwable ignored) {
                    return null;
                }
            }
            if (n instanceof TableView) break;
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyPrimarySort(TableView<?> table, TableColumn<?, ?> clicked, TableColumn.SortType sortType) {
        if (table == null || clicked == null) return;
        TableColumn rawColumn = (TableColumn) clicked;
        rawColumn.setSortType(sortType);
        table.getSortOrder().remove(rawColumn);
        table.getSortOrder().add(0, rawColumn);
        table.sort();
    }

    private static ExplorerPresetRow buildPresetRow(
            TableView<?> table,
            TableColumn<?, ?> clickedColumn,
            ContextMenu menu,
            PresetDefinition preset
    ) {
        ExplorerPresetRow row = new ExplorerPresetRow(preset.label(), preset.iconText());
        row.item.setOnAction(ae -> {
            rememberSelectedPreset(table, clickedColumn, preset.key());
            applyPrimarySort(table, clickedColumn, preset.sortType());
            menu.hide();
        });
        return row;
    }

    private static void syncPresetSelection(
            TableView<?> table,
            TableColumn<?, ?> clicked,
            List<PresetDefinition> presetDefinitions,
            Map<String, ExplorerPresetRow> presetRows
    ) {
        for (ExplorerPresetRow row : presetRows.values()) {
            row.setSelected(false);
        }

        if (table == null || clicked == null || presetDefinitions == null || presetDefinitions.isEmpty()) {
            return;
        }

        String selectedKey = getRememberedSelectedPreset(table, clicked);

        if ((selectedKey == null || !presetRows.containsKey(selectedKey))
                && !table.getSortOrder().isEmpty()
                && table.getSortOrder().get(0) == clicked
                && usesDefaultAlphaNumericPresets(clicked)) {
            if (clicked.getSortType() == TableColumn.SortType.DESCENDING) {
                selectedKey = "Q-Z";
            } else if (isNumericHeader(clicked)) {
                selectedKey = "0-9";
            } else {
                selectedKey = "A-H";
            }
        }

        ExplorerPresetRow selectedRow = presetRows.get(selectedKey);
        if (selectedRow != null) {
            selectedRow.setSelected(true);
        }
    }

    private static List<PresetDefinition> resolvePresetDefinitions(TableColumn<?, ?> column) {
        if (isSizeColumn(column)) {
            return List.of(
                    new PresetDefinition("size-empty", "Empty (0 KB)", "0", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("size-tiny", "Tiny (0 - 16 KB)", "T", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("size-small", "Small (16 KB - 1 MB)", "S", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("size-medium", "Medium (1 - 128 MB)", "M", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("size-large", "Large (128 MB - 1 GB)", "L", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("size-unspecified", "Unspecified", "?", TableColumn.SortType.ASCENDING)
            );
        }

        if (isDateColumn(column)) {
            return List.of(
                    new PresetDefinition("date-long-ago", "A long time ago", "L", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("date-earlier-year", "Earlier this year", "Y", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("date-last-month", "Last month", "M", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("date-earlier-month", "Earlier this month", "m", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("date-last-week", "Last week", "W", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("date-yesterday", "Yesterday", "D", TableColumn.SortType.ASCENDING),
                    new PresetDefinition("date-today", "Today", "T", TableColumn.SortType.ASCENDING)
            );
        }

        return List.of(
                new PresetDefinition("0-9", "0-9", "#", TableColumn.SortType.ASCENDING),
                new PresetDefinition("A-H", "A-H", "A", TableColumn.SortType.ASCENDING),
                new PresetDefinition("I-P", "I-P", "I", TableColumn.SortType.ASCENDING),
                new PresetDefinition("Q-Z", "Q-Z", "Q", TableColumn.SortType.DESCENDING)
        );
    }

    private static boolean usesDefaultAlphaNumericPresets(TableColumn<?, ?> column) {
        return !isSizeColumn(column) && !isDateColumn(column);
    }

    private static boolean isSizeColumn(TableColumn<?, ?> column) {
        String text = normalizeColumnText(column);
        return text.contains("size") || text.contains("length");
    }

    private static boolean isDateColumn(TableColumn<?, ?> column) {
        String text = normalizeColumnText(column);
        return text.contains("date") || text.contains("modified") || text.contains("created");
    }

    private static boolean isNumericHeader(TableColumn<?, ?> column) {
        String text = normalizeColumnText(column);
        return text.contains("#") || text.contains("number") || text.contains("count");
    }

    private static String normalizeColumnText(TableColumn<?, ?> column) {
        if (column == null || column.getText() == null) {
            return "";
        }
        return column.getText().trim().toLowerCase();
    }

    private static void rememberSelectedPreset(TableView<?> table, TableColumn<?, ?> column, String presetKey) {
        if (table == null || column == null || presetKey == null || presetKey.isBlank()) {
            return;
        }
        selectedPresetMap(table).put(columnSelectionKey(column), presetKey);
    }

    private static String getRememberedSelectedPreset(TableView<?> table, TableColumn<?, ?> column) {
        if (table == null || column == null) {
            return null;
        }
        return selectedPresetMap(table).get(columnSelectionKey(column));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> selectedPresetMap(TableView<?> table) {
        Object existing = table.getProperties().get(PROP_HEADER_SELECTED_PRESET_KEYS);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, String>) map;
        }
        Map<String, String> created = new LinkedHashMap<>();
        table.getProperties().put(PROP_HEADER_SELECTED_PRESET_KEYS, created);
        return created;
    }

    private static String columnSelectionKey(TableColumn<?, ?> column) {
        String id = column != null ? column.getId() : null;
        if (id != null && !id.isBlank()) {
            return id.trim().toLowerCase();
        }
        String text = column != null ? column.getText() : null;
        return text == null ? "" : text.trim().toLowerCase();
    }


    private static void updateHeaderHotzoneState(MouseEvent evt) {
        Node header = findHeaderNode(evt);
        TableView<?> table = findOwningTable(evt);
        if (header == null) {
            clearAllHeaderHotzoneState(table);
            return;
        }

        if (table != null) {
            for (Node n : table.lookupAll(".column-header")) {
                n.getStyleClass().remove("details-menu-hot");
                if (n.getCursor() == Cursor.HAND) {
                    n.setCursor(Cursor.DEFAULT);
                }
            }
        }

        if (isHeaderMenuTriggerZone(evt)) {
            if (!header.getStyleClass().contains("details-menu-hot")) {
                header.getStyleClass().add("details-menu-hot");
            }
            header.setCursor(Cursor.HAND);
            showHeaderMenuChevron(table, header);
        } else {
            header.getStyleClass().remove("details-menu-hot");
            if (header.getCursor() == Cursor.HAND) {
                header.setCursor(Cursor.DEFAULT);
            }
            hideHeaderMenuChevron(table);
        }
    }

    private static void clearAllHeaderHotzoneState(TableView<?> table) {
        if (table == null) {
            return;
        }
        hideHeaderMenuChevron(table);
        for (Node n : table.lookupAll(".column-header")) {
            n.getStyleClass().remove("details-menu-hot");
            if (n.getCursor() == Cursor.HAND) {
                n.setCursor(Cursor.DEFAULT);
            }
        }
    }

    private static Popup getOrCreateHeaderMenuChevronPopup(TableView<?> table) {
        if (table == null) {
            return null;
        }
        Object existing = table.getProperties().get(PROP_HEADER_MENU_CHEVRON_POPUP);
        if (existing instanceof Popup popup) {
            return popup;
        }

        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);

        StackPane root = buildHeaderMenuChevronNode();
        popup.getContent().add(root);

        table.getProperties().put(PROP_HEADER_MENU_CHEVRON_POPUP, popup);
        table.getProperties().put(PROP_HEADER_MENU_CHEVRON_NODE, root);
        return popup;
    }

    private static StackPane buildHeaderMenuChevronNode() {
        Path chevron = new Path(
                new MoveTo(1.0, 1.0),
                new LineTo(HEADER_MENU_CHEVRON_WIDTH_PX / 2.0, HEADER_MENU_CHEVRON_HEIGHT_PX),
                new LineTo(HEADER_MENU_CHEVRON_WIDTH_PX - 1.0, 1.0)
        );
        chevron.setFill(Color.TRANSPARENT);
        chevron.setStrokeWidth(1.25);
        chevron.setManaged(false);
        chevron.setMouseTransparent(true);
        chevron.setSmooth(true);
        chevron.setStyle("-fx-stroke-line-cap: round; -fx-stroke-line-join: round;");

        StackPane root = new StackPane(chevron);
        root.setManaged(false);
        root.setMouseTransparent(true);
        root.setPickOnBounds(false);
        root.setMinSize(HEADER_MENU_CHEVRON_WIDTH_PX + 4.0, HEADER_MENU_CHEVRON_HEIGHT_PX + 4.0);
        root.setPrefSize(HEADER_MENU_CHEVRON_WIDTH_PX + 4.0, HEADER_MENU_CHEVRON_HEIGHT_PX + 4.0);
        root.setMaxSize(HEADER_MENU_CHEVRON_WIDTH_PX + 4.0, HEADER_MENU_CHEVRON_HEIGHT_PX + 4.0);
        return root;
    }

    private static void showHeaderMenuChevron(TableView<?> table, Node header) {
        if (table == null || header == null || table.getScene() == null) {
            hideHeaderMenuChevron(table);
            return;
        }

        Bounds bounds = header.localToScreen(header.getBoundsInLocal());
        Window window = table.getScene().getWindow();
        Popup popup = getOrCreateHeaderMenuChevronPopup(table);
        if (bounds == null || window == null || popup == null) {
            hideHeaderMenuChevron(table);
            return;
        }

        Object nodeObj = table.getProperties().get(PROP_HEADER_MENU_CHEVRON_NODE);
        if (nodeObj instanceof StackPane root
                && !root.getChildren().isEmpty()
                && root.getChildren().get(0) instanceof Path chevron) {
            chevron.setStroke(resolveHeaderMenuChevronColor(table));
        }

        double popupWidth = HEADER_MENU_CHEVRON_WIDTH_PX + 4.0;
        double popupHeight = HEADER_MENU_CHEVRON_HEIGHT_PX + 4.0;
        double x = Math.round(bounds.getMaxX() - HEADER_MENU_CHEVRON_RIGHT_INSET_PX - popupWidth);
        double y = Math.round(bounds.getMinY() + ((bounds.getHeight() - popupHeight) / 2.0));

        if (popup.isShowing()) {
            popup.setX(x);
            popup.setY(y);
        } else {
            popup.show(window, x, y);
        }
    }

    private static Paint resolveHeaderMenuChevronColor(TableView<?> table) {
        if (table != null && table.getScene() != null && table.getScene().getRoot() != null) {
            if (table.getScene().getRoot().getStyleClass().contains("theme-light")) {
                return Color.web("#4d5b75");
            }
        }
        return Color.web("rgba(236,242,255,0.96)");
    }

    private static void hideHeaderMenuChevron(TableView<?> table) {
        if (table == null) {
            return;
        }
        Object existing = table.getProperties().get(PROP_HEADER_MENU_CHEVRON_POPUP);
        if (existing instanceof Popup popup && popup.isShowing()) {
            popup.hide();
        }
    }

    private static TableView<?> findOwningTable(MouseEvent evt) {
        Node target = (evt.getPickResult() != null) ? evt.getPickResult().getIntersectedNode() : null;
        if (target == null) return null;
        for (Node n = target; n != null; n = n.getParent()) {
            if (n instanceof TableView<?> tv) return tv;
        }
        return null;
    }


private static boolean isHeaderEvent(ContextMenuEvent evt) {
        Node target = (evt.getPickResult() != null) ? evt.getPickResult().getIntersectedNode() : null;
        if (target == null) return false;
        for (Node n = target; n != null; n = n.getParent()) {
            String cn = n.getClass().getName();
            if (cn.contains("TableColumnHeader") || cn.contains("TableHeaderRow")) return true;
            if (n instanceof TableView) break;
        }
        return false;
    }

private static TableColumn<?, ?> findClickedColumn(ContextMenuEvent evt) {
        Node target = (evt.getPickResult() != null) ? evt.getPickResult().getIntersectedNode() : null;
        if (target == null) return null;
        for (Node n = target; n != null; n = n.getParent()) {
            String cn = n.getClass().getName();
            if (cn.contains("TableColumnHeader")) {
                try {
                    var m = n.getClass().getMethod("getTableColumn");
                    Object o = m.invoke(n);
                    if (o instanceof TableColumn<?, ?> col) return col;
                } catch (Throwable ignored) {
                    return null;
                }
            }
            if (n instanceof TableView) break;
        }
        return null;
    }

    public static Map<String, TableColumn<?, ?>> defaultDetailsOrder(
            TableColumn<?, ?> name,
            TableColumn<?, ?> dateModified,
            TableColumn<?, ?> type,
            TableColumn<?, ?> size,
            TableColumn<?, ?> dateCreated,
            TableColumn<?, ?> authors,
            TableColumn<?, ?> tags,
            TableColumn<?, ?> title
    ) {
        Map<String, TableColumn<?, ?>> m = new LinkedHashMap<>();
        m.put("Name", name);
        m.put("Date modified", dateModified);
        m.put("Type", type);
        m.put("Size", size);
        m.put("Date created", dateCreated);
        m.put("Authors", authors);
        m.put("Tags", tags);
        m.put("Title", title);
        return m;
    }

    /**
     * A CustomMenuItem that renders an Explorer-style checkmark column.
     * We do this instead of CheckMenuItem to avoid skin/CSS issues where marks
     * can disappear in themed context menus.
     */

    private static final class ExplorerPresetRow {
        final CustomMenuItem item;
        final Label mark;
        final Label icon;
        final Label text;
        private boolean selected;

        ExplorerPresetRow(String label, String iconText) {
            mark = new Label("");
            mark.getStyleClass().add("explorer-menu-checkmark");
            mark.setMinWidth(18);
            mark.setPrefWidth(18);
            mark.setMaxWidth(18);

            icon = new Label(iconText);
            icon.getStyleClass().add("explorer-menu-item-icon");
            icon.setMinWidth(16);
            icon.setPrefWidth(16);
            icon.setMaxWidth(16);

            text = new Label(label);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(8, mark, icon, text, spacer);
            row.getStyleClass().add("explorer-menu-checkrow");

            item = new CustomMenuItem(row, true);
            item.getStyleClass().add("explorer-menu-checkitem");

            row.setOnMouseReleased(ev -> item.fire());
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            mark.setText(selected ? "\u2713" : "");
        }

        boolean isSelected() {
            return selected;
        }
    }

    private static final class ExplorerCheckRow {
        final CustomMenuItem item;
        final Label mark;
        final Label text;
        private boolean locked;
        private boolean selected;

        ExplorerCheckRow(String label) {
            mark = new Label("");
            mark.getStyleClass().add("explorer-menu-checkmark");
            // Fixed column width like Explorer.
            mark.setMinWidth(18);
            mark.setPrefWidth(18);
            mark.setMaxWidth(18);

            text = new Label(label);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(8, mark, text, spacer);
            row.getStyleClass().add("explorer-menu-checkrow");

            item = new CustomMenuItem(row, true);
            item.getStyleClass().add("explorer-menu-checkitem");

            // Clicking anywhere on the row toggles (on release, Explorer-like).
            row.setOnMouseReleased(ev -> {
                if (locked) return;
                setSelected(!selected);
                item.fire();
            });
        }

        void setLocked(boolean locked) {
            this.locked = locked;
            item.setDisable(locked);
        }

        boolean isSelected() { return selected; }

        void setSelected(boolean selected) {
            this.selected = selected;
            // Simple check glyph; renders correctly in Segoe UI.
            mark.setText(selected ? "\u2713" : "");
        }
    }
}
