# HOTFIX235 / Phase 4P.9EK — Three-Region Workspace Shell and Lazy Inspector Host

## Summary
This phase replaces the primary `SplitPane` shell with a three-region horizontal workspace:

1. navigation tree region
2. main file-view workspace region
3. lazy on-demand inspector host region

The new shell keeps the file view as the dominant center surface, preserves a stable left navigation width, and renders the right-side inspector only when Details, Preview, or Operations is explicitly requested.

## Implemented changes
- replaced the outer `SplitPane` workspace with an `HBox`-based shell in `MainLayout.fxml`
- converted the right-side auxiliary surface into an `inspectorHost` sibling region instead of a nested split item
- added explicit shell resizer regions for the navigation pane and inspector host so the layout no longer depends on `SplitPane` divider behavior
- updated `MainController` to manage inspector visibility through a single inspector mode state
- kept the inspector collapsed to `0 px` until Details, Preview, or Operations is requested
- updated responsive table viewport sizing to measure the new `workspaceShell` / `contentPane` geometry instead of split-pane items
- refreshed workspace chrome styling so the manual resizers visually replace the old split-pane dividers

## Notes
- the file-view surface remains the expanding center region
- the navigation pane restores its last width when shown again
- the inspector host restores its last width when reopened
- the Details and Preview toggles now swap the active inspector card instead of relying on nested split-pane visibility
