#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="FileExplorer"
if [ ! -d "$PROJECT_ROOT" ]; then
  echo "ERROR: $PROJECT_ROOT not found. Run generate-ultra-file-explorer-full.sh first."
  exit 1
fi

SRC_BASE="$PROJECT_ROOT/src/main/java"
RES_BASE="$PROJECT_ROOT/src/main/resources"

SRC_DIR="$SRC_BASE/com/fileexplorer"
UI_PKG="$SRC_DIR/ui"
FXML_DIR="$RES_BASE/com/fileexplorer/ui"
CSS_DIR="$RES_BASE/com/fileexplorer/ui/css"
ICON_DIR="$RES_BASE/com/fileexplorer/icons"

mkdir -p "$CSS_DIR" "$ICON_DIR"

echo "Applying Windows Explorer theme pack..."

# ============================================================
# 1) Windows Explorer CSS theme
#    - explorer-base.css: general app + fonts
#    - explorer-light-win.css: colors, paddings
#    - explorer-table.css: table specific tuning
# ============================================================

cat > "$CSS_DIR/explorer-base.css" << 'EOF'
/* explorer-base.css
 * Global font + base look
 */

.root {
    -fx-font-family: "Segoe UI", "Segoe UI Variable", system-ui, sans-serif;
    -fx-font-size: 13.5px;
    -fx-background-color: #f3f3f3;
}

/* Generic background anchor */
.explorer-background {
    -fx-background-color: #f3f3f3;
}

/* Tree + table default backgrounds */
.tree-view,
.table-view,
.text-area {
    -fx-background-color: white;
}

/* Disable ugly focus rings */
.button:focused,
.tree-view:focused,
.table-view:focused {
    -fx-focus-color: transparent;
    -fx-faint-focus-color: transparent;
}
EOF

cat > "$CSS_DIR/explorer-light-win.css" << 'EOF'
/* explorer-light-win.css
 * Windows 10/11 Explorer-style colors & layout
 */

/* Toolbar -------------------------------------------------- */

.toolbar {
    -fx-background-color: linear-gradient(#fafafa, #eaeaea);
    -fx-border-color: #c8c8c8;
    -fx-border-width: 0 0 1 0;
    -fx-padding: 4 8 4 8;
    -fx-spacing: 4;
}

.toolbar .button {
    -fx-background-radius: 2;
    -fx-padding: 4 10 4 10;
    -fx-font-size: 13px;
    -fx-background-color: transparent;
}

.toolbar .button:hover {
    -fx-background-color: #d7e8fa;
}

.toolbar .button:armed {
    -fx-background-color: #c2defc;
}

/* Left folder tree ---------------------------------------- */

.explorer-tree {
    -fx-background-color: #ffffff;
    -fx-padding: 4 2 4 2;
    -fx-border-color: #c8c8c8;
    -fx-border-width: 0 1 0 0;
}

.explorer-tree .tree-cell {
    -fx-padding: 3 2 3 6;
    -fx-font-size: 13px;
}

.explorer-tree .tree-cell:selected {
    -fx-background-color: #cce8ff;
}

/* Table (file list) --------------------------------------- */

.explorer-table {
    -fx-background-color: white;
    -fx-padding: 0;
}

.explorer-table .column-header-background {
    -fx-background-color: linear-gradient(#f7f7f7, #e5e5e5);
    -fx-border-color: #c8c8c8;
    -fx-border-width: 0 0 1 0;
}

.explorer-table .column-header,
.explorer-table .filler {
    -fx-size: 28px;
    -fx-font-size: 13px;
    -fx-border-color: transparent;
    -fx-padding: 0 6 0 6;
}

.explorer-table .table-row-cell {
    -fx-cell-size: 24px;
}

.explorer-table .table-row-cell:selected {
    -fx-background-color: #cce8ff;
}

.explorer-table .table-row-cell:hover {
    -fx-background-color: #e6f2ff;
}

/* Preview area -------------------------------------------- */

.preview-box {
    -fx-background-color: #ffffff;
    -fx-border-color: #c8c8c8;
    -fx-border-width: 1 0 0 0;
}

/* Status bar ---------------------------------------------- */

.status-bar-root {
    -fx-padding: 2 10 2 10;
    -fx-background-color: #e5e5e5;
    -fx-border-color: #c8c8c8;
    -fx-border-width: 1 0 0 0;
}

.status-bar-root .label {
    -fx-font-size: 11px;
}
EOF

cat > "$CSS_DIR/explorer-table.css" << 'EOF'
/* explorer-table.css
 * Additional table tweaks (can be loaded separately if needed)
 */
.table-view {
    -fx-table-cell-border-color: transparent;
}
EOF

echo "Created Windows Explorer CSS theme files."

# ============================================================
# 2) Update MainLayout.fxml to use style classes
# ============================================================

MAIN_FXML="$FXML_DIR/MainLayout.fxml"
if [ ! -f "$MAIN_FXML" ]; then
  echo "WARNING: $MAIN_FXML not found; skipping FXML patch."
else
  cp "$MAIN_FXML" "$MAIN_FXML.bak.win-theme"

  cat > "$MAIN_FXML" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.image.ImageView?>
<?import javafx.geometry.Orientation?>

<BorderPane xmlns="http://javafx.com/javafx/25"
            xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="com.fileexplorer.ui.MainController"
            styleClass="explorer-background">

    <top>
        <ToolBar fx:id="ribbonBar" styleClass="toolbar">
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
        <TreeView fx:id="folderTree" prefWidth="260" styleClass="explorer-tree"/>
    </left>

    <center>
        <SplitPane orientation="VERTICAL" dividerPositions="0.65">
            <items>
                <TableView fx:id="fileTable" styleClass="explorer-table">
                    <columns>
                        <TableColumn fx:id="nameColumn" text="Name" prefWidth="260.0"/>
                        <TableColumn fx:id="typeColumn" text="Type" prefWidth="160.0"/>
                        <TableColumn fx:id="sizeColumn" text="Size" prefWidth="120.0"/>
                        <TableColumn fx:id="modifiedColumn" text="Modified" prefWidth="200.0"/>
                    </columns>
                </TableView>
                <VBox fx:id="previewBox" spacing="4" styleClass="preview-box" style="-fx-padding: 6;">
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

  echo "Updated MainLayout.fxml with Explorer style classes (backup: MainLayout.fxml.bak.win-theme)"
fi

# ============================================================
# 3) Update MainApp.java to load new CSS
# ============================================================

MAIN_APP="$SRC_DIR/MainApp.java"
if [ -f "$MAIN_APP" ]; then
  cp "$MAIN_APP" "$MAIN_APP.bak.win-theme"
  cat > "$MAIN_APP" << 'EOF'
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

        // Windows Explorer theme
        scene.getStylesheets().add(
                getClass().getResource("/com/fileexplorer/ui/css/explorer-base.css").toExternalForm());
        scene.getStylesheets().add(
                getClass().getResource("/com/fileexplorer/ui/css/explorer-light-win.css").toExternalForm());
        scene.getStylesheets().add(
                getClass().getResource("/com/fileexplorer/ui/css/explorer-table.css").toExternalForm());

        stage.setTitle("Ultra File Explorer");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
EOF
  echo "Updated MainApp.java to use Windows Explorer theme (backup: MainApp.java.bak.win-theme)"
else
  echo "WARNING: MainApp.java not found; skipping."
fi

# ============================================================
# 4) Extend IconLoader for size variants
#    - Keep same API + add getSized(...)
#    - Files will live under: icons/{16,32,64,128}/folder.png etc.
# ============================================================

ICON_LOADER="$UI_PKG/IconLoader.java"
if [ -f "$ICON_LOADER" ]; then
  cp "$ICON_LOADER" "$ICON_LOADER.bak.win-theme"

  cat > "$ICON_LOADER" << 'EOF'
package com.fileexplorer.ui;

import javafx.scene.image.Image;

public class IconLoader {

    public enum IconType {
        FOLDER, FILE, IMAGE, VIDEO, AUDIO
    }

    // default (16px) small icons
    private static Image folder16, file16, image16, video16, audio16;

    // larger variants
    private static Image folder32, file32, image32, video32, audio32;
    private static Image folder64, file64, image64, video64, audio64;
    private static Image folder128, file128, image128, video128, audio128;

    private static Image load(String path) {
        return new Image(
            IconLoader.class.getResourceAsStream("/com/fileexplorer/icons/" + path));
    }

    // Original small icon API
    public static Image get(IconType type) {
        return get(type, 16);
    }

    // Size-aware API: 16, 32, 64, 128
    public static Image get(IconType type, int size) {
        int s = switch (size) {
            case 32 -> 32;
            case 64 -> 64;
            case 128 -> 128;
            default -> 16;
        };

        return switch (type) {
            case FOLDER -> switch (s) {
                case 32 -> {
                    if (folder32 == null) folder32 = load("32/folder.png");
                    yield folder32;
                }
                case 64 -> {
                    if (folder64 == null) folder64 = load("64/folder.png");
                    yield folder64;
                }
                case 128 -> {
                    if (folder128 == null) folder128 = load("128/folder.png");
                    yield folder128;
                }
                default -> {
                    if (folder16 == null) folder16 = load("16/folder.png");
                    yield folder16;
                }
            };
            case IMAGE -> switch (s) {
                case 32 -> {
                    if (image32 == null) image32 = load("32/image.png");
                    yield image32;
                }
                case 64 -> {
                    if (image64 == null) image64 = load("64/image.png");
                    yield image64;
                }
                case 128 -> {
                    if (image128 == null) image128 = load("128/image.png");
                    yield image128;
                }
                default -> {
                    if (image16 == null) image16 = load("16/image.png");
                    yield image16;
                }
            };
            case VIDEO -> switch (s) {
                case 32 -> {
                    if (video32 == null) video32 = load("32/video.png");
                    yield video32;
                }
                case 64 -> {
                    if (video64 == null) video64 = load("64/video.png");
                    yield video64;
                }
                case 128 -> {
                    if (video128 == null) video128 = load("128/video.png");
                    yield video128;
                }
                default -> {
                    if (video16 == null) video16 = load("16/video.png");
                    yield video16;
                }
            };
            case AUDIO -> switch (s) {
                case 32 -> {
                    if (audio32 == null) audio32 = load("32/audio.png");
                    yield audio32;
                }
                case 64 -> {
                    if (audio64 == null) audio64 = load("64/audio.png");
                    yield audio64;
                }
                case 128 -> {
                    if (audio128 == null) audio128 = load("128/audio.png");
                    yield audio128;
                }
                default -> {
                    if (audio16 == null) audio16 = load("16/audio.png");
                    yield audio16;
                }
            };
            case FILE -> switch (s) {
                case 32 -> {
                    if (file32 == null) file32 = load("32/file.png");
                    yield file32;
                }
                case 64 -> {
                    if (file64 == null) file64 = load("64/file.png");
                    yield file64;
                }
                case 128 -> {
                    if (file128 == null) file128 = load("128/file.png");
                    yield file128;
                }
                default -> {
                    if (file16 == null) file16 = load("16/file.png");
                    yield file16;
                }
            };
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

  echo "Updated IconLoader.java to support 16/32/64/128 icons (backup: IconLoader.java.bak.win-theme)"
else
  echo "WARNING: IconLoader.java not found; skipping IconLoader patch."
fi

# ============================================================
# 5) Create icon folder structure + placeholder PNGs
#    (simple flat placeholders – safe to replace with your own)
# ============================================================

for sz in 16 32 64 128; do
  mkdir -p "$ICON_DIR/$sz"
done

# Tiny valid PNG (1x1 white). Same data used for all placeholders.
BASE64_PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgYAAAAAMAASsJTYQAAAAASUVORK5CYII="

for sz in 16 32 64 128; do
  for name in folder file image video audio; do
    echo "$BASE64_PNG" | base64 -d > "$ICON_DIR/$sz/$name.png"
  done
done

echo "Created placeholder icons for sizes 16/32/64/128 (folder/file/image/video/audio)."
echo
echo "Windows Explorer theme pack applied."
echo "Next: cd FileExplorer && mvn clean javafx:run"
