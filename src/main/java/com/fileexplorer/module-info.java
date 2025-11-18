module com.fileexplorer.desktop {
    requires java.base;
    requires java.desktop;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.swing;

    opens com.fileexplorer.ui to javafx.fxml;
    opens com.fileexplorer.thumb to javafx.fxml;

    exports com.fileexplorer.ui;
    exports com.fileexplorer.thumb;
}
