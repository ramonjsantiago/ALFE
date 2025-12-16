package com.fileexplorer.ui;

import javafx.scene.image.Image;

public class IconLoader {

    private static final Image folderIcon = new Image(IconLoader.class.getResourceAsStream("/icons/folder.png"));
    private static final Image fileIcon = new Image(IconLoader.class.getResourceAsStream("/icons/file.png"));

    public static Image getFolderIcon() { return folderIcon; }
    public static Image getFileIcon() { return fileIcon; }
}
