# HOTFIX239 / Phase 4P.9EO

Baseline: HOTFIX238 / Phase 4P.9EN — FILE_VIEW_ITEM_CONTEXT_MENU_RESTORE

## Scope
Restore file-view item right-click popup menus using container-level fallback routing so item menus open reliably even when modular surfaces, virtualized cells, or right-click refresh churn bypass per-item handlers.

## Implemented
- added a shared item-context-menu path resolver that can recover a file path from either a Details row target or an icon-surface tile target
- routed table-level `ContextMenuRequested` events back to the item menu when the target is inside a data row instead of assuming the row-local handler already fired
- routed view-host icon-surface `ContextMenuRequested` events back to the item menu when the target is inside an icon item instead of falling through to background handling
- kept background menus available only for true empty-space requests, preserving the existing suppression window for duplicate follow-on events from the same gesture

## Key files
- `src/main/java/com/fileexplorer/controller/MainController.java`

## Notes
This hotfix targets the remaining regression where tree-view context menus still worked, but file-view item right-click menus did not reliably appear because the new three-region shell and modular file surfaces could bypass the original per-item handlers.
