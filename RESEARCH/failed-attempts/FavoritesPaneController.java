// package com.explorer.ui;

// import javafx.fxml.FXML;
// import javafx.scene.control.ListView;
// import javafx.scene.input.MouseEvent;
// import java.nio.file.Path;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.function.Consumer;

// public class FavoritesPaneController {

    // @FXML private ListView<String> favoritesList;

    // private List<Path> favoritePaths = new ArrayList<>();
    // private Consumer<Path> onSelect;

    // public void setOnSelect(Consumer<Path> c) {
        // this.onSelect = c;
    // }

    // public void addFavorite(Path path) {
        // if (!favoritePaths.contains(path)) {
            // favoritePaths.add(path);
            // favoritesList.getItems().add(path.getFileName().toString());
        // }
    // }

    // public void removeFavorite(Path path) {
        // int index = favoritePaths.indexOf(path);
        // if (index >= 0) {
            // favoritePaths.remove(index);
            // favoritesList.getItems().remove(index);
        // }
    // }

    // @FXML
    // private void initialize() {
        // favoritesList.setOnMouseClicked(this::onItemClick);
    // }

    // private void onItemClick(MouseEvent event) {
        // int idx = favoritesList.getSelectionModel().getSelectedIndex();
        // if (idx >= 0 && onSelect != null) {
            // onSelect.accept(favoritePaths.get(idx));
        // }
    // }
// }

package com.fileexplorer.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

import java.nio.file.Path;

public class FavoritesPaneController {

    @FXML private ListView<Path> favoritesListView;

    private final ObservableList<Path> favorites = FXCollections.observableArrayList();
    private FavoritesService favoritesService;

    @FXML
    private void initialize() {
        favoritesListView.setItems(favorites);
    }

    public void setFavoritesService(FavoritesService service) {
        this.favoritesService = service;
        refresh();
    }

    public void refresh() {
        if (favoritesService != null) {
            favorites.setAll(favoritesService.getFavorites());
        }
    }

    public ListView<Path> getListView() {
        return favoritesListView;
    }
}