# Changelog

All notable changes for the **FileExplorer** prototype are documented here.

## Unreleased

## 0.1.0 - Phase 3.7.0 (Preview pane upgrades)

- Implemented a **preview router** that selects preview behavior by item type (folder / image / text / unknown).
- Folder preview shows a **cheap bounded count** of immediate child items (files vs folders).
- Image preview displays an image thumbnail (background loading) and shows basic metadata including dimensions when available.
- Text preview reads the first 256KB (UTF-8, best-effort), normalizes line endings, and truncates safely.
- Preview loads are **cancellable** via a request token so rapid selection changes do not display stale preview content.

## 0.1.0 - Phase 3.6.3 (Recycle Bin delete pipeline)

- Routed **Delete → Recycle Bin** through `FileOperationsService.trash(...)` so it runs off the FX thread and emits `FileOp*` progress/status events.
- Added new file-op kind `TRASH` (separate from permanent `DELETE`).
- If the platform does not support moving items to trash, the UI prompts to **permanently delete** instead (Shift+Delete remains permanent + confirmed).
## 0.1.0 - Phase 3.6.2 (Undo + operation history)

- Added in-memory `FileOpHistory` and recorded reversible operations.
- Added **Ctrl+Z** to undo the last reversible operation (initial scope: **Rename** and **Move**).
- Undo operations are executed via `FileOperationsService` (background, with existing progress/status events).

Phase 3.6.1 (Paste conflict policy + status bar progress)

- Added **paste conflict handling** when the destination already contains an item with the same name:
  - Prompt provides **Replace**, **Skip**, **Rename** (Windows Explorer-style "Copy (2)" naming), or **Cancel**.
  - Copy/Move operations now accept a `FileOperationsService.ConflictPolicy`.
- Added a **status-bar progress indicator** for background file operations:
  - Shows operation name and `(done/total)` in the left status region.
  - Shows a progress bar on the right; hides automatically after completion.
- Updated `MainLayout.fxml` status bar to include `statusProgress`.

## 0.1.0 - Phase 3.6.0 (File operations pipeline)

- Centralized filesystem mutations (copy, move, delete, rename) into `FileOperationsService`.
- File operations execute on the IO executor and publish lifecycle events (`FileOpStarted/Progress/Succeeded/Failed`).

## 0.1.0 - Phase 3.5.x (Navigation + UI parity)

- Navigation cancellation token wiring.
- Tree refresh + re-probe selection.
- Icon pipeline cache/dedupe + placeholder behavior.
- Address bar & breadcrumb parity.
- Search fast filter.

