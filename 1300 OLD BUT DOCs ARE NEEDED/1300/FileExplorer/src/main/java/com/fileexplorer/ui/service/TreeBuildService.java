package com.fileexplorer.service;

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
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

public final class TreeBuildService {

    // Root-level noise / pseudo-filesystems.
    // Applies only at the first level under a filesystem root (e.g., C:\$Recycle.Bin or /proc).
    private static final Set<String> ROOT_DENY_NAMES = new HashSet<>();

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

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

    public TreeItem<Path> buildComputerRoot() {
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

    public String toDisplayName(Path path, TreeItem<Path> treeItem) {
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
        if (computerRoot == null || target == null) {
            return null;
        }
        Path normTarget = target.normalize();

        for (TreeItem<Path> child : computerRoot.getChildren()) {
            Path root = child.getValue();
            if (root == null) {
                continue;
            }
            if (pathStartsWith(normTarget, root.normalize())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Finds the best initial selection for the TreeView.
     *
     * On Windows, this prefers the user's home folder (e.g., C:\Users\...\),
     * but will fall back safely without forcing deep traversal if not found.
     */
    public TreeItem<Path> findBestInitialSelection(TreeItem<Path> computerRoot) {
        if (computerRoot == null) {
            return null;
        }

        // Prefer user's home directory if it exists and is under a known root.
        try {
            String homeProp = System.getProperty("user.home");
            if (homeProp != null && !homeProp.isBlank()) {
                Path home = Path.of(homeProp).toAbsolutePath().normalize();
                TreeItem<Path> homeItem = findPathItem(computerRoot, home);
                if (homeItem != null) {
                    return homeItem;
                }

                TreeItem<Path> rootItem = findContainingRootItem(computerRoot, home);
                if (rootItem != null) {
                    return rootItem;
                }
            }
        } catch (Exception ex) {
            // ignore and fall back
        }

        if (!computerRoot.getChildren().isEmpty()) {
            return computerRoot.getChildren().get(0);
        }
        return computerRoot;
    }

    /**
     * Finds a TreeItem for the given target path by walking down from the filesystem root.
     * This may cause lazy nodes to load children for directories along the path.
     */
    public TreeItem<Path> findPathItem(TreeItem<Path> computerRoot, Path target) {
        if (computerRoot == null || target == null) {
            return null;
        }

        Path t = normalizeAbs(target);
        TreeItem<Path> rootItem = findContainingRootItem(computerRoot, t);
        if (rootItem == null) {
            return null;
        }

        Path rootPath = rootItem.getValue();
        if (rootPath != null && samePath(normalizeAbs(rootPath), t)) {
            return rootItem;
        }

        TreeItem<Path> current = rootItem;

        // Guard against accidental loops.
        for (int guard = 0; guard < 512; guard++) {
            Path curPath = current.getValue();
            if (curPath != null && samePath(normalizeAbs(curPath), t)) {
                return current;
            }

            ObservableList<TreeItem<Path>> children = current.getChildren(); // triggers lazy load
            if (children == null || children.isEmpty()) {
                return null;
            }

            TreeItem<Path> next = null;
            for (TreeItem<Path> ch : children) {
                if (ch == null) {
                    continue;
                }
                Path chPath = ch.getValue();
                if (chPath == null) {
                    continue;
                }

                Path nCh = normalizeAbs(chPath);
                if (pathStartsWith(t, nCh)) {
                    next = ch;
                    break;
                }
            }

            if (next == null) {
                return null;
            }
            current = next;
        }

        return null;
    }

    private static Path normalizeAbs(Path p) {
        try {
            return p.toAbsolutePath().normalize();
        } catch (Exception ex) {
            return p.normalize();
        }
    }

    private static boolean samePath(Path a, Path b) {
        if (a == null || b == null) {
            return false;
        }
        String sa = a.toString();
        String sb = b.toString();
        if (IS_WINDOWS) {
            return sa.equalsIgnoreCase(sb);
        }
        return sa.equals(sb);
    }

    private static boolean pathStartsWith(Path full, Path prefix) {
        if (full == null || prefix == null) {
            return false;
        }

        // Prefer Path.startsWith first.
        try {
            if (full.startsWith(prefix)) {
                return true;
            }
        } catch (Exception ex) {
            // fall through
        }

        // String fallback (case-insensitive on Windows).
        String sFull = normalizeAbs(full).toString();
        String sPrefix = normalizeAbs(prefix).toString();

        if (IS_WINDOWS) {
            sFull = sFull.toLowerCase(Locale.ROOT);
            sPrefix = sPrefix.toLowerCase(Locale.ROOT);
        }

        if (!sPrefix.endsWith("\\") && !sPrefix.endsWith("/")) {
            sPrefix = sPrefix + (IS_WINDOWS ? "\\" : "/");
        }
        return sFull.equals(sPrefix.substring(0, sPrefix.length() - 1)) || sFull.startsWith(sPrefix);
    }

    private static String safeString(Path p) {
        return p == null ? "" : p.toString();
    }

    private static String describeRoot(Path root) {
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

    private static final class LazyDirTreeItem extends TreeItem<Path> {
        private final boolean isRootChild;
        private boolean childrenLoaded;

        private LazyDirTreeItem(Path value, boolean isRootChild) {
            super(value);
            this.isRootChild = isRootChild;
            this.childrenLoaded = false;
            setExpanded(false);
        }

        @Override
        public boolean isLeaf() {
            Path v = getValue();
            if (v == null) {
                return false;
            }
            try {
                return !Files.isDirectory(v, LinkOption.NOFOLLOW_LINKS);
            } catch (Exception ex) {
                return true;
            }
        }

        @Override
        public ObservableList<TreeItem<Path>> getChildren() {
            if (!childrenLoaded) {
                childrenLoaded = true;
                super.getChildren().setAll(loadChildren());
            }
            return super.getChildren();
        }

        private List<TreeItem<Path>> loadChildren() {
            Path dir = getValue();
            if (dir == null) {
                return List.of();
            }
            if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(dir)) {
                return List.of();
            }

            List<Path> dirs = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path p : stream) {
                    if (p == null) {
                        continue;
                    }
                    if (Files.isSymbolicLink(p)) {
                        continue; // avoid symlink/junction loops and noise
                    }
                    if (!Files.isReadable(p)) {
                        continue;
                    }
                    if (isHiddenSafe(p)) {
                        continue;
                    }
                    if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    if (denyRootNoise(dir, p)) {
                        continue;
                    }

                    dirs.add(p);
                }
            } catch (Exception ex) {
                return List.of();
            }

            dirs.sort(Comparator.comparing(LazyDirTreeItem::dirSortKey));

            List<TreeItem<Path>> out = new ArrayList<>(dirs.size());
            for (Path p : dirs) {
                out.add(new LazyDirTreeItem(p, false));
            }
            return out;
        }

        private boolean denyRootNoise(Path parentDir, Path child) {
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
            try {
                return Files.isHidden(p);
            } catch (Exception ex) {
                return false;
            }
        }

        private static String dirSortKey(Path p) {
            Path n = p.getFileName();
            String s = (n == null) ? p.toString() : n.toString();
            return s.toLowerCase(Locale.ROOT);
        }
    }
}
