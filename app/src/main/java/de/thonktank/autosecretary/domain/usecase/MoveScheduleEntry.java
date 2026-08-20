package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Moves one independently scheduled task placement and reconciles open Today rows. */
public final class MoveScheduleEntry {
    private static final long STEP = 1_024L;
    private final TaskRepository repository;
    private final Clock clock;

    public MoveScheduleEntry(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void execute(String entryId, TaskSlot targetSlot, String beforeEntryId) {
        repository.inTransaction(() -> {
            List<TaskScheduleEntry> all = repository.scheduleEntries();
            TaskScheduleEntry moving = find(all, entryId);
            if (moving == null) return null;
            Task task = repository.findTask(moving.taskId);
            if (task == null || task.archived || task.conditionDone)
                throw new IllegalArgumentException("Nur aktive Aufgaben können sortiert werden.");
            if (moving.slot != targetSlot)
                for (TaskScheduleEntry entry : all)
                    if (entry.taskId.equals(moving.taskId) && entry.slot == targetSlot)
                        throw new IllegalArgumentException(
                                "Die Aufgabe ist in dieser Tageszeit bereits eingeplant.");

            Occurrence open = openOccurrence(moving.taskId, moving.slot);
            if (open != null && moving.slot != targetSlot
                    && repository.findOccurrence(moving.taskId, open.scheduledOn, targetSlot) != null)
                throw new IllegalArgumentException(
                        "Das heutige Aufgabenblatt belegt diese Tageszeit bereits.");

            List<TaskScheduleEntry> source = inSlot(all, moving.slot, moving.id);
            List<TaskScheduleEntry> target = moving.slot == targetSlot
                    ? source : inSlot(all, targetSlot, moving.id);
            TaskScheduleEntry changed = moving.move(targetSlot, 0);
            int insertion = indexOf(target, beforeEntryId);
            if (insertion < 0) insertion = target.size();
            target.add(insertion, changed);
            List<TaskScheduleEntry> writes = new ArrayList<>();
            resequence(target, writes);
            if (moving.slot != targetSlot) resequence(source, writes);
            repository.putScheduleEntries(writes);
            syncTaskProjection(task);

            if (open != null && moving.slot != targetSlot)
                repository.updateOccurrence(open.moveTo(targetSlot, open.sortOrder));
            reconcileOpenOrders(moving.slot, targetSlot);
            return null;
        });
    }

    private void syncTaskProjection(Task task) {
        List<TaskScheduleEntry> entries = repository.scheduleEntries(task.id);
        entries.sort(Comparator.comparingInt((TaskScheduleEntry value) -> value.slot.rank)
                .thenComparingLong(value -> value.displayOrder));
        if (entries.isEmpty()) return;
        int mask = 0;
        for (TaskScheduleEntry entry : entries) mask |= TimeOfDay.fromSlot(entry.slot).bit;
        TaskScheduleEntry primary = entries.get(0);
        repository.updateTask(task.withSchedule(primary.slot,
                task.recurrence == Recurrence.ONCE ? 0 : mask, primary.displayOrder));
    }

    private void reconcileOpenOrders(TaskSlot first, TaskSlot second) {
        List<TaskScheduleEntry> schedule = repository.scheduleEntries();
        Map<String, Integer> ranks = new HashMap<>();
        for (TaskSlot slot : TaskSlot.values()) {
            List<TaskScheduleEntry> values = inSlot(schedule, slot, null);
            for (int index = 0; index < values.size(); index++)
                ranks.put(key(values.get(index).taskId, slot), index + 1);
        }
        Map<TaskSlot, Integer> tail = new HashMap<>();
        for (Occurrence occurrence : repository.openOccurrences()) {
            if (occurrence.slot != first && occurrence.slot != second) continue;
            Integer rank = ranks.get(key(occurrence.taskId, occurrence.slot));
            int value = rank == null
                    ? ranks.size() + tail.merge(occurrence.slot, 1, Integer::sum) : rank;
            if (occurrence.sortOrder != value)
                repository.updateOccurrence(occurrence.moveTo(value));
        }
    }

    private Occurrence openOccurrence(TaskId taskId, TaskSlot slot) {
        for (Occurrence occurrence : repository.openOccurrences())
            if (occurrence.taskId.equals(taskId) && occurrence.slot == slot) return occurrence;
        return null;
    }

    private static List<TaskScheduleEntry> inSlot(List<TaskScheduleEntry> all, TaskSlot slot,
                                                   String excludedId) {
        List<TaskScheduleEntry> values = new ArrayList<>();
        for (TaskScheduleEntry entry : all)
            if (entry.slot == slot && (excludedId == null || !entry.id.equals(excludedId)))
                values.add(entry);
        values.sort(Comparator.comparingLong((TaskScheduleEntry value) -> value.displayOrder)
                .thenComparing(value -> value.id));
        return values;
    }

    private static void resequence(List<TaskScheduleEntry> values,
                                   List<TaskScheduleEntry> writes) {
        for (int index = 0; index < values.size(); index++) {
            TaskScheduleEntry changed = values.get(index).withOrder((index + 1L) * STEP);
            values.set(index, changed);
            writes.add(changed);
        }
    }

    private static TaskScheduleEntry find(List<TaskScheduleEntry> values, String id) {
        for (TaskScheduleEntry value : values) if (value.id.equals(id)) return value;
        return null;
    }

    private static int indexOf(List<TaskScheduleEntry> values, String id) {
        if (id == null) return -1;
        for (int index = 0; index < values.size(); index++)
            if (values.get(index).id.equals(id)) return index;
        return -1;
    }

    private static String key(TaskId id, TaskSlot slot) { return id.value + '|' + slot.name(); }
}
