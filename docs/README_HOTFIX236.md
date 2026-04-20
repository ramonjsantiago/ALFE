# HOTFIX236 / Phase 4P.9EL

Baseline: HOTFIX235 / Phase 4P.9EK — THREE_REGION_WORKSPACE_SHELL_AND_LAZY_INSPECTOR_HOST

## Scope
Stabilize the new three-region workspace shell and complete inspector-mode integration without returning to SplitPane as the primary shell.

## Implemented
- kept the workspace center on a horizontal three-region shell: navigation, file workspace, inspector host
- converted the right inspector region into a fully lazy host with runtime-built Details, Preview, and compatibility Operations cards
- removed eager inspector subtree construction from `MainLayout.fxml`; the inspector host is now empty until requested
- persisted and restored workspace shell state through preferences:
  - navigation pane width
  - navigation pane visibility
  - inspector width
  - inspector mode
- preserved workspace focus when toggling Details or Preview so file/table and tree focus do not get stranded on pane toggles
- kept the file workspace as the dominant elastic middle section while navigation and inspector widths remain explicit
- kept legacy split-pane fields in quarantine for compatibility paths, but the primary shell remains the horizontal workspace container

## Key files
- `src/main/resources/com/fileexplorer/ui/layout/MainLayout.fxml`
- `src/main/java/com/fileexplorer/controller/MainController.java`

## Notes
This hotfix is the consolidation phase for the HOTFIX235 shell conversion. The next likely phase should target deeper Preview pipeline loading and inspector-content rendering behavior on top of the stabilized shell.
