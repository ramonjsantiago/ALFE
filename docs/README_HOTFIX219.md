# HOTFIX219 / Phase 4P.9DU

This hotfix restores non-Details file-item presentation after the recent menu/icon rendering iterations.

## Scope
- Restore visible filename text for non-Details views:
  - Extra large icons
  - Large icons
  - Medium icons
  - Small icons
  - List
  - Tiles
  - Content
- Restore reliable hover tooltips for the same non-Details views
- Keep the H217/H218 two-tone toolbar/menu icon treatment intact
- Keep Details-view behavior unchanged

## Implementation
- Reassert explicit text-fill values for the shared non-Details icon/tile/list/content label pipeline
- Reassert text-column width constraints so row-mode text remains laid out beside the icon
- Install standard JavaFX `Tooltip` objects on icon/tile roots for reliable hover metadata in non-Details views
- Harden virtual `ListCell` reuse by clearing stale text state and forcing `ContentDisplay.GRAPHIC_ONLY`

## Notes
This hotfix intentionally limits itself to the non-Details presentation pipeline and does not modify the Details table render path.
