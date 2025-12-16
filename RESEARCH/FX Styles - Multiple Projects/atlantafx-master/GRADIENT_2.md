# Changelog

All notable changes to this project are documented in this file.

The entries below describe the current FileExplorer “1300” iteration. Earlier
iterations (e.g., 11xx / 12xx) are not exhaustively documented here.

---

## [Unreleased]

### Added

- **Centralised theming (`ThemeService`)**
  - Introduced `ThemeService` to manage:
    - Applying light / dark / system themes to a `Scene`.
    - Loading core stylesheets:
      - `explorer-base.css`
      - `explorer-table.css`
      - `explorer-light-win.css`
      - `explorer-dark-win.css`
      - `explorer-win11.css`
    - Inspecting the current theme (`getCurrentTheme`, `isDarkTheme`).
  - Added OS helpers to `ThemeService`:
    - `openWithDesktop(Path)` for opening files and folders via the system shell.
    - `openUriInBrowser(String)` for opening URLs.
    - Clipboard helpers (`copyToClipboard`, `copyStylesheetsToClipboard`).
    - Diagnostics (`dumpStylesheets(Scene)`).

- **Refactored main UI controller (`MainController`)**
  - Consolidated navigation, views, and state management into a single controller:
    - Tree navigation (`TreeView<Path>`).
    - Details table (`TableView<FileItem>`).
    - Large icons / content layout (`ScrollPane` + `FlowPane`).
    - Preview pane (icon + text summary).
    - Status bar (item count, selection size, current path).
  - Introduced a `FileItem` record as the single backing model for both
    Details and Large icons views.
  - Added navigation history (Back / Forward / Up) with `Deque<Path>` stacks.

- **Breadcrumb bar (`BreadcrumbBar.fxml` + `BreadcrumbController`)**
  - New breadcrumb bar rendered as:
    - Scrollable list of path segments with “>” separators.
    - Optional dropdown for overflow / utilities.
  - `BreadcrumbController` exposes callbacks:
    - `setOnNavigate(Consumer<Path>)`
    - `setOnOpenInNewWindow(Consumer<Path>)`
    - `setOnCopyAddress(Consumer<Path>)`
    - `setOnBrowseNetwork(Runnable)`
  - Segments are clickable buttons that navigate to the corresponding folder.
  - Right-click context menu on breadcrumb segments:
    - “Open in new window”
    - “Copy address”
    - “Browse network” (placeholder implementation).

- **Tree / breadcrumb / content synchronisation**
  - Tree selection drives navigation to the selected folder.
  - Navigating via breadcrumb updates:
    - Current folder.
    - Tree selection and expansion.
    - Details / Large icons content.
  - Programmatic tree selection is guarded by a `syncingTreeSelection` flag to
    avoid feedback loops.

- **True Tiles / Content layout (multi-column icon view)**
  - Added a dedicated icon view:
    - `ScrollPane iconScroll` containing `FlowPane iconFlow`.
    - Tiles wrap horizontally and reflow when the window is resized.
  - Each tile contains:
    - A multi-size icon (`ImageView`), typically 64×64 in Large icons mode.
    - A wrapped file/folder name label.
    - A smaller metadata label:
      - Folders: “File folder”.
      - Files: `"<type> • <size>"` (e.g., “PNG image • 238 KB”).
  - Tiles support:
    - Single-click to show preview.
    - Double-click to open file or navigate into a folder.

- **View switching (Details vs Large icons)**
  - Introduced view toggles in the command bar:
    - `detailsToggle` / `largeIconsToggle`.
  - Mirrored view toggles in the status bar:
    - `statusDetailsButton` / `statusLargeIconsButton`.
  - Shared backing data:
    - Both views are fed from the same `ObservableList<FileItem>`.
    - Search filter applies to both views.
  - Switching behaviour:
    - Details:
      - `TableView` visible/managed, icon view hidden/unmanaged.
    - Large icons:
      - `ScrollPane` + `FlowPane` visible/managed, table hidden/unmanaged.
  - A `syncingViewButtons` flag ensures all four toggles stay in sync without
    recursive events.

- **File metadata abstraction (`FileMetadataService`)**
  - Added `FileMetadataService` with:
    - `read(Path)` → `Optional<FileMetadata>`
    - `detectFileType(Path)` for human-friendly type strings (“Image”,
      “Text document”, “PDF document”, etc.).
  - `FileMetadata` encapsulates:
    - Path, directory flag, size, created/modified timestamps, type label.

- **File size formatting (`FileSizeFormatter`)**
  - Introduced `FileSizeFormatter.format(long)`:
    - Windows-like units: B, KB, MB, GB, TB.
    - One decimal precision and thousands grouping (`#,##0.#`).
  - Used for:
    - Size column in Details view.
    - Selection total in the status bar.
    - Metadata text in Large icons tiles.

- **Icon loader (`IconLoader`)**
  - Centralised icon handling with:
    - `loadForPath(Path, boolean darkTheme, int size)`.
    - `load(IconType, boolean darkTheme, int size)`.
    - `inferFrom(String mimeType, String fileName)` to map content to:
      - `FOLDER`, `FILE`, `IMAGE`, `TEXT`, `ARCHIVE`, `AUDIO`, `VIDEO`, `PDF`.
  - Multi-size handling:
    - Accepts any requested size but clamps to a discrete set:
      - {16, 24, 32, 48, 64, 96, 128, 256}.
    - Attempts to load themed PNG resources:
      - `/com/fileexplorer/ui/icons/<light|dark>/<type>-<size>.png`.
  - Placeholder icon drawing:
    - When PNG resources are missing, draws a simple glyph per type using
      `WritableImage` and `PixelWriter`.
    - Uses distinct colours per file type and theme (dark/light).

- **Preview pane**
  - Added preview pane to the right of the content area:
    - Shows a larger icon (96px) for the selected item.
    - Displays:
      - Name.
      - Type.
      - Size (files only).
      - Modified date.
  - Preview updates on:
    - Row selection in Details view.
    - Tile click in Large icons view.

- **Status bar enhancements**
  - Left side:
    - Shows item count (“N items”) or selection summary
      (“N items selected (X KB/MB/…)”).
  - Right side:
    - Shows current folder path.
  - View toggle buttons embedded in the right side to mirror toolbar view
    state (Details / Large).

---

### Changed

- **UI theming to Win 11–style flat design**
  - Eliminated all gradients from UI components:
    - Toolbars, table headers, buttons, text fields, separators, scrollbars,
      and other surfaces.
  - Replaced variable-based theming (string-typed CSS vars) with concrete,
    JavaFX-compatible colours in all theme sheets.
  - Retuned dark theme to approximate Windows 11 Explorer:
    - Slightly lighter panel backgrounds.
    - Clear separation between chrome and content.
    - Flat, high-contrast column headers.

- **CSS structure**
  - `explorer-base.css`:
    - Now provides only structural rules and neutral base styling (font,
      padding, simple radii).
    - No colour variables or gradients.
  - `explorer-table.css`:
    - Encapsulates TableView visual rules:
      - Flat header backgrounds and borders (separate for light/dark).
      - Alternating row colours for both themes.
      - Hover and selection states without gradients.
    - Ensures header text is left-aligned by default.
  - `explorer-light-win.css`:
    - Defines all light theme colours as concrete values:
      - Window, toolbar, tree, table, preview, status backgrounds.
      - Neutral button chrome and hover/pressed states.
      - Focused text-field border colour (accent).
  - `explorer-dark-win.css`:
    - Defines dark theme colours:
      - Window and toolbars around `#1e1e1e`–`#2d2d30`.
      - Table/tree/preview backgrounds around `#252526`–`#2d2d30`.
      - Neutral button chrome with lighter hover/pressed steps.
      - Accent blue for selected toggles and focused text fields.
  - `explorer-win11.css`:
    - Reduced to layout/sizing only:
      - Top-level padding.
      - Toolbar / secondary bar / status bar heights.
      - Preferred header height in the table.
    - All colours removed to avoid conflicts and CSS-type issues.

- **Main layout and FXML wiring**
  - `MainLayout.fxml` now:
    - Uses only valid JavaFX types (`Separator`, `Insets`, etc.).
    - Replaces shorthand padding with proper `<Insets>` declarations.
    - Declares a `StackPane` with:
      - `TableView fileTable` (Details).
      - `ScrollPane iconScroll` (Large icons).
    - Embeds:
      - `fx:include` of `BreadcrumbBar.fxml` and a corresponding
        `BreadcrumbController breadcrumbBarController` field in `MainController`.
    - Routes all event handlers through named methods or direct `onAction`
      assignments in `MainController`.
  - `BreadcrumbBar.fxml`:
    - Cleaned up to:
      - An `HBox` root with a `ScrollPane` (crumbs) and a dropdown button.
      - `fx:controller="com.fileexplorer.ui.BreadcrumbController"`.

- **Navigation logic**
  - Tree navigation, direct path navigation, and breadcrumb navigation all
    converge on a single `navigateTo(Path, boolean pushHistory)` method.
  - History stacks (Back/Forward) now integrate with all navigation entry
    points (tree, breadcrumb, parent/up, double-clicks).

---

### Fixed

- **Compilation errors**
  - Added or corrected methods to resolve compilation failures:
    - `FileMetadataService.detectFileType(Path)` and usage in `MainController`.
    - `FileSizeFormatter.format(long)` and all size display call sites.
    - `IconLoader.loadForPath(Path, boolean, int)` and references from:
      - Table icon column.
      - Large icon tiles.
      - Preview pane.
    - `ThemeService.openWithDesktop(Path)` and integration in `MainController`.
  - Corrected `MainApp.configureExplorerStage(...)` signature usage:
    - Ensured all callers provide `(Stage, Path, ThemeService.Theme)` and
      `MainController` exposes `openInitialFolder(Path, Theme)`.

- **FXML instantiation and field injection issues**
  - Fixed invalid FXML elements:
    - Replaced non-FXML types with `Separator`, proper layout tags, and
      fully qualified `Insets` definitions.
  - Aligned `fx:id` fields and types:
    - Ensured `MainController.breadcrumbBarController` is of type
      `BreadcrumbController` and the FXML includes the correct controller.
    - Corrected mismatches where `HBox` was being injected into
      `BreadcrumbController` fields.
  - Removed/renamed invalid event handler references (e.g., missing
    `onDetailsToolbarButton`) and wired view toggles directly to
    `switchToDetailsView()` / `switchToLargeIconsView()`.

- **CSS ClassCastException warnings**
  - Resolved JavaFX CSS warnings such as:
    - `ClassCastException: String cannot be cast to Paint while converting '-fx-background-color'`
      from several selectors in `explorer-win11.css`.
  - Ensured all `-fx-background-color` and `-fx-border-color` declarations are:
    - Valid JavaFX `Paint` values (`#RRGGBB`, `rgba(...)`).
    - Never string placeholders or undefined variables.

- **Table header behaviour**
  - Removed gradient-like visuals in table headers by switching to flat
    background colours in both themes.
  - Ensured header labels are **left-aligned**, matching the Windows
    Explorer look for Details view.

---

### Removed

- **CSS variable-based theming**
  - Removed custom CSS variables that were previously used for theming and
    were causing type-compatibility issues in JavaFX.
  - Replaced with explicit light/dark properties in dedicated theme sheets.

- **Gradient-based styles**
  - All gradient backgrounds and layered colour definitions have been removed
    from:
    - Toolbars and secondary bars.
    - Table headers and rows.
    - Text fields, buttons, separators, and scrollbars.
  - The UI now uses strictly flat fills for all controls across both themes.

---

## Historical Notes (Pre-1300)

> Earlier FileExplorer versions (11xx / 12xx) introduced the initial JavaFX
> UI, basic navigation, and theming concepts. Those iterations are not
> exhaustively documented here; this changelog focuses on the 1300 series,
> where theming, navigation synchronisation, and the Tiles/Content icon view
> were substantially redesigned.
