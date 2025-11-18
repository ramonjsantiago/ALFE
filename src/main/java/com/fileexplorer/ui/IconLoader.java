package com.fileexplorer.ui;

import javafx.scene.image.Image;
import java.io.File;

public class IconLoader {
    public static Image loadIcon(File file) {
        // placeholder 1x1 pixel
        return new Image("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8Xw8AAn8B9L5tqgAAAABJRU5ErkJggg==");
    }
}
