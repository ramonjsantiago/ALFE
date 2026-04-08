# H187 CSV File Icon Update

This update adds the provided CSV/Excel-style icon as the packaged default icon for `*.csv` files while a thumbnail is pending.

## Changes

- Added `csv-16.png`, `csv-24.png`, `csv-32.png`, `csv-48.png`, `csv-64.png`, `csv-96.png`, `csv-128.png`, and `csv-256.png` under both:
  - `src/main/resources/com/fileexplorer/ui/icons/light/`
  - `src/main/resources/com/fileexplorer/ui/icons/dark/`
- Updated `IconLoader` so `ext:csv` resolves to the dedicated packaged CSV icon resources.

## Result

`*.csv` files now use the supplied resource icon until any thumbnail pipeline produces a richer preview.
