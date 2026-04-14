# HOTFIX213 / Phase 4P.9DO

## Details View Scroll Throughput, Chrome-State Coalescing, and Safe Render-Path Stabilization

This hotfix hardens the live Details render path by coalescing selection-driven chrome refresh work and avoiding redundant View-button rebuilds.

### What changed
- Coalesced selection-driven command-bar and file-ops/context-menu refresh work behind a short FX debounce so rapid Details selection churn does not repeatedly recompute chrome state during scroll and viewport movement.
- Added cached explorer-command-state snapshots so chrome/menu refreshes short-circuit when the semantic state has not changed.
- Routed file-ops and background context-menu state synchronization through the shared snapshot so duplicate state walks do not repeat on the same pulse.
- Added lightweight chrome hitch logging hooks for top-chrome and selection-command recomputation when explicitly enabled or when refreshes exceed the configured threshold.
- Stabilized the View command-bar button refresh path by caching the active icon/label signature and skipping redundant structured-button rebuilds when the view mode presentation is unchanged.

### Intended result
- Preserve the eager Details startup path from H212.
- Reduce command-bar/context-menu churn during Details scrolling and selection presentation updates.
- Keep the View button icon+label parity without re-materializing the structured button on no-op refreshes.
