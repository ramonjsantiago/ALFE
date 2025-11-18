package com.fileexplorer.ui;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class IconLoader {

    private static final Image PLACEHOLDER = new Image(
            IconLoader.class.getResourceAsStream("/com/fileexplorer/ui/icons/placeholder.png")
    );

    public static Image loadIcon(File file) {
        try {
            // ImageIO with TwelveMonkeys supports extended formats: TIFF, BMP, PSD, JPEG2000, etc.
            BufferedImage bufferedImage = ImageIO.read(file);
            if (bufferedImage != null) {
                return SwingFXUtils.toFXImage(bufferedImage, null);
            } else {
                return PLACEHOLDER;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return PLACEHOLDER;
        }
    }

    public static Image getPlaceholder() {
        return PLACEHOLDER;
    }
}
