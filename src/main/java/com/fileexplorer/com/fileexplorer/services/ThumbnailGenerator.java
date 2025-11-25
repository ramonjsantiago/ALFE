package com.fileexplorer.services;

import javafx.scene.image.Image;
import javafx.embed.swing.SwingFXUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;

public class ThumbnailGenerator {

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

    public Image generateThumbnail(File file, int width, int height) {
        if (file.isDirectory()) {
            return createFolderPlaceholder(width, height);
        }
        try {
            BufferedImage bimg = ImageIO.read(file);
            if (bimg == null) return createFilePlaceholder(width, height);
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = scaled.createGraphics();
            g2d.drawImage(bimg, 0, 0, width, height, null);
            g2d.dispose();
            return SwingFXUtils.toFXImage(scaled, null);
        } catch (IOException e) {
            return createFilePlaceholder(width, height);
        }
    }

    private Image createFolderPlaceholder(int width, int height) {
        BufferedImage bimg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2d = bimg.createGraphics();
        g2d.setColor(java.awt.Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(java.awt.Color.DARK_GRAY);
        g2d.drawRect(0, 0, width-1, height-1);
        g2d.dispose();
        return SwingFXUtils.toFXImage(bimg, null);
    }

    private Image createFilePlaceholder(int width, int height) {
        BufferedImage bimg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2d = bimg.createGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(java.awt.Color.RED);
        g2d.drawLine(0,0,width,height);
        g2d.drawLine(0,height,width,0);
        g2d.dispose();
        return SwingFXUtils.toFXImage(bimg, null);
    }

    public Future<Image> generateThumbnailAsync(File file, int width, int height) {
        return executor.submit(() -> generateThumbnail(file, width, height));
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
