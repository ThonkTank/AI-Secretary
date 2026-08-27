package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FlowWakeSchedulerTest {
    @Test public void wakeDelayUsesDurableEpochAndNeverBecomesNegative() {
        assertEquals(2_000L, FlowWakeScheduler.initialDelay(10_000L, 12_000L));
        assertEquals(0L, FlowWakeScheduler.initialDelay(12_000L, 10_000L));
    }
}
