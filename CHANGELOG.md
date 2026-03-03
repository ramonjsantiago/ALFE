## Phase 7.3.1 — Icons Filetype Association (HOTFIX2) (2026-02-21)
- UI: See More menu icon/text spacing; align Operation History and Command Log indentation in View menu.

- View menu: removed Sort submenu under View menu (Sort remains available via toolbar button).

- TreeView: fixed occasional row misalignment by stabilizing disclosure chevron spacing when virtualized cells are reused/swapped.
- TreeView: reduced left inset by 1px (additional) to match Explorer alignment.

### Fixed
- Fixed a Java compilation error in `IconLoader` caused by duplicate switch labels (removed `yaml`/`yml` from the CODE mapping because they are already categorized as TEXT).
- Details pane is now hosted in a SplitPane and defaults to 0px width; the toolbar Details toggle shows/hides it.
- Increased icon-to-text spacing in the toolbar "See more" menu items to match other menus.
- View menu selection now swaps the main content view (Extra Large/Large/Medium/Small/List/Details/Tiles/Content) without opening the Command Dialog and without affecting the right-hand pane toggle.
- "Operation History..." and "Command Log..." items are indented/aligned to match other View menu rows.
- TableView: right-clicking the header row now shows an Explorer-like menu with "Size Column to Fit", "Size all Columns to Fit", checkable column visibility items, and "More..." (opens a placeholder Choose Details dialog).
- TableView: Size column displays minimum 1 KB for non-zero files (0 bytes shows 0 KB).

---

# Changelog


## Phase 7.0.0 — Conflict Engine Enhancements (FULL)

### Added
- CUSTOM conflict policy now supports **ordered rules** (first match wins) using `glob:` or `regex:` patterns.
- Conflict Policy Editor UI includes a **CUSTOM rules** text area (one rule per line: `id|pattern|ACTION`).
- Conflict policy JSON import/export upgraded to **version 2** with `customRules` array.

### Changed
- Conflict policy engine can return a detailed decision (`Decision`) including the matched rule id.

## Phase 7.1.0 — Template Packs (Import/Export + Sharing) (FULL)

### Added
- Template Packs export/import (zip format) in the Templates window.
- Pack contents:
  - `templates/<id>.template` (one file per template)
  - `templates/recurring-schedules.properties` (optional)
  - `manifest.json` (pack metadata)

### Notes
- Import supports overwrite or skip when a template id already exists.
- Implemented as file-level portability to remain compatible with legacy template persistence formats.

## Phase 7.2.0 — Operations UX Upgrade (UI)

- Added Pause/Resume control for the dispatcher (prevents starting new operations while allowing active work to continue).
- Added Cancel Selected button (works for both Active and Queue lists).
- Minor UI wiring improvements in Progress pane.


## Phase 6.6.0 — CI Wiring + Release Packaging (FULL)

**Added**
- GitHub Actions CI workflow (`.github/workflows/ci.yml`) building and testing on Windows + Linux with Java 25.
- GitHub Actions release workflow (`.github/workflows/release.yml`) producing a distributable ZIP and attaching it to tagged releases.
- Release packaging scripts: `scripts/package-release.sh` and `scripts/package-release.ps1` producing `dist/FileExplorer-<version>-<sha>.zip`.

**Notes**
- CI runs `mvn test` and uploads Surefire reports and built JARs as workflow artifacts.


## Phase 6.4.1 — Crash Report Viewer + Auto-Attach to Support Bundle

- Added CrashReportViewerDialog utility.
- Support bundles already include crash snapshot when present.


## Phase 6.2.0 — Template Builder UX + Validation (FULL)

### Added
- Template Manager now supports **Edit…** and **Validate** actions.
- Validation blocks running/scheduling templates that are obviously incomplete (missing sources/target, empty name, duplicates).

### Changed
- Create dialog upgraded to a richer template builder (multi-line sources, drift/rollback, batch toggle).

### Fixed
- Prevents saving templates with empty required fields (best-effort, UI-level validation).

## Phase 6.1.0 — Conflict Resolution UX Upgrade (FULL)

- **Repost/regeneration note:** This package re-uploads the Phase 6.1.0 artifact after link expiry.
- Conflict resolution dialog improvements and batch actions (Skip / Overwrite / Rename) are available via the existing conflict session UI.



## Phase 6.5.0 — Automated Regression Checks (FULL)

**Added**
- Headless regression test suite (JUnit) covering operation queue and recurring schedule persistence.
- `com.fileexplorer.tools.RegressionCheckMain` for CI/smoke execution without JavaFX.
- Scripts: `scripts/run-regression-check.sh` and `scripts/run-regression-check.ps1`.

**Changed**
- Master roadmap updated to include Phase 6.5.x regression hardening track.

## Master roadmap (planned)

- [ ] **Phase 7.3.0 — Windows Explorer UX Parity Pass (Navigation + Shell Behaviors) (FULL)**

- [x] **Phase 6.6.0 — CI Wiring + Release Packaging (FULL)**
- [x] **Phase 5.3.1 — Scheduler UI Polish + Validation (FULL)**
- [x] **Phase 5.3.2 — Scheduler UI Persistence + Restore (FULL)**
- [x] **Phase 5.3.3 — Scheduler UI Performance (FULL)**
- [x] **Phase 5.4.0 — Due Execution Correctness + Edge Cases (FULL)**
- [x] **Phase 5.4.1 — Scheduler Concurrency + Locking (FULL)**
- [x] **Phase 5.4.2 — Scheduler Failure Policy (FULL)**
- [x] **Phase 5.4.3 — Conflict Policy Integration (FULL)**
- [x] **Phase 5.5.0 — Scheduler Reporting + Export (FULL)**
- [x] **Phase 5.5.1 — Audit Trail Enrichment for Scheduled Runs (FULL)**
- [x] **Phase 5.5.2 — History Queries + Filters (FULL)**
- [x] **Phase 5.6.0 — Settings Panel for Scheduler (FULL)**
- [x] **Phase 5.6.1 — Maintenance Tools (FULL)**
- [x] **Phase 5.6.2 — Final Polish + Regression Pass (FULL)**
- [x] **Phase 6.0.0 — Operations Queue Reliability + Persistence (FULL)**

- [x] **Phase 6.1.0 — Conflict Resolution UX Upgrade (FULL)**
- [x] **Phase 6.2.0 — Template Builder UX + Validation (FULL)**
- [ ] **Phase 6.3.0 — Diagnostics Bundle + Support Mode (FULL)**
- [ ] **Phase 6.4.0 — Self-Check, Crash Snapshot, and Safe Mode Launch (FULL)**

---

## Phase 7.3.0 — Windows Explorer UX Parity Pass (Navigation + Shell Behaviors) (2026-02-21)

### Changed
- Table row right-click behavior is now Explorer-like: right-clicking an unselected row selects it, while right-clicking an already-selected row preserves the current multi-selection.
- Win11 density + hover/selection visuals updated (table rows, tree rows, breadcrumb surface).

### Fixed
- Dark theme parity for context menus (surface, border, focused item colors) when using the Win11 dark theme.


## Phase 7.3.1 — Icons + File Type Association Pass (FULL)

- Expanded file-type icon mapping (Office docs, code, executables, links) using extension-based identities.
- Added new logical icon categories with distinct placeholders when dedicated resources are unavailable.
- Improved extension coverage for a more Windows Explorer-like Details view.


## Phase 6.0.0 — Operations Queue Reliability + Persistence (2026-02-21)

### Added
- Persisted operation-queue snapshot now includes a stable operation id and last-known status (best-effort).
- Recovery-enqueued operations are stamped with an origin audit block (`originType=RECOVERY`, `triggerType=RESTORE`) for traceability.

### Changed
- Queue restore now re-enqueues persisted items using their persisted ids when available (prevents accidental duplication during recovery).
- Persisted RUNNING items are normalized back to QUEUED on restore and require explicit user confirmation to resume.

### Fixed
- Improved crash/restart robustness by making queue persistence format forward/backward compatible (legacy lines still load).

## Phase 5.3.1 — Scheduler UI Polish + Validation (2026-02-20)

### Added
- Filter box for schedules and activity feed (Ctrl+F).
- Keyboard shortcuts in Scheduler dashboard: F5 refresh, Enter run now, Delete remove schedule, Ctrl+E edit.
- Quick-select recurrence buttons (15/60/1440) in recurrence dialog.

### Changed
- Recurrence dialog now validates input and disables Save when the value is invalid.
- Enable/Disable button now reflects current state and prompts before disabling.
- Next-run column now includes a relative hint (e.g., "in 2h 15m").

### Fixed
- Improved empty-state placeholders for schedules and history views.
- Fixed compilation on JavaFX by replacing non-existent `SpinnerValueFactory.LongSpinnerValueFactory` with `SpinnerValueFactory.IntegerSpinnerValueFactory` (bounds fit in `int`).

---

## Phase 5.3.2 — Scheduler UI Persistence + Restore (2026-02-20)

### Added
- Preferences-backed persistence for Scheduler dashboard UI state.

### Changed
- Scheduler dashboard now restores: window size/position, split-pane divider, filter text, column widths, sort order, and last selected schedule.

### Fixed
- N/A

---

## Phase 5.4.0 — Due Execution Correctness + Edge Cases (2026-02-20)

### Added
- Persisted recurring schedule state now includes **lastRun** and **nextDue** epoch-millis for deterministic scheduling.
- Scheduler exposes next-due timestamps to the UI for accurate "Next run" display.

### Changed
- Recurring execution now uses a periodic **due-evaluation tick** instead of `scheduleAtFixedRate` for DST-safe, deterministic behavior.
- After downtime (or long pauses), missed intervals trigger **at most one immediate run** and the next-due timestamp advances to the first interval strictly after "now" (prevents catch-up storms).
- Manual **Run now** bumps the next-due time forward to avoid an immediate double-run.

### Fixed
- Handles significant system clock rewinds by recomputing next-due timestamps to prevent schedules getting stuck.

---

## Phase 5.4.1 — Scheduler Concurrency + Locking (2026-02-20)

### Added
- Dedicated execution executor so scheduler tick and UI-driven refreshes are never blocked by template execution.
- Single-flight locking per templateId with in-flight tracking (prevents double-run when due and manual run coincide).
- Best-effort cancellation of in-flight executions during shutdown (safe close).

### Changed
- Scheduler tick now advances next-due timestamps first, then submits work asynchronously.
- Run now and one-shot scheduling now share the same single-flight submission path.

### Fixed
- Prevents duplicate enqueues for the same template when multiple triggers occur close together ("Already running" skip logged).

---


## Phase 5.4.2 — Scheduler Failure Policy (2026-02-20)

### Added
- Failure policy for recurring scheduled runs with bounded retries and exponential backoff.
- Persisted failure-policy state in recurring schedule storage (backward compatible):
  `minutes|lastRunEpochMillis|nextDueEpochMillis|retryCount|backoffUntilEpochMillis|lastFailureCategory`.

### Changed
- Scheduler now records retry state per template (retry count, backoff-until, last failure category) and advances next execution to the backoff deadline when retrying.

### Fixed
- Prevents rapid re-trigger storms when a template repeatedly fails (bounded retries; then returns to normal cadence).



## Phase 5.4.3 — Conflict Policy Integration (2026-02-20)

### Added
- Template executions now apply the template’s **conflictProfileId** (when set) as a per-operation conflict policy override.
- Scheduler attaches a **dry-run preview snapshot** to scheduled operations (best-effort) so conflicts/rename decisions are deterministic.

### Changed
- Scheduled template runs now log **preview conflict and warning counts** into Template Run History before enqueueing.

### Fixed
- N/A
## Phase 5.3.3 — Scheduler UI Performance (2026-02-20)

### Added
- Debounced filtering in Scheduler dashboard to reduce UI churn while typing.
- Coalesced refresh requests (button, edits, shortcuts) to avoid repeated full reloads.

### Changed
- Filtering now uses cached lowercase fields for each schedule row to reduce allocations.

### Fixed
- N/A

---

## Phase 5.5.0 — Scheduler Reporting + Export (2026-02-20)

### Added
- Scheduler dashboard reporting actions: **Export Schedules**, **Export History**, and **Copy Diagnostics**.
- CSV export for recurring schedules (includes minutes, enabled state, last/next due timestamps, retry/backoff fields).
- JSON export for recurring schedules.
- CSV/JSON export for scheduler run history (all history, or selected template only).
- Diagnostics bundle to clipboard (environment + schedule snapshot + recent history snapshot).

### Changed
- `TemplateRunHistoryService` now supports full-history reads (best-effort) for reporting/export.

### Fixed
- N/A

---

## Phase 5.5.1 — Audit Trail Enrichment for Scheduled Runs (2026-02-21)

### Added
- Operation history entries now persist **origin/audit metadata** for scheduled template runs:
  - `originType` (e.g., TEMPLATE_SCHEDULER)
  - `originTriggerType` (DUE / ONESHOT / MANUAL)
  - `originTemplateId` / `originScheduleId`
  - `originRecurrenceMinutes`
  - `originRetryAttempt`
- Scheduler now stamps queued operations with origin metadata so the Operation History view can display it.

### Changed
- Operation history JSON persistence now includes the above origin fields and also includes `commandId` in the serialized record.

### Fixed
- N/A

---

## Phase 5.3.0 — Scheduler UI, Controls, and Observability (2026-02-20)

### Added
- Scheduler dashboard window to view recurring schedules and recent scheduler activity.
- Scheduler controls: **Run now**, **Enable/Disable**, **Edit recurrence**, **Delete schedule**.
- Global “recent activity” aggregation across templates via `TemplateRunHistoryService#listRecentAll(...)`.
- A dedicated **Scheduler…** entry point in the Operations/Progress pane.

### Changed
- Scheduler runs now surface more actionable details (timestamps, status, and identifiers where available).

### Fixed
- N/A

---

## Phase 5.2.1 — Scheduler Recurring History (2026-02-20)

### Added
- Recurring template schedules (“every N minutes”) persisted across restarts.
- Append-only scheduler run history log.
- History viewer inside the Templates dialog.

### Changed
- Template scheduler records enqueued operation IDs when available.

### Fixed
- N/A

---

# Phase 4.4.0 EXECUTE FROM SNAPSHOT
## Phase 4.2.0 - Conflict Policy Profiles
- Added Preferences-backed conflict policy profiles (DEFAULT, CONSERVATIVE, AGGRESSIVE, MIRROR, CUSTOM).
- Added ConflictPolicyEngine to auto-resolve conflicts before escalating to the Conflict Queue UI.
- Extended history/audit conflict summary with policy auto/escalation counts.

CHANGELOG.md

## 2026-02-15 (Phase 3.9.0)

### Added
- Transactional WAL batching for Operation History persistence:
  - WAL writes are wrapped in BEGIN_TXN/END_TXN markers.
  - Recovery only applies fully closed transactions (prevents partial/crash mid-write pollution).
- OperationHistoryService.addBatch(...) for future multi-operation transaction commits.

## 2026-02-15 (Phase 3.8.3)

### Added
- Operation History: Persistence Settings dialog (WAL checkpoint bytes, archive retention, startup checkpoint toggle).
- Dialog indicates when values are overridden by -D system properties and disables those fields accordingly.

## 2026-02-11 (Phase 3.6.0)

### Added
- File operations (copy/cut/paste/delete/rename) with background execution and best-effort cancellation.
- EventBus events for file operations: started/progress/completed/cancelled/failed.
- Table context menu + keyboard shortcuts:
  - Ctrl+C / Ctrl+X / Ctrl+V
  - Delete
  - F2 rename

### Notes
- Delete is currently permanent (Recycle Bin integration is planned for a follow-on phase).

## 2026-02-11 (Phase 3.5.3)

### Added
- Breadcrumb/address-bar parity improvements:
  - Ctrl+L now focuses the address bar (in addition to F4 and Alt+D).
  - Clicking empty space in the breadcrumb bar enters address mode (Explorer-like).

## 2026-01-26 (Fix22)

### Fixed
- Fixed `MainController` compilation error by initializing the `displayService` field (used by TreeCells) to the controller's `TreeBuildService` instance.

## 2026-01-26 (Fix21)

### Fixed
- Fixed compilation errors introduced during Fix20 iteration:
  - Added missing `java.util.logging.Level` import.
  - Added `syspropBoolean(String, boolean)` helper for system-property wiring.
  - Tree cell renderers now use an injected `TreeBuildService` instance for display names (no static method calls).

## 2026-01-26 (Fix20)

### Fixed
- Restored TreeView root display: the Computer root now renders even when its backing `TreeItem` value is `null`.
- Added Fluent disclosure chevrons for the TreeView:
  - Collapsed: ChevronRight (U+E974)
  - Expanded: ChevronDown (U+E972)
- Updated the "See more" menu button to use the Fluent glyph (U+E712).

### Changed
- The Computer root now expands on startup to show drive roots; child folders remain lazy and only populate on expansion (activation).

## 2026-01-26 (Fix19)

### Fixed
- Restored compilation for Fix18 by adding an overload of `enforceVirtualizedPrefSize(...)` that accepts per-control system-property keys for pref height/width.
- Added a defensive system-property double parser for the pref-size guard wiring (invalid values fall back to defaults).
- Reintroduced the `RESOURCE_AUDIT` flag in `MainController` (driven by `-Dfileexplorer.resourceAudit`).

## 2026-01-26 (Fix18)

### Fixed
- Strengthened startup virtualization guards by clamping both pref *and* max dimensions during the first CSS/layout pass. This is intended to prevent VirtualFlow runaway cell allocation during Stage.show().
- Automatically release the temporary max-size clamps after the first successful Stage.show()/pulse so SplitPane resizing behaves normally (can be held with `-Dfileexplorer.ui.guard.keepMaxClamps=true`).

### Changed
- Safe mode still disables initial directory load by default, but now supports an explicit override via `-Dfileexplorer.safeMode.allowInitialDirectoryLoad=true`.

## 2026-01-25 (Fix17)

### Fixed
- Restored compilation after the "prefHeight guards always" iteration by:
  - Replacing the removed `loadDirectory(Path)` call with `loadDirectoryIntoTableAsync(Path)`.
  - Updating TreeView activation handlers to use `navigateToFolder(Path, boolean)`.
  - Adding conservative TreeView drag/drop handlers (`onTreeDragOver`, `onTreeDragDropped`) to satisfy wiring.
  - Removing the stale `IconLoader.IconSize` usage and switching TreeCell icon assignment to `IconLoader.load(IconType, dark, size)`.
- Updated this changelog (requested for every produced zip).

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


## Phase 6.2.0 — Template Builder UX + Validation (FULL)

### Added
- Template Manager now supports **Edit…** and **Validate** actions.
- Validation blocks running/scheduling templates that are obviously incomplete (missing sources/target, empty name, duplicates).

### Changed
- Create dialog upgraded to a richer template builder (multi-line sources, drift/rollback, batch toggle).

### Fixed
- Prevents saving templates with empty required fields (best-effort, UI-level validation).

## Fix20 - Restore Tree root display, Fluent chevrons, and More-menu glyph (2026-01-26)

- Restored proper TreeView root labeling: the Computer root now renders (even when its backing `TreeItem` value is `null`).
- Added Fluent-style disclosure chevrons for tree expansion state:
  - Collapsed: ChevronRight (U+E974)
  - Expanded: ChevronDown (U+E972)
- Updated the command bar “See more” menu button to use the Fluent glyph U+E712.
- Kept “activation-only” navigation semantics: Safe Mode still does not navigate on selection/double-click, but tree expansion remains lazy and safe.

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

# Changelog


## Phase 6.2.0 — Template Builder UX + Validation (FULL)

### Added
- Template Manager now supports **Edit…** and **Validate** actions.
- Validation blocks running/scheduling templates that are obviously incomplete (missing sources/target, empty name, duplicates).

### Changed
- Create dialog upgraded to a richer template builder (multi-line sources, drift/rollback, batch toggle).

### Fixed
- Prevents saving templates with empty required fields (best-effort, UI-level validation).

All notable changes to this project will be documented in this file.

## [Unreleased] — 2025-12-29

### Added
- **HiDPI-aware startup UI scaling**: initial UI font size is computed from the primary screen DPI and applied to the scene root before the first layout pass.
- **TreeView label fallback**: when the platform display name resolver returns blank, Tree nodes fall back to `Path.getFileName()` (or `Path.toString()` as a last resort) to ensure node labels always render.

### Changed
- **Minimum UI font size increased to 24px** and default zoom baseline raised to improve readability on high-resolution displays.
- **Ctrl++ / Ctrl-- zoom behavior**: zoom updates are applied by updating `-fx-font-size` on the scene root so the UI scales consistently across controls.
- **Startup window sizing**: default window bounds are now constrained to a sensible workstation size (instead of near full-screen on 4K) while still respecting minimum dimensions.

### Fixed
- **Table “Size” column empty**: size now renders using a local `Files.size(path)`-based formatter with Explorer-like behavior (folders show blank).
- **Breadcrumb clipping risk reduced**: breadcrumb layout/CSS rules were tightened to avoid fixed-height constraints and to allow toolbar/breadcrumb rows to compute their height from content.
- **New window creation compilation error**: removed invalid `configureExplorerStage(Stage)` call and routed all new-window creation through the existing `configureExplorerStage(Stage, Path, boolean)` overload.

### Notes
- The project previously exhibited a “large shell + small content” symptom on 4K due to competing startup sizing behaviors. The current approach establishes a **single source of truth** for initial UI scaling (MainApp computes startup font; controller adopts it for subsequent zoom operations).

# Changelog


## Phase 6.2.0 — Template Builder UX + Validation (FULL)

### Added
- Template Manager now supports **Edit…** and **Validate** actions.
- Validation blocks running/scheduling templates that are obviously incomplete (missing sources/target, empty name, duplicates).

### Changed
- Create dialog upgraded to a richer template builder (multi-line sources, drift/rollback, batch toggle).

### Fixed
- Prevents saving templates with empty required fields (best-effort, UI-level validation).

All notable changes to the FileExplorer UI/zoom work are documented here.

## Unreleased

- Fix Table header context menu: render Explorer-style checkmarks reliably (CustomMenuItem check column), and enforce mutual exclusion with file-ops context menu (never show both at once).

### Fixed
- **Invalid CSS**: removed stray non-CSS text (`...`) from `zoom_overrides.css` that was preventing later rules from loading (leading to clipped toolbars and inconsistent scaling).
- **TreeView labels disappearing**: hardened the `TreeCell` rendering so a blank/empty display name falls back to `Path#getFileName()` (or `Path#toString()` as last resort), and ensured text color is set explicitly for light/dark themes.
- **HiDPI first-frame sizing**: `MainController` now adopts the startup UI font value provided by `MainApp` via the root property `main.uiFontPx`, so the initial render matches the intended scale.

### Improved
- **Tree readability**: the navigation tree now scales larger than the rest of the UI (`~1.55x`) and uses a fixed cell size derived from the font size to avoid clipped glyphs.
- **View modes**: implemented real `Tiles` and `Content` layouts using the existing icon panel:
  - `Tiles`: vertical rows with icon + name + type/size summary.
  - `Content`: vertical rows with icon + name + type/size summary + modified time.
- **Search in folder (Phase 3.5.4)**: added fast, debounced filtering of the current directory listing via the Search box; `Ctrl+F` focuses search; `Esc` clears.

### Notes
- These changes avoid window/scene scale transforms and instead rely on font-driven sizing plus control metric overrides, which is substantially more stable across platforms and DPI settings.

## 2026-02-12 (Phase 3.6.1)

### Changed
- Delete now moves items to the Windows Recycle Bin when supported (via `java.awt.Desktop.moveToTrash`).
- Shift+Delete performs permanent delete.
- Context menu now offers both **Delete** and **Delete permanently**.

### Notes
- If the OS trash operation is unsupported or fails, the app falls back to permanent delete.

## 2026-02-14 (Phase 3.6.10)

### Added
- Orphan temp file detection for atomic-copy temp artifacts (`.__fe_tmp__*`).
- Operations pane banner showing orphan temp count with actions: **Clean up** and **Ignore**.
- Preference-backed orphan temp policy: **Ask**, **Auto-clean**, **Never**.

### Changed
- App startup now performs a best-effort scan of target directories referenced by queued/persisted operations to surface orphan temp files.

\n## Phase 4.0.0 - Command Framework (Scaffold)\n- Introduced Command, CommandContext, and CommandManager scaffolding.\n- Added file operation command wrappers (Copy/Move/Delete) that delegate to OperationQueueService.\n- Wired CommandManager into ExplorerContext for future UI migration.\n

## Phase 4.3.0 - Operation Preview Engine
- Added dry-run preview engine with conflict outcome estimation and risk warnings.
---

## Phase 5.6.0 — Settings Panel for Scheduler (FULL)

### Added
- Scheduler “Settings…” dialog for global scheduler behavior (tick cadence, max concurrent scheduled runs, retry policy, and history retention).
- Persisted settings via `Preferences` (applies immediately without restart).

### Changed
- Scheduler service now reads settings at startup and can apply changes at runtime (tick restart + executor reconfigure).

## Phase 5.5.2 — History Queries + Filters (2026-02-21)

### Added
- History-specific filters in the Scheduler dashboard: status dropdown and date-range (From/To) pickers.
- Persistent filter state across restarts for history filters (status/from/to).

### Changed
- Recent scheduler activity is now backed by `TemplateRunHistoryEntry` objects (cell-rendered via `TemplateRunHistoryService.formatForUi`) to support richer filtering.

### Fixed
- Reduced history list churn by filtering entries directly rather than filtering formatted strings.

---

## Phase 5.6.1 — Maintenance Tools (2026-02-21)

### Added
- Scheduler dashboard **Maintenance…** action with:
  - Validate/repair recurring schedule store
  - Recompute next-run timestamps for all enabled schedules
  - Trim history log immediately (best-effort)

### Changed
- Scheduler services now expose maintenance entry points for validation/repair and recompute.

### Fixed
- History retention trimming can now be triggered on-demand (in addition to periodic trimming).

---

## Phase 5.6.2 — Final Polish + Regression Pass (2026-02-21)

### Added
- Scheduler dashboard now shows a compact **Status** line summarizing effective settings (tick cadence, max concurrency, retry policy, retention).
- Tooltips added for Scheduler **Settings…** and **Maintenance…** actions.

### Changed
- Normalized Scheduler table date/time formatting (Last run / Next run) for consistency.
- Maintenance operations are now strictly best-effort: empty stores, missing files, or parse errors no longer surface as exceptions to the UI.

### Fixed
- Guarded schedule parsing and history rendering against missing/deleted templates.
- Null-safety improvements in scheduler UI refresh paths.

---

## Phase 6.4.0 — Self-Check, Crash Snapshot, and Safe Mode Launch (2026-02-21)

### Added
- Startup self-check that validates key persistence files are readable and quarantines corrupted files with a `.corrupt-<timestamp>` suffix.
- Crash snapshot writer for uncaught exceptions, stored under `~/.fileexplorer/crash/last-crash.txt`.
- Next-launch prompt when a prior crash snapshot is present, offering one-click support bundle generation (including the crash report).

### Changed
- Safe mode now disables operation-queue auto-recovery re-enqueue and scheduler due-execution auto-run (tick).

- View menu: aligned Show submenu header and aligned Operation History / Command Log rows with standard checkbox+icon columns.
- View menu: corrected Sort submenu header indentation to align with other View menu items.- Tree view: reduced TreeCell left padding by 1px to match Explorer-style alignment.

- Tree view: increased spacing between disclosure chevron and folder icon; ensured icons are vertically aligned via fixed-size graphic container.

- Table header: right-click header now suppresses row/file context menu so only the header columns menu is shown.

- Thumbnails: image files (supported extensions) now lazily render scaled thumbnails in all views (Details/Table and icon modes). Uses JavaFX decoding for common formats and ImageIO (TwelveMonkeys) as a fallback; continues showing placeholder icon if thumbnail cannot be loaded.

- Thumbnails: decoding is now deferred until after the first full UI render to avoid startup slowdown; thumbnail cache invalidates when a file's last-modified timestamp changes.
- Persisted Table Details column visibility/order/widths (Preferences) and restored on startup.
- Debounced thumbnail decode requests (75ms per (path,size)) to reduce background churn during rapid scrolling.
- Thumbnails: replaced unbounded cache with size-bucketed LRU limits to cap memory growth.
- Thumbnails: added optional periodic metrics logging (enable with -Dfileexplorer.debug.thumbs=true).

- Table header: "More..." now opens a real "Choose Details" dialog (column visibility + reordering) with Reset/OK/Cancel; Name is locked on.

- Phase 3: Details/Preview panes now render selection metadata and image previews (when enabled), updated live on selection and pane toggles.
