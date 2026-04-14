# HOTFIX201 / Phase 4P.9DC

## Details View Navigation-Epoch Isolation, Stale Async Icon Result Suppression, and Sort/Refresh Paint Stability Parity

This hotfix hardens the Details Name-column binding after HOTFIX200.

### What changed
- Added a controller-owned Details async binding epoch so navigation, explicit Details refresh passes, and sort transitions invalidate older icon publishes before they can repaint reused rows.
- Reworked `MainController.ExplorerNameTableCell` to listen to `TableRow.itemProperty()` and rebind against the current `FileItem`, path, display text, and resolved identity instead of trusting only the cell text snapshot.
- Tightened async icon and thumbnail commit checks so late completions must still match the active Details epoch, row, `FileItem`, path, display text, and resolved shell identity.
- Preserved thumbnail wins over later async icon completions and upgraded the fallback placeholder path so folders fall back to folder identity instead of generic file identity.

### Expected result
- Rapid folder navigation no longer lets stale Details icon work publish into the newly visible folder surface.
- Sorts and refreshes remain stable even when rows are virtualized/reused and the visible text string stays the same.
- Details rows keep the correct placeholder/icon/thumbnail chain during refresh churn, rename churn, and late async completion races.
