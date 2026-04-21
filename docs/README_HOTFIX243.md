# HOTFIX243 — File View Context Menu Sticky Lifetime and Hover Freeze

Baseline: HOTFIX242G — Extra Large Icons Context Menu Tooltip Lockdown

## Scope
Keep file-view item context menus open until the user explicitly dismisses them, and freeze hover-driven repaint/tooltip churn while a file-view context menu is pending or visible.

## Implemented
- switched shared Explorer context menus from native auto-hide to explicit outside-click / focus-loss dismissal
- closed the menu explicitly after item actions so command execution preserves normal popup behavior
- froze icon/details hover presentation while file-view menus are pending or visible

## Primary file
- `src/main/java/com/fileexplorer/controller/MainController.java`
