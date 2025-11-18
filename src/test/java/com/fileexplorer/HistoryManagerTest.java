package com.fileexplorer;

import com.fileexplorer.thumb.HistoryManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class HistoryManagerTest {

    @Test
    void testRenameUndo() throws Exception {
        Path tmp = Files.createTempDirectory("hm");
        Path a = tmp.resolve("a.txt");
        Path b = tmp.resolve("b.txt");
        Files.writeString(a, "x");

        HistoryManager hm = new HistoryManager();
        Files.move(a, b);
        hm.recordRename(a, b);

        assertTrue(hm.canUndo());
        hm.undo();
        assertTrue(Files.exists(a));
        assertFalse(Files.exists(b));
        Files.deleteIfExists(a);
        Files.deleteIfExists(tmp);
    }

    @Test
    void deleteIsRecordedButNotUndoable() {
        HistoryManager hm = new HistoryManager();
        hm.recordDelete(Path.of("dummy"));
        assertFalse(hm.canUndo());
    }
}
