module com.fileexplorer {
    requires java.desktop;
    requires java.prefs;

    requires javafx.base;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.swing;

    // Only keep these if you truly use WebView / Media APIs:
    requires javafx.web;
    requires javafx.media;

    // FXML reflection access (update package names to match your controllers)
    opens com.fileexplorer.ui to javafx.fxml;

    // Export your public API packages as needed
    exports com.fileexplorer;
    exports com.fileexplorer.ui;
    exports com.fileexplorer.ui.service;
}
