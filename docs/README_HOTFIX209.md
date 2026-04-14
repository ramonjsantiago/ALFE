# HOTFIX209 / Phase 4P.9DK

## Main-UI First-Render Critical-Path Decomposition and Lazy Details Mount

This hotfix targets the remaining first-render drag seen after H208 by moving the initial Details-view materialization out of the `FXMLLoader.load()` critical path and into a one-pulse-later lazy mount.

### What changed
- Removed the eager Details-view module load from `MainController.initialize(...)` so the main layout can finish loading without synchronously materializing `DetailsView.fxml`.
- Added a lightweight startup placeholder in `viewHost` so the main root can attach before the Details table is ready.
- Added a deferred lazy-details mount lane that loads `DetailsView.fxml`, runs `configureTable()`, and restores the current file-view mode after the main UI is already attached.
- Added new `StartupTrace` markers around `MainController.initialize(...)`, `initializeWithContext(...)`, lazy-details scheduling, and lazy-details configuration so the next startup trace shows where the remaining time is being spent.

### Expected result
- The shell-to-main-root handoff should complete with less work on the first real render path.
- The Details view should mount shortly after the main root is visible instead of blocking `FXMLLoader.load()`.
- Startup traces should make the main-layout load, controller initialization, and deferred Details mount easier to distinguish.
