package com.fileexplorer.ui;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TabManager {

    private final TabPane tabPane;
    private final File sessionFile = new File(System.getProperty("user.home"), ".fileexplorer_session");

    public TabManager(TabPane tabPane) {
        this.tabPane = tabPane;
    }

    public void pinTab(Tab tab, boolean pinned) {
        tab.setClosable(!pinned);
        tab.getProperties().put("pinned", pinned);
    }

    public boolean isPinned(Tab tab) {
        Object val = tab.getProperties().get("pinned");
        return val != null && (boolean) val;
    }

    public void saveSession() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(sessionFile))) {
            List<String> folderPaths = new ArrayList<>();
            for (Tab tab : tabPane.getTabs()) {
                folderPaths.add(tab.getText());
            }
            oos.writeObject(folderPaths);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void restoreSession() {
        if (!sessionFile.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(sessionFile))) {
            List<String> folderPaths = (List<String>) ois.readObject();
            tabPane.getTabs().clear();
            for (String path : folderPaths) {
                Tab tab = new Tab(path);
                tabPane.getTabs().add(tab);
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
