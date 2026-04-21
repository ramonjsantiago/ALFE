# HOTFIX241 / Phase 4P.9EQ — Virtual File View Item Context Menu Restore

This hotfix targets the file-view item context-menu regression that still reproduced only in larger folders where icon-family views switch from the incremental FlowPane surfaces to the virtualized ListView-based grid/list surfaces.

Changes made:
- added explicit secondary-click and context-menu handlers directly on the virtual icon grid cells and virtual icon list cells
- resolved the clicked item path from the virtual cell itself instead of depending only on the shared host pick-target chain
- added a screen-bounds child hit test for virtual grid rows so right-clicking a visible tile in a multi-item virtual row can still resolve the exact item
- kept the existing small-folder flow-surface item menu behavior unchanged
