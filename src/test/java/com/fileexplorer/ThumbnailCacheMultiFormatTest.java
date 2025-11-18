package com.fileexplorer.ui;

import javafx.scene.image.Image;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class ThumbnailCacheMultiFormatTest {

    @Test
    void testLoadTiffImage() {
        File tiffFile = new File("src/test/resources/test_image.tiff");
        Image img = IconLoader.loadIcon(tiffFile);
        assertNotNull(img, "TIFF image should load successfully");
    }

    @Test
    void testLoadBmpImage() {
        File bmpFile = new File("src/test/resources/test_image.bmp");
        Image img = IconLoader.loadIcon(bmpFile);
        assertNotNull(img, "BMP image should load successfully");
    }

    @Test
    void testLoadJpegImage() {
        File jpgFile = new File("src/test/resources/test_image.jpg");
        Image img = IconLoader.loadIcon(jpgFile);
        assertNotNull(img, "JPEG image should load successfully");
    }

    @Test
    void testLoadUnsupportedImage() {
        File txtFile = new File("src/test/resources/test_file.txt");
        Image img = IconLoader.loadIcon(txtFile);
        assertNotNull(img, "Unsupported image should fallback to placeholder");
    }
}