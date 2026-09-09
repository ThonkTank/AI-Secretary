package de.thonktank.autosecretary.presentation.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class FlowRunsTimeTest {
    @Test
    fun remainingTimeRoundsUpAndUsesCompactUnits() {
        val now = 1_000_000L
        assertEquals("", remainingDurationText(null, now))
        assertEquals("0 min", remainingDurationText(now, now))
        assertEquals("1 min", remainingDurationText(now + 1, now))
        assertEquals("59 min", remainingDurationText(now + 59 * 60_000L, now))
        assertEquals("1 h", remainingDurationText(now + 60 * 60_000L, now))
        assertEquals("1 h 1 min", remainingDurationText(now + 61 * 60_000L, now))
        assertEquals("1 d", remainingDurationText(now + 24 * 60 * 60_000L, now))
        assertEquals("1 d 2 h", remainingDurationText(now + 26 * 60 * 60_000L, now))
    }
}
