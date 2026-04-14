# HOTFIX204 / Phase 4P.9DF

## Details Header Dropdown Ownership, Right-Edge Menu Parity, and Column-Snapshot Correctness

This hotfix hardens the Details header right-edge dropdown path after HOTFIX203.

### What changed
- Corrected a compile regression in `DetailsViewRefreshCoordinator` where a primitive `int` restore index was mistakenly dereferenced during selection replay.
- Rebound the Details header dropdown builders to immutable invocation snapshots so the clicked header column and visible column list are captured once per menu open instead of being rebuilt from mutable shared state.
- Restored the compact right-edge dropdown to a visible-only column surface so it contains only the currently selected Details columns, in canonical Explorer order, with the checkbox, icon, and string row structure preserved.
- Added attached-header positioning for the compact right-edge menu so it realigns itself to the clicked header after show and stays visually connected to the header edge.
- Updated transient UI dismissal paths so opening file-surface context menus also dismisses the compact header dropdown instead of leaving a stale right-edge menu behind.

### Expected result
- The right-edge Details header dropdown stays attached to the header that opened it and no longer inherits later header clicks or model churn.
- The compact dropdown now reflects only the currently visible Details columns instead of surfacing unrelated typed preset rows.
- Switching to row or background context menus clears any active header dropdown/popup state first.
