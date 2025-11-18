package com.fileexplorer.ui;

import java.util.Locale;

public final class ThemeUtils {
    public enum BaseTheme { LIGHT, DARK }
    public enum Overlay { NONE, GLASSY, MICA, ACRYLIC }

    public static String baseCssFile(BaseTheme t) {
        return switch(t) {
            case DARK -> "/css/explorer-dark.css";
            default -> "/css/explorer-light.css";
        };
    }

    public static String overlayCssFile(Overlay o) {
        return switch(o) {
            case GLASSY -> "/css/explorer-glassy.css";
            case MICA -> "/css/mica-acrylic.css";
            case ACRYLIC -> "/css/mica-acrylic.css";
            default -> null;
        };
    }

    private ThemeUtils() {}
}
