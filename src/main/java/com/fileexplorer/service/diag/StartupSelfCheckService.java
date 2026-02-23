package com.fileexplorer.service.diag;

import com.fileexplorer.app.ExplorerContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Startup self-check and best-effort quarantine/repair.
 *
 * <p>This is intentionally conservative: it only validates that key persistence files can be read.
 * If a file appears corrupted/unreadable, it is moved aside (quarantined) so the app can start.</p>
 */
public final class StartupSelfCheckService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private static final String DIR_NAME = ".fileexplorer";

    public record SelfCheckResult(boolean hadIssues, String report) {
    }

    /**
     * Run self-check against the on-disk state.
     */
    public SelfCheckResult run(ExplorerContext context) {
        Objects.requireNonNull(context, "context");

        List<String> lines = new ArrayList<>();
        boolean issues = false;

        // Operation queue persistence file (package-private persistence class; check file path directly).
        Path opQueue = Path.of(System.getProperty("user.home"), DIR_NAME, "operation-queue.tsv");
        issues |= checkReadableOrQuarantine(opQueue, lines);

        // Templates/scheduler persistence.
        issues |= checkReadableOrQuarantine(context.templateRecurringScheduleService().schedulesFile(), lines);
        issues |= checkReadableOrQuarantine(context.templateRunHistoryService().historyFile(), lines);

        // Operation history.
        issues |= checkReadableOrQuarantine(context.operationHistoryService().historyFile(), lines);

        if (!issues) {
            return new SelfCheckResult(false, "Startup self-check: OK");
        }

        StringBuilder sb = new StringBuilder(2048);
        sb.append("Startup self-check detected issues and quarantined files where needed.\n\n");
        for (String s : lines) {
            sb.append("- ").append(s).append('\n');
        }
        sb.append("\nQuarantined files are kept alongside the original with a .corrupt-<timestamp> suffix.");
        return new SelfCheckResult(true, sb.toString());
    }

    private static boolean checkReadableOrQuarantine(Path file, List<String> reportLines) {
        if (file == null) return false;
        if (!Files.exists(file)) return false;
        try {
            // Read a small amount; if this throws, treat as unreadable.
            Files.readAllLines(file, StandardCharsets.UTF_8);
            reportLines.add("OK: " + file);
            return false;
        } catch (IOException ex) {
            boolean quarantined = quarantine(file, reportLines, ex);
            return quarantined;
        } catch (Exception ex) {
            boolean quarantined = quarantine(file, reportLines, ex);
            return quarantined;
        }
    }

    private static boolean quarantine(Path file, List<String> reportLines, Exception ex) {
        try {
            Path q = file.resolveSibling(file.getFileName() + ".corrupt-" + TS.format(Instant.now()));
            Files.createDirectories(q.getParent());
            Files.move(file, q);
            reportLines.add("QUARANTINED: " + file + " -> " + q + " (" + ex.getClass().getSimpleName() + ")");
            return true;
        } catch (Exception moveEx) {
            reportLines.add("FAILED TO QUARANTINE: " + file + " (" + moveEx.getClass().getSimpleName() + ")");
            return true;
        }
    }
}
