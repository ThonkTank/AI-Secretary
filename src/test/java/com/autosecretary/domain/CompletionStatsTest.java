package com.autosecretary.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class CompletionStatsTest {
    @Test
    public void bestStreakIsRaisedToCurrentStreak() {
        CompletionStats value = new CompletionStats(3, 2, 4);

        assertEquals(3, value.bestStreak());
    }

    @Test
    public void streakCannotExceedRecordedCompletions() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompletionStats(3, 3, 2));
    }
}
