package com.fileexplorer.service.diag;

import com.fileexplorer.app.ExplorerContext;
import com.fileexplorer.service.template.SchedulerSettings;
import com.fileexplorer.service.template.SchedulerSettingsService;

import com.fileexplorer.service.diag.CrashReportService;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Best-effort generator for a support/diagnostics zip bundle.
 */
public final class DiagnosticsBundleService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    /**
     * Generate a support bundle zip.
     *
     * <p>The bundle is best-effort: missing files are skipped. Any IO errors are surfaced to the caller.</p>
     *
     * @param context app context
     * @param outZip destination zip path
     * @return the written zip path
     */
    public Path generate(ExplorerContext context, Path outZip) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(outZip, "outZip");

        if (outZip.getParent() != null) {
            Files.createDirectories(outZip.getParent());
        }

        List<Path> candidateFiles = new ArrayList<>();
        // Scheduler / templates
        candidateFiles.add(context.templateRecurringScheduleService().schedulesFile());
        candidateFiles.add(context.templateRunHistoryService().historyFile());
        // Operations history
        candidateFiles.add(context.operationHistoryService().historyFile());

        // Crash snapshot (if present)
        candidateFiles.add(CrashReportService.lastCrashFile());

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outZip))) {
            // metadata
            writeString(zos, "bundle/meta.txt",
                    "FileExplorer Support Bundle\n" +
                    "Generated: " + Instant.now() + "\n" +
                    "Java: " + System.getProperty("java.version") + "\n" +
                    "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")\n");

            // scheduler settings snapshot
            SchedulerSettings settings = new SchedulerSettingsService().load();
            writeString(zos, "scheduler/settings.json", toJson(settings));

            for (Path p : candidateFiles) {
                if (p == null) continue;
                if (!Files.exists(p)) continue;
                String entryName = "files/" + safeFileName(p);
                addFile(zos, entryName, p);
            }
        }

        return outZip;
    }

    /**
     * Default filename suggestion.
     */
    public String defaultFileName() {
        return "fileexplorer-support-bundle-" + TS.format(Instant.now()) + ".zip";
    }

    private static void addFile(ZipOutputStream zos, String entryName, Path file) throws IOException {
        ZipEntry e = new ZipEntry(entryName);
        e.setTime(Files.getLastModifiedTime(file).toMillis());
        zos.putNextEntry(e);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            in.transferTo(zos);
        }
        zos.closeEntry();
    }

    private static void writeString(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry e = new ZipEntry(entryName);
        zos.putNextEntry(e);
        byte[] b = content.getBytes(StandardCharsets.UTF_8);
        zos.write(b);
        zos.closeEntry();
    }

    private static String safeFileName(Path p) {
        String name = p.getFileName() == null ? "file" : p.getFileName().toString();
        // If multiple files share a name in different dirs, include a short hash.
        int h = Math.abs(p.toString().hashCode());
        return h + "-" + name;
    }

    private static String toJson(SchedulerSettings s) {
        return "{\n" +
                "  \"tickSeconds\": " + s.tickSeconds() + ",\n" +
                "  \"maxParallel\": " + s.maxParallel() + ",\n" +
                "  \"maxRetryAttempts\": " + s.maxRetryAttempts() + ",\n" +
                "  \"retryBaseMillis\": " + s.retryBaseMillis() + ",\n" +
                "  \"retryMaxMillis\": " + s.retryMaxMillis() + ",\n" +
                "  \"historyRetentionEntries\": " + s.historyRetentionEntries() + "\n" +
                "}\n";
    }
}
