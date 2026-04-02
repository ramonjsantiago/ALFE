# Changelog

## HOTFIX171 / Phase 4P.9BY — Post-Show Directory Hydration, Deferred Icon/Thumbnail Warmup, and First-Interaction Readiness Parity
- Routed initial folder opening through a post-show hydration handoff so the shell can paint before directory enumeration begins.
- Added startup instrumentation for post-show hydration scheduling, first-batch directory commit, icon-gate open, thumbnail-gate open, and first-interaction readiness.
- Split progressive directory enumeration into a smaller startup-first batch and larger follow-on batches to reduce first-click/first-scroll contention.
- Added async icon gating so placeholder icons stay cheap until the first visible directory batch is committed.
- Deferred current-folder thumbnail warmup until after first-interaction readiness, keeping preview/thumbnail work behind a later startup gate.

## HOTFIX170 / Phase 4P.9BX — Cold-Start Bootstrap Decomposition, FXML Load Deferral, and CSS Selection Paint Type-Safety Parity
- Split startup stylesheet attachment into a smaller shell bundle and a deferred main-UI critical bundle to reduce pre-show cold-start work.
- Added shared startup-work-queue scene wiring so `MainController.setScene(...)` can push theme work onto the critical queue and font/motion work onto the idle queue.
- Added finer startup instrumentation for ExplorerContext creation, overlay-root creation, controller resolution, and main-UI stylesheet attachment.
- Removed the extra `Platform.runLater(...)` hop from `openInitialFolder(...)` when already on the FX thread so initial directory navigation can begin earlier.
- Replaced `*.list-cell:selected` selection paint usage in `explorer-base.css` with explicit focused/non-focused paint values to avoid JavaFX background-paint cast warnings.

## HOTFIX169 / Phase 4P.9BW — Undo/Redo Create-Rename Shell-State Parity and Post-Action Reveal Stability
- Added synchronous `CreateDirectoryCommand` and `RenamePathCommand` so create-folder and inline-rename actions participate in the undo/redo stack.
- Extended command-stack persistence to restore create-directory and rename-path commands across sessions.
- Added command-stack discard support so a cancelled transient create-and-rename flow does not leave a stale undo entry behind.
- Routed create-folder and inline-rename execution through `CommandManager` for Explorer-style Ctrl+Z / Ctrl+Y coverage.
- Added post-undo/post-redo shell-state restoration in `MainController` so viewport, focus, selection, and reveal state remain stable for create/rename commands.
- Added fallback selection-on-missing-path handling so undoing a create keeps the viewport stable and reselects the nearest surviving row when possible.
