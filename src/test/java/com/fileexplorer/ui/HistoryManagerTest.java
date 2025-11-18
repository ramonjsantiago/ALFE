package com.fileexplorer.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryManagerTest {

    @Test
    void testUndoRedo() {
        HistoryManager hm = new HistoryManager();
        hm.recordAction("Create File A");
        hm.recordAction("Delete File B");
        assertEquals("Delete File B", hm.undoStack.peek());
        hm.undo();
        assertEquals("Delete File B", hm.redoStack.peek());
        hm.redo();
        assertEquals("Delete File B", hm.undoStack.peek());
    }
}
