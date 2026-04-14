# HOTFIX197 / Phase 4P.9CY — Document Thumbnail Backend Routing, Failure Backoff, and Resolution Promotion Parity

This phase is based on the H196 placeholder-routing baseline and focuses on stabilizing the document thumbnail backend path for Office-family documents and PDFs.

## Included

- Centralized document thumbnail backend selection inside `AsyncThumbnailService` so `.pdf` is explicitly routed to the PDFBox render path while `.doc`, `.docx`, `.pptx`, `.xls`, and `.xlsx` are explicitly routed to the thumbnails4j lane.
- Added Office-family document failure cooldown/backoff so repeated thumbnail failures do not hammer the thumbnails4j backend on every repaint, hover, or scroll pass.
- Added low-tier/high-tier render-quality tracking for document thumbnails.
- Added first-pass low-resolution Office-family document rendering for visible large-icon requests, followed by scheduled high-tier promotion when the item is still current and visible.
- Kept disk-cache persistence scoped to fully promoted document thumbnails so low-tier preview results do not pollute the persistent cache.
- Preserved the existing PDF adaptive timeout and promotion path while aligning it with the new centralized backend-selection model.

## Primary updated files

- `src/main/java/com/fileexplorer/service/icon/AsyncThumbnailService.java`
- `docs/README_HOTFIX197.md`
- `PHASE4P_9CY_DOCUMENT_THUMBNAIL_BACKEND_ROUTING_FAILURE_BACKOFF_AND_RESOLUTION_PROMOTION_PARITY_NOTES.txt`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `PHASE_LABEL.txt`

## Notes

This package was prepared as a source-level full-project delivery in the container environment. I did not run a full Maven/JavaFX build here.


## Compile fix
- Restored missing `IconPathTreeCell` imports for `javax.swing.filechooser.FileSystemView` and `java.util.Locale`.
