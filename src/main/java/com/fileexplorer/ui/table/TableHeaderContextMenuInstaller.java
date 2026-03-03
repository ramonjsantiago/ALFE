package com.fileexplorer.ui.table;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Installs an Explorer-like header context menu on a JavaFX TableView.
 */
public final class TableHeaderContextMenuInstaller {

    private TableHeaderContextMenuInstaller() {}

    /** TableView property keys used to coordinate context menu visibility. */
    public static final String PROP_HEADER_MENU = "fileexplorer.headerMenu";
    public static final String PROP_FILEOPS_MENU = "fileexplorer.fileOpsMenu";

        public static void install(
            TableView<?> table,
            Map<String, TableColumn<?, ?>> detailsColumns,
            Runnable showChooseDetailsDialog
    ) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(detailsColumns, "detailsColumns");
        Objects.requireNonNull(showChooseDetailsDialog, "showChooseDetailsDialog");

        // One shared menu instance per TableView, always hide before re-showing.
        final Holder<TableColumn<?, ?>> currentClickedColumn = new Holder<>();

        Platform.runLater(() -> {
            final ContextMenu headerMenu = buildMenu(table, currentClickedColumn, detailsColumns, showChooseDetailsDialog);

            // Expose for coordination with the row/file ops menu.
            table.getProperties().put(PROP_HEADER_MENU, headerMenu);

            // Capture phase on the standard ContextMenuEvent (fires on release). This avoids header nodes
            // consuming mouse events and makes behavior consistent across skins.
            table.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, evt -> {
                if (!isHeaderEvent(evt)) return;

                // Remember which column header was clicked for "Size Column to Fit".
                currentClickedColumn.value = findClickedColumn(evt);

                // Ensure only one context menu instance is visible at a time.
                if (table.getContextMenu() != null) table.getContextMenu().hide();

                Object other = table.getProperties().get(PROP_FILEOPS_MENU);
                if (other instanceof ContextMenu cm) cm.hide();

                headerMenu.hide();
                headerMenu.show(table, evt.getScreenX(), evt.getScreenY());

                evt.consume();
            });
        });
    }

    private static final class Holder<T> { T value; }

    private static ContextMenu buildMenu(
            TableView<?> table,
            Holder<TableColumn<?, ?>> currentClickedColumn,
            Map<String, TableColumn<?, ?>> detailsColumns,
            Runnable showChooseDetailsDialog
    ) {
        ContextMenu menu = new ContextMenu();
        menu.setAutoHide(true);

        MenuItem sizeCol = new MenuItem("Size Column to Fit");
        sizeCol.setOnAction(ae -> {
            TableColumn<?, ?> col = currentClickedColumn.value;
            if (col != null) {
                ColumnAutoFitUtil.sizeToFit(table, col);
            }
            menu.hide();
        });

        MenuItem sizeAll = new MenuItem("Size all Columns to Fit");
        sizeAll.setOnAction(ae -> {
            ColumnAutoFitUtil.sizeAllToFit(table);
            menu.hide();
        });

        menu.getItems().addAll(sizeCol, sizeAll, new SeparatorMenuItem());

        // Build Explorer-like "check" rows using CustomMenuItem so checkmarks render reliably
        // across JavaFX skins (some dark themes hide the built-in CheckMenuItem mark).
        final Map<String, ExplorerCheckRow> checkRows = new LinkedHashMap<>();

        for (Map.Entry<String, TableColumn<?, ?>> e : detailsColumns.entrySet()) {
            String label = e.getKey();
            TableColumn<?, ?> col = e.getValue();
            if (col == null) continue;

            ExplorerCheckRow row = new ExplorerCheckRow(label);

            if (!"Name".equals(label)) {
                row.item.setOnAction((ActionEvent ae) -> {
                    col.setVisible(row.isSelected());
                    menu.hide();
                });
            } else {
                // Name is always visible and not user-toggleable
                row.setLocked(true);
            }

            // Keep visual state in sync with column visibility (and allow external changes).
            col.visibleProperty().addListener((obs, ov, nv) -> row.setSelected(Boolean.TRUE.equals(nv)));

            checkRows.put(label, row);
            menu.getItems().add(row.item);
        }

        menu.getItems().addAll(new SeparatorMenuItem());

        MenuItem more = new MenuItem("More...");
        more.setOnAction(ae -> {
            menu.hide();
            showChooseDetailsDialog.run();
        });
        menu.getItems().add(more);

        // Re-sync dynamic state each time the menu is shown (and apply defaults).
        menu.setOnShowing(evt -> {
            TableColumn<?, ?> clicked = currentClickedColumn.value;
            sizeCol.setDisable(clicked == null);

            for (Map.Entry<String, TableColumn<?, ?>> e : detailsColumns.entrySet()) {
                String label = e.getKey();
                TableColumn<?, ?> col = e.getValue();
                if (col == null) continue;

                // Explorer defaults: ensure these are visible.
                if ("Name".equals(label) || "Date modified".equals(label) || "Type".equals(label) || "Size".equals(label)) {
                    col.setVisible(true);
                }

                ExplorerCheckRow row = checkRows.get(label);
                if (row == null) continue;

                // force selection state from column visibility (Name forced true)
                if ("Name".equals(label)) {
                    row.setSelected(true);
                } else {
                    row.setSelected(col.isVisible());
                }
            }
        });

        return menu;
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
