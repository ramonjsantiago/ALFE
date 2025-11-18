package com.fileexplorer.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.collections.ObservableList;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ThemeManager (singleton)
 * - Mixed model:
 *   * Global base theme (Light / Dark)
 *   * Per-tab overlay (None / Glassy / Mica / Acrylic)
 *
 * Usage:
 *  ThemeManager.get().setBaseTheme(ThemeUtils.BaseTheme.LIGHT);
 *  ThemeManager.get().registerScene(primaryScene);
 *  ThemeManager.get().setTabOverlay(tab, ThemeUtils.Overlay.MICA);
 */
public class ThemeManager {
    private static final ThemeManager instance = new ThemeManager();

    public static ThemeManager get() { return instance; }

    private ThemeUtils.BaseTheme baseTheme = ThemeUtils.BaseTheme.LIGHT;
    // Scenes to hot-reload on change
    private final List<Scene> registeredScenes = new CopyOnWriteArrayList<>();
    // Tab -> overlay mapping (stored by Tab id)
    private final Map<Tab, ThemeUtils.Overlay> tabOverlays = Collections.synchronizedMap(new WeakHashMap<>());

    private ThemeManager() {}

    // Register a scene so theme changes automatically apply
    public void registerScene(Scene scene) {
        if (scene == null) return;
        if (!registeredScenes.contains(scene)) {
            registeredScenes.add(scene);
        }
        applyThemeToScene(scene);
    }

    public void unregisterScene(Scene scene) {
        registeredScenes.remove(scene);
    }

    // Base theme controls
    public ThemeUtils.BaseTheme getBaseTheme() { return baseTheme; }

    public void setBaseTheme(ThemeUtils.BaseTheme base) {
        if (base == null) return;
        baseTheme = base;
        // Apply to all registered scenes
        for (Scene s : new ArrayList<>(registeredScenes)) {
            applyThemeToScene(s);
        }
    }

    // Per-tab overlay
    public void setTabOverlay(Tab tab, ThemeUtils.Overlay overlay) {
        if (tab == null) return;
        if (overlay == null || overlay == ThemeUtils.Overlay.NONE) {
            tabOverlays.remove(tab);
        } else {
            tabOverlays.put(tab, overlay);
        }
        // If the tab has an associated Scene (common pattern: tab content has scene), try to reapply
        applyOverlayToTab(tab);
    }

    public ThemeUtils.Overlay getTabOverlay(Tab tab) {
        return tabOverlays.getOrDefault(tab, ThemeUtils.Overlay.NONE);
    }

    // Internal helpers
    private void applyThemeToScene(Scene scene) {
        if (scene == null) return;
        Platform.runLater(() -> {
            try {
                ObservableList<String> sheets = scene.getStylesheets();
                sheets.clear();
                // Always load base + explorer-base.css
                sheets.add(Objects.requireNonNull(getClass().getResource("/css/explorer-base.css")).toExternalForm());
                String base = ThemeUtils.baseCssFile(baseTheme);
                if (base != null) sheets.add(Objects.requireNonNull(getClass().getResource(base)).toExternalForm());
                // If the scene's window contains a TabPane we will let MainController set per-tab overlays.
                // For general Scenes, don't add overlays globally.
            } catch (Exception e) {
                System.err.println("ThemeManager: failed to apply theme to scene: " + e.getMessage());
            }
        });
    }

    private void applyOverlayToTab(Tab tab) {
        if (tab == null) return;
        ThemeUtils.Overlay overlay = getTabOverlay(tab);
        // If the tab content is a Node with a Scene, manipulate that Scene's stylesheets to include overlay
        Platform.runLater(() -> {
            try {
                if (tab.getContent() != null && tab.getContent().getScene() != null) {
                    Scene s = tab.getContent().getScene();
                    ObservableList<String> sheets = s.getStylesheets();
                    // First remove any known overlay sheets
                    removeOverlaySheets(sheets);
                    String overlayPath = ThemeUtils.overlayCssFile(overlay);
                    if (overlayPath != null) {
                        sheets.add(Objects.requireNonNull(getClass().getResource(overlayPath)).toExternalForm());
                    }
                } else {
                    // If content has no scene yet, try to attach when content gets scene (MainController will reapply on tab selection)
                }
            } catch (Exception e) {
                System.err.println("ThemeManager.applyOverlayToTab error: " + e.getMessage());
            }
        });
    }

    private void removeOverlaySheets(ObservableList<String> sheets) {
        if (sheets == null) return;
        // overlay file names we introduced
        String[] known = new String[] { "/css/explorer-glassy.css", "/css/mica-acrylic.css", "/css/explorer-glassy.css" };
        sheets.removeIf(url -> {
            for (String k : known) {
                if (url.endsWith(k)) return true;
            }
            return false;
        });
    }

    // Convenience: apply current theme to all registered scenes (hot-reload)
    public void reloadAll() {
        for (Scene s : new ArrayList<>(registeredScenes)) applyThemeToScene(s);
    }
}
