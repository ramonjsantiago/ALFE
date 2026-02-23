package com.fileexplorer.service.template;

import com.fileexplorer.service.template.OperationTemplateService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Phase 7.1.0: Template Packs import/export.
 *
 * <p>A "template pack" is a portable zip containing one or more template definition files and, optionally,
 * the recurring schedules store. This is intentionally file-based (copies the existing on-disk stores) to keep
 * compatibility with prior persistence formats.</p>
 *
 * <h3>Pack structure</h3>
 * <ul>
 *   <li>{@code manifest.json} - minimal metadata</li>
 *   <li>{@code templates/<id>.template} - template files as persisted by {@link OperationTemplateService}</li>
 *   <li>{@code templates/recurring-schedules.properties} - optional schedules file</li>
 * </ul>
 */
public final class TemplatePackService {

    private TemplatePackService() {}

    public static final String ENTRY_MANIFEST = "manifest.json";
    public static final String DIR_TEMPLATES = "templates/";
    public static final String ENTRY_SCHEDULES = "templates/recurring-schedules.properties";

    /**
     * Export a template pack.
     *
     * @param outZip output zip path
     * @param templates template service (source of template files)
     * @param templateIds template ids to include (empty = include all)
     * @param recurringStore recurring schedule store (optional)
     * @param includeSchedules true to include recurring schedules file
     */
    public static void exportPack(Path outZip,
                                  OperationTemplateService templates,
                                  List<String> templateIds,
                                  TemplateRecurringScheduleService recurringStore,
                                  boolean includeSchedules) throws IOException {
        Objects.requireNonNull(outZip, "outZip");
        Objects.requireNonNull(templates, "templates");
        if (templateIds == null) templateIds = List.of();

        Files.createDirectories(outZip.toAbsolutePath().getParent());

        List<String> ids = new ArrayList<>();
        if (templateIds.isEmpty()) {
            for (var t : templates.list()) ids.add(t.id());
        } else {
            ids.addAll(templateIds);
        }

        // Build a tiny manifest (no external JSON libs).
        String manifest = "{\n" +
                "  \"format\": \"fileexplorer.templatePack\",\n" +
                "  \"formatVersion\": 1,\n" +
                "  \"createdAt\": \"" + Instant.now().toString() + "\",\n" +
                "  \"templates\": " + ids.size() + ",\n" +
                "  \"includesSchedules\": " + includeSchedules + "\n" +
                "}\n";

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outZip,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {

            // manifest
            ZipEntry man = new ZipEntry(ENTRY_MANIFEST);
            zos.putNextEntry(man);
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // templates
            for (String id : ids) {
                Path file = templates.templatePath(id);
                if (!Files.isRegularFile(file)) continue;
                String entryName = DIR_TEMPLATES + id + ".template";
                putFile(zos, entryName, file);
            }

            // schedules (optional)
            if (includeSchedules && recurringStore != null) {
                Path schedules = recurringStore.schedulesFile();
                if (Files.isRegularFile(schedules)) {
                    putFile(zos, ENTRY_SCHEDULES, schedules);
                }
            }
        }
    }

    /**
     * Import a template pack into the current user's stores.
     *
     * @param inZip input zip
     * @param templates template service (target)
     * @param recurringStore recurring schedule store (optional)
     * @param overwriteExisting overwrite existing template files and schedules file
     * @return import report
     */
    public static ImportReport importPack(Path inZip,
                                         OperationTemplateService templates,
                                         TemplateRecurringScheduleService recurringStore,
                                         boolean overwriteExisting) throws IOException {
        Objects.requireNonNull(inZip, "inZip");
        Objects.requireNonNull(templates, "templates");

        int importedTemplates = 0;
        boolean schedulesImported = false;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(inZip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name == null) continue;

                if (name.startsWith(DIR_TEMPLATES) && name.endsWith(".template")) {
                    String fn = name.substring(DIR_TEMPLATES.length());
                    String id = fn.substring(0, fn.length() - ".template".length());
                    if (id.isBlank()) continue;

                    Path target = templates.templatePath(id);
                    Files.createDirectories(target.getParent());

                    if (!overwriteExisting && Files.exists(target)) {
                        // skip
                    } else {
                        copyEntry(zis, target);
                        importedTemplates++;
                    }
                } else if (ENTRY_SCHEDULES.equals(name) && recurringStore != null) {
                    Path target = recurringStore.schedulesFile();
                    Files.createDirectories(target.getParent());
                    if (!overwriteExisting && Files.exists(target)) {
                        // skip
                    } else {
                        copyEntry(zis, target);
                        schedulesImported = true;
                    }
                }

                zis.closeEntry();
            }
        }

        return new ImportReport(importedTemplates, schedulesImported);
    }

    private static void putFile(ZipOutputStream zos, String entryName, Path file) throws IOException {
        ZipEntry e = new ZipEntry(entryName);
        zos.putNextEntry(e);
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(zos);
        }
        zos.closeEntry();
    }

    private static void copyEntry(InputStream in, Path out) throws IOException {
        try (OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            in.transferTo(os);
        }
    }

    /**
     * Simple import report for UI.
     */
    public record ImportReport(int templatesImported, boolean schedulesImported) {}
}
