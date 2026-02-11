package com.fileexplorer.app;

import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.filesystem.FileMetadataService;
import com.fileexplorer.service.filesystem.TreeBuildService;
import com.fileexplorer.service.icon.IconCacheService;
import com.fileexplorer.service.theme.ThemeService;

import java.nio.file.Path;
import java.util.Objects;

/**
 * ExplorerContext: shared state holder (Step 7).
 *
 * This is intentionally simple and mutable. It centralizes shared services and
 * high-level state (e.g., current directory) so controllers/components can share it
 * without growing controller fields indefinitely.
 */
public final class ExplorerContext {

    private final ThemeService themeService;
    private final FileMetadataService fileMetadataService;
    private final IconCacheService iconCacheService;
    private final TreeBuildService treeBuildService;
    private final EventBus eventBus;

    private Path currentDirectory;

    public ExplorerContext(
            ThemeService themeService,
            FileMetadataService fileMetadataService,
            IconCacheService iconCacheService,
            TreeBuildService treeBuildService,
            EventBus eventBus
    ) {
        this.themeService = Objects.requireNonNull(themeService, "themeService");
        this.fileMetadataService = Objects.requireNonNull(fileMetadataService, "fileMetadataService");
        this.iconCacheService = Objects.requireNonNull(iconCacheService, "iconCacheService");
        this.treeBuildService = Objects.requireNonNull(treeBuildService, "treeBuildService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public ThemeService themeService() { return themeService; }
    public FileMetadataService fileMetadataService() { return fileMetadataService; }
    public IconCacheService iconCacheService() { return iconCacheService; }
    public TreeBuildService treeBuildService() { return treeBuildService; }
    public EventBus eventBus() { return eventBus; }

    public Path currentDirectory() { return currentDirectory; }
    public void setCurrentDirectory(Path currentDirectory) { this.currentDirectory = currentDirectory; }
}
