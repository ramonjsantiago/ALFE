// package com.explorer.ui;

// import javafx.scene.input.DragEvent;
// import javafx.scene.input.Dragboard;
// import javafx.scene.input.TransferMode;
// import javafx.scene.Node;

// import java.nio.file.Path;
// import java.util.List;

// /**
 // * DragDropService
 // *  - Adds drag-and-drop support for nodes
 // *  - Returns files dropped onto a node
 // */
// public class DragDropService {

    // public interface DropListener {
        // void onFilesDropped(List<Path> files);
    // }

    // public void enable(Node node, DropListener listener) {
        // node.setOnDragOver(event -> {
            // if (event.getGestureSource() != node && event.getDragboard().hasFiles()) {
                // event.acceptTransferModes(TransferMode.COPY);
            // }
            // event.consume();
        // });

        // node.setOnDragDropped(event -> {
            // Dragboard db = event.getDragboard();
            // boolean success = false;
            // if (db.hasFiles()) {
                // listener.onFilesDropped(db.getFiles().stream().map(Path::of).toList());
                // success = true;
            // }
            // event.setDropCompleted(success);
            // event.consume();
        // });
    // }
// }

package com.fileexplorer.ui;

import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.Node;

import java.nio.file.Path;
import java.util.List;

public class DragDropService {

    public void enableDrag(Node node, List<Path> paths) {
        node.setOnDragDetected(event -> {
            Dragboard db = node.startDragAndDrop(TransferMode.COPY_OR_MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putFiles(paths.stream().map(Path::toFile).toList());
            db.setContent(content);
            event.consume();
        });
    }

    public void enableDrop(Node node, DropHandler handler) {
        node.setOnDragOver(event -> {
            if (event.getGestureSource() != node && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        node.setOnDragDropped(event -> {
            var db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = handler.handle(db.getFiles().stream().map(Path::of).toList());
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    public interface DropHandler {
        boolean handle(List<Path> droppedFiles);
    }
}
