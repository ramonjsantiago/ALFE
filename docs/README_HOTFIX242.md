# HOTFIX242 / Phase 4P.9ER — File View Item Context Menu Release Stabilization

Baseline: HOTFIX241 / Phase 4P.9EQ — Virtual File View Item Context Menu Restore

## Scope
Fix the regression where file-view item context menus in Details and icon-family views appear briefly and then immediately dismiss.

## Implemented
- split file-item right-click handling into an arm-on-secondary-press step and a show-on-`ContextMenuRequested` step so popup display happens after the opening gesture settles
- retained a short-lived armed item path plus screen-position match so virtualized icon grid/list surfaces can still recover the clicked item when the later context-menu event loses the direct tile node
- cleared stale armed item requests when falling back to the file-view background menu so empty-space right clicks do not reopen an older file item target

## Key files
- `src/main/java/com/fileexplorer/controller/MainController.java`

## Notes
This hotfix preserves the HOTFIX241 virtual-surface target recovery work, but moves the actual file-operations popup show point back to the post-gesture context-menu phase so the menu no longer gets immediately dismissed by the same right-click interaction.
