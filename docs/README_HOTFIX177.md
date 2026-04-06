# HOTFIX177 / Phase 4P.9CE — JPEG2000 PDF Thumbnail Decode Support and Graceful Fallback Parity

## Scope
This corrective hotfix addresses PDF thumbnails that fail when a PDF contains JPEG2000/JPX image streams.

## What changed
- Added explicit Maven dependency: `com.github.jai-imageio:jai-imageio-jpeg2000:${jai.imageio.version}`
- Added JPEG2000 ImageIO reader capability probing in `AsyncThumbnailService`
- Added bounded `/JPXDecode` PDF preflight detection so JPX-backed PDFs can short-circuit to icon fallback when the decoder is unavailable
- Added one-shot warning logging for missing runtime JPEG2000 support

## Files changed
- `pom.xml`
- `src/main/java/com/fileexplorer/service/icon/AsyncThumbnailService.java`

## Verification checklist
1. Refresh Maven dependencies and rebuild the project.
2. Open a PDF that previously logged `Cannot read JPEG2000 image`.
3. Confirm either:
   - the PDF now renders a thumbnail successfully, or
   - the app falls back quietly to the PDF/file icon without repeated PDFBox stack traces.
