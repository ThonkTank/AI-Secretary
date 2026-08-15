package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public class ScheduleCalculatorTest {
    private TaskEntity task(String recurrence, int interval, int mask) {
        return new TaskEntity("id", "Test", TaskSlots.MORNING, recurrence, interval, mask, false, "", false, false,
                "2026-08-15", "", "", 1, 0, 0, "", 1_001_000L, false);
    }

    @Test public void intervalIsAnchoredToActualCompletion() {
        assertEquals(LocalDate.of(2026, 8, 18), ScheduleCalculator.nextDue(task("INTERVAL", 3, 0), LocalDate.of(2026, 8, 15)));
    }

    @Test public void weekdayScheduleFindsNextSelectedDay() {
        boolean[] selected = {false, false, true, false, false, false, true}; // Wednesday and Sunday
        assertEquals(LocalDate.of(2026, 8, 19), ScheduleCalculator.nextDue(task("WEEKDAYS", 1, ScheduleCalculator.weekdayMask(selected)), LocalDate.of(2026, 8, 16)));
    }

    @Test public void dateDueRuleCarriesOnlyOneOpenOccurrenceForward() {
        TaskEntity daily = task("DAILY", 1, 0); daily.nextDueOn = "2026-08-14";
        assertTrue(ScheduleCalculator.isDue(daily, LocalDate.of(2026, 8, 15)));
        daily.nextDueOn = "2026-08-16";
        assertFalse(ScheduleCalculator.isDue(daily, LocalDate.of(2026, 8, 15)));
    }

    @Test public void completingLateBreaksOnlyCurrentStreak() {
        assertFalse(ScheduleCalculator.completedOnTime("2026-08-14", LocalDate.of(2026, 8, 15)));
        assertTrue(ScheduleCalculator.completedOnTime("2026-08-15", LocalDate.of(2026, 8, 15)));
    }
}
