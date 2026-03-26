## HOTFIX130 / Phase 4P.9AJ — Icon View Marquee Gesture Ownership, Release Collapse Suppression, and Stable Multi-Select Commit

- stopped icon-view marquee live preview from mutating the shared TableView selection model on every drag pulse; icon marquee now previews through dedicated presentation state and commits the multi-select only once on mouse release
- added marquee gesture ownership guards so trailing icon click/clicked handlers cannot reclaim the gesture and collapse the committed marquee result back to a single lead item
- suppressed hover-class churn while marquee ownership is active so drag-selection paint remains stable instead of flickering as the pointer crosses tiles

## HOTFIX118 / Phase 4P.9X — Marquee and Ctrl Selection Parity

- added Explorer-style icon-view selection handling so Extra large, Large, Medium, Small, List, Tiles, and Content views support single-select, Ctrl-click toggle select, and Shift-range extension through the shared file selection model
- added a view-host marquee selection overlay that hit-tests visible icon tiles during click-drag selection and applies the live result back to the file selection state
- stopped virtual icon-grid row selection leakage by consuming tile mouse events at the icon level and disabling whole-row pick bounds for the virtual grid/list cells

## HOTFIX117 / Phase 4P.9W — Icon View Live Reflow on Resize

- pushed the live visible icon viewport width directly into the FlowPane icon host instead of only updating wrap length
- added a follow-up responsive pass for non-virtual icon views so SplitPane / ScrollPane layout settles before the final icon-flow width is applied
- preserved the existing virtual icon-grid row regrouping behavior for larger folders while making small FlowPane-based icon folders reflow more reliably during grow/shrink cycles

## HOTFIX112 / Phase 4P.9R — Structured Command Bar Chevron Render Finalization
- converted New, Sort, and View into structured graphic-only MenuButtons so the visible layout is explicitly icon + label + down chevron
- replaced chevron visibility dependence on the native MenuButton arrow lane with a dedicated SVG chevron slot inside the command-bar content tree
- preserved the existing flyout behavior and full-button hit target while restoring the missing down chevrons

## HOTFIX108 / Phase 4P.9N — Toolbar Menu Chevron Slot and Baseline Alignment Parity
- reworked the command-bar New, Sort, and View MenuButtons into a fixed structure of icon + label + dedicated trailing chevron slot
- moved the visible chevron ownership into the button content instead of relying on the skin arrow-button lane
- collapsed the default MenuButton arrow-button width for those structured controls so the chevron stays aligned and visible
- preserved the existing flyout hierarchy, full-button hit target, and command-bar hover/pressed states

## HOTFIX107 / Phase 4P.9N — Toolbar Flyout Chevron Visibility Fix
- fixed the missing down chevrons on the toolbar New, Sort, and View MenuButtons
- replaced the open chevron shapes with closed painted chevron paths so the glyph renders reliably in JavaFX
- preserved the existing command-bar metrics and flyout hierarchy from HOTFIX106


## HOTFIX105 / Phase 4P.9L — Top Nav Icon 48px Center Spacing
- adjusted the top navigation toolbar icon rhythm to 48px center-to-center using a 40px button well with 8px inter-button spacing
- kept the existing Back, Forward, Up, and Refresh toolbar behavior, icons, and tooltips intact while retuning only the spacing metrics
- applied the spacing override in the late Explorer override stylesheet so the change stays low-risk and easy to tune further


## HOTFIX102 / Phase 4P.9I — Tab Folder Icons and Vertical Offset
- Added folder icons to the left of the Home and Current folder tab text.
- Current folder tab now refreshes its folder icon alongside tab text updates.
- Shifted tab buttons 3px downward to match the requested vertical placement.

## HOTFIX102 — Phase 4P.9H Tab Folder Icons and Vertical Offset
- removed the vertical gap between the tab strip and the top toolbar surface
- aligned the bottom edge of the tabs with the top edge of the toolbar stack
- kept the existing HOTFIX99/HOTFIX100 toolbar icon-only and tooltip styling intact

## HOTFIX97 / Phase 4P.9D — Table View Right Edge Container Parity Compile Fix
- Corrected the HOTFIX96 compile break by replacing the missing syncDetailsLastVisibleColumnClasses() call with syncDetailsVisibleColumnRoleClasses().
- Corrected the invalid Node.getWidth() usage in responsive split-item width resolution by switching to bounds-based width probes that compile for javafx.scene.Node.
- Preserved the HOTFIX96 right-edge container parity behavior while making the package compile-clean again.

## HOTFIX92 / Phase 4P.8 — Icon View live resize anchor and metrics parity

- hardened icon-view width resolution so responsive column counts follow the current visible host width instead of latching onto stale larger widths during later window resizes
- bound the virtual icon views to the live view-host size and extended resize listeners to the Scene and Window so repeated resize cycles keep driving relayout
- preserved the virtual icon-grid vertical scroll anchor across responsive row-count rebuilds to reduce jumpiness while the icon views reflow
- kept the existing HOTFIX91 responsive relayout behavior for first-open icon views while extending it to later continuous resize passes


## HOTFIX89 / Phase 4P.5 — View Menu icon-slot restore

- backed out the HOTFIX88 native View menu mark-gutter parity changes because the result removed the dedicated icon slot the user preferred
- restored the prior View menu implementation and spacing model from the HOTFIX87 baseline
- kept the HOTFIX87 startup correction and the thumbnail/cache pipeline unchanged while undoing only the HOTFIX88 View menu experiment


## HOTFIX88 / Phase 4P.4 — View Menu native mark-gutter parity

- replaced the View menu CustomMenuItem rows with native `RadioMenuItem` / `CheckMenuItem` / `MenuItem` rows so JavaFX owns the mark column, submenu arrow, and popup row semantics again
- locked the View menu mark gutter, graphic gutter, and label spacing in CSS to line up the text starts and submenu indentation against the supplied Windows reference screenshot
- kept the existing view-mode, pane, and Show submenu behaviors wired through `MainController`, but removed the extra `Command Log...` row from View > Show so the submenu structure matches the supplied reference more closely


## HOTFIX87 / Phase 4P.3 — Single-Pass Styled Startup

- removed the startup black pre-show placeholder scene and now build the lightweight shell root before `stage.show()`
- attached the critical stylesheet layer before the first visible frame so startup begins on a styled dark Scene instead of flashing through unstyled intermediate frames
- kept the single-Scene shell-to-main-root handoff, but limited post-load CSS work to the deferred/noncritical stylesheet layer
- set the startup Scene fill to a matching theme color to reduce default white-frame exposure during root swaps


## HOTFIX86 / Phase 4P.2 — View Menu Icon Rollback

- backed out the HOTFIX85 View menu vector icon implementation and restored the prior View menu appearance from the HOTFIX84 baseline
- removed the ViewMenuIcons helper and reverted the MainController and CSS changes that introduced the generated icon blocks
- kept the thumbnail pipeline and prior HOTFIX84 functionality unchanged while undoing only the View menu icon experiment


## HOTFIX85 / Phase 4P.1 — View Menu Vector Icons

- replaced the current View menu view-mode glyph labels with code-generated JavaFX vector icons for Extra large icons, Large icons, Medium icons, Small icons, List, Details, Tiles, and Content
- wired the icons directly into the existing View menu row implementation in MainController so the current radio-button interaction model stays unchanged
- added view-menu vector icon CSS so the new icons remain monochrome, theme-aware, and crisp without bundled image assets


## HOTFIX84 / Phase 4O.12 — Disk Cache Touch-On-Read and Cold Aging

- added optional touch-on-read refresh for persisted thumbnail payload files so age-based disk-cache pruning reflects recent reuse rather than only write time
- excluded manifest and temporary files from touch-on-read refresh and added a minimum touch interval to avoid excessive metadata churn on hot entries
- extended thumbnail diagnostics and debug output with disk-cache touch write / skip / fail counters and touch-related settings


## HOTFIX83 / Phase 4O.11 — Disk cache metadata protection and temp hygiene

- hardened `AsyncThumbnailService.pruneDiskCache()` so it protects `thumbcache-manifest.properties` from age/size pruning and only applies cache eviction rules to persisted thumbnail payload files
- added stale temporary cache-file cleanup for interrupted `.tmp` writes plus empty hash-directory cleanup after payload deletions
- extended thumbnail diagnostics with temp-file and empty-directory prune counters and improved the persistent-cache summary so it reports payload files separately from temporary files
- added `-Dfileexplorer.thumb.diskCache.tmpMaxAgeHours=<hours>` for bounded cleanup of abandoned temp files


## HOTFIX79 / Phase 4O.7 — Safe document disk cache

- carefully reintroduced persistent thumbnail caching as a read-through / write-through disk cache layered behind the existing in-memory cache
- scoped disk persistence to successful document thumbnails by default so the slower Office/PDF providers benefit most without disturbing the faster native/imageio paths
- keyed cache entries by absolute path, extension, size bucket, last-modified time, file size, and provider so changes naturally invalidate without suppressing fallback icons
- kept all disk-cache reads and writes best effort / fail-open: cache errors fall back to live generation and then to the normal file-type icon path
- added bounded age/size pruning plus debug counters and system-property switches for enablement, scope, max bytes, max age, prune cadence, and cache location


## HOTFIX78 / Phase 4O.6 — Current-folder thumbnail warmup

- added a bounded post-navigation thumbnail warmup pass in `MainController` so only the current folder's visible/top thumbnail candidates are warmed after folder-open settles
- routed warmup through `AsyncThumbnailService.warm(...)` so it reuses the existing in-memory cache, coalescing, throttling, generation cancellation, and fail-open behavior instead of introducing a new cache layer
- canceled/restarted warmup on directory navigation and exposed tuning switches for warmup enablement, delay, and max warmed items


## HOTFIX77 / Phase 4O.5 — In-memory thumbnail reuse and viewport-aware cancellation

- updated `AsyncThumbnailService` to reuse in-memory thumbnails through size buckets and file-change-aware cache validation using absolute path, size bucket, last-modified time, and file size
- added stronger request coalescing so repeated requests for the same file and size bucket share one pending/running decode instead of re-queuing duplicate work
- preserved nearest-larger thumbnail reuse for smaller views and added start-window throttling to smooth out scroll bursts without reintroducing disk persistence risk
- updated `VisibleThumbnailManager` to cancel offscreen table-cell thumbnail interest immediately and clear cancelled/null futures so visible cells can retry cleanly
- raised preview thumbnail requests to `USER_ACTION` priority and visible tile/list icon thumbnail requests to `VISIBLE` priority


## HOTFIX76 / Phase 4O.4 — Thumbnail hardening and capability gating

- hardened `AsyncThumbnailService` so thumbnail failures fail open and preserve the normal file-type icon fallback path
- added provider routing for JavaFX-native images, thumbnails4j document thumbnails, and ImageIO-backed formats
- added per-format document thumbnail gates via system properties for PDF, Word, Excel, and PPTX
- isolated document thumbnail rendering behind a timeout-guarded executor to prevent long-running document work from blocking the main thumbnail pipeline indefinitely
- added thumbnail diagnostics counters for requested, queued, rendered, failed, fallback-used, cancelled, and document-timeout events
- extended thumbnail debug/perf output with provider counts and gate-state visibility
- hardened decode-queue trimming so dropped queued tasks are cleaned up instead of leaving stale in-flight entries behind


## HOTFIX75 / Phase 4O.3 rollback — Restore thumbnail rendering

- backed out the HOTFIX74 persistent thumbnail cache changes after they caused thumbnails to stop appearing in file views
- restored `AsyncThumbnailService` to the known-good HOTFIX73 behavior
- preserved the thumbnails4j document thumbnail support and JAI Image I/O support from HOTFIX72/HOTFIX73


## HOTFIX56 / Phase 4N.3 — Show Menu Indentation Parity
- adjusted View > Show menu row indentation to better align with the other View menu items

## HOTFIX57 / Phase 4N.4 — Show Menu Final Alignment
- Nudged the View > Show row a few more pixels to the right for alignment parity.
- Increased submenu graphic-container left padding from 6px to 10px.

## HOTFIX58 / Phase 4N.5 — Show Menu Indentation Final Tweak
- Nudged the View > Show row a few more pixels to the right for closer alignment with the other View menu entries.
- Increased the submenu graphic-container left padding from 10px to 14px.

## HOTFIX59 / Phase 4N.6 — Show Menu Indentation Nudge
- Nudged the View > Show row a few more pixels to the right for closer alignment with the other View menu entries.
- Increased the submenu graphic-container left padding from 14px to 18px.

## HOTFIX60 / Phase 4N.7 — Show Menu Placeholder Icon Alignment
- Replaced the View > Show visible glyph with a placeholder icon region for alignment parity.
- Reset the submenu graphic-container left padding back to 6px.

## HOTFIX61 / Phase 4L.6 — Details Header Drop-down Alignment and Sort Preset Placeholders
- Added a right-edge primary-click drop-down trigger zone on Details column headers.
- Added aligned sort preset rows at the top of each header drop-down: 0-9, A-Z, a-z.
- Each preset row renders as checkbox, icon, and label in that order.
- Clicking a preset applies ascending/descending primary sort on the clicked column.

## HOTFIX62 / Phase 4L.7 — Details Header Menu Hot-zone and Preset Semantics
- Added a right-edge Details header hot-zone visual state and hand cursor for the drop-down trigger area.
- Preset rows now use more meaningful placeholder icons: #, A, and a.
- Preserved the aligned checkbox, icon, and string row structure.

## HOTFIX63 / Phase 4L.8 — Details Header Compact Preset Drop-down
- Right-edge primary-click header menu now opens a compact preset-only drop-down.
- The compact drop-down contains only the aligned preset rows: 0-9, A-Z, a-z.
- Right-click/context-menu behavior continues to open the full Details header menu.


## HOTFIX64 / Phase 4L.8 — Repost Package
- Reposted the fully populated project zip for the compact preset-only Details header drop-down build.
- No source changes relative to HOTFIX63; this package preserves the HOTFIX63 implementation and makes the artifact available again.


## HOTFIX65 / Phase 4L.9 — Details Header Visible-Only Drop-down
- Right-edge primary-click Details header drop-down now lists only the menu items that are currently checked on / visible.
- Preserved the Explorer-style checkbox, icon, and label alignment in the compact drop-down.
- Selecting a visible entry from the compact drop-down applies primary ascending sort to that Details column.

## HOTFIX66 / Phase 4L.10 — Details Header Hover Chevron
- Replaced the Table View Details right-edge menu indicator with a small down chevron.
- The chevron is only visible while the pointer is over the Details header menu hot-zone.
- The chevron hides automatically when the pointer leaves the hot-zone or when the drop-down opens.
- Preserved the HOTFIX65 visible-only compact drop-down contents and behavior.

## HOTFIX67 / Phase 4L.11 — Details Header Range Preset Menu
- Updated the Table View Details right-edge compact drop-down to use four aligned preset rows.
- Each row preserves the Explorer-style checkbox, icon, and string order.
- The compact preset strings are now: 0-9, A-H, I-P, Q-Z.
- Updated the full Details header context menu preset rows to match the same four-entry range set.
- Preserved the HOTFIX66 hover chevron behavior.


## HOTFIX68 / Phase 4L.12 — Column-Specific Details Header Preset Menus
- Extended the Table View Details right-edge compact preset drop-down to all Details columns with column-specific menu sets.
- Size-family columns now show: Empty (0 KB), Tiny (0 - 16 KB), Small (16 KB - 1 MB), Medium (1 - 128 MB), Large (128 MB - 1 GB), Unspecified.
- Date-family columns now show: A long time ago, Earlier this year, Last month, Earlier this month, Last week, Yesterday, Today.
- Other text-like Details columns continue to use the aligned Explorer-style range presets: 0-9, A-H, I-P, Q-Z.
- Preserved the checkbox, icon, string row structure and the HOTFIX66 hover-only right-edge chevron trigger.

## HOTFIX69 / Phase 4L.13 — Outside-Click Dismissal For Header Menus
- Added scene-level outside-click dismissal for the Details header right-edge preset menus.
- Added the same outside-click dismissal behavior for the full Details header context menu so both header menu entry points collapse consistently.
- Preserved the HOTFIX68 column-specific preset menu contents and the HOTFIX66 hover-only right-edge chevron trigger.

## HOTFIX71 / Phase 4L.15 — Header Context Anchored Separators
- Updated the Table header right-click popup builder to use anchored trailing separators for the visible Details section.
- Preserved the separator after Size All Columns to Fit.
- Added explicit separator support after Title when Title is present in the popup.
- When Title is not currently visible, the separator is attached to the last visible Details row so More... remains visually separated from the Details list.


## HOTFIX72 / Phase 4O.1 — thumbnails4j Lazy Office/PDF Thumbnails
- Added the Elastic thumbnails4j library using the current 1.2.0 Maven Central release.
- Extended thumbnail candidate detection to include .doc, .docx, .pdf, .pptx, .xls, and .xlsx.
- Routed those formats through the existing AsyncThumbnailService so they use the same lazy, gated, debounced, priority-aware thumbnail construction path as the current image thumbnails.
- Preserved the placeholder-first UX: file-type icons appear immediately and upgrade asynchronously to generated thumbnails when decoding completes.
- Added a runtime SLF4J NOP binding to suppress missing-binder warnings from thumbnails4j dependencies.

## HOTFIX73 / Phase 4O.2 — JAI Image I/O Tools
- Added the standalone JAI Image I/O Tools dependency using com.github.jai-imageio:jai-imageio-core 1.4.0.
- Preserved the current lazy thumbnail construction path; ImageIO SPI scanning now picks up both TwelveMonkeys and JAI providers at AsyncThumbnailService startup.
- Extended the ImageIO fallback thumbnail candidate list to include .pcx so the newly added provider can participate in the existing thumbnail pipeline.
- Updated ImageIO-related implementation notes/comments to reflect the combined TwelveMonkeys + JAI provider stack.


## HOTFIX80 / Phase 4O.8 — Disk Cache Hygiene and Startup Prune
- Added a deferred startup prune for the safe thumbnail disk cache so stale cache files can be cleaned shortly after thumbnailing is enabled, even before the next write-triggered prune cycle.
- Added self-healing disk-cache reads: zero-length or decode-failed cached PNGs are deleted on read failure instead of being retried forever.
- Added a best-effort clearPersistentDiskCache() utility for future reset/troubleshooting flows without disturbing the normal fail-open thumbnail pipeline.
- Extended thumbnail diagnostics with disk-cache prune runs, startup-prune runs, and corrupt-cache deletion counters.


## HOTFIX81 / Phase 4O.9 — Thumbnail Cache Reset and Diagnostics
- Added an optional one-shot startup reset for the thumbnail disk cache via `-Dfileexplorer.thumb.diskCache.clearOnStartup=true|false` to make stale-cache troubleshooting safer and easier.
- Consolidated startup cache maintenance so optional reset and startup prune run through one deferred maintenance pass after thumbnails are enabled.
- Added richer thumbnail diagnostics snapshots with cache directory, effective disk-cache settings, file count, byte size, and accumulated counters.
- Included thumbnail diagnostics in generated support bundle zips for easier field triage.


## HOTFIX82 / Phase 4O.10 — Disk Cache Compatibility Manifest
- Added a lightweight `thumbcache-manifest.properties` file in the thumbnail disk-cache root so persisted thumbnail settings are captured alongside the cache contents.
- Added a compatibility fingerprint for cache-affecting thumbnail pipeline settings and an optional startup clear path via `-Dfileexplorer.thumb.diskCache.clearOnManifestMismatch=true|false`.
- Added manifest write, write-failure, mismatch-detected, and mismatch-clear counters to thumbnail diagnostics.
- Included the manifest contents in generated support bundle zips for easier cache triage after future thumbnail pipeline changes.

## HOTFIX90 / Phase 4P.6 — Icon View Responsive Horizontal Flow
- Adjusted icon-mode responsive layout so Extra large icons, Large icons, Medium icons, and Small icons use the full visible width of the container instead of stopping at a stale low column count.
- Added a debounced viewport-width refresh path that rebuilds the virtual icon grid when the computed items-per-row changes.
- Updated virtualized icon-grid row spacing to follow the active mode's real flow padding and gaps.
- Updated row-capacity calculation to use the active mode's actual tile width, horizontal gap, and padding for more accurate fill behavior.


## HOTFIX91 / Phase 4P.7 — Icon View Resize Relayout Fix
- Fixed the HOTFIX90 regression where grid icon views could re-open responsively once and then fall back to a stale fixed column count during later window resizes.
- Added responsive layout listeners to the actual icon-view host, virtual icon grid, and scroll container so width changes keep retriggering the row-capacity refresh path.
- Hardened width resolution so transient zero-width measurements reuse the last good viewport width instead of falling back to the old default three-column estimate.

## HOTFIX93 / Phase 4P.9 — Table View Inner Viewport Resize Relayout Fix
- Added a responsive Details/Table viewport relayout path that tracks the live inner content width instead of relying on a stale first-open measurement.
- Wired table-width refresh listeners to the table, details shell, content host, split pane, side pane, scene, and window so repeated stretch/shrink cycles keep retriggering constrained-column layout.
- Reapplied the constrained flex-last-column resize policy after split-pane, navigation-pane, and Details-pane visibility changes so the table occupies the full remaining center width when the right pane is hidden.


## HOTFIX94 / Phase 4P.9A — Table View Resize Regression Fix
- Corrected the HOTFIX93 regression that could pin Table View sizing by forcing pref widths during responsive relayout.
- Kept the responsive Table/Details relayout trigger path, but switched the refresh to layout and constrained-column policy reapplication instead of writing fixed widths.
- Reset the Details shell and TableView back to computed sizing during refresh and added a width-change guard to reduce self-triggered resize churn.


## HOTFIX95 / Phase 4P.9B — Navigation Pane Growth Lock and Metrics
- Changed navigation-pane resize behavior so horizontal window growth preserves the Tree View's current width instead of expanding it proportionally.
- Allowed the navigation Tree View to shrink down to 54px content width while preserving a constant 3px shell padding around it.
- Updated the dark navigation-pane visuals to use a #191919 Tree View background with a 1px #3A3A3A border.
- Restored navigation-pane show/hide behavior using the last known shell width instead of forcing a fixed percentage divider on reopen.


## HOTFIX97 / Phase 4P.9C — Table View Right Edge Container Parity
- Reworked responsive Table View width resolution so it follows the live view container width first, instead of latching onto stale table-shell measurements during resize churn.
- Applied the responsive width back onto the details shell and TableView so the table right edge stays aligned with the container while shrinking and growing the application window.
- Kept constrained flex-last-column behavior in the responsive refresh path so the last visible column continues to absorb remaining width instead of leaving a dead area on the right.


## HOTFIX98 / Phase 4P.9E — Dark Surface Color Alignment
- Updated the dark navigation Tree View background to #000000 while keeping the requested 1px #3A3A3A border and 3px shell padding.
- Updated dark toolbar surfaces to #2C2C2C.
- Updated custom tab buttons so unselected tabs use #202020 and the selected tab uses #2C2C2C.
- Updated dark Table View surfaces to #000000.
- Unified separator lines to #3A3A3A across toolbar, generic, and context-menu separators.

## HOTFIX99 / Phase 4P.9F — Toolbar Icon-Only and Color Parity
- Made the leading command-bar actions up to See more render as icon-only controls while keeping their current actions and flyouts.
- Moved those command-bar labels into exact-match tooltips so hover text now reflects the hidden toolbar label text directly.
- Updated dark command-bar text and glyph color to #E0DFDF, changed toolbar separator lines to #272727, and flattened the dark toolbar surface corners.



## HOTFIX100 / Phase 4P.9G — Toolbar Tooltip Visibility Fix
- corrected tooltip contrast so icon-only toolbar button tooltips are readable again
- aligned tooltip background/border colors with the dark palette
- kept HOTFIX99 toolbar icon-only layout unchanged

## HOTFIX103 / Phase 4P.9J — Restore Sort/View Toolbar Icon+Text
- Restored the toolbar **Sort** and **View** entries to render as **icon + name** instead of icon-only controls.
- Preserved the existing Sort and View flyout/dropdown menu hierarchies and actions.
- Added a labeled-command-bar style so Sort and View keep normal button sizing and left-aligned icon/text spacing while the other leading toolbar commands remain icon-only.

## HOTFIX104 / Phase 4P.9K — Toolbar New / Sort / View Icon+Text Chevron Restore
- Restored **New** on the toolbar from icon-only to **icon + text**.
- Kept the existing **New**, **Sort**, and **View** flyout menu hierarchies unchanged.
- Tuned the toolbar flyout arrow treatment so **New**, **Sort**, and **View** use a smaller right-side chevron.


## HOTFIX106 / Phase 4P.9M — Top Chrome and Command Bar Metrics Parity
- Tightened the top chrome so the tab strip, address row, and command bar read as a quieter Windows-style surface stack.
- Retuned the top navigation button wells to use flatter hover/pressed states, calmer disabled contrast, and more Windows-like glyph sizing.
- Refined command-bar metrics for icon-only actions and the New / Sort / View flyouts, including subtler open-state wells and tighter label-to-chevron spacing.

HOTFIX110 / Phase 4P.9P — Toolbar Native Menu Restore and Tab Strip Close/Plus Parity
- src/main/java/com/fileexplorer/controller/MainController.java
- src/main/resources/com/fileexplorer/ui/layout/MainLayout.fxml
- src/main/resources/com/fileexplorer/ui/css/explorer-override-everything.css
- src/main/resources/com/fileexplorer/ui/css/home-tabs-parity.css
- PHASE4P_9P_TOOLBAR_NATIVE_MENU_RESTORE_AND_TAB_STRIP_CLOSE_PLUS_PARITY_NOTES.txt


## HOTFIX111 / Phase 4P.9Q — Inline Tab Close and Plus-After-Last-Tab Parity
- Moved the visible tab close affordance into each tab so the X now sits immediately after the tab label.
- Reordered the tab strip so the + button renders directly after the last visible tab instead of being separated by the flexible spacer.
- Left the old strip-level close button hidden and unmanaged as a fallback path while the inline tab close slot handles user interaction.


## HOTFIX113 / Phase 4P.9S — Command Bar Vector Icon Restore
- Replaced the New / Sort / View toolbar glyph dependency with dedicated vector icons so those controls no longer fall back to ellipsis.
- Kept the HOTFIX112 structured command-bar layout intact: icon + label + dedicated trailing chevron slot.
- Added theme-aware vector stroke styling so the restored icons remain legible in dark and light modes.

## HOTFIX114 / Phase 4P.9T — Command Bar Icon Size Parity
- Increased the visible size of the structured vector icons used by **New**, **Sort**, and **View**.
- Widened the command-bar icon lane slightly so the larger icons remain centered and do not crowd the label.
- Increased vector stroke weight modestly for better toolbar icon legibility.

## HOTFIX115 / Phase 4P.9U — Command Bar Icon Size More Parity
- Increased the visible size of the structured vector icons used by **New**, **Sort**, and **View** again for stronger command-bar parity.
- Expanded the icon slot further so the larger icons remain centered without colliding with the labels.
- Slightly increased vector stroke weight and fallback glyph size so the icons read more clearly at normal desktop scale.

## HOTFIX119 / Phase 4P.9Y — Details View Marquee Selection Parity
- Added click-and-drag marquee selection to the **Details** view when dragging from blank file-view space.
- Marquee hit-testing now selects every intersected visible details row both logically and visually instead of leaving the UI looking single-selected.
- Preserved additive **Ctrl-drag** marquee behavior while explicitly focusing the details table so active selection paint stays stable during the drag.


## HOTFIX120 / Phase 4P.9Z — Details Marquee Multi-Select Visual and Logical Fix
- Switched details marquee selection application to an atomic multi-index selection path so every intersected row is selected in one pass.
- Added a dedicated details-row `explorer-selected` presentation state so marquee-selected rows paint correctly even while the virtualized table is mid-drag.
- Forced immediate and deferred details selection presentation refreshes after marquee updates to keep logical and visual multi-selection in sync.

## HOTFIX121 / Phase 4P.9AA — Details View Marquee Drag Activation Fix
- Allowed marquee selection in **Details** view to arm from existing rows as well as blank viewport space, so the drag can begin even when the folder fills the table.
- Excluded the details header, show/hide columns button, and scrollbars from marquee arming so header interactions still behave normally.
- Preserved normal single-click row behavior when no drag occurs, while switching to marquee selection as soon as the drag threshold is crossed.

## HOTFIX122 / Phase 4P.9AB — Details Marquee Release Selection Commit Fix
- Captured the active Details-view marquee hit set during drag and committed it again after JavaFX mouse-release processing.
- Prevented the final native row-selection pass from collapsing a completed marquee selection back to a single row.
- Kept visual and logical multi-selection aligned after release, including additive Ctrl-drag marquee selection.

## HOTFIX128 / Phase 4P.9AH — Icon View Marquee Hit-Set Commit and Tile Selection Rebind Finalization
- Added a persistent icon selection presentation set so marquee-selected icon tiles repaint from committed selection state instead of only transient drag state.
- Reapplied icon marquee selection after mouse release and forced immediate plus deferred icon-tile refresh passes to stabilize post-release paint.
- Initialized rebuilt and recycled icon tiles from the committed selection predicate so virtualization and relayout do not drop marquee-selected visuals.

## HOTFIX129 / Phase 4P.9AI — Icon View Marquee Final Commit, Invalidation, and Selection Model Unification
- Recomputed the icon marquee hit set on mouse release so the committed selection matches the final drag rectangle instead of an earlier drag sample.
- Wrapped icon-selection model updates in a presentation transaction so transient clear-and-reselect passes do not erase committed icon-tile paint.
- Refreshed icon-tile selected and hover visuals with explicit state invalidation after release to keep marquee-selected tiles painted when the pointer moves away.



## HOTFIX131 / Phase 4P.9AK — Icon View Marquee Click-Through Suppression and Virtual Cell Input Isolation
- Added a view-host `MOUSE_CLICKED` suppression path for marquee-owned icon gestures so the post-release click sequence cannot collapse a committed multi-selection to the last icon.
- Held icon marquee click suppression across additional UI pulses so release/click cleanup from JavaFX skins cannot immediately override the marquee commit.
- Installed virtual icon cell mouse-event suppression and live marquee selection delta filtering to reduce drag flicker and block ListView cell input from reasserting single-item selection during marquee completion.
