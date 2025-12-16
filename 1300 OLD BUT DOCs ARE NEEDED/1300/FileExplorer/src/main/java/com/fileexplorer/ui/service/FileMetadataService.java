package com.fileexplorer.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * File-system metadata and directory listing helpers with robust fallbacks.
 */
public final class FileMetadataService {

    private static final DateTimeFormatter MODIFIED_FORMAT =
            DateTimeFormatter.ofPattern("MMMM, d, yyyy", Locale.US);

    public FileMetadataService() {
    }

    public ObservableList<Path> listDirectory(Path dir) {
        Objects.requireNonNull(dir, "dir");

        List<Path> results = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (p != null) {
                    results.add(p);
                }
            }
        } catch (IOException ex) {
            // Return empty list on failure.
        }

        results.sort(new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                boolean ad = isDirectorySafe(a);
                boolean bd = isDirectorySafe(b);
                if (ad != bd) {
                    return ad ? -1 : 1;
                }
                String an = displayName(a);
                String bn = displayName(b);
                return an.compareToIgnoreCase(bn);
            }
        });

        return FXCollections.observableArrayList(results);
    }

    public boolean isDirectorySafe(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        } catch (SecurityException ex) {
            return false;
        }
    }

    public String displayName(Path path) {
        if (path == null) {
            return "";
        }
        Path fn = path.getFileName();
        if (fn == null) {
            return path.toString();
        }
        String s = fn.toString();
        return s.isEmpty() ? path.toString() : s;
    }

    public String displayPathForStatus(Path path) {
        if (path == null) {
            return "";
        }
        try {
            return path.toAbsolutePath().normalize().toString();
        } catch (Exception ex) {
            return path.toString();
        }
    }

    public String detectFileType(Path path) {
        if (path == null) {
            return "";
        }
        if (isDirectorySafe(path)) {
            return "File folder";
        }

        String name = displayName(path);
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "File";
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);

        switch (ext) {
            case "txt":
            case "log":
            case "md":
                return "Text document";
            case "pdf":
                return "PDF document";
            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
            case "bmp":
            case "webp":
                return "Image";
            case "mp3":
            case "wav":
            case "flac":
            case "aac":
            case "m4a":
                return "Audio";
            case "mp4":
            case "mkv":
            case "mov":
            case "avi":
            case "wmv":
                return "Video";
            case "zip":
            case "7z":
            case "rar":
            case "tar":
            case "gz":
                return "Compressed (zipped) folder";
            case "exe":
                return "Application";
            case "msi":
                return "Windows Installer package";
            case "doc":
            case "docx":
                return "Microsoft Word document";
            case "xls":
            case "xlsx":
                return "Microsoft Excel worksheet";
            case "ppt":
            case "pptx":
                return "Microsoft PowerPoint presentation";
            default:
                return ext.toUpperCase(Locale.ROOT) + " file";
        }
    }

    public String humanReadableSize(Path path) {
        if (path == null) {
            return "";
        }
        if (isDirectorySafe(path)) {
            return "";
        }

        try {
            long size = Files.size(path);
            return formatBytes(size);
        } catch (IOException | SecurityException ex) {
            return "";
        }
    }

    public String lastModifiedLocalString(Path path) {
        if (path == null) {
            return "";
        }
        try {
            BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Instant lm = a.lastModifiedTime().toInstant();
            LocalDateTime ldt = LocalDateTime.ofInstant(lm, ZoneId.systemDefault());
            return MODIFIED_FORMAT.format(ldt);
        } catch (IOException | SecurityException ex) {
            return "";
        }
    }

    public String describeForStatusBar(Path path) {
        if (path == null) {
            return "";
        }
        String name = displayName(path);
        String type = detectFileType(path);
        String size = humanReadableSize(path);
        if (size.isEmpty()) {
            return name + " \u2014 " + type;
        }
        return name + " \u2014 " + type + " \u2014 " + size;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "";
        }
        final String[] units = new String[] {"B", "KB", "MB", "GB", "TB"};
        double v = (double) bytes;
        int idx = 0;
        while (v >= 1024.0 && idx < units.length - 1) {
            v = v / 1024.0;
            idx++;
        }

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setGroupingUsed(true);

        if (idx == 0) {
            return nf.format((long) v) + " " + units[idx];
        }
        nf.setMaximumFractionDigits(v >= 10.0 ? 1 : 2);
        nf.setMinimumFractionDigits(0);
        return nf.format(v) + " " + units[idx];
    }
}
