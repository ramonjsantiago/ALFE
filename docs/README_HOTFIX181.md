# HOTFIX181 / Phase 4P.9CI — PDF Worker Quarantine, Retry-After-Change Invalidation, and Document-Lane Fairness Parity

This project is the **fully populated HOTFIX181 integration** produced from the attached **H180** baseline zip.

## Integrated changes

### PDF worker quarantine
- Split document thumbnail execution into two dedicated lanes inside `AsyncThumbnailService`:
  - `thumb-doc-pdf` for PDF thumbnails
  - `thumb-doc-office` for Word / Excel / PowerPoint thumbnails
- PDF thumbnail work still uses the existing timeout/cancel(false) safety posture from H180, but any slow or timed-out PDF render is now quarantined away from non-PDF document thumbnails.
- Added document-lane diagnostics to the thumbnail debug snapshot/logger so Office-lane and PDF-lane queue/active counts can be inspected independently.

### Retry-after-change invalidation
- Reworked PDF timeout cooldown tracking to key suppression by **file identity** (`lastModifiedMs` + `fileSizeBytes`) instead of path alone.
- If a PDF is replaced or edited, the prior timeout cooldown is invalidated immediately and the next request is allowed to retry.
- Updated one-shot PDF timeout / interrupt / oversize / malformed diagnostics to include file-identity-aware signatures so changed PDFs can emit fresh diagnostics when needed.

### Stale-completion quarantine
- Upgraded async thumbnail request keys to include the file identity snapshot alongside the path and size bucket.
- Added a final identity check before thumbnail completion/cache write so late completions for an outdated file version are discarded instead of repainting or caching stale output.
- Added counters for:
  - `staleFileVersionDrops`
  - `pdfCooldownInvalidations`

### Tests
Added JUnit coverage for:
- cooldown invalidation after PDF content/identity changes
- dedicated executor routing for PDF vs Office-family document thumbnails

## Source areas changed
- `src/main/java/com/fileexplorer/service/icon/AsyncThumbnailService.java`
- `src/test/java/com/fileexplorer/service/icon/AsyncThumbnailServiceHotfix181Test.java`
- `PHASE_LABEL.txt`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `docs/README_HOTFIX181.md`
- `PHASE4P_9CI_PDF_WORKER_QUARANTINE_RETRY_AFTER_CHANGE_INVALIDATION_AND_DOCUMENT_LANE_FAIRNESS_PARITY_NOTES.txt`

## Verification note
The runtime used to prepare this delivery did not include Maven or JDK 25 / JavaFX 25 for a full project build. The H181 changes were integrated source-side against the provided H180 baseline and checked carefully for consistency within the modified project files.
