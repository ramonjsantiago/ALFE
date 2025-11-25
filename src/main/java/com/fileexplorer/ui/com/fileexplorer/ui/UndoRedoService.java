// package com.explorer.ui;

// import java.nio.file.Path;
// import java.util.Stack;

// /**
 // * UndoRedoService
 // *  - Tracks file operations for undo/redo
 // *  - Supports simple move/copy/delete operations
 // */
// public class UndoRedoService {

    // public static class Action {
        // public enum Type {COPY, MOVE, DELETE}
        // public final Type type;
        // public final Path src;
        // public final Path dest; // for MOVE/COPY

        // public Action(Type type, Path src, Path dest) {
            // this.type = type;
            // this.src = src;
            // this.dest = dest;
        // }
    // }

    // private final Stack<Action> undoStack = new Stack<>();
    // private final Stack<Action> redoStack = new Stack<>();

    // public void addAction(Action action) {
        // undoStack.push(action);
        // redoStack.clear();
    // }

    // public Action undo() {
        // if (!undoStack.isEmpty()) {
            // Action a = undoStack.pop();
            // redoStack.push(a);
            // return a;
        // }
        // return null;
    // }

    // public Action redo() {
        // if (!redoStack.isEmpty()) {
            // Action a = redoStack.pop();
            // undoStack.push(a);
            // return a;
        // }
        // return null;
    // }

    // public boolean canUndo() { return !undoStack.isEmpty(); }
    // public boolean canRedo() { return !redoStack.isEmpty(); }
// }

// package com.explorer.ui;

// import java.util.ArrayDeque;
// import java.util.Deque;

// public class UndoRedoService {

    // public interface Action {
        // void execute() throws Exception;
        // void undo() throws Exception;
    // }

    // private final Deque<Action> undoStack = new ArrayDeque<>();
    // private final Deque<Action> redoStack = new ArrayDeque<>();

    // public void perform(Action action) throws Exception {
        // action.execute();
        // undoStack.push(action);
        // redoStack.clear();
    // }

    // public boolean canUndo() {
        // return !undoStack.isEmpty();
    // }

    // public boolean canRedo() {
        // return !redoStack.isEmpty();
    // }

    // public void undo() throws Exception {
        // if (!canUndo()) return;
        // Action action = undoStack.pop();
        // action.undo();
        // redoStack.push(action);
    // }

    // public void redo() throws Exception {
        // if (!canRedo()) return;
        // Action action = redoStack.pop();
        // action.execute();
        // undoStack.push(action);
    // }

    // public void clear() {
        // undoStack.clear();
        // redoStack.clear();
    // }
// }

package com.fileexplorer.ui;

import java.util.ArrayDeque;
import java.util.Deque;

public class UndoRedoService {

    public interface Action {
        void execute() throws Exception;
        void undo() throws Exception;
    }

    private final Deque<Action> undoStack = new ArrayDeque<>();
    private final Deque<Action> redoStack = new ArrayDeque<>();

    public void perform(Action action) throws Exception {
        action.execute();
        undoStack.push(action);
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() throws Exception {
        if (!canUndo()) return;
        Action action = undoStack.pop();
        action.undo();
        redoStack.push(action);
    }

    public void redo() throws Exception {
        if (!canRedo()) return;
        Action action = redoStack.pop();
        action.execute();
        undoStack.push(action);
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
