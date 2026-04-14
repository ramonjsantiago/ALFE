# HOTFIX202 / Phase 4P.9DD

## Details View Directory-Scope Publish Fencing, Same-Name Row-Reuse Finalization, and Selection/Focus Continuity Parity

This hotfix tightens the Details-view navigation/refresh path after HOTFIX201.

### What changed
- Added a controller-owned visible directory scope so Details name-cell async icon and thumbnail publishes are fenced not only by the binding epoch, but also by the currently active directory surface.
- Finalized same-name row-reuse handling by carrying the bound Details directory scope through rebind/equivalence checks and rejecting late publishes that no longer belong to the active folder surface.
- Scoped viewport continuity capture/restore to the active directory, so cross-folder navigation no longer tries to replay stale selection or focus state from the previously visible folder.
- Cleared presentation-only selection/focus state on true directory-scope transitions when no same-scope continuity should be preserved.

### Expected result
- Details rows no longer accept stale async icon/thumbnail results after a real folder-scope transition, even when row text and placeholder family happen to match.
- Cross-folder navigation stops carrying transient Details selection/focus paint into the next folder surface.
- Same-folder refresh/hydration still preserves scroll, selection, and focus continuity where appropriate.
