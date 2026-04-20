# HOTFIX237 / Phase 4P.9EM

Baseline: HOTFIX236 / Phase 4P.9EL — THREE_REGION_SHELL_STABILIZATION_AND_INSPECTOR_MODE_INTEGRATION

## Scope
Fix the non-Details file-view regression introduced after the three-region shell stabilization so icon, list, tiles, and content views again occupy the full file workspace.

## Implemented
- forced modular file-view roots to fill the shared `viewHost` instead of relying on their computed size inside the new workspace shell
- bound loaded file-view roots to the host width and height so non-Details views cannot collapse to a narrow strip after shell relayout
- aligned loaded file-view roots to the top-left of the host so icon surfaces anchor correctly within the elastic middle workspace
- kept `ScrollPane`-based icon surfaces in fit-to-width and fit-to-height mode so the visible viewport tracks the workspace geometry
- left the Details modular surface compatible with the same host-fill path for consistent behavior across all view modes

## Key files
- `src/main/java/com/fileexplorer/ui/fileview/host/FileViewHost.java`

## Notes
This hotfix is a regression correction on top of HOTFIX236. It targets the symptom shown in the screenshot where non-Details views rendered as an effectively collapsed surface inside the otherwise full-width file workspace.
