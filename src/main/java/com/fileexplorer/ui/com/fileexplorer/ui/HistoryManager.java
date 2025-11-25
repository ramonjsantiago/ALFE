package com.fileexplorer.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {
    public static class Action {
        public final Path src;
        public final Path dest; // null for delete
        public Action(Path src, Path dest) { this.src = src; this.dest = dest; }
    }

    private final List<Action> actions = new ArrayList<>();

    public void recordRename(Path src, Path dest) { actions.add(new Action(src, dest)); }
    public void recordDelete(Path src) { actions.add(new Action(src, null)); }

    public boolean canUndo() {
        return !actions.isEmpty() && actions.get(actions.size()-1).dest != null;
    }

    public void undo() throws Exception {
        if (!canUndo()) return;
        Action a = actions.remove(actions.size()-1);
        if (a.dest != null) java.nio.file.Files.move(a.dest, a.src);
    }

    public List<Action> getActions() { return new ArrayList<>(actions); }
}
