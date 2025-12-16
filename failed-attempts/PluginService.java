package com.fileexplorer.ui;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PluginService
 *  - Loads external plugins from JARs
 *  - Each plugin should implement ExplorerPlugin interface
 */
public class PluginService {

    private final List<ExplorerPlugin> plugins = new ArrayList<>();

    public void loadPlugin(Path jarPath) throws Exception {
        URL url = jarPath.toUri().toURL();
        URLClassLoader loader = new URLClassLoader(new URL[]{url}, getClass().getClassLoader());
        // TODO: Load classes implementing ExplorerPlugin and instantiate
        // Placeholder: just add to list if castable
    }

    public List<ExplorerPlugin> getPlugins() {
        return List.copyOf(plugins);
    }

    public interface ExplorerPlugin {
        void init();
    }
}