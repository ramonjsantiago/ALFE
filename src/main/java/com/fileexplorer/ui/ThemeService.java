package com.fileexplorer.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Central place for theme and OS integration helpers.
 */
public final class ThemeService {

    public enum Theme {
        LIGHT,
        DARK,
        SYSTEM
    }

    private static final String BASE_CSS   = "/com/fileexplorer/ui/css/explorer-base.css";
    private static final String TABLE_CSS  = "/com/fileexplorer/ui/css/explorer-table.css";
    private static final String LIGHT_CSS  = "/com/fileexplorer/ui/css/explorer-light-win.css";
    private static final String DARK_CSS   = "/com/fileexplorer/ui/css/explorer-dark-win.css";
    private static final String SHELL_CSS  = "/com/fileexplorer/ui/css/explorer-win11.css";

    private ThemeService() {
    }

    // ---------------------------------------------------------------------
    // Theme plumbing
    // ---------------------------------------------------------------------

    public static void applyTheme(Scene scene, Theme theme) {
        if (scene == null) {
            return;
        }

        ensureBaseStylesheets(scene);

        Parent root = scene.getRoot();
        if (root != null) {
            root.getStyleClass().removeAll("theme-light", "theme-dark", "theme-system");
            switch (theme) {
                case LIGHT -> root.getStyleClass().add("theme-light");
                case DARK -> root.getStyleClass().add("theme-dark");
                case SYSTEM -> root.getStyleClass().add("theme-system");
            }
        }
    }

    private static void ensureBaseStylesheets(Scene scene) {
        List<String> sheets = scene.getStylesheets();
        addStylesheetIfMissing(sheets, BASE_CSS);
        addStylesheetIfMissing(sheets, TABLE_CSS);
        addStylesheetIfMissing(sheets, LIGHT_CSS);
        addStylesheetIfMissing(sheets, DARK_CSS);
        addStylesheetIfMissing(sheets, SHELL_CSS);
    }

    private static void addStylesheetIfMissing(List<String> sheets, String resourcePath) {
        URL url = ThemeService.class.getResource(resourcePath);
        if (url == null) {
            return;
        }
        String external = url.toExternalForm();
        boolean present = sheets.stream().anyMatch(s -> Objects.equals(s, external));
        if (!present) {
            sheets.add(external);
        }
    }

    public static Theme getCurrentTheme(Scene scene) {
        if (scene == null) {
            return Theme.SYSTEM;
        }
        Parent root = scene.getRoot();
        if (root == null) {
            return Theme.SYSTEM;
        }
        List<String> styleClasses = root.getStyleClass();
        if (styleClasses.contains("theme-dark")) {
            return Theme.DARK;
        }
        if (styleClasses.contains("theme-light")) {
            return Theme.LIGHT;
        }
        return Theme.SYSTEM;
    }

    public static boolean isDarkTheme(Scene scene) {
        return getCurrentTheme(scene) == Theme.DARK;
    }

    // ---------------------------------------------------------------------
    // OS helpers
    // ---------------------------------------------------------------------

    public static void openWithDesktop(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (Files.isDirectory(path)) {
                    desktop.open(path.toFile());
                } else {
                    desktop.open(path.toFile());
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void openUriInBrowser(String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(uri));
            }
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
        }
    }

    public static void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text != null ? text : "");
        clipboard.setContent(content);
    }

    public static void copyStylesheetsToClipboard(Scene scene) {
        if (scene == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : scene.getStylesheets()) {
            sb.append("[").append(i++).append("] ").append(s).append('\n');
        }
        copyToClipboard(sb.toString());
    }

    public static void dumpStylesheets(Scene scene) {
        if (scene == null) {
            System.out.println("=== Scene stylesheets (scene == null) ===");
            return;
        }
        System.out.println("=== Scene stylesheets ===");
        int i = 0;
        for (String s : scene.getStylesheets()) {
            System.out.println("[" + i++ + "] " + s);
        }
    }
}
