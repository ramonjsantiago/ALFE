#!/usr/bin/env bash
set -euo pipefail

# Ultra File Explorer - Clean Windows-Style Build
# - JDK 25 target
# - JavaFX 25 via Maven
# - Non-modular (no module-info.java) to avoid module-path issues
# - Windows Explorer style:
#     * Left: folder tree
#     * Center: file table
#     * Bottom: preview pane + status bar
#     * Toolbar with New Folder / Delete / Open / Up / Refresh
# - OS integration:
#     * Open with system default app (Desktop.open)
#     * Move to Trash (Desktop.moveToTrash when supported)
# - Embedded icons (PNG) for folder/file/image/video/audio

PROJECT_ROOT="FileExplorer"
GROUP_ID="com.fileexplorer"
ARTIFACT_ID="FileExplorer"

echo "Creating project at $PROJECT_ROOT ..."
rm -rf "$PROJECT_ROOT"
mkdir -p "$PROJECT_ROOT"

SRC_BASE="$PROJECT_ROOT/src/main/java"
RES_BASE="$PROJECT_ROOT/src/main/resources"

SRC_DIR="$SRC_BASE/com/fileexplorer"
UI_PKG="$SRC_DIR/ui"
THUMB_PKG="$SRC_DIR/thumb"

FXML_DIR="$RES_BASE/com/fileexplorer/ui"
CSS_DIR="$RES_BASE/com/fileexplorer/ui/css"
ICON_DIR="$RES_BASE/com/fileexplorer/icons"

mkdir -p "$UI_PKG" "$THUMB_PKG" "$FXML_DIR" "$CSS_DIR" "$ICON_DIR"

# ============================================================
# pom.xml (non-modular JavaFX project)
# ============================================================
cat > "$PROJECT_ROOT/pom.xml" << 'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.fileexplorer</groupId>
    <artifactId>FileExplorer</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <javafx.version>25</javafx.version>
    </properties>

    <dependencies>
        <!-- JavaFX Core -->
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-fxml</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-graphics</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-media</artifactId>
            <version>${javafx.version}</version>
        </dependency>

        <!-- JUnit (optional) -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- JavaFX Maven Plugin for running -->
            <plugin>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-maven-plugin</artifactId>
                <version>0.0.8</version>
                <configuration>
                    <mainClass>com.fileexplorer.MainApp</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
EOF

echo "Created pom.xml"

# ============================================================
# MainApp.java
# ============================================================
cat > "$SRC_DIR/MainApp.java" << 'EOF'
package com.fileexplorer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/fileexplorer/ui/MainLayout.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 800);
        scene.getStylesheets().add(
                getClass().getResource("/com/fileexplorer/ui/css/light.css").toExternalForm());
        scene.getStylesheets().add(
                getClass().getResource("/com/fileexplorer/ui/css/statusbar.css").toExternalForm());
        scene.getStylesheets().add(
                getClass().getResource("/com/fileexplorer/ui/css/table.css").toExternalForm());

        stage.setTitle("Ultra File Explorer");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
EOF

echo "Created MainApp.java"

# ============================================================
# FileSizeFormatter.java
# ============================================================
cat > "$UI_PKG/FileSizeFormatter.java" << 'EOF'
package com.fileexplorer.ui;

public class FileSizeFormatter {

    private static final long KB = 1024;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;
    private static final long TB = GB * 1024;

    public static String format(long bytes) {
        if (bytes < KB) return bytes + " B";
        if (bytes < MB) return String.format("%.1f KB", (double) bytes / KB);
        if (bytes < GB) return String.format("%.1f MB", (double) bytes / MB);
        if (bytes < TB) return String.format("%.2f GB", (double) bytes / GB);
        return String.format("%.2f TB", (double) bytes / TB);
    }
}
EOF

echo "Created FileSizeFormatter.java"

# ============================================================
# FileMetadataService.java
# ============================================================
cat > "$UI_PKG/FileMetadataService.java" << 'EOF'
package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Optional;

public class FileMetadataService {

    public static class FileMetadata {
        public Path path;
        public boolean directory;
        public long size;
        public Instant created;
        public Instant modified;
        public Instant accessed;
        public String type;
    }

    public Optional<FileMetadata> read(Path file) {
        if (file == null || !Files.exists(file)) return Optional.empty();
        try {
            BasicFileAttributes attrs =
                    Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            FileMetadata m = new FileMetadata();
            m.path = file;
            m.directory = attrs.isDirectory();
            m.size = attrs.size();
            m.created = attrs.creationTime().toInstant();
            m.modified = attrs.lastModifiedTime().toInstant();
            m.accessed = attrs.lastAccessTime().toInstant();
            try {
                m.type = Files.probeContentType(file);
            } catch (IOException e) {
                m.type = null;
            }
            return Optional.of(m);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
EOF

echo "Created FileMetadataService.java"

# ============================================================
# IconLoader.java (uses embedded PNG icons)
# ============================================================
cat > "$UI_PKG/IconLoader.java" << 'EOF'
package com.fileexplorer.ui;

import javafx.scene.image.Image;

public class IconLoader {

    public enum IconType {
        FOLDER, FILE, IMAGE, VIDEO, AUDIO
    }

    private static Image folderIcon;
    private static Image fileIcon;
    private static Image imageIcon;
    private static Image videoIcon;
    private static Image audioIcon;

    private static Image load(String name) {
        return new Image(
            IconLoader.class.getResourceAsStream("/com/fileexplorer/icons/" + name));
    }

    public static Image get(IconType type) {
        return switch (type) {
            case FOLDER -> {
                if (folderIcon == null) folderIcon = load("folder.png");
                yield folderIcon;
            }
            case IMAGE -> {
                if (imageIcon == null) imageIcon = load("image.png");
                yield imageIcon;
            }
            case VIDEO -> {
                if (videoIcon == null) videoIcon = load("video.png");
                yield videoIcon;
            }
            case AUDIO -> {
                if (audioIcon == null) audioIcon = load("audio.png");
                yield audioIcon;
            }
            case FILE -> {
                if (fileIcon == null) fileIcon = load("file.png");
                yield fileIcon;
            }
        };
    }

    public static IconType typeFor(String contentType, boolean isDir) {
        if (isDir) return IconType.FOLDER;
        if (contentType == null) return IconType.FILE;
        if (contentType.startsWith("image")) return IconType.IMAGE;
        if (contentType.startsWith("video")) return IconType.VIDEO;
        if (contentType.startsWith("audio")) return IconType.AUDIO;
        return IconType.FILE;
    }
}
EOF

echo "Created IconLoader.java"

# ============================================================
# MainController.java (Windows Explorer-style)
# ============================================================
cat > "$UI_PKG/MainController.java" << 'EOF'
package com.fileexplorer.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.*;

public class MainController {

    @FXML private BorderPane root;

    // Toolbar buttons
    @FXML private Button upButton;
    @FXML private Button newFolderButton;
    @FXML private Button deleteButton;
    @FXML private Button openButton;
    @FXML private Button refreshButton;

    // Left tree (folders)
    @FXML private TreeView<Path> folderTree;

    // Center table (files)
    @FXML private TableView<FileItem> fileTable;
    @FXML private TableColumn<FileItem, String> nameColumn;
    @FXML private TableColumn<FileItem, String> typeColumn;
    @FXML private TableColumn<FileItem, String> sizeColumn;
    @FXML private TableColumn<FileItem, String> modifiedColumn;

    // Preview pane
    @FXML private VBox previewBox;
    @FXML private ImageView previewImage;
    @FXML private TextArea previewText;

    // Status bar
    @FXML private Label statusLeft;
    @FXML private Label statusRight;

    private Path currentFolder;
    private final FileMetadataService metadataService = new FileMetadataService();

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());

    @FXML
    private void initialize() {
        setupTree();
        setupTable();
        setupToolbar();
        setupPreview();

        // Start in user home
        Path home = Path.of(System.getProperty("user.home"));
        selectFolderInTree(home);
        loadFolder(home);
    }

    private void setupTree() {
        folderTree.setShowRoot(true);

        TreeItem<Path> rootItem = new TreeItem<>(null);
        rootItem.setExpanded(true);

        // Root drives / root folders
        try {
            for (Path root : FileSystems.getDefault().getRootDirectories()) {
                rootItem.getChildren().add(createTreeItem(root));
            }
        } catch (Exception e) {
            // fallback to user home
            Path home = Path.of(System.getProperty("user.home"));
            rootItem.getChildren().add(createTreeItem(home));
        }

        folderTree.setRoot(rootItem);
        folderTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String label = item.getFileName() != null ? item.getFileName().toString() : item.toString();
                    setText(label);
                    ImageView iv = new ImageView(
                        IconLoader.get(IconLoader.IconType.FOLDER));
                    iv.setFitWidth(16);
                    iv.setFitHeight(16);
                    setGraphic(iv);
                }
            }
        });

        folderTree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.getValue() != null) {
                loadFolder(newV.getValue());
            }
        });
    }

    private TreeItem<Path> createTreeItem(Path folder) {
        TreeItem<Path> item = new TreeItem<>(folder);
        item.setExpanded(false);
        item.getChildren().add(new TreeItem<>()); // dummy child
        item.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
            if (isExpanded) {
                if (item.getChildren().size() == 1 && item.getChildren().get(0).getValue() == null) {
                    item.getChildren().clear();
                    try (Stream<Path> s = Files.list(folder)) {
                        s.filter(Files::isDirectory)
                         .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                         .forEach(sub -> item.getChildren().add(createTreeItem(sub)));
                    } catch (IOException ignored) {}
                }
            }
        });
        return item;
    }

    private void setupTable() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().type()));
        sizeColumn.setCellValueFactory(data ->
                new SimpleStringProperty(FileSizeFormatter.format(data.getValue().size())));
        modifiedColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().modified()));

        fileTable.setRowFactory(tv -> {
            TableRow<FileItem> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) {
                    FileItem item = row.getItem();
                    updatePreview(item.path());
                    if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                        if (Files.isDirectory(item.path())) {
                            loadFolder(item.path());
                            selectFolderInTree(item.path());
                        } else {
                            openWithSystem(item.path());
                        }
                    }
                }
            });
            return row;
        });

        fileTable.setContextMenu(createContextMenu());
    }

    private ContextMenu createContextMenu() {
        MenuItem open = new MenuItem("Open");
        MenuItem openFolder = new MenuItem("Open Containing Folder");
        MenuItem delete = new MenuItem("Move to Recycle Bin");
        MenuItem props = new MenuItem("Properties");

        open.setOnAction(e -> {
            FileItem sel = fileTable.getSelectionModel().getSelectedItem();
            if (sel != null) openWithSystem(sel.path());
        });

        openFolder.setOnAction(e -> {
            FileItem sel = fileTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                Path parent = sel.path().getParent();
                if (parent != null) {
                    loadFolder(parent);
                    selectFolderInTree(parent);
                }
            }
        });

        delete.setOnAction(e -> {
            FileItem sel = fileTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                deletePath(sel.path());
            }
        });

        props.setOnAction(e -> {
            FileItem sel = fileTable.getSelectionModel().getSelectedItem();
            if (sel != null) showProperties(sel.path());
        });

        ContextMenu menu = new ContextMenu(open, openFolder, delete, props);
        return menu;
    }

    private void setupToolbar() {
        upButton.setOnAction(e -> {
            if (currentFolder != null) {
                Path parent = currentFolder.getParent();
                if (parent != null && Files.exists(parent)) {
                    loadFolder(parent);
                    selectFolderInTree(parent);
                }
            }
        });

        newFolderButton.setOnAction(e -> {
            if (currentFolder == null) return;
            try {
                String base = "New Folder";
                Path candidate = currentFolder.resolve(base);
                int i = 1;
                while (Files.exists(candidate)) {
                    candidate = currentFolder.resolve(base + " (" + i++ + ")");
                }
                Files.createDirectory(candidate);
                loadFolder(currentFolder);
                updateStatus("Created folder: " + candidate.getFileName(), "");
            } catch (IOException ex) {
                updateStatus("Failed to create folder: " + ex.getMessage(), "");
            }
        });

        deleteButton.setOnAction(e -> {
            FileItem sel = fileTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                deletePath(sel.path());
            }
        });

        openButton.setOnAction(e -> {
            FileItem sel = fileTable.getSelectionModel().getSelectedItem();
            if (sel != null) openWithSystem(sel.path());
        });

        refreshButton.setOnAction(e -> {
            if (currentFolder != null) loadFolder(currentFolder);
        });
    }

    private void setupPreview() {
        previewText.setEditable(false);
        previewText.setWrapText(true);
    }

    private void loadFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) return;
        currentFolder = folder;
        fileTable.getItems().clear();

        try (Stream<Path> s = Files.list(folder)) {
            List<FileItem> items = s.map(this::toFileItem)
                    .sorted(Comparator
                            .comparing(FileItem::isDirectory).reversed()
                            .thenComparing(fi -> fi.name().toLowerCase()))
                    .toList();
            fileTable.getItems().addAll(items);
            updateStatus("Items: " + items.size(), folder.toString());
        } catch (IOException e) {
            updateStatus("Failed to list folder: " + e.getMessage(), folder.toString());
        }

        // clear preview
        previewImage.setImage(null);
        previewText.clear();
    }

    private FileItem toFileItem(Path path) {
        boolean isDir = Files.isDirectory(path);
        long size = 0;
        String type = "";
        String modified = "";
        try {
            if (!isDir) size = Files.size(path);
            type = Files.probeContentType(path);
            var attrs = Files.getLastModifiedTime(path);
            modified = DT_FMT.format(attrs.toInstant());
        } catch (IOException ignored) {}

        return new FileItem(
                path,
                path.getFileName() != null ? path.getFileName().toString() : path.toString(),
                type != null ? type : (isDir ? "Folder" : "File"),
                size,
                modified,
                isDir
        );
    }

    private void updatePreview(Path file) {
        if (file == null || !Files.exists(file) || Files.isDirectory(file)) {
            previewImage.setImage(null);
            previewText.clear();
            return;
        }
        try {
            String type = Files.probeContentType(file);
            if (type != null && type.startsWith("image")) {
                previewText.clear();
                previewImage.setImage(new javafx.scene.image.Image(file.toUri().toString(), 300, 300, true, true));
            } else if (type != null && type.startsWith("text")) {
                previewImage.setImage(null);
                String content = readFirstLines(file, 2000);
                previewText.setText(content);
            } else {
                previewImage.setImage(null);
                previewText.setText("No preview available for this file type.");
            }
        } catch (IOException e) {
            previewImage.setImage(null);
            previewText.setText("Preview failed: " + e.getMessage());
        }
    }

    private String readFirstLines(Path file, int maxChars) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int ch;
            while ((ch = reader.read()) != -1 && sb.length() < maxChars) {
                sb.append((char) ch);
            }
        }
        if (sb.length() == maxChars) {
            sb.append("\n...\n");
        }
        return sb.toString();
    }

    private void openWithSystem(Path file) {
        try {
            if (!Desktop.isDesktopSupported()) {
                updateStatus("Desktop API not supported", "");
                return;
            }
            Desktop.getDesktop().open(file.toFile());
            updateStatus("Opened with system: " + file.getFileName(), file.toString());
        } catch (IOException e) {
            updateStatus("Open failed: " + e.getMessage(), file.toString());
        }
    }

    private void deletePath(Path file) {
        File f = file.toFile();
        try {
            if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                boolean ok = Desktop.getDesktop().moveToTrash(f);
                if (!ok) {
                    updateStatus("Move to Trash not allowed", file.toString());
                    return;
                }
            } else {
                // fallback: delete
                if (Files.isDirectory(file)) {
                    try (Stream<Path> walk = Files.walk(file)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(p -> {
                                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                                });
                    }
                } else {
                    Files.deleteIfExists(file);
                }
            }
            updateStatus("Deleted: " + file.getFileName(), file.toString());
            if (currentFolder != null) loadFolder(currentFolder);
        } catch (IOException e) {
            updateStatus("Delete failed: " + e.getMessage(), file.toString());
        }
    }

    private void showProperties(Path file) {
        var opt = metadataService.read(file);
        if (opt.isEmpty()) {
            updateStatus("No metadata for: " + file.getFileName(), file.toString());
            return;
        }
        var m = opt.get();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Properties");
        alert.setHeaderText(file.getFileName() != null ? file.getFileName().toString() : file.toString());
        StringBuilder sb = new StringBuilder();
        sb.append("Path: ").append(file.toAbsolutePath()).append("\n");
        sb.append("Directory: ").append(m.directory).append("\n");
        sb.append("Size: ").append(FileSizeFormatter.format(m.size)).append("\n");
        if (m.type != null) sb.append("Type: ").append(m.type).append("\n");
        if (m.created != null) sb.append("Created: ").append(DT_FMT.format(m.created)).append("\n");
        if (m.modified != null) sb.append("Modified: ").append(DT_FMT.format(m.modified)).append("\n");
        if (m.accessed != null) sb.append("Accessed: ").append(DT_FMT.format(m.accessed)).append("\n");
        alert.setContentText(sb.toString());
        alert.showAndWait();
    }

    private void updateStatus(String left, String right) {
        Platform.runLater(() -> {
            statusLeft.setText(left);
            statusRight.setText(right);
        });
    }

    private void selectFolderInTree(Path folder) {
        if (folderTree.getRoot() == null) return;
        TreeItem<Path> found = findTreeItem(folderTree.getRoot(), folder);
        if (found != null) {
            folderTree.getSelectionModel().select(found);
        }
    }

    private TreeItem<Path> findTreeItem(TreeItem<Path> current, Path target) {
        if (current.getValue() != null &&
            Objects.equals(current.getValue().normalize(), target.normalize())) {
            return current;
        }
        for (TreeItem<Path> child : current.getChildren()) {
            TreeItem<Path> r = findTreeItem(child, target);
            if (r != null) return r;
        }
        return null;
    }

    public record FileItem(Path path, String name, String type,
                           long size, String modified, boolean isDirectory) {}
}
EOF

echo "Created MainController.java"

# ============================================================
# MainLayout.fxml
# ============================================================
cat > "$FXML_DIR/MainLayout.fxml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.image.ImageView?>
<?import javafx.geometry.Orientation?>
<?import javafx.geometry.Insets?>

<BorderPane xmlns="http://javafx.com/javafx/25"
            xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="com.fileexplorer.ui.MainController">

    <top>
        <ToolBar>
            <Button fx:id="upButton" text="Up"/>
            <Separator orientation="VERTICAL"/>
            <Button fx:id="newFolderButton" text="New Folder"/>
            <Button fx:id="deleteButton" text="Delete"/>
            <Separator orientation="VERTICAL"/>
            <Button fx:id="openButton" text="Open"/>
            <Button fx:id="refreshButton" text="Refresh"/>
        </ToolBar>
    </top>

    <left>
        <TreeView fx:id="folderTree" prefWidth="260"/>
    </left>

    <center>
        <SplitPane orientation="VERTICAL" dividerPositions="0.65">
            <items>
                <TableView fx:id="fileTable">
                    <columns>
                        <TableColumn fx:id="nameColumn" text="Name" prefWidth="260.0"/>
                        <TableColumn fx:id="typeColumn" text="Type" prefWidth="160.0"/>
                        <TableColumn fx:id="sizeColumn" text="Size" prefWidth="120.0"/>
                        <TableColumn fx:id="modifiedColumn" text="Modified" prefWidth="200.0"/>
                    </columns>
                </TableView>
                <VBox fx:id="previewBox" spacing="4" style="-fx-padding: 6;">
                    <Label text="Preview"/>
                    <ImageView fx:id="previewImage" fitWidth="300" fitHeight="300" preserveRatio="true"/>
                    <TextArea fx:id="previewText" prefRowCount="8"/>
                </VBox>
            </items>
        </SplitPane>
    </center>

    <bottom>
        <HBox spacing="12" styleClass="status-bar-root">
            <Label fx:id="statusLeft" text="Ready"/>
            <Pane HBox.hgrow="ALWAYS"/>
            <Label fx:id="statusRight" text=""/>
        </HBox>
    </bottom>
</BorderPane>
EOF

echo "Created MainLayout.fxml"

# ============================================================
# CSS files
# ============================================================
cat > "$CSS_DIR/light.css" << 'EOF'
.root {
    -fx-font-family: "Segoe UI", sans-serif;
    -fx-font-size: 12pt;
    -fx-background-color: #f3f3f3;
}
.tree-view, .table-view, .text-area {
    -fx-background-color: white;
}
.tool-bar {
    -fx-background-color: linear-gradient(to bottom, #ffffff, #e5e5e5);
}
.button {
    -fx-focus-color: transparent;
}
EOF

cat > "$CSS_DIR/statusbar.css" << 'EOF'
.status-bar-root {
    -fx-padding: 4 10;
    -fx-background-color: #e5e5e5;
    -fx-border-color: #c0c0c0;
    -fx-border-width: 1 0 0 0;
}
.status-bar-root .label {
    -fx-font-size: 11px;
}
EOF

cat > "$CSS_DIR/table.css" << 'EOF'
.table-view {
    -fx-cell-size: 24px;
}
.table-view .column-header-background {
    -fx-background-color: linear-gradient(#f7f7f7, #dcdcdc);
}
.table-view .column-header, .table-view .filler {
    -fx-border-color: #c0c0c0;
}
EOF

echo "Created CSS files"

# ============================================================
# Embedded icons (simple 1x1 PNG placeholders)
# ============================================================
# Use the same tiny PNG for all; they can be replaced later.
BASE64_PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgYAAAAAMAASsJTYQAAAAASUVORK5CYII="

for name in folder file image video audio; do
  echo "$BASE64_PNG" | base64 -d > "$ICON_DIR/$name.png"
done

echo "Created placeholder icons (folder.png, file.png, image.png, video.png, audio.png)"

# ============================================================
# README.md
# ============================================================
cat > "$PROJECT_ROOT/README.md" << 'EOF'
# Ultra File Explorer

Generated by `generate-ultra-file-explorer-full.sh`.

Features:

- JavaFX-based desktop file explorer
- Windows Explorer–style layout:
  - Left folder tree
  - Center file table (name, type, size, modified)
  - Bottom preview (image or text) + status bar
- OS integration:
  - Open file with system default application (Desktop.open)
  - Move to Recycle Bin / Trash when supported (Desktop.moveToTrash)
- Simple properties dialog (size, type, timestamps)

## Build and Run

Requires JDK 25 (or later compatible) and Maven.

```bash
cd FileExplorer
mvn clean javafx:run
EOF

echo "Created README.md"

# ============================================================
# ZIP project
# ============================================================

OUT_ZIP="UltraFileExplorer-Full.zip"
echo "Creating zip archive $OUT_ZIP (if 'zip' is available)..."
(
cd "$PROJECT_ROOT"
zip -r "../$OUT_ZIP" . >/dev/null 2>&1 || true
)

echo "Done."
echo "Project root: $PROJECT_ROOT"
echo "ZIP (if created): $OUT_ZIP"
echo "To run: cd FileExplorer && mvn clean javafx:run"