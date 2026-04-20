# HOTFIX226 / Phase 4P.9EB

This hotfix corrects the Details-view margin interpretation from H225.

## Changes

- reverted the unintended right-side tightening inside the Details **Name** column cell so the name cell keeps a `5 px` left inset for the icon lane while restoring the standard `12 px` trailing padding
- added an explicit `5 px` right inset on the Details view shell so the gap between the **last visible Details column** and the **Details view container** is exactly `5 px`
- left the H225 multi-selection and directory double-click fixes unchanged

## Files updated

- `src/main/resources/com/fileexplorer/ui/css/details-view-parity.css`
- `docs/README_HOTFIX226.md`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`
- `PHASE4P_9EB_DETAILS_RIGHT_CONTAINER_MARGIN_CORRECTION_AND_NAME_COLUMN_PADDING_REVERT_NOTES.txt`
