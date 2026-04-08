# HOTFIX193 / Phase 4P.9CU

Explorer Search Session Lifecycle, Scope Routing, and Result-Surface Parity.

## What changed
- Added an explicit Explorer search session lifecycle with `IDLE`, `TYPING`, `SEARCHING`, `RESULTS`, and `NO_RESULTS` controller states.
- Captured the current folder as the search scope root and cancel the active search session when navigation moves to a different folder or Home.
- Added debounced generation-token search dispatch so stale search completions are discarded when the user keeps typing or navigates away.
- Moved large-folder search match computation off the immediate UI path and allow precomputed huge-folder matches to be applied back into the existing paging model.
- Restored the original folder surface when search is cleared, including best-effort selection/focus recovery.
- Added a dedicated in-surface search state overlay for `Searching…` and `No results found` so empty search results no longer look like an empty folder.
- Updated the bottom status/location surfaces to show search scope-aware text such as `Search results in <folder>` and `No results for "<query>" in <folder>`.

## Primary files
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/css/app-main.css`


## Compile fix
- Restored the missing `isUsingVirtualIconListForCurrentView()` helper in `MainController` so `focusPrimaryFileSurface()` compiles and can route focus to the virtual list surface for List, Tiles, and Content views.
