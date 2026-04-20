# HOTFIX229 / Phase 4P.9EE — Shared Popup Tooltip Routing, Persistent Folder Menus, and Multi-Select Stabilization

This hotfix continues from the H226 baseline and addresses three interaction regressions:

1. Non-Details hover tooltips no longer rely on the standard JavaFX tooltip path for file-surface items. Icon, list, tile, and content views now route hover metadata through the same controller-owned popup used by the stable Details implementation, which prevents the black-square tooltip regression.
2. Explorer multi-selection is hardened across Details and icon-oriented views by snapshotting the pre-click selection set before primary gestures, using that preserved set for Ctrl/Shift range/toggle operations, and replaying the committed selection across follow-up FX pulses so platform/default control behavior cannot collapse the selection back to a single item.
3. Folder/file popup menus now remain visible until the user clicks elsewhere in the owner scene (or the window loses focus / Escape is pressed) instead of disappearing immediately after the opening gesture.

Updated files are listed in `CHANGED_FILES.txt`.
