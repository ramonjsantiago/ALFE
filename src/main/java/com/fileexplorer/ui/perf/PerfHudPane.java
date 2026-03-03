package com.fileexplorer.ui.perf;

import com.fileexplorer.service.filesystem.FileMetadataBudgetService;
import com.fileexplorer.service.icon.AsyncThumbnailService;
import com.fileexplorer.service.icon.IconCacheService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Phase 4C.1: simple always-on-top perf HUD.
 *
 * <p>Enabled via -Dfileexplorer.perfHud=true</p>
 */
public final class PerfHudPane extends VBox {

    private final Label line1 = new Label();
    private final Label line2 = new Label();
    private final Label line3 = new Label();

    private final Timeline timer;

    private final AsyncThumbnailService thumbs;
    private final IconCacheService icons;
    private final FileMetadataBudgetService metadata;

    public PerfHudPane(AsyncThumbnailService thumbs, IconCacheService icons, FileMetadataBudgetService metadata) {
        this.thumbs = thumbs;
        this.icons = icons;
        this.metadata = metadata;

        setSpacing(2);
        setPadding(new Insets(6));
        setAlignment(Pos.TOP_LEFT);

        // Flat translucent background.
        setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.55), new CornerRadii(8), Insets.EMPTY)));

        for (Label l : new Label[]{line1, line2, line3}) {
            l.setTextFill(Color.WHITE);
            l.setStyle("-fx-font-size: 11px; -fx-font-family: 'Consolas';");
        }

        HBox header = new HBox(new Label("PERF"));
        header.setAlignment(Pos.CENTER_LEFT);
        Label h = (Label) header.getChildren().get(0);
        h.setTextFill(Color.WHITE);
        h.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-font-family: 'Consolas';");

        getChildren().addAll(header, line1, line2, line3);

        timer = new Timeline(new KeyFrame(Duration.millis(750), e -> refresh()));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();

        refresh();
    }

    private void refresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refresh);
            return;
        }
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long used = rt.totalMemory() - rt.freeMemory();
        line1.setText("heap=" + fmtMb(used) + "MB/" + fmtMb(max) + "MB");
        line2.setText("thumbs: " + safe(thumbs != null ? thumbs.debugString() : "n/a"));
        String iconStr = icons != null ? icons.debugString() : "n/a";
        String metaStr = metadata != null ? metadata.debugString() : "n/a";
        line3.setText("icons: " + iconStr + " | meta: " + metaStr);
    }

    private static long fmtMb(long bytes) {
        if (bytes <= 0) return 0;
        return bytes / (1024L * 1024L);
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ');
    }

    public void stop() {
        try { timer.stop(); } catch (Throwable ignored) {}
    }
}
