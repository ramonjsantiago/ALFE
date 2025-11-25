package com.fileexplorer.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TabManagerService {

    private final List<Path> openTabs = new ArrayList<>();
    private int currentIndex = -1;

    public void openTab(Path folder) {
        // Remove forward history if any
        while (openTabs.size() > currentIndex + 1) {
            openTabs.remove(openTabs.size() - 1);
        }

        openTabs.add(folder);
        currentIndex = openTabs.size() - 1;
    }

    public Path getCurrentTab() {
        if (currentIndex >= 0 && currentIndex < openTabs.size()) {
            return openTabs.get(currentIndex);
        }
        return null;
    }

    public Path goBack() {
        if (currentIndex > 0) {
            currentIndex--;
            return openTabs.get(currentIndex);
        }
        return getCurrentTab();
    }

    public Path goForward() {
        if (currentIndex < openTabs.size() - 1) {
            currentIndex++;
            return openTabs.get(currentIndex);
        }
        return getCurrentTab();
    }

    public List<Path> getOpenTabs() {
        return new ArrayList<>(openTabs);
    }
}
