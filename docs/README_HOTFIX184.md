# HOTFIX184 / Phase 4P.9CL — Stage-Show Critical-Path Triage, Split-FXML Bootstrap, and Lazy Thumbnail Capability Initialization Parity

This project is the **fully populated HOTFIX184 integration** produced from the supplied **H183** baseline.

## Integrated changes

### Stage-show critical-path triage
- Kept the startup shell almost entirely **inline styled** so `stage.show()` does not pay for the full theme/window CSS pipeline before the first visible frame.
- Preserved the single-scene shell-first startup handoff while reducing work in the pre-show path.
- Left the full Explorer stylesheet stack attached only when the main UI root is ready.

### Split-FXML bootstrap
- Removed the breadcrumb include and operations/progress include from the main `MainLayout.fxml` bootstrap load.
- Added deferred loading for `BreadcrumbBar.fxml` and `ProgressPane.fxml` after the main scene is already live.
- Added startup trace markers for deferred breadcrumb/progress-pane mounting so the split bootstrap is measurable in traces.

### Lazy thumbnail capability initialization
- Removed eager ImageIO plugin scanning and capability probing from `AsyncThumbnailService` construction.
- Added delayed background capability initialization after the thumbnail gate opens.
- Reserved synchronous capability initialization only for non-native ImageIO formats that actually need reader discovery.
- Left normal PDF/Office thumbnail provider routing unchanged so document-lane startup no longer pays for AVIF/HEIF/WebP reader probing up front.

## Source areas changed
- `src/main/java/com/fileexplorer/app/MainApp.java`
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `src/main/java/com/fileexplorer/service/icon/AsyncThumbnailService.java`
- `src/main/resources/com/fileexplorer/ui/layout/MainLayout.fxml`
- `PHASE_LABEL.txt`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `docs/README_HOTFIX184.md`
- `PHASE4P_9CL_STAGE_SHOW_CRITICAL_PATH_TRIAGE_SPLIT_FXML_BOOTSTRAP_AND_LAZY_THUMBNAIL_CAPABILITY_INITIALIZATION_PARITY_NOTES.txt`

## Verification note
The runtime used to prepare this delivery did not include Maven or the full JavaFX dependency graph required for an end-to-end local app launch. The HOTFIX184 changes were integrated source-side against the supplied H183 project and syntax-checked on the modified Java source as far as the available container tooling allowed.
