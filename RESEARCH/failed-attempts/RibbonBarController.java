package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;

public class RibbonBarController {

    @FXML private ToolBar ribbonBar;

    /* FILE GROUP */
    @FXML private Button btnNewFolder;
    @FXML private Button btnDelete;

    /* CLIPBOARD GROUP */
    @FXML private Button btnCopy;
    @FXML private Button btnPaste;
    @FXML private Button btnCut;

    /* VIEW GROUP */
    @FXML private Button btnViewDetails;
    @FXML private Button btnViewTiles;
    @FXML private Button btnViewContent;

    /* NAVIGATION GROUP */
    @FXML private Button btnUp;

    /* THEME SWITCH GROUP */
    @FXML private MenuItem menuLight;
    @FXML private MenuItem menuDark;
    @FXML private MenuItem menuGlassy;

    private CssModeManager cssModeManager;

    @FXML
    public void initialize() {

        // Scene might not be available immediately;
        ribbonBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                cssModeManager = new CssModeManager(newScene);
            }
        });

        /* Theme switching */
        menuLight.setOnAction(e -> safeApply(CssModeManager.Mode.LIGHT));
        menuDark.setOnAction(e -> safeApply(CssModeManager.Mode.DARK));
        menuGlassy.setOnAction(e -> safeApply(CssModeManager.Mode.GLASSY));

        /* FILE GROUP ACTIONS */
        btnNewFolder.setOnAction(e -> fireCommand("new-folder"));
        btnDelete.setOnAction(e -> fireCommand("delete"));

        /* CLIPBOARD GROUP */
        btnCopy.setOnAction(e -> fireCommand("copy"));
        btnPaste.setOnAction(e -> fireCommand("paste"));
        btnCut.setOnAction(e -> fireCommand("cut"));

        /* VIEW GROUP */
        btnViewDetails.setOnAction(e -> fireCommand("view-details"));
        btnViewTiles.setOnAction(e -> fireCommand("view-tiles"));
        btnViewContent.setOnAction(e -> fireCommand("view-content"));

        /* NAVIGATION */
        btnUp.setOnAction(e -> fireCommand("nav-up"));
    }

    /** Safe wrapper so we don't crash early if scene not initialized. */
    private void safeApply(CssModeManager.Mode mode) {
        if (cssModeManager != null)
            cssModeManager.apply(mode);
    }

    /** EventBus hook (your actual bus implementation integrates here). */
    private void fireCommand(String command) {
        System.out.println("Ribbon: fireCommand -> " + command);

        // Replace with your actual event bus:
        // EventBus.getDefault().publish(new RibbonCommandEvent(command));
    }
}
