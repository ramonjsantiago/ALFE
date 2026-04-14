package com.fileexplorer.ui.table;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.model.FileItem;
import com.fileexplorer.model.FileStatus;
import com.fileexplorer.service.icon.AsyncIconService;
import com.fileexplorer.service.icon.AsyncThumbnailService;
import com.fileexplorer.util.IconLoader;
import com.fileexplorer.util.ImageSupport;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
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
import javafx.scene.layout.Priority;
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
        final DetailsViewRefreshCoordinator refreshCoordinator = ensureRefreshCoordinator(fileTable);
        installRefreshObservation(fileTable, refreshCoordinator);

        if (colName != null) {
            colName.setCellValueFactory(param -> new javafx.beans.property.ReadOnlyObjectWrapper<>(displayNameForTable.apply(param.getValue())));

            colName.setCellFactory(_ -> new TableCell<>() {
                private static final int ICON_PX = 18;

                private final HBox box = new HBox(10.0);
                private final ImageView iconView = new ImageView();
                private final Label textLabel = new Label();

                // HOTFIX199: gate every async completion against the exact bound FileItem + row token.
                private FileItem lastRowItem = null;
                private Path lastPath = null;
                private String lastIdentity = null;
                private String lastDisplayText = null;
                private String lastTypeKey = null;
                private boolean lastFolder = false;
                private boolean thumbnailPublished = false;

                private java.util.concurrent.CompletableFuture<javafx.scene.image.Image> pendingThumb = null;
                private java.util.concurrent.CompletableFuture<javafx.scene.image.Image> pendingIcon = null;
                private long bindingStamp = 0L;

                private boolean isEquivalentBinding(FileItem item,
                                                    Path path,
                                                    String identity,
                                                    String displayText,
                                                    String typeKey,
                                                    boolean isFolder) {
                    return lastRowItem == item
                            && Objects.equals(lastPath, path)
                            && Objects.equals(lastIdentity, identity)
                            && Objects.equals(lastDisplayText, displayText)
                            && Objects.equals(lastTypeKey, typeKey)
                            && lastFolder == isFolder;
                }

                private TableRow<FileItem> observedRow = null;
                private final ChangeListener<FileItem> rowItemListener = (obs, oldItem, newItem) -> {
                    if (isEmpty() || newItem == null) {
                        sanitizeCell();
                        return;
                    }
                    String displayText = displayNameForTable.apply(newItem);
                    if (displayText == null) {
                        sanitizeCell();
                        return;
                    }
                    rebindForCurrentRow(displayText);
                };

                {
                    box.setAlignment(Pos.CENTER_LEFT);
                    iconView.setPreserveRatio(true);
                    iconView.setSmooth(true);
                    iconView.setFitWidth(ICON_PX);
                    iconView.setFitHeight(ICON_PX);
                    textLabel.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(textLabel, Priority.ALWAYS);
                    box.getChildren().addAll(iconView, textLabel);

                    tableRowProperty().addListener((obs, oldRow, newRow) -> {
                        detachRowListeners(oldRow);
                        attachRowListeners(newRow);
                        if (newRow == null) {
                            sanitizeCell();
                        } else if (!isEmpty()) {
                            FileItem rowItem = newRow.getItem();
                            String displayText = rowItem == null ? getItem() : displayNameForTable.apply(rowItem);
                            if (displayText == null) {
                                sanitizeCell();
                            } else {
                                rebindForCurrentRow(displayText);
                            }
                        }
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        sanitizeCell();
                        return;
                    }

                    rebindForCurrentRow(item);
                }

                private void attachRowListeners(TableRow<FileItem> row) {
                    observedRow = row;
                    if (row == null) {
                        return;
                    }
                    row.itemProperty().addListener(rowItemListener);
                }

                private void detachRowListeners(TableRow<FileItem> row) {
                    if (row == null) {
                        return;
                    }
                    row.itemProperty().removeListener(rowItemListener);
                    if (observedRow == row) {
                        observedRow = null;
                    }
                }

                private void sanitizeCell() {
                    cancelPendingWork();
                    bindingStamp++;
                    lastRowItem = null;
                    lastPath = null;
                    lastIdentity = null;
                    lastDisplayText = null;
                    lastTypeKey = null;
                    lastFolder = false;
                    thumbnailPublished = false;
                    iconView.setImage(null);
                    textLabel.setText(null);
                    setText(null);
                    setGraphic(null);
                }

                private void cancelPendingWork() {
                    if (pendingThumb != null) {
                        pendingThumb.cancel(false);
                        pendingThumb = null;
                    }
                    if (pendingIcon != null) {
                        pendingIcon.cancel(false);
                        pendingIcon = null;
                    }
                    thumbMgr.unregister(this);
                }

                private void rebindForCurrentRow(String displayText) {
                    FileItem fi = currentRowItem();
                    if (fi == null) {
                        sanitizeCell();
                        return;
                    }

                    final Path p = fi.path();
                    final boolean dark = ctx.themeService().isDarkPreferred();
                    final boolean isFolder = isFolder(fi);
                    final String identity = resolveIdentity(fi, isFolder);
                    final String typeKey = computeTypeKey(fi, isFolder, identity);

                    if (isEquivalentBinding(fi, p, identity, displayText, typeKey, isFolder)) {
                        textLabel.setText(displayText);
                        setText(null);
                        if (iconView.getImage() == null) {
                            iconView.setImage(resolvePlaceholderImage(p, dark, identity, isFolder));
                        }
                        if (getGraphic() != box) {
                            setGraphic(box);
                        }
                        return;
                    }

                    cancelPendingWork();
                    bindingStamp++;

                    final long capturedStamp = bindingStamp;
                    final FileItem boundItem = fi;
                    final Image placeholder = resolvePlaceholderImage(p, dark, identity, isFolder);

                    lastRowItem = boundItem;
                    lastPath = p;
                    lastIdentity = identity;
                    lastDisplayText = displayText;
                    lastTypeKey = typeKey;
                    lastFolder = isFolder;
                    thumbnailPublished = false;

                    iconView.setImage(placeholder);
                    textLabel.setText(displayText);
                    setText(null);
                    setGraphic(box);

                    pendingIcon = AsyncIconService.getInstance().request(identity, dark, ICON_PX, AsyncIconService.RequestPriority.VISIBLE);
                    pendingIcon.thenAccept(img -> Platform.runLater(() -> {
                        if (!isCurrentBinding(capturedStamp, boundItem, p, identity, displayText, typeKey, isFolder)) {
                            return;
                        }
                        if (img == null || thumbnailPublished) {
                            return;
                        }
                        iconView.setImage(img);
                    }));

                    if (!isFolder && p != null && ImageSupport.isThumbCandidate(p)) {
                        thumbMgr.register(this, p, ICON_PX, identity, img -> {
                            if (!isCurrentBinding(capturedStamp, boundItem, p, identity, displayText, typeKey, false)) {
                                return;
                            }
                            if (img == null) {
                                return;
                            }
                            thumbnailPublished = true;
                            iconView.setImage(img);
                        });
                    } else {
                        thumbMgr.unregister(this);
                    }
                }

                private boolean isCurrentBinding(long capturedStamp,
                                                 FileItem boundItem,
                                                 Path path,
                                                 String identity,
                                                 String displayText,
                                                 String typeKey,
                                                 boolean isFolder) {
                    if (capturedStamp != bindingStamp) {
                        return false;
                    }
                    if (observedRow != null && getTableRow() != observedRow) {
                        return false;
                    }
                    if (lastRowItem != boundItem) {
                        return false;
                    }
                    if (!Objects.equals(lastPath, path)) {
                        return false;
                    }
                    if (!Objects.equals(lastIdentity, identity)) {
                        return false;
                    }
                    if (!Objects.equals(lastDisplayText, displayText)) {
                        return false;
                    }
                    if (!Objects.equals(lastTypeKey, typeKey)) {
                        return false;
                    }
                    if (lastFolder != isFolder) {
                        return false;
                    }

                    FileItem current = currentRowItem();
                    if (current == null || current != boundItem) {
                        return false;
                    }
                    if (!Objects.equals(current.path(), path)) {
                        return false;
                    }

                    boolean currentIsFolder = isFolder(current);
                    if (currentIsFolder != isFolder) {
                        return false;
                    }
                    String currentIdentity = resolveIdentity(current, currentIsFolder);
                    String currentTypeKey = computeTypeKey(current, currentIsFolder, currentIdentity);
                    if (!Objects.equals(currentIdentity, identity)) {
                        return false;
                    }
                    if (!Objects.equals(currentTypeKey, typeKey)) {
                        return false;
                    }

                    String currentDisplayText = displayNameForTable.apply(current);
                    if (!Objects.equals(currentDisplayText, displayText)) {
                        return false;
                    }

                    TableRow<FileItem> row = getTableRow();
                    return row != null && row.getItem() == current;
                }

                private FileItem currentRowItem() {
                    TableRow<FileItem> row = getTableRow();
                    return row == null ? null : row.getItem();
                }

                private boolean isFolder(FileItem item) {
                    return item != null && "Folder".equalsIgnoreCase(Objects.requireNonNullElse(item.type(), ""));
                }

                private String computeTypeKey(FileItem item, boolean isFolder, String identity) {
                    if (item == null) {
                        return identity;
                    }
                    return (isFolder ? "folder:" : "file:")
                            + Objects.requireNonNullElse(item.type(), "")
                            + "|"
                            + identity;
                }

                private String resolveIdentity(FileItem item, boolean isFolder) {
                    Path p = item == null ? null : item.path();
                    if (isFolder) {
                        return folderIdentityNoIo(p);
                    }
                    if (p != null) {
                        try {
                            String identity = ctx.fileMetadataService().iconIdentity(p);
                            if (identity != null && !identity.isBlank()) {
                                return identity;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    return computeIdentityNoIo(p, false);
                }

                private Image resolvePlaceholderImage(Path path, boolean dark, String identity, boolean isFolder) {
                    try {
                        return IconLoader.loadForIdentity(identity, dark, ICON_PX);
                    } catch (Exception ignored) {
                    }
                    try {
                        return IconLoader.placeholderForPath(path, dark, ICON_PX);
                    } catch (Exception ignored) {
                    }
                    return IconLoader.load(isFolder ? IconLoader.IconType.FOLDER : IconLoader.IconType.FILE, dark, ICON_PX);
                }

                private String folderIdentityNoIo(Path p) {
                    if (p == null) {
                        return "type:" + IconLoader.IconType.FOLDER.name();
                    }
                    try {
                        Path normalized = p.toAbsolutePath().normalize();
                        String raw = normalized.toString();
                        Path root = normalized.getRoot();
                        if (raw.startsWith("\\") || raw.startsWith("//")) {
                            return "special:networkdrive";
                        }
                        if (root != null && normalized.equals(root.normalize())) {
                            return "special:localdisk";
                        }
                    } catch (Exception ignored) {
                    }
                    return "type:" + IconLoader.IconType.FOLDER.name();
                }

                private String computeIdentityNoIo(Path p, boolean isFolder) {
                    if (isFolder) {
                        return folderIdentityNoIo(p);
                    }
                    if (p == null) {
                        return "type:" + IconLoader.IconType.FILE.name();
                    }

                    String name = p.getFileName() != null ? p.getFileName().toString() : p.toString();
                    int dot = name.lastIndexOf('.');
                    if (dot > 0 && dot < name.length() - 1) {
                        String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
                        if (!ext.isBlank()) {
                            return switch (ext) {
                                case "mp4", "mkv", "mov", "avi", "wmv", "webm", "m4v" -> "kind:video";
                                case "mp3", "wav", "flac", "m4a", "ogg", "aac", "wma" -> "kind:audio";
                                case "png", "jpg", "jpeg", "gif", "bmp", "webp", "avif", "heif", "heic", "tif", "tiff", "svg" -> "kind:image";
                                case "zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst" -> "kind:archive";
                                case "md", "log", "rtf", "cfg", "conf", "tsv", "json", "xml", "yaml", "yml", "properties" -> "kind:text";
                                default -> "ext:" + ext;
                            };
                        }
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

    private static final String DETAILS_REFRESH_COORDINATOR_KEY = TableViewSupport.class.getName() + ".detailsRefreshCoordinator";
    private static final String DETAILS_REFRESH_OBSERVATION_KEY = TableViewSupport.class.getName() + ".detailsRefreshObservationInstalled";

    private static DetailsViewRefreshCoordinator ensureRefreshCoordinator(TableView<FileItem> fileTable) {
        Object existing = fileTable.getProperties().get(DETAILS_REFRESH_COORDINATOR_KEY);
        if (existing instanceof DetailsViewRefreshCoordinator coordinator) {
            return coordinator;
        }
        DetailsViewRefreshCoordinator coordinator = new DetailsViewRefreshCoordinator(fileTable);
        fileTable.getProperties().put(DETAILS_REFRESH_COORDINATOR_KEY, coordinator);
        return coordinator;
    }

    private static void installRefreshObservation(TableView<FileItem> fileTable, DetailsViewRefreshCoordinator coordinator) {
        if (Boolean.TRUE.equals(fileTable.getProperties().get(DETAILS_REFRESH_OBSERVATION_KEY))) {
            return;
        }
        fileTable.getProperties().put(DETAILS_REFRESH_OBSERVATION_KEY, Boolean.TRUE);

        javafx.collections.ListChangeListener<FileItem> itemsListener = change -> {
            java.util.LinkedHashSet<Path> changedPaths = new java.util.LinkedHashSet<>();
            boolean structural = false;
            while (change.next()) {
                if (change.wasPermutated() || change.wasUpdated()) {
                    structural = true;
                }
                if (change.wasRemoved()) {
                    for (FileItem item : change.getRemoved()) {
                        if (item != null && item.path() != null) {
                            changedPaths.add(item.path());
                        }
                    }
                }
                if (change.wasAdded()) {
                    for (FileItem item : change.getAddedSubList()) {
                        if (item != null && item.path() != null) {
                            changedPaths.add(item.path());
                        }
                    }
                }
            }
            if (!changedPaths.isEmpty()) {
                TableHeaderContextMenuInstaller.resetEphemeralHeaderState(fileTable);
                coordinator.requestRefresh(changedPaths, structural ? "details-items-structural" : "details-items-delta");
            } else if (structural) {
                TableHeaderContextMenuInstaller.resetEphemeralHeaderState(fileTable);
                coordinator.requestVisibleRefresh("details-items-structure");
            }
        };

        fileTable.itemsProperty().addListener((obs, oldList, newList) -> {
            if (oldList != null) {
                oldList.removeListener(itemsListener);
            }
            if (newList != null) {
                newList.addListener(itemsListener);
            }
            TableHeaderContextMenuInstaller.resetEphemeralHeaderState(fileTable);
            coordinator.requestVisibleRefresh("details-items-source");
        });
        if (fileTable.getItems() != null) {
            fileTable.getItems().addListener(itemsListener);
        }

        fileTable.comparatorProperty().addListener((obs, oldValue, newValue) -> {
            TableHeaderContextMenuInstaller.resetEphemeralHeaderState(fileTable);
            coordinator.requestVisibleRefresh("details-comparator");
        });
        fileTable.getSortOrder().addListener((javafx.collections.ListChangeListener<TableColumn<FileItem, ?>>) change -> {
            TableHeaderContextMenuInstaller.resetEphemeralHeaderState(fileTable);
            coordinator.requestVisibleRefresh("details-sort-order");
        });
        fileTable.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (Boolean.TRUE.equals(isVisible)) {
                coordinator.requestVisibleRefresh("details-visible");
            }
        });
    }

    public static VisibleThumbnailManager visibleThumbnailManager(TableView<FileItem> table, ExplorerContext ctx) {
        return ensureThumbManager(table, ctx);
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
