// package com.fileexplorer.ui;

// import javafx.fxml.FXML;
// import javafx.scene.control.CheckBox;
// import javafx.scene.control.ToggleButton;

// /**
 // * Controller for the RibbonBar. Methods bridge UI actions to MainController via MainControllerAccessor.
 // * Many methods call MainController wrapper methods (copy/move/delete/undo/refresh/new folder etc.).
 // */
// public class RibbonBarController {
    @FXML private javafx.scene.control.Button undoButton;
    @FXML private javafx.scene.control.Button redoButton;

    public void initializeUndoRedoButtons(HistoryManager historyManager) {
        undoButton.setOnAction(e -> {
            historyManager.undo();
        });
        redoButton.setOnAction(e -> {
            historyManager.redo();
        });
        // Bind button disable states to history availability
        undoButton.disableProperty().bind(historyManager.canUndoProperty().not());
        redoButton.disableProperty().bind(historyManager.canRedoProperty().not());
    }
    @FXML private javafx.scene.control.Menu fileMenu;
    @FXML private javafx.scene.control.Menu editMenu;
    @FXML private javafx.scene.control.Menu viewMenu;
    @FXML private javafx.scene.control.Menu helpMenu;

    public void initializeMenus(MainController controller) {
        // File Menu
        javafx.scene.control.MenuItem newFolder = new javafx.scene.control.MenuItem("New Folder");
        newFolder.setOnAction(e -> controller.createNewFolder());
        javafx.scene.control.MenuItem open = new javafx.scene.control.MenuItem("Open");
        open.setOnAction(e -> controller.openSelectedFiles());
        javafx.scene.control.MenuItem properties = new javafx.scene.control.MenuItem("Properties");
        properties.setOnAction(e -> controller.showPropertiesDialog());
        fileMenu.getItems().addAll(newFolder, open, properties);

        // Edit Menu
        javafx.scene.control.MenuItem cut = new javafx.scene.control.MenuItem("Cut");
        cut.setOnAction(e -> controller.cutSelectedFiles());
        javafx.scene.control.MenuItem copy = new javafx.scene.control.MenuItem("Copy");
        copy.setOnAction(e -> controller.copySelectedFilesWithProgress(controller.getCurrentFolder()));
        javafx.scene.control.MenuItem paste = new javafx.scene.control.MenuItem("Paste");
        paste.setOnAction(e -> controller.pasteFiles());
        javafx.scene.control.MenuItem delete = new javafx.scene.control.MenuItem("Delete");
        delete.setOnAction(e -> controller.deleteSelectedFilesWithUndo());
        editMenu.getItems().addAll(cut, copy, paste, delete);

        // View Menu
        javafx.scene.control.CheckMenuItem showPreview = new javafx.scene.control.CheckMenuItem("Show Preview Pane");
        showPreview.setSelected(true);
        showPreview.setOnAction(e -> controller.togglePreviewPane(showPreview.isSelected()));
        javafx.scene.control.CheckMenuItem showDetails = new javafx.scene.control.CheckMenuItem("Details Pane");
        showDetails.setSelected(true);
        showDetails.setOnAction(e -> controller.toggleDetailsPane(showDetails.isSelected()));
        viewMenu.getItems().addAll(showPreview, showDetails);

        // Help Menu
        javafx.scene.control.MenuItem about = new javafx.scene.control.MenuItem("About");
        about.setOnAction(e -> controller.showAboutDialog());
        helpMenu.getItems().addAll(about);
    }
    @FXML private javafx.scene.control.ToggleButton toggleMetadataBtn;
    @FXML private javafx.scene.control.ChoiceBox<String> thumbnailSizeChoice;

    public void initializePreviewPaneControls(PreviewPaneController previewPane) {
        toggleMetadataBtn.setOnAction(e -> previewPane.toggleMetadataVisibility(toggleMetadataBtn.isSelected()));
        thumbnailSizeChoice.getItems().addAll("Small", "Medium", "Large");
        thumbnailSizeChoice.setValue("Medium");
        thumbnailSizeChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            int size = 64; if ("Small".equals(newVal)) size = 32; else if ("Medium".equals(newVal)) size = 64; else if ("Large".equals(newVal)) size = 128;
            previewPane.setThumbnailSize(size);
        });
    }

    // @FXML private ToggleButton toggleNavPane;
    // @FXML private ToggleButton togglePreview;
    // @FXML private ToggleButton toggleDetails;
    // @FXML private CheckBox chkExtensions;
    // @FXML private CheckBox chkHidden;

    // private MainController main() {
        // return MainControllerAccessor.get();
    // }

    HOME tab handlers
    // @FXML public void onHomePaste() { /* implement if needed */ }
    // @FXML public void onHomeCut() { /* implement if needed */ }
    // @FXML public void onHomeCopy() { /* implement if needed */ }
    // @FXML public void onHomePasteShortcut() { /* implement if needed */ }
    // @FXML public void onHomeNewFolder() { if (main() != null) main().onNewFolder(); }
    // @FXML public void onHomeRename() { /* if needed: main().renameSelected() */ }
    // @FXML public void onHomeOpen() { /* open selected */ }
    // @FXML public void onHomeOpenWith() { /* implement choose app */ }
    // @FXML public void onHomeSelectAll() { /* main().selectAll() */ }
    // @FXML public void onHomeInvertSelection() { /* main().invertSelection() */ }
    // @FXML public void onHomeProperties() { /* main().showPropertiesForSelected() */ }
    // @FXML public void onHomeCompressZip() { /* main().compressSelected() */ }
    // @FXML public void onHomeBurn() { /* main().burnSelected() */ }
    // @FXML public void onHomeCreateShortcut() { /* main().createShortcut() */ }

    SHARE tab handlers
    // @FXML public void onShareShare() { /* main().shareSelected() */ }
    // @FXML public void onShareEmail() { /* main().emailSelected() */ }
    // @FXML public void onShareZip() { /* main().zipSelected() */ }
    // @FXML public void onShareBurn() { /* main().burnSelected() */ }
    // @FXML public void onShareMapNetworkDrive() { /* main().mapNetworkDrive() */ }

    VIEW tab handlers
    // @FXML public void onViewToggleNavigationPane() {
        // if (main() != null) main().toggleNavigationPane(toggleNavPane.isSelected());
    // }
    // @FXML public void onViewTogglePreviewPane() {
        // if (main() != null) main().togglePreviewPane(togglePreview.isSelected());
    // }
    // @FXML public void onViewToggleDetails() {
        // if (main() != null) main().toggleDetailsPane(toggleDetails.isSelected());
    // }
    // @FXML public void onViewLayoutExtraLarge() { if (main() != null) main().setLayout("EXTRA_LARGE"); }
    // @FXML public void onViewLayoutLarge() { if (main() != null) main().setLayout("LARGE"); }
    // @FXML public void onViewLayoutMedium() { if (main() != null) main().setLayout("MEDIUM"); }
    // @FXML public void onViewLayoutSmall() { if (main() != null) main().setLayout("SMALL"); }
    // @FXML public void onViewLayoutList() { if (main() != null) main().setLayout("LIST"); }
    // @FXML public void onViewLayoutDetails() { if (main() != null) main().setLayout("DETAILS"); }

    // @FXML public void onViewToggleExtensions() { if (main() != null) main().toggleShowExtensions(chkExtensions.isSelected()); }
    // @FXML public void onViewToggleHidden() { if (main() != null) main().toggleShowHidden(chkHidden.isSelected()); }

    // @FXML public void onViewSortByName() { if (main() != null) main().sortBy("name"); }
    // @FXML public void onViewSortByDate() { if (main() != null) main().sortBy("date"); }
    // @FXML public void onViewSortByType() { if (main() != null) main().sortBy("type"); }
    // @FXML public void onViewSortBySize() { if (main() != null) main().sortBy("size"); }

    // @FXML public void onViewGroupByNone() { if (main() != null) main().groupBy("none"); }
    // @FXML public void onViewGroupByDate() { if (main() != null) main().groupBy("date"); }

    MANAGE handlers
    // @FXML public void onManageFormat() { /* main().formatDrive() */ }
    // @FXML public void onManageOptimize() { /* main().optimizeDrive() */ }
    // @FXML public void onManageEject() { /* main().ejectDrive() */ }

    Ribbon common actions that exist elsewhere (use MainController wrappers)
    // @FXML public void onRibbonCopyWithChooser() { if (main() != null) main().onRibbonCopyWithChooser(); }
    // @FXML public void onRibbonMoveWithChooser() { if (main() != null) main().onRibbonMoveWithChooser(); }
    // @FXML public void onRibbonDeleteWithProgress() { if (main() != null) main().onRibbonDeleteWithProgress(); }
    // @FXML public void onRibbonCancelOperation() { if (main() != null) main().onRibbonCancelOperation(); }
// }
package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ToggleButton;

/**
 * Controller for the RibbonBar. Methods bridge UI actions to MainController via MainControllerAccessor.
 * Many methods call MainController wrapper methods (copy/move/delete/undo/refresh/new folder etc.).
 */
public class RibbonBarController {
    @FXML private javafx.scene.control.Button undoButton;
    @FXML private javafx.scene.control.Button redoButton;

    public void initializeUndoRedoButtons(HistoryManager historyManager) {
        undoButton.setOnAction(e -> {
            historyManager.undo();
        });
        redoButton.setOnAction(e -> {
            historyManager.redo();
        });
        // Bind button disable states to history availability
        undoButton.disableProperty().bind(historyManager.canUndoProperty().not());
        redoButton.disableProperty().bind(historyManager.canRedoProperty().not());
    }
    @FXML private javafx.scene.control.Menu fileMenu;
    @FXML private javafx.scene.control.Menu editMenu;
    @FXML private javafx.scene.control.Menu viewMenu;
    @FXML private javafx.scene.control.Menu helpMenu;

    public void initializeMenus(MainController controller) {
        // File Menu
        javafx.scene.control.MenuItem newFolder = new javafx.scene.control.MenuItem("New Folder");
        newFolder.setOnAction(e -> controller.createNewFolder());
        javafx.scene.control.MenuItem open = new javafx.scene.control.MenuItem("Open");
        open.setOnAction(e -> controller.openSelectedFiles());
        javafx.scene.control.MenuItem properties = new javafx.scene.control.MenuItem("Properties");
        properties.setOnAction(e -> controller.showPropertiesDialog());
        fileMenu.getItems().addAll(newFolder, open, properties);

        // Edit Menu
        javafx.scene.control.MenuItem cut = new javafx.scene.control.MenuItem("Cut");
        cut.setOnAction(e -> controller.cutSelectedFiles());
        javafx.scene.control.MenuItem copy = new javafx.scene.control.MenuItem("Copy");
        copy.setOnAction(e -> controller.copySelectedFilesWithProgress(controller.getCurrentFolder()));
        javafx.scene.control.MenuItem paste = new javafx.scene.control.MenuItem("Paste");
        paste.setOnAction(e -> controller.pasteFiles());
        javafx.scene.control.MenuItem delete = new javafx.scene.control.MenuItem("Delete");
        delete.setOnAction(e -> controller.deleteSelectedFilesWithUndo());
        editMenu.getItems().addAll(cut, copy, paste, delete);

        // View Menu
        javafx.scene.control.CheckMenuItem showPreview = new javafx.scene.control.CheckMenuItem("Show Preview Pane");
        showPreview.setSelected(true);
        showPreview.setOnAction(e -> controller.togglePreviewPane(showPreview.isSelected()));
        javafx.scene.control.CheckMenuItem showDetails = new javafx.scene.control.CheckMenuItem("Details Pane");
        showDetails.setSelected(true);
        showDetails.setOnAction(e -> controller.toggleDetailsPane(showDetails.isSelected()));
        viewMenu.getItems().addAll(showPreview, showDetails);

        // Help Menu
        javafx.scene.control.MenuItem about = new javafx.scene.control.MenuItem("About");
        about.setOnAction(e -> controller.showAboutDialog());
        helpMenu.getItems().addAll(about);
    }
    @FXML private javafx.scene.control.ToggleButton toggleMetadataBtn;
    @FXML private javafx.scene.control.ChoiceBox<String> thumbnailSizeChoice;

    public void initializePreviewPaneControls(PreviewPaneController previewPane) {
        toggleMetadataBtn.setOnAction(e -> previewPane.toggleMetadataVisibility(toggleMetadataBtn.isSelected()));
        thumbnailSizeChoice.getItems().addAll("Small", "Medium", "Large");
        thumbnailSizeChoice.setValue("Medium");
        thumbnailSizeChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            int size = 64; if ("Small".equals(newVal)) size = 32; else if ("Medium".equals(newVal)) size = 64; else if ("Large".equals(newVal)) size = 128;
            previewPane.setThumbnailSize(size);
        });
    }

    @FXML private ToggleButton toggleNavPane;
    @FXML private ToggleButton togglePreview;
    @FXML private ToggleButton toggleDetails;
    @FXML private CheckBox chkExtensions;
    @FXML private CheckBox chkHidden;

    private MainController main() {
        return MainControllerAccessor.get();
    }

    // HOME tab handlers
    @FXML public void onHomePaste() { /* implement if needed */ }
    @FXML public void onHomeCut() { /* implement if needed */ }
    @FXML public void onHomeCopy() { /* implement if needed */ }
    @FXML public void onHomePasteShortcut() { /* implement if needed */ }
    @FXML public void onHomeNewFolder() { if (main() != null) main().onNewFolder(); }
    @FXML public void onHomeRename() { /* if needed: main().renameSelected() */ }
    @FXML public void onHomeOpen() { /* open selected */ }
    @FXML public void onHomeOpenWith() { /* implement choose app */ }
    @FXML public void onHomeSelectAll() { /* main().selectAll() */ }
    @FXML public void onHomeInvertSelection() { /* main().invertSelection() */ }
    @FXML public void onHomeProperties() { /* main().showPropertiesForSelected() */ }
    @FXML public void onHomeCompressZip() { /* main().compressSelected() */ }
    @FXML public void onHomeBurn() { /* main().burnSelected() */ }
    @FXML public void onHomeCreateShortcut() { /* main().createShortcut() */ }

    // SHARE tab handlers
    @FXML public void onShareShare() { /* main().shareSelected() */ }
    @FXML public void onShareEmail() { /* main().emailSelected() */ }
    @FXML public void onShareZip() { /* main().zipSelected() */ }
    @FXML public void onShareBurn() { /* main().burnSelected() */ }
    @FXML public void onShareMapNetworkDrive() { /* main().mapNetworkDrive() */ }

    // VIEW tab handlers
    @FXML public void onViewToggleNavigationPane() {
        if (main() != null) main().toggleNavigationPane(toggleNavPane.isSelected());
    }
    @FXML public void onViewTogglePreviewPane() {
        if (main() != null) main().togglePreviewPane(togglePreview.isSelected());
    }
    @FXML public void onViewToggleDetails() {
        if (main() != null) main().toggleDetailsPane(toggleDetails.isSelected());
    }
    @FXML public void onViewLayoutExtraLarge() { if (main() != null) main().setLayout("EXTRA_LARGE"); }
    @FXML public void onViewLayoutLarge() { if (main() != null) main().setLayout("LARGE"); }
    @FXML public void onViewLayoutMedium() { if (main() != null) main().setLayout("MEDIUM"); }
    @FXML public void onViewLayoutSmall() { if (main() != null) main().setLayout("SMALL"); }
    @FXML public void onViewLayoutList() { if (main() != null) main().setLayout("LIST"); }
    @FXML public void onViewLayoutDetails() { if (main() != null) main().setLayout("DETAILS"); }

    @FXML public void onViewToggleExtensions() { if (main() != null) main().toggleShowExtensions(chkExtensions.isSelected()); }
    @FXML public void onViewToggleHidden() { if (main() != null) main().toggleShowHidden(chkHidden.isSelected()); }

    @FXML public void onViewSortByName() { if (main() != null) main().sortBy("name"); }
    @FXML public void onViewSortByDate() { if (main() != null) main().sortBy("date"); }
    @FXML public void onViewSortByType() { if (main() != null) main().sortBy("type"); }
    @FXML public void onViewSortBySize() { if (main() != null) main().sortBy("size"); }

    @FXML public void onViewGroupByNone() { if (main() != null) main().groupBy("none"); }
    @FXML public void onViewGroupByDate() { if (main() != null) main().groupBy("date"); }

    // MANAGE handlers
    @FXML public void onManageFormat() { /* main().formatDrive() */ }
    @FXML public void onManageOptimize() { /* main().optimizeDrive() */ }
    @FXML public void onManageEject() { /* main().ejectDrive() */ }

    // Ribbon common actions that exist elsewhere (use MainController wrappers)
    @FXML public void onRibbonCopyWithChooser() { if (main() != null) main().onRibbonCopyWithChooser(); }
    @FXML public void onRibbonMoveWithChooser() { if (main() != null) main().onRibbonMoveWithChooser(); }
    @FXML public void onRibbonDeleteWithProgress() { if (main() != null) main().onRibbonDeleteWithProgress(); }
    @FXML public void onRibbonCancelOperation() { if (main() != null) main().onRibbonCancelOperation(); }
}
