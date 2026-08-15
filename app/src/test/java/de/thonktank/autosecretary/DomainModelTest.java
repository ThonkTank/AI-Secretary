package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import de.thonktank.autosecretary.data.local.TaskEntityMapper;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RoutineProgress;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import org.junit.Test;

import java.time.LocalDate;

public final class DomainModelTest {
    @Test public void taskSlotsUseOnlyStableStorageCodes() {
        assertEquals(TaskSlot.MORNING, TaskSlot.fromStorage("MORNING"));
        assertThrows(IllegalArgumentException.class, () -> TaskSlot.fromStorage("Morgen"));
        assertThrows(IllegalArgumentException.class, () -> TaskSlot.fromStorage("unbekannt"));
        assertEquals("MIDDAY", TaskSlot.MIDDAY.storageCode);
    }

    @Test public void invalidDomainCombinationsAreRejectedAtCreation() {
        assertThrows(IllegalArgumentException.class, () -> Task.create(
                TaskId.of("weekday"), "Routine", TaskSlot.MORNING, Recurrence.WEEKDAYS,
                1, 0, false, "", LocalDate.of(2026, 8, 15), 1_001_000L));
        assertThrows(IllegalArgumentException.class, () -> Task.create(
                TaskId.of("ongoing"), "Praktikum", TaskSlot.LATER, Recurrence.ONCE,
                1, 0, true, "", LocalDate.of(2026, 8, 15), 4_001_000L));
    }

    @Test public void routineProgressCountsAtMostOncePerWeekAndLateCompletionResets() {
        LocalDate monday = LocalDate.of(2026, 8, 10);
        RoutineProgress first = new RoutineProgress(1, 0, 0, null)
                .recordCompletion(true, false, monday);
        RoutineProgress sameWeek = first.recordCompletion(true, true, monday.plusDays(2));
        RoutineProgress nextWeek = sameWeek.recordCompletion(true, true, monday.plusWeeks(1));
        RoutineProgress late = nextWeek.recordCompletion(false, true, monday.plusWeeks(1).plusDays(1));

        assertEquals(1, first.weekStreak);
        assertEquals(1, sameWeek.weekStreak);
        assertEquals(2, nextWeek.weekStreak);
        assertEquals(0, late.weekStreak);
        assertEquals(0, late.occurrenceStreak);
        assertNull(late.lastCountedWeek);
        assertEquals(nextWeek.level, late.level);
    }

    @Test public void entityMapperKeepsRoomCodesOutOfTheDomainModel() {
        TaskEntity entity = new TaskEntity("id", "Aufgabe", "MORNING", "DAILY", 1, 0,
                false, "", false, false, "2026-08-15", "", "", 2, 3, 4,
                "2026-08-11", 1_001_000L, true);
        TaskEntityMapper mapper = new TaskEntityMapper();

        Task task = mapper.toDomain(entity);
        TaskEntity roundTrip = mapper.toEntity(task);

        assertEquals(TaskSlot.MORNING, task.slot);
        assertEquals(Recurrence.DAILY, task.recurrence);
        assertEquals("MORNING", roundTrip.slot);
        assertEquals("DAILY", roundTrip.recurrence);
        assertEquals(4, roundTrip.routineStreakWeeks);
        assertEquals("2026-08-10", roundTrip.lastStreakWeek);
    }
}
