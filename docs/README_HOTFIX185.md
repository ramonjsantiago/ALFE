# HOTFIX185 / Phase 4P.9CM — Ultra-Minimal Shell Scene, Lazy Menu Tree Materialization, and Two-Stage ExplorerContext Activation Parity

This project is the **fully populated HOTFIX185 integration** produced from the supplied **H184** baseline.

## What changed

- `MainApp` now uses an ultra-minimal pre-show shell scene with a reduced node tree and inline styling only.
- `MainLayout.fxml` no longer eagerly instantiates the large New / Sort / View / See more menu hierarchies during FXMLLoader startup.
- `MainController` lazily materializes those menu trees on first open and emits startup trace markers for each materialization.
- `ExplorerContext` now keeps stage-A construction lightweight and activates queue/history/template/command services as a stage-B graph after startup is already interactive.
- Operation-queue bindings are installed after first-interaction readiness rather than during `attach()`.

## Changed files

- `src/main/java/com/fileexplorer/app/MainApp.java`
- `src/main/java/com/fileexplorer/app/ExplorerContext.java`
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/resources/com/fileexplorer/ui/layout/MainLayout.fxml`
- `docs/README_HOTFIX185.md`
- `PHASE4P_9CM_ULTRA_MINIMAL_SHELL_SCENE_LAZY_MENU_TREE_MATERIALIZATION_AND_TWO_STAGE_EXPLORERCONTEXT_ACTIVATION_PARITY_NOTES.txt`

## Build note

The runtime used to prepare this delivery did not include the full Maven/JavaFX toolchain required for an end-to-end local app launch. The HOTFIX185 changes were integrated source-side against the supplied H184 project and then syntax-reviewed in-container.
