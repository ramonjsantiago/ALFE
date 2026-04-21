## HOTFIX244 — File View Context Menu Console Lifecycle Logging
- added explicit console and logger lifecycle traces for Explorer context menus covering `window-showing`, `window-shown`, `window-hiding`, and `window-hidden`
- tagged each Explorer popup instance with a menu kind (`tree`, `file-ops`, `file-view-background`) so right-click investigations can distinguish which popup is opening or closing
- logged hide-request reasons for owner-scene mouse presses, owner-window focus loss, popup replacement before re-show, command execution, and transient-UI teardown to expose why a menu collapses immediately

## HOTFIX243 — File View Context Menu Sticky Lifetime and Hover Freeze
- switched Explorer file-view and tree context menus to an explicit sticky lifetime model so the popup stays open until the user clicks outside, presses Escape, or the owner window loses focus
- disabled native JavaFX auto-hide for shared Explorer menus and installed explicit owner-scene outside-click dismissal plus parent-popup close on action execution
- froze file-view hover presentation while context menus are pending or visible so extra-large-icons and details hover repaint churn cannot immediately collapse the popup

## HOTFIX242F — Context Menu Tooltip Suppression Hardening and Right-Click Selection Stabilization
- suppress the file-view hover tooltip as soon as an item context menu is armed or pending so the delayed metadata popup can no longer appear during right-click menu open and collapse the menu
- make item right-click selection an immediate single-target selection update instead of routing through the heavier deferred selection stabilization path, reducing large-directory churn during popup open
- keep the file-view context-menu pending state alive until the popup is shown or cancelled so item and background menus remain tooltip-suppressed until the user clicks away

## HOTFIX242C — File View Context Menu Mouse-Move Stability
- moved file-view item popup opening onto the secondary-press path for Details and icon-family surfaces so the menu is no longer tied to the later `ContextMenuRequested` lifecycle while the right button remains down
- extended duplicate-request suppression for the same right-click gesture so the follow-on native context-menu event does not immediately retrigger or destabilize the popup after the menu is already visible
- removed owner-window focus-loss dismissal from the shared Explorer popup wiring and added popup-scene mouse-release dismissal so moving the pointer across the shown popup no longer collapses it before the release gesture completes

## HOTFIX242B — File View Context Menu Release-Only Dismissal
- restored controlled file-view context-menu lifetime management so popup dismissal happens on owner-scene mouse release instead of mouse press
- removed owner-scene `ContextMenuRequested` teardown from the shared Explorer popup wiring so the opening secondary-click gesture no longer dismisses the menu before release
- preserved Escape and window-focus-loss dismissal behavior while keeping the existing opening-gesture ignore window in place

## HOTFIX242 / Phase 4P.9ER — File View Item Context Menu Release Stabilization
- split file-item secondary-click handling into an arming pass on mouse press and a popup-show pass on the later `ContextMenuRequested` event so the menu opens after the right-click release instead of being dismissed by the same gesture
- retained a short-lived armed item path plus tight screen-position matching so virtualized icon grid/list surfaces can still resolve the correct item even when the later context-menu event no longer points at the original tile node
- cleared stale armed item requests before showing the file-view background menu so empty-space right clicks stay on the background path instead of reviving an older item target

## HOTFIX241 / Phase 4P.9EQ — Virtual File View Item Context Menu Restore
- targeted the large-folder-only file-view context-menu regression by wiring explicit secondary-click and context-menu handlers onto the virtualized icon grid and virtualized icon list cells
- resolved the requested item path from the virtual cell itself, including child screen-bounds hit testing for multi-item virtual grid rows, so right-clicking visible file items in large directories reaches the file operations menu reliably
- kept the existing small-folder FlowPane icon-surface item popup behavior unchanged while preserving the shared background-menu separation path

## HOTFIX240 / Phase 4P.9EP — File View Context Menu Capture Fix and Tree Secondary-Nav Guard
- added a shared file-view host secondary-press capture path for icon-family views so right-clicking an item resolves the target before virtualized or modular surfaces can drop the gesture
- deferred icon-surface item popup display by an additional FX pulse so the file-operations menu opens after the secondary-click gesture settles instead of racing the opening release event
- added diagnostic logging that prints resolved Extra Large Icons item names and full paths when the item popup target is found
- prevented tree-view secondary-click selection from auto-loading the clicked directory while preserving double-click activation for opening folders

## HOTFIX239 / Phase 4P.9EO — FILE_VIEW_CONTEXT_MENU_EVENT_FALLBACK_RESTORE
- added shared item-target resolution for both Details rows and icon-surface tiles so container-level context-menu handlers can recover the clicked file path reliably
- routed file-table and file-view-host ContextMenuRequested events back to the item popup menu for true item targets instead of assuming row/tile-local handlers already fired
- kept empty-space background context menus and duplicate-gesture suppression behavior intact so only actual item requests promote to the file operations menu

## HOTFIX236 / Phase 4P.9EL — THREE_REGION_SHELL_STABILIZATION_AND_INSPECTOR_MODE_INTEGRATION
- stabilized the HOTFIX235 three-region workspace shell
- removed eager inspector-pane FXML construction and switched to a lazy runtime-mounted inspector host
- persisted and restored navigation width, navigation visibility, inspector width, and inspector mode
- preserved workspace focus when toggling Details and Preview
- retained shared inspector-host compatibility for Operations

# Changelog

## HOTFIX232 / Phase 4P.9EH — Tree Leaf Indent Final Adjustment
- Reduced navigation-tree leaf disclosure padding by 2 additional pixels so non-directory items shift left while expandable directory rows keep their existing alignment.
- Updated both the shared tree stylesheet and the navigation-pane parity override so the leaf-indent correction applies consistently anywhere the Explorer tree skin is active.

## HOTFIX217 / Phase 4P.9DS — Two-Tone View Icon Rendering with Blue Accent Preservation
- Replaced the flat all-white View icon masking path with a two-tone conversion that preserves original blue accent pixels while recoloring neutral icon strokes to white.
- Applied the same two-tone treatment to the top-level View button active icon and to the View flyout raster icons so command-bar and menu rendering stay visually aligned.
- Kept the H215/H216 white-on-dark readability improvements without flattening pane and navigation icon accent details.

## HOTFIX209 / Phase 4P.9DK — Main-UI First-Render Critical-Path Decomposition and Lazy Details Mount
- Removed the eager Details-view module load from `MainController.initialize(...)` so the main layout can finish loading without synchronously materializing `DetailsView.fxml` during `FXMLLoader.load()`.
- Added a lightweight `viewHost` startup placeholder plus a deferred lazy-details mount path that loads/configures the Details surface after the main UI root is already attached.
- Added new `StartupTrace` markers for `MainController.initialize(...)`, `initializeWithContext(...)`, and the lazy-details schedule/mount/configure phases so first-render regressions are easier to attribute.

## HOTFIX204 / Phase 4P.9DF — Details Header Dropdown Ownership, Right-Edge Menu Parity, and Column-Snapshot Correctness
- Rebound the Details header right-edge dropdown and full header popup to per-open column snapshots so actions and menu contents no longer drift when header interactions overlap.
- Restored the compact right-edge dropdown to the currently visible Details columns, preserving the checkbox + icon + label row structure and canonical Explorer column order.
- Added attached-header menu realignment after show and cleared active header dropdown state whenever row/background context menus take ownership.

## HOTFIX201 / Phase 4P.9DC — Details View Navigation-Epoch Isolation, Stale Async Icon Result Suppression, and Sort/Refresh Paint Stability Parity
- Added a controller-owned Details async binding epoch so navigation, Details refresh passes, and Details sort transitions invalidate older icon publishes before they can repaint reused rows.
- Reworked `MainController.ExplorerNameTableCell` to rebind from the active `TableRow` item so row reuse cannot preserve a stale icon binding when the visible name string is unchanged.
- Tightened Details async icon and thumbnail commit guards to require epoch, row, `FileItem`, path, display text, and resolved identity parity before painting.
- Preserved thumbnail wins over slower icon completions and upgraded folder fallback placeholders to retain folder/file identity correctness during refresh churn.

## HOTFIX200 / Phase 4P.9DB — Details View Icon Refresh Coalescing, Watcher-Burst Deduplication, and Selection-Stable Repaint Parity
- Added a dedicated `DetailsViewRefreshCoordinator` for Details view so bursty item-source, sort, and comparator changes collapse into a single coalesced `TableView.refresh()` pass.
- Preserved selection and focus by file path across the coalesced refresh so Details repaint stays visually stable under watcher and refresh churn.
- Updated `TableViewSupport` and `VisibleThumbnailManager` to skip duplicate same-binding icon/thumbnail re-registration, reducing redundant icon work during bursty refresh cycles.

## HOTFIX198 / Phase 4P.9CZ — Details View Icon Identity Binding, Cell Reuse Sanitization, and Post-Refresh Paint Parity
- Reworked the Details Name-column cell binding so async icon/thumbnail paint is validated against the current row identity, path, type key, and display text instead of only the cell text snapshot.
- Added explicit cell sanitization on reuse/unbind paths, cancelling pending icon and thumbnail work, unregistering viewport thumbnail bindings, and clearing stale graphic/text state before rebinding.
- Added row-item listeners so virtualized Details rows rebind their icon immediately after refresh/sort/rename churn even when the display text stays the same.

## HOTFIX195 / Phase 4P.9CW — Command Bar Template Fidelity, Segment Spacing, and Right-Rail Action Parity

- HOTFIX195 follow-up: integrated the supplied `.bin` file artwork as the dedicated BIN thumbnail/icon resource under `src/main/resources/com/fileexplorer/ui/icons/{light,dark}` and routed `ext:bin` through `IconLoader` override resolution.
- HOTFIX195 follow-up: guarded responsive virtual-icon prefWidth writes when the list/grid views are already width-bound, then requested layout refresh instead of setting a bound value.

- HOTFIX195 crash follow-up: added controller-disposal guards, stopped pending metadata/search debounces during dispose(), and routed IO/hover background dispatch through safe executor wrappers so shutdown cannot throw `RejectedExecutionException` on the JavaFX thread.

- converted Cut / Copy / Paste / Rename / Share / Delete to Explorer-style icon+text command-bar entries
- grouped Preview / Operations / Details into a more Windows-like trailing action rail with dedicated separator treatment
- tightened command-bar height, padding, icon/text spacing, and structured menu chevron spacing for closer Explorer parity

- HOTFIX194 follow-up: removed the bottom status-bar view-mode text labels so the Details and Extra large icons toggles render as icon-only buttons.
- HOTFIX194 follow-up: corrected Sort/View/Filter structured menu vertical alignment by collapsing the native MenuButton arrow lane and re-centering the custom icon/text/chevron content to match the surrounding command-bar controls.
- HOTFIX194: top-chrome metrics parity pass with tighter tab/address/search geometry, command-bar icon alignment refinements, and a structured Filter command added to match the Windows Explorer reference.
## HOTFIX192 / Phase 4P.9CT — Explorer Search UX Parity
- Added an in-field clear-search affordance to the main Explorer search box and only surface it while a query is active.
- Added focused and active search-shell chrome states plus dynamic search tooltip/prompt syncing so the top-right search surface behaves more like Windows 11 Explorer.
- Added `Ctrl+E` focus parity and tightened Escape semantics so Escape clears the query first, then returns focus to the active file surface once the search box is already empty.
- Updated active-search status text to report scoped result counts for the current folder.

## HOTFIX191 / Phase 4P.9CS — Navigation Pane Fixed-Width Window Resize Parity
- Reworked `MainController` navigation-pane resize handling so the left tree shell keeps a pinned pixel width during window stretch and shrink, letting the file-view side absorb resize deltas.
- Added main-divider tracking that refreshes the stored navigation width only after explicit user divider moves, eliminating width drift caused by proportional `SplitPane` resizing.
- Added a programmatic-divider guard so resize correction passes do not recursively overwrite the user-established navigation width.

## HOTFIX189 / Phase 4P.9CQ — Viewport-Priority PDF Thumbnail Scheduling, Scroll-Cancel Hygiene, and First-Visible Commit Parity
- Reworked `VisibleThumbnailManager` to snapshot visible Details cells and issue visible thumbnail requests in scene/viewport order instead of `WeakHashMap` iteration order, so top-of-viewport thumbnails begin first.
- Added a late-commit visibility guard so async thumbnail completions are ignored once a recycled or scrolled-out cell is no longer visibly realized.
- Preserved the existing cancellation path for non-visible cells and now cancel hidden registrations opportunistically during the visible pump as well.

- Added dedicated .ini file icon resources under src/main/resources/com/fileexplorer/ui/icons/{light,dark} and routed ext:ini through IconLoader override resolution.
## HOTFIX186 / Phase 4P.9CN — Modular File-View Extraction, Per-Viewer FXML Decomposition, and Lazy Active-View Host Parity
- Added a new modular `com.fileexplorer.ui.fileview` package family plus a lazy `FileViewHost` to load file viewers into the main `viewHost` on demand.
- Split the Details, Extra large icons, Large icons, Medium icons, Small icons, List, Tiles, and Content surfaces into dedicated FXML/controller modules.
- Reworked `MainLayout.fxml` and `MainController` so view-mode changes activate modular viewer surfaces while preserving the current MainController-driven interaction pipeline.

# Changelog

## HOTFIX187 / Phase 4P.9CO — Idle-Budget Menu/View Prewarm, Predictive Adjacent-Viewer Priming, and First-Switch Zero-Hitch Parity
- Added opportunistic idle startup work in `StartupWorkQueue` so speculative menu/view prewarm only runs during a true quiet window and is never forced through by the maximum-deferral timer.
- Added zero-hitch startup prewarm scheduling in `MainController` right after the first visible directory batch commits.
- Prewarmed `View`, `See more`, `Sort`, and `New` command-bar menus plus the likely next file-view surfaces so first-open / first-switch hitching is reduced without giving back the HOTFIX186 startup win.

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

[APP/TRAY ICON UPDATE]
- Added src/main/resources/icons/app.png from the attached asset.
- Added src/main/resources/icons/app.tray.png from the attached asset.
- Updated MainApp to apply the application icon and install/remove the system tray icon.

- Added dedicated .msi file-type icon resources and IconLoader ext:msi override mapping.

- Added dedicated default PDF resource icons (light/dark, 16-256) for ext:pdf so PDF files show the attached icon until thumbnails are ready.

## H187 Text File Icon Update
- Added dedicated packaged default icons for `.txt` and `.text` files in light and dark resource sets.
- Updated `IconLoader` so `ext:txt` and `ext:text` resolve to the new packaged text-file icon while content/text loading is pending.
- Mirrored the new resources into `target/classes` for the packaged full-project tree.


## Packaging reintegration
- Reintegrated HOTFIX187 updates onto the larger HOTFIX186 compile-fix baseline to preserve the fully populated project footprint.
- Restored missing mirrored build/resource payload under target/classes while keeping HOTFIX187 code and icon changes.

- H187 resource update: added dedicated packaged CSV default icons (`csv-16..256.png`, light/dark) and mapped `ext:csv` to the CSV resource icon while thumbnails are pending.

- H187 resource update: added dedicated packaged DOCX default icons (`docx-16..256.png`, light/dark) and mapped `ext:docx` to the DOCX resource icon while thumbnails are pending.

- H187 resource update: added dedicated packaged compressed-archive default icons (`compressed-16..256.png`, light/dark) and mapped `ext:zip`, `ext:7z`, `ext:gz`, `ext:bz2`, and `ext:tar` to the compressed resource icon while thumbnails are pending.
## HOTFIX188 / Phase 4P.9CP
- Reintegrated the Details name cell with the viewport-aware thumbnail pipeline while preserving inline rename.
- Restored small Details-cell thumbnail request sizing and kept extension-specific fallback icons active until thumbnail completion.
- Exposed the shared table VisibleThumbnailManager for custom Details name cells so viewport cancellation/debounce behavior remains consistent.


- HOTFIX188 follow-up: tuned the main Explorer search box to match Windows 11 Explorer more closely, added an in-field magnifying glass on the left, and retained dynamic prompt text in the form `Search <current folder>`.

## HOTFIX190 / Phase 4P.9CR
- Locked Extra large icon view to 256px request sizing and widened controller-side icon request clamps up to 256px.
- Added a fixed 256px Extra large icon slot and pinned grid tile width to 256px so the File view no longer uses the earlier 228px width budget in this mode.
- Switched the 256px Extra large icon path to intrinsic-size ImageView rendering so loaded 256px assets are not post-scaled via fitWidth/fitHeight.

## HOTFIX193 / Phase 4P.9CU
- Added an explicit Explorer search session lifecycle with idle/typing/searching/results/no-results controller states.
- Captured the current folder as the active search scope and cancel the session automatically when navigation moves to a different folder or Home.
- Added debounced generation-token search dispatch so stale search completions are discarded instead of publishing into the wrong folder or older query state.
- Added a dedicated in-surface `Searching…` / `No results found` overlay so empty search results are visually distinct from an empty folder.
- Restored the original folder surface on search clear and perform best-effort selection/focus recovery after exiting search.

- HOTFIX193 compile fix: restored the missing `isUsingVirtualIconListForCurrentView()` controller helper used by search-session focus restoration so the project compiles again.

- HOTFIX194 follow-up: wired the bottom status-bar `Details` button to switch to Details view and the bottom status-bar `Large` button to switch to Extra large icons view.
- HOTFIX194 follow-up: assigned the supplied icon assets to the two status-bar view buttons and synchronized their selected state with the active file view.

- HOTFIX194 follow-up: removed the top command-bar `Filter` menu and replaced the top `Preview` button glyph with the newly supplied icon asset.

- HOTFIX195 corrective pass: raised structured command-bar menu content baseline so New / Sort / View align vertically with adjacent command buttons.

- HOTFIX195 final corrective pass: increased the structured command-bar menu baseline lift and raised the icon/text/chevron slots together so New / Sort / View no longer sit visibly lower than neighboring toolbar commands.

- Corrective pass: raised structured New / Sort / View command-bar menu content an additional 2 px for closer vertical baseline parity.

- HOTFIX195 follow-up: pinned dark top-chrome / command-bar labels, glyph icons, vector icons, and chevrons to white for stronger Windows Explorer parity.

- HOTFIX195 follow-up: added dedicated `.iso` thumbnail/icon resources in both light and dark theme resource trees and routed `ext:iso` through `IconLoader`.

- H195: updated dedicated `.ini` thumbnail/icon resources from supplied artwork in light/dark resource folders.
- H195: Added dedicated .reg thumbnail/icon resources (light/dark, 16-256) and routed .reg extension lookup through IconLoader override resources.
HOTFIX195: integrated dedicated Home icon resources and wired Home UI surfaces to use them.
- H195 asset update: replaced the Undo menu icon resource with the supplied artwork under `src/main/resources/icons/see_more_undo.png` (and mirrored `see.more.undo.png`).


- H195 asset update: added dedicated disk icon resources and routed filesystem root drives through `special:disk` so disk/root-drive thumbnails use the supplied artwork.

- H195 asset update: added dedicated network-drive icon resources and routed network-drive roots through `special:networkdrive` so network drive thumbnails use the supplied artwork.

- H195 asset update: added dedicated local-disk icon resources and routed non-network root drives through `special:localdisk` so local disk thumbnails/icons use the supplied artwork.

- H195 asset update: added dedicated video file placeholder icon resources under `src/main/resources/com/fileexplorer/ui/icons/{light,dark}/` so video files use the supplied artwork until actual thumbnails resolve.

- H195: Replaced generic video placeholder thumbnail artwork with updated supplied icon resources.

- H195: added dedicated `.ico` placeholder thumbnail resources and `IconLoader` override routing for icon files.

- H195 resource update: added dedicated This PC icon resources and wired the navigation tree root to `special:thispc`.

- HOTFIX196 / Phase 4P.9CX: centralized placeholder icon identity routing in `IconLoader.identityForPath(...)` so tree, preview, and file-surface placeholder binding use the same shell identity model.
- HOTFIX196 / Phase 4P.9CX: added grouped `kind:` placeholder identities for video/audio/image/archive/text families while preserving dedicated special/ext overrides.
- HOTFIX196 / Phase 4P.9CX: preview pane now keeps the correct placeholder icon visible for thumbnail candidates while the resolved thumbnail is pending.
- HOTFIX196 / Phase 4P.9CX: tightened async thumbnail swap guards by binding both target path and identity before publishing the decoded image.

- HOTFIX197 / Phase 4P.9CY: centralized document thumbnail backend selection so PDFs route explicitly to PDFBox and Office-family document types route explicitly to thumbnails4j.
- HOTFIX197 / Phase 4P.9CY: added Office-family document failure cooldown/backoff to suppress repeated backend churn on every repaint/scroll after a thumbnail failure.
- HOTFIX197 / Phase 4P.9CY: added low-tier/high-tier Office-family document thumbnail promotion so visible large-icon requests can render a fast first-pass preview before promoting to the requested resolution.
- HOTFIX197 / Phase 4P.9CY: prevented low-tier document previews from being persisted into the disk cache so only stable promoted document thumbnails are written.

- HOTFIX197 compile fix: restored missing IconPathTreeCell imports for FileSystemView and Locale.

## HOTFIX199 / Phase 4P.9DA
- Tightened Details name-cell async icon commit gating so late completions must still match the exact bound `FileItem`, row, path, display text, and resolved identity before painting.
- Rebound Details icon state immediately on row-item replacement so refresh/sort/rename model churn invalidates older icon work instead of letting stale completions publish.
- Promoted the initial Details placeholder to identity-aware fallback selection (`ext:*`, `kind:*`, `special:*`, folder) for better shell/file-type parity while async work is settling.
- Prevented late async icon completions from overwriting a thumbnail that already won the placeholder → thumbnail promotion path.

## HOTFIX202 / Phase 4P.9DD
- Added a controller-owned visible directory scope so Details async icon and thumbnail publishes are fenced to the active folder surface as well as the current binding epoch.
- Finalized same-name row-reuse handling by threading the active directory scope through Details Name-cell binding equivalence/current-binding checks.
- Scoped viewport continuity capture/restore to the active directory and cleared stale presentation-only selection/focus carryover on true folder-scope transitions.

## HOTFIX203 / Phase 4P.9DE
- Added a generation-based Details refresh/sort commit barrier so refresh + selection/focus replay only commit after the active model/sort state settles for the current directory scope.
- Published Details directory-scope, anchor-path, and lead-path state from `MainController` into the refresh coordinator for same-directory refresh and rename-resort continuity.
- Isolated header preset/context-menu actions to the clicked column snapshot for each menu instance and reset ephemeral header/dropdown state on Details model churn.
- Added a final stable-pulse Details refresh after commit to reduce transient wrong-row paint during fast refresh + resort bursts.

- HOTFIX204 compile fix: removed an erroneous primitive dereference in `DetailsViewRefreshCoordinator` selection replay so the project compiles again.

## HOTFIX208 / Phase 4P.9DJ
- Corrected archive placeholder resource routing in `IconLoader` so `IconType.ARCHIVE` resolves to the packaged `compressed-*.png` resources instead of a non-existent `archive-*.png` path.
- Expanded archive identity overrides to cover `kind:archive` plus `zip`, `7z`, `rar`, `tar`, `gz`, `bz2`, `xz`, and `zst` extension identities so compressed files consistently show the packaged compressed icon while thumbnails are pending or unavailable.

## HOTFIX210 / Phase 4P.9DL
- Kept the startup/default explorer view aligned to Details and set the View command-bar button to show the active view icon, starting with the Details icon on first render and updating as the user switches modes.
- Added a null-safe primary-selection fallback so top-chrome and context-menu state refreshes no longer throw when the lazy Details surface has not mounted yet.


## HOTFIX211 / Phase 4P.9DM
- Kept the command-bar View label visible next to the active view icon by rebuilding the structured View button content during icon refresh instead of replacing it with an icon-only graphic.
- Updated the structured View button icon builder so the visible View button continues to track the current active view mode while preserving the label text.


## HOTFIX212 / Phase 4P.9DN
- Backed out the H209 lazy Details mount / startup placeholder path and restored eager Details initialization plus eager `configureTable()` so the Details surface is ready on the original startup path again.
- Kept the H210/H211 View-button behavior: the command-bar View button still shows the active view icon with the visible `View` label, and the null-safe `getPrimarySelection()` guard remains in place.

## HOTFIX213 / Phase 4P.9DO
- Coalesced selection-driven command-bar, file-ops, and background-context-menu refresh work behind a short FX debounce and shared explorer-command-state snapshots so Details selection churn does not repeatedly recompute shell state during scroll/viewport movement.
- Added no-op short-circuiting for explorer command-state refreshes and lightweight chrome hitch logging so slow top-chrome or selection-command recomputations are easier to spot in future traces.
- Stabilized the command-bar View button refresh path by caching the active view icon/label signature and skipping redundant structured-button rebuilds when the visible View presentation is unchanged.

## HOTFIX214 / Phase 4P.9DP
- Updated the View flyout styling so glyph-based View-menu icons, View-menu row labels, and the Show submenu label/chevron all render in white for stronger contrast and consistent menu presentation.
- Kept the existing View button active-icon tracking behavior unchanged; this hotfix is CSS-only and does not alter Java view-selection logic.

## HOTFIX215 / Phase 4P.9DQ
- Fixed the real View-menu contrast issue by tinting the actual image-based View graphics to solid white at render time instead of only changing text color.
- Applied the white graphic treatment to the command-bar View button active-view icon, all image-backed View-mode rows, pane-toggle rows, and the Navigation pane entry in the Show submenu so menu graphics now match the white label styling.

## HOTFIX216 / Phase 4P.9DR
- Finished the white-icon treatment by routing the structured command-bar View button active-view raster icon through the same white alpha-mask path used by the View flyout graphics.
- Kept the visible `View` label next to the active icon unchanged while making the top-level View button icon visually match the now-white View menu icons.

## HOTFIX218 / Phase 4P.9DT — Sort Menu Two-Tone Icon Rendering with Blue Accent Preservation
- applied the View-menu two-tone white/blue-accent icon treatment to the structured Sort command-bar icon
- preserved the blue accent pixels from `toolbar.sort.png` while recoloring dark/neutral pixels to white
- no behavioral changes to sort actions or sort menu population

## HOTFIX219 / Phase 4P.9DU — Non-Details View Cell Text/Tooltip Restoration and Shared Icon Pipeline Isolation
- restored explicit filename text rendering for non-Details icon/list/tile/content view cells by reasserting label text-fill and text-column width constraints in the shared icon-tile builder
- replaced the fragile icon-view hover popup wiring with per-tile JavaFX `Tooltip` installation so non-Details views recover reliable hover metadata without affecting Details-row tooltip behavior
- hardened virtual icon/list cells to clear stale text state and stay in `GRAPHIC_ONLY` mode during reuse so shared menu/icon rendering changes do not leak into file-item cells

## HOTFIX220 / Phase 4P.9DV — Non-Details Tooltip Text Visibility, Cell-Reuse Tooltip Rebinding, and Tooltip CSS Isolation
- replaced the plain-text non-Details tooltip render path with an explicit rich tooltip label/graphic so hover metadata stays visible instead of collapsing to a black rectangle
- rebound tooltip text on every show/hover refresh and added a tagged-path fallback for reused virtual cells that transiently produce blank tooltip content
- tightened explorer tooltip CSS selectors so tooltip content styling is isolated from recent menu/icon tint work

## HOTFIX221 / Phase 4P.9DW — Compact Tooltip Metrics, Fast Details Hover, and Context Menu Auto-Dismiss Stabilization
- restored a compact text-first tooltip path for non-Details icon/list/tile/content surfaces so hover popups no longer inflate to oversized dark panels
- reduced the Details metadata popup hover delay from 650 ms to 180 ms for faster Explorer-like hover response in Details view
- deferred file-ops and background context-menu show calls to the next FX pulse to stabilize right-click popup lifetime and avoid immediate auto-dismiss

## HOTFIX222 / Phase 4P.9DX — Tooltip Type Line for Files and Non-Details Tooltip Text Render Fix
- added a conditional `Type:` line to Explorer tooltip metadata for files when a concrete file type string is available, while omitting that line for directories
- replaced the fragile non-Details tooltip text-only render path with an explicit compact label graphic so other views stop collapsing to black rectangles
- tightened tooltip content styling so the inner tooltip container stays transparent and compact across icon/list/tile/content surfaces
## HOTFIX223 / Phase 4P.9DY — Directory Tooltip Size Suppression, Fast First Hover, and Standard Tooltip Render Restoration
- removed the `Size:` line from directory tooltips while keeping file `Type:` / `Size:` metadata when a concrete value is available
- restored non-Details icon/list/tile/content surfaces to a standard JavaFX text-tooltip render path so hover popups no longer collapse into black rectangles
- reduced both the non-Details tooltip show delay and the Details metadata-popup delay for faster first-hover response after a view receives focus

## HOTFIX224 / Phase 4P.9DZ — Square Selection Surfaces, Tooltip Path Removal, Non-Details Tooltip Render Repair, and Details Metrics Alignment
- removed `Path:` from Explorer tooltip text and kept only the compact name / type / size / date metadata lines that apply to the item
- replaced the fragile non-Details tooltip text path with an explicit compact label graphic so icon/list/tile/content hover popups stop collapsing into black squares
- converted icon/tile selection surfaces and Details row selection surfaces to square corners, tightened Details row spacing to a 2 px gap, and raised the default Details Name column width to 375 px

## HOTFIX225 / Phase 4P.9EA — Shared Multi-Selection Ownership Preservation, Directory Double-Click Restore, and Details Icon Margin Parity
- routed Details-row primary click selection, icon-surface single selection, and icon-surface toggle selection through the shared `applyExplorerPathSelection(...)` path so Ctrl/Shift multi-selection is no longer collapsed by post-gesture presentation refreshes
- restored directory activation in Details view by adding an explicit TableView double-click fallback for row-content clicks that should navigate into a folder
- restored the standard non-Details text-tooltip path to avoid black-square hover regressions and tightened the Details Name-column cell padding to 5 px for icon-margin parity

## HOTFIX226 / Phase 4P.9EB — Details Right Container Margin Correction and Name-Column Padding Revert
- reverted the H225 right-side Name-column padding tightening so the Details Name cell keeps a 5 px left inset for the icon lane while restoring the standard 12 px trailing padding
- added a 5 px right inset to the Details view shell so the last visible Details column sits exactly 5 px from the Details container edge

## HOTFIX229 / Phase 4P.9EE — Shared Popup Tooltip Routing, Persistent Folder Menus, and Multi-Select Stabilization
- routed icon/list/tile/content file-item hover metadata through the controller-owned Explorer popup path so non-Details tooltips no longer collapse into black squares
- preserved the pre-gesture Explorer selection snapshot for Ctrl/Shift operations and replayed committed selection across follow-up FX pulses so multi-select no longer collapses back to a single item
- made Explorer file and folder popup menus persist until an outside click, focus loss, or Escape instead of dropping immediately after the opening gesture

## HOTFIX230 / Phase 4P.9EF — Details Marquee Selection, Persistent Item Menus, Tree Leaf Indent Tuning, and Inline Rename Focus Guarding
- changed Details-view marquee drag to maintain a presentation-only preview selection during the gesture and commit the final path set on mouse release so marquee multi-select can complete reliably
- added an opening-gesture dismissal guard to Explorer item context menus so right-click menus stay open instead of immediately collapsing
- reduced navigation-tree leaf disclosure padding by 2 px so non-directory items sit two pixels closer to the folder alignment target
- added initial focus-commit guards to Explorer inline-rename editors in tree and file surfaces so rename fields do not pop down immediately after opening


## HOTFIX231 / Phase 4P.9EG — Shared Selection Persistence and Tree Leaf Indent Fine-Tuning
- persisted committed Ctrl/Shift and marquee selections across additional FX pulses and replayed them whenever downstream control churn attempted to collapse selection back to a single item
- routed Details marquee completion through the same shared stabilization path used by additive and range selection so marquee selection no longer appears to work briefly and then revert
- taught selection-presentation refreshes in Details and icon surfaces to respect the stabilized path set while the gesture settles, eliminating visible snap-back
- reduced navigation-tree leaf disclosure padding by 1 additional pixel so non-directory items sit one pixel closer to the requested alignment target

## HOTFIX233 / Phase 4P.9EI — Deferred Selection Stabilization Reapply and Tree Branch Indent Correction
- deferred selection-stabilization reapply work until after JavaFX selection-model notifications complete so Ctrl/Shift multi-select and marquee selection no longer snap back to a single row or throw during metadata-driven table churn
- wrapped Details-table selection listeners in an explicit model-notification guard so stabilization logic updates presentation immediately but replays concrete TableView selection only on the next safe FX pulse
- reduced navigation-tree branch disclosure padding by 2 px so expandable folder rows sit 2 pixels closer to the left and match the requested screenshot alignment more closely


## HOTFIX234 / Phase 4P.9EJ — File Context Menu Restore and Selection Presentation Hold
- prevented Explorer context-menu selection preparation from re-entering `TableView.clearSelection()` during active selection-model notifications by deferring the concrete selection replay to the next safe FX pulse
- preserved the visible Explorer multi-selection presentation while the file-operations context menu is open so right-clicking a selected group no longer drops the selection rectangle
- unified Details-row secondary-click selection prep with the shared path-based context-menu helper so file-item context menus open reliably again


## HOTFIX235 / Phase 4P.9EK — Three-Region Workspace Shell and Lazy Inspector Host
- replaced the primary workspace `SplitPane` shell with a three-region horizontal `HBox` shell so Tree, File View, and Inspector now live as sibling regions instead of nested split items
- converted the right-side auxiliary region into a collapsed-by-default inspector host that expands only when Details, Preview, or Operations is requested
- added explicit drag resizers for the navigation pane and inspector host and updated responsive viewport sizing to measure the new shell geometry


## HOTFIX237 / Phase 4P.9EM — Icon View Workspace Fill Regression Fix
- bound modular file-view roots to the shared `viewHost` width and height so icon, list, tiles, and content views fill the full file workspace again instead of collapsing to their computed size within the three-region shell
- aligned loaded view roots to the top-left and gave Region-based roots unbounded max size so the middle workspace remains dominant across non-Details view switches
- kept `ScrollPane`-based icon surfaces fit-to-width and fit-to-height so the visible viewport tracks shell geometry during relayout


## HOTFIX238 / Phase 4P.9EN — File View Item Context Menu Restore
- restored item right-click context menus across file views by scheduling item menu opening from the secondary-press selection-prep path instead of relying on a later `ContextMenuRequested` event that could be lost after item-surface refreshes
- suppressed follow-on background context-menu events for the same gesture so file and folder item menus are not replaced by the file-view background menu while the selection model is being restabilized
- kept tree-view behavior unchanged and preserved multi-selection presentation while the item menu is open
