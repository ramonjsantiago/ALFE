package com.fileexplorer.ui;

/**
 * Small static accessor so RibbonBarController can call into MainController
 * without fragile lookup logic. MainController.initialize() sets the reference.
 */
public final class MainControllerAccessor {
    private static volatile MainController instance;
    private MainControllerAccessor() {}
    public static void set(MainController c) { instance = c; }
    public static MainController get() { return instance; }
}
