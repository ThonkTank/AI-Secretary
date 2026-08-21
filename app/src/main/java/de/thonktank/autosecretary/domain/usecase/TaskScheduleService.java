package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The sole application service allowed to mutate persisted task placements. */
public final class TaskScheduleService {
    private final TaskRepository repository;
    private final IdGenerator ids;

    public TaskScheduleService(TaskRepository repository, IdGenerator ids) {
        this.repository = repository;
        this.ids = ids;
    }

    public void create(Task task, TaskDefinition definition) {
        create(task, TaskSchedule.desiredSlots(definition));
    }

    public void create(Task task, List<TaskSlot> desiredSlots) {
        TaskSchedule current = new TaskSchedule(repository.scheduleEntries());
        List<TaskScheduleEntry> entries = new ArrayList<>();
        for (TaskSlot slot : desiredSlots)
            entries.add(new TaskScheduleEntry(ids.nextId(), task.id, slot,
                    current.nextOrder(slot)));
        repository.putScheduleEntries(entries);
    }

    public void sync(Task task, TaskDefinition definition) {
        sync(task, TaskSchedule.desiredSlots(definition));
    }

    public void sync(Task task, List<TaskSlot> desiredSlots) {
        TaskSchedule current = new TaskSchedule(repository.scheduleEntries());
        Map<TaskSlot, TaskScheduleEntry> existing = new HashMap<>();
        for (TaskScheduleEntry entry : current.placements(task.id))
            existing.put(entry.slot, entry);
        List<TaskScheduleEntry> writes = new ArrayList<>();
        for (TaskSlot slot : desiredSlots) {
            TaskScheduleEntry retained = existing.remove(slot);
            writes.add(retained == null
                    ? new TaskScheduleEntry(ids.nextId(), task.id, slot, current.nextOrder(slot))
                    : retained);
        }
        for (TaskScheduleEntry removed : existing.values())
            repository.deleteScheduleEntry(removed.id);
        repository.putScheduleEntries(writes);
    }

    public void move(String entryId, TaskSlot targetSlot, String beforeEntryId) {
        repository.inTransaction(() -> {
            TaskSchedule schedule = new TaskSchedule(repository.scheduleEntries());
            TaskScheduleEntry moving = null;
            for (TaskScheduleEntry entry : schedule.entries())
                if (entry.id.equals(entryId)) moving = entry;
            if (moving == null) return null;
            Task task = repository.findTask(moving.taskId);
            if (task == null || task.archived || task.conditionDone)
                throw new IllegalArgumentException("Nur aktive Aufgaben können sortiert werden.");

            Occurrence open = openOccurrence(moving.taskId, moving.slot);
            if (open != null && moving.slot != targetSlot
                    && repository.findOccurrence(moving.taskId, open.scheduledOn, targetSlot) != null)
                throw new IllegalArgumentException(
                        "Das heutige Aufgabenblatt belegt diese Tageszeit bereits.");

            TaskSchedule.Mutation mutation = schedule.move(entryId, targetSlot, beforeEntryId);
            if (!mutation.changed) return null;
            repository.putScheduleEntries(mutation.writes);
            if (open != null && mutation.sourceSlot != mutation.targetSlot)
                repository.updateOccurrence(open.moveTo(mutation.targetSlot, open.sortOrder));
            reconcileOpenOrders(mutation.schedule, mutation.sourceSlot, mutation.targetSlot);
            return null;
        });
    }

    public TaskSchedule load() { return new TaskSchedule(repository.scheduleEntries()); }

    private void reconcileOpenOrders(TaskSchedule schedule, TaskSlot first, TaskSlot second) {
        Map<String, Integer> ranks = new HashMap<>();
        for (TaskSlot slot : TaskSlot.values()) {
            int rank = 0;
            for (TaskScheduleEntry entry : schedule.entries())
                if (entry.slot == slot) ranks.put(key(entry.taskId, slot), ++rank);
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

    private static String key(TaskId id, TaskSlot slot) {
        return id.value + '|' + slot.name();
    }
}
