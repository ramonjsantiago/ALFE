# HOTFIX211 / Phase 4P.9DM

This hotfix applies a focused follow-up correction on top of the H210 baseline:

- keep the command-bar **View** button label visible next to the active view icon
- preserve the H210 behavior where the icon tracks the current active view mode

## Included changes

### 1) Structured View button content refresh
- Updated `updateViewMenuButtonGraphic()` in `MainController`
- When the View button is using the structured command-bar menu skin, icon refresh now rebuilds the full structured content instead of replacing it with an icon-only graphic
- The visible `View` label therefore stays present next to the active-mode icon

### 2) Active view icon propagation in structured mode
- Updated the structured View button icon builder so it resolves the packaged icon for the current `viewMode`
- This keeps the structured command-bar presentation aligned with the actual active view, including startup/default **Details** mode

## Files changed
- `src/main/java/com/fileexplorer/controller/MainController.java`

## Notes
- No resource or FXML changes were required for this follow-up
- I did not run a full Maven/JavaFX build in this environment
