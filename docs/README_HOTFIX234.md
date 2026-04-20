# HOTFIX234 / Phase 4P.9EJ — File Context Menu Restore and Selection Presentation Hold

This hotfix continues from the H233 baseline and applies the reported follow-up fixes:

1. File-item right-click now reliably opens the Explorer file-operations context menu again. Context-menu selection preparation no longer mutates the JavaFX `TableView` selection model while the control is inside selection-list change notifications. The requested selection is painted immediately and replayed onto the concrete selection model on the next safe FX pulse instead.
2. Right-clicking an already multi-selected item now preserves the visible Explorer multi-selection rectangle while the file-operations context menu is open. The menu show/hide lifecycle now holds the current selection presentation and restores the normal refresh path after the menu closes.
3. Details-row secondary-click handling now routes through the same safe path-based context-menu selection helper used by icon surfaces, keeping row and tile behavior aligned.

Updated files are listed in `CHANGED_FILES.txt`.
