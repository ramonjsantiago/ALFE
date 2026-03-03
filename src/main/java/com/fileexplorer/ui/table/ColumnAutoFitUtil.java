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

            int n = Math.min(table.getItems() != null ? table.getItems().size() : 0, 250);
            for (int i = 0; i < n; i++) {
                Object v = col.getCellData(i);
                max = Math.max(max, textWidth(v));
            }

            col.setPrefWidth(Math.ceil(max + 32));
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

    private static double textWidth(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        Text t = new Text(s);
        return t.getLayoutBounds().getWidth();
    }
}
