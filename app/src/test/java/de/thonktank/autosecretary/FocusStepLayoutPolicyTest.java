package de.thonktank.autosecretary;

import de.thonktank.autosecretary.ui.today.FocusStepLayoutPolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FocusStepLayoutPolicyTest {
    @Test public void admitsFollowingRowsInOrderWithinHeightBudget() {
        int[] rows = {40, 60, 50};

        assertEquals(0, FocusStepLayoutPolicy.visibleFollowing(139, 100, rows, 20, 3));
        assertEquals(1, FocusStepLayoutPolicy.visibleFollowing(160, 100, rows, 20, 3));
        assertEquals(2, FocusStepLayoutPolicy.visibleFollowing(220, 100, rows, 20, 3));
        assertEquals(3, FocusStepLayoutPolicy.visibleFollowing(250, 100, rows, 20, 3));
    }

    @Test public void numericPreferenceRemainsAnUpperBoundOnLargeViewports() {
        assertEquals(1, FocusStepLayoutPolicy.visibleFollowing(1_000, 100,
                new int[]{40, 40, 40}, 20, 1));
    }

    @Test public void mandatoryActiveContentWinsWhenViewportIsTooSmall() {
        assertEquals(0, FocusStepLayoutPolicy.visibleFollowing(80, 100,
                new int[]{40, 40}, 20, 2));
    }

    @Test public void pureCrossProductPreservesBudgetAndLimitContracts() {
        int[][] rowSets = {{}, {24}, {40, 60, 50}, {100, 20, 80, 10, 120}};
        int[] budgets = {0, 80, 100, 119, 120, 160, 220, 400, 1_000};
        int[] limits = {0, 1, 3, 5, 99};
        for (int[] rows : rowSets) for (int limit : limits) {
            int previous = -1;
            for (int budget : budgets) {
                int actual = FocusStepLayoutPolicy.visibleFollowing(
                        budget, 100, rows, 20, limit);
                int upperBound = Math.min(limit, rows.length);
                assertTrue(actual >= 0);
                assertTrue(actual <= upperBound);
                assertTrue("a larger budget must never reduce the admitted row count",
                        actual >= previous);
                assertEquals(expectedVisible(budget, 100, rows, 20, upperBound), actual);
                previous = actual;
            }
        }
    }

    private static int expectedVisible(int budget, int required, int[] rows,
                                       int more, int upperBound) {
        int best = 0;
        int prefix = 0;
        for (int candidate = 0; candidate <= upperBound; candidate++) {
            int extent = required + prefix + (candidate < rows.length ? more : 0);
            if (extent <= budget) best = candidate;
            if (candidate < upperBound) prefix += rows[candidate];
        }
        return best;
    }
}
