package com.fileexplorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import java.util.function.Consumer;

public class ToolbarController {

    @FXML private Button newFolderBtn;
    @FXML private Button deleteBtn;
    @FXML private Button refreshBtn;
    @FXML private Button backBtn;
    @FXML private Button forwardBtn;

    private Consumer<Void> onNewFolder;
    private Consumer<Void> onDelete;
    private Consumer<Void> onRefresh;
    private Consumer<Void> onBack;
    private Consumer<Void> onForward;

    public void setOnNewFolder(Consumer<Void> c) { this.onNewFolder = c; }
    public void setOnDelete(Consumer<Void> c) { this.onDelete = c; }
    public void setOnRefresh(Consumer<Void> c) { this.onRefresh = c; }
    public void setOnBack(Consumer<Void> c) { this.onBack = c; }
    public void setOnForward(Consumer<Void> c) { this.onForward = c; }

    @FXML
    private void initialize() {
        newFolderBtn.setOnAction(e -> { if (onNewFolder != null) onNewFolder.accept(null); });
        deleteBtn.setOnAction(e -> { if (onDelete != null) onDelete.accept(null); });
        refreshBtn.setOnAction(e -> { if (onRefresh != null) onRefresh.accept(null); });
        backBtn.setOnAction(e -> { if (onBack != null) onBack.accept(null); });
        forwardBtn.setOnAction(e -> { if (onForward != null) onForward.accept(null); });
    }
}