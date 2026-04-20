# HOTFIX230 / Phase 4P.9EF — Details Marquee Selection, Persistent Item Menus, Tree Leaf Indent Tuning, and Inline Rename Focus Guarding

This hotfix continues from the H229 baseline and addresses four new interaction regressions:

1. Details-view marquee selection now keeps a dedicated preview selection set while the drag is in progress and only commits the final multi-selection back into the shared table selection model on release. This prevents live TableView selection churn from breaking the marquee gesture.
2. Explorer item context menus now ignore the opening gesture for a short guarded interval when they are first shown, so right-click menus remain open until the user clicks away, presses Escape, or the window loses focus.
3. Tree-view leaf items now reserve 2 fewer pixels in the disclosure lane than expandable directory rows, reducing the indent for non-directory items without disturbing folder alignment.
4. Inline rename editors in both the tree and file surfaces now arm an initial focus-commit guard. If the opening gesture or menu-dismiss choreography tries to steal focus immediately, the editor restores focus instead of committing/cancelling right away.

Updated files are listed in `CHANGED_FILES.txt`.
