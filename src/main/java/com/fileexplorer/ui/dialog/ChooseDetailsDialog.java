package com.fileexplorer.ui.dialog;

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

    /**
     * A column in the details model.
     *
     * @param key stable storage key (e.g. "name")
     * @param label display label (e.g. "Name")
     * @param visible initial visible state
     * @param locked if true, cannot be unchecked (Name)
     */
    public record DetailSpec(String key, String label, boolean visible, boolean locked) {}

    /**
     * Dialog result.
     *
     * @param orderedKeys ordered list of keys (left-to-right)
     * @param visibleKeys visible key set
     */
    public record Result(List<String> orderedKeys, Set<String> visibleKeys) {}

    /**
     * Show the dialog.
     *
     * @param owner owner window
     * @param initial ordered/visible specs
     * @param defaultSpecs defaults to apply when Reset is pressed
     */
    public static Optional<Result> show(Window owner,
                                        List<DetailSpec> initial,
                                        List<DetailSpec> defaultSpecs) {

        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(defaultSpecs, "defaultSpecs");

        ObservableList<ModelRow> rows = FXCollections.observableArrayList();
        for (DetailSpec s : initial) rows.add(new ModelRow(s.key(), s.label(), s.visible(), s.locked()));

        ListView<ModelRow> list = new ListView<>(rows);
        list.setCellFactory(v -> new DetailCell());
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        list.setPrefWidth(360);
        list.setPrefHeight(420);

        Button up = new Button("Move up");
        Button down = new Button("Move down");
        up.setMaxWidth(Double.MAX_VALUE);
        down.setMaxWidth(Double.MAX_VALUE);
        up.setDisable(true);
        down.setDisable(true);

        list.getSelectionModel().selectedIndexProperty().addListener((obs, ov, nv) -> {
            int idx = (nv == null) ? -1 : nv.intValue();
            up.setDisable(idx <= 0);
            down.setDisable(idx < 0 || idx >= rows.size() - 1);
        });

        up.setOnAction(ae -> {
            int idx = list.getSelectionModel().getSelectedIndex();
            if (idx > 0) {
                Collections.swap(rows, idx, idx - 1);
                list.getSelectionModel().select(idx - 1);
            }
        });
        down.setOnAction(ae -> {
            int idx = list.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < rows.size() - 1) {
                Collections.swap(rows, idx, idx + 1);
                list.getSelectionModel().select(idx + 1);
            }
        });

        VBox moveBox = new VBox(8, up, down);
        moveBox.setPadding(new Insets(0, 0, 0, 12));
        moveBox.setAlignment(Pos.TOP_CENTER);

        HBox center = new HBox(list, moveBox);
        center.setPadding(new Insets(12));

        Button reset = new Button("Reset");
        Button ok = new Button("OK");
        Button cancel = new Button("Cancel");
        ok.setDefaultButton(true);
        cancel.setCancelButton(true);

        reset.setOnAction(ae -> {
            rows.setAll(toRows(defaultSpecs));
            if (!rows.isEmpty()) list.getSelectionModel().select(0);
        });

        HBox buttons = new HBox(10, reset, new Region(), ok, cancel);
        HBox.setHgrow(buttons.getChildren().get(1), Priority.ALWAYS);
        buttons.setPadding(new Insets(0, 12, 12, 12));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane();
        Label title = new Label("Choose Details");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 600;");
        BorderPane.setMargin(title, new Insets(12, 12, 0, 12));
        root.setTop(title);
        root.setCenter(center);
        root.setBottom(buttons);

        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Choose Details");
        stage.setResizable(false);
        stage.setScene(new Scene(root));

        final ResultHolder holder = new ResultHolder();

        ok.setOnAction(ae -> {
            holder.result = buildResult(rows);
            stage.close();
        });
        cancel.setOnAction(ae -> {
            holder.result = null;
            stage.close();
        });

        if (!rows.isEmpty()) list.getSelectionModel().select(0);
        stage.showAndWait();

        return Optional.ofNullable(holder.result);
    }

    private static Result buildResult(List<ModelRow> rows) {
        List<String> ordered = new ArrayList<>(rows.size());
        Set<String> visible = new HashSet<>();
        for (ModelRow r : rows) {
            ordered.add(r.key);
            if (r.visible || r.locked) visible.add(r.key);
        }
        return new Result(ordered, visible);
    }

    private static List<ModelRow> toRows(List<DetailSpec> specs) {
        List<ModelRow> out = new ArrayList<>(specs.size());
        for (DetailSpec s : specs) out.add(new ModelRow(s.key(), s.label(), s.visible(), s.locked()));
        return out;
    }

    private static final class ResultHolder {
        Result result;
    }

    private static final class ModelRow {
        final String key;
        final String label;
        boolean visible;
        final boolean locked;

        ModelRow(String key, String label, boolean visible, boolean locked) {
            this.key = key;
            this.label = label;
            this.visible = visible;
            this.locked = locked;
        }
    }

    private static final class DetailCell extends ListCell<ModelRow> {
        private final CheckBox cb = new CheckBox();
        private final Label lbl = new Label();
        private final HBox box = new HBox(10, cb, lbl);

        DetailCell() {
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(2, 6, 2, 6));
            cb.setFocusTraversable(false);
        }

        @Override
        protected void updateItem(ModelRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            lbl.setText(item.label);
            cb.setSelected(item.visible || item.locked);
            cb.setDisable(item.locked);
            cb.setOnAction(ae -> {
                if (item.locked) {
                    cb.setSelected(true);
                    return;
                }
                item.visible = cb.isSelected();
            });
            setGraphic(box);
        }
    }
}
