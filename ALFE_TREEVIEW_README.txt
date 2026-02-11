ALFE Tree Metrics Update (Single Overlay ZIP)
============================================

This ZIP contains *full-file* replacements and the supporting stylesheet to enforce the measured
Explorer-like TreeView constraints across BOTH trees:

Measured constraints (from your screenshot)
------------------------------------------
- Icon slot: 16x16
- Left offset to icon: ~24px (achieved via 8px cell padding + 16px -fx-indent)
- Icon-to-text gap: 5px
- Row pitch: 24px
- Font: Segoe UI, 12px (via CSS), with near-white text on dark background

Files replaced/added
--------------------
1) src/main/java/com/fileexplorer/ui/MainController.java
   - folderTree cell factory now enforces padding/gap
   - IconPathTreeCell now uses fixed 16x16 icons
   - ensureTreeViewStyleClass loads /css/explorer_tree.css and adds style class "explorer-tree"

2) src/main/java/com/fileexplorer/controllers/NavigationPaneController.java
   - FIX: does NOT extend LazyFileTreeCell (it is final)
   - navTreeView cell factory enforces padding/gap/icon sizing
   - loads /css/explorer_tree.css

3) src/main/resources/com/fileexplorer/ui/MainLayout.fxml
   - folderTree styleClass now includes "explorer-tree"
   - folderTree fixedCellSize set to 24.0

4) src/main/resources/css/explorer_tree.css (NEW)
   - Explorer-like visuals + -fx-indent:16px + font settings

How to apply
------------
Unzip this archive on top of your project root (the folder that contains pom.xml),
allowing it to overwrite existing files.

Then run:
  mvn -q -DskipTests=false test
  mvn javafx:run

If you still see old behavior, you likely have multiple copies of the project and are compiling the wrong one.
Confirm the compiled source path matches the folder you unzipped into.
