package com.fileexplorer.ui.table;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.model.FileItem;
import com.fileexplorer.service.icon.AsyncThumbnailService;
import com.fileexplorer.util.ImageSupport;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Phase 4A.2: Visibility-driven thumbnailing.
 *
 * <p>Problem: decoding thumbnails during fast scroll causes wasted work and UI jank.
 *
 * <p>Solution: when a cell binds to an image candidate, we register it, but only
 * start the decode after a short scroll-idle debounce. Pending work for cells that
 * scroll out of view is cancelled best-effort.</p>
 *
 * <p>HOTFIX189 extends the visible pump so thumbnail requests are issued in actual viewport order
 * (top-to-bottom, then left-to-right) instead of the arbitrary iteration order of the internal
 * registration map. This keeps visible PDF thumbnail work biased toward the first realized rows and
 * suppresses late commits into cells that have already slipped out of view.</p>
 */
public final class VisibleThumbnailManager {

    private final TableView<FileItem> table;
    private final ExplorerContext ctx;

    private final PauseTransition idleDebounce;

    private final Map<TableCell<FileItem, ?>, Registration> regs = new WeakHashMap<>();

    private static final class Registration {
        Path path;
        int size;
        String identity;
        long bindingStamp;
        java.util.function.Consumer<javafx.scene.image.Image> apply;
        CompletableFuture<javafx.scene.image.Image> future;
    }

    private record VisibleCellRegistration(TableCell<FileItem, ?> cell,
                                           Registration registration,
                                           double minY,
                                           double minX,
                                           int rowIndex) {
    }

    public VisibleThumbnailManager(TableView<FileItem> table, ExplorerContext ctx) {
        this.table = Objects.requireNonNull(table, "table");
        this.ctx = Objects.requireNonNull(ctx, "ctx");

        long ms = longProp("fileexplorer.thumb.scrollIdleMs", 140L);
        this.idleDebounce = new PauseTransition(Duration.millis(Math.max(30, ms)));
        this.idleDebounce.setOnFinished(_ -> pumpVisible());

        // Any scroll event resets debounce.
        table.addEventFilter(ScrollEvent.ANY, e -> {
            cancelNonVisible();
            idleDebounce.stop();
            idleDebounce.playFromStart();
        });

        // Also react to keyboard navigation (selection changes) to hydrate visible thumbs.
        try {
            table.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<? super Integer>) _ -> {
                idleDebounce.stop();
                idleDebounce.playFromStart();
            });
        } catch (Exception ignored) {
        }

        // First pump after skin is ready.
        Platform.runLater(() -> {
            idleDebounce.stop();
            idleDebounce.playFromStart();
        });
    }

    /** Register an image candidate cell. Does not necessarily start decoding immediately. */
    public void register(TableCell<FileItem, ?> cell, Path path, int sizePx, String identity,
                         java.util.function.Consumer<javafx.scene.image.Image> apply) {
        if (cell == null || path == null || apply == null) return;
        if (!ImageSupport.isThumbCandidate(path)) {
            unregister(cell);
            return;
        }

        Registration r = regs.computeIfAbsent(cell, _ -> new Registration());
        // Cancel old work if binding changed.
        if (r.future != null && (!Objects.equals(r.path, path) || r.size != sizePx)) {
            r.future.cancel(false);
            r.future = null;
        }
        r.path = path;
        r.size = sizePx;
        r.identity = identity;
        r.bindingStamp++;
        r.apply = apply;

        // Debounce: user may still be scrolling.
        idleDebounce.stop();
        idleDebounce.playFromStart();
    }


    public void unregister(TableCell<FileItem, ?> cell) {
        if (cell == null) {
            return;
        }
        Registration r = regs.remove(cell);
        if (r != null && r.future != null) {
            AsyncThumbnailService.getInstance().noteViewportCancellation();
            r.future.cancel(false);
            r.future = null;
        }
    }

    /** Best-effort cancel all pending thumbnail work (e.g., when directory changes). */
    public void cancelAll() {
        regs.values().forEach(r -> {
            if (r.future != null) {
                AsyncThumbnailService.getInstance().noteViewportCancellation();
                r.future.cancel(false);
                r.future = null;
            }
        });
    }

    private void pumpVisible() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::pumpVisible);
            return;
        }

        // If thumbnails are still gated off, do nothing.
        if (!AsyncThumbnailService.getInstance().isEnabled()) {
            return;
        }

        List<VisibleCellRegistration> visible = collectVisibleRegistrationsInViewportOrder();

        // Start requests for visible registered cells in top-to-bottom/left-to-right viewport order.
        for (VisibleCellRegistration entry : visible) {
            TableCell<FileItem, ?> cell = entry.cell();
            Registration r = entry.registration();
            if (cell == null || r == null || r.path == null || r.apply == null) {
                continue;
            }

            // Already requested.
            if (r.future != null) {
                continue;
            }

            final Path p = r.path;
            final int size = r.size;
            final String id = r.identity;
            final long stamp = r.bindingStamp;

            r.future = AsyncThumbnailService.getInstance().request(p, size, AsyncThumbnailService.RequestPriority.VISIBLE);
            final CompletableFuture<javafx.scene.image.Image> fut = r.future;
            fut.whenComplete((img, ex) -> Platform.runLater(() -> {
                Registration cur = regs.get(cell);
                if (cur == null) {
                    return;
                }
                if (cur.future != fut) {
                    return;
                }
                if (cur.bindingStamp != stamp) {
                    return;
                }
                if (fut.isCancelled() || ex != null || img == null || !isCellVisible(cell)) {
                    cur.future = null;
                }
            }));
            fut.thenAccept(img -> Platform.runLater(() -> {
                if (fut.isCancelled()) {
                    return;
                }
                // Ensure still bound to same file and still actually visible before committing.
                Registration cur = regs.get(cell);
                if (cur == null) {
                    return;
                }
                if (cur.bindingStamp != stamp) {
                    return;
                }
                if (!Objects.equals(cur.path, p)) {
                    return;
                }
                if (!Objects.equals(cur.identity, id)) {
                    return;
                }
                if (!Objects.equals(currentPathForCell(cell), p)) {
                    return;
                }
                if (!isCellVisible(cell)) {
                    cur.future = null;
                    return;
                }
                if (img == null) {
                    return;
                }
                cur.apply.accept(img);
            }));
        }
    }

    private List<VisibleCellRegistration> collectVisibleRegistrationsInViewportOrder() {
        List<VisibleCellRegistration> visible = new ArrayList<>(regs.size());
        for (Map.Entry<TableCell<FileItem, ?>, Registration> entry : regs.entrySet()) {
            TableCell<FileItem, ?> cell = entry.getKey();
            Registration registration = entry.getValue();
            if (cell == null || registration == null || registration.path == null || registration.apply == null) {
                continue;
            }
            if (!isCellVisible(cell)) {
                if (registration.future != null) {
                    AsyncThumbnailService.getInstance().noteViewportCancellation();
                    registration.future.cancel(false);
                    registration.future = null;
                }
                continue;
            }
            Bounds bounds = safeSceneBounds(cell);
            double minY = bounds == null ? Double.MAX_VALUE : bounds.getMinY();
            double minX = bounds == null ? Double.MAX_VALUE : bounds.getMinX();
            TableRow<FileItem> row = cell.getTableRow();
            int rowIndex = row == null ? Integer.MAX_VALUE : row.getIndex();
            visible.add(new VisibleCellRegistration(cell, registration, minY, minX, rowIndex));
        }
        visible.sort(Comparator
                .comparingDouble(VisibleCellRegistration::minY)
                .thenComparingInt(VisibleCellRegistration::rowIndex)
                .thenComparingDouble(VisibleCellRegistration::minX));
        return visible;
    }

    private void cancelNonVisible() {
        for (Map.Entry<TableCell<FileItem, ?>, Registration> e : regs.entrySet()) {
            TableCell<FileItem, ?> cell = e.getKey();
            Registration r = e.getValue();
            if (r == null || r.future == null) continue;
            if (cell != null && isCellVisible(cell)) continue;
            AsyncThumbnailService.getInstance().noteViewportCancellation();
            r.future.cancel(false);
            r.future = null;
        }
    }

    private static Path currentPathForCell(TableCell<FileItem, ?> cell) {
        if (cell == null) return null;
        TableRow<FileItem> row = cell.getTableRow();
        FileItem item = row == null ? null : row.getItem();
        return item == null ? null : item.path();
    }

    private static boolean isCellVisible(TableCell<FileItem, ?> cell) {
        if (cell == null || !cell.isVisible()) return false;
        TableRow<FileItem> row = cell.getTableRow();
        if (row == null || !row.isVisible()) return false;
        if (row.getItem() == null) return false;
        // If layout bounds are zero, it isn't actually rendered.
        return row.getLayoutBounds().getHeight() > 0 && row.getLayoutBounds().getWidth() > 0;
    }

    private static Bounds safeSceneBounds(TableCell<FileItem, ?> cell) {
        try {
            if (cell == null || cell.getScene() == null) {
                return null;
            }
            return cell.localToScene(cell.getBoundsInLocal());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long longProp(String key, long def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
