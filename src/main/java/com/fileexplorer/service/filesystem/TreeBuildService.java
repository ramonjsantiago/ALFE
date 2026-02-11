package com.fileexplorer.service.filesystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import java.util.logging.Logger;
import com.fileexplorer.util.LogSupport;

public final class TreeBuildService {

    /**
     * Marker for TreeItems that lazily load their children.
     * UI helpers can call ensureChildrenLoaded() before expanding.
     */
    public interface LazyLoadingTreeItem {
        void ensureChildrenLoaded();
        boolean isChildrenLoaded();
        /**
         * Reset the node so it will re-probe and/or reload children.
         * Used by Refresh to keep chevrons/children accurate.
         */
        void invalidate();
    }


    private static final Logger LOG = Logger.getLogger(TreeBuildService.class.getName());

    // Root-level noise / pseudo-filesystems.
    // Applies only at the first level under a filesystem root (e.g., C:\$Recycle.Bin or /proc).
    private static final Set<String> ROOT_DENY_NAMES = new HashSet<>();

    static {
        // Windows
        ROOT_DENY_NAMES.add("$recycle.bin");
        ROOT_DENY_NAMES.add("system volume information");
        ROOT_DENY_NAMES.add("recovery");
        ROOT_DENY_NAMES.add("msocache");

        // Linux pseudo/virtual FS that frequently causes stalls or permission churn
        ROOT_DENY_NAMES.add("proc");
        ROOT_DENY_NAMES.add("sys");
        ROOT_DENY_NAMES.add("dev");
        ROOT_DENY_NAMES.add("run");
        ROOT_DENY_NAMES.add("snap");
        ROOT_DENY_NAMES.add("lost+found");
    }

/**
 * Hard safety cap to prevent runaway memory usage when a directory contains an unusually large number
 * of immediate child folders (e.g., certain network shares or system volumes).
 *
 * Configure via -Dfileexplorer.maxTreeChildDirs=N (default 5000).
 */
private static final int MAX_TREE_CHILD_DIRS =
        Integer.getInteger("fileexplorer.maxTreeChildDirs", 2000);

/**
 * A non-navigable informational row inside the navigation tree (e.g., to indicate truncation).
 */
public static final class MessageTreeItem extends TreeItem<Path> {
    private final String message;

    public MessageTreeItem(String message) {
        super(null);
        this.message = (message == null) ? "" : message;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }
}
    public TreeItem<Path> buildComputerRoot() {
        LogSupport.enter(LOG, "buildComputerRoot");
        TreeItem<Path> computer = new TreeItem<>(null);
        computer.setExpanded(true);

        Iterable<Path> roots = FileSystems.getDefault().getRootDirectories();
        List<TreeItem<Path>> rootItems = new ArrayList<>();

        for (Path r : roots) {
            if (r == null) {
                continue;
            }
            rootItems.add(new LazyDirTreeItem(r, true));
        }

        rootItems.sort(Comparator.comparing(a -> safeString(a.getValue())));
        computer.getChildren().setAll(rootItems);

        return computer;
    }

    /**
     * Compatibility alias for callers that expect a rebuildTree() entry point.
     *
     * @return the rebuilt root tree item
     */
    public TreeItem<Path> rebuildTree() {
        LogSupport.enter(LOG, "rebuildTree");
        return buildComputerRoot();
    }


    public String toDisplayName(Path path, TreeItem<Path> treeItem) {
        LogSupport.enter(LOG, "toDisplayName");
        if (treeItem != null && treeItem.getParent() == null) {
            return "Computer";
        }
        if (path == null) {
            return "";
        }

        if (path.getFileName() == null) {
            return describeRoot(path);
        }

        return path.getFileName().toString();
    }

    /**
     * Constant-time: find the filesystem root TreeItem that contains the target.
     * Does not traverse descendants and therefore will not force lazy children to load.
     */
    public TreeItem<Path> findContainingRootItem(TreeItem<Path> computerRoot, Path target) {
        LogSupport.enter(LOG, "findContainingRootItem");
        if (computerRoot == null || target == null) {
            return null;
        }
        Path normTarget = target.normalize();

        for (TreeItem<Path> child : computerRoot.getChildren()) {
            Path root = child.getValue();
            if (root != null && normTarget.startsWith(root.normalize())) {
                return child;
            }
        }
        return null;
    }

    private static String safeString(Path p) {
        LogSupport.enter(LOG, "safeString");
        return p == null ? "" : p.toString();
    }

    private static String describeRoot(Path root) {
        LogSupport.enter(LOG, "describeRoot");
        String drive = root.toString();

        if (drive.length() >= 3 && drive.charAt(1) == ':' && (drive.endsWith("\\") || drive.endsWith("/"))) {
            drive = drive.substring(0, 2);
        }

        String storeName = "";
        try {
            FileStore fs = Files.getFileStore(root);
            String n = fs.name();
            if (n != null) {
                storeName = n.trim();
            }
        } catch (Exception ex) {
            // ignore
        }

        if (!storeName.isBlank() && !storeName.equalsIgnoreCase(drive)) {
            return storeName + " (" + drive + ")";
        }
        return drive;
    }

        private static final class LazyDirTreeItem extends TreeItem<Path> implements LazyLoadingTreeItem {
        private final TreeItem<Path> placeholder = new TreeItem<>(null);

        private final boolean isRootChild;
        private volatile boolean childrenLoaded;
        private volatile boolean childrenLoading;

        private LazyDirTreeItem(Path value, boolean isRootChild) {
            LogSupport.enter(LOG, "LazyDirTreeItem");
            super(value);
            this.isRootChild = isRootChild;
            this.childrenLoaded = false;
            this.childrenLoading = false;

            // Cheap pre-check: only show the disclosure chevron if we can quickly confirm at least
            // one visible sub-directory exists. This matches Windows Explorer behavior (no chevron
            // on empty directories) without doing a full enumeration.
            if (hasAnyVisibleChildDirectory(value, isRootChild)) {
                // Placeholder child so the disclosure node appears without triggering full enumeration.
                super.getChildren().add(placeholder);
            } else {
                // No visible children: treat as loaded so isLeaf() can return true immediately.
                this.childrenLoaded = true;
            }

            // Load children only when the item is expanded.
            expandedProperty().addListener((_, _, isExpanded) -> {
                if (isExpanded) {
                    ensureChildrenLoaded();
                }
            });

            setExpanded(false);
        }

        /**
         * Fast probe: returns true if the directory appears to contain at least one eligible
         * sub-directory. Stops after the first match.
         */
        private boolean hasAnyVisibleChildDirectory(Path dir, boolean parentIsRootChild) {
            if (dir == null) {
                return false;
            }
            try {
                if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(dir)) {
                    return false;
                }
            } catch (Exception ex) {
                return false;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path p : stream) {
                    try {
                        if (p == null || !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                            continue;
                        }
                        if (parentIsRootChild && denyRootNoise(dir, p)) {
                            continue;
                        }
                        if (isHiddenSafe(p)) {
                            continue;
                        }
                        return true; // stop at first qualifying child
                    } catch (Exception ex) {
                        // ignore per-entry failures
                    }
                }
            } catch (Exception ex) {
                return false;
            }

            return false;
        }

        @Override
        public boolean isLeaf() {
            Path v = getValue();
            if (v == null) {
                return false;
            }

            // For lazy directory nodes we intentionally avoid enumerating children until the user expands.
            // However, once children have been loaded we can accurately report leaf-ness so empty folders
            // do not display a disclosure chevron (Explorer-like behavior).
            if (!Files.isDirectory(v, LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
            if (childrenLoaded) {
                return super.getChildren().isEmpty();
            }
            return false;
        }

        @Override
        public ObservableList<TreeItem<Path>> getChildren() {
            // Do NOT enumerate on getChildren(); keep strictly lazy (expand-only).
            return super.getChildren();
        }

        @Override
        public void ensureChildrenLoaded() {
            LogSupport.enter(LOG, "ensureChildrenLoadedAsync");
            if (childrenLoaded || childrenLoading) {
                return;
            }
            childrenLoading = true;

            CompletableFuture
                    .supplyAsync(() -> loadChildren(getValue(), isRootChild))
                    .handle((children, err) -> {
                        if (err != null) {
                            return List.<TreeItem<Path>>of();
                        }
                        return (children != null) ? children : List.<TreeItem<Path>>of();
                    })
                    .thenAccept(children -> Platform.runLater(() -> {
                        try {
                            if (!childrenLoaded) {
                                super.getChildren().setAll(children);
                            }
                        } finally {
                            childrenLoaded = true;
                            childrenLoading = false;
                        }
                    }));
        }

        @Override
        public boolean isChildrenLoaded() {
            return childrenLoaded;
        }




        @Override
                public void invalidate() {
                    Path v = getValue();
                    // Reset lazy state and re-run the cheap "has any child dir" probe so the disclosure node
                    // matches Explorer behavior after filesystem changes.
                    childrenLoaded = false;
                    childrenLoading = false;
                    Platform.runLater(() -> {
                        try {
                            setExpanded(false);
                            super.getChildren().clear();
        
                            if (v != null && hasAnyVisibleChildDirectory(v, isRootChild)) {
                                super.getChildren().add(placeholder);
                            } else {
                                // No visible children: treat as loaded so isLeaf() can return true immediately.
                                childrenLoaded = true;
                            }
                        } catch (Exception ex) {
                            // fall back to leaf behavior
                            childrenLoaded = true;
                        }
                    });
                }
        
        private List<TreeItem<Path>> loadChildren(Path dir, boolean parentIsRootChild) {
    LogSupport.enter(LOG, "loadChildren");
    if (dir == null || !Files.isDirectory(dir) || !Files.isReadable(dir)) {
        return List.of();
    }

    List<Path> dirs = new ArrayList<>();
    boolean truncated = false;

    // Enumerate only immediate sub-directories. Guard against directories with an unusually large number
    // of children (common on some network shares) to avoid exhausting heap.
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
        for (Path p : stream) {
            if (!Files.isDirectory(p)) {
                continue;
            }
            if (parentIsRootChild && denyRootNoise(dir, p)) {
                continue;
            }
            if (isHiddenSafe(p)) {
                continue;
            }

            dirs.add(p);

            if (dirs.size() >= MAX_TREE_CHILD_DIRS) {
                truncated = true;
                break;
            }
        }
    } catch (IOException ex) {
        return List.of();
    }

    dirs.sort(Comparator.comparing(LazyDirTreeItem::dirSortKey, String.CASE_INSENSITIVE_ORDER));

    int extra = truncated ? 1 : 0;
    List<TreeItem<Path>> out = new ArrayList<>(dirs.size() + extra);
    for (Path p : dirs) {
        out.add(new LazyDirTreeItem(p, false));
    }

    if (truncated) {
        out.add(new MessageTreeItem("… more folders not shown (limit " + MAX_TREE_CHILD_DIRS + ")"));
    }

    return out;
}

        private boolean denyRootNoise(Path parentDir, Path child) {
            LogSupport.enter(LOG, "denyRootNoise");
            if (!isRootChild) {
                return false;
            }
            if (parentDir.getFileName() != null) {
                return false; // only apply deny-list directly under filesystem root
            }
            Path n = child.getFileName();
            if (n == null) {
                return false;
            }
            String lower = n.toString().toLowerCase(Locale.ROOT);
            return ROOT_DENY_NAMES.contains(lower);
        }

        private static boolean isHiddenSafe(Path p) {
            LogSupport.enter(LOG, "isHiddenSafe");
            try {
                return Files.isHidden(p);
            } catch (Exception ex) {
                return false;
            }
        }

        private static String dirSortKey(Path p) {
            LogSupport.enter(LOG, "dirSortKey");
            Path n = p.getFileName();
            String s = (n == null) ? p.toString() : n.toString();
            return s.toLowerCase(Locale.ROOT);
        }
    }

}
