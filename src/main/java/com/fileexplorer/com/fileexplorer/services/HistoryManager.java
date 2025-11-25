package com.fileexplorer.services;

import java.io.File;
import java.util.Deque;
import java.util.LinkedList;

public class HistoryManager {

    private final Deque<FileAction> undoStack = new LinkedList<>();
    private final Deque<FileAction> redoStack = new LinkedList<>();

    public void recordMove(File src, File dest) { record(new MoveAction(src, dest)); }
    public void recordRename(File oldFile, File newFile) { record(new RenameAction(oldFile, newFile)); }
    public void recordDelete(File file) { record(new DeleteAction(file)); }

    private void record(FileAction action) {
        undoStack.push(action);
        redoStack.clear();
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            FileAction action = undoStack.pop();
            action.undo();
            redoStack.push(action);
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            FileAction action = redoStack.pop();
            action.redo();
            undoStack.push(action);
        }
    }

    // --- FileAction and concrete implementations ---
    private interface FileAction { void undo(); void redo(); }

    private static class MoveAction implements FileAction {
        private final File src, dest;
        MoveAction(File src, File dest) { this.src = src; this.dest = dest; }
        public void undo() { dest.renameTo(src); }
        public void redo() { src.renameTo(dest); }
    }

    private static class RenameAction implements FileAction {
        private final File oldFile, newFile;
        RenameAction(File oldFile, File newFile) { this.oldFile = oldFile; this.newFile = newFile; }
        public void undo() { newFile.renameTo(oldFile); }
        public void redo() { oldFile.renameTo(newFile); }
    }

    private static class DeleteAction implements FileAction {
        private final File file;
        DeleteAction(File file) { this.file = file; }
        public void undo() { /* attempt to restore from recycle bin or previous location */ }
        public void redo() { file.delete(); }
    }
}
