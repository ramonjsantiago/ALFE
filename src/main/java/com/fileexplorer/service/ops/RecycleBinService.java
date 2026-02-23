package com.fileexplorer.service.ops;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Phase 3.9.7: App-managed recycle bin to support reliable Undo for DELETE (send-to-trash).
 *
 * <p>We do NOT rely on platform recycle-bin APIs (which generally don't expose restore). Instead,
 * we move items into a private recycle directory and persist a small JSONL manifest mapping
 * original path -> recycled path.</p>
 */
public final class RecycleBinService {

    private static final String ROOT_DIR_NAME = ".fileexplorer";
    private static final String RECYCLE_DIR_NAME = "recycle-bin";
    private static final String MANIFEST_NAME = "recycle-manifest.jsonl";

    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean indexLoaded = false;

    /**
     * Maps original absolute path string -> deque of recycled paths (most recent first).
     */
    private final Map<String, Deque<String>> index = new HashMap<>();

/**
 * recycleDir.
 *
 * @return TODO
 */
    public Path recycleDir() {
        Path home = Path.of(System.getProperty("user.home", "."));
        return home.resolve(ROOT_DIR_NAME).resolve(RECYCLE_DIR_NAME);
    }

/**
 * manifestPath.
 *
 * @return TODO
 */
    private Path manifestPath() {
        return recycleDir().resolve(MANIFEST_NAME);
    }

    /**
     * Moves {@code src} into the recycle bin and records a manifest entry.
     *
     * @return the recycled path
     */
    public Path moveToRecycle(Path src) throws IOException {
        Objects.requireNonNull(src, "src");
        Path abs = src.toAbsolutePath().normalize();

        lock.lock();
        try {
            ensureDirs();
            loadIndexIfNeeded();

            String id = UUID.randomUUID().toString();
            String name = abs.getFileName() == null ? "item" : abs.getFileName().toString();
            String safeName = sanitizeName(name);
            Path dst = recycleDir().resolve(id + "__" + safeName);

            // Ensure uniqueness (paranoia)
            int i = 0;
            while (Files.exists(dst)) {
                i++;
                dst = recycleDir().resolve(id + "_" + i + "__" + safeName);
            }

            Files.move(abs, dst, StandardCopyOption.ATOMIC_MOVE);

            appendManifest(abs, dst, id);

            index.computeIfAbsent(abs.toString(), k -> new ArrayDeque<>()).addFirst(dst.toString());
            return dst;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the most recent recycled path for {@code original} that still exists on disk.
     */
    public Optional<Path> resolveLatestRecycled(Path original) {
        if (original == null) return Optional.empty();
        Path abs = original.toAbsolutePath().normalize();
        lock.lock();
        try {
            ensureDirs();
            loadIndexIfNeeded();
            Deque<String> dq = index.get(abs.toString());
            if (dq == null) return Optional.empty();

            while (!dq.isEmpty()) {
                Path candidate = Path.of(dq.peekFirst());
                if (Files.exists(candidate)) {
                    return Optional.of(candidate);
                }
                dq.removeFirst(); // stale
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Best-effort prune of old manifest lines is out of scope for 3.9.7; the WAL already has rotation.
     */
    private void appendManifest(Path originalAbs, Path recycledAbs, String id) throws IOException {
        String json = toJsonLine(originalAbs, recycledAbs, id);
        try (BufferedWriter w = Files.newBufferedWriter(manifestPath(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            w.write(json);
            w.newLine();
        }
    }

/**
 * toJsonLine.
 *
 * @param originalAbs TODO
 * @param recycledAbs TODO
 * @param id TODO
 * @return TODO
 */
    private String toJsonLine(Path originalAbs, Path recycledAbs, String id) {
        String ts = Instant.now().toString();
        return "{"
                + "\"id\":\"" + esc(id) + "\","
                + "\"original\":\"" + esc(originalAbs.toString()) + "\","
                + "\"recycled\":\"" + esc(recycledAbs.toString()) + "\","
                + "\"ts\":\"" + esc(ts) + "\""
                + "}";
    }

/**
 * esc.
 *
 * @param s TODO
 * @return TODO
 */
    private static String esc(String s) {
        if (s == null) return "";
        // minimal JSON escaping
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

/**
 * sanitizeName.
 *
 * @param name TODO
 * @return TODO
 */
    private static String sanitizeName(String name) {
        // keep filename-ish; remove path separators and control chars
        String n = name.replace('/', '_').replace('\\', '_');
        n = n.replaceAll("\\p{Cntrl}", "_").trim();
        if (n.isBlank()) return "item";
        return n;
    }

/**
 * ensureDirs.
 *
 */
    private void ensureDirs() throws IOException {
        Files.createDirectories(recycleDir());
    }

/**
 * loadIndexIfNeeded.
 *
 */
    private void loadIndexIfNeeded() throws IOException {
        if (indexLoaded) return;
        Path m = manifestPath();
        if (!Files.exists(m)) {
            indexLoaded = true;
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(m, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                // extremely small parser: find "original":"...","recycled":"..."
                String original = extractJsonField(line, "original");
                String recycled = extractJsonField(line, "recycled");
                if (original == null || recycled == null) continue;
                index.computeIfAbsent(original, k -> new ArrayDeque<>()).addFirst(recycled);
            }
        }
        indexLoaded = true;
    }

/**
 * extractJsonField.
 *
 * @param json TODO
 * @param field TODO
 * @return TODO
 */
    private static String extractJsonField(String json, String field) {
        // expects: "field":"value"
        String key = "\"" + field + "\"";
        int k = json.indexOf(key);
        if (k < 0) return null;
        int c = json.indexOf(':', k + key.length());
        if (c < 0) return null;
        int q1 = json.indexOf('"', c + 1);
        if (q1 < 0) return null;
        int q2 = q1 + 1;
        boolean esc = false;
        StringBuilder sb = new StringBuilder();
        while (q2 < json.length()) {
            char ch = json.charAt(q2);
            if (esc) {
                sb.append(ch);
                esc = false;
            } else if (ch == '\\') {
                esc = true;
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
            q2++;
        }
        return sb.toString().replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
