// package com.explorer.ui;

// import java.io.IOException;
// import java.nio.file.*;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.function.Predicate;

// public class SearchService {

    // public List<Path> search(Path root, String query, boolean recursive) {
        // List<Path> results = new ArrayList<>();
        // Predicate<Path> match = p ->
            // p.getFileName().toString().toLowerCase().contains(query.toLowerCase());

        // try {
            // if (recursive) {
                // try (var stream = Files.walk(root)) {
                    // stream.filter(Files::exists)
                          // .filter(Files::isReadable)
                          // .filter(match)
                          // .forEach(results::add);
                // }
            // } else {
                // try (var stream = Files.list(root)) {
                    // stream.filter(Files::exists)
                          // .filter(Files::isReadable)
                          // .filter(match)
                          // .forEach(results::add);
                // }
            // }
        // } catch (IOException ignored) {}

        // return results;
    // }
// }
package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * SearchService
 *  - Performs recursive or shallow search
 *  - Supports name-based and predicate-based matching
 *  - Designed for async execution (Task<> inside controllers)
 */
public class SearchService {

    public List<Path> search(Path root, String query, boolean recursive) {
        List<Path> results = new ArrayList<>();
        String qLower = query.toLowerCase();

        try {
            if (recursive) {
                Files.walk(root).forEach(p -> {
                    if (p.getFileName().toString().toLowerCase().contains(qLower)) {
                        results.add(p);
                    }
                });
            } else {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                    for (Path p : ds) {
                        if (p.getFileName().toString().toLowerCase().contains(qLower)) {
                            results.add(p);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // ignore or log
        }

        return results;
    }

    /** Generic predicate-based search */
    public List<Path> search(Path root, Predicate<Path> filter, boolean recursive) {
        List<Path> results = new ArrayList<>();

        try {
            if (recursive) {
                Files.walk(root).forEach(p -> {
                    if (filter.test(p)) {
                        results.add(p);
                    }
                });
            } else {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                    for (Path p : ds) {
                        if (filter.test(p)) {
                            results.add(p);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // ignore or log
        }
        return results;
    }
}

