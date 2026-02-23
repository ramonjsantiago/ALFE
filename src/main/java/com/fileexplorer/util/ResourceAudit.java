package com.fileexplorer.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;

/**
 * Resource audit for CSS, fonts, and images, including CSS url(...) references.
 * <p>
 * Notes:
 *  - This audits stylesheets added to Scene/Parent, reads their CSS text, follows @import chains,
 *    and extracts/resolves url(...) references (background images, borders, etc.).
 *  - It logs resolved URLs/paths via java.util.logging.
 */
public final class ResourceAudit {

    private static final Logger LOG = Logger.getLogger("com.fileexplorer.resources");

    private static final boolean ENABLED = Boolean.getBoolean("fileexplorer.resourceAudit");

    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private static final Set<String> SCANNED_STYLESHEETS = ConcurrentHashMap.newKeySet();

    private static volatile boolean installed;
    private static volatile ClassLoader loggingClassLoader;

/**
 * ResourceAudit.
 *
 * @return TODO
 */
    private ResourceAudit() {
        LogSupport.enter(LOG, "ResourceAudit");
    }

/**
 * install.
 *
 * @param anchor TODO
 */
    public static void install(Class<?> anchor) {
        LogSupport.enter(LOG, "install");
        if (!ENABLED) {
            return;
        }
        if (installed) {
            return;
        }
        synchronized (ResourceAudit.class) {
            if (installed) {
                return;
            }

            configureLogger();

            ClassLoader parent = (anchor == null) ? null : anchor.getClassLoader();
            if (parent == null) {
                parent = ClassLoader.getSystemClassLoader();
            }
            loggingClassLoader = new LoggingClassLoader(parent);

            try {
                Thread.currentThread().setContextClassLoader(loggingClassLoader);
            } catch (RuntimeException ex) {
                // ignore
            }

            try {
                Class<?> loader = Class.forName("javafx.fxml.FXMLLoader");
                java.lang.reflect.Method m = loader.getMethod("setDefaultClassLoader", ClassLoader.class);
                m.invoke(null, loggingClassLoader);
            } catch (ReflectiveOperationException | RuntimeException _) {
                // ignore
            }

            installed = true;
            LOG.info("Resource audit enabled.");
        }
    }

/**
 * classLoader.
 *
 * @return TODO
 */
    public static ClassLoader classLoader() {
        LogSupport.enter(LOG, "classLoader");
        if (!ENABLED) {
            return null;
        }
        return loggingClassLoader;
    }

/**
 * resourceUrl.
 *
 * @param anchor TODO
 * @param resourcePath TODO
 * @return TODO
 */
    public static URL resourceUrl(Class<?> anchor, String resourcePath) {
        LogSupport.enter(LOG, "resourceUrl");

        if (!ENABLED) {
            if (resourcePath == null) {
                return null;
            }
            String p = resourcePath.trim();
            while (p.startsWith("/")) {
                p = p.substring(1);
            }
            if (anchor != null) {
                return anchor.getResource("/" + p);
            }
            return ClassLoader.getSystemResource(p);
        }

        install(anchor);


        if (resourcePath == null) {
            return null;
        }
        String p = resourcePath.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }

        ClassLoader cl = loggingClassLoader;
        URL url = (cl != null) ? cl.getResource(p) : null;
        if (url == null && anchor != null) {
            url = anchor.getResource("/" + p);
        }

        if (url == null) {
            if (looksAuditable(resourcePath)) {
                LogSupport.enter(LOG, "looksAuditable");
                LOG.warning("RESOURCE NOT FOUND requestedPath=" + resourcePath);
            }
            return null;
        }

        String resolved = url.toExternalForm();
        logResolved("ClassLoader.resolve", resourcePath, resolved);
        return url;
    }

/**
 * addStylesheet.
 *
 * @param scene TODO
 * @param anchor TODO
 * @param resourcePath TODO
 */
    public static void addStylesheet(Scene scene, Class<?> anchor, String resourcePath) {
        LogSupport.enter(LOG, "addStylesheet");
        if (!ENABLED) {
            return;
        }
        if (scene == null || resourcePath == null || resourcePath.isBlank()) {
            return;
        }
        URL url = resourceUrl(anchor, resourcePath);
        if (url == null) {
            return;
        }
        String resolved = url.toExternalForm();
        scene.getStylesheets().add(resolved);
        logCssLoaded("Scene.addStylesheet", resourcePath, resolved);
        scanStylesheetForUrlReferences(resolved);
    }

/**
 * attachStylesheetAudit.
 *
 * @param scene TODO
 */
    public static void attachStylesheetAudit(Scene scene) {
        LogSupport.enter(LOG, "attachStylesheetAudit");
        if (!ENABLED) {
            return;
        }
        if (scene == null) {
            return;
        }

        for (String existing : scene.getStylesheets()) {
            logCssLoaded("Scene.stylesheets(existing)", null, existing);
            scanStylesheetForUrlReferences(existing);
        }

        scene.getStylesheets().addListener((ListChangeListener<String>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (String added : change.getAddedSubList()) {
                        logCssLoaded("Scene.stylesheets(added)", null, added);
                        scanStylesheetForUrlReferences(added);
                    }
                }
            }
        });
    }

/**
 * attachStylesheetAudit.
 *
 * @param parent TODO
 */
    public static void attachStylesheetAudit(Parent parent) {
        LogSupport.enter(LOG, "attachStylesheetAudit");
        if (!ENABLED) {
            return;
        }
        if (parent == null) {
            return;
        }

        for (String existing : parent.getStylesheets()) {
            logCssLoaded("Parent.stylesheets(existing)", null, existing);
            scanStylesheetForUrlReferences(existing);
        }

        parent.getStylesheets().addListener((ListChangeListener<String>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (String added : change.getAddedSubList()) {
                        logCssLoaded("Parent.stylesheets(added)", null, added);
                        scanStylesheetForUrlReferences(added);
                    }
                }
            }
        });
    }

/**
 * attachImageViewAudit.
 *
 * @param root TODO
 */
    public static void attachImageViewAudit(Parent root) {
        LogSupport.enter(LOG, "attachImageViewAudit");
        if (!ENABLED) {
            return;
        }
        if (root == null) {
            return;
        }
        walkAndAttachImageViews(root);
    }

/**
 * logFontLoaded.
 *
 * @param requestedPath TODO
 * @param resolvedUrl TODO
 * @param loadedFont TODO
 */
    public static void logFontLoaded(String requestedPath, URL resolvedUrl, Font loadedFont) {
        LogSupport.enter(LOG, "logFontLoaded");
        if (!ENABLED) {
            return;
        }
        if (resolvedUrl == null || loadedFont == null) {
            return;
        }
        String resolved = resolvedUrl.toExternalForm();
        String key = "FONT|" + resolved;
        if (SEEN.add(key)) {
            LOG.info("FONT loaded requestedPath=" + requestedPath
                    + " resolvedUrl=" + resolved
                    + " family=" + safeString(loadedFont.getFamily())
                    + " name=" + safeString(loadedFont.getName()));
        }
    }

/**
 * logImageLoaded.
 *
 * @param source TODO
 * @param requestedPath TODO
 * @param resolvedUrl TODO
 */
    public static void logImageLoaded(String source, String requestedPath, String resolvedUrl) {
        LogSupport.enter(LOG, "logImageLoaded");
        if (!ENABLED) {
            return;
        }
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            return;
        }

        String lower = resolvedUrl.toLowerCase();
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                || lower.endsWith(".bmp") || lower.endsWith(".webp") || lower.endsWith(".svg"))) {
            return;
        }

        String key = "IMG|" + resolvedUrl;
        if (SEEN.add(key)) {
            LOG.info("IMAGE loaded source=" + source
                    + " requestedPath=" + requestedPath
                    + " resolvedUrl=" + resolvedUrl);
        }
    }

/**
 * configureLogger.
 *
 */
    private static void configureLogger() {
        LogSupport.enter(LOG, "configureLogger");
        try {
            LOG.setLevel(Level.INFO);

            Handler[] hs = LOG.getHandlers();
            if (hs == null || hs.length == 0) {
                ConsoleHandler h = new ConsoleHandler();
                h.setLevel(Level.INFO);
                h.setFormatter(new SimpleConsoleFormatter());
                LOG.addHandler(h);
                LOG.setUseParentHandlers(false);
            }
        } catch (RuntimeException ex) {
            // ignore
        }
    }

/**
 * logCssLoaded.
 *
 * @param source TODO
 * @param requestedPath TODO
 * @param resolvedUrl TODO
 */
    private static void logCssLoaded(String source, String requestedPath, String resolvedUrl) {
        LogSupport.enter(LOG, "logCssLoaded");
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            return;
        }
        String lower = resolvedUrl.toLowerCase();
        if (!lower.endsWith(".css")) {
            return;
        }
        String key = "CSS|" + resolvedUrl;
        if (SEEN.add(key)) {
            LOG.info("CSS loaded source=" + source
                    + " requestedPath=" + requestedPath
                    + " resolvedUrl=" + resolvedUrl);
        }
    }

/**
 * logResolved.
 *
 * @param source TODO
 * @param requestedPath TODO
 * @param resolvedUrl TODO
 */
    private static void logResolved(String source, String requestedPath, String resolvedUrl) {
        LogSupport.enter(LOG, "logResolved");
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            return;
        }
        if (!looksAuditable(requestedPath) && !looksAuditable(resolvedUrl)) {
            LogSupport.enter(LOG, "looksAuditable");
            return;
        }

        String lower = resolvedUrl.toLowerCase();
        if (lower.endsWith(".css")) {
            logCssLoaded(source, requestedPath, resolvedUrl);
        } else if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc") || lower.endsWith(".woff") || lower.endsWith(".woff2")) {
            String key = "FONTRESOLVE|" + resolvedUrl;
            if (SEEN.add(key)) {
                LOG.info("FONT resolved source=" + source + " requestedPath=" + requestedPath + " resolvedUrl=" + resolvedUrl);
            }
        } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                || lower.endsWith(".bmp") || lower.endsWith(".webp") || lower.endsWith(".svg")) {
            logImageLoaded(source, requestedPath, resolvedUrl);
        }
    }

/**
 * looksAuditable.
 *
 * @param path TODO
 * @return TODO
 */
    private static boolean looksAuditable(String path) {
        LogSupport.enter(LOG, "looksAuditable");
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.endsWith(".css")
                || lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc") || lower.endsWith(".woff") || lower.endsWith(".woff2")
                || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                || lower.endsWith(".bmp") || lower.endsWith(".webp") || lower.endsWith(".svg");
    }

/**
 * safeString.
 *
 * @param v TODO
 * @return TODO
 */
    private static String safeString(String v) {
        LogSupport.enter(LOG, "safeString");
        return v == null ? "" : v;
    }

/**
 * scanStylesheetForUrlReferences.
 *
 * @param stylesheetExternalForm TODO
 */
    private static void scanStylesheetForUrlReferences(String stylesheetExternalForm) {
        LogSupport.enter(LOG, "scanStylesheetForUrlReferences");
        if (stylesheetExternalForm == null || stylesheetExternalForm.isBlank()) {
            return;
        }
        try {
            URL cssUrl = new URL(stylesheetExternalForm);
            scanStylesheet(cssUrl);
        } catch (MalformedURLException | RuntimeException _) {
            // ignore
        }
    }

/**
 * scanStylesheet.
 *
 * @param cssUrl TODO
 */
    private static void scanStylesheet(URL cssUrl) {
        LogSupport.enter(LOG, "scanStylesheet");
        if (cssUrl == null) {
            return;
        }

        String cssKey = cssUrl.toExternalForm();
        if (!SCANNED_STYLESHEETS.add(cssKey)) {
            return;
        }

        logCssLoaded("CSS.scan", null, cssKey);

        String cssText = readUrlAsString(cssUrl);
        if (cssText == null || cssText.isBlank()) {
            return;
        }

        String stripped = stripCssComments(cssText);

        for (String imp : extractImports(stripped)) {
            LogSupport.enter(LOG, "extractImports");
            URL imported = resolveCssReference(cssUrl, imp);
            if (imported != null) {
                scanStylesheet(imported);
            } else {
                LOG.warning("CSS import unresolved base=" + cssKey + " ref=" + imp);
            }
        }

        for (String ref : extractUrlFunctions(stripped)) {
            LogSupport.enter(LOG, "extractUrlFunctions");
            if (ref == null) {
                continue;
            }
            String cleaned = ref.trim();
            if (cleaned.isEmpty()) {
                continue;
            }

            if (cleaned.startsWith("#") || cleaned.regionMatches(true, 0, "data:", 0, 5)) {
                continue;
            }

            URL resolved = resolveCssReference(cssUrl, cleaned);
            if (resolved != null) {
                logResolved("CSS.url", cleaned, resolved.toExternalForm());
            } else {
                LOG.warning("CSS url unresolved base=" + cssKey + " ref=" + cleaned);
            }
        }
    }

/**
 * resolveCssReference.
 *
 * @param cssUrl TODO
 * @param ref TODO
 * @return TODO
 */
    private static URL resolveCssReference(URL cssUrl, String ref) {
        LogSupport.enter(LOG, "resolveCssReference");
        if (cssUrl == null || ref == null) {
            return null;
        }

        String trimmed = ref.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            URL u = resourceUrl(ResourceAudit.class, trimmed);
            if (u != null) {
                return u;
            }
        }

        try {
            return new URL(trimmed);
        } catch (MalformedURLException ex) {
            // fall through
        }

        try {
            return new URL(cssUrl, trimmed);
        } catch (MalformedURLException ex) {
            // fall through
        }

        try {
            String base = cssUrl.toExternalForm();
            int idx = base.lastIndexOf('/');
            if (idx >= 0) {
                String prefix = base.substring(0, idx + 1);
                return new URL(prefix + trimmed);
            }
        } catch (MalformedURLException | RuntimeException _) {
            // ignore
        }

        return null;
    }

/**
 * readUrlAsString.
 *
 * @param url TODO
 * @return TODO
 */
    private static String readUrlAsString(URL url) {
        LogSupport.enter(LOG, "readUrlAsString");
        try (InputStream in = url.openStream()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return "";
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException _) {
            return null;
        }
    }

/**
 * stripCssComments.
 *
 * @param css TODO
 * @return TODO
 */
    private static String stripCssComments(String css) {
        LogSupport.enter(LOG, "stripCssComments");
        if (css == null || css.isEmpty()) {
            return css;
        }

        StringBuilder out = new StringBuilder(css.length());
        int i = 0;
        int n = css.length();
        while (i < n) {
            char c = css.charAt(i);
            if (c == '/' && (i + 1) < n && css.charAt(i + 1) == '*') {
                i += 2;
                while (i < n) {
                    if (css.charAt(i) == '*' && (i + 1) < n && css.charAt(i + 1) == '/') {
                        i += 2;
                        break;
                    }
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

/**
 * extractImports.
 *
 * @param css TODO
 * @return TODO
 */
    private static List<String> extractImports(String css) {
        LogSupport.enter(LOG, "extractImports");
        List<String> imports = new ArrayList<>();
        if (css == null || css.isEmpty()) {
            return imports;
        }

        String lower = css.toLowerCase();
        int idx = 0;
        while (idx >= 0) {
            idx = lower.indexOf("@import", idx);
            if (idx < 0) {
                break;
            }

            int i = idx + 7;
            i = skipWhitespace(css, i);
            if (i >= css.length()) {
                break;
            }

            String ref;
            if (startsWithIgnoreCase(lower, i, "url(")) {
                LogSupport.enter(LOG, "startsWithIgnoreCase");
                ParsedFunction f = parseFunctionArgument(css, i + 4);
                ref = f.value;
                i = f.nextIndex;
            } else {
                ParsedString s = parseStringOrBare(css, i);
                ref = s.value;
                i = s.nextIndex;
            }

            if (ref != null && !ref.isBlank()) {
                imports.add(ref.trim());
            }

            int semi = css.indexOf(';', i);
            if (semi >= 0) {
                idx = semi + 1;
            } else {
                idx = i + 1;
            }
        }

        return imports;
    }

/**
 * extractUrlFunctions.
 *
 * @param css TODO
 * @return TODO
 */
    private static List<String> extractUrlFunctions(String css) {
        LogSupport.enter(LOG, "extractUrlFunctions");
        List<String> urls = new ArrayList<>();
        if (css == null || css.isEmpty()) {
            return urls;
        }

        String lower = css.toLowerCase();
        int idx = 0;
        while (idx >= 0) {
            idx = lower.indexOf("url(", idx);
            if (idx < 0) {
                break;
            }
            ParsedFunction f = parseFunctionArgument(css, idx + 4);
            if (f.value != null && !f.value.isBlank()) {
                urls.add(f.value.trim());
            }
            idx = Math.max(f.nextIndex, idx + 4);
        }

        return urls;
    }

/**
 * startsWithIgnoreCase.
 *
 * @param lower TODO
 * @param index TODO
 * @param tokenLower TODO
 * @return TODO
 */
    private static boolean startsWithIgnoreCase(String lower, int index, String tokenLower) {
        LogSupport.enter(LOG, "startsWithIgnoreCase");
        if (lower == null || tokenLower == null) {
            return false;
        }
        if (index < 0 || index + tokenLower.length() > lower.length()) {
            return false;
        }
        return lower.startsWith(tokenLower, index);
    }

/**
 * skipWhitespace.
 *
 * @param s TODO
 * @param i TODO
 * @return TODO
 */
    private static int skipWhitespace(String s, int i) {
        LogSupport.enter(LOG, "skipWhitespace");
        int n = s.length();
        int j = i;
        while (j < n) {
            char c = s.charAt(j);
            if (!Character.isWhitespace(c)) {
                break;
            }
            j++;
        }
        return j;
    }

/**
 * parseFunctionArgument.
 *
 * @param s TODO
 * @param startIndex TODO
 * @return TODO
 */
    private static ParsedFunction parseFunctionArgument(String s, int startIndex) {
        LogSupport.enter(LOG, "parseFunctionArgument");
        int i = startIndex;
        int n = s.length();
        i = skipWhitespace(s, i);

        if (i >= n) {
            return new ParsedFunction("", n);
        }

        String value;
        char first = s.charAt(i);
        if (first == '"' || first == '\'') {
            ParsedString ps = parseQuoted(s, i);
            value = ps.value;
            i = ps.nextIndex;
        } else {
            StringBuilder sb = new StringBuilder();
            while (i < n) {
                char c = s.charAt(i);
                if (c == ')') {
                    i++;
                    break;
                }
                sb.append(c);
                i++;
            }
            value = sb.toString().trim();
        }

        while (i < n && s.charAt(i) != ')') {
            i++;
        }
        if (i < n && s.charAt(i) == ')') {
            i++;
        }

        return new ParsedFunction(unquote(value), i);
    }

/**
 * parseStringOrBare.
 *
 * @param s TODO
 * @param startIndex TODO
 * @return TODO
 */
    private static ParsedString parseStringOrBare(String s, int startIndex) {
        LogSupport.enter(LOG, "parseStringOrBare");
        int i = skipWhitespace(s, startIndex);
        if (i >= s.length()) {
            return new ParsedString("", s.length());
        }

        char c = s.charAt(i);
        if (c == '"' || c == '\'') {
            ParsedString ps = parseQuoted(s, i);
            return new ParsedString(unquote(ps.value), ps.nextIndex);
        }

        StringBuilder sb = new StringBuilder();
        int n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch) || ch == ';') {
                break;
            }
            sb.append(ch);
            i++;
        }
        return new ParsedString(unquote(sb.toString()), i);
    }

/**
 * parseQuoted.
 *
 * @param s TODO
 * @param startIndex TODO
 * @return TODO
 */
    private static ParsedString parseQuoted(String s, int startIndex) {
        LogSupport.enter(LOG, "parseQuoted");
        int n = s.length();
        char quote = s.charAt(startIndex);
        int i = startIndex + 1;
        StringBuilder sb = new StringBuilder();

        while (i < n) {
            char c = s.charAt(i);
            if (c == quote) {
                i++;
                break;
            }
            if (c == '\\' && (i + 1) < n) {
                i++;
                sb.append(s.charAt(i));
                i++;
                continue;
            }
            sb.append(c);
            i++;
        }
        return new ParsedString(sb.toString(), i);
    }

/**
 * unquote.
 *
 * @param v TODO
 * @return TODO
 */
    private static String unquote(String v) {
        LogSupport.enter(LOG, "unquote");
        if (v == null) {
            return null;
        }
        String s = v.trim();
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

/**
 * walkAndAttachImageViews.
 *
 * @param node TODO
 */
    private static void walkAndAttachImageViews(Node node) {
        LogSupport.enter(LOG, "walkAndAttachImageViews");
        if (node == null) {
            return;
        }

        if (node instanceof ImageView iv) {
            Image current = iv.getImage();
            if (current != null && current.getUrl() != null && !current.getUrl().isBlank()) {
                logImageLoaded("ImageView", null, current.getUrl());
            }
            iv.imageProperty().addListener((_, _, newImg) -> {
                if (newImg != null && newImg.getUrl() != null && !newImg.getUrl().isBlank()) {
                    logImageLoaded("ImageView", null, newImg.getUrl());
                }
            });
        }

        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                walkAndAttachImageViews(child);
            }
        }
    }

    private static final class LoggingClassLoader extends ClassLoader {

        LoggingClassLoader(ClassLoader parent) {
            LogSupport.enter(LOG, "LoggingClassLoader");
            super(parent);
        }

        @Override
/**
 * getResource.
 *
 * @param name TODO
 * @return TODO
 */
        public URL getResource(String name) {
            LogSupport.enter(LOG, "getResource");
            URL url = super.getResource(name);
            if (url != null && looksAuditable(name)) {
                logResolved("ClassLoader.getResource", name, url.toExternalForm());
            }
            return url;
        }

        @Override
/**
 * getResourceAsStream.
 *
 * @param name TODO
 * @return TODO
 */
        public InputStream getResourceAsStream(String name) {
            LogSupport.enter(LOG, "getResourceAsStream");
            InputStream in = super.getResourceAsStream(name);
            URL url = super.getResource(name);
            if (url != null && looksAuditable(name)) {
                logResolved("ClassLoader.getResourceAsStream", name, url.toExternalForm());
            }
            return in;
        }
    }

    private static final class SimpleConsoleFormatter extends Formatter {
        @Override
/**
 * format.
 *
 * @param record TODO
 * @return TODO
 */
        public String format(LogRecord record) {
            LogSupport.enter(LOG, "format");
            return record.getLevel().getName() + " " + record.getLoggerName()
                    + " - " + formatMessage(record) + System.lineSeparator();
        }
    }

    private record ParsedFunction(String value, int nextIndex) {
        private ParsedFunction {
            LogSupport.enter(LOG, "ParsedFunction");
        }
        }

    private record ParsedString(String value, int nextIndex) {
        private ParsedString {
            LogSupport.enter(LOG, "ParsedString");
        }
        }
}
