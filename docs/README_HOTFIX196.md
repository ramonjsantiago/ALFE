# HOTFIX196 / Phase 4P.9CX — Placeholder Thumbnail Routing, Pending-Thumbnail Swap Stability, and Shell Icon Identity Parity

This phase is based on the H195 This PC icon baseline and focuses on making placeholder-first icon and thumbnail binding behave consistently across Explorer surfaces.

## Included

- Centralized canonical placeholder identity routing in `IconLoader.identityForPath(...)` so the tree, file views, and preview pane resolve the same shell/icon identity for the same path.
- Added `kind:` canonical identities for grouped placeholder families such as video, audio, image, archive, and text to reduce cache fragmentation while keeping dedicated overrides (`.bin`, `.iso`, `.ini`, `.reg`, `.ico`, Home, This PC, local disk, network drive) intact.
- Routed `FileMetadataService.iconIdentity(...)` through the shared `IconLoader` identity pipeline.
- Updated tree/root icon binding to use the same shell identity routing model.
- Improved preview-pane placeholder behavior so thumbnail candidates keep the correct placeholder icon visible while the real thumbnail is pending, instead of briefly going blank.
- Tightened async thumbnail swap guards in the icon/file-surface path by binding both the target path and the identity to the in-flight thumbnail request before publishing the resolved image.

## Primary updated files

- `src/main/java/com/fileexplorer/util/IconLoader.java`
- `src/main/java/com/fileexplorer/service/filesystem/FileMetadataService.java`
- `src/main/java/com/fileexplorer/ui/tree/IconPathTreeCell.java`
- `src/main/java/com/fileexplorer/controller/MainController.java`
- `docs/README_HOTFIX196.md`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`

## Notes

This container does not include the same Windows/JavaFX runtime stack as your target machine, so this package was prepared as a source-level full-project delivery.
