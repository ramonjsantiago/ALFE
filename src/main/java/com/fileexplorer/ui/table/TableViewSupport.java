package com.fileexplorer.ui.table;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.model.FileItem;
import com.fileexplorer.model.FileStatus;
import com.fileexplorer.util.IconLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Step 7: Extracted TableView setup from MainController into ui.table.
 *
 * This configures:
 * - Name column (icon + text)
 * - Status column (check outline with hover/selection tint)
 * - Type/Size/Modified value factories (callers provide formatters)
 */
public final class TableViewSupport {

    private TableViewSupport() {}

    public static void configure(
            ExplorerContext ctx,
            TableView<FileItem> fileTable,
            TableColumn<FileItem, String> colName,
            TableColumn<FileItem, Node> colStatus,
            TableColumn<FileItem, String> colType,
            TableColumn<FileItem, String> colSize,
            TableColumn<FileItem, String> colModified,
            Function<FileItem, String> displayNameForTable,
            Function<FileItem, String> typeForPath,
            Function<FileItem, String> sizeForPath,
            Function<FileItem, String> modifiedForPath
    ) {
        if (fileTable == null) return;

        if (colName != null) {
            colName.setCellValueFactory(param -> new javafx.beans.property.ReadOnlyObjectWrapper<>(displayNameForTable.apply(param.getValue())));

            colName.setCellFactory(_ -> new TableCell<>() {
                private final HBox box = new HBox(10.0);
                private final ImageView iconView = new ImageView();
                private final Label textLabel = new Label();

                {
                    box.setAlignment(Pos.CENTER_LEFT);
                    iconView.setPreserveRatio(true);
                    iconView.setSmooth(true);
                    textLabel.setMaxWidth(Double.MAX_VALUE);
                    box.getChildren().addAll(iconView, textLabel);
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }

                    FileItem fi = getTableRow() != null ? (FileItem) getTableRow().getItem() : null;
                    Path p = (fi != null) ? fi.path() : null;
                    double iconPx = 18.0;

                    Image img;
                    try {
                        img = IconLoader.loadForPath(p, ctx.themeService().isDarkPreferred(), (int) Math.round(iconPx));
                    } catch (Exception ex) {
                        img = IconLoader.load(IconLoader.IconType.FILE, ctx.themeService().isDarkPreferred(), (int) Math.round(iconPx));
                    }

                    iconView.setFitWidth(iconPx);
                    iconView.setFitHeight(iconPx);
                    iconView.setImage(img);

                    textLabel.setText(item);
                    setText(null);
                    setGraphic(box);
                }
            });
        }

        if (colStatus != null) {
            colStatus.setSortable(false);
            colStatus.setReorderable(false);

            final Color normal = Color.web("#6CCB5F");
            final Color hover = Color.web("#86E173");
            final Color selected = Color.web("#FFFFFF");

            colStatus.setCellFactory(_ -> new TableCell<>() {
                private Label icon;

                private void syncTint() {
                    if (icon == null) return;
                    TableRow<FileItem> row = getTableRow();
                    if (row != null && row.isSelected()) {
                        icon.setTextFill(selected);
                    } else if (row != null && row.isHover()) {
                        icon.setTextFill(hover);
                    } else {
                        icon.setTextFill(normal);
                    }
                }

                @Override
                protected void updateItem(Node item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                        icon = null;
                        return;
                    }
                    if (icon == null) {
                        icon = new Label(""); // set per-status
                        icon.setFont(Font.font("Segoe Fluent Icons", 14));
                        icon.setMinWidth(18);
                        icon.setPrefWidth(18);
                        icon.setAlignment(Pos.CENTER);
                        tableRowProperty().addListener((obs, oldR, newR) -> syncTint());
                    }
                    FileItem fi = getTableRow() != null ? (FileItem) getTableRow().getItem() : null;
FileStatus st = (fi != null) ? fi.status() : FileStatus.NONE;

// Phase 2: real status-driven icon
if (st == FileStatus.CHECKED) {
    icon.setText("\uE73E"); // check outline
} else if (st == FileStatus.SYNCING) {
    icon.setText("\uE823"); // clock
} else if (st == FileStatus.ERROR) {
    icon.setText("\uE783"); // warning
} else {
    icon.setText("");
}

setGraphic(icon.getText().isEmpty() ? null : icon);
setText(null);
syncTint();

                }
            });
        }

        if (colType != null) {
            colType.setCellValueFactory(param -> new javafx.beans.property.ReadOnlyObjectWrapper<>(typeForPath.apply(param.getValue())));
        }
        if (colSize != null) {
            colSize.setCellValueFactory(param -> new javafx.beans.property.ReadOnlyObjectWrapper<>(sizeForPath.apply(param.getValue())));
            colSize.setCellFactory(_ -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            });
        }
        if (colModified != null) {
            colModified.setCellValueFactory(param -> new javafx.beans.property.ReadOnlyObjectWrapper<>(modifiedForPath.apply(param.getValue())));
        }
    }
}
