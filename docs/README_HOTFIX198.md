# HOTFIX198 / Phase 4P.9CZ — Details View Icon Identity Binding, Cell Reuse Sanitization, and Post-Refresh Paint Parity

This phase is based on the attached HOTFIX197 baseline and hardens Details view icon/thumbnail binding so recycled rows cannot show the wrong icon after refresh, sort, rename, or virtualization churn.

## Included

- Reworked the Details `Name` column cell binding so icon paint is validated against the **current row identity** instead of only the current cell text.
- Added explicit cell sanitization on empty/unbind paths, including pending async icon cancellation, thumbnail unregister, graphic reset, and text reset.
- Added row-item rebinding hooks so when JavaFX virtualized rows are reused for a different `FileItem` with the same display text, the icon is rebound immediately instead of waiting for stale async completions.
- Added stronger post-refresh commit guards that compare path, icon identity, folder/file type key, and current display text before an async icon or thumbnail can paint into the cell.
- Preserved the existing viewport-aware thumbnail pipeline while making late completions no-ops when the row has already been rebound.

## Primary updated files

- `src/main/java/com/fileexplorer/ui/table/TableViewSupport.java`
- `docs/README_HOTFIX198.md`
- `PHASE4P_9CZ_DETAILS_VIEW_ICON_IDENTITY_BINDING_CELL_REUSE_SANITIZATION_AND_POST_REFRESH_PAINT_PARITY_NOTES.txt`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`

## Notes

This package was prepared as a source-level full-project delivery in the container environment. I did not run a full Maven/JavaFX build here.
