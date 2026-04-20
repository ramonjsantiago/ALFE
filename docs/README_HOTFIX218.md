# HOTFIX218 / Phase 4P.9DT — Sort Menu Two-Tone Icon Rendering with Blue Accent Preservation

## Summary
This hotfix extends the same two-tone white-icon treatment used by the View menu to the Sort command-bar menu icon.

## Included changes
- Updated the structured Sort command-bar icon path to use the two-tone white render path
- Preserved blue accent pixels from `toolbar.sort.png` while recoloring dark/neutral pixels to white
- Kept the existing Sort menu behavior and menu item actions unchanged

## Files changed
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `docs/README_HOTFIX218.md`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`
- `PHASE4P_9DT_SORT_MENU_TWO_TONE_ICON_RENDERING_WITH_BLUE_ACCENT_PRESERVATION_NOTES.txt`
