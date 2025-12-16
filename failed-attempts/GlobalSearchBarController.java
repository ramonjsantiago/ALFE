// package com.explorer.ui;

// import javafx.fxml.FXML;
// import javafx.scene.control.TextField;
// import javafx.scene.control.Button;

// import java.util.function.Consumer;

// public class GlobalSearchBarController {

    // @FXML private TextField searchField;
    // @FXML private Button clearBtn;
    // @FXML private Button searchBtn;

    // private Consumer<String> onSearch;

    // public void setOnSearch(Consumer<String> c) {
        // this.onSearch = c;
    // }

    // @FXML
    // private void initialize() {
        // clearBtn.setVisible(false);

        // searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            // clearBtn.setVisible(!newVal.isEmpty());
        // });

        // searchField.setOnAction(e -> triggerSearch());
    // }

    // @FXML
    // private void onClearClick() {
        // searchField.clear();
        // if (onSearch != null) onSearch.accept("");
    // }

    // @FXML
    // private void onSearchClick() {
        // triggerSearch();
    // }

    // private void triggerSearch() {
        // if (onSearch != null) {
            // onSearch.accept(searchField.getText().trim());
        // }
    // }

    // public void setQuery(String q) {
        // searchField.setText(q);
    // }
// }

package com.fileexplorer.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;

import java.util.List;

public class GlobalSearchBarController {

    @FXML private ComboBox<String> searchComboBox;

    private RecentSearchesService recentSearchesService;
    private SearchService searchService;

    @FXML
    private void initialize() {
        searchComboBox.setEditable(true);
        searchComboBox.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                executeSearch();
            }
        });
    }

    public void setServices(RecentSearchesService recentService, SearchService searchService) {
        this.recentSearchesService = recentService;
        this.searchService = searchService;
        refreshRecent();
    }

    public void refreshRecent() {
        if (recentSearchesService != null) {
            List<String> recent = recentSearchesService.list();
            ObservableList<String> items = FXCollections.observableArrayList(recent);
            searchComboBox.setItems(items);
        }
    }

    private void executeSearch() {
        String query = searchComboBox.getEditor().getText();
        if (query == null || query.isBlank()) return;

        if (recentSearchesService != null) {
            recentSearchesService.add(query);
            refreshRecent();
        }

        if (searchService != null) {
            // Fire off search asynchronously, handle results elsewhere
            var task = searchService.search(null, query, null); // root folder TBD
            new Thread(task).start();
        }
    }
}