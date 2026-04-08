# HOTFIX191 / Phase 4P.9CS

This pass starts from HOTFIX190 and focuses only on main split resize behavior.

## Included
- Pinned the left navigation pane shell width during window resize so the file view absorbs stretch and shrink.
- Added divider-width tracking that updates the pinned width only when the user explicitly drags the main divider.
- Prevented programmatic divider corrections from feeding back into the stored navigation width.
- Preserved the existing hide/show navigation-pane restore path using the last user-established navigation width.

## Notes
- This does not change the minimum navigation pane width floor.
- This package is otherwise the same fully populated project tree as HOTFIX190.
