# HOTFIX240 / Phase 4P.9EP

Baseline: HOTFIX239 / Phase 4P.9EO — FILE_VIEW_CONTEXT_MENU_EVENT_FALLBACK_RESTORE

## Scope
Fix the remaining file-view item right-click regression by catching item secondary-press gestures at the shared file-view host before virtualized icon surfaces can drop the event, delay menu showing until after the secondary-click gesture settles, prevent tree-view secondary clicks from triggering directory navigation, and print resolved Extra Large Icons item names for diagnostics.

## Implemented
- added a shared view-host secondary-press capture path for icon-family views so right-clicking a file or folder item resolves the concrete tile path before downstream virtualization refresh churn can swallow the menu request
- deferred icon-surface file-operations menu display by an extra FX pulse so the popup is shown after the opening secondary-click gesture completes instead of racing the release event
- added explicit diagnostic logging for Extra Large Icons item-target resolution so the resolved item names and paths are printed when the context-menu target is found
- prevented tree-view secondary-click selection from triggering selection-driven directory loading while preserving double-click activation for opening folders

## Key files
- `src/main/java/com/fileexplorer/controller/MainController.java`

## Notes
This hotfix specifically targets the still-broken file-view item popup path reported after HOTFIX239 and adds the requested Extra Large Icons target-name printout to help confirm whether the shared icon-surface resolver is finding the correct item before the menu is shown.
