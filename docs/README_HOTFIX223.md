# HOTFIX223 / Phase 4P.9DY

## Title
Directory Tooltip Size Suppression, Fast First Hover, and Standard Tooltip Render Restoration

## Summary
This hotfix removes the `Size:` line from directory tooltips, speeds up the first tooltip popup after a view
receives focus, and restores non-Details tooltips to the standard JavaFX text-tooltip path so they stop
rendering as black rectangles.

## Files Changed
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `docs/README_HOTFIX223.md`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`
- `PHASE4P_9DY_DIRECTORY_TOOLTIP_SIZE_SUPPRESSION_FAST_FIRST_HOVER_AND_STANDARD_TOOLTIP_RENDER_RESTORATION_NOTES.txt`

## Notes
- directory tooltips no longer show `Size:`
- non-directory tooltips still show `Type:` and `Size:` when available
- non-Details icon/list/tile/content views now use a standard text tooltip render path again instead of a graphic-only tooltip shell
- non-Details tooltip show delay was reduced for faster first hover response
- Details view metadata popup delay was also reduced for faster initial hover response
