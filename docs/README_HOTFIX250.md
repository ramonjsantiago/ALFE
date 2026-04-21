# HOTFIX250 / Phase 4P.9EO — Preview Thumbnail Retention and Selection Refresh Guard

Baseline: HOTFIX249 — Inspector Mode Persistence and Cross-View Parity

## Scope
Fix the preview-pane regression where image thumbnails briefly appear and then fall back to the default placeholder icon during selection or inspector refresh churn.

## Implemented
- prevented same-selection inspector refreshes from reapplying the placeholder image once a real preview thumbnail is already displayed
- tracked whether the current preview image is a resolved thumbnail versus a temporary placeholder
- kept preview fallback text hidden when a resolved thumbnail remains valid for the active selection
- preserved the existing async thumbnail request path for new selections while skipping redundant placeholder resets for unchanged selections

## Primary files
- `src/main/java/com/fileexplorer/controller/MainController.java`
