# H187 PDF Default Icon Update

Applied the attached PNG as the dedicated default resource icon for `*.pdf` files.

## Updated behavior
- `ext:pdf` now resolves to dedicated packaged PNG resources.
- The PDF file icon is shown while thumbnail generation is pending or when thumbnail rendering falls back to the file-type icon.

## Resource paths
- `src/main/resources/com/fileexplorer/ui/icons/light/pdf-16.png` through `pdf-256.png`
- `src/main/resources/com/fileexplorer/ui/icons/dark/pdf-16.png` through `pdf-256.png`
- mirrored copies under `target/classes/com/fileexplorer/ui/icons/light/` and `dark/`
