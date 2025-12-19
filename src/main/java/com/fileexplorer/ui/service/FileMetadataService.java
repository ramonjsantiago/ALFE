package com.fileexplorer.ui.service;

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

public final class FileMetadataService {

    private static final DecimalFormat SIZE_FMT = new DecimalFormat("#,##0.#");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<Path> listDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir) || !Files.isReadable(dir)) {
            return List.of();
        }

        List<Path> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.forEach(out::add);
        } catch (IOException ex) {
            return List.of();
        }

        out.sort(new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                boolean ad = Files.isDirectory(a);
                boolean bd = Files.isDirectory(b);
                if (ad != bd) {
                    return ad ? -1 : 1; // directories first
                }
                String an = displayName(a).toLowerCase();
                String bn = displayName(b).toLowerCase();
                return an.compareTo(bn);
            }
        });

        return out;
    }

    public String displayName(Path p) {
        if (p == null) {
            return "";
        }
        Path fn = p.getFileName();
        if (fn != null) {
            return fn.toString();
        }
        return p.toString();
    }

    public String detectFileType(Path p) {
        if (p == null) {
            return "";
        }
        try {
            if (Files.isDirectory(p)) {
                return "Folder";
            }
        } catch (Exception ex) {
            // ignore
        }

        String ext = extensionLower(p);
        if (!ext.isBlank()) {
            return ext.toUpperCase() + " File";
        }

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

    public String humanReadableSize(Path p) {
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

    public String lastModifiedLocalString(Path p) {
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

    public String describeForStatusBar(Path p) {
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

    public String displayPathForStatus(Path p) {
        if (p == null) {
            return "";
        }
        return p.toString();
    }

    private static String extensionLower(Path p) {
        String n = p == null ? "" : p.getFileName() == null ? p.toString() : p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        if (dot < 0 || dot == n.length() - 1) {
            return "";
        }
        return n.substring(dot + 1).toLowerCase();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double v = bytes;
        String[] units = new String[] { "B", "KB", "MB", "GB", "TB", "PB" };
        int u = 0;
        while (v >= 1024.0 && u < units.length - 1) {
            v /= 1024.0;
            u++;
        }
        return SIZE_FMT.format(v) + " " + units[u];
    }
}
