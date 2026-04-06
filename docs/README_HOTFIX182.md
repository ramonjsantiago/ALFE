# HOTFIX182 / Phase 4P.9CJ — Adaptive PDF Render Budgeting, First-Page Heuristics, and Large-Document Recovery Parity

This project is the **fully populated HOTFIX182 integration** produced from the generated **H181** baseline.

## Integrated changes

### Adaptive PDF render budgeting
- Added file-size and observed render-latency aware PDF timeout budgeting in `AsyncThumbnailService`.
- PDF thumbnail requests now compute an **adaptive timeout budget** instead of always using the fixed document timeout.
- Recent successful or slow PDF render history is retained per file identity and fed back into later budget decisions.

### First-page heuristics
- Added a PDF first-page render plan that inspects the first page dimensions and total page count before choosing the raster scale.
- Large documents, unusually large first pages, and recently slow PDFs now downshift to a smaller effective thumbnail target and a lower render-scale cap.
- These heuristics preserve the existing H181 quarantine and stale-completion protections while biasing toward faster first visible thumbnails.

### Large-document recovery parity
- Added large-document recovery fallback for PDFs that either:
  - cross the new hard large-document threshold, or
  - accumulate a timeout streak while already above the soft large-document threshold.
- Recovery mode falls back directly to the normal file-type icon instead of repeatedly retrying an obviously expensive PDF thumbnail path.
- File-identity changes automatically invalidate both cooldown and adaptive history, so editing/replacing the PDF allows a fresh attempt.

### Diagnostics and manifest updates
- Added counters for:
  - `pdfHistoryResets`
  - `pdfBudgetPlans`
  - `pdfBudgetDownshifts`
  - `pdfLargeDocFallbacks`
- Extended the disk-cache compatibility fingerprint/manifest with the new PDF adaptive-planning knobs so stale cached output is less likely to survive pipeline changes.

### Tests
Added JUnit coverage for:
- adaptive PDF timeout growth for large/slower render scenarios
- first-page heuristic downshifts for large/slow PDFs
- large-document recovery invalidation after the underlying PDF changes

## Source areas changed
- `src/main/java/com/fileexplorer/service/icon/AsyncThumbnailService.java`
- `src/test/java/com/fileexplorer/service/icon/AsyncThumbnailServiceHotfix182Test.java`
- `PHASE_LABEL.txt`
- `CHANGELOG.md`
- `CHANGED_FILES.txt`
- `docs/README_HOTFIX182.md`
- `PHASE4P_9CJ_ADAPTIVE_PDF_RENDER_BUDGETING_FIRST_PAGE_HEURISTICS_AND_LARGE_DOCUMENT_RECOVERY_PARITY_NOTES.txt`

## Verification note
The runtime used to prepare this delivery did not include Maven or JDK 25 / JavaFX 25 for a full project build. The H182 changes were integrated source-side against the H181 project and checked carefully for consistency within the modified project files.
