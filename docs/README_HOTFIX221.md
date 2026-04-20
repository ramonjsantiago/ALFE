# HOTFIX221 / Phase 4P.9DW

## Title
Compact Tooltip Metrics, Fast Details Hover, and Context Menu Auto-Dismiss Stabilization

## Summary
This hotfix trims non-Details tooltip geometry back to a compact text-first presentation,
reduces the Details hover metadata delay so the Details surface feels more responsive,
and stabilizes Explorer context-menu showing by deferring popup display until the originating
mouse/context-menu gesture has completed.

## Files Changed
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/css/app-main.css`
- `docs/README_HOTFIX221.md`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`
- `PHASE4P_9DW_COMPACT_TOOLTIP_METRICS_FAST_DETAILS_HOVER_AND_CONTEXT_MENU_AUTO_DISMISS_STABILIZATION_NOTES.txt`

## Notes
- non-Details icon/list/tile/content tooltips now use a compact text tooltip path again with small padding and no oversized rich-content container
- Details metadata popup delay is reduced from 650 ms to 180 ms so hover metadata appears much faster in Details view
- file-ops and background Explorer context menus are shown on the next FX pulse to prevent gesture-time auto-dismiss flicker on right click
