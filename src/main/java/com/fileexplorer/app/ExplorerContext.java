package com.fileexplorer.app;

import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.filesystem.FileMetadataService;
import com.fileexplorer.service.filesystem.TreeBuildService;
import com.fileexplorer.service.icon.IconCacheService;
import com.fileexplorer.service.theme.ThemeService;
import com.fileexplorer.util.CompositeCloseable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * ExplorerContext: shared state holder.
 *
 * <p>Centralizes shared services and high-level state (e.g., current directory) so controllers/components
 * can share it without growing controller fields indefinitely.</p>
 *
 * <p>Owns a shared {@link CompositeCloseable} for app-lifetime / cross-controller subscriptions.</p>
 */
public final class ExplorerContext implements AutoCloseable {

    private final ThemeService themeService;
    private final FileMetadataService fileMetadataService;
    private final IconCacheService iconCacheService;
    private final TreeBuildService treeBuildService;
    private final EventBus eventBus;

    private final CompositeCloseable disposables = new CompositeCloseable();

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

    /**
     * Shared CompositeCloseable for cross-controller subscriptions.
     */
    public CompositeCloseable disposables() { return disposables; }

    public Path currentDirectory() { return currentDirectory; }
    public void setCurrentDirectory(Path currentDirectory) { this.currentDirectory = currentDirectory; }

    @Override
    public void close() {
        disposables.close();
    }
}
