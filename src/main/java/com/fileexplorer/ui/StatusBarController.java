// package com.fileexplorer.ui;

// import javafx.fxml.FXML;
// import javafx.scene.control.Label;
// import javafx.scene.control.Tab;
// import javafx.scene.control.TabPane;

// import java.io.File;
// import java.util.List;

// public class StatusBarController {

    // @FXML private Label selectionCountLabel;
    // @FXML private Label totalSizeLabel;
    // @FXML private Label activeTabLabel;

    // private TabPane tabPane;

    // public void setTabPane(TabPane tabPane) {
        // this.tabPane = tabPane;
        // tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateTabInfo());
    // }

    // public void updateSelection(List<File> selectedFiles) {
        // int count = selectedFiles.size();
        // long totalSize = selectedFiles.stream().mapToLong(f -> f.isFile() ? f.length() : 0).sum();
        // selectionCountLabel.setText("Selected: " + count);
        // totalSizeLabel.setText("Size: " + humanReadableByteCountBin(totalSize));
    // }

    // public void updateTabInfo() {
        // if (tabPane != null) {
            // Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
            // if (selectedTab != null) {
                // activeTabLabel.setText("Tab: " + selectedTab.getText());
            // } else {
                // activeTabLabel.setText("Tab: None");
            // }
        // }
    // }

    // private String humanReadableByteCountBin(long bytes) {
        // long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        // if (absB < 1024) return bytes + " B";
        // long value = absB;
        // String[] units = {"KiB","MiB","GiB","TiB","PiB","EiB"};
        // int i = 0;
        // for (; i < units.length && value >= 1024; i++) {
            // value /= 1024;
        // }
        // return String.format("%.1f %s", bytes / Math.pow(1024, i), units[i-1]);
    // }
// }
package com.explorer.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class StatusBarController {

    @FXML private Label leftStatus;
    @FXML private Label rightStatus;

    public void setLeftMessage(String msg) {
        leftStatus.setText(msg);
    }

    public void setRightMessage(String msg) {
        rightStatus.setText(msg);
    }
}