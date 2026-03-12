package com.fileexplorer.ui.dialog;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.*;

/**
 * Explorer-like "Choose Details" dialog.
 *
 * Implemented in code (no FXML) to avoid skin/loader fragility.
 */
public final class ChooseDetailsDialog {

    private ChooseDetailsDialog() {}

    public record DetailSpec(String key, String label, boolean visible, boolean locked) {}

    public record Result(List<String> orderedKeys, Set<String> visibleKeys, Map<String, Double> widthByKey) {}

    public static Optional<Result> show(Window owner,
                                        List<DetailSpec> initial,
                                        List<DetailSpec> defaultSpecs,
                                        Map<String, Double> currentWidths) {

        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(defaultSpecs, "defaultSpecs");
        Objects.requireNonNull(currentWidths, "currentWidths");

        ObservableList<ModelRow> rows = FXCollections.observableArrayList();
        for (DetailSpec s : initial) {
            int widthPx = clampWidth((int) Math.round(currentWidths.getOrDefault(s.key(), (double) defaultWidthForLabel(s.label()))));
            rows.add(new ModelRow(s.key(), s.label(), s.visible(), s.locked(), widthPx));
        }

        ListView<ModelRow> list = new ListView<>(rows);
        list.getStyleClass().add("choose-details-list");
        list.setCellFactory(v -> new DetailCell());
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        list.setPrefWidth(246);
        list.setMinWidth(246);
        list.setMaxWidth(246);
        list.setPrefHeight(262);

        Button moveUp = dialogButton("Move Up");
        Button moveDown = dialogButton("Move Down");
        Button show = dialogButton("Show");
        Button hide = dialogButton("Hide");

        TextField widthField = new TextField();
        widthField.getStyleClass().add("choose-details-width-field");
        widthField.setAlignment(Pos.CENTER_RIGHT);
        widthField.setPrefColumnCount(5);
        widthField.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d{0,4}") ? change : null));

        Runnable syncSelectionState = () -> {
            int idx = list.getSelectionModel().getSelectedIndex();
            ModelRow row = list.getSelectionModel().getSelectedItem();
            boolean hasSelection = row != null;
            moveUp.setDisable(!hasSelection || idx <= 0);
            moveDown.setDisable(!hasSelection || idx < 0 || idx >= rows.size() - 1);
            show.setDisable(!hasSelection || row.locked || row.visibleProperty().get());
            hide.setDisable(!hasSelection || row.locked || !row.visibleProperty().get());
            widthField.setDisable(!hasSelection);
            widthField.setText(hasSelection ? Integer.toString(row.widthProperty().get()) : "");
        };

        list.getSelectionModel().selectedIndexProperty().addListener((obs, ov, nv) -> syncSelectionState.run());
        list.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> syncSelectionState.run());

        moveUp.setOnAction(ae -> {
            int idx = list.getSelectionModel().getSelectedIndex();
            if (idx > 0) {
                Collections.swap(rows, idx, idx - 1);
                list.getSelectionModel().select(idx - 1);
                list.scrollTo(Math.max(0, idx - 2));
            }
        });
        moveDown.setOnAction(ae -> {
            int idx = list.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < rows.size() - 1) {
                Collections.swap(rows, idx, idx + 1);
                list.getSelectionModel().select(idx + 1);
                list.scrollTo(Math.max(0, idx - 1));
            }
        });
        show.setOnAction(ae -> {
            ModelRow row = list.getSelectionModel().getSelectedItem();
            if (row != null && !row.locked) {
                row.visibleProperty().set(true);
                list.refresh();
                syncSelectionState.run();
            }
        });
        hide.setOnAction(ae -> {
            ModelRow row = list.getSelectionModel().getSelectedItem();
            if (row != null && !row.locked) {
                row.visibleProperty().set(false);
                list.refresh();
                syncSelectionState.run();
            }
        });

        Runnable commitWidth = () -> {
            ModelRow row = list.getSelectionModel().getSelectedItem();
            if (row == null) {
                widthField.setText("");
                return;
            }
            String raw = widthField.getText();
            int width = (raw == null || raw.isBlank()) ? row.widthProperty().get() : clampWidth(parseWidth(raw, row.widthProperty().get()));
            row.widthProperty().set(width);
            widthField.setText(Integer.toString(width));
        };
        widthField.setOnAction(ae -> commitWidth.run());
        widthField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV) {
                commitWidth.run();
            }
        });

        VBox commandButtons = new VBox(8.0, moveUp, moveDown, spacer(8.0), show, hide);
        commandButtons.getStyleClass().add("choose-details-command-buttons");
        commandButtons.setAlignment(Pos.TOP_CENTER);
        commandButtons.setPadding(new Insets(1, 0, 0, 12));
        commandButtons.setPrefWidth(96);

        Label lead = new Label("Select the details you want to display for the items in this folder.");
        lead.getStyleClass().add("choose-details-lead");
        lead.setWrapText(true);

        Label detailsLabel = new Label("Details:");
        detailsLabel.getStyleClass().add("choose-details-section-label");

        HBox listArea = new HBox(list, commandButtons);
        listArea.setAlignment(Pos.TOP_LEFT);

        Label widthLabel = new Label("Width of selected column (in pixels):");
        widthLabel.getStyleClass().add("choose-details-width-label");
        HBox widthRow = new HBox(12.0, widthLabel, spacer(), widthField);
        widthRow.getStyleClass().add("choose-details-width-row");
        widthRow.setAlignment(Pos.CENTER_LEFT);

        Separator separator = new Separator();
        separator.getStyleClass().add("choose-details-separator");

        Button ok = dialogButton("OK");
        Button cancel = dialogButton("Cancel");
        ok.setDefaultButton(true);
        cancel.setCancelButton(true);

        HBox buttonBar = new HBox(10.0, spacer(), ok, cancel);
        buttonBar.getStyleClass().add("choose-details-button-bar");
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12.0, lead, detailsLabel, listArea, widthRow, separator, buttonBar);
        root.getStyleClass().addAll("explorer-root", "choose-details-dialog-root");
        root.setPadding(new Insets(14, 12, 12, 12));
        root.setPrefWidth(338);

        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Choose Details");
        stage.setResizable(false);
        Scene dialogScene = new Scene(root);

        try {
            boolean dark = true;
            if (owner != null && owner.getScene() != null && owner.getScene().getRoot() != null) {
                var ownerClasses = owner.getScene().getRoot().getStyleClass();
                if (ownerClasses.contains("theme-light")) {
                    dark = false;
                }
                if (ownerClasses.contains("theme-dark")) {
                    dark = true;
                }
            }

            var sc = root.getStyleClass();
            sc.remove("theme-dark");
            sc.remove("theme-light");
            sc.add(dark ? "theme-dark" : "theme-light");

            if (owner != null && owner.getScene() != null) {
                for (String s : owner.getScene().getStylesheets()) {
                    if (s != null && !s.isBlank() && !dialogScene.getStylesheets().contains(s)) {
                        dialogScene.getStylesheets().add(s);
                    }
                }
            }

            addStylesheet(dialogScene, "/com/fileexplorer/ui/css/explorer-base.css");
            addStylesheet(dialogScene, "/com/fileexplorer/ui/css/explorer-win11.css");
            addStylesheet(dialogScene, "/com/fileexplorer/ui/css/explorer-fluent.css");
            addStylesheet(dialogScene, dark ? "/com/fileexplorer/ui/css/explorer-dark-win.css" : "/com/fileexplorer/ui/css/explorer-light-win.css");
            addStylesheet(dialogScene, "/com/fileexplorer/ui/css/explorer-win.css");
            addStylesheet(dialogScene, "/com/fileexplorer/ui/css/ui_fixes.css");
            addStylesheet(dialogScene, "/com/fileexplorer/ui/css/fluent-explorer.css");
            addStylesheet(dialogScene, "/com/fileexplorer/ui/css/explorer-override-everything.css");
            addStylesheet(dialogScene, "/com/fileexplorer/ui/css/choose-details-dialog.css");
        } catch (Throwable ignored) {
        }

        stage.setScene(dialogScene);

        final ResultHolder holder = new ResultHolder();
        ok.setOnAction(ae -> {
            commitWidth.run();
            holder.result = buildResult(rows);
            stage.close();
        });
        cancel.setOnAction(ae -> {
            holder.result = null;
            stage.close();
        });

        if (!rows.isEmpty()) {
            list.getSelectionModel().select(0);
        }
        syncSelectionState.run();
        stage.showAndWait();
        return Optional.ofNullable(holder.result);
    }

    private static Result buildResult(List<ModelRow> rows) {
        List<String> ordered = new ArrayList<>(rows.size());
        Set<String> visible = new LinkedHashSet<>();
        Map<String, Double> widths = new LinkedHashMap<>();
        for (ModelRow r : rows) {
            ordered.add(r.key);
            if (r.visibleProperty().get() || r.locked) {
                visible.add(r.key);
            }
            widths.put(r.key, (double) r.widthProperty().get());
        }
        return new Result(ordered, visible, widths);
    }

    private static final class ResultHolder {
        Result result;
    }

    private static final class ModelRow {
        final String key;
        final String label;
        final boolean locked;
        private final BooleanProperty visible = new SimpleBooleanProperty();
        private final IntegerProperty width = new SimpleIntegerProperty();

        ModelRow(String key, String label, boolean visible, boolean locked, int widthPixels) {
            this.key = key;
            this.label = label;
            this.locked = locked;
            this.visible.set(visible || locked);
            this.width.set(clampWidth(widthPixels));
        }

        BooleanProperty visibleProperty() {
            return visible;
        }

        IntegerProperty widthProperty() {
            return width;
        }
    }

    private static final class DetailCell extends ListCell<ModelRow> {
        private final CheckBox checkBox = new CheckBox();
        private final Label label = new Label();
        private final Region spacer = spacer();
        private final HBox box = new HBox(8.0, checkBox, label, spacer);

        DetailCell() {
            getStyleClass().add("choose-details-list-cell");
            box.getStyleClass().add("choose-details-row-box");
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(0, 8, 0, 8));
            label.getStyleClass().add("choose-details-row-label");
            HBox.setHgrow(spacer, Priority.ALWAYS);
            checkBox.setFocusTraversable(false);
            checkBox.setAllowIndeterminate(false);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(ModelRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            label.setText(item.label);
            checkBox.setSelected(item.visibleProperty().get());
            checkBox.setDisable(item.locked);
            checkBox.setOnAction(ae -> {
                if (item.locked) {
                    checkBox.setSelected(true);
                    return;
                }
                item.visibleProperty().set(checkBox.isSelected());
                ListView<ModelRow> lv = getListView();
                if (lv != null) {
                    lv.refresh();
                }
            });
            setGraphic(box);
        }
    }

    private static Button dialogButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("choose-details-command-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefWidth(92);
        return button;
    }

    private static Region spacer() {
        return spacer(0.0);
    }

    private static Region spacer(double minHeight) {
        Region region = new Region();
        if (minHeight > 0.0) {
            region.setMinHeight(minHeight);
            region.setPrefHeight(minHeight);
        }
        HBox.setHgrow(region, Priority.ALWAYS);
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    private static int parseWidth(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clampWidth(int width) {
        return Math.max(24, Math.min(2000, width));
    }

    private static int defaultWidthForLabel(String label) {
        if (label == null || label.isBlank()) {
            return 96;
        }
        return Math.max(96, Math.min(320, (int) Math.round(label.length() * 8.5 + 32.0)));
    }

    private static void addStylesheet(Scene scene, String resourcePath) {
        if (scene == null || resourcePath == null || resourcePath.isBlank()) return;
        var url = ChooseDetailsDialog.class.getResource(resourcePath);
        if (url == null) return;
        String resolved = url.toExternalForm();
        if (!scene.getStylesheets().contains(resolved)) {
            scene.getStylesheets().add(resolved);
        }
    }
}
