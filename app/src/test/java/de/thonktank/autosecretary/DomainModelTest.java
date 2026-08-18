package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import de.thonktank.autosecretary.data.local.TaskEntityMapper;
import de.thonktank.autosecretary.domain.model.Recurrence;
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

    @Test public void entityMapperKeepsRoomCodesOutOfTheDomainModel() {
        TaskEntity entity = new TaskEntity("id", "Aufgabe", "MORNING", "DAILY", 1, 0,
                false, "", false, false, "2026-08-15", "", "", 1_001_000L,
                true, null, 1, "FOREVER", "", null, null, "", "");
        TaskEntityMapper mapper = new TaskEntityMapper();

        Task task = mapper.toDomain(entity);
        TaskEntity roundTrip = mapper.toEntity(task);

        assertEquals(TaskSlot.MORNING, task.slot);
        assertEquals(Recurrence.DAILY, task.recurrence);
        assertEquals("MORNING", roundTrip.slot);
        assertEquals("DAILY", roundTrip.recurrence);
        assertEquals(1_001_000L, roundTrip.displayOrder);
    }
}
