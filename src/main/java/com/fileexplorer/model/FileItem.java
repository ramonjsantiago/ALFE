package com.fileexplorer.model;

import java.nio.file.Path;
import java.util.Objects;

public final class FileItem {

    private final Path path;
    private final String name;
    private final String type;
    private final String size;
    private final String modified;
    private final FileStatus status;

    public FileItem(Path path, String name, String type, String size, String modified, FileStatus status) {
        this.path = Objects.requireNonNull(path, "path");
        this.name = Objects.requireNonNullElse(name, "");
        this.type = Objects.requireNonNullElse(type, "");
        this.size = Objects.requireNonNullElse(size, "");
        this.modified = Objects.requireNonNullElse(modified, "");
        this.status = Objects.requireNonNullElse(status, FileStatus.NONE);
    }

    public Path path() { return path; }
    public String name() { return name; }
    public String type() { return type; }
    public String size() { return size; }
    public String modified() { return modified; }
    public FileStatus status() { return status; }
}
