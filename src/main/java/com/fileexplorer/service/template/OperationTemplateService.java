package com.fileexplorer.service.template;

import com.fileexplorer.service.ops.ExecutionDriftPolicy;
import com.fileexplorer.service.ops.FileOperationType;
import com.fileexplorer.service.ops.rollback.RollbackMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 5.2.0: Template persistence.
 *
 * <p>Persistence format is dependency-free and intentionally simple (key=value per line).</p>
 */
public final class OperationTemplateService {

    private static final String DIR_NAME = ".fileexplorer";
    private static final String TEMPLATES_DIR = "templates";

/**
 * templatesDir.
 *
 * @return TODO
 */
    public Path templatesDir() {
        return Paths.get(System.getProperty("user.home"), DIR_NAME, TEMPLATES_DIR);
    }

/**
 * list.
 *
 * @return TODO
 */
    public List<OperationTemplate> list() {
        Path dir = templatesDir();
        if (!Files.isDirectory(dir)) return List.of();
        try {
            List<OperationTemplate> out = new ArrayList<>();
            try (var s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".template"))
                        .forEach(p -> read(templateIdFromPath(p)).ifPresent(out::add));
            }
            out.sort(Comparator.comparing(OperationTemplate::name, String.CASE_INSENSITIVE_ORDER));
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

/**
 * read.
 *
 * @param id TODO
 * @return TODO
 */
    public Optional<OperationTemplate> read(String id) {
        Objects.requireNonNull(id, "id");
        Path file = templatePath(id);
        if (!Files.exists(file)) return Optional.empty();

        try {
            Map<String, String> m = new HashMap<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int i = line.indexOf('=');
                if (i <= 0) continue;
                String k = line.substring(0, i).trim();
                String v = line.substring(i + 1).trim();
                m.put(k, unescape(v));
            }

            String name = m.getOrDefault("name", id);
            FileOperationType type = FileOperationType.valueOf(m.getOrDefault("type", "COPY"));
            List<String> sources = splitList(m.getOrDefault("sources", ""));
            String target = m.getOrDefault("target", "");
            String conflictProfileId = emptyToNull(m.get("conflictProfileId"));
            ExecutionDriftPolicy driftPolicy = ExecutionDriftPolicy.valueOf(m.getOrDefault("driftPolicy", ExecutionDriftPolicy.FAIL_FAST.name()));
            RollbackMode rollbackMode = RollbackMode.valueOf(m.getOrDefault("rollbackMode", RollbackMode.ASK.name()));
            boolean batch = Boolean.parseBoolean(m.getOrDefault("batchTransaction", "false"));

            return Optional.of(new OperationTemplate(
                    id,
                    name,
                    type,
                    sources,
                    target,
                    conflictProfileId,
                    driftPolicy,
                    rollbackMode,
                    batch
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

/**
 * save.
 *
 * @param t TODO
 */
    public void save(OperationTemplate t) {
        Objects.requireNonNull(t, "t");
        Path file = templatePath(t.id());

        try {
            Files.createDirectories(file.getParent());

            List<String> lines = new ArrayList<>();
            lines.add("# FileExplorer operation template (Phase 5.2.0)");
            lines.add("name=" + escape(t.name()));
            lines.add("type=" + t.type().name());
            lines.add("sources=" + escape(String.join("|", t.sources())));
            lines.add("target=" + escape(t.target()));
            if (t.conflictProfileId() != null) {
                lines.add("conflictProfileId=" + escape(t.conflictProfileId()));
            }
            lines.add("driftPolicy=" + t.driftPolicy().name());
            lines.add("rollbackMode=" + t.rollbackMode().name());
            lines.add("batchTransaction=" + t.batchTransaction());

            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ignored) {
            // best effort
        }
    }

/**
 * delete.
 *
 * @param id TODO
 */
    public void delete(String id) {
        Objects.requireNonNull(id, "id");
        try {
            Files.deleteIfExists(templatePath(id));
        } catch (IOException ignored) {
        }
    }

/**
 * newId.
 *
 * @return TODO
 */
    public String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

/**
 * templatePath.
 *
 * @param id TODO
 * @return TODO
 */
    public Path templatePath(String id) {
        return templatesDir().resolve(id + ".template");
    }

    private static String templateIdFromPath(Path p) {
        String fn = p.getFileName().toString();
        return fn.substring(0, fn.length() - ".template".length());
    }

/**
 * buildSimple.
 *
 * @param name TODO
 * @param type TODO
 * @param sourcesCsv TODO
 * @param target TODO
 * @return TODO
 */
    public static OperationTemplate buildSimple(String name, String type, String sourcesCsv, String target) {
        String id = UUID.randomUUID().toString().replace("-", "");
        FileOperationType opType = FileOperationType.valueOf(type == null ? "COPY" : type);
        List<String> sources = new ArrayList<>();
        if (sourcesCsv != null) {
            for (String s : sourcesCsv.split(",")) {
                String v = s.trim();
                if (!v.isEmpty()) sources.add(v);
            }
        }
        String tgt = target == null ? "" : target.trim();
        return new OperationTemplate(
                id,
                name == null ? "Template" : name.trim(),
                opType,
                sources,
                tgt,
                null,
                ExecutionDriftPolicy.FAIL_FAST,
                RollbackMode.ASK,
                false
        );
    }

/**
 * splitList.
 *
 * @param s TODO
 * @return TODO
 */
    private static List<String> splitList(String s) {
        if (s == null || s.isBlank()) return List.of();
        String[] parts = s.split("\\|");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String v = p.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

/**
 * emptyToNull.
 *
 * @param s TODO
 * @return TODO
 */
    private static String emptyToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }

/**
 * escape.
 *
 * @param s TODO
 * @return TODO
 */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "").replace("=", "\\=");
    }

/**
 * unescape.
 *
 * @param s TODO
 * @return TODO
 */
    private static String unescape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                if (c == 'n') out.append('\n');
                else out.append(c);
                esc = false;
            } else {
                if (c == '\\') esc = true;
                else out.append(c);
            }
        }
        if (esc) out.append('\\');
        return out.toString();
    }

/**
 * Validate a template for basic completeness and internal consistency.
 *
 * <p>This is intentionally "static" validation: it does not touch the filesystem and does not attempt to
 * verify that paths exist. The goal is to prevent creating templates that are obviously unusable.</p>
 *
 * @param t template to validate
 * @return list of error messages; empty means valid
 */
public java.util.List<String> validate(OperationTemplate t) {
    java.util.Objects.requireNonNull(t, "t");
    java.util.List<String> errors = new java.util.ArrayList<>();

    String name = t.name() == null ? "" : t.name().trim();
    if (name.isEmpty()) errors.add("Name is required.");

    if (t.type() == null) {
        errors.add("Operation type is required.");
    }

    java.util.List<String> src = t.sources() == null ? java.util.List.of() : t.sources();
    long nonBlankSources = src.stream().filter(s -> s != null && !s.trim().isEmpty()).count();
    if (t.type() != null && t.type() != com.fileexplorer.service.ops.FileOperationType.DELETE) {
        if (nonBlankSources <= 0) errors.add("At least one source path is required.");
    } else {
        if (nonBlankSources <= 0) errors.add("At least one target path is required for DELETE.");
    }

    // For COPY/MOVE, a non-empty target directory is required.
    String target = t.target() == null ? "" : t.target().trim();
    if (t.type() != null && t.type() != com.fileexplorer.service.ops.FileOperationType.DELETE) {
        if (target.isEmpty()) errors.add("Target directory is required for " + t.type().name() + ".");
    }

    // Duplicate sources (case-insensitive) are almost always user error.
    java.util.Map<String, Integer> seen = new java.util.HashMap<>();
    for (String s : src) {
        if (s == null) continue;
        String v = s.trim();
        if (v.isEmpty()) continue;
        String k = v.toLowerCase();
        seen.put(k, seen.getOrDefault(k, 0) + 1);
    }
    java.util.List<String> dups = seen.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(java.util.Map.Entry::getKey)
            .sorted()
            .toList();
    if (!dups.isEmpty()) {
        errors.add("Duplicate sources detected: " + String.join(", ", dups));
    }

    return errors;
}

/**
 * Human-friendly formatting for validation errors.
 *
 * @param errors errors from {@link #validate(OperationTemplate)}
 * @return formatted string
 */
public static String formatValidationErrors(java.util.List<String> errors) {
    if (errors == null || errors.isEmpty()) return "No issues found.";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < errors.size(); i++) {
        sb.append("• ").append(errors.get(i));
        if (i < errors.size() - 1) sb.append("\n");
    }
    return sb.toString();
}

}
