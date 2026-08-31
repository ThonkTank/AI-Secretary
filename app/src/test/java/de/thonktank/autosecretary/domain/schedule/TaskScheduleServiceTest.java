package de.thonktank.autosecretary.domain.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.ScheduleEntryId;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TaskScheduleServiceTest {
    @Test public void reorderReadsAndNormalizesOnlySourceAndTargetSlots() {
        ScheduleDouble store = new ScheduleDouble();
        Task morningTask = task("morning");
        Task eveningTask = task("evening");
        Task untouched = task("midday");
        store.add(morningTask, new TaskScheduleEntry("m", morningTask.id,
                TaskSlot.MORNING, 1_024));
        store.add(eveningTask, new TaskScheduleEntry("e", eveningTask.id,
                TaskSlot.EVENING, 1_024));
        store.add(untouched, new TaskScheduleEntry("u", untouched.id,
                TaskSlot.MIDDAY, 7_168));
        store.add(open("mo", morningTask.id, TaskSlot.MORNING, 9));
        store.add(open("eo", eveningTask.id, TaskSlot.EVENING, 8));
        store.add(open("uo", untouched.id, TaskSlot.MIDDAY, 77));

        ScheduleMoveResult result = new TaskScheduleService(store, store, () -> "unused").move(
                new ScheduleMoveRequest(ScheduleEntryId.of("m"), TaskSlot.EVENING,
                        Optional.empty()));

        assertEquals(ScheduleMoveResult.MOVED, result);
        assertEquals(EnumSet.of(TaskSlot.MORNING, TaskSlot.EVENING), store.readSlots);
        assertFalse(store.readSlots.contains(TaskSlot.MIDDAY));
        assertEquals(77, store.occurrences.get("uo").sortOrder);
        assertEquals(TaskSlot.MIDDAY, store.schedule.get("u").slot);
    }

    private static Task task(String id) {
        return Task.restore(TaskId.of(id), id, Recurrence.DAILY, 1, 0,
                false, "", false, false, LocalDate.of(2026, 8, 21), null, null,
                LocalDate.of(2026, 8, 21), 1_024, false, null, TaskBoundKind.FOREVER,
                null, null, null,
                null, "");
    }

    private static Occurrence open(String id, TaskId task, TaskSlot slot, int order) {
        return new Occurrence(id, task, LocalDate.of(2026, 8, 21), slot,
                OccurrenceState.OPEN, order, null, OccurrenceKind.SCHEDULED);
    }

    /** Focused double: it cannot accidentally provide catalog, template, reward or global reads. */
    private static final class ScheduleDouble implements TaskScheduleRepository, TransactionRunner {
        final Map<TaskId, Task> tasks = new LinkedHashMap<>();
        final Map<String, TaskScheduleEntry> schedule = new LinkedHashMap<>();
        final Map<String, Occurrence> occurrences = new LinkedHashMap<>();
        final Set<TaskSlot> readSlots = EnumSet.noneOf(TaskSlot.class);

        void add(Task task, TaskScheduleEntry entry) {
            tasks.put(task.id, task);
            schedule.put(entry.id, entry);
        }
        void add(Occurrence value) { occurrences.put(value.id, value); }

        @Override public <T> T inTransaction(TransactionRunner.Transaction<T> operation) {
            return operation.execute();
        }
        @Override public Task findTask(TaskId id) { return tasks.get(id); }
        @Override public TaskScheduleEntry findScheduleEntry(String id) {
            return schedule.get(id);
        }
        @Override public List<TaskScheduleEntry> scheduleEntries() {
            throw new AssertionError("Scheduling mutations must not perform a global read");
        }
        @Override public List<TaskScheduleEntry> scheduleEntries(TaskId taskId) {
            List<TaskScheduleEntry> result = new ArrayList<>();
            for (TaskScheduleEntry value : schedule.values())
                if (value.taskId.equals(taskId)) result.add(value);
            return result;
        }
        @Override public List<TaskScheduleEntry> scheduleEntries(TaskSlot slot) {
            readSlots.add(slot);
            List<TaskScheduleEntry> result = new ArrayList<>();
            for (TaskScheduleEntry value : schedule.values())
                if (value.slot == slot) result.add(value);
            return result;
        }
        @Override public void putScheduleEntries(List<TaskScheduleEntry> entries) {
            for (TaskScheduleEntry value : entries) schedule.put(value.id, value);
        }
        @Override public void deleteScheduleEntry(String id) { schedule.remove(id); }
        @Override public Occurrence openOccurrence(TaskId taskId, TaskSlot slot) {
            for (Occurrence value : occurrences.values())
                if (value.taskId.equals(taskId) && value.slot == slot
                        && value.state == OccurrenceState.OPEN) return value;
            return null;
        }
        @Override public List<Occurrence> openOccurrences(TaskSlot slot) {
            readSlots.add(slot);
            List<Occurrence> result = new ArrayList<>();
            for (Occurrence value : occurrences.values())
                if (value.slot == slot && value.state == OccurrenceState.OPEN) result.add(value);
            return result;
        }
        @Override public Occurrence findOccurrence(TaskId taskId, LocalDate date, TaskSlot slot) {
            for (Occurrence value : occurrences.values())
                if (value.taskId.equals(taskId) && value.scheduledOn.equals(date)
                        && value.slot == slot) return value;
            return null;
        }
        @Override public void updateOccurrence(Occurrence occurrence) {
            occurrences.put(occurrence.id, occurrence);
        }
    }
}
