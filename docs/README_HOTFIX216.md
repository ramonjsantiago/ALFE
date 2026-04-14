# HOTFIX216 / Phase 4P.9DR

This hotfix finishes the white-icon treatment for the command-bar View button itself.

## Scope
- apply the same white alpha-mask treatment to the visible View button active-view icon
- keep the existing white View flyout menu icon treatment intact
- keep the visible `View` label next to the active icon unchanged

## Implementation
H215 correctly white-tinted the View flyout menu graphics, but the structured command-bar View button still built its active icon through the plain raster icon path. This hotfix adds a white-mask-capable raster helper for structured command-bar icons and routes the View button active-view icon through that path so the button icon matches the flyout icon treatment.
