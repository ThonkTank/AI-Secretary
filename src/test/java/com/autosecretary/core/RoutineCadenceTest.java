package com.autosecretary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public final class RoutineCadenceTest {
    @Test
    public void missedWeeklyRoutineStaysOpenUntilCompletedThenAdvancesPastToday() {
        Obligation routine = routine("2026-08-03", 7);

        assertTrue(routine.isOpenOn(LocalDate.parse("2026-08-10")));
        assertTrue(routine.isOpenOn(LocalDate.parse("2026-08-12")));

        RoutineCadence.complete(routine, LocalDate.parse("2026-08-12"));

        assertEquals(LocalDate.parse("2026-08-17"), routine.nextDueDate);
        assertFalse(routine.isOpenOn(LocalDate.parse("2026-08-12")));
        assertEquals(1, routine.totalCompletions);
    }

    @Test
    public void streakContinuesWithinOneCadenceAndResetsAfterLongGap() {
        Obligation routine = routine("2026-08-10", 7);
        routine.currentStreak = 4;

        RoutineCadence.complete(routine, LocalDate.parse("2026-08-15"));
        assertEquals(5, routine.currentStreak);
        assertEquals(5, routine.bestStreak);

        routine.nextDueDate = LocalDate.parse("2026-08-17");
        RoutineCadence.complete(routine, LocalDate.parse("2026-08-31"));
        assertEquals(1, routine.currentStreak);
        assertEquals(5, routine.bestStreak);
    }

    private static Obligation routine(String due, int cadence) {
        Obligation item = new Obligation();
        item.kind = Obligation.Kind.ROUTINE;
        item.title = "Routine";
        item.cadenceDays = cadence;
        item.nextDueDate = LocalDate.parse(due);
        return item;
    }
}
