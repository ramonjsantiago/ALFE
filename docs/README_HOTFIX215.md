# HOTFIX215 / Phase 4P.9DQ

This hotfix fixes the View menu icon color regression by tinting the actual image-based View menu graphics to white at render time.

## Scope
- white-tint the command-bar View button active-view icon
- white-tint image-backed View mode menu icons
- white-tint pane toggle menu icons
- white-tint the Show > Navigation pane menu icon

## Implementation
The previous hotfix only updated CSS text color, which correctly affected labels and glyph fonts but not PNG-backed menu graphics. This hotfix adds a white alpha-mask image path in `MainController` for View menu graphics so the menu icons render as solid white while preserving transparency and sizing.
