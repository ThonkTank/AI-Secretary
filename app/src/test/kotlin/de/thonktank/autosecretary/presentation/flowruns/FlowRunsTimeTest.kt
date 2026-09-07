package de.thonktank.autosecretary.presentation.flowruns

import org.junit.Assert.assertEquals
import org.junit.Test

class FlowRunsTimeTest {
    @Test
    fun remainingTimeRoundsUpAndUsesCompactUnits() {
        val now = 1_000_000L
        assertEquals("", remainingFlowTime(null, now))
        assertEquals("0 min", remainingFlowTime(now, now))
        assertEquals("1 min", remainingFlowTime(now + 1, now))
        assertEquals("59 min", remainingFlowTime(now + 59 * 60_000L, now))
        assertEquals("1 h", remainingFlowTime(now + 60 * 60_000L, now))
        assertEquals("1 h 1 min", remainingFlowTime(now + 61 * 60_000L, now))
        assertEquals("1 d", remainingFlowTime(now + 24 * 60 * 60_000L, now))
        assertEquals("1 d 2 h", remainingFlowTime(now + 26 * 60 * 60_000L, now))
    }
}
