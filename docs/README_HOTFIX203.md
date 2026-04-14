# HOTFIX203 / Phase 4P.9DE

## Details View Refresh/Sort Commit Barrier, Anchor Preservation, and Header-State Isolation

This hotfix hardens the Details-view refresh/re-sort path after HOTFIX202.

### What changed
- Added a generation-based Details refresh commit barrier so `TableView.refresh()` and selection/focus replay only commit after the active items/sort state has settled for the current directory scope.
- Published directory-scope, anchor-path, and lead-path state from `MainController` into the Details refresh coordinator so same-directory refreshes and rename-triggered re-sorts preserve the intended anchor/focus target instead of replaying stale row order.
- Isolated header popup/preset actions to the clicked header snapshot for that menu instance and reset ephemeral header/dropdown state whenever the Details model/sort surface churns.
- Added a second stable-pulse refresh after commit so fast refresh + re-sort bursts stop leaving transient wrong-row paint behind.

### Expected result
- Same-directory watcher refreshes and rename re-sorts preserve Details selection anchor, lead, and focus more reliably.
- Refresh/resize/sort bursts no longer replay selection against an intermediate row order.
- Header dropdown/preset UI stays scoped to the active header interaction and closes cleanly when the Details surface rebinds.
