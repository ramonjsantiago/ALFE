package com.fileexplorer.service.icon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncThumbnailServiceHotfix182Test {

    @Test
    void adaptivePdfTimeoutBudgetExpandsForLargeFilesAndSlowHistory() throws Exception {
        AsyncThumbnailService service = AsyncThumbnailService.getInstance();

        Method computeAdaptivePdfTimeoutMs = AsyncThumbnailService.class.getDeclaredMethod(
                "computeAdaptivePdfTimeoutMs", long.class, int.class, double.class);
        computeAdaptivePdfTimeoutMs.setAccessible(true);

        long smallBudget = (Long) computeAdaptivePdfTimeoutMs.invoke(service, 2L * 1024L * 1024L, 96, 0.0d);
        long largeSlowBudget = (Long) computeAdaptivePdfTimeoutMs.invoke(service, 20L * 1024L * 1024L, 256, 5200.0d);

        assertTrue(largeSlowBudget > smallBudget,
                "Adaptive PDF timeout budgeting should expand for larger/slower PDFs");
    }

    @Test
    void adaptivePdfTargetSizeShrinksForLargeDocumentFirstPageHeuristics() throws Exception {
        AsyncThumbnailService service = AsyncThumbnailService.getInstance();

        Method computeAdaptivePdfTargetSizePx = AsyncThumbnailService.class.getDeclaredMethod(
                "computeAdaptivePdfTargetSizePx",
                int.class,
                long.class,
                int.class,
                float.class,
                float.class,
                double.class,
                int.class);
        computeAdaptivePdfTargetSizePx.setAccessible(true);

        int requested = 256;
        int adapted = (Integer) computeAdaptivePdfTargetSizePx.invoke(
                service,
                requested,
                24L * 1024L * 1024L,
                240,
                1600.0f,
                2200.0f,
                3800.0d,
                2);

        assertTrue(adapted < requested,
                "Large-document first-page heuristics should downshift the PDF target thumbnail size");
        assertTrue(adapted <= 96,
                "Large documents with repeated slow renders should clamp to the most conservative target size");
    }

    @Test
    void largePdfRecoveryFallbackResetsAfterTheFileChanges() throws Exception {
        AsyncThumbnailService service = AsyncThumbnailService.getInstance();

        Method recordPdfRenderTimeout = AsyncThumbnailService.class.getDeclaredMethod(
                "recordPdfRenderTimeout", Path.class, long.class, long.class, long.class);
        Method shouldFallbackLargePdf = AsyncThumbnailService.class.getDeclaredMethod(
                "shouldFallbackLargePdf", Path.class, long.class, long.class);
        Method safeLastModifiedMs = AsyncThumbnailService.class.getDeclaredMethod(
                "safeLastModifiedMs", Path.class);
        Method safeFileSizeBytes = AsyncThumbnailService.class.getDeclaredMethod(
                "safeFileSizeBytes", Path.class);

        recordPdfRenderTimeout.setAccessible(true);
        shouldFallbackLargePdf.setAccessible(true);
        safeLastModifiedMs.setAccessible(true);
        safeFileSizeBytes.setAccessible(true);

        Path pdf = Files.createTempFile("hotfix182-", ".pdf");
        try {
            byte[] largePayload = new byte[17 * 1024 * 1024];
            byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(header, 0, largePayload, 0, header.length);
            Files.write(pdf, largePayload, StandardOpenOption.TRUNCATE_EXISTING);

            long lastModified1 = (Long) safeLastModifiedMs.invoke(null, pdf);
            long size1 = (Long) safeFileSizeBytes.invoke(null, pdf);

            recordPdfRenderTimeout.invoke(service, pdf, lastModified1, size1, 5000L);
            recordPdfRenderTimeout.invoke(service, pdf, lastModified1, size1, 5000L);

            boolean inRecovery = (Boolean) shouldFallbackLargePdf.invoke(service, pdf, lastModified1, size1);
            assertTrue(inRecovery,
                    "Large PDFs with a timeout streak should enter direct-recovery fallback mode");

            Thread.sleep(15L);
            Files.write(pdf, "\n%changed\n".getBytes(StandardCharsets.US_ASCII), StandardOpenOption.APPEND);

            long lastModified2 = (Long) safeLastModifiedMs.invoke(null, pdf);
            long size2 = (Long) safeFileSizeBytes.invoke(null, pdf);
            assertTrue(lastModified1 != lastModified2 || size1 != size2,
                    "Test setup must change either size or last-modified time");

            boolean changedRecovery = (Boolean) shouldFallbackLargePdf.invoke(service, pdf, lastModified2, size2);
            assertFalse(changedRecovery,
                    "Changing the PDF must invalidate the prior large-document recovery state");
        } finally {
            Files.deleteIfExists(pdf);
        }
    }
}
