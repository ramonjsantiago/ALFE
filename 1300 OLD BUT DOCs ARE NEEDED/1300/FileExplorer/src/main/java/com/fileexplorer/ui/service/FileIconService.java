package com.fileexplorer.ui.service;

import java.nio.file.Path;
import java.util.Objects;
import javafx.scene.image.ImageView;

public final class FileIconService {

    private final IconService iconService;

    public FileIconService() {
        this(new IconService());
    }

    public FileIconService(IconService iconService) {
        this.iconService = Objects.requireNonNull(iconService, "iconService");
    }

    public ImageView iconForPath(Path p, boolean isTreeRow) {
        return iconService.iconForPath(p, isTreeRow);
    }
}
