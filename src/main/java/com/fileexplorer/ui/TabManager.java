package com.fileexplorer.ui;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages tabs in a dual-pane File Explorer.
 * Each pane has a TabPane. Supports adding/removing/renaming tabs.
 */
public class TabManager {

    private final TabPane leftPane;
    private final TabPane rightPane;

    public TabManager(TabPane left, TabPane right) {
        this.leftPane = left;
        this.rightPane = right;
    }

    public void addTab(boolean leftSide, String title) {
        Tab tab = new Tab(title);
        tab.setClosable(true);
        if (leftSide) leftPane.getTabs().add(tab);
        else rightPane.getTabs().add(tab);
    }

    public void closeTab(Tab tab, boolean leftSide) {
        if (leftSide) leftPane.getTabs().remove(tab);
        else rightPane.getTabs().remove(tab);
    }

    public List<String> getAllTabTitles(boolean leftSide) {
        List<String> titles = new ArrayList<>();
        TabPane pane = leftSide ? leftPane : rightPane;
        for (Tab t : pane.getTabs()) titles.add(t.getText());
        return titles;
    }
}
