# HOTFIX214 / Phase 4P.9DP

## Summary
This hotfix updates the View flyout styling so the View-menu icon glyphs and menu text render in white for stronger contrast and visual parity.

## Changes
- forced white text fill for glyph-based View-menu icons via `.label.view-menu-icon`
- forced white text fill for View-menu row labels via `.view-menu-text`
- forced white text fill for the View submenu label and white submenu chevron color
- left PNG-based view-mode icons unchanged; only text/glyph-rendered View-menu visuals were restyled

## Files changed
- `src/main/resources/com/fileexplorer/ui/css/app-main.css`
