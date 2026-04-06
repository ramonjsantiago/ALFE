# HOTFIX183 / Phase 4P.9CK — Viewport-Scoped PDF Page Prioritization, Progressive Resolution Upgrade, and Scroll-Stability Parity

This project is the **fully populated HOTFIX183 integration** produced from the supplied **H182** baseline.

## Integrated changes

### Viewport-scoped PDF prioritization
- Visible PDF thumbnail requests now enter a **low-tier visible-first render path** instead of going straight to the heaviest PDF raster budget.
- The thumbnail service tracks the current viewport-scoped visible PDF set and prunes stale promotion intents whenever the viewport scope advances.
- Cached low-tier PDF thumbnails are still returned immediately, but they now trigger a follow-on promotion request only when the file remains part of the current viewport scope.

### Progressive resolution upgrade
- Added an explicit PDF render tier model with **LOW** and **HIGH** planning paths.
- LOW tier uses a tighter size/scale cap so first useful visible PDF imagery appears faster during active scroll and early settle windows.
- HIGH tier promotion is queued only after motion settles and only for PDFs that still belong to the current visible scope.
- High-tier completions replace the lower-quality cached entry and persist to disk cache; low-tier transient results stay memory-scoped so stale low-resolution payloads do not become the long-term cache truth.

### Scroll-stability and fairness guards
- Added viewport-scope keyed promotion records and delayed settled promotion scheduling so off-screen PDFs are demoted instead of competing with currently visible work.
- Added bounded counters for:
  - low-tier PDF completions
  - high-tier PDF completions
  - queued promotions
  - completed promotions
  - skipped promotions
- Added per-document PDF render fairness guards so high-tier promotion work cannot fan out across the same PDF and monopolize the document lane.
- Added promotion pruning on viewport-scope changes so rapid scrolling avoids stale high-tier work bursts and reduces repaint churn.

### Disk-cache compatibility update
- Bumped the disk-cache compatibility fingerprint to `phase4p9ck` so previously cached artifacts from the older PDF budgeting pipeline are less likely to survive the new progressive-resolution planning path.

## Source areas changed
- `src/main/java/com/fileexplorer/service/icon/AsyncThumbnailService.java`
- `PHASE_LABEL.txt`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `docs/README_HOTFIX183.md`
- `PHASE4P_9CK_VIEWPORT_SCOPED_PDF_PAGE_PRIORITIZATION_PROGRESSIVE_RESOLUTION_UPGRADE_AND_SCROLL_STABILITY_PARITY_NOTES.txt`

## Verification note
The runtime used to prepare this delivery did not include Maven, JavaFX, or the project dependency graph required for a full local build. The HOTFIX183 changes were integrated source-side against the supplied H182 project and syntax-checked on the modified Java source as far as the available container tooling allowed.
