# HOTFIX217 / Phase 4P.9DS

This hotfix replaces the flat all-white View icon masking with a two-tone render path that preserves the original blue accent pixels.

## Scope
- preserve the blue accent regions in the View flyout raster icons
- recolor the neutral/dark portions of the same icons to white
- apply the same two-tone treatment to the command-bar View button active-view icon
- keep the visible `View` label and the existing H215/H216 menu styling intact

## Implementation
H216 used a single white alpha mask for the View button and flyout raster icons. That made the icons visible on the dark menu surface, but it also flattened the original Explorer-style blue accent pixels to white.

This hotfix replaces that image transformation with a two-tone conversion pass. During icon load, blue-accent pixels are detected and preserved, while the non-blue raster content is rewritten to white using the source alpha channel. The result keeps the white-on-dark readability improvement without losing the blue accent treatment in pane and navigation icons.
