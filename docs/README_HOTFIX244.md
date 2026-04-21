# HOTFIX244 — File View Context Menu Console Lifecycle Logging

Baseline: HOTFIX243 — File View Context Menu Sticky Lifetime and Hover Freeze

## Scope
Add direct console diagnostics that show when Explorer right-click context menus pop up and pop down, plus why a hide was requested.

## Implemented
- lifecycle logging for popup show/hide transitions
- menu-kind tagging for tree vs file item vs background menus
- hide-request reason logging for owner interaction, focus loss, command execution, and menu replacement

## Primary file
- `src/main/java/com/fileexplorer/controller/MainController.java`
