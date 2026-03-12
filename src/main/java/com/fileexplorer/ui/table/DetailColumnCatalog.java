package com.fileexplorer.ui.table;

import com.fileexplorer.ui.dialog.ChooseDetailsDialog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catalog of Explorer-style "Choose Details" fields.
 *
 * <p>The large optional field list is loaded from a resource so we do not eagerly create hundreds of columns.
 * Columns are created only when selected in the dialog or restored from preferences.</p>
 */
public final class DetailColumnCatalog {

    public static final String RESOURCE_PATH = "/com/fileexplorer/ui/details/choose_details_columns.txt";

    private static final List<ChooseDetailsDialog.DetailSpec> CACHE = buildCatalog();
    private static final Map<String, ChooseDetailsDialog.DetailSpec> BY_KEY = buildByKey(CACHE);

    private DetailColumnCatalog() {
    }

    public static List<ChooseDetailsDialog.DetailSpec> allSpecs() {
        return List.copyOf(CACHE);
    }

    public static Map<String, ChooseDetailsDialog.DetailSpec> specsByKey() {
        return Map.copyOf(BY_KEY);
    }

    public static List<String> defaultOrderedKeys() {
        List<String> keys = new ArrayList<>(CACHE.size());
        for (ChooseDetailsDialog.DetailSpec spec : CACHE) {
            keys.add(spec.key());
        }
        return keys;
    }

    public static String labelForKey(String key) {
        ChooseDetailsDialog.DetailSpec spec = BY_KEY.get(key);
        return spec != null ? spec.label() : key;
    }

    public static boolean isKnownKey(String key) {
        return BY_KEY.containsKey(key);
    }

    private static List<ChooseDetailsDialog.DetailSpec> buildCatalog() {
        List<ChooseDetailsDialog.DetailSpec> out = new ArrayList<>();
        out.add(new ChooseDetailsDialog.DetailSpec("name", "Name", true, true));
        out.add(new ChooseDetailsDialog.DetailSpec("modified", "Date modified", true, false));
        out.add(new ChooseDetailsDialog.DetailSpec("type", "Type", true, false));
        out.add(new ChooseDetailsDialog.DetailSpec("size", "Size", true, false));

        List<String> labels = readLabels();
        Map<String, Integer> counts = new HashMap<>();
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            String trimmed = label.trim();
            switch (trimmed) {
                case "Name", "Date modified", "Type", "Size" -> {
                    continue;
                }
                default -> {
                }
            }
            String baseKey = canonicalBaseKey(trimmed);
            int n = counts.merge(baseKey, 1, Integer::sum);
            String key = (n == 1) ? baseKey : (baseKey + "_" + n);
            out.add(new ChooseDetailsDialog.DetailSpec(key, trimmed, false, false));
        }
        return out;
    }

    private static Map<String, ChooseDetailsDialog.DetailSpec> buildByKey(List<ChooseDetailsDialog.DetailSpec> specs) {
        Map<String, ChooseDetailsDialog.DetailSpec> out = new LinkedHashMap<>();
        for (ChooseDetailsDialog.DetailSpec spec : specs) {
            out.put(spec.key(), spec);
        }
        return out;
    }

    private static List<String> readLabels() {
        InputStream in = DetailColumnCatalog.class.getResourceAsStream(RESOURCE_PATH);
        if (in == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return out;
    }

    private static String canonicalBaseKey(String label) {
        return switch (label) {
            case "#" -> "index";
            case "35mm focal length" -> "focalLength35mm";
            case "Date created" -> "dateCreated";
            case "Authors" -> "authors";
            case "Tags" -> "tags";
            case "Title" -> "title";
            default -> slug(label);
        };
    }

    private static String slug(String raw) {
        String s = raw.toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replace("'", "")
                .replace("/", " ")
                .replace("-", " ")
                .replace(".", " ")
                .replace(":", " ");
        String[] parts = s.split("[^a-z0-9]+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (out.isEmpty()) {
                out.append(part);
            } else {
                out.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    out.append(part.substring(1));
                }
            }
        }
        if (out.isEmpty()) {
            return "detail";
        }
        return out.toString();
    }
}
