package com.fileexplorer.app;

import com.fileexplorer.service.event.EventBus;
import com.fileexplorer.service.filesystem.FileMetadataService;
import com.fileexplorer.service.filesystem.TreeBuildService;
import com.fileexplorer.service.icon.IconCacheService;
import com.fileexplorer.service.ops.OperationQueueService;
import com.fileexplorer.service.ops.history.OperationHistoryService;
import com.fileexplorer.service.ops.command.CommandContext;
import com.fileexplorer.service.ops.command.CommandManager;
import com.fileexplorer.service.template.OperationTemplateService;
import com.fileexplorer.service.template.TemplateRecurringScheduleService;
import com.fileexplorer.service.template.TemplateRunHistoryService;
import com.fileexplorer.service.template.TemplateSchedulerService;
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

    /** Safe mode disables background automation (e.g., scheduler auto-run, queue auto-recovery). */
    private final boolean safeMode;

    private final ThemeService themeService;
    private final FileMetadataService fileMetadataService;
    private final IconCacheService iconCacheService;
    private final TreeBuildService treeBuildService;
    private final EventBus eventBus;

    // Phase 3.6.2: Operation queue + progress UI model
    private final OperationQueueService operationQueueService;

    // Phase 3.7.0: Operation history + audit trail
    private final OperationHistoryService operationHistoryService;

    // Phase 4.0.0: Command framework
    private final CommandManager commandManager;

    // Phase 5.2.x: Templates + scheduler
    private final OperationTemplateService operationTemplateService;
    private final TemplateRecurringScheduleService templateRecurringScheduleService;
    private final TemplateRunHistoryService templateRunHistoryService;
    private final TemplateSchedulerService templateSchedulerService;

    private final CompositeCloseable disposables = new CompositeCloseable();

    private Path currentDirectory;

    public ExplorerContext(
            ThemeService themeService,
            FileMetadataService fileMetadataService,
            IconCacheService iconCacheService,
            TreeBuildService treeBuildService,
            EventBus eventBus,
            boolean safeMode
    ) {
        this.themeService = Objects.requireNonNull(themeService, "themeService");
        this.fileMetadataService = Objects.requireNonNull(fileMetadataService, "fileMetadataService");
        this.iconCacheService = Objects.requireNonNull(iconCacheService, "iconCacheService");
        this.treeBuildService = Objects.requireNonNull(treeBuildService, "treeBuildService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");

        this.safeMode = safeMode;

        this.operationQueueService = new OperationQueueService();
        this.operationHistoryService = new OperationHistoryService();

        this.operationTemplateService = new OperationTemplateService();
        this.templateRecurringScheduleService = new TemplateRecurringScheduleService();
        this.templateRunHistoryService = new TemplateRunHistoryService();
        this.templateSchedulerService = new TemplateSchedulerService(
                this.operationTemplateService,
                this.operationQueueService,
                this.templateRecurringScheduleService,
                this.templateRunHistoryService,
                safeMode
        );

        this.commandManager = new CommandManager(new CommandContext(this.operationQueueService, this.operationHistoryService, this.eventBus));
    }

    public ThemeService themeService() { return themeService; }
    public FileMetadataService fileMetadataService() { return fileMetadataService; }
    public IconCacheService iconCacheService() { return iconCacheService; }
    public TreeBuildService treeBuildService() { return treeBuildService; }
    public EventBus eventBus() { return eventBus; }

    public OperationQueueService operationQueueService() { return operationQueueService; }
    public OperationHistoryService operationHistoryService() { return operationHistoryService; }


    public CommandManager commandManager() { return commandManager; }

    public OperationTemplateService operationTemplateService() { return operationTemplateService; }
    public TemplateRecurringScheduleService templateRecurringScheduleService() { return templateRecurringScheduleService; }
    public TemplateRunHistoryService templateRunHistoryService() { return templateRunHistoryService; }
    public TemplateSchedulerService templateSchedulerService() { return templateSchedulerService; }

    /**
     * True if the app is running in safe mode.
     */
    public boolean isSafeMode() { return safeMode; }

    /**
     * Shared CompositeCloseable for cross-controller subscriptions.
     */
    public CompositeCloseable disposables() { return disposables; }

    public Path currentDirectory() { return currentDirectory; }
    public void setCurrentDirectory(Path currentDirectory) { this.currentDirectory = currentDirectory; }

    @Override
/**
 * close.
 *
 */
    public void close() {
        try {
            operationQueueService.close();
        } catch (Throwable ignored) {
            // best effort
        }
        try {
            templateSchedulerService.close();
        } catch (Throwable ignored) {
            // best effort
        }
        try {
            operationHistoryService.close();
        } catch (Throwable ignored) {
            // best effort
        }
        disposables.close();
    }
}
