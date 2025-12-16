package com.fileexplorer.ui;

import java.io.*;
import java.util.*;
import javafx.scene.control.Tab;

public class SessionManager {
    private static final String SESSION_FILE = "tab_sessions.ser";

    public static void saveTabStates(List<Tab> tabs) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SESSION_FILE))) {
            List<String> tabNames = new ArrayList<>();
            for (Tab tab : tabs) {
                tabNames.add(tab.getText());
            }
            oos.writeObject(tabNames);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> loadTabStates() {
        File f = new File(SESSION_FILE);
        if (!f.exists()) return Collections.emptyList();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            return (List<String>) ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
