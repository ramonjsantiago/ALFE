package com.fileexplorer.util;

import com.fileexplorer.controller.MainController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Phase 4C.1: Optional successive-folder navigation soak runner.
 *
 * <p>Enabled with -Dfileexplorer.soak.enabled=true. This cycles through a small
 * set of common folders under the user's profile (Home, Desktop, Documents, Downloads)
 * to help surface leaks, listener accumulation, and queue growth issues.</p>
 */
public final class SoakNavigator implements AutoCloseable {

    private final MainController controller;
    private final ScheduledExecutorService ses;
    private final List<Path> targets;
    private volatile int idx = 0;

    public SoakNavigator(MainController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.ses = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "fe-soak-nav");
            t.setDaemon(true);
            return t;
        });
        this.targets = buildTargets();
    }

    public void start() {
        if (targets.isEmpty()) return;
        long periodMs = Long.getLong("fileexplorer.soak.periodMs", 3000L);
        long initialDelayMs = Long.getLong("fileexplorer.soak.initialDelayMs", 2500L);
        ses.scheduleAtFixedRate(() -> {
            try {
                Path p = targets.get(idx % targets.size());
                idx++;
                controller.soakNavigateToFolder(p);
            } catch (Throwable ignored) {
            }
        }, initialDelayMs, Math.max(750L, periodMs), TimeUnit.MILLISECONDS);
    }

    private static List<Path> buildTargets() {
        List<Path> out = new ArrayList<>();
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            addIfDir(out, Path.of(home));
            addIfDir(out, Path.of(home, "Desktop"));
            addIfDir(out, Path.of(home, "Documents"));
            addIfDir(out, Path.of(home, "Downloads"));
            addIfDir(out, Path.of(home, "Pictures"));
        }
        // Also allow explicit semicolon-separated list.
        String extra = System.getProperty("fileexplorer.soak.paths");
        if (extra != null && !extra.isBlank()) {
            for (String part : extra.split(";")) {
                String t = part.trim();
                if (t.isEmpty()) continue;
                addIfDir(out, Path.of(t));
            }
        }
        return out;
    }

    private static void addIfDir(List<Path> out, Path p) {
        try {
            if (p != null && Files.isDirectory(p)) {
                out.add(p);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void close() {
        try { ses.shutdownNow(); } catch (Exception ignored) {}
    }
}
