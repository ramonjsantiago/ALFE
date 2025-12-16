package com.fileexplorer.service;

import java.nio.file.Path;
import javafx.scene.image.ImageView;

public final class IconService {
    public IconService() {}

    public ImageView iconForPath(Path p, boolean isTreeRow) { return new ImageView(); }

    public ImageView iconForExtension(String extensionLowercase, boolean isTreeRow) { return new ImageView(); }
}
