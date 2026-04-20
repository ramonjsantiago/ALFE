# HOTFIX231 / Phase 4P.9EG — Shared Selection Persistence and Tree Leaf Indent Fine-Tuning

This hotfix continues from the H230 baseline and addresses the remaining selection regressions plus a final tree-indent adjustment:

1. Shared Explorer selection stabilization now persists committed Ctrl/Shift and marquee path sets for multiple FX pulses and re-applies them whenever downstream control churn tries to collapse the selection back to a single item.
2. Details-view marquee selection now feeds the same stabilization path used by additive and range selection, so marquee commits no longer appear to work briefly and then revert.
3. Selection presentation refreshes in both Details and icon surfaces now honor the stabilized path set while the gesture settles, preventing visible snap-back to a single selected item.
4. Navigation-tree leaf disclosure padding is reduced by one additional pixel so non-directory items sit one pixel closer to the desired alignment target.

Updated files are listed in `CHANGED_FILES.txt`.
