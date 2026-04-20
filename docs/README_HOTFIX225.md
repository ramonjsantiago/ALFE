# HOTFIX225 / Phase 4P.9EA

This hotfix addresses three regressions observed after the H224 baseline:

- multi-selection collapsing immediately after Ctrl/Shift selection gestures
- directory double-click activation failing in Details view
- Details name-column icon margin parity adjustments

## Changes

- moved Details-row primary selection handling onto an explicit shared selection path so Ctrl, Shift, and single-click selection all route through the same semantic selection application path used by the icon surfaces
- switched icon-surface single-select and toggle-select handlers to use `applyExplorerPathSelection(...)` so presentation refreshes cannot collapse a fresh multi-selection back to a single row
- added a TableView double-click fallback filter so directory activation continues to work when the click lands on nested row content in Details view
- restored non-Details item tooltips to the standard text-tooltip path to avoid recurrence of black-square tooltip rendering on icon/list/tile/content surfaces
- tightened the Details name-column cell padding to `5 px` on both sides for icon-margin parity inside the first column

## Files updated

- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/css/details-view-parity.css`
- `docs/README_HOTFIX225.md`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`
- `PHASE4P_9EA_SHARED_MULTI_SELECTION_OWNERSHIP_PRESERVATION_DIRECTORY_DOUBLE_CLICK_RESTORE_AND_DETAILS_ICON_MARGIN_PARITY_NOTES.txt`
