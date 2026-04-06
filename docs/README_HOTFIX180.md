# HOTFIX180 / Phase 4P.9CH — PDF In-Memory Render Isolation, Timeout Cooldown, and Noise-Collapse Parity

This hotfix hardens PDF thumbnail rendering against interrupt-driven PDFBox channel failures and repeated library logger spam.

## What changed

- PDF thumbnails are now rendered from an in-memory byte array instead of a live file channel.
- PDF document thumbnail timeouts no longer interrupt an already-running task.
- Timed-out PDFs enter a short cooldown window before another thumbnail attempt is allowed.
- High-noise internal PDFBox JUL loggers are forced to `OFF`; the service emits concise JUL warnings instead.
- New diagnostics were added for PDF timeout cooldown and in-memory render limits.

## New runtime properties

- `fileexplorer.thumb.pdf.inMemoryMaxBytes`
  - Default: `67108864` (64 MiB)
- `fileexplorer.thumb.pdf.timeoutCooldownMs`
  - Default: `30000` (30 seconds)

## Expected behavior

- Problematic PDFs now fall back to the generic file-type icon more quietly.
- Repeated scroll/repaint pressure should no longer flood the console with `ClosedByInterruptException`, `ClosedChannelException`, `Can't dereference COSObject`, `Missing XObject`, or `shading ... does not exist` noise from PDFBox internals.
- The application remains on `java.util.logging` as the single logging pipeline.
