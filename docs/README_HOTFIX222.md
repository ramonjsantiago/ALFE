# HOTFIX222 / Phase 4P.9DX

## Title
Tooltip Type Line for Files and Non-Details Tooltip Text Render Fix

## Summary
This hotfix adds a conditional `Type:` line to Explorer tooltips for files when a file type is available,
omits that line for directories, and hardens non-Details tooltips so their text renders through an explicit
compact label graphic instead of collapsing to black rectangles.

## Files Changed
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/css/app-main.css`
- `docs/README_HOTFIX222.md`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`
- `PHASE4P_9DX_TOOLTIP_TYPE_LINE_FOR_FILES_AND_NON_DETAILS_TOOLTIP_TEXT_RENDER_FIX_NOTES.txt`

## Notes
- file tooltips now show `Type: ...` only for non-directory items when a real type string is available
- directory tooltips no longer render a `Type:` line
- non-Details tooltips now bind their visible content through an explicit compact label graphic with white text
- tooltip content/root styling is kept compact with transparent inner containers so other views no longer show black rectangles
