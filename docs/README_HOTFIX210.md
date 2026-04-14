# HOTFIX210 / Phase 4P.9DL

This hotfix applies three focused UI/runtime corrections on top of the H209 baseline:

- keep the explorer default/startup view aligned to **Details**
- make the command-bar **View** button graphic mirror the current active view mode
- harden lazy-startup selection queries so early top-chrome state refreshes cannot dereference a null `fileTable`

## Included changes

### 1) View button active-icon synchronization
- Added a `viewModeIconResource(...)` mapper in `MainController`
- Added `updateViewMenuButtonGraphic()` and wired it into startup initialization and view-mode synchronization
- The View button now starts with the packaged **Details** icon and updates to the active mode icon as the user switches views

### 2) Details-default startup presentation
- Updated `MainLayout.fxml` so the initial View button graphic is the packaged Details icon rather than a generic glyph
- This keeps the visible command-bar state aligned with the default Details startup mode even before the menu is first materialized

### 3) Lazy Details mount null guard
- Hardened `getPrimarySelection()` so it safely returns `null` when `fileTable` or its selection model are not yet available
- This prevents the observed JavaFX-thread crash path during early `updateTopChromeState()` / context-menu shell-state sync while the lazy Details surface is still mounting

## Files changed
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/layout/MainLayout.fxml`

## Notes
- No icon asset additions were required; this hotfix reuses the existing packaged view-mode icons
- I did not run a full Maven/JavaFX build in this environment
