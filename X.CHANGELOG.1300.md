# CHANGELOG

All notable changes to this project are documented in this file.

This changelog is reconstructed from the work performed in this chat (and the build/runtime errors shared), spanning roughly **Dec 10–Dec 14, 2025**. Exact commit hashes are not available because Git was not initialized during most of the work.

---

## [Unreleased] - 2025-12-14

### Added
- **Theme font normalization**
  - Standardized UI font family across theme CSS files to **"Segoe UI Variable"** (target OS: Windows 11).
- **UI scaling and defaults**
  - Introduced “initial font size” adjustments across the UI (iterated from 16px → larger sizes per feedback).
  - Added requirement for runtime zoom controls via **Ctrl++ / Ctrl--** (implementation work started / partially integrated, but later regressions occurred).
- **Resize grip concept**
  - Added requirement and partial implementation attempts for a **Windows 11-style bottom-right resize grip** in the status bar:
    - Exact size requested: **24px**
    - Match Win11 dot spacing/opacity
    - Diagonal cursors (NWSE/NESW) associated with the grip.

### Changed
- **Window sizing / constraints**
  - Adjusted app default size multiple times to better suit 4K monitors.
  - Added requirement: window can be shrunk to minimum **256×256**.
  - Adjusted requirement: overall app size should be **~15% smaller** than a prior baseline.
- **TableView behavior**
  - Requirements added/changed:
    - Columns must be **movable**.
    - Column dividers (“splitter between columns”) should have **no color**.
    - Row spacing increased slightly (vertical separation between rows).
    - Column header text clipping addressed (CSS iterations).
  - Date display requirement added: “Date modified” format **`December, 13, 2025`**.

### Fixed
- **TreeView arrow/text alignment**
  - CSS adjustments requested/iterated to align disclosure arrows with row text.
- **Gradients removal**
  - Multiple passes on CSS to remove gradients across controls (especially table headers and general UI elements).

### Known Issues / Regressions
- **FXML injection failures (NPEs)**
  - Repeated runtime failures due to missing `fx:id` bindings (e.g., `colName`, `nameColumn`, `iconColumn` being null in `configureTable()`), indicating FXML/controller drift.
- **Black UI / non-resizable behavior**
  - At points, UI rendered black and/or had resizing constraints not matching expectations.
- **Possible infinite loop / UI thread stall**
  - Thread dump indicated JavaFX Application Thread doing filesystem attribute reads during initial tree selection:
    - `TreeBuildService$LazyDirTreeItem.loadChildren()` / `findBestInitialSelection()`
    - Symptom: apparent hang without a Java exception stack trace.
- **CursorService compilation breakages**
  - Compilation errors like:
    - `class, interface, enum, or record expected`
    - Missing/incorrect package references
    - Missing `IcoCurDecoder`
- **Class/file naming mismatches**
  - Errors such as:
    - `class ResizeGrip is public, should be declared in a file named ResizeGrip.java`
    - `class IcoCurDecoder is public, should be declared in a file named IcoCurDecoder.java`
- **Service/package duplication**
  - Duplicate/incorrect class placement (e.g., `IconService` duplicated across packages).

---

## [Unreleased] - 2025-12-13

### Added
- **Windows-style cursor behavior requirements**
  - Hovering buttons/links uses `HAND`.
  - Text inputs use `TEXT`.
  - Splitters/resizers use directional resize cursors (`EW`, `NS`, plus diagonals when a diagonal grip node exists).
- **Icon mapping inputs**
  - Added external mapping files:
    - `icon_map.properties` (file-extension → icon mapping intent)
    - `cursor-map.properties.txt` (cursor variant mapping intent)
  - Uploaded icon/cursor resource packs:
    - `src - icons.zip`
    - `src - cursors.zip`
  - Stated goal: make app look/behave closer to **Windows File Explorer** (icons + cursors).

### Changed
- **CSS revisions (multiple)**
  - Removed gradients from table headers and other controls.
  - Set table header text left-aligned.
  - Adjusted TreeView text visibility and clipping.
  - Updated font size defaults and global font family.
- **App resizing rules**
  - Addressed inability to resize window smaller using window cursors (separate from min-size constraints).
  - Increased title bar / caption button sizing was discussed (Win11 min/max/close too small), but results were inconsistent across iterations.

### Fixed
- **JavaFX CSS warnings mitigations**
  - Worked on eliminating CSS warnings such as:
    - `ClassCastException: String cannot be cast to Paint` on `-fx-border-color` / `-fx-background-color` for toggle buttons.
    - Lookup resolution warnings for `-fx-text-base-color`.
  - Root cause: invalid CSS value types for paint properties and/or relying on lookups not present in current Modena context.

### Known Issues / Regressions
- **BreadcrumbBar regression**
  - Breadcrumb bar disappeared or visually regressed after UI scaling/theme adjustments.
- **Icons regression**
  - File/folder icons disappeared or mismatched when transitioning toward extension-based mapping.
- **ThemeService API churn**
  - Multiple compilation failures caused by inconsistent ThemeService APIs:
    - `ThemeService.Theme` not found / wrong package
    - `fromPreference()` missing
    - `ThemeService()` constructor private
    - `setTheme()` / `apply(Scene)` missing or mismatched
  - Conflicts between `com.fileexplorer.ui.ThemeService` and `com.fileexplorer.service.ThemeService`.

---

## [Unreleased] - 2025-12-12 to 2025-12-10

### Added / Changed
- **Tree view correctness focus**
  - Priority work: “Tree view is full of garbage. Fix that first.”
  - Added/iterated tree population strategies (lazy loading and/or initial selection logic).

### Fixed
- **Gradients removal (initial pass)**
  - Table view header gradients removed (initial request).
  - Table header text alignment adjustments started.

### Known Issues
- **Compilation errors around TreeCell**
  - Incorrect import: attempted `javafx.scene.control.cell.TreeCell` (should be `javafx.scene.control.TreeCell`).
- **ObservableList vs List**
  - Compilation error: `List<Path> cannot be converted to ObservableList<Path>`.
- **MainApp/MainController API drift**
  - Errors like missing `MainController.setScene(Scene)` and `openInitialFolder(...)`, indicating evolving wiring between app bootstrap and controller.

---

## Repository / Build System Notes (ALFE migration)

### Maven / JavaFX dependency issues
- Encountered dependency resolution failure:
  - `org.openjfx:javafx-*:jar:25.0.0 (absent)` from Maven Central cache.
  - Indicates the chosen JavaFX version may not exist in central (or was temporarily unavailable), and Maven cached the negative lookup.

### module-info.java issues
- Module compilation failures:
  - `module not found: javafx.media`
  - `module not found: javafx.web`
  - `module not found: org.junit.jupiter.api` (because tests shouldn't be required in main module graph)
- Missing Java module read:
  - `java.util.prefs is not visible` → required adding `requires java.prefs;`

### Duplicate MainApp
- Compilation failure:
  - `duplicate class: com.fileexplorer.MainApp`
  - Indicates multiple `MainApp.java` exist under different source paths/packages.

---

## Next Steps (recommended)
1. **Stabilize packages and class locations**
   - Ensure there is exactly one `MainApp` and one `ThemeService`.
   - Remove duplicated `IconService`/cursor classes and normalize packages.
2. **Lock FXML/controller contracts**
   - Ensure `MainLayout.fxml` `fx:id` values match `@FXML` fields exactly.
3. **Fix module-info.java**
   - Add `requires java.prefs;`
   - Remove test modules from `module-info.java`
   - Only include JavaFX modules actually used and present in dependencies.
4. **Initialize Git**
   - Create baseline “last known good” commit to enable clean revert points going forward.
