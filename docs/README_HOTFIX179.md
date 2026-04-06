# HOTFIX179 / Phase 4P.9CG — JBIG2 PDF Thumbnail Decode Support and Malformed-PDF Fallback Parity

## What changed
- Added `org.apache.pdfbox:jbig2-imageio` to the Maven build so PDFBox can decode JBIG2 image streams embedded inside PDFs.
- Moved the PDF thumbnail path in `AsyncThumbnailService` off `thumbnails4j`'s `PDFThumbnailer` and onto a direct PDFBox render path.
- Added JBIG2 capability probing, JBIG2 preflight scanning, malformed-PDF one-shot warnings, and a quieter JUL default for PDF thumbnail library loggers.

## Why
The previous H178 build still routed PDF thumbnails through `co.elastic.thumbnails4j.pdf.PDFThumbnailer`. That path logged noisy `SEVERE` failures before control returned to File Explorer when a PDF depended on a missing JBIG2 decoder or when the input PDF was truncated/malformed.

## Result
- JBIG2-backed PDFs can render thumbnails once Maven restores the new dependency.
- Malformed or truncated PDFs now degrade to the normal file icon with a single File Explorer JUL warning instead of repeated library stack traces.
- JPEG2000/JPX fallback remains in place from HOTFIX177.
