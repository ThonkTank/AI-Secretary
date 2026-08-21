package de.thonktank.autosecretary.domain.schedule;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The sole application service allowed to mutate persisted task placements. */
public final class TaskScheduleService {
    private final TaskScheduleRepository repository;
    private final IdGenerator ids;

    public TaskScheduleService(TaskScheduleRepository repository, IdGenerator ids) {
        this.repository = repository;
        this.ids = ids;
    }

    public void create(Task task, TaskDefinition definition) {
        create(task, TaskSchedule.desiredSlots(definition));
    }

    public void create(Task task, List<TaskSlot> desiredSlots) {
        List<TaskScheduleEntry> entries = new ArrayList<>();
        for (TaskSlot slot : desiredSlots) {
            TaskSchedule current = new TaskSchedule(repository.scheduleEntries(slot));
            entries.add(new TaskScheduleEntry(ids.nextId(), task.id, slot,
                    current.nextOrder(slot)));
        }
        repository.putScheduleEntries(entries);
    }

    public void sync(Task task, TaskDefinition definition) {
        sync(task, TaskSchedule.desiredSlots(definition));
    }

    public void sync(Task task, List<TaskSlot> desiredSlots) {
        TaskSchedule current = new TaskSchedule(repository.scheduleEntries(task.id));
        Map<TaskSlot, TaskScheduleEntry> existing = new HashMap<>();
        for (TaskScheduleEntry entry : current.placements(task.id))
            existing.put(entry.slot, entry);
        List<TaskScheduleEntry> writes = new ArrayList<>();
        for (TaskSlot slot : desiredSlots) {
            TaskScheduleEntry retained = existing.remove(slot);
            writes.add(retained == null
                    ? new TaskScheduleEntry(ids.nextId(), task.id, slot,
                    new TaskSchedule(repository.scheduleEntries(slot)).nextOrder(slot))
                    : retained);
        }
        for (TaskScheduleEntry removed : existing.values())
            repository.deleteScheduleEntry(removed.id);
        repository.putScheduleEntries(writes);
    }

    public ScheduleMoveResult move(ScheduleMoveRequest request) {
        return repository.inTransaction(() -> {
            TaskScheduleEntry moving = repository.findScheduleEntry(request.entryId.value);
            if (moving == null) return ScheduleMoveResult.NOT_FOUND;
            List<TaskScheduleEntry> affected = new ArrayList<>(
                    repository.scheduleEntries(moving.slot));
            if (moving.slot != request.targetSlot)
                affected.addAll(repository.scheduleEntries(request.targetSlot));
            TaskSchedule schedule = new TaskSchedule(affected);
            Task task = repository.findTask(moving.taskId);
            if (task == null || task.archived || task.conditionDone)
                return ScheduleMoveResult.REJECTED_INACTIVE_TASK;
            if (moving.slot != request.targetSlot && schedule.contains(
                    moving.taskId, request.targetSlot))
                return ScheduleMoveResult.REJECTED_DUPLICATE_SLOT;

            Occurrence open = repository.openOccurrence(moving.taskId, moving.slot);
            if (open != null && moving.slot != request.targetSlot
                    && repository.findOccurrence(moving.taskId, open.scheduledOn,
                    request.targetSlot) != null)
                return ScheduleMoveResult.REJECTED_TODAY_SLOT_OCCUPIED;

            TaskSchedule.Mutation mutation = schedule.move(request.entryId.value,
                    request.targetSlot, request.beforeEntryId.map(value -> value.value).orElse(null));
            if (!mutation.changed) return ScheduleMoveResult.NOT_FOUND;
            repository.putScheduleEntries(mutation.writes);
            if (open != null && mutation.sourceSlot != mutation.targetSlot)
                repository.updateOccurrence(open.moveTo(mutation.targetSlot, open.sortOrder));
            reconcileOpenOrders(mutation.schedule, mutation.sourceSlot, mutation.targetSlot);
            return ScheduleMoveResult.MOVED;
        });
    }

    private void reconcileOpenOrders(TaskSchedule schedule, TaskSlot first, TaskSlot second) {
        Map<String, Integer> ranks = new HashMap<>();
        List<TaskSlot> affected = new ArrayList<>();
        affected.add(first);
        if (second != first) affected.add(second);
        Map<TaskSlot, Integer> slotSizes = new HashMap<>();
        for (TaskSlot slot : affected) {
            int rank = 0;
            for (TaskScheduleEntry entry : schedule.entries())
                if (entry.slot == slot) ranks.put(key(entry.taskId, slot), ++rank);
            slotSizes.put(slot, rank);
        }
        Map<TaskSlot, Integer> tail = new HashMap<>();
        for (TaskSlot slot : affected) {
            for (Occurrence occurrence : repository.openOccurrences(slot)) {
                Integer rank = ranks.get(key(occurrence.taskId, occurrence.slot));
                int value = rank == null
                        ? slotSizes.get(slot) + tail.merge(slot, 1, Integer::sum) : rank;
                if (occurrence.sortOrder != value)
                    repository.updateOccurrence(occurrence.moveTo(value));
            }
        }
    }

    private static String key(TaskId id, TaskSlot slot) {
        return id.value + '|' + slot.name();
    }
}
