## HOTFIX186 / Phase 4P.9CN — Modular File-View Extraction, Per-Viewer FXML Decomposition, and Lazy Active-View Host Parity
- Added a new modular `com.fileexplorer.ui.fileview` package family plus a lazy `FileViewHost` to load file viewers into the main `viewHost` on demand.
- Split the Details, Extra large icons, Large icons, Medium icons, Small icons, List, Tiles, and Content surfaces into dedicated FXML/controller modules.
- Reworked `MainLayout.fxml` and `MainController` so view-mode changes activate modular viewer surfaces while preserving the current MainController-driven interaction pipeline.

# Changelog

## HOTFIX186 compile correction
- Restored the missing JavaFX imports in `MainApp` for `Label` and `Priority`, fixing the shell-scene build break introduced during the H186 modular file-view extraction pass.


## HOTFIX185 / Phase 4P.9CM — Ultra-Minimal Shell Scene, Lazy Menu Tree Materialization, and Two-Stage ExplorerContext Activation Parity

- minimized the pre-show shell scene to reduce `stage.show()` realization cost
- moved New / Sort / View / See more menu trees out of eager FXML construction and into first-open lazy materialization
- added startup trace markers around command-menu population and deferred ExplorerContext stage-B activation
- deferred operation-queue UI bindings until first-interaction readiness so attach/bootstrap stays lighter

## HOTFIX184 / Phase 4P.9CL — Stage-Show Critical-Path Triage, Split-FXML Bootstrap, and Lazy Thumbnail Capability Initialization Parity
- Kept the startup shell on an inline-style path by making the shell-stage stylesheet attachment optional, reducing pre-show CSS work before the first visible frame.
- Split `MainLayout.fxml` bootstrap loading by deferring `BreadcrumbBar.fxml` and `ProgressPane.fxml` until after the main UI scene is already live.
- Reworked `AsyncThumbnailService` startup so ImageIO plugin scanning and capability summaries are delayed until thumbnail enablement and only forced synchronously for ImageIO-managed formats that actually require reader discovery.

## HOTFIX183 / Phase 4P.9CK — Viewport-Scoped PDF Page Prioritization, Progressive Resolution Upgrade, and Scroll-Stability Parity
- Added explicit LOW/HIGH PDF thumbnail render tiers so visible PDF work can land a cheaper first useful image before any higher-resolution promotion attempts.
- Added viewport-scoped PDF promotion tracking and settle-time promotion scheduling so only the current visible PDF set is eligible for high-tier upgrade work.
- Added promotion pruning and per-document fairness guards to reduce stale off-screen upgrades and keep large-PDF promotion work from monopolizing the PDF document lane.
- Bumped the disk-cache compatibility fingerprint to align cached document thumbnails with the new progressive PDF planning pipeline.

## HOTFIX182 / Phase 4P.9CJ — Adaptive PDF Render Budgeting, First-Page Heuristics, and Large-Document Recovery Parity
- Added adaptive PDF thumbnail timeout budgeting in `AsyncThumbnailService`, using file size, requested thumbnail size, and recent render history to choose a more realistic budget before a PDF is treated as a timeout.
- Added first-page PDF render planning that inspects page count and first-page dimensions, then downshifts the effective thumbnail target size and render-scale cap for large/slow/pathological PDFs to improve first visible readiness.
- Added large-document recovery fallback for oversized or repeated-timeout PDFs, plus file-identity-driven adaptive history invalidation so editing/replacing the PDF clears the prior recovery state automatically.

## HOTFIX181 / Phase 4P.9CI — PDF Worker Quarantine, Retry-After-Change Invalidation, and Document-Lane Fairness Parity
- Split `AsyncThumbnailService` document thumbnail execution into dedicated PDF and Office-family executor lanes so slow/timed-out PDF renders remain quarantined away from Word/Excel/PowerPoint thumbnail throughput.
- Reworked PDF timeout cooldown suppression to key off file identity (`lastModifiedMs` + `fileSizeBytes`) rather than path alone, so an edited/replaced PDF can retry immediately instead of waiting for the previous cooldown window to expire.
- Upgraded thumbnail request/version tracking to include file identity and added a final stale-version completion guard, preventing late results for an outdated file revision from repainting or being cached after the file changes.

## HOTFIX179 / Phase 4P.9CG — JBIG2 PDF Thumbnail Decode Support and Malformed-PDF Fallback Parity
- Added the Apache PDFBox `jbig2-imageio` dependency so PDF thumbnails can decode PDFs that embed JBIG2 image streams instead of failing with `MissingImageReaderException`.
- Replaced the PDF thumbnail path in `AsyncThumbnailService` with a direct PDFBox renderer so malformed PDFs and missing PDF image readers fall back cleanly to the normal file-type icon without the repeated `PDFThumbnailer` SEVERE noise path.
- Expanded PDF capability probing and JUL configuration to include JBIG2 support, malformed-PDF one-shot warnings, and quieter third-party PDF thumbnail logger defaults.

## HOTFIX178 / Phase 4P.9CF — AVIF/WebP Thumbnail Decode Support and JUL Logging Unification
- Added TwelveMonkeys `imageio-webp` plus NightMonkeys `imageio-heif` runtime dependencies, and enabled `--enable-native-access=ALL-UNNAMED` for `javafx:run` so WebP thumbnails and HEIF/AVIF thumbnail readers can participate in the ImageIO pipeline when their runtime requirements are present.
- Expanded the thumbnail candidate/image classification pipeline for `.webp`, `.avif`, `.heif`, and `.heic`, and gated ImageIO-backed thumbnail requests on actual reader availability to avoid repeated decode failures when the runtime is missing a plugin or native HEIF/AVIF support.
- Replaced remaining direct console / stack-trace runtime logging in `StartupTrace`, `MainApp`, breadcrumb new-window helpers, and `RegressionCheckMain` with JUL logging, and switched the SLF4J runtime binding from `slf4j-nop` to `slf4j-jdk14` so third-party SLF4J logs route into JUL instead of disappearing.

## HOTFIX177 / Phase 4P.9CE — JPEG2000 PDF Thumbnail Decode Support and Graceful Fallback Parity
- Added the explicit `jai-imageio-jpeg2000` runtime dependency so PDFBox/thumbnails4j can decode JPX/JPEG2000 image streams embedded inside PDFs when the project is rebuilt from Maven.
- Hardened `AsyncThumbnailService` with a JPEG2000 reader capability probe plus a JPX preflight scan for PDFs, so JPX-backed PDFs degrade quietly to the normal file-type icon instead of spamming repeated thumbnail decode failures.
- Added one-shot diagnostic logging for missing JPEG2000 support and kept the rest of the document thumbnail pipeline unchanged for non-JPX PDFs.

## HOTFIX175 / Phase 4P.9CC — Velocity-Aware Prefetch Windows, Decode Cancellation, and Viewport Settle Parity
- Added velocity-aware viewport prefetch scaling for Details and icon views so look-ahead expands during fast scroll bursts and contracts again once the viewport settles.
- Added viewport-scope pruning and stale-completion discard counters to async icon/thumbnail pipelines so obsolete viewport work is cancelled earlier instead of repainting after the user has already outrun it.
- Added a post-idle viewport settle pass that reasserts selection/focus continuity and issues one direct visible-first realization refresh after scrolling stops.

- HOTFIX173 follow-up: reduced Tree view row spacing and cell padding for a denser navigation pane, and updated the runtime/tree CSS metrics to keep the tighter spacing consistent.
- HOTFIX173 follow-up: corrected the toolbar Sort command icon to use the attached resource-backed sort glyph instead of the prior vector fallback.
# Changelog

## HOTFIX174 / Phase 4P.9CB — Predictive Thumbnail Decode Budgeting, Cell Reuse Sanitization, and Scroll-Jank Telemetry Parity
- Added viewport-motion aware decode budgeting to thumbnail and icon scheduling so background/prefetch work yields more aggressively while the user is still scrolling.
- Hardened Details view cell reuse with binding stamps and explicit unregister paths so stale async icon/thumbnail completions cannot repaint recycled cells.
- Added scroll-hitch and idle-burst telemetry hooks in `MainController`, plus richer icon/thumbnail diagnostics for queue trim, stale-generation, and moving-background deferrals.

## HOTFIX173 / Phase 4P.9CA — Scroll-Ahead Prefetch, Realization Backpressure, and Selection/Anchor Continuity Parity
- Added viewport-scoped visible-first realization refreshes so Details and icon views prefetch just ahead of the current viewport instead of warming the entire folder at once.
- Reworked `AsyncIconService` into a generation-aware visible/prefetch/background queue with backpressure, deduplicated in-flight requests, and best-effort cancellation on directory / viewport scope changes.
- Hardened icon cell rebinding with an icon-stamp guard so stale async completions cannot paint into recycled visuals.
- Captured and restored viewport continuity around progressive directory reloads, preserving selection sets, focus/anchor intent, and first-visible positioning more reliably across refresh and hydration churn.
- Integrated viewport realization refresh scheduling with scroll, resize, progressive item streaming, and post-load restoration so nearby assets fill in after the visible set stabilizes.
- Refreshed the toolbar Sort menu icon resource from the attached `ContextMenu.Sort.svg`, regenerated `toolbar.sort.png`, and mirrored the resource into the packaged classpath copies.

## HOTFIX171 / Phase 4P.9BY — Post-Show Directory Hydration, Deferred Icon/Thumbnail Warmup, and First-Interaction Readiness Parity
- Routed initial folder opening through a post-show hydration handoff so the shell can paint before directory enumeration begins.
- Added startup instrumentation for post-show hydration scheduling, first-batch directory commit, icon-gate open, thumbnail-gate open, and first-interaction readiness.
- Split progressive directory enumeration into a smaller startup-first batch and larger follow-on batches to reduce first-click/first-scroll contention.
- Added async icon gating so placeholder icons stay cheap until the first visible directory batch is committed.
- Deferred current-folder thumbnail warmup until after first-interaction readiness, keeping preview/thumbnail work behind a later startup gate.
