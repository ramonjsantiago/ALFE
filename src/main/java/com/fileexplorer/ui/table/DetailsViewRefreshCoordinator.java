package com.fileexplorer.ui.table;

import com.fileexplorer.model.FileItem;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.FocusModel;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TableView;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * HOTFIX203 / Phase 4P.9DE.
 *
 * <p>Coalesces bursty Details-table refresh requests into a stable commit barrier so row repaint,
 * selection restore, and focus restore only publish after the active item model and sort state have
 * settled for the current directory scope.</p>
 */
public final class DetailsViewRefreshCoordinator {

    public static final String ACTIVE_DIRECTORY_SCOPE_KEY = DetailsViewRefreshCoordinator.class.getName() + ".activeDirectoryScope";
    public static final String SELECTION_ANCHOR_PATH_KEY = DetailsViewRefreshCoordinator.class.getName() + ".selectionAnchorPath";
    public static final String SELECTION_LEAD_PATH_KEY = DetailsViewRefreshCoordinator.class.getName() + ".selectionLeadPath";

    private final TableView<FileItem> table;
    private final PauseTransition debounce;
    private final LinkedHashSet<Path> dirtyPaths = new LinkedHashSet<>();
    private final LinkedHashSet<String> dirtyReasons = new LinkedHashSet<>();

    private long mutationGeneration = 0L;
    private long committedGeneration = -1L;

    public DetailsViewRefreshCoordinator(TableView<FileItem> table) {
        this.table = Objects.requireNonNull(table, "table");
        long debounceMs = Long.getLong("fileexplorer.details.refreshCoalesceMs", 55L);
        this.debounce = new PauseTransition(Duration.millis(Math.max(20L, debounceMs)));
        this.debounce.setOnFinished(_ -> flushNow());
    }

    public static void publishTableState(TableView<FileItem> table,
                                         Path activeDirectoryScope,
                                         Path selectionAnchorPath,
                                         Path selectionLeadPath) {
        if (table == null) {
            return;
        }
        putOrRemove(table, ACTIVE_DIRECTORY_SCOPE_KEY, normalize(activeDirectoryScope));
        putOrRemove(table, SELECTION_ANCHOR_PATH_KEY, normalize(selectionAnchorPath));
        putOrRemove(table, SELECTION_LEAD_PATH_KEY, normalize(selectionLeadPath));
    }

    public void requestRefresh(Collection<Path> paths, String reason) {
        if (paths != null) {
            for (Path path : paths) {
                if (path != null) {
                    dirtyPaths.add(path);
                }
            }
        }
        if (reason != null && !reason.isBlank()) {
            dirtyReasons.add(reason);
        }
        mutationGeneration++;
        arm();
    }

    public void requestRefresh(Path path, String reason) {
        if (path != null) {
            dirtyPaths.add(path);
        }
        if (reason != null && !reason.isBlank()) {
            dirtyReasons.add(reason);
        }
        mutationGeneration++;
        arm();
    }

    public void requestVisibleRefresh(String reason) {
        if (reason != null && !reason.isBlank()) {
            dirtyReasons.add(reason);
        }
        mutationGeneration++;
        arm();
    }

    private void arm() {
        if (Platform.isFxApplicationThread()) {
            debounce.stop();
            debounce.playFromStart();
            return;
        }
        Platform.runLater(() -> {
            debounce.stop();
            debounce.playFromStart();
        });
    }

    private void flushNow() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::flushNow);
            return;
        }

        if (table.getScene() == null || !table.isVisible()) {
            return;
        }

        final long generationAtFlush = mutationGeneration;
        final SelectionSnapshot snapshot = captureSelectionSnapshot();

        try {
            table.refresh();
        } catch (Exception ignored) {
        }

        Platform.runLater(() -> commitIfStable(generationAtFlush, snapshot, true));
    }

    private void commitIfStable(long generationAtFlush, SelectionSnapshot snapshot, boolean allowExtraPulse) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> commitIfStable(generationAtFlush, snapshot, allowExtraPulse));
            return;
        }
        if (table.getScene() == null || table.getItems() == null) {
            return;
        }
        if (mutationGeneration != generationAtFlush) {
            arm();
            return;
        }
        if (!sameDirectoryScope(snapshot.directoryScope(), activeDirectoryScope())) {
            dirtyPaths.clear();
            dirtyReasons.clear();
            return;
        }

        restoreSelectionAndFocus(snapshot);
        committedGeneration = generationAtFlush;
        dirtyPaths.clear();
        dirtyReasons.clear();

        if (allowExtraPulse) {
            Platform.runLater(() -> {
                if (mutationGeneration != generationAtFlush || committedGeneration != generationAtFlush) {
                    arm();
                    return;
                }
                try {
                    table.refresh();
                } catch (Exception ignored) {
                }
                commitIfStable(generationAtFlush, snapshot, false);
            });
        }
    }

    private SelectionSnapshot captureSelectionSnapshot() {
        MultipleSelectionModel<FileItem> selectionModel = table.getSelectionModel();
        FocusModel<FileItem> focusModel = table.getFocusModel();
        Set<Path> selectedPaths = snapshotSelectedPaths(selectionModel);
        Path focusedPath = focusedPath(focusModel);
        Path anchorPath = normalize(pathProperty(SELECTION_ANCHOR_PATH_KEY));
        Path leadPath = normalize(pathProperty(SELECTION_LEAD_PATH_KEY));
        Path directoryScope = activeDirectoryScope();
        if (!selectedPaths.contains(anchorPath)) {
            anchorPath = null;
        }
        if (!selectedPaths.contains(leadPath)) {
            leadPath = selectedPaths.contains(focusedPath) ? focusedPath : null;
        }
        return new SelectionSnapshot(selectedPaths, focusedPath, anchorPath, leadPath, directoryScope);
    }

    private Set<Path> snapshotSelectedPaths(MultipleSelectionModel<FileItem> selectionModel) {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        if (selectionModel == null) {
            return paths;
        }
        ObservableList<FileItem> selectedItems = selectionModel.getSelectedItems();
        if (selectedItems == null) {
            return paths;
        }
        for (FileItem item : selectedItems) {
            if (item != null && item.path() != null) {
                paths.add(normalize(item.path()));
            }
        }
        return paths;
    }

    private Path focusedPath(FocusModel<FileItem> focusModel) {
        if (focusModel == null) {
            return null;
        }
        FileItem focusedItem = focusModel.getFocusedItem();
        return focusedItem == null ? null : normalize(focusedItem.path());
    }

    private void restoreSelectionAndFocus(SelectionSnapshot snapshot) {
        if (table.getScene() == null || table.getItems() == null) {
            return;
        }
        if (!sameDirectoryScope(snapshot.directoryScope(), activeDirectoryScope())) {
            return;
        }

        MultipleSelectionModel<FileItem> selectionModel = table.getSelectionModel();
        FocusModel<FileItem> focusModel = table.getFocusModel();
        List<Integer> toSelect = indicesOfPaths(snapshot.selectedPaths());
        int leadIndex = preferredRestoreIndex(snapshot.leadPath(), snapshot.focusedPath(), snapshot.anchorPath(), toSelect);
        int focusIndex = preferredRestoreIndex(snapshot.focusedPath(), snapshot.leadPath(), snapshot.anchorPath(), toSelect);
        int anchorIndex = preferredRestoreIndex(snapshot.anchorPath(), snapshot.focusedPath(), snapshot.leadPath(), toSelect);

        if (selectionModel != null) {
            selectionModel.clearSelection();
            if (!toSelect.isEmpty()) {
                if (leadIndex >= 0) {
                    for (Integer index : toSelect) {
                        if (index != null && index != leadIndex) {
                            selectionModel.select(index);
                        }
                    }
                    selectionModel.select(leadIndex);
                } else {
                    int firstIndex = toSelect.get(0);
                    int[] rest = new int[Math.max(0, toSelect.size() - 1)];
                    for (int i = 1; i < toSelect.size(); i++) {
                        rest[i - 1] = toSelect.get(i);
                    }
                    selectionModel.selectIndices(firstIndex, rest);
                }
            }
        }

        if (focusModel != null) {
            if (focusIndex >= 0) {
                focusModel.focus(focusIndex);
            } else if (selectionModel != null && !toSelect.isEmpty()) {
                focusModel.focus(toSelect.get(0));
            }
        }

        Path restoredAnchorPath = anchorIndex >= 0 ? pathAt(anchorIndex) : focusIndex >= 0 ? pathAt(focusIndex) : leadIndex >= 0 ? pathAt(leadIndex) : null;
        Path restoredLeadPath = leadIndex >= 0 ? pathAt(leadIndex) : focusIndex >= 0 ? pathAt(focusIndex) : restoredAnchorPath;
        publishTableState(table, snapshot.directoryScope(), restoredAnchorPath, restoredLeadPath);
    }

    private List<Integer> indicesOfPaths(Set<Path> selectedPaths) {
        List<Integer> indices = new ArrayList<>();
        ObservableList<FileItem> items = table.getItems();
        if (items == null || selectedPaths == null || selectedPaths.isEmpty()) {
            return indices;
        }
        for (int i = 0; i < items.size(); i++) {
            FileItem item = items.get(i);
            if (item != null && item.path() != null && selectedPaths.contains(normalize(item.path()))) {
                indices.add(i);
            }
        }
        return indices;
    }

    private int preferredRestoreIndex(Path primary, Path secondary, Path tertiary, List<Integer> selectedIndices) {
        int index = indexOfPath(primary);
        if (index >= 0) {
            return index;
        }
        index = indexOfPath(secondary);
        if (index >= 0) {
            return index;
        }
        index = indexOfPath(tertiary);
        if (index >= 0) {
            return index;
        }
        return (selectedIndices == null || selectedIndices.isEmpty()) ? -1 : selectedIndices.get(0);
    }

    private int indexOfPath(Path path) {
        ObservableList<FileItem> items = table.getItems();
        Path normalizedPath = normalize(path);
        if (items == null || normalizedPath == null) {
            return -1;
        }
        for (int i = 0; i < items.size(); i++) {
            FileItem item = items.get(i);
            if (item != null && Objects.equals(normalize(item.path()), normalizedPath)) {
                return i;
            }
        }
        return -1;
    }

    private Path pathAt(int index) {
        ObservableList<FileItem> items = table.getItems();
        if (items == null || index < 0 || index >= items.size()) {
            return null;
        }
        FileItem item = items.get(index);
        return item == null ? null : normalize(item.path());
    }

    private Path pathProperty(String key) {
        Object value = table.getProperties().get(key);
        return value instanceof Path path ? path : null;
    }

    private Path activeDirectoryScope() {
        return normalize(pathProperty(ACTIVE_DIRECTORY_SCOPE_KEY));
    }

    private static boolean sameDirectoryScope(Path left, Path right) {
        return Objects.equals(normalize(left), normalize(right));
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.normalize();
    }

    private static void putOrRemove(TableView<FileItem> table, String key, Path value) {
        if (table == null || key == null) {
            return;
        }
        if (value == null) {
            table.getProperties().remove(key);
        } else {
            table.getProperties().put(key, value);
        }
    }

    private record SelectionSnapshot(Set<Path> selectedPaths,
                                     Path focusedPath,
                                     Path anchorPath,
                                     Path leadPath,
                                     Path directoryScope) {
    }
}
