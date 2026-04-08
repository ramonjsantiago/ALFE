# HOTFIX192 / Phase 4P.9CT

Explorer Search UX Parity.

## What changed
- Added a right-edge in-field clear affordance that appears only while a search query is active.
- Added active-query and focused-search chrome states so the search box reads more like Windows 11 Explorer during typing and keyboard focus transitions.
- Added `Ctrl+E` as an Explorer-style shortcut to focus the search box, while keeping `Ctrl+F` working.
- Tightened Escape semantics so Escape clears the active query first and then returns focus to the file surface once the query is already empty.
- Updated the status bar to show result counts in the current folder scope while a search is active.

## Primary files
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/layout/MainLayout.fxml`
- `src/main/resources/com/fileexplorer/ui/css/address-command-parity.css`
- `src/main/resources/com/fileexplorer/ui/css/explorer-override-everything.css`
