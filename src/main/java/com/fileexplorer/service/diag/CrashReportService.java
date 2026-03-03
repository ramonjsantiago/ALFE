package com.fileexplorer.service.diag;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Best-effort crash snapshot writer.
 *
 * <p>Designed to be safe to call from an uncaught-exception handler.</p>
 */
public final class CrashReportService {

    private static final String DIR_NAME = ".fileexplorer";
    private static final String CRASH_DIR = "crash";
    private static final String LAST_CRASH = "last-crash.txt";

    private CrashReportService() {
    }

    /** Crash directory under user home. */
    public static Path crashDir() {
        return Paths.get(System.getProperty("user.home"), DIR_NAME, CRASH_DIR);
    }

    /** Path to the most recent crash report. */
    public static Path lastCrashFile() {
        return crashDir().resolve(LAST_CRASH);
    }

    

    private static final String LAST_SUCCESS = "last-success.txt";

    /** Marker file written after a successful startup. */
    public static Path lastSuccessFile() {
        return crashDir().resolve(LAST_SUCCESS);
    }

    /** Write/refresh the successful-startup marker. Best-effort. */
    public static void writeSuccessMarker() {
        try {
            Files.createDirectories(crashDir());
            String payload = "Last success: " + Instant.now().toString() + System.lineSeparator();
            Files.writeString(lastSuccessFile(), payload, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception ignored) {
        }
    }
/**
     * Write a crash report.
     *
     * @param thread thread where the exception was uncaught
     * @param error uncaught error/exception
     */
    public static void writeCrashReport(Thread thread, Throwable error) {
        if (error == null) return;
        try {
            Files.createDirectories(crashDir());
        } catch (Exception ignored) {
        }

        StringWriter sw = new StringWriter(16_384);
        PrintWriter pw = new PrintWriter(sw);
        pw.println("FileExplorer Crash Snapshot");
        pw.println("Timestamp: " + Instant.now());
        pw.println("Thread: " + (thread == null ? "(unknown)" : thread.getName()));
        pw.println("Java: " + System.getProperty("java.version"));
        pw.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        pw.println();
        error.printStackTrace(pw);
        pw.flush();

        try {
            Files.writeString(
                    lastCrashFile(),
                    sw.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (Exception ignored) {
        }
    }
}
