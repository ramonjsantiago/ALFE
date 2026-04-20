# HOTFIX233 / Phase 4P.9EI — Deferred Selection Stabilization Reapply and Tree Branch Indent Correction

This hotfix continues from the H232 baseline and applies the reported follow-up fixes:

1. Selection stabilization in Details and icon-backed file views no longer re-enters the JavaFX `TableView` selection model while the control is still broadcasting selection-list change notifications. Instead, the presentation snapshot is updated immediately and the concrete `TableView` selection replay is deferred to the next safe FX pulse. This addresses the crash/snap-back path reported in the attached crash snapshot and keeps Ctrl/Shift multi-select plus marquee selection from collapsing back to a single item.
2. Navigation-tree expandable folder rows now sit 2 pixels closer to the left by tightening branch disclosure-node padding in both the shared tree stylesheet and the navigation-pane parity override.

Updated files are listed in `CHANGED_FILES.txt`.
