package com.fileexplorer.ui;

import java.nio.file.Path;
import java.util.*;

/**
 * WorkspaceService
 *  - Stores session layout:
 *     * open tabs
 *     * active tab index
 *     * last browsed folder
 */
public class WorkspaceService {

    private Path lastFolder;
    private final List<Path> openTabs = new ArrayList<>();
    private int activeTabIndex = 0;

    public void setLastFolder(Path folder) {
        this.lastFolder = folder;
    }

    public Path getLastFolder() {
        return lastFolder;
    }

    public List<Path> getOpenTabs() {
        return List.copyOf(openTabs);
    }

    public void setOpenTabs(List<Path> tabs) {
        openTabs.clear();
        openTabs.addAll(tabs);
    }

    public void setActiveTabIndex(int index) {
        this.activeTabIndex = index;
    }

    public int getActiveTabIndex() {
        return activeTabIndex;
    }
}