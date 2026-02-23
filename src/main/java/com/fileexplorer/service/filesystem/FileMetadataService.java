package com.fileexplorer.service.filesystem;

import com.fileexplorer.util.IconLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

/**
 * File system metadata helpers.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>Extension-aware icon identity (e.g. {@code ext:pdf}, {@code ext:txt}) to support extension-level caching.</li>
 *   <li>Extension-first icon type mapping so icon selection is stable and fast (no per-file MIME sniffing required).</li>
 * </ul>
 */
public final class FileMetadataService {
    private static final Logger LOG = Logger.getLogger(FileMetadataService.class.getName());

    private static final DecimalFormat SIZE_FMT = new DecimalFormat("#,##0.#");

    /**
     * Windows-style timestamp (Explorer-like): MM/DD/YYYY h:mm AM/PM
     */
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a", Locale.US);

/**
 * listDirectory.
 *
 * @param dir TODO
 * @return TODO
 */
    public List<Path> listDirectory(Path dir) {
        LogSupport.enter(LOG, "listDirectory");
        if (dir == null || !safeIsDirectory(dir) || !Files.isReadable(dir)) {
            return List.of();
        }

        // Use a DirectoryStream (lower overhead than Files.list) and precompute
        // stable sort keys once to avoid allocation-heavy per-compare lowercasing.
/**
 * DirEntry.
 * <p>
 * Auto-generated API documentation for this type.
 */
        record DirEntry(Path path, boolean isDir, String name) {}

        List<DirEntry> entries = new ArrayList<>(2048);
        try (var ds = Files.newDirectoryStream(dir)) {
            for (Path pth : ds) {
                if (pth == null) {
                    continue;
                }
                boolean isDir = safeIsDirectory(pth);
                String name = displayName(pth);
                entries.add(new DirEntry(pth, isDir, name));
            }
        } catch (IOException | SecurityException ex) {
            return List.of();
        }

        entries.sort(Comparator
                .comparing((DirEntry e) -> !e.isDir())
                .thenComparing(DirEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DirEntry::name));

        List<Path> out = new ArrayList<>(entries.size());
        for (DirEntry e : entries) {
            out.add(e.path());
        }
        return out;
    }

/**
 * Result of a capped directory listing to avoid runaway memory usage on unusually large folders.
 */
public record DirectoryListing(List<Path> entries, boolean truncated, int limit) {
}

/**
 * Lists immediate children of {@code dir} with an upper bound on returned entries.
 *
 * <p>Configure via -Dfileexplorer.maxDirEntries=N (default 200000). This limit is a defensive safety cap,
 * primarily for environments where a directory can contain an extremely large number of entries (e.g.,
 * certain network shares or virtualized mounts).</p>
 */
public DirectoryListing listDirectoryLimited(Path dir, boolean includeHidden, int limit) {
        LogSupport.enter(LOG, "listDirectoryLimited");
        if (limit <= 0) {
            return new DirectoryListing(List.of(), false, limit);
        }
        if (dir == null || !safeIsDirectory(dir) || !Files.isReadable(dir)) {
            return new DirectoryListing(List.of(), false, limit);
        }

        // Hard clamp to prevent accidental runaway memory use on unusually large folders.
        // Users can still raise fileexplorer.maxDirEntries, but we cap it here defensively.
        final int hardMax = Integer.getInteger("fileexplorer.maxDirEntries.hardMax", 25_000);
        final int capped = Math.max(1, Math.min(limit, Math.max(1, hardMax)));

        record DirEntry(Path path, boolean isDir, String name) {}

        List<DirEntry> entries = new ArrayList<>(Math.min(capped, 2048));
        boolean truncated = false;

        try (var ds = Files.newDirectoryStream(dir)) {
            for (Path pth : ds) {
                if (pth == null) {
                    continue;
                }
                if (!includeHidden && isHiddenSafe(pth)) {
                    continue;
                }
                boolean isDir = safeIsDirectory(pth);
                String name = displayName(pth);
                entries.add(new DirEntry(pth, isDir, name));

                if (entries.size() >= capped) {
                    truncated = true;
                    break;
                }
            }
        } catch (IOException | SecurityException ex) {
            return new DirectoryListing(List.of(), false, capped);
        }

        entries.sort(Comparator
                .comparing((DirEntry e) -> !e.isDir())
                .thenComparing(DirEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DirEntry::name));

        List<Path> out = new ArrayList<>(entries.size());
        for (DirEntry e : entries) {
            out.add(e.path());
        }

        return new DirectoryListing(out, truncated, capped);
    }

/**
 * isHiddenSafe.
 *
 * @param p TODO
 * @return TODO
 */
private boolean isHiddenSafe(Path p) {
    if (p == null) {
        return false;
    }
    try {
        return Files.isHidden(p);
    } catch (IOException ex) {
        return false;
    } catch (SecurityException ex) {
        return false;
    }
}

/**
 * displayName.
 *
 * @param p TODO
 * @return TODO
 */
    public String displayName(Path p) {
        LogSupport.enter(LOG, "displayName");
        if (p == null) {
            return "";
        }
        Path fn = p.getFileName();
        if (fn != null) {
            return fn.toString();
        }
        return p.toString();
    }

    /**
     * Human-friendly type string for the Details table.
     */
    public String detectFileType(Path p) {
        LogSupport.enter(LOG, "detectFileType");
        if (p == null) {
            return "";
        }

        if (safeIsDirectory(p)) {
            LogSupport.enter(LOG, "safeIsDirectory");
            return "Folder";
        }

        String ext = extensionLower(p);
        if (!ext.isBlank()) {
            return ext.toUpperCase(Locale.ROOT) + " File";
        }

        // Fallback for display only.
        try {
            String mime = Files.probeContentType(p);
            if (mime != null && !mime.isBlank()) {
                return mime;
            }
        } catch (IOException ex) {
            // ignore
        }

        return "File";
    }

/**
 * humanReadableSize.
 *
 * @param p TODO
 * @return TODO
 */
    public String humanReadableSize(Path p) {
        LogSupport.enter(LOG, "humanReadableSize");
        if (p == null) {
            return "";
        }
        try {
            if (Files.isDirectory(p)) {
                return "";
            }
            long size = Files.size(p);
            return formatBytes(size);
        } catch (Exception ex) {
            return "";
        }
    }

/**
 * lastModifiedLocalString.
 *
 * @param p TODO
 * @return TODO
 */
    public String lastModifiedLocalString(Path p) {
        LogSupport.enter(LOG, "lastModifiedLocalString");
        if (p == null) {
            return "";
        }
        try {
            FileTime ft = Files.getLastModifiedTime(p);
            Instant instant = ft.toInstant();
            LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            return TS_FMT.format(ldt);
        } catch (Exception ex) {
            return "";
        }
    }

/**
 * describeForStatusBar.
 *
 * @param p TODO
 * @return TODO
 */
    public String describeForStatusBar(Path p) {
        LogSupport.enter(LOG, "describeForStatusBar");
        if (p == null) {
            return "Ready.";
        }
        String name = displayName(p);
        String type = detectFileType(p);
        String size = humanReadableSize(p);
        String mod = lastModifiedLocalString(p);

        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (!type.isBlank()) {
            sb.append("  |  ").append(type);
        }
        if (!size.isBlank()) {
            sb.append("  |  ").append(size);
        }
        if (!mod.isBlank()) {
            sb.append("  |  ").append(mod);
        }
        return sb.toString();
    }

/**
 * displayPathForStatus.
 *
 * @param p TODO
 * @return TODO
 */
    public String displayPathForStatus(Path p) {
        LogSupport.enter(LOG, "displayPathForStatus");
        if (p == null) {
            return "";
        }
        return p.toString();
    }

    // ---------------------------------------------------------------------
    // Icon identity + mapping (identity drives selection + caching)
    // ---------------------------------------------------------------------

    /**
     * Stable identity string used for icon caching and selection.
     * <p>
     * Rules:
     *  - Directories: type:FOLDER
     *  - Files with extension: ext:<lowercase-extension> (e.g., ext:pdf)
     *  - Files without extension: type:<IconType>
     */
    public String iconIdentity(Path p) {
        LogSupport.enter(LOG, "iconIdentity");
        if (p == null) {
            return "type:" + IconLoader.IconType.FILE.name();
        }

        if (safeIsDirectory(p)) {
            LogSupport.enter(LOG, "safeIsDirectory");
            return "type:" + IconLoader.IconType.FOLDER.name();
        }

        String ext = extensionLower(p);
        if (!ext.isBlank()) {
            return "ext:" + ext;
        }

        IconLoader.IconType t = iconTypeFor(p);
        if (t == null) {
            t = IconLoader.IconType.FILE;
        }
        return "type:" + t.name();
    }

    /**
     * Extension-first icon type mapping. Uses extension when present; only falls back to MIME probing when no extension.
     */
    public IconLoader.IconType iconTypeFor(Path p) {
        LogSupport.enter(LOG, "iconTypeFor");
        if (p == null) {
            return IconLoader.IconType.FILE;
        }

        if (safeIsDirectory(p)) {
            LogSupport.enter(LOG, "safeIsDirectory");
            return IconLoader.IconType.FOLDER;
        }

        String ext = extensionLower(p);
        if (!ext.isBlank()) {
            return iconTypeForExtension(ext);
        }

        // Fallback: MIME only when extension is missing.
        String mime = null;
        try {
            mime = Files.probeContentType(p);
        } catch (IOException ex) {
            // ignore
        }

        if (mime != null && !mime.isBlank()) {
            String m = mime.toLowerCase(Locale.ROOT);
            if (m.startsWith("image/")) {
                return IconLoader.IconType.IMAGE;
            }
            if (m.startsWith("audio/")) {
                return IconLoader.IconType.AUDIO;
            }
            if (m.startsWith("video/")) {
                return IconLoader.IconType.VIDEO;
            }
            if ("application/pdf".equals(m)) {
                return IconLoader.IconType.PDF;
            }
            if (m.startsWith("text/")) {
                return IconLoader.IconType.TEXT;
            }
        }

        return IconLoader.IconType.FILE;
    }

    /**
     * Maps a lowercase extension (without dot) to an icon type.
     */
    public IconLoader.IconType iconTypeForExtension(String extLower) {
        LogSupport.enter(LOG, "iconTypeForExtension");
        if (extLower == null || extLower.isBlank()) {
            return IconLoader.IconType.FILE;
        }

        String e = extLower.toLowerCase(Locale.ROOT);

/**
 * switch.
 *
 * @param e TODO
 * @return TODO
 */
        return switch (e) {
            case "pdf" -> IconLoader.IconType.PDF;
            case "png",
                 "jpg",
                 "jpeg",
                 "gif",
                 "bmp",
                 "webp",
                 "tif",
                 "tiff",
                 "svg",
                 "ico" -> IconLoader.IconType.IMAGE;
            case "txt",
                 "md",
                 "log",
                 "rtf",
                 "ini",
                 "cfg",
                 "csv",
                 "json",
                 "xml",
                 "yaml",
                 "yml" ->
                    IconLoader.IconType.TEXT;
            case "zip", "7z", "rar", "tar", "gz", "bz2", "xz" -> IconLoader.IconType.ARCHIVE;
            case "mp3", "wav", "flac", "m4a", "ogg", "aac", "wma" -> IconLoader.IconType.AUDIO;
            case "mp4", "mkv", "mov", "avi", "wmv", "webm", "m4v" -> IconLoader.IconType.VIDEO;
            default -> IconLoader.IconType.FILE;
        };

    }

    /**
     * Returns the lowercase extension for the provided path, excluding the dot. If no extension is present, returns "".
     */
    public String extensionLower(Path p) {
        LogSupport.enter(LOG, "extensionLower");
        if (p == null) {
            return "";
        }

        String n = (p.getFileName() != null) ? p.getFileName().toString() : p.toString();

        int lastSep = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        int lastDot = n.lastIndexOf('.');
        if (lastDot <= lastSep || lastDot < 0 || lastDot == n.length() - 1) {
            return "";
        }

        return n.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

/**
 * safeIsDirectory.
 *
 * @param p TODO
 * @return TODO
 */
    private static boolean safeIsDirectory(Path p) {
        LogSupport.enter(LOG, "safeIsDirectory");
        try {
            return p != null && Files.isDirectory(p);
        } catch (Exception ex) {
            return false;
        }
    }

/**
 * formatBytes.
 *
 * @param bytes TODO
 * @return TODO
 */
    private static String formatBytes(long bytes) {
        LogSupport.enter(LOG, "formatBytes");
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double v = bytes;
        String[] units = new String[]{"B", "KB", "MB", "GB", "TB", "PB"};
        int u = 0;
        while (v >= 1024.0 && u < units.length - 1) {
            v /= 1024.0;
            u++;
        }
        return SIZE_FMT.format(v) + " " + units[u];
    }
}
