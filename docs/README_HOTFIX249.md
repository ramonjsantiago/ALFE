# HOTFIX249 / Phase 4P.9EM — Inspector Mode Persistence and Cross-View Parity

Baseline: HOTFIX248 — Details / Preview Pane Restore

## Scope
Stabilize the shared inspector region so Details, Preview, and Operations behave consistently across view switches, directory navigation, and restart, while also increasing Details view row spacing to 3 pixels.

## Implemented
- persisted the last non-operations inspector content mode separately from transient Operations visibility so Details vs Preview restores predictably
- restored inspector content mode together with inspector visibility/width preferences during startup
- re-applied inspector presentation and current selection content after file-view switches and directory listing refreshes
- kept Details/Preview content refresh tied to the current primary selection even when the active surface changes
- adjusted Details view row metrics from 2px inter-row spacing to 3px spacing

## Primary files
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/css/details-view-parity.css`
