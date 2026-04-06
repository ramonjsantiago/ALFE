package com.fileexplorer.perf.viewport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameBudgetTest {
    @Test
    void acceptsAndRejectsSpendRequestsCorrectly() {
        FrameBudget budget = new FrameBudget(100L);

        assertTrue(budget.trySpend(60L));
        assertEquals(40L, budget.remainingNanos());
        assertFalse(budget.trySpend(50L));
        assertEquals(60L, budget.spentNanos());
    }
}
