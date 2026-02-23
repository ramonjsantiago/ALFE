package com.fileexplorer.util;

import java.util.Objects;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

/**
 * DialogTheme.
 * <p>
 * Applies the application's active stylesheets (including Win11 + dark mode) to JavaFX dialogs
 * (Alert/Dialog). JavaFX dialogs otherwise use the default Modena stylesheet, which looks
 * visually inconsistent with the application's Fluent/Win11 theme.
 */
public final class DialogTheme {

    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    private DialogTheme() {
        // util
    }

    /**
     * Apply the owner's scene stylesheets and dark pseudo-class to the given dialog.
     *
     * @param dialog Dialog to style
     * @param owner Owner window (may be null)
     */
    public static void apply(Dialog<?> dialog, Window owner) {
        Objects.requireNonNull(dialog, "dialog");
        if (owner == null) {
            try {
                for (Window w : Window.getWindows()) {
                    if (w != null && w.isShowing() && w.isFocused()) {
                        owner = w;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        DialogPane pane = dialog.getDialogPane();
        if (pane == null) {
            return;
        }

        // Copy stylesheets from owner window scene (best-effort).
        if (owner != null) {
            Scene ownerScene = owner.getScene();
            if (ownerScene != null) {
                for (String css : ownerScene.getStylesheets()) {
                    if (css != null && !pane.getStylesheets().contains(css)) {
                        pane.getStylesheets().add(css);
                    }
                }

                // Mirror dark mode pseudo class on the dialog root.
                boolean isDark = ownerScene.getRoot() != null && ownerScene.getRoot().getPseudoClassStates().contains(DARK);
                pane.pseudoClassStateChanged(DARK, isDark);
            }
        }

        // Ensure dialog uses its preferred height.
        pane.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    }
}
