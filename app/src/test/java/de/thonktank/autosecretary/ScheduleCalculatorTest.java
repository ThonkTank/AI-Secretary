package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RoutineProgress;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import org.junit.Test;

import java.time.LocalDate;

public class ScheduleCalculatorTest {
    private Task task(Recurrence recurrence, int interval, int mask, LocalDate due) {
        return Task.restore(TaskId.of("id"), "Test", TaskSlot.MORNING, recurrence,
                interval, mask, false, "", false, false, due, null, null,
                new RoutineProgress(1, 0, 0, null), 1_001_000L, false);
    }

    @Test public void intervalIsAnchoredToActualCompletion() {
        assertEquals(LocalDate.of(2026, 8, 18), ScheduleCalculator.nextDue(
                task(Recurrence.INTERVAL, 3, 0, LocalDate.of(2026, 8, 15)),
                LocalDate.of(2026, 8, 15)));
    }

    @Test public void weekdayScheduleFindsNextSelectedDay() {
        boolean[] selected = {false, false, true, false, false, false, true};
        assertEquals(LocalDate.of(2026, 8, 19), ScheduleCalculator.nextDue(
                task(Recurrence.WEEKDAYS, 1, ScheduleCalculator.weekdayMask(selected),
                        LocalDate.of(2026, 8, 15)), LocalDate.of(2026, 8, 16)));
    }

    @Test public void dateDueRuleCarriesOnlyOneOpenOccurrenceForward() {
        assertTrue(ScheduleCalculator.isDue(
                task(Recurrence.DAILY, 1, 0, LocalDate.of(2026, 8, 14)),
                LocalDate.of(2026, 8, 15)));
        assertFalse(ScheduleCalculator.isDue(
                task(Recurrence.DAILY, 1, 0, LocalDate.of(2026, 8, 16)),
                LocalDate.of(2026, 8, 15)));
    }

    @Test public void completingLateBreaksOnlyCurrentStreak() {
        assertFalse(ScheduleCalculator.completedOnTime("2026-08-14", LocalDate.of(2026, 8, 15)));
        assertTrue(ScheduleCalculator.completedOnTime("2026-08-15", LocalDate.of(2026, 8, 15)));
    }
}
