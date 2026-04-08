# HOTFIX187 / Phase 4P.9CO — Idle-Budget Menu/View Prewarm, Predictive Adjacent-Viewer Priming, and First-Switch Zero-Hitch Parity

This project is the **fully populated HOTFIX187 integration** produced from the supplied **HOTFIX186** baseline.

## What changed

- Added opportunistic idle prewarm scheduling to the shared `StartupWorkQueue` so noncritical menu/view prewarm work only runs during a true quiet window.
- Added startup-time prewarm scheduling in `MainController` immediately after the first visible directory hydration batch commits.
- Prewarms the `View`, `See more`, `Sort`, and `New` command-bar menus before the first click whenever startup enters an idle window.
- Prewarms likely next file-view surfaces in predictive order so the first switch from Details into icon/list/tile/content modes avoids the lazy-load hitch introduced in HOTFIX186.

## Changed files

- `src/main/java/com/fileexplorer/util/StartupWorkQueue.java`
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `docs/README_HOTFIX187.md`
- `PHASE4P_9CO_IDLE_BUDGET_MENU_VIEW_PREWARM_PREDICTIVE_ADJACENT_VIEWER_PRIMING_AND_FIRST_SWITCH_ZERO_HITCH_PARITY_NOTES.txt`
- `CHANGELOG.md`
- `PHASE_LABEL.txt`
- `CHANGED_FILES.txt`

## Build note

The runtime used to prepare this delivery did not include the full Maven/JavaFX toolchain required for an end-to-end local app launch. The HOTFIX187 changes were integrated source-side against the supplied HOTFIX186 project and syntax-reviewed in-container.

[APP/TRAY ICON UPDATE]
- Added src/main/resources/icons/app.png from the attached asset.
- Added src/main/resources/icons/app.tray.png from the attached asset.
- Updated MainApp to apply the application icon and install/remove the system tray icon.

- Added dedicated default .ini file icon resources and ext:ini override loading in IconLoader.

- Added dedicated MSI file icon resources and wired `ext:msi` to the new assets.
