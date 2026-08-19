package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

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
}
