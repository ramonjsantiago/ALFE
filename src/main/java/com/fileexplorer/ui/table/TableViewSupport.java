package com.fileexplorer.ui.table;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.model.FileItem;
import com.fileexplorer.model.FileStatus;
import com.fileexplorer.service.icon.AsyncIconService;
import com.fileexplorer.service.icon.AsyncThumbnailService;
import com.fileexplorer.util.IconLoader;
import com.fileexplorer.util.ImageSupport;
import javafx.application.Platform;
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
import java.util.Objects;
import java.util.Locale;
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

        // Phase 4A.2: visibility-driven thumbnailing to avoid wasted decode during fast scroll.
        final VisibleThumbnailManager thumbMgr = ensureThumbManager(fileTable, ctx);

        if (colName != null) {
            colName.setCellValueFactory(param -> new javafx.beans.property.ReadOnlyObjectWrapper<>(displayNameForTable.apply(param.getValue())));

            colName.setCellFactory(_ -> new TableCell<>() {
                private final HBox box = new HBox(10.0);
                private final ImageView iconView = new ImageView();
                private final Label textLabel = new Label();

                // Guards against stale async completions updating a recycled cell.
                private String lastIdentity = null;
                private Path lastPath = null;

                private java.util.concurrent.CompletableFuture<javafx.scene.image.Image> pendingThumb = null;

                {
                    box.setAlignment(Pos.CENTER_LEFT);
                    iconView.setPreserveRatio(true);
                    iconView.setSmooth(true);
                    textLabel.setMaxWidth(Double.MAX_VALUE);
                    box.getChildren().addAll(iconView, textLabel);
                }

                @Override
/**
 * updateItem.
 *
 * @param item TODO
 * @param empty TODO
 */
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);

                    // Cancel any pending thumbnail work for the previous item (cell reuse/virtualization).
                    if (pendingThumb != null) {
                        pendingThumb.cancel(false);
                        pendingThumb = null;
                    }

                    if (empty || item == null) {
                        lastIdentity = null;
                        lastPath = null;
                        setText(null);
                        setGraphic(null);
                        return;
                    }

                    FileItem fi = getTableRow() != null ? (FileItem) getTableRow().getItem() : null;
                    Path p = (fi != null) ? fi.path() : null;

                    final boolean dark = ctx.themeService().isDarkPreferred();
                    final int iconPx = 18;

                    // Set placeholder immediately (cheap).
                    final boolean isFolder = (fi != null) && "Folder".equalsIgnoreCase(Objects.requireNonNullElse(fi.type(), ""));
                    Image placeholder = IconLoader.load(isFolder ? IconLoader.IconType.FOLDER : IconLoader.IconType.FILE, dark, iconPx);

                    iconView.setFitWidth(iconPx);
                    iconView.setFitHeight(iconPx);
                    iconView.setImage(placeholder);

                    textLabel.setText(item);
                    setText(null);
                    setGraphic(box);

                    // Compute identity without I/O: directories from FileItem.type, otherwise by extension.
                    final String identity = computeIdentityNoIo(p, isFolder);

                    lastIdentity = identity;
                    lastPath = p;

                    AsyncIconService.getInstance()
                            .request(identity, dark, iconPx)
                            .thenAccept(img -> Platform.runLater(() -> {
                                // Ignore stale completions (cell reused).
                                if (!Objects.equals(lastIdentity, identity)) return;
                                if (!Objects.equals(lastPath, p)) return;
                                if (img == null) return;
                                iconView.setImage(img);
                            }));

                    // If this is a supported image file, lazily replace the placeholder with a scaled thumbnail.
                    if (!isFolder && p != null && ImageSupport.isThumbCandidate(p)) {
                        // Register with the viewport-aware manager; it will request only after scroll-idle.
                        thumbMgr.register(this, p, iconPx, identity, iconView::setImage);
                    }
                }

/**
 * computeIdentityNoIo.
 *
 * @param p TODO
 * @param isFolder TODO
 * @return TODO
 */
                private String computeIdentityNoIo(Path p, boolean isFolder) {
                    if (isFolder) return "type:" + IconLoader.IconType.FOLDER.name();
                    if (p == null) return "type:" + IconLoader.IconType.FILE.name();

                    String name = p.getFileName() != null ? p.getFileName().toString() : p.toString();
                    int dot = name.lastIndexOf('.');
                    if (dot > 0 && dot < name.length() - 1) {
                        String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
                        if (!ext.isBlank()) return "ext:" + ext;
                    }
                    return "type:" + IconLoader.IconType.FILE.name();
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

/**
 * syncTint.
 *
 */
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
/**
 * updateItem.
 *
 * @param item TODO
 * @param empty TODO
 */
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

            // IMPORTANT:
            // The displayed size is typically a human-readable string (e.g. "1.2 MB").
            // JavaFX will otherwise sort lexicographically, which breaks numeric ordering.
            // We keep the column type as String (no API changes) and provide a comparator
            // that parses the human-readable size into bytes.
            colSize.setComparator((a, b) -> {
                long ba = parseHumanSizeToBytes(a);
                long bb = parseHumanSizeToBytes(b);
                return Long.compare(ba, bb);
            });

            colSize.setCellFactory(_ -> new TableCell<>() {
                @Override
/**
 * updateItem.
 *
 * @param item TODO
 * @param empty TODO
 */
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

    /**
     * Parses strings like "12 B", "1.2 KB", "5.6 MB", "7 GB", "1.0 TB" into bytes.
     * Returns -1 for blanks/unknowns (commonly used for folders).
     */
    private static long parseHumanSizeToBytes(String s) {
        if (s == null) return -1L;
        String t = s.trim();
        if (t.isEmpty()) return -1L;

        // Common non-values.
        String tl = t.toLowerCase(Locale.ROOT);
        if (tl.equals("-") || tl.equals("--") || tl.equals("n/a") || tl.equals("na")) return -1L;

        // Normalize separators.
        t = t.replace(",", "").trim();

        // Split into number + unit (best effort).
        String numberPart = null;
        String unitPart = null;
        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
                i++;
            } else {
                break;
            }
        }
        if (i == 0) return -1L;
        numberPart = t.substring(0, i).trim();
        unitPart = t.substring(i).trim();
        if (unitPart.isEmpty()) unitPart = "B";

        final double value;
        try {
            value = Double.parseDouble(numberPart);
        } catch (NumberFormatException ex) {
            return -1L;
        }

        String u = unitPart.toLowerCase(Locale.ROOT);
        // Allow "bytes" or "byte".
        if (u.equals("byte") || u.equals("bytes")) u = "b";

        // Accept IEC units too.
        boolean iec = u.contains("i");
        u = u.replace("bytes", "b").replace("byte", "b");
        u = u.replaceAll("\\s+", "");

        long multiplier;
        if (u.equals("b")) multiplier = 1L;
        else if (u.equals("kb") || u.equals("kib")) multiplier = iec ? (1L << 10) : 1_000L;
        else if (u.equals("mb") || u.equals("mib")) multiplier = iec ? (1L << 20) : 1_000_000L;
        else if (u.equals("gb") || u.equals("gib")) multiplier = iec ? (1L << 30) : 1_000_000_000L;
        else if (u.equals("tb") || u.equals("tib")) multiplier = iec ? (1L << 40) : 1_000_000_000_000L;
        else if (u.equals("pb") || u.equals("pib")) multiplier = iec ? (1L << 50) : 1_000_000_000_000_000L;
        else return -1L;

        double bytes = value * (double) multiplier;
        if (bytes < 0) return -1L;
        if (bytes > Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) bytes;
    }

    private static VisibleThumbnailManager ensureThumbManager(TableView<FileItem> table, ExplorerContext ctx) {
        final String key = "fileexplorer.visibleThumbMgr";
        Object existing = table.getProperties().get(key);
        if (existing instanceof VisibleThumbnailManager m) {
            return m;
        }
        VisibleThumbnailManager created = new VisibleThumbnailManager(table, ctx);
        table.getProperties().put(key, created);
        return created;
    }
}
