# HOTFIX224 / Phase 4P.9DZ

## Title
Square Selection Surfaces, Tooltip Path Removal, Non-Details Tooltip Render Repair, and Details Metrics Alignment

## Summary
This hotfix removes `Path:` from Explorer tooltips, restores robust non-Details tooltip text rendering, changes file selection surfaces to square corners, and aligns several Details view metrics including the first-column startup width and row spacing.

## Files Changed
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/css/app-main.css`
- `src/main/resources/com/fileexplorer/ui/css/selection-state-tokens.css`
- `src/main/resources/com/fileexplorer/ui/css/details-view-parity.css`
- `docs/README_HOTFIX224.md`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`
- `PHASE4P_9DZ_SQUARE_SELECTION_SURFACES_TOOLTIP_PATH_REMOVAL_NON_DETAILS_TOOLTIP_RENDER_REPAIR_AND_DETAILS_METRICS_ALIGNMENT_NOTES.txt`

## Notes
- Explorer tooltips no longer show `Path:`
- non-Details icon/list/tile/content tooltips now use an explicit compact text label render path to avoid black-square rendering regressions
- icon/tile selection surfaces now render with square corners
- Details rows now render with square highlight corners, a 2 px row-to-row gap, and a 2 px header-to-first-row gap
- the Details name column now defaults to 375 px when no remembered width overrides it
- the Details name-cell icon lane now adds 1 px internal left padding so the icon sits 5 px from the left selection border
