package com.fileexplorer;

import com.fileexplorer.thumb.ThumbnailCache;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class ThumbnailCacheTest {

    @Test
    void testCachePutGet() {
        ThumbnailCache cache = new ThumbnailCache(10);
        WritableImage img = new WritableImage(1,1);
        Path key = Path.of("A");
        cache.put(key, img);
        assertSame(img, cache.get(key));
    }

    @Test
    void testStrongLimitBehaviour() {
        ThumbnailCache cache = new ThumbnailCache(1);
        WritableImage a = new WritableImage(1,1);
        WritableImage b = new WritableImage(1,1);
        Path ka = Path.of("A");
        Path kb = Path.of("B");
        cache.put(ka, a);
        cache.put(kb, b);
        assertNotNull(cache.get(kb));
    }
}
