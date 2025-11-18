package com.fileexplorer.ui;

import org.junit.jupiter.api.Test;
import javafx.scene.image.Image;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class ThumbnailCacheTest {

    @Test
    void testThumbnailCache() {
        ThumbnailGenerator generator = new ThumbnailGenerator();
        File f = new File(System.getProperty("user.home"), "test.jpg");
        Image img = generator.loadThumbnail(f, 128, 128);
        assertNotNull(img);
    }
}
