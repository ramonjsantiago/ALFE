package com.fileexplorer.ui;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;

/**
 * ThumbnailService
 *  - Generates cached thumbnails for images
 *  - Called async using TaskSchedulerService
 */
public class ThumbnailService {

    public Image createThumbnail(Path file, int size) {
        try {
            BufferedImage bi = ImageIO.read(file.toFile());
            if (bi == null) return null;

            int w = bi.getWidth();
            int h = bi.getHeight();
            double scale = (double) size / Math.max(w, h);

            int nw = (int)(w * scale);
            int nh = (int)(h * scale);

            BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            scaled.getGraphics().drawImage(bi, 0, 0, nw, nh, null);

            WritableImage fx = new WritableImage(nw, nh);
            return SwingFXUtils.toFXImage(scaled, fx);

        } catch (IOException e) {
            return null;
        }
    }
}
