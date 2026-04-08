# HOTFIX190 / Phase 4P.9CR

This pass starts from HOTFIX189 and focuses only on Extra large icon view metrics.

## Included
- Extra large icon preset moved to 256px.
- Extra large icon slot fixed to 256px x 256px.
- Intrinsic-size rendering for 256px icon assets in Extra large view (no ImageView fit scaling on the 256px path).
- Controller warmup/request clamps widened so 256px assets can be requested by hover prefetch and viewport realization.

## Notes
- I did not change adaptive PDF downgrade behavior for pathological documents; those safeguards still exist in `AsyncThumbnailService` for recovery scenarios.
- This package is otherwise the same fully populated project tree as HOTFIX189.
