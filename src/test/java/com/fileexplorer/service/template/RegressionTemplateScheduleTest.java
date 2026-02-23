package com.fileexplorer.service.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6.5.0: Headless checks around recurring schedule persistence.
 */
public class RegressionTemplateScheduleTest {

    @Test
    void recurringSchedule_roundTrip(@TempDir Path home) {
        String original = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());

            TemplateRecurringScheduleService s = new TemplateRecurringScheduleService();
            String id = "t-1";
            s.removeRecurring(id);
            s.setRecurringMinutes(id, 30);
            assertEquals(30L, s.getRecurringMinutes(id).orElseThrow());

            s.setSchedule(id, 60, 0L, System.currentTimeMillis());
            assertEquals(60L, s.getSchedule(id).orElseThrow().minutes());
        } finally {
            if (original != null) System.setProperty("user.home", original);
        }
    }
}
