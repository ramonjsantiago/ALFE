CHANGELOG.md

## 2025-12-10

### Added
- Window sizing now scales with screen resolution (roughly 40–50% of screen, instead of tiny default window).
- Theme system based on root style classes: `.theme-light`, `.theme-dark`, `.theme-system` on the `explorer-root` node.
- Theme Debug dialog wired to `ThemeService` to switch between System / Light / Dark at runtime.
- Status bar logic:
  - Left side: `<N> items` (total items in the current folder view).
  - Right side: `<K> selected, <SIZE>` using human-readable size.
  - Bottom-right view buttons (`statusDetailsButton`, `statusLargeIconsButton`) mirror `detailsToggle` / `largeIconsToggle` and stay mutually exclusive.
- Toolbar under breadcrumb:
  - `New` menu (Folder, Text Document).
  - Cut, Copy, Paste, Rename, Delete icon buttons (currently disabled as placeholders).
  - `Sort`, `View`, and `…` menus.
  - Right-aligned `Details` button to show/hide the details/preview pane.

### Changed
- All CSS gradients removed from table headers and controls; headers now use flat backgrounds.
- TableView:
  - Headers are left-aligned.
  - No alternating row stripes; solid background using theme tokens.
  - `sizeColumn` is right-aligned via `.size-column` style class and CSS.
  - Date/time column formatted as `MM/dd/yyyy hh:mm a` (for example: `12/08/2025 12:00 PM`).
- Icon handling:
  - Toolbar and status bar buttons attempt to load icons via `IconLoader`.
  - When an icon cannot be loaded, a placeholder glyph/icon is used instead of leaving the button blank.
  - `.ico` files can be used to override default file/folder icons where available.
- Scrollbars styled to be “Win-ish”:
  - Flat colors, no gradients.
  - Theme-aware colors (light vs dark).
  - Narrower thumb and track styling compared to default JavaFX.

### Fixed
- Theme Debug dialog now actually switches theme classes on the root node instead of being informational only.
- Dark theme now correctly colors:
  - Tree background and text.
  - Table header and row background/foreground.
  - Preview pane background/foreground.
  - Status bar background and text to remain readable in dark mode.
- Removed use of wildcard imports from newly updated Java files (explicit imports only).


FileExplorer/
├─ pom.xml
├─ README.md                      (optional)
├─ src/
│  ├─ main/
│  │  ├─ java/
│  │  │  └─ com/
│  │  │     └─ fileexplorer/
│  │  │        ├─ MainApp.java
│  │  │        └─ ui/
│  │  │           ├─ MainController.java
│  │  │           ├─ ThemeService.java
│  │  │           ├─ BreadcrumbBar.java
│  │  │           ├─ BreadcrumbBarController.java
│  │  │           ├─ BreadcrumbController.java
│  │  │           ├─ FileMetadataService.java
│  │  │           ├─ FileSizeFormatter.java
│  │  │           ├─ IconLoader.java
│  │  │           └─ (any additional UI helpers, e.g. models)
│  │  └─ resources/
│  │     └─ com/
│  │        └─ fileexplorer/
│  │           └─ ui/
│  │              ├─ MainLayout.fxml
│  │              ├─ BreadcrumbBar.fxml
│  │              ├─ BreadcrumbController.fxml          (if used)
│  │              ├─ css/
│  │              │  ├─ explorer-base.css
│  │              │  ├─ explorer-table.css
│  │              │  ├─ explorer-theme.css
│  │              │  ├─ explorer-light-win.css
│  │              │  ├─ explorer-dark-win.css
│  │              │  ├─ explorer-fluent.css
│  │              │  └─ (any other CSS you add)
│  │              └─ icons/
│  │                 ├─ folder-light-16.png
│  │                 ├─ folder-dark-16.png
│  │                 ├─ file-text-light-16.png
│  │                 ├─ file-text-dark-16.png
│  │                 └─ (all other images used by IconLoader)
│  └─ test/
│     └─ java/
│        └─ com/
│           └─ fileexplorer/
│              └─ (unit tests, if any)
└─ target/
   ├─ classes/
   │  └─ com/
   │     └─ fileexplorer/
   │        └─ ui/
   │           ├─ *.class
   │           └─ css/… (compiled resources)
   └─ (other Maven build output)

# Changelog

All notable changes to this project are documented in this file.

## [1.3.0] – 2025-12-11 (Build 1300)

### Added

- **Windows 11–style dark theme**
  - Introduced token-based dark theme (`theme-dark`) aligned with Win11 dark:
    - Layered backgrounds for window, surfaces, toolbars, table headers, and subtle hover states.
    - Flat, border-based separation (no gradients anywhere in the UI).
  - Themed:
    - Toolbars and command bars (`fluent-toolbar`, generic `tool-bar`).
    - Status bar (`status-bar-root`).
    - Preview pane container (`fluent-preview-box`).
    - TreeView, TableView (details view), TextField, and TextArea for dark mode.

- **Icon layouts and scaling**
  - Added support for multiple layout modes:
    - `DETAILS` – classic table view.
    - `TILES` – multi-column tile layout with medium icons.
    - `CONTENT` – multi-column tile layout with richer metadata.
  - Introduced `IconScale` enum with standard icon sizes:
    - `SMALL_16`, `MEDIUM_32`, `LARGE_48`, `EXTRA_LARGE_96`, `JUMBO_256`.
  - Implemented View menu options:
    - Extra large icons
    - Large icons
    - Medium icons
    - Small icons
    - List
    - Details
    - Tiles
    - Content

- **FlowPane-based icon tiles**
  - Implemented `iconScrollPane` + `iconFlowPane` icon-view:
    - Wrapping tiles in multiple columns as the window expands.
    - `IconTile` class with:
      - `ImageView` for the icon.
      - Primary label (file/folder name).
      - Secondary label (type/size/modified metadata depending on layout).
  - Integrated tiles with:
    - Double-click to open.
    - Single-click selection.
    - Selection synchronization with the details table.

- **Centralized IconLoader**
  - New `IconLoader` implementation:
    - `IconType` categories: `FOLDER`, `FILE`, `IMAGE`, `TEXT`, `BACK`, `FORWARD`, `UP`, `REFRESH`.
    - MIME and filename-based type inference (e.g. `image/*`, `text/*`, `.png`, `.jpg`, `.txt`, `.json`, `.xml`, `.md`, `.log`).
  - Asset loading:
    - Uses raster icons from `/com/fileexplorer/ui/icons/<light|dark>/<base>-<size>.png`.
    - Standard bitmap sizes supported: `16, 24, 32, 48, 64, 96, 128, 256`.
  - Fallback logic:
    - If the exact requested size is not present, finds the closest standard size and lets JavaFX scale to the requested size.
    - If no bitmap exists, generates vector placeholders:
      - Folder icon.
      - Image thumbnail placeholder.
      - Text document placeholder.
      - Generic file placeholder.

- **Image thumbnails**
  - `IconLoader.loadForFile(Path, mime, dark, size)`:
    - For image files, loads the actual file content as the icon and scales it to the requested size, preserving aspect ratio.
    - For non-image files, delegates to the type-based icon loader.

- **Integrated icon loading in MainController**
  - New helper in `MainController`:
    - `loadIconForItem(FileItem item, int size)`:
      - Detects MIME via `FileMetadataService`.
      - Determines dark/light theme via `ThemeService`.
      - Loads icons through `IconLoader.loadForFile`.
  - Icons are now consistently supplied for:
    - Details view (table icon column).
    - Tiles/Content views (FlowPane icon tiles).
    - Folder tree nodes (`TreeView<Path>` root and children).

- **Status-bar view toggles**
  - Added `statusDetailsButton` and `statusLargeIconsButton`:
    - Styled via `status-toggle-button` CSS.
    - Kept in sync with top toolbar:
      - `detailsToggle` (preview/details pane visibility).
      - `largeIconsToggle` (small vs large icon scale).
  - Single place to switch details visibility and icon size from the status bar.

- **Theme debug dialog**
  - Added `themeDebugToggle` action to open a theme debug dialog:
    - Shows current theme (SYSTEM / LIGHT / DARK).
    - Allows explicit theme selection via `ThemeService.applyTheme`.

### Changed

- **Main controller responsibilities**
  - `MainController` now owns:
    - Layout mode (`LayoutMode`) and icon scale (`IconScale`) state.
    - Construction and refresh of icon tiles (`IconTile` inner class).
    - Synchronization of:
      - Details table selection.
      - Icon-tile selection.
      - Status bar text.
      - Edit button enable/disable state.
      - Preview pane content.

- **Details view row height**
  - Table row height now scales with icon size (`setFixedCellSize`):
    - ~28 px for 16×16.
    - ~40 px for 32×32.
    - ~56 px for 48×48.
    - ~96 px for 96×96.
    - ~256 px for 256×256.

- **Preview pane behavior**
  - Preview pane initially hidden (`setDetailsVisible(false)`).
  - Text preview:
    - For `text/*` MIME types and common textual extensions, loads UTF-8 content into `previewText`.
  - Preview is updated from whichever layout is active (Details vs Tiles/Content) via unified selection logic.

- **Dark theme styling**
  - Replaced all gradient-based JavaFX defaults with flat backgrounds:
    - All `ToolBar` instances under `.explorer-root.theme-dark`.
    - All buttons/toggles/menu buttons in the explorer:
      - Transparent by default.
      - Soft hover overlays only (RGBA fills).
      - Accent-colored background for selected states.
  - Updated color tokens in `explorer-theme.css` to lighter, Win11-like neutrals:
    - Window background `#252526`.
    - Surface/background variants in the `#2f3034`–`#36373c` range.
    - Selection accent aligned with `#0f6cbd`.

- **Folder tree icons**
  - Tree items now use `IconLoader` instead of ad-hoc icons.
  - Icons respect current theme (light/dark).

### Fixed

- **Large icon / tiles placeholder issue**
  - Previously, large icon modes showed blank grey rectangles when large bitmaps were not available.
  - Resolved by:
    - Centralizing icon loading via `IconLoader`.
    - Implementing explicit fallback across standard sizes.
    - Using image thumbnails for image files instead of generic file icons.

- **Selection and status synchronization**
  - Selection changes in Tiles/Content view now:
    - Update the status bar item count and selected size.
    - Enable/disable edit actions (`open`, `delete`, `rename`, etc.).
    - Keep table selection roughly in sync for consistent behavior between modes.

- **Preview pane theme consistency**
  - Preview TextArea and container now use theme background colors:
    - No longer render as bright white in dark mode.
  - Search field and other text fields now use theme-consistent backgrounds and placeholder colors.

---

## [1.2.0] – 2025-12-10 (Build 1247)

> Summary of previous iteration (high level; details retained for context).

### Added

- Initial implementation of:
  - Dark and light theme switching via `ThemeService`.
  - Token-based color variables in CSS for explorer surfaces, borders, and text.
  - Basic preview pane and status bar.

### Changed

- Refined scene stylesheet loading and theme application to avoid redundant styles.
- Adjusted tree and table styling for clearer separation in dark mode.

### Fixed

- Resolved JavaFX CSS warnings caused by invalid `-fx-background-color` values (string vs `Paint`).
- Cleaned up remaining gradient references in custom styles.

---

## [1.1.0] – 2025-12-03 (Build 1147)

> Initial File Explorer UI foundation.

### Added

- Core JavaFX Explorer shell:
  - Left-hand folder `TreeView`.
  - Right-hand details `TableView` (name, type, size, modified).
  - Basic toolbar with navigation (`Back`, `Forward`, `Up`, `Refresh`).
  - New folder and delete operations.
- Initial status bar showing item counts.
- Basic file-type detection via `FileMetadataService`.
- Simple icon loading support and placeholder icons.

---

## [Unreleased]

Planned work:

- Hook up `BreadcrumbBar` / `BreadcrumbController` to:
  - Reflect current path segments as clickable breadcrumb buttons.
  - Support navigation to parent segments via breadcrumb.
  - Keep tree selection, folder path, and breadcrumb trail fully synchronized.
- Enhance content preview:
  - Add image previews in the preview pane for image files.
  - Add configurable preview size limits and lazy loading for large files.
- Add user preferences:
  - Persist last layout mode (Details/Tiles/Content).
  - Persist last used icon size and window size/location between sessions.

---

## Versioning

- Build numbers (e.g., 1147, 1247, 1300) correspond to internal project milestones.
- Semantic version (`1.x.y`) reflects feature-level changes and backwards compatibility at the UI/API level.
