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
import com.fileexplorer.util.StartupTrace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ExplorerContext: shared state holder.
 *
 * <p>Centralizes shared services and high-level state (e.g., current directory) so controllers/components
 * can share it without growing controller fields indefinitely.</p>
 *
 * <p>HOTFIX185 introduces a lightweight stage-A constructor and a lazily activated stage-B service graph
 * so first-paint/bootstrap work does not eagerly instantiate scheduler/history/command subsystems.</p>
 */
public final class ExplorerContext implements AutoCloseable {

    /** Safe mode disables background automation (e.g., scheduler auto-run, queue auto-recovery). */
    private final boolean safeMode;

    private final ThemeService themeService;
    private final FileMetadataService fileMetadataService;
    private final IconCacheService iconCacheService;
    private final TreeBuildService treeBuildService;
    private final EventBus eventBus;

    private volatile OperationQueueService operationQueueService;
    private volatile OperationHistoryService operationHistoryService;
    private volatile CommandManager commandManager;
    private volatile OperationTemplateService operationTemplateService;
    private volatile TemplateRecurringScheduleService templateRecurringScheduleService;
    private volatile TemplateRunHistoryService templateRunHistoryService;
    private volatile TemplateSchedulerService templateSchedulerService;

    private final CompositeCloseable disposables = new CompositeCloseable();
    private final AtomicBoolean stageBActivationStarted = new AtomicBoolean(false);

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
    }

    public ThemeService themeService() { return themeService; }
    public FileMetadataService fileMetadataService() { return fileMetadataService; }
    public IconCacheService iconCacheService() { return iconCacheService; }
    public TreeBuildService treeBuildService() { return treeBuildService; }
    public EventBus eventBus() { return eventBus; }

    public OperationQueueService operationQueueService() {
        OperationQueueService existing = operationQueueService;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (operationQueueService == null) {
                StartupTrace.mark("ExplorerContext stageB operationQueue init");
                operationQueueService = new OperationQueueService();
            }
            return operationQueueService;
        }
    }

    public OperationHistoryService operationHistoryService() {
        OperationHistoryService existing = operationHistoryService;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (operationHistoryService == null) {
                StartupTrace.mark("ExplorerContext stageB operationHistory init");
                operationHistoryService = new OperationHistoryService();
            }
            return operationHistoryService;
        }
    }

    public CommandManager commandManager() {
        CommandManager existing = commandManager;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (commandManager == null) {
                StartupTrace.mark("ExplorerContext stageB commandManager init");
                commandManager = new CommandManager(new CommandContext(operationQueueService(), operationHistoryService(), eventBus));
            }
            return commandManager;
        }
    }

    public OperationTemplateService operationTemplateService() {
        OperationTemplateService existing = operationTemplateService;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (operationTemplateService == null) {
                StartupTrace.mark("ExplorerContext stageB templateService init");
                operationTemplateService = new OperationTemplateService();
            }
            return operationTemplateService;
        }
    }

    public TemplateRecurringScheduleService templateRecurringScheduleService() {
        TemplateRecurringScheduleService existing = templateRecurringScheduleService;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (templateRecurringScheduleService == null) {
                StartupTrace.mark("ExplorerContext stageB recurringSchedule init");
                templateRecurringScheduleService = new TemplateRecurringScheduleService();
            }
            return templateRecurringScheduleService;
        }
    }

    public TemplateRunHistoryService templateRunHistoryService() {
        TemplateRunHistoryService existing = templateRunHistoryService;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (templateRunHistoryService == null) {
                StartupTrace.mark("ExplorerContext stageB templateHistory init");
                templateRunHistoryService = new TemplateRunHistoryService();
            }
            return templateRunHistoryService;
        }
    }

    public TemplateSchedulerService templateSchedulerService() {
        TemplateSchedulerService existing = templateSchedulerService;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (templateSchedulerService == null) {
                StartupTrace.mark("ExplorerContext stageB scheduler init");
                templateSchedulerService = new TemplateSchedulerService(
                        operationTemplateService(),
                        operationQueueService(),
                        templateRecurringScheduleService(),
                        templateRunHistoryService(),
                        safeMode
                );
            }
            return templateSchedulerService;
        }
    }

    /**
     * Starts the deferred stage-B service graph on a background thread once startup is already interactive.
     * Repeated calls are ignored.
     */
    public void activateDeferredServicesAsync() {
        if (!stageBActivationStarted.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(this::activateDeferredServicesNow);
    }

    /**
     * Synchronously realizes the deferred service graph.
     */
    public void activateDeferredServicesNow() {
        StartupTrace.mark("ExplorerContext stageB activation begin");
        try {
            operationQueueService();
            operationHistoryService();
            operationTemplateService();
            templateRecurringScheduleService();
            templateRunHistoryService();
            templateSchedulerService();
            commandManager();
        } finally {
            StartupTrace.mark("ExplorerContext stageB activation end");
        }
    }

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
    public void close() {
        try {
            if (templateSchedulerService != null) {
                templateSchedulerService.close();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (operationQueueService != null) {
                operationQueueService.close();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (operationHistoryService != null) {
                operationHistoryService.close();
            }
        } catch (Throwable ignored) {
        }
        disposables.close();
    }
}
