# HOTFIX200 / Phase 4P.9DB

## Details View Icon Refresh Coalescing, Watcher-Burst Deduplication, and Selection-Stable Repaint Parity

This hotfix tightens the Details-view refresh path after HOTFIX199.

### What changed
- Added a `DetailsViewRefreshCoordinator` that coalesces repeated Details-table refresh requests caused by list replacement, sort/comparator churn, and watcher-style burst updates.
- Preserved selection and focus by file path across the coalesced refresh pass so Details repaint remains stable while row visuals are refreshed.
- Updated `TableViewSupport` so identical Details name-cell bindings no longer cancel and restart icon work when the effective row identity has not changed.
- Updated `VisibleThumbnailManager` registration to collapse duplicate same-binding thumbnail registrations instead of incrementing the binding stamp and rearming work unnecessarily.

### Expected result
- Fewer redundant `TableView.refresh()` passes during rapid refresh bursts.
- Less icon churn in Details rows when the same item is rebound repeatedly.
- More stable selection/focus paint while the table is refreshing under watcher or sort pressure.
