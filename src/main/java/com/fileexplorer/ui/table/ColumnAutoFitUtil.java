package com.fileexplorer.ui.table;

import javafx.application.Platform;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Text;

/**
 * Utilities for Explorer-like column sizing in the TableView header menu.
 */
public final class ColumnAutoFitUtil {

    private ColumnAutoFitUtil() {}

    public static void sizeToFit(TableView<?> table, TableColumn<?, ?> col) {
        if (table == null || col == null) return;
        Platform.runLater(() -> {
            double max = textWidth(col.getText());

            int n = Math.min(table.getItems() != null ? table.getItems().size() : 0, 400);
            for (int i = 0; i < n; i++) {
                Object v = col.getCellData(i);
                max = Math.max(max, textWidth(v));
            }

            double width = Math.ceil(max + cellChromePadding(col));
            width = Math.max(minWidthFor(col), Math.min(maxWidthFor(col), width));
            col.setPrefWidth(width);
        });
    }

    public static void sizeAllToFit(TableView<?> table) {
        if (table == null) return;
        Platform.runLater(() -> {
            for (TableColumn<?, ?> c : table.getColumns()) {
                sizeToFit(table, c);
            }
        });
    }

    private static double cellChromePadding(TableColumn<?, ?> col) {
        String key = detailKey(col);
        return "size".equals(key) || "index".equals(key) ? 26.0 : 38.0;
    }

    private static double minWidthFor(TableColumn<?, ?> col) {
        String key = detailKey(col);
        return switch (key) {
            case "name" -> 240.0;
            case "modified", "dateCreated", "dateAccessed" -> 156.0;
            case "type", "authors", "tags", "title", "path", "folder", "fileLocation" -> 128.0;
            case "size", "index" -> 84.0;
            default -> 72.0;
        };
    }

    private static double maxWidthFor(TableColumn<?, ?> col) {
        String key = detailKey(col);
        return switch (key) {
            case "size", "index" -> 240.0;
            case "modified", "dateCreated", "dateAccessed" -> 360.0;
            default -> 1600.0;
        };
    }

    private static String detailKey(TableColumn<?, ?> col) {
        if (col == null) {
            return "";
        }
        Object key = col.getProperties().get("fileexplorer.detailColumnKey");
        return key instanceof String s ? s : "";
    }

    private static double textWidth(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        Text t = new Text(s);
        return t.getLayoutBounds().getWidth();
    }
}
