package com.fileexplorer.ui.service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import javafx.scene.image.ImageView;

public final class IconService {

    private final com.fileexplorer.service.IconService delegate;

    public IconService() {
        this(new com.fileexplorer.service.IconService());
    }

    public IconService(com.fileexplorer.service.IconService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public ImageView iconForPath(Path p, boolean isTreeRow) {
        return delegate.iconForPath(p, isTreeRow);
    }

    public ImageView iconForExtension(String extensionLowercase, boolean isTreeRow) {
        String ext = extensionLowercase == null ? "" : extensionLowercase.toLowerCase(Locale.ROOT).trim();
        return delegate.iconForExtension(ext, isTreeRow);
    }
}
