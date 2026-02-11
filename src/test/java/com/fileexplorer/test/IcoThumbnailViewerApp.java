package com.fileexplorer.test;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import net.sf.image4j.codec.ico.ICODecoder;
import net.sf.image4j.codec.ico.ICOImage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test-only JavaFX application:
 * - Recursively scans a selected directory for .ico files
 * - Uses image4j (ICODecoder.readExt) to extract each image variant and metadata
 * - Displays ONE row per ICO file, with a nested list of extracted variants in a table cell
 *
 * Location: src/test/java only (does not ship with production code).
 */
public final class IcoThumbnailViewerApp extends Application {

    private static final int THUMBNAIL_FIT = 40;
    private static final int VARIANT_TILE_WIDTH = 92;

    private final ObservableList<IcoFileRow> backing = FXCollections.observableArrayList();
    private final FilteredList<IcoFileRow> filtered = new FilteredList<>(backing, r -> true);

    private final TextField directoryField = new TextField();
    private final TextField filterField = new TextField();

    private final Label statusLabel = new Label("Select a directory to scan for .ico files.");
    private final ProgressIndicator progress = new ProgressIndicator();

    private volatile Task<List<IcoFileRow>> currentTask;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("ICO Thumbnail Viewer (Test Harness) - one row per file");

        // --- Top controls
        directoryField.setPromptText("Directory to scan…");
        directoryField.setEditable(false);

        Button browseBtn = new Button("Browse…");
        Button scanBtn = new Button("Scan");

        filterField.setPromptText("Filter (path, name, or size e.g. \"shell\" or \"32x32\")…");

        progress.setVisible(false);
        progress.setMaxSize(18, 18);

        HBox topRow = new HBox(8, new Label("Folder:"), directoryField, browseBtn, scanBtn, progress);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(directoryField, Priority.ALWAYS);

        HBox filterRow = new HBox(8, new Label("Filter:"), filterField);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filterField, Priority.ALWAYS);

        VBox top = new VBox(8, topRow, filterRow);
        top.setPadding(new Insets(10));

        // --- Table
        TableView<IcoFileRow> table = buildTable();
        table.setItems(filtered);

        BorderPane root = new BorderPane(table);
        root.setTop(top);

        // --- Bottom status
        BorderPane bottom = new BorderPane();
        bottom.setPadding(new Insets(8, 10, 10, 10));
        bottom.setLeft(statusLabel);
        root.setBottom(bottom);

        // --- Actions
        browseBtn.setOnAction(e -> chooseDirectory(stage));
        scanBtn.setOnAction(e -> scanSelectedDirectory());
        filterField.textProperty().addListener((obs, oldV, newV) -> applyFilter(newV));

        // Sensible default: project test resources if it exists; otherwise user home.
        Path defaultDir = guessDefaultScanDirectory();
        if (defaultDir != null) {
            directoryField.setText(defaultDir.toAbsolutePath().toString());
            scanSelectedDirectory();
        }

        Scene scene = new Scene(root, 1200, 740);
        stage.setScene(scene);
        stage.show();
    }

    private TableView<IcoFileRow> buildTable() {
        TableView<IcoFileRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<IcoFileRow, Image> colPreview = new TableColumn<>("Preview");
        colPreview.setMinWidth(90);
        colPreview.setMaxWidth(120);
        colPreview.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().preview()));
        colPreview.setCellFactory(col -> new TableCell<>() {
            private final ImageView view = new ImageView();

            {
                view.setFitWidth(THUMBNAIL_FIT);
                view.setFitHeight(THUMBNAIL_FIT);
                view.setPreserveRatio(true);
                view.setSmooth(false);
                view.setCache(true);
            }

            @Override
            protected void updateItem(Image item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    view.setImage(item);
                    setGraphic(view);
                }
            }
        });

        TableColumn<IcoFileRow, String> colPath = new TableColumn<>("ICO File (relative)");
        colPath.setMinWidth(380);
        colPath.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().relativePath()));

        TableColumn<IcoFileRow, String> colName = new TableColumn<>("File Name");
        colName.setMinWidth(220);
        colName.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().fileName()));

        TableColumn<IcoFileRow, Integer> colCount = new TableColumn<>("Variants");
        colCount.setMinWidth(90);
        colCount.setMaxWidth(110);
        colCount.setCellValueFactory(cd -> new ReadOnlyIntegerWrapper(cd.getValue().variants().size()).asObject());

        TableColumn<IcoFileRow, List<IcoVariant>> colVariants = new TableColumn<>("Extracted Variants (thumbnail + size)");
        colVariants.setMinWidth(420);
        colVariants.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().variants()));
        colVariants.setCellFactory(col -> new VariantsCell());

        table.getColumns().addAll(colPreview, colPath, colName, colCount, colVariants);
        return table;
    }

    private final class VariantsCell extends TableCell<IcoFileRow, List<IcoVariant>> {
        private final ScrollPane scroll = new ScrollPane();
        private final FlowPane flow = new FlowPane();

        private VariantsCell() {
            flow.setHgap(8);
            flow.setVgap(8);
            flow.setPadding(new Insets(6));
            flow.setPrefWrapLength(540);

            scroll.setContent(flow);
            scroll.setFitToHeight(true);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setPannable(true);

            // Keep table rows tidy: variants stay within a fixed vertical space.
            scroll.setPrefViewportHeight(72);
            scroll.setMinHeight(72);
            scroll.setMaxHeight(72);
        }

        @Override
        protected void updateItem(List<IcoVariant> item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || item.isEmpty()) {
                setGraphic(null);
                return;
            }

            flow.getChildren().clear();
            for (IcoVariant v : item) {
                flow.getChildren().add(buildVariantTile(v));
            }
            setGraphic(scroll);
        }

        private Region buildVariantTile(IcoVariant v) {
            ImageView iv = new ImageView(v.image());
            iv.setFitWidth(THUMBNAIL_FIT);
            iv.setFitHeight(THUMBNAIL_FIT);
            iv.setPreserveRatio(true);
            iv.setSmooth(false);

            Label sizeLabel = new Label(v.width() + "x" + v.height());
            sizeLabel.setStyle("-fx-font-size: 11px;");

            String pngFlag = v.pngCompressed() ? "PNG" : "BMP";
            Label metaLabel = new Label(v.colourDepth() + " bpp • " + pngFlag);
            metaLabel.setStyle("-fx-font-size: 10px;");

            VBox box = new VBox(2, iv, sizeLabel, metaLabel);
            box.setAlignment(Pos.CENTER);
            box.setMinWidth(VARIANT_TILE_WIDTH);
            box.setPrefWidth(VARIANT_TILE_WIDTH);
            box.setMaxWidth(VARIANT_TILE_WIDTH);
            box.setPadding(new Insets(4));

            box.setStyle("""
                    -fx-border-color: -fx-box-border;
                    -fx-border-radius: 6;
                    -fx-background-radius: 6;
                    -fx-background-color: rgba(0,0,0,0.03);
                    """);

            String tooltipText = "Index: " + v.iconIndex() +
                    "\nSize: " + v.width() + "x" + v.height() +
                    "\nBPP: " + v.colourDepth() +
                    "\nPNG: " + (v.pngCompressed() ? "Yes" : "No");
            Tooltip.install(box, new Tooltip(tooltipText));

            return box;
        }

    }


    private void chooseDirectory(Stage owner) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select a directory to scan for .ico files");

        String existing = directoryField.getText();
        if (existing != null && !existing.isBlank()) {
            Path p = Paths.get(existing);
            if (Files.isDirectory(p)) {
                chooser.setInitialDirectory(p.toFile());
            }
        }

        File selected = chooser.showDialog(owner);
        if (selected != null) {
            directoryField.setText(selected.getAbsolutePath());
            scanSelectedDirectory();
        }
    }

    private void scanSelectedDirectory() {
        String dir = directoryField.getText();
        if (dir == null || dir.isBlank()) {
            statusLabel.setText("No directory selected.");
            return;
        }

        Path root = Paths.get(dir);
        if (!Files.isDirectory(root)) {
            statusLabel.setText("Not a directory: " + root);
            return;
        }

        cancelCurrentTask();

        Task<List<IcoFileRow>> task = new Task<>() {
            @Override
            protected List<IcoFileRow> call() throws Exception {
                return scanForIcoFiles(root);
            }
        };
        currentTask = task;

        progress.setVisible(true);
        statusLabel.setText("Scanning " + root + " …");

        task.setOnSucceeded(e -> {
            List<IcoFileRow> rows = task.getValue();
            backing.setAll(rows);
            applyFilter(filterField.getText());

            progress.setVisible(false);
            statusLabel.setText(summaryText(root, rows));
        });

        task.setOnFailed(e -> {
            progress.setVisible(false);
            Throwable ex = task.getException();
            statusLabel.setText("Scan failed: " + (ex == null ? "Unknown error" : ex.getClass().getSimpleName() + ": " + ex.getMessage()));
        });

        task.setOnCancelled(e -> {
            progress.setVisible(false);
            statusLabel.setText("Scan cancelled.");
        });

        Thread t = new Thread(task, "ico-scan-" + Instant.now().toEpochMilli());
        t.setDaemon(true);
        t.start();
    }

    private void cancelCurrentTask() {
        Task<?> t = currentTask;
        if (t != null) {
            t.cancel();
        }
        currentTask = null;
    }

    private List<IcoFileRow> scanForIcoFiles(Path root) throws IOException {
        Objects.requireNonNull(root, "root");

        List<Path> icoFiles;
        try (var walk = Files.walk(root)) {
            icoFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ico"))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        }

        List<IcoFileRow> out = new ArrayList<>();
        int i = 0;

        for (Path ico : icoFiles) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            i++;
            String msg = "Decoding " + i + " / " + icoFiles.size() + " : " + ico.getFileName();
            Platform.runLater(() -> statusLabel.setText(msg));

            try {
                IcoFileRow row = decodeIcoFile(root, ico);
                if (row != null && !row.variants().isEmpty()) {
                    out.add(row);
                }
            } catch (Exception ignored) {
                // continue scanning
            }
        }

        if (!icoFiles.isEmpty() && out.isEmpty()) {
            throw new IOException("No ICO files decoded. Found " + icoFiles.size() + " .ico file(s), but decoding failed.");
        }

        return out;
    }

    private IcoFileRow decodeIcoFile(Path root, Path icoFile) throws IOException {
        // readExt returns ICOImage objects (image + metadata like icon index, width/height, colour depth, pngCompressed)
        List<ICOImage> images = ICODecoder.readExt(icoFile.toFile());
        if (images == null || images.isEmpty()) {
            return null;
        }

        String rel = safeRelativize(root, icoFile);
        String fileName = (icoFile.getFileName() == null) ? icoFile.toString() : icoFile.getFileName().toString();

        List<IcoVariant> variants = new ArrayList<>(images.size());
        for (ICOImage icoImg : images) {
            if (icoImg == null) {
                continue;
            }
            BufferedImage bi = icoImg.getImage();
            Image fx = (bi == null) ? null : bufferedToFxImage(bi);
            if (fx == null) {
                continue;
            }

            variants.add(new IcoVariant(
                    icoImg.getIconIndex(),
                    icoImg.getWidth(),
                    icoImg.getHeight(),
                    icoImg.getColourDepth(),
                    icoImg.isPngCompressed(),
                    fx
            ));
        }

        // Sort variants (ascending size, then depth); helps the user visually scan
        variants.sort(Comparator
                .comparingInt(IcoVariant::width)
                .thenComparingInt(IcoVariant::height)
                .thenComparingInt(IcoVariant::colourDepth));

        Image preview = pickPreview(variants);

        return new IcoFileRow(icoFile, rel, fileName, variants, preview);
    }

    private static Image pickPreview(List<IcoVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        // Choose the largest variant as the preview.
        return variants.stream()
                .max(Comparator.comparingInt(v -> v.width() * v.height()))
                .map(IcoVariant::image)
                .orElse(variants.get(0).image());
    }

    private void applyFilter(String raw) {
        String q = (raw == null) ? "" : raw.trim().toLowerCase(Locale.ROOT);

        if (q.isBlank()) {
            filtered.setPredicate(r -> true);
            return;
        }

        filtered.setPredicate(r -> {
            if (containsIgnoreCase(r.relativePath(), q) || containsIgnoreCase(r.fileName(), q)) {
                return true;
            }
            for (IcoVariant v : r.variants()) {
                String size = (v.width() + "x" + v.height()).toLowerCase(Locale.ROOT);
                if (size.contains(q)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean containsIgnoreCase(String haystack, String needleLower) {
        if (needleLower == null || needleLower.isBlank()) {
            return true;
        }
        if (haystack == null) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private static String safeRelativize(Path root, Path file) {
        try {
            return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString();
        } catch (Exception ex) {
            return file.toString();
        }
    }

    private static String summaryText(Path root, List<IcoFileRow> rows) {
        int files = rows.size();
        int variants = rows.stream().mapToInt(r -> r.variants().size()).sum();
        return "Scanned: " + root + " | ICO files decoded: " + files + " | total variants: " + variants;
    }

    private static Path guessDefaultScanDirectory() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path testResources = cwd.resolve("src").resolve("test").resolve("resources");
        if (Files.isDirectory(testResources)) {
            return testResources;
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            Path p = Paths.get(home);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Convert java.awt BufferedImage (ARGB) to a JavaFX Image without requiring javafx-swing.
     */
    private static Image bufferedToFxImage(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }

        int[] argb = src.getRGB(0, 0, w, h, null, 0, w);

        WritableImage out = new WritableImage(w, h);
        out.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        return out;
    }

    /**
     * One row per ICO file.
     */
    public record IcoFileRow(
            Path icoPath,
            String relativePath,
            String fileName,
            List<IcoVariant> variants,
            Image preview
    ) { }

    /**
     * A single extracted icon variant within an ICO file.
     */
    public record IcoVariant(
            int iconIndex,
            int width,
            int height,
            int colourDepth,
            boolean pngCompressed,
            Image image
    ) { }
}
