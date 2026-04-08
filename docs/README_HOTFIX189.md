# HOTFIX189 / Phase 4P.9CQ

Viewport-Priority PDF Thumbnail Scheduling, Scroll-Cancel Hygiene, and First-Visible Commit Parity.

## What changed
- Reworked the shared Details-table `VisibleThumbnailManager` pump to issue visible thumbnail requests in real viewport order instead of `WeakHashMap` iteration order.
- Prioritized top-of-viewport cells first so visible PDF thumbnail work starts at the first realized rows and then proceeds downward.
- Added a visibility guard on thumbnail completion so late async results do not commit into cells that have already scrolled out of view.
- Kept best-effort cancellation for non-visible registrations active during scroll churn.

## Primary files
- `src/main/java/com/fileexplorer/ui/table/VisibleThumbnailManager.java`
- `CHANGELOG.md`
- `PHASE_LABEL.txt`
- `CHANGED_FILES.txt`
