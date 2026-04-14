# HOTFIX212 / Phase 4P.9DN

## Startup Details Eager Restore and View Button Label/Icon Parity

This hotfix backs out the H209 lazy Details mount path while keeping the H210/H211 View-button corrections.

### What changed
- Restored eager Details-view module load during controller initialization.
- Restored eager `configureTable()` on the original initialization path.
- Removed the startup placeholder / deferred lazy-details mount path introduced in H209.
- Preserved H210/H211 behavior so the command-bar **View** button still shows the current active view icon with the **View** label next to it.
- Preserved the null guard in `getPrimarySelection()` so early shell-state refreshes cannot crash when the table is unavailable.

### Intended result
- Return startup and scroll behavior to the pre-H209 eager Details baseline.
- Keep the View-button presentation improvements without the lazy-mount performance regression.
