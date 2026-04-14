# HOTFIX199 / Phase 4P.9DA — Details View Async Icon Token Gating, Rename/Refresh Reconciliation, and Shell-Fallback Upgrade Parity

This phase is based on the HOTFIX198 full-project baseline and tightens the Details `Name` cell binding so late async icon completions cannot repaint a recycled row after rename, refresh, sort, or virtualization churn.

## Included

- Added a stricter Details-cell binding gate keyed to the **exact bound `FileItem` instance**, current `TableRow`, binding stamp, path, display text, folder/file classification, and icon identity.
- Rebound the Details cell immediately whenever the row item changes, so refresh- or rename-driven model replacement invalidates older async icon work before it can publish.
- Prevented late async icon completions from overwriting a thumbnail that already won the placeholder → thumbnail upgrade path.
- Upgraded the initial Details placeholder to use the resolved shell/file-type identity (`ext:*`, `kind:*`, `special:*`, folder) instead of the earlier generic file/folder placeholder, improving fallback parity during refresh churn.
- Extended the no-I/O identity fallback path so root folders and network roots keep the correct placeholder family even before any later refresh or realization work settles.

## Primary updated files

- `src/main/java/com/fileexplorer/ui/table/TableViewSupport.java`
- `docs/README_HOTFIX199.md`
- `PHASE4P_9DA_DETAILS_VIEW_ASYNC_ICON_TOKEN_GATING_RENAME_REFRESH_RECONCILIATION_AND_SHELL_FALLBACK_UPGRADE_PARITY_NOTES.txt`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`

## Notes

This package was prepared as a source-level full-project delivery in the container environment. I did not run a full Maven/JavaFX build here.
