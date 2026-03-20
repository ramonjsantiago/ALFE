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
