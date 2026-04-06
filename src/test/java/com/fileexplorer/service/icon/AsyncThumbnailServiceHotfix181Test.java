package com.fileexplorer.service.icon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncThumbnailServiceHotfix181Test {

    @Test
    void pdfTimeoutCooldownInvalidatesWhenFileIdentityChanges() throws Exception {
        AsyncThumbnailService service = AsyncThumbnailService.getInstance();

        Method markCooldown = AsyncThumbnailService.class.getDeclaredMethod(
                "markPdfTimeoutCooldown", Path.class, long.class, long.class);
        Method isInCooldown = AsyncThumbnailService.class.getDeclaredMethod(
                "isPdfInTimeoutCooldown", Path.class, long.class, long.class);
        Method safeLastModifiedMs = AsyncThumbnailService.class.getDeclaredMethod(
                "safeLastModifiedMs", Path.class);
        Method safeFileSizeBytes = AsyncThumbnailService.class.getDeclaredMethod(
                "safeFileSizeBytes", Path.class);

        markCooldown.setAccessible(true);
        isInCooldown.setAccessible(true);
        safeLastModifiedMs.setAccessible(true);
        safeFileSizeBytes.setAccessible(true);

        Path pdf = Files.createTempFile("hotfix181-", ".pdf");
        try {
            Files.writeString(pdf,
                    "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n",
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.TRUNCATE_EXISTING);

            long lastModified1 = (Long) safeLastModifiedMs.invoke(null, pdf);
            long size1 = (Long) safeFileSizeBytes.invoke(null, pdf);

            markCooldown.invoke(service, pdf, lastModified1, size1);
            boolean initialCooldown = (Boolean) isInCooldown.invoke(service, pdf, lastModified1, size1);
            assertTrue(initialCooldown, "Expected the original PDF identity to be in cooldown");

            Thread.sleep(15L);
            Files.writeString(pdf, "\n%changed\n", StandardCharsets.US_ASCII, StandardOpenOption.APPEND);

            long lastModified2 = (Long) safeLastModifiedMs.invoke(null, pdf);
            long size2 = (Long) safeFileSizeBytes.invoke(null, pdf);
            assertTrue(lastModified1 != lastModified2 || size1 != size2,
                    "Test setup must change either size or last-modified time");

            boolean changedIdentityCooldown = (Boolean) isInCooldown.invoke(service, pdf, lastModified2, size2);
            assertFalse(changedIdentityCooldown,
                    "A changed PDF identity must invalidate the prior timeout cooldown");
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    @Test
    void pdfAndOfficeDocumentsUseSeparateExecutors() throws Exception {
        AsyncThumbnailService service = AsyncThumbnailService.getInstance();

        Method documentExecutorFor = AsyncThumbnailService.class.getDeclaredMethod("documentExecutorFor", String.class);
        documentExecutorFor.setAccessible(true);

        Field officeExecutorField = AsyncThumbnailService.class.getDeclaredField("officeDocumentExecutor");
        Field pdfExecutorField = AsyncThumbnailService.class.getDeclaredField("pdfDocumentExecutor");
        officeExecutorField.setAccessible(true);
        pdfExecutorField.setAccessible(true);

        ExecutorService officeExecutor = (ExecutorService) officeExecutorField.get(service);
        ExecutorService pdfExecutor = (ExecutorService) pdfExecutorField.get(service);

        ExecutorService routedPdfExecutor = (ExecutorService) documentExecutorFor.invoke(service, "pdf");
        ExecutorService routedDocxExecutor = (ExecutorService) documentExecutorFor.invoke(service, "docx");
        ExecutorService routedXlsxExecutor = (ExecutorService) documentExecutorFor.invoke(service, "xlsx");

        assertSame(pdfExecutor, routedPdfExecutor, "PDF work should be quarantined onto the dedicated PDF lane");
        assertSame(officeExecutor, routedDocxExecutor, "DOCX work should stay on the Office-family document lane");
        assertSame(officeExecutor, routedXlsxExecutor, "XLSX work should stay on the Office-family document lane");
        assertNotSame(routedPdfExecutor, routedDocxExecutor, "PDF and Office-family thumbnails must not share the same executor lane");
    }
}
